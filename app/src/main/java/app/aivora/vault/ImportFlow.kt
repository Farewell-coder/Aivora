package app.aivora.vault

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aivora.vault.data.ServiceConfig
import app.aivora.vault.data.ThirdParty
import app.aivora.vault.data.VaultRepository
import app.aivora.vault.protocol.Aex
import app.aivora.vault.protocol.Akcp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 导入流程：识别 → 分流 → 预览 → 写入。
 *
 * 分流规则（按内容判断，不看扩展名）：
 *  - `Aex.detect() == AEX`     → AEX 1.0 容器，必须输入凭证
 *  - `Aex.detect() == LEGACY`  → 旧 AKCP 容器，必须输入凭证
 *  - 其余                      → 第三方明文格式，按各自规则解析，多数无需凭证
 */
object ImportFlow {

    /** 识别阶段结果。 */
    sealed class Detected {
        /** AEX 1.0 或旧 AKCP：需要凭证。[legacy] 为 true 时走 [Akcp.import]。 */
        data class Encrypted(val bytes: ByteArray, val fileName: String, val legacy: Boolean) : Detected()
        /** 第三方格式：已解析出候选，等待用户在预览页勾选。 */
        data class Plain(val candidates: List<ThirdParty.Candidate>) : Detected()
        /** 识别或解析失败。 */
        data class Failed(val reason: String, val showSupported: Boolean = true) : Detected()
    }

    /** 只读文件并识别格式，不写库。 */
    suspend fun detect(context: Context, uri: Uri, fileName: String): Detected = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext Detected.Failed("文件读取失败", showSupported = false)

        if (bytes.isEmpty()) return@withContext Detected.Failed("文件为空", showSupported = false)
        if (bytes.size > Aex.MAX_FILE_SIZE) return@withContext Detected.Failed("文件超过 16 MiB", showSupported = false)

        when (Aex.detect(bytes)) {
            Aex.Detected.AEX -> Detected.Encrypted(bytes, fileName, legacy = false)
            Aex.Detected.LEGACY -> Detected.Encrypted(bytes, fileName, legacy = true)
            Aex.Detected.UNKNOWN -> when (val parsed = ThirdParty.parse(bytes, fileName)) {
                is ThirdParty.Parsed.Ok -> Detected.Plain(parsed.candidates)
                is ThirdParty.Parsed.Failed -> Detected.Failed(parsed.reason)
            }
        }
    }

    /** 用凭证解密 AEX / 旧 AKCP 容器，返回可落库的配置。 */
    suspend fun openEncrypted(
        bytes: ByteArray,
        credential: String,
        legacy: Boolean,
        repo: VaultRepository
    ): List<ServiceConfig> = withContext(Dispatchers.IO) {
        if (legacy) {
            Akcp.import(bytes, credential).map { repo.fromAkcp(it) }
        } else {
            Aex.import(bytes, credential).map { profile ->
                ServiceConfig(
                    name = profile.name,
                    provider = profile.provider,
                    // AEX 允许空 Key（仅交换端点信息），空字符串直接落到空字段。
                    encryptedKey = if (profile.apiKey.isBlank()) "" else repo.crypto.encrypt(profile.apiKey),
                    endpoint = profile.baseUrl,
                    models = profile.models.joinToString(","),
                    defaultModel = profile.defaultModel,
                    notes = profile.description,
                    extensionsJson = profile.extra.toString(),
                    source = "AEX"
                )
            }
        }
    }

    /** 第三方候选转成可落库配置，[source] 写入角标字段。 */
    fun toConfig(candidate: ThirdParty.Candidate, name: String, repo: VaultRepository): ServiceConfig =
        ServiceConfig(
            name = name.ifBlank { candidate.name },
            provider = candidate.provider,
            encryptedKey = if (candidate.apiKey.isBlank()) "" else repo.crypto.encrypt(candidate.apiKey),
            endpoint = app.aivora.vault.data.Endpoint.normalize(candidate.endpoint).endpoint,
            models = candidate.models.joinToString(","),
            defaultModel = candidate.defaultModel,
            notes = candidate.notes,
            source = candidate.source
        )

    /** 写入前按 name + endpoint 去重：同名同端点视为同一条，覆盖而不是叠加。 */
    suspend fun commit(
        repo: VaultRepository,
        configs: List<ServiceConfig>,
        existing: List<ServiceConfig>
    ) = withContext(Dispatchers.IO) {
        var count = 0
        configs.forEach { item ->
            existing.firstOrNull { it.name == item.name && it.endpoint == item.endpoint }
                ?.let { repo.dao.delete(it) }
            repo.dao.save(item)
            count++
        }
        count
    }
}

