package app.aivora.vault

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aivora.vault.data.ExportRecord
import app.aivora.vault.data.VaultRepository
import app.aivora.vault.protocol.Aex
import app.aivora.vault.protocol.AexShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 统一的导出弹窗。
 *
 * 三处共用：配置卡片的单条导出、选择模式的批量导出、导出记录的重新导出。
 * 三者的界面完全一致 —— 都先显示凭证，再给「导出」与「分享」两个入口。
 *
 * 唯一差别在凭证来源：
 *  - [existingCredential] 为 null：生成新凭证，并在实际导出时写一条导出记录（单条 / 批量）
 *  - [existingCredential] 非 null：复用历史凭证，不新增记录（记录页重新导出）
 *
 * 之前批量导出直接调 `shareProfiles()` 弹分享面板、不显示凭证，用户拿不到密钥，
 * 这个函数取代了那条路径。
 *
 * 【深链按钮】[onDeeplink] 非 null 才显示，且只在单条导出时传入（批量模式下传 null）。
 * 它被刻意放在「复制凭证」与「导出/分享」之间独立成行：深链是「发链接」，
 * 底下两颗是「发文件」，两套模型混在一行会让人以为深链也是加密文件。
 */
@Composable
fun ExportSheet(
    title: String,
    fileName: String,
    profiles: List<Aex.Profile>,
    existingCredential: String? = null,
    header: (@Composable () -> Unit)? = null,
    /** 非 null 时显示「深链」按钮；仅单条导出传入。 */
    onDeeplink: (() -> Unit)? = null,
    repo: VaultRepository,
    appVersion: String,
    close: () -> Unit,
    toast: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var working by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var exported by remember { mutableStateOf<Aex.Export?>(null) }
    var pendingSave by remember { mutableStateOf<ByteArray?>(null) }

    // 派生 600000 次 PBKDF2 需要几百毫秒，放后台线程算，UI 先显示进度。
    LaunchedEffect(fileName, existingCredential, profiles) {
        exported = null
        if (profiles.isEmpty()) return@LaunchedEffect
        generating = true
        try {
            exported = withContext(Dispatchers.Default) {
                runCatching {
                    if (existingCredential == null) Aex.export(profiles, appVersion)
                    else Aex.export(profiles, appVersion, existingCredential)
                }.getOrNull()
            }
        } finally { generating = false }
    }

    val displayCredential = exported?.credential
    val safeName = remember(fileName) { Aex.sanitizeFileName(fileName) }
    val ready = exported != null && !generating

    /** 生成记录。仅新导出（existingCredential == null）时写库；取消分享则不写。 */
    fun recordIfNew(credential: String) {
        if (existingCredential != null) return
        if (credential.isBlank()) return
        // 快照含明文 API Key，落库前必须加密。
        val snapshot = runCatching { Aex.snapshot(profiles) }.getOrNull() ?: return
        val names = runCatching {
            kotlinx.serialization.json.buildJsonArray {
                profiles.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.name)) }
            }.toString()
        }.getOrDefault("[]")
        val row = ExportRecord(
            fileName = safeName,
            encryptedCredential = repo.crypto.encrypt(credential),
            configCount = profiles.size,
            configNames = names,
            encryptedSnapshot = repo.crypto.encrypt(snapshot)
        )
        scope.launch { withContext(Dispatchers.IO) { repo.dao.recordExport(row) } }
    }

    // 「导出」= 用户自选位置保存到本地。
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Aex.MIME_TYPE)) { uri ->
        val bytes = pendingSave; pendingSave = null
        if (uri == null || bytes == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("无法写入文件")
                }
                recordIfNew(displayCredential.orEmpty())
                close(); toast("已导出 .akcp 文件")
            } catch (e: Exception) { toast(e.message ?: "导出失败") }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!working) close() },
        title = { Text(title) },
        text = {
            Column {
                header?.let {
                    it()
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    "保存到本机，或直接分享给其他软件。接收方必须输入下面的凭证才能读取配置。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Surface(
                    Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    SelectionContainer {
                        Text(
                            displayCredential ?: if (generating) "正在生成凭证…" else "无法生成，请检查配置内容",
                            Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("文件已加密，凭证单独传递；不会随文件一起发出。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("该文件包含完整 API Key，接收应用解密后可读取明文，请仅分享给可信应用。", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                LabelButton(
                    {
                        val value = displayCredential
                        if (value.isNullOrBlank()) toast("凭证尚未生成") else {
                            clipboard.setText(AnnotatedString(value)); toast("凭证已复制")
                        }
                    },
                    Lucide.Copy, "复制凭证"
                )
                // 深链：只发链接、不加密、不写记录。单独一行，与下面的「发文件」区分开。
                if (onDeeplink != null) {
                    Spacer(Modifier.height(8.dp))
                    LabelButton(
                        { if (!working && ready) onDeeplink() },
                        Lucide.Link, "深链",
                        Modifier.fillMaxWidth(),
                        enabled = ready && !working
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 导出：写入用户自选位置
                    Button(
                        {
                            scope.launch {
                                if (working) return@launch
                                try {
                                    val data = exported ?: error("导出内容生成失败")
                                    working = true; pendingSave = data.bytes; saveLauncher.launch(safeName)
                                } catch (e: Exception) { toast(e.message ?: "导出失败") } finally { working = false }
                            }
                        },
                        Modifier.weight(1f), enabled = ready && !working
                    ) {
                        if (generating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else { Icon(Lucide.Export, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("导出") }
                    }
                    // 分享：交给系统分享面板
                    Button(
                        {
                            scope.launch {
                                if (working) return@launch
                                working = true
                                try {
                                    val data = exported ?: error("导出内容生成失败")
                                    if (!AexShare.canShare(context)) error("没有可接收分享的应用")
                                    val prepared = AexShare.prepare(context, data, safeName, title)
                                        .getOrElse { error(it.message ?: "无法生成临时文件") }
                                    recordIfNew(data.credential)
                                    context.startActivity(prepared.first)
                                    close(); toast("已生成 .akcp 文件，请选择接收应用")
                                } catch (e: Exception) { toast(e.message ?: "分享失败") } finally { working = false }
                            }
                        },
                        Modifier.weight(1f), enabled = ready && !working
                    ) {
                        if (working || generating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else { Icon(Lucide.Share, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("分享") }
                    }
                }
            }
        },
        confirmButton = { TextButton({ close() }, enabled = !working) { Text("关闭") } },
        dismissButton = null
    )
}