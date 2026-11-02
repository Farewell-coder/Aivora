package app.aivora.vault.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * 第三方软件的配置导出格式解析。
 *
 * 用 kotlinx.serialization 的 [JsonElement] 做动态解析，不用数据类映射，原因有三个：
 *  1. 各家字段嵌套层级差异极大（Cherry Studio 是 redux 切片再 stringify 一次）；
 *  2. 项目开了 `isMinifyEnabled = true`，手写取值可以彻底避开 R8 反射裁剪问题；
 *  3. JsonElement 是纯 JVM 实现，可直接在单元测试里跑，不像 `org.json` 在单测中是桩。
 *
 * 解析只负责「识别 + 提取」，不做任何写入。调用方拿到 [Candidate] 列表后走预览页。
 */
object ThirdParty {

    /** 一条解析出来的候选配置。 */
    data class Candidate(
        val name: String,
        val provider: String = "",
        val endpoint: String = "",
        val apiKey: String = "",
        val models: List<String> = emptyList(),
        val defaultModel: String = "",
        val notes: String = "",
        /** 来源标记，用于导入后显示角标：Chatbox / Continue / … / .env */
        val source: String
    )

    /** 解析结果。 */
    sealed class Parsed {
        data class Ok(val candidates: List<Candidate>) : Parsed()
        data class Failed(val reason: String) : Parsed()
    }

    /** 支持格式清单，用于报错时提示。 */
    val SUPPORTED: List<String> = listOf(
        "Chatbox（settings.apiKey / apiHost）",
        "Continue（models[].apiKey / apiBase）",
        "LobeChat（keyVaults.openai.apiKey / baseURL）",
        "CC Switch（env.ANTHROPIC_API_KEY / ANTHROPIC_BASE_URL）",
        "New API / One API（key / base_url / models）",
        "Cherry Studio（备份 ZIP 内的 data.json）",
        "通用 .env（OPENAI_API_KEY / OPENAI_API_BASE）"
    )

    private const val MAX_BYTES = 16 * 1024 * 1024
    private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 自动识别并解析。[fileName] 仅用于日志，不作为格式依据 —— 判定一律靠内容结构。
     */
    fun parse(bytes: ByteArray, fileName: String = ""): Parsed {
        if (bytes.isEmpty()) return Parsed.Failed("文件为空")
        if (bytes.size > MAX_BYTES) return Parsed.Failed("文件超过 16 MiB")

        // 1. ZIP（Cherry Studio 备份）
        if (looksLikeZip(bytes)) {
            return parseZip(bytes) ?: Parsed.Failed("ZIP 内未找到可识别的配置（Cherry Studio 备份应包含 data.json）")
        }

        val text = decodeText(bytes) ?: return Parsed.Failed("无法以 UTF-8 解码，可能不是文本配置文件")
        return parseText(text, fileName)
    }

    /** 文本形态：先试 JSON，再试 .env / INI。 */
    private fun parseText(text: String, fileName: String = ""): Parsed {
        val trimmed = text.trimStart()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            parseEnv(text)?.let { return Parsed.Ok(it) }
            return Parsed.Failed("无法识别该文本格式")
        }
        val root = runCatching { lenient.parseToJsonElement(text) }.getOrNull()
            ?: return Parsed.Failed("JSON 语法错误，无法解析")

        val list = buildList {
            addAll(parseChatbox(root))
            addAll(parseContinue(root))
            addAll(parseLobeChat(root))
            addAll(parseCcSwitch(root))
            addAll(parseNewApi(root))
            addAll(parseCherry(root))
        }.filter { it.apiKey.isNotBlank() || it.endpoint.isNotBlank() }

