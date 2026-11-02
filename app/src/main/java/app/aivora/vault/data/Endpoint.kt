package app.aivora.vault.data

/**
 * API 端点规范化。
 *
 * 目标：无论用户从哪抄来一个 OpenAI 兼容端点（裸域名、带 /v1、带中转前缀、直接贴接口地址），
 * 都归一到「base URL」形态，供 Diagnostics 拼接 `/models` 与 `/chat/completions`。
 *
 * 三级判别：
 *  - **模板白名单**：官方服务商域名精确匹配，一律原样返回，绝不改写。
 *  - **第三方**：IP 字面量 / 非标端口 / 内网段 / 路径含中转标识 / 不在白名单，走完整规范化。
 *  - **无法判断**：路径结构异常（末尾是接口层名且无版本段），不改写，提示手动确认。
 */
object Endpoint {

    /** 官方模板域名。命中即视为「模板」，不做任何规范化。 */
    private val TEMPLATE_HOSTS = listOf(
        "api.openai.com",
        "api.deepseek.com",
        "api.moonshot.cn",
        "open.bigmodel.cn",
        "dashscope.aliyuncs.com",
        "api.siliconflow.cn",
        "openrouter.ai",
        "api.anthropic.com",
        "generativelanguage.googleapis.com",
        "127.0.0.1",
        "localhost"
    )

    /** 终结端点段：出现在末尾说明用户贴的是完整接口地址，需要剥掉。 */
    private val TERMINAL_SEGMENTS = listOf(
        "chat/completions",
        "completions",
        "responses",
        "messages",
        "embeddings",
        "models"
    )

    /** 接口层名：末尾出现时说明前面是 base，只需补 /v1，不能再剥。 */
    private val INTERFACE_NAMES = setOf(
        "openai", "anthropic", "claude", "gemini", "azure",
        "compatible", "compatible-mode", "api", "proxy", "relay", "messages"
    )

    /** 已含版本段的判定：v1 / v2 / v3 / v4 / v1beta / v1alpha 等。 */
    private val VERSION_SEGMENT = Regex("^v\\d+[a-z]*$", RegexOption.IGNORE_CASE)

    /** 路径中出现中转标识时，视为第三方。 */
    private val RELAY_MARKERS = listOf(
        "/api/openai", "/proxy/", "/relay/", "/compatible-mode/", "/one-api/", "/new-api/"
    )

    private val IPV4 = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")

    enum class Kind { TEMPLATE, THIRD_PARTY, UNKNOWN }

    /** 规范化结果。[changed] 为 true 时界面应显示「已自动规范化」。 */
    data class Result(
        val endpoint: String,
        val kind: Kind,
        val changed: Boolean,
        val note: String = ""
    )

    /**
     * 规范化入口。[allowHttp] 指是否允许明文 HTTP（关闭网络安全配置时不应改写协议）。
     */
    fun normalize(input: String, enabled: Boolean = true): Result {
        val raw = input.trim()
        if (raw.isEmpty()) return Result(raw, Kind.UNKNOWN, false)

        // 模板白名单优先：官方地址永远正确，一个字符都不动。
        val hostOfRaw = hostOf(raw)
        if (hostOfRaw != null && isTemplateHost(hostOfRaw)) {
            return Result(raw, Kind.TEMPLATE, false)
        }

        val kind = classify(raw, hostOfRaw)

        if (!enabled) return Result(raw, kind, false)

        // 无法判断的结构：不改，只提示。
        if (kind == Kind.UNKNOWN) {
            return Result(raw, Kind.UNKNOWN, false, "路径结构非标准，请手动确认端点是否正确")
        }

        val normalized = rewrite(raw)
        return Result(normalized, kind, normalized != raw, if (normalized != raw) "已自动规范化：$normalized" else "")
    }

    // ------------------------------------------------------------------ 分类

    private fun isTemplateHost(host: String): Boolean {
        val h = host.lowercase()
        return TEMPLATE_HOSTS.any { h == it || h.endsWith(".$it") }
    }

    private fun classify(raw: String, host: String?): Kind {
        val withScheme = if (raw.contains("://")) raw else "http://$raw"
        val authority = runCatching { java.net.URI(withScheme).authority }.getOrNull()
        val hostName = host ?: authority?.substringBefore(':')?.substringBefore('@') ?: return Kind.UNKNOWN
        val path = pathOf(withScheme)
        val port = runCatching { java.net.URI(withScheme).port }.getOrNull() ?: -1

        // IP 字面量 / 内网 / 本地 / 非标端口 / 路径含中转标识 -> 第三方
        if (IPV4.matches(hostName)) return Kind.THIRD_PARTY
        if (hostName.startsWith("192.168.") || hostName.startsWith("10.") || hostName.startsWith("172.")) return Kind.THIRD_PARTY
        if (hostName == "localhost" || hostName.endsWith(".local")) return Kind.THIRD_PARTY
        if (port != -1 && port != 80 && port != 443) return Kind.THIRD_PARTY
        if (RELAY_MARKERS.any { path.contains(it, ignoreCase = true) }) return Kind.THIRD_PARTY

        // 路径结构异常判定：末尾是接口层名、且整条路径没有版本段 -> 不猜。
        // 注意顺序：中转标识（/proxy/、/api/openai 等）优先判为第三方，可安全补 /v1；
        // 只有「既无中转标识、又无版本段、末尾还是接口层名」才进入不猜分支。
        val segments = path.split('/').filter(String::isNotEmpty)
        val hasVersion = segments.any { VERSION_SEGMENT.matches(it) }
        val relayLike = RELAY_MARKERS.any { path.contains(it, ignoreCase = true) }
        if (!relayLike && !hasVersion && segments.isNotEmpty() && segments.last().lowercase() in INTERFACE_NAMES) {
            return Kind.UNKNOWN
        }

        // 其余一律按第三方处理（域名不在白名单）
        return Kind.THIRD_PARTY
    }

