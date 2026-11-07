package app.aivora.vault

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import app.aivora.vault.data.ExportRecord
import app.aivora.vault.data.ServiceConfig
import app.aivora.vault.data.VaultRepository
import app.aivora.vault.protocol.Aex
import app.aivora.vault.protocol.AexShare
import app.aivora.vault.ui.kit.ConfirmDialog
import app.aivora.vault.ui.kit.EmptyState
import app.aivora.vault.ui.kit.HintText
import app.aivora.vault.ui.kit.ItemDivider
import app.aivora.vault.ui.kit.PrimaryButton
import app.aivora.vault.ui.kit.RowItem
import app.aivora.vault.ui.kit.SecondaryButton
import app.aivora.vault.ui.kit.SectionBlock
import app.aivora.vault.ui.kit.SectionLabel
import app.aivora.vault.ui.kit.StatusBadge
import app.aivora.vault.ui.kit.StatusKind
import app.aivora.vault.ui.kit.TechWell
import app.aivora.vault.ui.kit.TextAction
import app.aivora.vault.ui.kit.WarningHint
import app.aivora.vault.ui.theme.AivoraShape
import app.aivora.vault.ui.theme.AivoraSize
import app.aivora.vault.ui.theme.AivoraSpace
import app.aivora.vault.ui.theme.AivoraThemeColors
import app.aivora.vault.ui.theme.AivoraType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.text.DateFormat
import java.util.Date