        if (list.isEmpty()) return Parsed.Failed("未识别出任何含 API Key 或端点的配置")
        return Parsed.Ok(dedupe(list))
    }

    // ------------------------------------------------------------- Chatbox

    /** `settings.apiHost` / `settings.apiKey` / `settings.models[].modelId` */
    private fun parseChatbox(root: JsonElement): List<Candidate> {
        val obj = root as? JsonObject ?: return emptyList()
        val settings = obj["settings"] as? JsonObject ?: obj
        val apiKey = settings.str("apiKey").ifBlank { settings.str("api_key") }
        val apiHost = settings.str("apiHost").ifBlank { settings.str("api_host") }
        // 必须是 Chatbox 特征字段，避免把其他格式误判
        if (obj["settings"] == null && settings["apiHost"] == null && settings["api_host"] == null) return emptyList()
        if (apiKey.isBlank() && apiHost.isBlank()) return emptyList()

        val models = mutableListOf<String>()
        (settings["models"] as? JsonArray)?.forEach { item ->
            val o = item as? JsonObject ?: return@forEach
            o.str("modelId").ifBlank { o.str("model") }.takeIf(String::isNotBlank)?.let(models::add)
        }
        if (models.isEmpty()) settings.str("model").takeIf(String::isNotBlank)?.let(models::add)

        return listOf(
            Candidate(
                name = settings.str("name").ifBlank { obj.str("name") }.ifBlank { "Chatbox 配置" },
                provider = "Chatbox",
                endpoint = apiHost,
                apiKey = apiKey,
                models = models,
                defaultModel = models.firstOrNull().orEmpty(),
                source = "Chatbox"
            )
        )
    }

    // ------------------------------------------------------------- Continue

    /** `models[].apiKey` / `apiBase` / `provider` / `model` */
    private fun parseContinue(root: JsonElement): List<Candidate> {
        val obj = root as? JsonObject ?: return emptyList()
        val models = obj["models"] as? JsonArray ?: return emptyList()
        return buildList {
            models.forEachIndexed { i, item ->
                val m = item as? JsonObject ?: return@forEachIndexed
                val apiKey = m.str("apiKey")
                val apiBase = m.str("apiBase").ifBlank { m.str("api_base") }
                if (apiKey.isBlank() && apiBase.isBlank()) return@forEachIndexed
                val model = m.str("model")
                add(
                    Candidate(
                        name = m.str("title").ifBlank { model.ifBlank { "Continue ${i + 1}" } },
                        provider = m.str("provider").ifBlank { "Continue" },
                        endpoint = apiBase,
                        apiKey = apiKey,
                        models = if (model.isBlank()) emptyList() else listOf(model),
                        defaultModel = model,
                        source = "Continue"
                    )
                )
            }
        }
    }

    // ------------------------------------------------------------- LobeChat

    /** `keyVaults.<provider>.apiKey` / `baseURL` */
    private fun parseLobeChat(root: JsonElement): List<Candidate> {
        val obj = root as? JsonObject ?: return emptyList()
        val vaults = obj["keyVaults"] as? JsonObject ?: return emptyList()
        return buildList {
            vaults.forEach { (key, value) ->
                val v = value as? JsonObject ?: return@forEach
                val apiKey = v.str("apiKey").ifBlank { v.str("api_key") }
                val baseURL = v.str("baseURL").ifBlank { v.str("baseUrl") }
                    .ifBlank { v.str("endpoint") }
                if (apiKey.isBlank() && baseURL.isBlank()) return@forEach
                add(
                    Candidate(
                        name = "LobeChat · $key",
                        provider = key,
                        endpoint = baseURL,
                        apiKey = apiKey,
                        source = "LobeChat"
                    )
                )
            }
        }
    }

    // ------------------------------------------------------------- CC Switch

    /** `env.ANTHROPIC_API_KEY` / `ANTHROPIC_BASE_URL` / `ANTHROPIC_MODEL` */
    private fun parseCcSwitch(root: JsonElement): List<Candidate> {
        val obj = root as? JsonObject ?: return emptyList()
        val env = obj["env"] as? JsonObject ?: return emptyList()
        val apiKey = env.str("ANTHROPIC_API_KEY").ifBlank { env.str("ANTHROPIC_AUTH_TOKEN") }
        val baseURL = env.str("ANTHROPIC_BASE_URL")
        val model = env.str("ANTHROPIC_MODEL")
        if (apiKey.isBlank() && baseURL.isBlank()) return emptyList()
        return listOf(
            Candidate(
                name = obj.str("name").ifBlank { "CC Switch 配置" },
                provider = "Anthropic",
                endpoint = baseURL,
                apiKey = apiKey,
                models = if (model.isBlank()) emptyList() else listOf(model),
                defaultModel = model,
                source = "CC Switch"
            )
        )
    }

    // ------------------------------------------------------------- New API / One API

    /** `key` / `base_url` / `models`(逗号分隔) / `type` / `group` */
    private fun parseNewApi(root: JsonElement): List<Candidate> {
        val obj = root as? JsonObject ?: return emptyList()
        val out = mutableListOf<Candidate>()
        singleChannel(obj)?.let { out += it }
        listOf("data", "channels", "items").forEach { key ->
            (obj[key] as? JsonArray)?.forEach { item ->
                (item as? JsonObject)?.let { singleChannel(it)?.let(out::add) }
            }
        }
        return out
    }

    private fun singleChannel(o: JsonObject): Candidate? {
        val key = o.str("key").ifBlank { o.str("api_key") }
        val baseUrl = o.str("base_url").ifBlank { o.str("baseUrl") }
        if (key.isBlank() || baseUrl.isBlank()) return null
        // 排除 Continue / Chatbox 的键组合，避免与前者重复
        if (o.containsKey("apiKey") || o.containsKey("apiBase")) return null
        val models = o.str("models").split(',').map(String::trim).filter(String::isNotEmpty)
        return Candidate(
            name = o.str("name").ifBlank { "New API 渠道" },
            provider = o.str("type").ifBlank { "New API" },
            endpoint = baseUrl,
            apiKey = key,
            models = models,
            defaultModel = models.firstOrNull().orEmpty(),
            notes = o.str("group"),
            source = "New API"
        )
    }

    // ------------------------------------------------------------- Cherry Studio

    /**
     * Cherry 的 data.json 内层是 redux 切片再 stringify 一次，层级很深。
     * 这里递归扫描，凡同时带 apiKey 与 baseUrl 的对象都视为一个 provider。
     */
    private fun parseCherry(root: JsonElement): List<Candidate> {
        val found = mutableListOf<Candidate>()
        walk(root, found)
        return found
    }

    private fun walk(node: JsonElement?, out: MutableList<Candidate>, depth: Int = 0) {
        if (depth > 8 || node == null) return
        when (node) {
            is JsonObject -> {
                val apiKey = node.str("apiKey").ifBlank { node.str("api_key") }
                val baseUrl = node.str("baseUrl").ifBlank { node.str("apiHost") }
                    .ifBlank { node.str("baseURL") }
                if (apiKey.isNotBlank() && baseUrl.isNotBlank()) {
                    val models = mutableListOf<String>()
                    (node["models"] as? JsonArray)?.forEach { m ->
                        when (m) {
                            is JsonObject -> m.str("id").ifBlank { m.str("modelId") }
                                .takeIf(String::isNotBlank)?.let(models::add)
                            is JsonPrimitive -> m.content.takeIf(String::isNotBlank)?.let(models::add)
                            else -> Unit
                        }
                    }
                    out += Candidate(
                        name = node.str("name").ifBlank { node.str("id").ifBlank { "Cherry 配置" } },
                        provider = node.str("type").ifBlank { "Cherry Studio" },
                        endpoint = baseUrl,
                        apiKey = apiKey,
                        models = models,
                        defaultModel = node.str("defaultModel").ifBlank { models.firstOrNull().orEmpty() },
                        source = "Cherry Studio"
                    )
                }
                node.values.forEach { v ->
                    // 内层可能是字符串化 JSON
                    if (v is JsonPrimitive && v.isString && v.content.trimStart().startsWith("{")) {
                        runCatching { lenient.parseToJsonElement(v.content) }.getOrNull()
                            ?.let { walk(it, out, depth + 1) }
                    } else {
                        walk(v, out, depth + 1)
                    }
                }
            }
            is JsonArray -> node.forEach { walk(it, out, depth + 1) }
            is JsonPrimitive -> if (node.isString && node.content.trimStart().startsWith("{")) {
                runCatching { lenient.parseToJsonElement(node.content) }.getOrNull()
                    ?.let { walk(it, out, depth + 1) }
            }
            else -> Unit
        }
    }

    // ------------------------------------------------------------- .env

    /** `OPENAI_API_KEY=` / `OPENAI_API_BASE=` / `ANTHROPIC_*` */
    private fun parseEnv(text: String): List<Candidate>? {
        val map = mutableMapOf<String, String>()
        text.lineSequence().forEach { line ->
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#") || t.startsWith(";")) return@forEach
            val eq = t.indexOf('=')
            if (eq <= 0) return@forEach
            val k = t.substring(0, eq).trim()
            val v = t.substring(eq + 1).trim().trim('"', '\'')
            if (k.isNotEmpty()) map[k] = v
        }
        val apiKey = map["OPENAI_API_KEY"] ?: map["ANTHROPIC_API_KEY"] ?: map["API_KEY"]
        val base = map["OPENAI_API_BASE"] ?: map["OPENAI_BASE_URL"]
            ?: map["ANTHROPIC_BASE_URL"] ?: map["BASE_URL"]
        if (apiKey.isNullOrBlank() && base.isNullOrBlank()) return null
        val model = map["OPENAI_MODEL"] ?: map["ANTHROPIC_MODEL"] ?: map["MODEL"]
        return listOf(
            Candidate(
                name = ".env 配置",
                provider = "",
                endpoint = base.orEmpty(),
                apiKey = apiKey.orEmpty(),
                models = if (model.isNullOrBlank()) emptyList() else listOf(model),
                defaultModel = model.orEmpty(),
                source = ".env"
            )
        )
    }

    // ------------------------------------------------------------- ZIP

    private fun looksLikeZip(bytes: ByteArray): Boolean =
        bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    /** 从 ZIP 中找 data.json（或任意 .json），按文本格式解析。 */
    private fun parseZip(bytes: ByteArray): Parsed? {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        var total = 0L
        val failure = runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
                var entry = zin.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val data = zin.readBytes()
                        total += data.size
                        // 防御 zip bomb
                        if (total > MAX_BYTES) error("ZIP 解压后超过 16 MiB")
                        entries += entry.name to data
                    }
                    zin.closeEntry()
                    entry = zin.nextEntry
                }
            }
        }.exceptionOrNull()
        if (failure != null) return Parsed.Failed(failure.message ?: "ZIP 解压失败")

        val entry = entries.firstOrNull { it.first.substringAfterLast('/').equals("data.json", true) }
            ?: entries.firstOrNull { it.first.endsWith(".json", true) }
            ?: return null

        val text = decodeText(entry.second) ?: return Parsed.Failed("data.json 无法解码")
        return parseText(text, entry.first)
    }

    // ------------------------------------------------------------- 工具

    private fun decodeText(bytes: ByteArray): String? = runCatching {
        // 去掉 UTF-8 BOM
        val start = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) 3 else 0
        String(bytes, start, bytes.size - start, Charsets.UTF_8)
    }.getOrNull()

    /** 安全取字符串：非字符串类型返回空串。 */
    private fun JsonObject.str(key: String): String {
        val v = this[key] ?: return ""
        if (v is JsonNull) return ""
        return (v as? JsonPrimitive)?.content.orEmpty()
    }

    /** 按 apiKey + endpoint 去重，保留先出现的。 */
    private fun dedupe(list: List<Candidate>): List<Candidate> {
        val seen = mutableSetOf<String>()
        return list.filter { seen.add(it.apiKey.trim() + "|" + it.endpoint.trim()) }
    }
}