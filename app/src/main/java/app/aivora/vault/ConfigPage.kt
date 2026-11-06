package app.aivora.vault

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.state.ToggleableState
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
import app.aivora.vault.protocol.AexDeepLink
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

    // 深链接收方选择：非 null 表示正在选目标软件。
    var deeplinkPicker by remember { mutableStateOf<List<AexDeepLink.Receiver>?>(null) }
    // 待深链导出的配置（只有单条导出才会设置）。
    var deeplinkSource by remember { mutableStateOf<ServiceConfig?>(null) }

    // 导入流程状态
    var importing by remember { mutableStateOf(false) }
    var detected by remember { mutableStateOf<ImportFlow.Detected?>(null) }
    var importError by remember { mutableStateOf("") }
    var importBusy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<List<ThirdParty.Candidate>?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptics = rememberHaptics()

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
    val filtered = configs.filter { "${it.name} ${it.provider} ${it.endpoint} ${it.notes}".contains(search, true) }

    // 列表惰性化：只组合可见行，条目多时不再一次性构建全部卡片。
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Tokens.PagePadding,
            end = Tokens.PagePadding,
            top = 4.dp,
            bottom = Tokens.NavSpace,
        ),
    ) {
        item {
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
            AivoraField(
                label = "搜索配置",
                value = search,
                onValueChange = { search = it },
                placeholder = "名称、服务商或 Endpoint",
                shape = CircleShape,
                leadingIcon = { Icon(Lucide.Search, null) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton({ search = "" }) { Icon(Lucide.X, "清除搜索") }
                    }
                },
            )
            if (search.isNotEmpty()) {
                Text(
                    "筛出 ${filtered.size} / ${configs.size} 条",
                    Modifier.padding(start = 6.dp, top = 6.dp),
                    fontSize = 11.sp, lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        if (filtered.isEmpty()) {
            item {
                AppCard {
                    EmptyState(
                        icon = if (configs.isEmpty()) Lucide.Plus else Lucide.Search,
                        title = if (configs.isEmpty()) "还没有服务配置" else "没有匹配的配置",
                        hint = if (configs.isEmpty()) "新建一条，或从别的软件导入" else "换个关键词试试",
                        action = {
                            if (configs.isEmpty()) LabelButton(add, Lucide.Plus, "新建配置")
                        },
                    )
                }
            }
        }

        items(filtered, key = { it.id }) { c ->
            val isPicked = c.id in picked
            // 【v0.1.0·条目动画】增删 / 筛选时平滑让位，不再整屏闪跳。
            //   动画本身由 LazyColumn 托管，不额外引入任何依赖。
            Column(Modifier.animateItem()) {
            val cardModifier = if (selecting) Modifier.fillMaxWidth()
            else Modifier.fillMaxWidth().combinedClickable(
                onClick = { },
                onLongClick = {
                    // 长按任一配置进入选择模式，并把它勾上（与记录页行为一致）。
                    haptics.toggle()
                    selecting = true
                    picked = picked + c.id
                }
            )
            AppCard(variant = if (selecting && isPicked) CardVariant.Outlined else CardVariant.Muted) {
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
                            IconButton({ edit(c) }, Modifier.size(40.dp)) { Icon(Lucide.Edit, "编辑", Modifier.size(20.dp)) }; Spacer(Modifier.width(Tokens.GapXs))
                            IconButton({ exporting = listOf(c) }, Modifier.size(40.dp)) { Icon(Lucide.Export, "导出", Modifier.size(20.dp)) }; Spacer(Modifier.width(Tokens.GapXs))
                            IconButton({
                                try {
                                    if (c.endpoint.isBlank() || c.encryptedKey.isBlank()) error("Endpoint 或 API Key 未填写")
                                    clipboard.setText(AnnotatedString(c.endpoint + "\n" + repo.crypto.decrypt(c.encryptedKey).trim()))
                                    Toast.makeText(context, "Endpoint 和 API Key 已复制", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) { Toast.makeText(context, e.message ?: "配置不完整", Toast.LENGTH_SHORT).show() }
                            }, Modifier.size(40.dp)) { Icon(Lucide.Copy, "复制 Endpoint 和 API Key", Modifier.size(20.dp)) }; Spacer(Modifier.width(Tokens.GapXs))
                            IconButton({ deleting = c }, Modifier.size(40.dp)) { Icon(Lucide.Trash, "删除", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            }
        }

        // 选择模式底部操作栏（作为列表最后一项，随内容滚动）
        if (selecting) {
            item {
                AppCard(variant = CardVariant.Outlined) {
                    val allPicked = filtered.isNotEmpty() && filtered.all { it.id in picked }
                    val somePicked = !allPicked && filtered.any { it.id in picked }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TriStateCheckbox(
                            state = when {
                                allPicked -> ToggleableState.On
                                somePicked -> ToggleableState.Indeterminate
                                else -> ToggleableState.Off
                            },
                            onClick = {
                                picked = if (allPicked) picked - filtered.map { it.id }.toSet()
                                else picked + filtered.map { it.id }.toSet()
                            },
                        )
                        Text("已选 ${picked.size} / ${configs.size} 个配置", Modifier.weight(1f), fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton({ picked = emptySet() }) { Text("清空") }
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
                    Text("包含配置", fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                    targets.forEach { Text("· ${it.name}", fontSize = 12.sp, lineHeight = 18.sp) }
                }
            },
            // 深链只支持单条配置：批量导出时传 null，按钮不出现。
            onDeeplink = if (single) {
                {
                    val installed = AexDeepLink.availableReceivers(context)
                    if (installed.isEmpty()) {
                        toast("暂无支持深链传递的软件")
                    } else {
                        deeplinkSource = targets.first()
                        // 先关掉导出弹窗再弹选择框，避免两个对话框叠在一起。
                        exporting = null
                        deeplinkPicker = installed
                    }
                }
            } else null,
            repo = repo,
            appVersion = appVersion,
            close = { exporting = null },
            toast = toast
        )
    }

    // ---- 深链：选择目标软件（只列名称，无图标）
    deeplinkPicker?.let { receivers ->
        AlertDialog(
            onDismissRequest = { deeplinkPicker = null },
            title = { Text("选择接收软件") },
            text = {
                Column {
                    Text(
                        "配置将以明文链接交给所选软件，不加密、不需要凭证，也不会记入导出记录。",
                        fontSize = 12.sp, lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    receivers.forEach { receiver ->
                        Surface(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            TextButton(
                                {
                                    val source = deeplinkSource
                                    deeplinkPicker = null
                                    if (source == null) { toast("配置已不存在，请重新导出") }
                                    else {
                                        val profile = repo.toAex(source)
                                        if (AexDeepLink.launch(context, profile, receiver, appVersion)) {
                                            // 转后台：Aivora 的导出动作已经完成，不占着前台。
                                            exporting = null
                                            (context as? android.app.Activity)?.moveTaskToBack(true)
                                        } else {
                                            toast("暂无支持深链传递的软件")
                                        }
                                    }
                                },
                                Modifier.fillMaxWidth()
                            ) { Text(receiver.displayName, maxLines = 1) }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ deeplinkPicker = null }) { Text("取消") } }
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