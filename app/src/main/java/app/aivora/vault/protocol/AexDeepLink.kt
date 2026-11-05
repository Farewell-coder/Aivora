package app.aivora.vault.protocol

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.Base64

/**
 * AEX 深链（Deeplink）导出：1.1 引入的第二条传递通道。
 *
 * 与 `.akcp` 文件的根本差别：
 *  - **不加密**：链接里的 JSON 是明文，任何拿到链接的人都能读。
 *  - **不需要凭证**：没有 PBKDF2、没有 AES-GCM。
 *  - **不写导出记录**：Aivora 侧不留任何痕迹。
 *  - **仅单条配置**：一个链接承载一个 profile。
 *
 * 之所以可以明文，是因为它复用了用户**主动选择接收软件**这一动作：用户点「深链」后
 * 从列表里挑一个已适配的软件，链接只交给那一个应用，不经过剪贴板、聊天记录或网盘。
 * 这与 `.akcp` 的「文件可以随便传、凭证另行传递」是两套模型，文档里必须讲清楚。
 *
 * 链接格式（见 protocol/AEX-DEEPLINK.md）：
 *
 *     <scheme>://<host>?d=<urlsafe-base64(JSON), 无填充>&v=1
 *
 * 【包可见性】Android 11+ 若不在 AndroidManifest 里用 `<queries>` 声明目标 scheme，
 * [android.content.pm.PackageManager.queryIntentActivities] 会恒返回空列表，
 * 导致「装了 Dollhouse 却提示没有可用软件」。新增接收方时两处都要改：
 * 本文件的 [RECEIVERS]、manifest 的 `<queries>`。
 */
object AexDeepLink {

    /** 链接信封版本。与 AEX `protocolVersion` 独立演进。 */
    const val ENVELOPE_VERSION = 1

    /** 载荷种类标识，接收方必须先校验它。 */
    const val KIND = "aivora.provider"

    /** 链接携带的 AEX 协议版本。 */
    const val PROTOCOL_VERSION = "1.1"

    /**
     * 一个已适配的接收方。
     *
     * 新增软件只需在 [RECEIVERS] 里追加一条（并同步 manifest 的 `<queries>`），
     * 不必改动任何判定逻辑 —— 这是「可扩展」的落点。
     */
    data class Receiver(
        val displayName: String,
        val scheme: String,
        val host: String
    ) {
        /** 用于 `queryIntentActivities` 的探测 URI。 */
        fun probeUri(): Uri = Uri.Builder().scheme(scheme).authority(host).build()
    }

    /**
     * 已适配软件注册表。
     *
     * 只有真正实现了解析的软件才能登记在这里 —— 列表里出现却打不开，比不出现更糟。
     */
    val RECEIVERS: List<Receiver> = listOf(
        Receiver(displayName = "Dollhouse", scheme = "dollhouse", host = "aivora-import")
    )

    /** 已安装且可达的接收方。列表为空时调用方提示「暂无支持深链传递的软件」。 */
    fun availableReceivers(context: Context): List<Receiver> = RECEIVERS.filter { reachable(context, it) }

    /** 判定系统中是否存在能处理该 receiver 的应用。 */
    fun reachable(context: Context, receiver: Receiver): Boolean = runCatching {
        context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_VIEW, receiver.probeUri()), 0
        ).isNotEmpty()
    }.getOrDefault(false)

    /**
     * 构造明文 JSON。
     *
     * 不含 `models` / `defaultModel` —— 与 AEX 1.1 一致，接收方自行请求 /models。
     */
    fun buildPayloadJson(
        profile: Aex.Profile,
        exporterVersion: String,
        exportedAt: String = Instant.now().toString()
    ): String = buildJsonObject {
        put("kind", KIND)
        put("ver", ENVELOPE_VERSION)
        put("schema", "AEX")
        put("protocolVersion", PROTOCOL_VERSION)
        put("name", profile.name)
        if (profile.provider.isNotEmpty()) put("provider", profile.provider)
        put("baseUrl", profile.baseUrl)
        put("apiKey", profile.apiKey)
        if (profile.description.isNotEmpty()) put("description", profile.description)
        put("exportedAt", exportedAt)
        put("exporter", buildJsonObject {
            put("name", "Aivora")
            put("version", exporterVersion)
        })
    }.toString()

    /** 明文 JSON → 链接可用的 base64url（去掉 `=` 填充）。 */
    fun encodePayload(json: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(Charsets.UTF_8))

    /** 反向解码，供测试与文档示例核对使用。 */
    fun decodePayload(encoded: String): String {
        val padded = encoded.replace('-', '+').replace('_', '/')
            .let { it + "=".repeat((4 - it.length % 4) % 4) }
        return String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
    }

    /** 组装最终 URI 字符串。不依赖 Android，方便单测。 */
    fun buildLinkString(receiver: Receiver, encoded: String): String =
        "${receiver.scheme}://${receiver.host}?d=$encoded&v=$ENVELOPE_VERSION"

    fun buildUri(profile: Aex.Profile, receiver: Receiver, exporterVersion: String): Uri =
        Uri.parse(buildLinkString(receiver, encodePayload(buildPayloadJson(profile, exporterVersion))))

    /**
     * 交给系统打开目标软件。
     *
     * 只做 ACTION_VIEW 投递，不弹 Sharesheet —— 接收方是用户在列表里**显式选中**的，
     * 再让他选一次没有意义。返回 false 表示系统里已经没有能处理该 scheme 的应用
     * （例如用户刚卸载），调用方应提示「暂无支持深链传递的软件」。
     */
    fun launch(context: Context, profile: Aex.Profile, receiver: Receiver, exporterVersion: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, buildUri(profile, receiver, exporterVersion))
        return runCatching {
            if (context.packageManager.queryIntentActivities(intent, 0).isEmpty()) return false
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /** 仅用于调试与文档示例：把链接还原成可读 JSON（它本来就是明文）。 */
    fun inspect(link: String): String? = runCatching {
        val d = Uri.parse(link).getQueryParameter("d") ?: return null
        decodePayload(d)
    }.getOrNull()
}