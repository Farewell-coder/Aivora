package app.aivora.vault

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import app.aivora.vault.data.ServiceConfig
import app.aivora.vault.data.ThirdParty
import app.aivora.vault.data.VaultRepository
import app.aivora.vault.protocol.Aex
import app.aivora.vault.protocol.AexDeepLink
import app.aivora.vault.ui.kit.AivoraField
import app.aivora.vault.ui.kit.BlockHeader
import app.aivora.vault.ui.kit.ConfirmDialog
import app.aivora.vault.ui.kit.EmptyState
import app.aivora.vault.ui.kit.HintText
import app.aivora.vault.ui.kit.IconAction
import app.aivora.vault.ui.kit.ItemDivider
import app.aivora.vault.ui.kit.PrimaryButton
import app.aivora.vault.ui.kit.RowItem
import app.aivora.vault.ui.kit.SectionBlock
import app.aivora.vault.ui.kit.SectionLabel
import app.aivora.vault.ui.kit.SecondaryButton
import app.aivora.vault.ui.kit.SubtlePanel
import app.aivora.vault.ui.kit.TextAction
import app.aivora.vault.ui.theme.AivoraShape
import app.aivora.vault.ui.theme.AivoraSize
import app.aivora.vault.ui.theme.AivoraSpace
import app.aivora.vault.ui.theme.AivoraThemeColors
import app.aivora.vault.ui.theme.AivoraType
import kotlinx.coroutines.launch