/**
 * 导入预览对话框。
 *
 * 第三方格式解析完后必须先经过这里：用户能看到将要导入哪些条目、来源是什么，
 * 并可以取消勾选误识别的项或改名。AEX 容器不经过此页（凭证验证通过即导入）。
 */
@Composable
fun ImportPreviewDialog(
    candidates: List<ThirdParty.Candidate>,
    close: () -> Unit,
    confirm: (List<Pair<ThirdParty.Candidate, String>>) -> Unit
) {
    // 默认全选；名称可编辑。
    var picked by remember { mutableStateOf(candidates.indices.toSet()) }
    val names = remember { mutableStateListOf<String>().apply { candidates.forEach { add(it.name) } } }
    val scroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = close,
        title = { Text("导入预览") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(scroll)) {
                Text(
                    "识别到 ${candidates.size} 条可导入配置，勾选后写入。",
                    fontSize = 12.sp, lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ picked = if (picked.size == candidates.size) emptySet() else candidates.indices.toSet() }) {
                        Text(if (picked.size == candidates.size) "取消全选" else "全选")
                    }
                    Spacer(Modifier.weight(1f))
                    Text("已选 ${picked.size}/${candidates.size}", fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                candidates.forEachIndexed { index, c ->
                    val checked = index in picked
                    Surface(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked, { on -> picked = if (on) picked + index else picked - index })
                                Column(Modifier.weight(1f)) {
                                    Text(names[index], fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        c.endpoint.ifBlank { "（无端点）" },
                                        fontSize = 11.sp, lineHeight = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = .12f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        c.source,
                                        Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        fontSize = 10.sp, lineHeight = 15.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (c.apiKey.isBlank()) "无密钥" else "含密钥",
                                    fontSize = 10.sp, lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                {
                    confirm(
                        picked.sorted().mapNotNull { i ->
                                candidates.getOrNull(i)?.let { c -> c to (names.getOrNull(i) ?: c.name) }
                        }
                    )
                },
                enabled = picked.isNotEmpty()
            ) { Text("导入 ${picked.size} 条") }
        },
        dismissButton = { TextButton(close) { Text("取消") } }
    )
}

/** AEX / 旧 AKCP 的凭证输入框。失败时把错误原因显示在下方。 */
@Composable
fun CredentialDialog(
    fileName: String,
    busy: Boolean,
    error: String,
    close: () -> Unit,
    confirm: (String) -> Unit
) {
    var credential by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) close() },
        title = { Text("输入导入凭证") },
        text = {
            Column {
                Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    credential,
                    { credential = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("导出凭证") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = error.isNotEmpty()
                )
                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(error, fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button({ if (credential.isBlank()) error else confirm(credential) }, enabled = !busy) {
                Text(if (busy) "验证中…" else "导入")
            }
        },
        dismissButton = { TextButton(close, enabled = !busy) { Text("取消") } }
    )
}

/** 识别失败提示：列出支持的格式，避免用户只看到「无法识别」。 */
@Composable
fun ImportFailedDialog(reason: String, showSupported: Boolean, close: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text("无法导入") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Text(reason, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, lineHeight = 19.sp)
                if (showSupported) {
                    Spacer(Modifier.height(14.dp))
                    Text("支持以下格式：", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("· Aivora 导出文件（.akcp，需要凭证）", fontSize = 12.sp, lineHeight = 18.sp)
                    ThirdParty.SUPPORTED.forEach { Text("· $it", fontSize = 12.sp, lineHeight = 18.sp) }
                }
            }
        },
        confirmButton = { TextButton(close) { Text("知道了") } }
    )
}