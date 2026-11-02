package app.aivora.vault

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aivora.vault.data.ExportRecord
import app.aivora.vault.data.ServiceConfig
import app.aivora.vault.data.ThirdParty
import app.aivora.vault.data.VaultRepository
import app.aivora.vault.protocol.Aex
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * 服务配置页。
 *
 * 选择模式下的约定：
 *  - 顶部「新建」「导入」隐藏，只留「取消」；
 *  - 每条卡片只保留勾选框，隐藏编辑/导出/复制/删除，避免误触；
 *  - 底部固定操作栏替换导航栏，含全选、批量导出、批量删除。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ConfigPage(
    configs: List<ServiceConfig>,
    repo: VaultRepository,
    appVersion: String,
    endpointFix: Boolean,
    edit: (ServiceConfig) -> Unit,
    add: () -> Unit,
    navigate: (Page) -> Unit,
    toast: (String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<ServiceConfig?>(null) }
    // 导出目标：单条是 size==1，批量是所选的全部。两者共用同一个弹窗。
    var exporting by remember { mutableStateOf<List<ServiceConfig>?>(null) }
    var selecting by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmBulkDelete by remember { mutableStateOf(false) }

    // 导入流程状态
    var importing by remember { mutableStateOf(false) }
    var detected by remember { mutableStateOf<ImportFlow.Detected?>(null) }
    var importError by remember { mutableStateOf("") }
    var importBusy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<List<ThirdParty.Candidate>?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                importing = true; importBusy = true; importError = ""; detected = null; preview = null
                val name = context.displayName(uri)
                detected = ImportFlow.detect(context, uri, name)
                importBusy = false
            }
        }
    }

    // 顶部操作：选择模式下隐藏「新建」「导入」
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!selecting) {
            LabelButton(add, Lucide.Plus, "新建配置", Modifier.weight(1f))
            LabelButton({ openLauncher.launch(arrayOf("application/octet-stream", "*/*")) }, Lucide.Import, "导入", Modifier.weight(1f))
        }
        LabelButton(
            { selecting = !selecting; if (!selecting) picked = emptySet() },
            Lucide.Check,
            if (selecting) "取消" else "选择",
            Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        search, { search = it }, Modifier.fillMaxWidth(),
        leadingIcon = { Icon(Lucide.Search, null) },
        placeholder = { Text("搜索名称、服务商或 Endpoint") },
        singleLine = true, shape = CircleShape
    )
    Spacer(Modifier.height(14.dp))

    val filtered = configs.filter { "${it.name} ${it.provider} ${it.endpoint} ${it.notes}".contains(search, true) }

    if (filtered.isEmpty()) {
        AppCard { Empty("还没有匹配的服务配置") }
    } else filtered.forEach { c ->
        val cardModifier = if (selecting) Modifier.fillMaxWidth()
        else Modifier.fillMaxWidth().combinedClickable(
            onClick = { },
            onLongClick = {
                // 长按任一配置进入选择模式，并把它勾上（与记录页行为一致）。
                selecting = true
                picked = picked + c.id
            }
        )
        AppCard {
            Column(cardModifier) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (selecting) {
                        Checkbox(c.id in picked, { on -> picked = if (on) picked + c.id else picked - c.id })
                        Spacer(Modifier.width(4.dp))
                    }
                    Box(Modifier.weight(1f).clickable(enabled = selecting) {
                        picked = if (c.id in picked) picked - c.id else picked + c.id
                    }) { ConfigSummary(c) }
                }
                // 选择模式下隐藏单条操作
                if (!selecting) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ edit(c) }, Modifier.size(40.dp)) { Icon(Lucide.Edit, "编辑", Modifier.size(20.dp)) }; Spacer(Modifier.width(4.dp))
                    IconButton({ exporting = listOf(c) }, Modifier.size(40.dp)) { Icon(Lucide.Export, "导出", Modifier.size(20.dp)) }; Spacer(Modifier.width(4.dp))
                    IconButton({
                        try {
                            if (c.endpoint.isBlank() || c.encryptedKey.isBlank()) error("Endpoint 或 API Key 未填写")
                            clipboard.setText(AnnotatedString(c.endpoint + "\n" + repo.crypto.decrypt(c.encryptedKey).trim()))
                            Toast.makeText(context, "Endpoint 和 API Key 已复制", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) { Toast.makeText(context, e.message ?: "配置不完整", Toast.LENGTH_SHORT).show() }
                    }, Modifier.size(40.dp)) { Icon(Lucide.Copy, "复制 Endpoint 和 API Key", Modifier.size(20.dp)) }; Spacer(Modifier.width(4.dp))
                    IconButton({ deleting = c }, Modifier.size(40.dp)) { Icon(Lucide.Trash, "删除", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    // 选择模式底部固定操作栏
    if (selecting) {
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("已选 ${picked.size} / ${configs.size} 个配置", Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton({
                    picked = if (picked.size == configs.size) emptySet() else configs.map { it.id }.toSet()
                }) { Text(if (picked.size == configs.size && configs.isNotEmpty()) "取消全选" else "全选") }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabelButton({
                    // 与单条导出走同一个弹窗：先显示凭证，再让你选「导出」或「分享」。
                    val targets = configs.filter { it.id in picked }
                    if (targets.isEmpty()) toast("请先勾选要导出的配置") else exporting = targets
                }, Lucide.Export, "导出所选", Modifier.weight(1f), enabled = picked.isNotEmpty())
                OutlinedButton(
                    { confirmBulkDelete = true },
                    Modifier.weight(1f),
                    enabled = picked.isNotEmpty(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = .7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Lucide.Trash, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text("删除所选", maxLines = 1)
                }
            }
        }
    }

    // ---- 单条删除确认
    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除服务配置") },
            text = { Text("确定删除「${c.name}」吗？此操作无法撤销。") },
            confirmButton = {
                TextButton({ scope.launch { repo.dao.delete(c); deleting = null; toast("配置已删除") } }) { Text("确认删除") }
            },
            dismissButton = { TextButton({ deleting = null }) { Text("取消") } }
        )
    }

    // ---- 批量删除确认（只写数量，不列名称；不提示成功）
    if (confirmBulkDelete) {
        AlertDialog(
            onDismissRequest = { confirmBulkDelete = false },
            title = { Text("删除配置") },
            text = { Text("将删除 ${picked.size} 个配置，此操作无法撤销。") },
            confirmButton = {
                TextButton({
                    scope.launch {
                        val ids = picked
                        configs.filter { it.id in ids }.forEach { repo.dao.delete(it) }
                        confirmBulkDelete = false
                        selecting = false
                        picked = emptySet()
                    }
                }) { Text("确认删除") }
            },
            dismissButton = { TextButton({ confirmBulkDelete = false }) { Text("取消") } }
        )
    }

    exporting?.let { targets ->
        val single = targets.size == 1
        val fileName = if (single) Aex.sanitizeFileName(targets.first().name)
        else "Aivora-${targets.size}-configs" + Aex.EXTENSION
        ExportSheet(
            title = if (single) "导出「${targets.first().name}」" else "导出 ${targets.size} 个配置",
            fileName = fileName,
            profiles = remember(targets) { targets.map { repo.toAex(it) } },
            header = if (single) null else {
                {
                    Text("包含配置", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    targets.forEach { Text("· ${it.name}", fontSize = 12.sp) }
                }
            },
            repo = repo,
            appVersion = appVersion,
            close = { exporting = null },
            toast = toast
        )
    }

    // ---- 导入：凭证对话框（AEX / 旧 AKCP）
    val enc = detected as? ImportFlow.Detected.Encrypted
    if (enc != null) {
        CredentialDialog(
            fileName = enc.fileName,
            busy = importBusy,
            error = importError,
            close = { importing = false; detected = null; importError = "" },
            confirm = { credential ->
                scope.launch {
                    importBusy = true; importError = ""
                    try {
                        val opened = ImportFlow.openEncrypted(enc.bytes, credential, enc.legacy, repo)
                        val count = ImportFlow.commit(repo, opened, configs)
                        importing = false; detected = null
                        toast("已导入 $count 条配置")
                    } catch (e: Exception) {
                        importError = e.message ?: "凭证错误或文件无效"
                    } finally { importBusy = false }
                }
            }
        )
    }

    // ---- 导入：第三方预览
    preview?.let { candidates ->
        ImportPreviewDialog(
            candidates = candidates,
            close = { importing = false; detected = null; preview = null },
            confirm = { items ->
                scope.launch {
                    val built = items.map { (c, name) -> ImportFlow.toConfig(c, name, repo) }
                    val count = ImportFlow.commit(repo, built, configs)
                    importing = false; detected = null; preview = null
                    toast("已导入 $count 条配置")
                }
            }
        )
    }

    // 识别完成后分流
    LaunchedEffect(detected) {
        val d = detected
        if (d is ImportFlow.Detected.Plain) preview = d.candidates
    }

    // ---- 导入：失败提示
    val failed = detected as? ImportFlow.Detected.Failed
    if (failed != null) {
        ImportFailedDialog(failed.reason, failed.showSupported) {
            importing = false; detected = null
        }
    }
}

/** 读取文件显示名，读不到时返回空串。 */
private fun android.content.Context.displayName(uri: Uri): String =
    contentResolver.query(uri, null, null, null, null)?.use { cur ->
        if (cur.moveToFirst()) {
            val index = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cur.getString(index) else ""
        } else ""
    } ?: ""