    // ------------------------------------------------------------------ 改写

    /** 十步规范化。顺序敏感：先清理，再剥离，最后补版本段。 */
    private fun rewrite(raw: String): String {
        var s = raw.trim()

        // 1-2. 缺协议补充：IP / 内网 / 本地补 http，域名补 https
        if (!s.contains("://")) {
            val hostCandidate = s.substringBefore('/').substringBefore(':').lowercase()
            val isLocal = IPV4.matches(hostCandidate) || hostCandidate == "localhost" ||
                hostCandidate.startsWith("192.168.") || hostCandidate.startsWith("10.") ||
                hostCandidate.endsWith(".local") || s.substringBefore('/').contains(":") &&
                !s.substringBefore('/').endsWith(".com")
            s = (if (isLocal) "http://" else "https://") + s
        }

        // 拆出 scheme / authority / path，避免误伤协议里的 //
        val schemeEnd = s.indexOf("://") + 3
        val scheme = s.substring(0, schemeEnd)
        val rest = s.substring(schemeEnd)
        val authority = rest.substringBefore('/')
        var path = rest.substring(authority.length)

        // 3. 版本段统一小写（只动路径段，不动 authority）
        // 4. 折叠重复斜杠
        path = path.replace(Regex("/{2,}"), "/")

        // 5. 剥末尾斜杠
        path = path.trimEnd('/')

        // 6. 剥终结段（可能重复多层，如 /v1/chat/completions/）
        var segments = path.split('/').filter(String::isNotEmpty).toMutableList()
        var stripped = true
        while (stripped && segments.isNotEmpty()) {
            stripped = false
            for (term in TERMINAL_SEGMENTS) {
                val parts = term.split('/')
                if (segments.size >= parts.size && segments.takeLast(parts.size).map(String::lowercase) == parts) {
                    repeat(parts.size) { segments.removeAt(segments.size - 1) }
                    stripped = true
                    break
                }
            }
        }

        // 版本段小写
        segments = segments.map { if (VERSION_SEGMENT.matches(it)) it.lowercase() else it }.toMutableList()

        // 7-10. 判断是否已有版本段
        val hasVersion = segments.any { VERSION_SEGMENT.matches(it) }
        if (!hasVersion) {
            if (segments.isEmpty()) {
                segments.add("v1")
            } else if (segments.last().lowercase() in INTERFACE_NAMES) {
                segments.add("v1")
            } else {
                segments.add("v1")
            }
        }

        return scheme + authority + "/" + segments.joinToString("/")
    }

    // ------------------------------------------------------------------ 候选路径

    /**
     * 检测失败时的兜底候选。仅在主地址 `/models` 明确失败后调用，最多 3 个。
     * 顺序：剥掉版本段后的裸 base -> /v1 -> /api/v1
     */
    fun candidates(endpoint: String): List<String> {
        val segments = pathSegmentsOf(endpoint).toMutableList()
        val versionIndex = segments.indexOfFirst { VERSION_SEGMENT.matches(it) }
        val bare = if (versionIndex >= 0) segments.take(versionIndex).toMutableList() else segments

        val base = authorityPrefix(endpoint) + (if (bare.isEmpty()) "" else "/" + bare.joinToString("/"))
        return listOf(
            base + "/models",
            base + "/v1/models",
            base + "/api/v1/models"
        ).distinct()
    }

    // ------------------------------------------------------------------ 工具

    private fun hostOf(raw: String): String? {
        val withScheme = if (raw.contains("://")) raw else "http://$raw"
        return runCatching {
            java.net.URI(withScheme).host?.lowercase()
        }.getOrNull()
    }

    private fun pathOf(raw: String): String {
        val withScheme = if (raw.contains("://")) raw else "http://$raw"
        return runCatching { java.net.URI(withScheme).path.orEmpty() }.getOrDefault("")
    }

    private fun pathSegmentsOf(raw: String): List<String> =
        pathOf(raw).split('/').filter(String::isNotEmpty)

    private fun authorityPrefix(raw: String): String {
        val withScheme = if (raw.contains("://")) raw else "http://$raw"
        val schemeEnd = withScheme.indexOf("://") + 3
        val scheme = withScheme.substring(0, schemeEnd)
        val rest = withScheme.substring(schemeEnd)
        return scheme + rest.substringBefore('/')
    }
}