/**
 * 导出记录页。
 *
 * 一条记录 = 一次导出动作。记录里保存**当时的凭证**与**完整快照**（含 API Key，主密钥加密），
 * 因此「重新导出」能复刻当初那份文件，而不是用配置的当前状态 —— 配置后来被改被删都不影响。
 *
 * 【为什么重写】旧实现把「文件名 / 条数 / 配置名 / 原配置已删除」压成四行 11sp 灰字，
 * 「原配置已删除」和普通时间戳是同一个颜色，用户扫不出来；详情是 AlertDialog，
 * 52 字符凭证在窄框里折行、还不能横向选。现在：
 *  - 列表 = LazyColumn + sticky 日期组头 + 行（RowItem 视觉）+ 右侧箭头；
 *  - 「原配置已删除」是 [StatusBadge]（Warning），颜色 + 状态点两个通道；
 *  - 详情 = ModalBottomSheet，凭证走 [TechWell]（等宽、可选中、复制按钮显眼）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordsPage(
    records: List<ExportRecord>,
    configs: List<ServiceConfig>,
    repo: VaultRepository,
    appVersion: String,
    toast: (String) -> Unit,
) {
    val c = AivoraThemeColors.colors
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    // 页面级状态：选择模式、选中集合、详情、删除确认、导出进行中。
    var selecting by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var detail by remember { mutableStateOf<ExportRecord?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    // 分组数据必须在 LazyColumn 之外算好：LazyListScope 的 content 不是 @Composable。
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val grouped = records.groupBy { df.format(Date(it.exportedAt)) }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = AivoraSpace.gutter,
            end = AivoraSpace.gutter,
            top = AivoraSpace.sm,
            bottom = AivoraSpace.navSpace,
        ),
    ) {
        // 顶部：总数 + 选择入口
        item(key = "records-header") {
            Row(
                Modifier.fillMaxWidth().height(AivoraSize.buttonSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "共 ${records.size} 条导出记录",
                    Modifier.weight(1f),
                    style = AivoraType.meta,
                    color = c.textSecondary,
                )
                if (records.isNotEmpty()) {
                    TextAction(
                        text = if (selecting) "取消" else "选择",
                        onClick = {
                            selecting = !selecting
                            if (!selecting) picked = emptySet()
                        },
                    )
                }
            }
        }

        if (records.isEmpty()) {
            item(key = "records-empty") {
                SectionBlock {
                    EmptyState(
                        icon = Lucide.History,
                        title = "还没有导出记录",
                        hint = "导出一次配置后，记录会连凭证与快照一起留在这里",
                    )
                }
            }
        }

        grouped.forEach { (day, dayItems) ->
            stickyHeader(key = "day-$day") {
                // 组头贴在列表顶部时要盖住下面滚上来的内容，所以显式铺页面底色。
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(c.background),
                ) {
                    SectionLabel("$day · ${dayItems.size} 条")
                }
            }
            items(dayItems, key = { it.id }) { record ->
                // 注意：这里不能用 remember —— LazyColumn 的 item 滚出屏幕会被回收，
                // 状态放进来就丢。解析在每次组合时即时算，成本很低。
                val names = parseNames(record.configNames)
                val missing = names.isNotEmpty() && names.all { n -> configs.none { it.name == n } }
                val isPicked = record.id in picked
                RecordRow(
                    fileName = record.fileName,
                    meta = "${record.configCount} 个配置 · ${timeFormat.format(Date(record.exportedAt))}",
                    names = names,
                    missing = missing,
                    selecting = selecting,
                    picked = isPicked,
                    onTogglePick = { picked = picked.toggle(record.id) },
                    onOpen = { if (selecting) picked = picked.toggle(record.id) else detail = record },
                    onLongPress = {
                        haptics.toggle()
                        selecting = true
                        picked = picked + record.id
                    },
                )
            }
        }

        // 选择模式操作栏：全选 / 清空 + 已选计数 + 删除所选。
        if (selecting) {
            item(key = "records-selection-bar") {
                SectionBlock(Modifier.padding(top = AivoraSpace.md)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AivoraSpace.blockPadding, vertical = AivoraSpace.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val allPicked = records.isNotEmpty() && records.all { it.id in picked }
                        TextAction(
                            text = if (allPicked) "取消全选" else "全选",
                            onClick = {
                                picked = if (allPicked) picked - records.map { it.id }.toSet()
                                else picked + records.map { it.id }.toSet()
                            },
                        )
                        TextAction(
                            text = "清空",
                            onClick = { picked = emptySet() },
                            enabled = picked.isNotEmpty(),
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "已选 ${picked.size}/${records.size}",
                            style = AivoraType.monoSmall,
                            color = c.textSecondary,
                        )
                    }
                    ItemDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AivoraSpace.blockPadding, vertical = AivoraSpace.md),
                    ) {
                        SecondaryButton(
                            text = "删除所选",
                            onClick = { confirmDelete = true },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Lucide.Trash,
                            enabled = picked.isNotEmpty(),
                            danger = true,
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "删除导出记录",
            text = "将删除 ${picked.size} 条记录，此操作无法撤销。",
            confirmText = "确认删除",
            onConfirm = {
                scope.launch {
                    val ids = picked
                    withContext(Dispatchers.IO) { records.filter { it.id in ids }.forEach { repo.dao.delete(it) } }
                    confirmDelete = false
                    selecting = false
                    picked = emptySet()
                }
            },
            onDismiss = { confirmDelete = false },
            danger = true,
        )
    }

    detail?.let { record ->
        RecordDetailSheet(
            record = record,
            repo = repo,
            appVersion = appVersion,
            busy = busy,
            onBusy = { busy = it },
            onDismiss = { detail = null },
            toast = toast,
        )
    }
}

/**
 * 一条记录的列表行。
 *
 * 用 [RowItem] 承载行内留白与 chip 圆角，手势（点击 / 长按）由外层的 `combinedClickable`
 * 提供 —— 这样列表行与配置页保持同一套视觉，同时保留「长按进选择模式」的既有交互。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordRow(
    fileName: String,
    meta: String,
    names: List<String>,
    missing: Boolean,
    selecting: Boolean,
    picked: Boolean,
    onTogglePick: () -> Unit,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    val c = AivoraThemeColors.colors
    RowItem(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
    ) {
        if (selecting) {
            Checkbox(checked = picked, onCheckedChange = { onTogglePick() })
            Spacer(Modifier.width(AivoraSpace.xs))
        }
        Column(Modifier.weight(1f)) {
            Text(
                fileName,
                style = AivoraType.bodyStrong,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(AivoraSpace.hair))
            Text(meta, style = AivoraType.meta, color = c.textTertiary)
            if (names.isNotEmpty()) {
                Spacer(Modifier.height(AivoraSpace.hair))
                Text(
                    names.joinToString("、"),
                    style = AivoraType.meta,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (missing) {
                // 「原配置已删除」是状态，不是备注：给小胶囊 + 状态点，扫一眼就能看见。
                Spacer(Modifier.height(AivoraSpace.xs))
                StatusBadge(StatusKind.Warning, "原配置已删除")
            }
        }
        if (!selecting) {
            Icon(Lucide.ChevronRight, null, Modifier.size(AivoraSize.iconButton), tint = c.textTertiary)
        }
    }
}

/**
 * 记录详情弹层。
 *
 * 【不可变快照】这里的 profile 一律由 `decrypt(record.encryptedSnapshot)` → [Aex.fromSnapshot]
 * 还原，重新导出 / 重新分享都用 `record` 里保存的**当时那个凭证**重新加密一遍，
 * 因此产出与最初那次导出逐字节等价；不会去读配置的当前状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordDetailSheet(
    record: ExportRecord,
    repo: VaultRepository,
    appVersion: String,
    busy: Boolean,
    onBusy: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    toast: (String) -> Unit,
) {
    val c = AivoraThemeColors.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val credential = remember(record.id) {
        runCatching { repo.crypto.decrypt(record.encryptedCredential) }.getOrNull().orEmpty()
    }
    val snapshot = remember(record.id) {
        record.encryptedSnapshot?.let { enc -> runCatching { repo.crypto.decrypt(enc) }.getOrNull() }
    }
    val profiles = remember(record.id) { snapshot?.let { Aex.fromSnapshot(it) }.orEmpty() }
    val names = parseNames(record.configNames)

    var pendingBytes by remember(record.id) { mutableStateOf<ByteArray?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Aex.MIME_TYPE)) { uri ->
        val data = pendingBytes; pendingBytes = null
        if (uri == null || data == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(data) } ?: error("无法写入文件")
                }
                onDismiss()
                toast("已导出 .akcp 文件")
            } catch (e: Exception) { toast(e.message ?: "导出失败") }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
        containerColor = c.surface,
        shape = AivoraShape.sheet,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = AivoraSpace.gutter)
                .padding(bottom = AivoraSpace.xxl),
        ) {
            Text(record.fileName, style = AivoraType.section, color = c.text)
            Spacer(Modifier.height(AivoraSpace.hair))
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(record.exportedAt)),
                style = AivoraType.meta,
                color = c.textTertiary,
            )

            Spacer(Modifier.height(AivoraSpace.lg))
            Text("包含配置（${names.size} 项）", style = AivoraType.label, color = c.textTertiary)
            Spacer(Modifier.height(AivoraSpace.xs))
            if (names.isEmpty()) {
                HintText("这条记录没有保存配置名清单。")
            } else {
                names.forEach { name ->
                    Text(
                        "· $name",
                        Modifier.padding(vertical = AivoraSpace.hair),
                        style = AivoraType.body,
                        color = c.text,
                    )
                }
            }

            Spacer(Modifier.height(AivoraSpace.lg))
            // 凭证是一等公民：等宽、可选中、复制按钮就在它右边。
            TechWell(
                value = credential.ifBlank { "—" },
                leadingLabel = "导出凭证（当时生成，接收方需要）",
                // 复制由 TechWell 自己完成，这里只负责反馈；凭证为空时不给复制入口。
                onCopy = if (credential.isBlank()) null else ({ toast("凭证已复制") }),
            )

            if (snapshot == null) {
                Spacer(Modifier.height(AivoraSpace.md))
                WarningHint("早期记录：未保存快照，无法重新导出。")
            }

            if (snapshot != null && profiles.isNotEmpty()) {
                Spacer(Modifier.height(AivoraSpace.lg))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AivoraSpace.sm),
                ) {
                    PrimaryButton(
                        text = "重新导出",
                        onClick = {
                            scope.launch {
                                if (busy) return@launch
                                onBusy(true)
                                try {
                                    val data = withContext(Dispatchers.Default) { Aex.export(profiles, appVersion, credential) }
                                    pendingBytes = data.bytes
                                    saveLauncher.launch(record.fileName)
                                } catch (e: Exception) { toast(e.message ?: "导出失败") } finally { onBusy(false) }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        icon = Lucide.Export,
                        enabled = !busy,
                        loading = busy,
                    )
                    SecondaryButton(
                        text = "重新分享",
                        onClick = {
                            scope.launch {
                                if (busy) return@launch
                                onBusy(true)
                                try {
                                    val data = withContext(Dispatchers.Default) { Aex.export(profiles, appVersion, credential) }
                                    val prepared = AexShare.prepare(context, data, record.fileName, "Aivora 配置（${profiles.size} 项）")
                                        .getOrElse { error(it.message ?: "无法生成临时文件") }
                                    // 与导出弹窗一致：不用 canShare() 硬拦，交给系统 chooser；
                                    // 真的没有接收方时 startActivity 会抛 ActivityNotFoundException。
                                    try {
                                        context.startActivity(prepared.first)
                                    } catch (_: android.content.ActivityNotFoundException) {
                                        error("没有可接收分享的应用")
                                    }
                                    onDismiss()
                                } catch (e: Exception) { toast(e.message ?: "分享失败") } finally { onBusy(false) }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        icon = Lucide.Share,
                        enabled = !busy,
                    )
                }
                Spacer(Modifier.height(AivoraSpace.sm))
                HintText("重新导出使用当时的完整快照与当时的凭证，内容与最初那次完全一致。")
            }

            Spacer(Modifier.height(AivoraSpace.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextAction("关闭", onClick = onDismiss, enabled = !busy)
            }
        }
    }
}

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

/** 解析 configNames JSON 数组字符串。 */
private fun parseNames(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        Json.parseToJsonElement(raw).jsonArray.mapNotNull { it.jsonPrimitive.content.takeIf(String::isNotBlank) }
    }.getOrDefault(emptyList())
}