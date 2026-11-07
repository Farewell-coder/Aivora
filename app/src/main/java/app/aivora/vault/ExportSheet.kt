package app.aivora.vault

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.aivora.vault.data.ExportRecord
import app.aivora.vault.data.VaultRepository
import app.aivora.vault.protocol.Aex
import app.aivora.vault.protocol.AexShare
import app.aivora.vault.ui.kit.DangerHint
import app.aivora.vault.ui.kit.HintText
import app.aivora.vault.ui.kit.PrimaryButton
import app.aivora.vault.ui.kit.SecondaryButton
import app.aivora.vault.ui.kit.TechWell
import app.aivora.vault.ui.kit.TextAction
import app.aivora.vault.ui.theme.AivoraShape
import app.aivora.vault.ui.theme.AivoraSpace
import app.aivora.vault.ui.theme.AivoraThemeColors
import app.aivora.vault.ui.theme.AivoraType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 统一的导出弹层。
 *
 * 三处共用：配置卡片的单条导出、选择模式的批量导出、导出记录的重新导出。
 * 三者的界面完全一致 —— 都**先显示凭证**，再给「导出」与「分享」两个入口。
 *
 * 唯一差别在凭证来源：
 *  - [existingCredential] 为 null：生成新凭证，并在实际导出时写一条导出记录（单条 / 批量）
 *  - [existingCredential] 非 null：复用历史凭证，不新增记录（记录页重新导出）
 *
 * 【为什么从 AlertDialog 换成 ModalBottomSheet】凭证是一段 52 字符的 Base32，
 * AlertDialog 的宽度里它会折成三行、还不能横向选；底部弹层给它整幅宽度，
 * 并且「先看凭证、再动手」的顺序在弹层里更自然。
 *
 * 【深链按钮】[onDeeplink] 非 null 才显示，且只在单条导出时传入（批量模式下传 null）。
 * 它被刻意放在「复制凭证」与「导出/分享」之间独立成行：深链是「发链接」，
 * 底下两颗是「发文件」，两套模型混在一行会让人以为深链也是加密文件。
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val c = AivoraThemeColors.colors
    val context = LocalContext.current
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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        // 生成中 / 写入中不允许下滑关闭：底部弹层一滑就没了，用户会以为文件已经发出去了。
        onDismissRequest = { if (!working) close() },
        sheetState = sheetState,
        containerColor = c.surface,
        shape = AivoraShape.sheet,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AivoraSpace.gutter)
                .padding(bottom = AivoraSpace.xxl),
        ) {
            Text(title, style = AivoraType.section, color = c.text)
            Spacer(Modifier.height(AivoraSpace.md))

            header?.let {
                it()
                Spacer(Modifier.height(AivoraSpace.md))
            }

            HintText("保存到本机，或直接分享给其他软件。接收方必须输入下面的凭证才能读取配置。")
            Spacer(Modifier.height(AivoraSpace.md))

            // 凭证是一等公民：等宽、可选中、复制按钮就在旁边。
            if (displayCredential != null) {
                TechWell(
                    value = displayCredential,
                    leadingLabel = "导出凭证（接收方需要）",
                    onCopy = { toast("凭证已复制") },
                )
            } else {
                TechWell(
                    value = if (generating) "正在生成凭证…" else "无法生成，请检查配置内容",
                    leadingLabel = "导出凭证（接收方需要）",
                )
            }

            Spacer(Modifier.height(AivoraSpace.md))
            HintText("文件已加密，凭证单独传递；不会随文件一起发出。")
            Spacer(Modifier.height(AivoraSpace.sm))
            DangerHint("该文件包含完整 API Key，接收应用解密后可读取明文，请仅分享给可信应用。")

            // 深链：只发链接、不加密、不写记录。单独一行，与下面的「发文件」区分开。
            if (onDeeplink != null) {
                Spacer(Modifier.height(AivoraSpace.md))
                SecondaryButton(
                    text = "深链",
                    onClick = { if (!working && ready) onDeeplink() },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Lucide.Link,
                    enabled = ready && !working,
                )
                Spacer(Modifier.height(AivoraSpace.xs))
                HintText("深链以链接形式传递同一份配置，不产生文件，也不写入导出记录。")
            }

            Spacer(Modifier.height(AivoraSpace.lg))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AivoraSpace.sm),
            ) {
                // 导出：写入用户自选位置
                PrimaryButton(
                    text = "导出",
                    onClick = {
                        scope.launch {
                            if (working) return@launch
                            try {
                                val data = exported ?: error("导出内容生成失败")
                                working = true; pendingSave = data.bytes; saveLauncher.launch(safeName)
                            } catch (e: Exception) { toast(e.message ?: "导出失败") } finally { working = false }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    icon = if (generating) null else Lucide.Export,
                    enabled = ready && !working,
                    loading = generating,
                )
                // 分享：交给系统分享面板
                SecondaryButton(
                    text = "分享",
                    onClick = {
                        scope.launch {
                            if (working) return@launch
                            working = true
                            try {
                                val data = exported ?: error("导出内容生成失败")
                                val prepared = AexShare.prepare(context, data, safeName, title)
                                    .getOrElse { error(it.message ?: "无法生成临时文件") }
                                // 不用 canShare() 硬拦：包可见性下它看不到「保存到文件」、蓝牙等
                                // 系统接收方，误判会让分享按钮点不动。直接投递，让系统 chooser 决定；
                                // 真的没有接收方时 startActivity 会抛 ActivityNotFoundException。
                                try {
                                    context.startActivity(prepared.first)
                                } catch (_: android.content.ActivityNotFoundException) {
                                    error("没有可接收分享的应用")
                                }
                                recordIfNew(data.credential)
                                close(); toast("已生成 .akcp 文件，请选择接收应用")
                            } catch (e: Exception) { toast(e.message ?: "分享失败") } finally { working = false }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    icon = Lucide.Share,
                    enabled = ready && !working,
                )
            }
            Spacer(Modifier.height(AivoraSpace.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextAction("关闭", onClick = close, enabled = !working)
            }
        }
    }
}