/**
 * 服务配置页。
 *
 * 【结构】外壳（标题栏 / 状态栏让位 / 悬浮底栏）由 `ui.kit.AivoraScaffold` 统一提供，
 * 本页只负责一块铺满的 `LazyColumn`：顶部操作区与批量操作栏用 [SectionBlock]，
 * 配置条目用 [RowItem]（**不再每条包一张卡**），说明文字用 [SubtlePanel] / [HintText]。
 *
 * 【条目的交互】点行 = 编辑，长按 = 进入选择模式并勾上该条；行尾只有一个「更多」
 * [IconAction] 打开 [DropdownMenu]（导出 / 复制 Endpoint 与 Key / 删除）。
 * 选择模式下隐藏更多菜单，行首换成 Checkbox。
 *
 * 【状态位置】搜索词、选择模式、勾选集、待确认的删除 / 导出、深链选择、导入流程状态
 * **全部提在页面级 `remember`** —— 放进 LazyColumn 的 item 里，滚动出屏就会丢。
 *
 * 选择模式下：
 *  - 顶部「新建」「导入」隐藏，只留「取消」；
 *  - 每行只保留勾选框，隐藏更多菜单，避免误触；
 *  - 列表末尾出现批量操作栏（全选 = 当前筛选结果、批量导出、批量删除）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConfigPage(
    configs: List<ServiceConfig>,
    repo: VaultRepository,
    appVersion: String,
    endpointFix: Boolean,
    edit: (ServiceConfig) -> Unit,
    add: () -> Unit,
    onOpenSettings: () -> Unit,
    toast: (String) -> Unit,
) {
    val c = AivoraThemeColors.colors

    var search by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<ServiceConfig?>(null) }
    // 导出目标：单条是 size==1，批量是所选的全部。两者共用同一个弹窗。
    var exporting by remember { mutableStateOf<List<ServiceConfig>?>(null) }
    var selecting by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    // 当前展开「更多」菜单的条目 id：页面级，保证同一时刻只有一行的菜单是打开的。
    var menuFor by remember { mutableStateOf<Long?>(null) }

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

    val filtered = configs.filter { "${it.name} ${it.provider} ${it.endpoint} ${it.notes}".contains(search, true) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AivoraSpace.gutter,
            end = AivoraSpace.gutter,
            top = AivoraSpace.md,
            bottom = AivoraSpace.navSpace,
        ),
    ) {
        // ---------- 顶部操作区：页面主功能区块 ----------
        item {
            SectionBlock {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = AivoraSpace.blockPadding,
                            vertical = AivoraSpace.itemVertical,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(AivoraSpace.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selecting) {
                        // 选择模式下「新建」「导入」隐藏，只留「取消」。
                        HintText("点按勾选，长按进入选择", Modifier.weight(1f))
                        TextAction("取消", {
                            selecting = false
                            picked = emptySet()
                            menuFor = null
                        })
                    } else {
                        PrimaryButton("新建配置", add, Modifier.weight(1f), icon = Lucide.Plus)
                        SecondaryButton(
                            "导入",
                            { openLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                            Modifier.weight(1f),
                            icon = Lucide.Import,
                        )
                        TextAction("选择", {
                            selecting = true
                            menuFor = null
                        })
                    }
                }
            }
        }

        // ---------- 搜索（胶囊） ----------
        item {
            Column {
                Spacer(Modifier.height(AivoraSpace.sectionGap))
                AivoraField(
                    label = "搜索配置",
                    value = search,
                    onValueChange = { search = it },
                    placeholder = "名称、服务商或 Endpoint",
                    shape = AivoraShape.pill,
                    leadingIcon = {
                        Icon(Lucide.Search, null, Modifier.size(AivoraSize.iconButton), tint = c.textTertiary)
                    },
                    trailingIcon = {
                        if (search.isNotEmpty()) {
                            IconAction(Lucide.X, "清除搜索", { search = "" })
                        }
                    },
                )
            }
        }

        // ---------- 空状态 ----------
        if (filtered.isEmpty()) {
            item {
                Column {
                    Spacer(Modifier.height(AivoraSpace.sectionGap))
                    SectionBlock {
                        if (configs.isEmpty()) {
                            EmptyState(
                                icon = Lucide.Plus,
                                title = "还没有服务配置",
                                hint = "新建一条，或从别的软件导入",
                                action = { PrimaryButton("新建配置", add, icon = Lucide.Plus) },
                            )
                        } else {
                            EmptyState(
                                icon = Lucide.Search,
                                title = "没有匹配的配置",
                                hint = "换个关键词试试",
                            )
                        }
                    }
                }
            }
        } else {
            // ---------- 计数 ----------
            item {
                SectionLabel(
                    if (search.isBlank()) "全部配置 · ${filtered.size} 条"
                    else "筛出 ${filtered.size} / ${configs.size} 条",
                )
            }
        }

        // ---------- 配置条目：行，不是卡 ----------
        items(filtered, key = { it.id }) { config ->
            val isPicked = config.id in picked
            // 分隔线只画在行与行之间，最后一行不画。
            val last = config.id == filtered.lastOrNull()?.id
            // 条目增删 / 筛选时的平滑让位由 LazyColumn 托管，不额外引入依赖。
            Column(Modifier.animateItem()) {
                // 点击 / 长按包在外层：RowItem 自身只提供行内留白语义，
                // 长按进选择模式需要 combinedClickable，所以手势放在包住它的 Box 上。
                RowItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (selecting) {
                                    picked = if (isPicked) picked - config.id else picked + config.id
                                } else {
                                    edit(config)
                                }
                            },
                            onLongClick = {
                                // 长按任一配置进入选择模式，并把它勾上（与记录页行为一致）。
                                if (!selecting) {
                                    haptics.toggle()
                                    selecting = true
                                    picked = picked + config.id
                                    menuFor = null
                                }
                            },
                        ),
                    contentPadding = PaddingValues(
                        horizontal = AivoraSpace.xs,
                        vertical = AivoraSpace.itemVertical,
                    ),
                ) {
                        if (selecting) {
                            Checkbox(isPicked, { on ->
                                picked = if (on) picked + config.id else picked - config.id
                            })
                            Spacer(Modifier.width(AivoraSpace.sm))
                        } else {
                            // 首字母方块：给列表一个可扫读的锚点。
                            Box(
                                Modifier
                                    .size(AivoraSize.avatar)
                                    .clip(AivoraShape.chip)
                                    .background(c.accentContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    config.name.take(1).uppercase().ifBlank { "?" },
                                    style = AivoraType.bodyStrong,
                                    color = c.onAccentContainer,
                                )
                            }
                            Spacer(Modifier.width(AivoraSpace.md))
                        }

                        Column(Modifier.weight(1f)) {
                            Text(
                                config.name,
                                style = AivoraType.bodyStrong,
                                color = c.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(AivoraSpace.hair))
                            // 服务商 · Endpoint：技术数据走等宽，需要能逐字符比对。
                            Text(
                                config.provider.ifBlank { "未设服务商" } +
                                    " · " +
                                    config.endpoint.ifBlank { "未设置 Endpoint" },
                                style = AivoraType.mono,
                                color = c.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        // 选择模式下隐藏行尾菜单，避免误触。
                        if (!selecting) {
                            Box {
                                IconAction(
                                    Lucide.More,
                                    "更多操作",
                                    { menuFor = config.id },
                                    tint = c.textTertiary,
                                )
                                DropdownMenu(
                                    expanded = menuFor == config.id,
                                    onDismissRequest = { menuFor = null },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("导出", style = AivoraType.body, color = c.text) },
                                        leadingIcon = {
                                            Icon(
                                                Lucide.Export,
                                                null,
                                                Modifier.size(AivoraSize.iconButton),
                                                tint = c.textSecondary,
                                            )
                                        },
                                        onClick = {
                                            menuFor = null
                                            exporting = listOf(config)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "复制 Endpoint 与 Key",
                                                style = AivoraType.body,
                                                color = c.text,
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Lucide.Copy,
                                                null,
                                                Modifier.size(AivoraSize.iconButton),
                                                tint = c.textSecondary,
                                            )
                                        },
                                        onClick = {
                                            menuFor = null
                                            try {
                                                if (config.endpoint.isBlank() || config.encryptedKey.isBlank()) {
                                                    error("Endpoint 或 API Key 未填写")
                                                }
                                                clipboard.setText(
                                                    AnnotatedString(
                                                        config.endpoint + "\n" +
                                                            repo.crypto.decrypt(config.encryptedKey).trim(),
                                                    ),
                                                )
                                                toast("Endpoint 和 API Key 已复制")
                                            } catch (e: Exception) {
                                                toast(e.message ?: "配置不完整")
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("删除", style = AivoraType.body, color = c.danger) },
                                        leadingIcon = {
                                            Icon(
                                                Lucide.Trash,
                                                null,
                                                Modifier.size(AivoraSize.iconButton),
                                                tint = c.danger,
                                            )
                                        },
                                        onClick = {
                                            menuFor = null
                                            deleting = config
                                        },
                                    )
                                }
                            }
                        }
                }
                if (!last) ItemDivider()
            }
        }

        // ---------- 说明：弱化面板 ----------
        if (filtered.isNotEmpty() && !selecting) {
            item {
                Column {
                    Spacer(Modifier.height(AivoraSpace.lg))
                    SubtlePanel {
                        HintText("点按条目编辑，长按可多选。Endpoint 与 API Key 只保存在本机，导出时由凭证加密。")
                    }
                }
            }
        }

        // ---------- 选择模式操作栏 ----------
        if (selecting) {
            item {
                Column {
                    Spacer(Modifier.height(AivoraSpace.sectionGap))
                    val allPicked = filtered.isNotEmpty() && filtered.all { it.id in picked }
                    val somePicked = !allPicked && filtered.any { it.id in picked }
                    SectionBlock {
                        BlockHeader("批量操作", icon = Lucide.Check)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AivoraSpace.blockPadding, vertical = AivoraSpace.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TriStateCheckbox(
                                state = when {
                                    allPicked -> ToggleableState.On
                                    somePicked -> ToggleableState.Indeterminate
                                    else -> ToggleableState.Off
                                },
                                onClick = {
                                    // 全选 / 取消全选只作用于**当前筛选结果**，不动筛选之外的勾选。
                                    picked = if (allPicked) {
                                        picked - filtered.map { it.id }.toSet()
                                    } else {
                                        picked + filtered.map { it.id }.toSet()
                                    }
                                },
                            )
                            Text(
                                "已选 ${picked.size} / ${configs.size} 个配置",
                                Modifier.weight(1f),
                                style = AivoraType.meta,
                                color = c.textSecondary,
                            )
                            TextAction("清空", { picked = emptySet() })
                        }
                        ItemDivider()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = AivoraSpace.blockPadding,
                                    vertical = AivoraSpace.itemVertical,
                                ),
                            horizontalArrangement = Arrangement.spacedBy(AivoraSpace.sm),
                        ) {
                            SecondaryButton(
                                "导出所选",
                                {
                                    // 与单条导出走同一个弹窗：先显示凭证，再让你选「导出」或「分享」。
                                    val targets = configs.filter { it.id in picked }
                                    if (targets.isEmpty()) toast("请先勾选要导出的配置") else exporting = targets
                                },
                                Modifier.weight(1f),
                                icon = Lucide.Export,
                                enabled = picked.isNotEmpty(),
                            )
                            SecondaryButton(
                                "删除所选",
                                { confirmBulkDelete = true },
                                Modifier.weight(1f),
                                icon = Lucide.Trash,
                                enabled = picked.isNotEmpty(),
                                danger = true,
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- 单条删除确认
    deleting?.let { config ->
        ConfirmDialog(
            title = "删除服务配置",
            text = "确定删除「${config.name}」吗？此操作无法撤销。",
            confirmText = "确认删除",
            onConfirm = {
                scope.launch {
                    repo.dao.delete(config)
                    deleting = null
                    toast("配置已删除")
                }
            },
            onDismiss = { deleting = null },
            danger = true,
        )
    }

    // ---- 批量删除确认（只写数量，不列名称；不提示成功）
    if (confirmBulkDelete) {
        ConfirmDialog(
            title = "删除配置",
            text = "将删除 ${picked.size} 个配置，此操作无法撤销。",
            confirmText = "确认删除",
            onConfirm = {
                scope.launch {
                    val ids = picked
                    configs.filter { it.id in ids }.forEach { repo.dao.delete(it) }
                    confirmBulkDelete = false
                    selecting = false
                    picked = emptySet()
                }
            },
            onDismiss = { confirmBulkDelete = false },
            danger = true,
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
                    Column {
                        Text("包含配置", style = AivoraType.label, color = c.textTertiary)
                        targets.forEach {
                            Text("· ${it.name}", style = AivoraType.meta, color = c.textSecondary)
                        }
                    }
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
            toast = toast,
        )
    }

    // ---- 深链：选择目标软件（只列名称，无图标）
    deeplinkPicker?.let { receivers ->
        AlertDialog(
            onDismissRequest = { deeplinkPicker = null },
            title = { Text("选择接收软件", style = AivoraType.section, color = c.text) },
            text = {
                Column {
                    HintText("配置将以明文链接交给所选软件，不加密、不需要凭证，也不会记入导出记录。")
                    Spacer(Modifier.height(AivoraSpace.sm))
                    receivers.forEach { receiver ->
                        RowItem(
                            onClick = {
                                val source = deeplinkSource
                                deeplinkPicker = null
                                if (source == null) {
                                    toast("配置已不存在，请重新导出")
                                } else {
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
                            contentPadding = PaddingValues(
                                horizontal = AivoraSpace.xs,
                                vertical = AivoraSpace.itemVertical,
                            ),
                        ) {
                            Icon(
                                Lucide.Link,
                                null,
                                Modifier.size(AivoraSize.iconButton),
                                tint = c.textSecondary,
                            )
                            Spacer(Modifier.width(AivoraSpace.md))
                            Text(
                                receiver.displayName,
                                Modifier.weight(1f),
                                style = AivoraType.bodyStrong,
                                color = c.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextAction("取消", { deeplinkPicker = null }) },
            containerColor = c.surface,
            shape = AivoraShape.block,
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
            },
        )
    }

    // ---- 导入：第三方预览
    preview?.let { candidates ->
        ImportPreviewDialog(
            candidates = candidates,
            close = { importing = false; detected = null; preview = null },
            confirm = { items ->
                scope.launch {
                    val built = items.map { (candidate, name) -> ImportFlow.toConfig(candidate, name, repo) }
                    val count = ImportFlow.commit(repo, built, configs)
                    importing = false; detected = null; preview = null
                    toast("已导入 $count 条配置")
                }
            },
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