package app.aivora.vault.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.aivora.vault.Lucide
import app.aivora.vault.data.KeyDiagnostics
import app.aivora.vault.data.ModelResult
import app.aivora.vault.data.ServiceConfig
import app.aivora.vault.data.VaultRepository
import app.aivora.vault.ui.kit.DataRow
import app.aivora.vault.ui.kit.EmptyState
import app.aivora.vault.ui.kit.HintText
import app.aivora.vault.ui.kit.ItemDivider
import app.aivora.vault.ui.kit.RowItem
import app.aivora.vault.ui.kit.SecondaryButton
import app.aivora.vault.ui.kit.SectionBlock
import app.aivora.vault.ui.kit.StatusBadge
import app.aivora.vault.ui.kit.StatusKind
import app.aivora.vault.ui.kit.SubtlePanel
import app.aivora.vault.ui.theme.AivoraMotion
import app.aivora.vault.ui.theme.AivoraSize
import app.aivora.vault.ui.theme.AivoraSpace
import app.aivora.vault.ui.theme.AivoraThemeColors
import app.aivora.vault.ui.theme.AivoraType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.DateFormat
import java.util.Date

/**
 * 密钥检测页。
 *
 * 【为什么重写】旧实现把「配置 → 检测状态 → 模型结果」三层信息压成一个 AppCard 里的
 * 一串 11/12sp 灰字：「可用 / 不可用 / 等待检测」只能靠读字分辨，HTTP 码与耗时夹在
 * 同一行里对不齐，检测过程中也看不出做到第几个模型。
 *
 * 现在：
 *  - 每个配置 = 一个 [SectionBlock]，头部一行是配置名 + 服务商 + 整体状态 [StatusBadge]；
 *  - 展开后每个模型 = 一行 [RowItem]，模型名走等宽，右侧状态徽标自带颜色 + 实心/空心点两个通道；
 *  - HTTP 码走 [DataRow]（等宽），耗时走 [AivoraType.monoSmall] 右对齐；
 *  - 错误摘要收进 [SubtlePanel]（dangerContainer），不再和正常信息混在一行；
 *  - 检测中显示 LinearProgressIndicator + 「已检测 n/N」等宽计数，进度可见。
 *
 * 业务逻辑与旧实现逐行等价：串行 forEachIndexed 循环、ensureActive 中断、persist 落库、
 * 停止与单项重测、CancellationException 分支全部原样保留。所有状态都提在页面级 remember。
 */
@Composable
fun DiagnosticsPage(
    configs: List<ServiceConfig>,
    repo: VaultRepository,
    appVersion: String,
    toast: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val checker = remember(appVersion) { KeyDiagnostics(appVersion) }
    var job by remember { mutableStateOf<Job?>(null) }
    var currentId by remember { mutableStateOf<Long?>(null) }
    var results by remember { mutableStateOf(mapOf<Long, List<ModelResult>>()) }
    var expanded by remember { mutableStateOf(setOf<Long>()) }

    LaunchedEffect(configs) {
        if (job?.isActive != true) results = configs.associate {
            it.id to try {
                Json.decodeFromString<List<ModelResult>>(it.modelResultsJson)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    suspend fun persist(c: ServiceConfig, status: String, items: List<ModelResult>) {
        repo.dao.save(
            (repo.dao.byId(c.id) ?: c).copy(
                checkStatus = status,
                lastCheckedAt = System.currentTimeMillis(),
                modelResultsJson = Json.encodeToString(items),
            )
        )
    }

    suspend fun diagnose(c: ServiceConfig) {
        currentId = c.id
        if (c.endpoint.isBlank() || c.encryptedKey.isBlank()) {
            repo.dao.save(c.copy(checkStatus = "请先补充 Endpoint 和 API Key")); toast("请先补充 Endpoint 和 API Key"); return
        }
        try {
            val key = repo.crypto.decrypt(c.encryptedKey); val modelIds = checker.fetchModels(c, key)
            val initial = modelIds.map { ModelResult(it, "等待检测") }; results = results + (c.id to initial)
            repo.dao.save(c.copy(models = modelIds.joinToString(","), checkStatus = "已成功拉取模型", modelResultsJson = Json.encodeToString(initial)))
            modelIds.forEachIndexed { index, model ->
                currentCoroutineContext().ensureActive()
                results = results + (c.id to results.getValue(c.id).mapIndexed { i, r -> if (i == index) r.copy(status = "检测中") else r })
                val result = checker.testModel(c, key, model)
                val updated = results.getValue(c.id).mapIndexed { i, r -> if (i == index) result else r }
                results = results + (c.id to updated); persist(c, "已检测 ${index + 1}/${modelIds.size}", updated)
            }
            persist(c, "检测完成：${results.getValue(c.id).count { it.status == "可用" }}/${modelIds.size} 个模型可用", results.getValue(c.id))
        } catch (e: CancellationException) { repo.dao.save((repo.dao.byId(c.id) ?: c).copy(checkStatus = "检测已停止")); throw e }
        catch (e: Exception) { repo.dao.save((repo.dao.byId(c.id) ?: c).copy(checkStatus = e.message ?: "检测失败", lastCheckedAt = System.currentTimeMillis())) }
    }

    fun start(c: ServiceConfig) { job = scope.launch { diagnose(c); currentId = null; job = null } }
    fun stop() { job?.cancel(); job = null; currentId = null }
    fun toggle(id: Long) { expanded = expanded.toMutableSet().apply { if (!add(id)) remove(id) } }
    fun retest(c: ServiceConfig, r: ModelResult) {
        job = scope.launch {
            try {
                val updated = checker.testModel(c, repo.crypto.decrypt(c.encryptedKey), r.name)
                val list = results.getValue(c.id).map { if (it.name == r.name) updated else it }
                results = results + (c.id to list)
                repo.dao.byId(c.id)?.let { repo.dao.save(it.copy(modelResultsJson = Json.encodeToString(list))) }
            } finally { job = null }
        }
    }

    if (configs.isEmpty()) {
        Column(Modifier.padding(horizontal = AivoraSpace.gutter, vertical = AivoraSpace.sm)) {
            SectionBlock {
                EmptyState(
                    icon = Lucide.Activity,
                    title = "暂无可检测配置",
                    hint = "先在「服务配置」里添加一条配置，再回来逐项检测密钥可用性",
                )
            }
            Spacer(Modifier.height(AivoraSpace.lg))
            HintText("仅在点击后联网，串行检测该配置的全部模型；可能消耗少量 API 额度。")
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = AivoraSpace.gutter,
            end = AivoraSpace.gutter,
            top = AivoraSpace.sm,
            bottom = AivoraSpace.navSpace,
        ),
        verticalArrangement = Arrangement.spacedBy(AivoraSpace.sectionGap),
    ) {
        items(configs, key = { it.id }) { config ->
            val running = job?.isActive == true
            DiagnosticsBlock(
                config = config,
                items = results[config.id].orEmpty(),
                expanded = config.id in expanded,
                running = running,
                isCurrent = currentId == config.id && running,
                onToggle = { toggle(config.id) },
                onStart = { expanded += config.id; start(config) },
                onStop = { stop() },
                onRetest = { result -> retest(config, result) },
            )
        }
        item(key = "diagnostics-footer") {
            Column {
                HintText("仅在点击后联网，串行检测该配置的全部模型；可能消耗少量 API 额度。")
                Spacer(Modifier.height(AivoraSpace.sm))
                HintText(
                    if (job?.isActive == true) "正在检测，完成前不能切换其它配置；可随时停止。"
                    else "检测结果会写回该配置，下次打开无需重新拉取模型列表。"
                )
            }
        }
    }
}

/**
 * 单个配置的检测区块。
 *
 * 整体状态用 [StatusBadge] 表达（颜色 + 状态点），完整状态文案仍在展开区里给出一行，
 * 因为 checkStatus 里带着「n/N 个模型可用」这类无法压进徽标的信息。
 */
@Composable
private fun DiagnosticsBlock(
    config: ServiceConfig,
    items: List<ModelResult>,
    expanded: Boolean,
    running: Boolean,
    isCurrent: Boolean,
    onToggle: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRetest: (ModelResult) -> Unit,
) {
    val c = AivoraThemeColors.colors
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = AivoraMotion.STANDARD_MS),
        label = "diagnostics-chevron",
    )
    val done = items.count { it.status == "可用" || it.status == "不可用" || it.status.contains("超时") }
    val total = items.size

    SectionBlock {
        RowItem(onClick = onToggle) {
            Column(Modifier.weight(1f)) {
                Text(config.name, style = AivoraType.bodyStrong,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(AivoraSpace.hair))
                Text(
                    buildString {
                        if (config.provider.isNotBlank()) append(config.provider)
                        if (config.endpoint.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(config.endpoint)
                        }
                        if (isEmpty()) append("未填写 Endpoint")
                    },
                    style = AivoraType.mono,
                    color = c.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(AivoraSpace.md))
            // 正在检测时优先显示「检测中 / 已检测 n/N」，否则按持久化状态判定。
            if (isCurrent) {
                StatusBadge(StatusKind.Info, if (total > 0) "已检测 $done/$total" else "检测中")
            } else {
                StatusBadge(configStatusKind(config.checkStatus), configStatusLabel(config.checkStatus))
            }
            Spacer(Modifier.width(AivoraSpace.xs))
            Icon(
                Lucide.ChevronDown,
                "展开或收起",
                Modifier.size(AivoraSize.iconButton).rotate(chevron),
                tint = c.textTertiary,
            )
        }

        if (expanded) {
            ItemDivider()
            Column(Modifier.padding(horizontal = AivoraSpace.blockPadding, vertical = AivoraSpace.md)) {
                Text(config.checkStatus.ifBlank { "未检测" }, style = AivoraType.meta,
                    color = if (configStatusKind(config.checkStatus) == StatusKind.Danger) c.danger else c.textSecondary,
                )
                Spacer(Modifier.height(AivoraSpace.hair))
                Text(
                    "检测时间：" + if (config.lastCheckedAt > 0) {
                        DateFormat.getDateTimeInstance().format(Date(config.lastCheckedAt))
                    } else {
                        "未检测"
                    },
                    style = AivoraType.meta,
                    color = c.textTertiary,
                )
                if (isCurrent && total > 0) {
                    Spacer(Modifier.height(AivoraSpace.md))
                    LinearProgressIndicator(
                        progress = { done.toFloat() / total.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = c.accent,
                        trackColor = c.subtle,
                    )
                    Spacer(Modifier.height(AivoraSpace.xs))
                    Text("已检测 $done/$total", style = AivoraType.monoSmall,
                        color = c.textSecondary,
                    )
                }
                Spacer(Modifier.height(AivoraSpace.md))
                Row(horizontalArrangement = Arrangement.spacedBy(AivoraSpace.sm)) {
                    SecondaryButton(
                        text = if (isCurrent) "检测中" else "检测",
                        onClick = onStart,
                        icon = Lucide.Play,
                        enabled = !running,
                    )
                    if (isCurrent) {
                        SecondaryButton(text = "停止", onClick = onStop, icon = Lucide.Stop)
                    }
                }
            }

            if (items.isEmpty()) {
                Column(Modifier.padding(horizontal = AivoraSpace.blockPadding)) {
                    HintText("点击检测后自动拉取并展示全部模型")
                }
                Spacer(Modifier.height(AivoraSpace.md))
            } else {
                ItemDivider()
                items.forEachIndexed { index, result ->
                    if (index > 0) ItemDivider()
                    ModelDiagnosticRow(
                        result = result,
                        running = running,
                        onRetest = { onRetest(result) },
                    )
                }
            }
        }
    }
}

/** 单个模型的结果行：模型名等宽 + 状态徽标 + HTTP 码 / 耗时 + 错误摘要 + 单项重测。 */
@Composable
private fun ModelDiagnosticRow(
    result: ModelResult,
    running: Boolean,
    onRetest: () -> Unit,
) {
    val c = AivoraThemeColors.colors
    // 一条 RowItem 承载整条模型信息：首行是「模型名 + 状态徽标」，
    // 下面依次是 HTTP 码、耗时、错误摘要与单项重测，靠行内留白分层而不是再包一层卡。
    RowItem {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    result.name,
                    Modifier.weight(1f),
                    style = AivoraType.mono,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(AivoraSpace.sm))
                StatusBadge(modelStatusKind(result.status), result.status)
            }
            Spacer(Modifier.height(AivoraSpace.xs))
            DataRow(
                label = "HTTP 码",
                value = result.httpStatus?.toString() ?: "—",
                emphasize = result.status == "不可用",
                valueColor = if (result.status == "不可用") c.danger else null,
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = AivoraSpace.hair),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    "耗时",
                    Modifier.weight(1f),
                    style = AivoraType.meta,
                    color = c.textTertiary,
                )
                Text(
                    result.latencyMs?.let { "$it ms" } ?: "— ms",
                    style = AivoraType.monoSmall,
                    color = if (result.status == "不可用") c.danger else c.textSecondary,
                    textAlign = TextAlign.End,
                )
            }
            if (result.detail.isNotEmpty()) {
                Spacer(Modifier.height(AivoraSpace.xs))
                if (result.status == "不可用") {
                    // 错误摘要收进 dangerContainer：失败原因与正常响应在视觉上必须分开。
                    SubtlePanel(color = c.dangerContainer) {
                        Text(result.detail, style = AivoraType.meta, color = c.onDangerContainer)
                    }
                } else {
                    Text(result.detail, style = AivoraType.meta, color = c.textTertiary)
                }
            }
            if (result.status == "不可用") {
                Spacer(Modifier.height(AivoraSpace.sm))
                SecondaryButton(
                    text = "重测此模型",
                    onClick = onRetest,
                    icon = Lucide.Refresh,
                    enabled = !running,
                )
            }
        }
    }
}

/** 模型状态 → 语义状态：可用=Success / 不可用=Danger / 检测中=Info / 等待检测=Neutral / 超时=Warning。 */
private fun modelStatusKind(status: String): StatusKind = when {
    status == "可用" -> StatusKind.Success
    status == "不可用" -> StatusKind.Danger
    status == "检测中" -> StatusKind.Info
    status.contains("超时") -> StatusKind.Warning
    else -> StatusKind.Neutral
}

/**
 * 配置整体状态 → 语义状态。
 *
 * checkStatus 是持久化的自由文本（可能是服务商返回的错误），这里只做前缀判定，
 * 判不出来时按失败处理 —— 让用户先注意到异常，而不是被一个中性的灰标签掩护过去。
 */
private fun configStatusKind(status: String): StatusKind {
    val text = status.trim()
    return when {
        text.isEmpty() || text == "未检测" -> StatusKind.Neutral
        text.startsWith("检测完成") -> {
            val available = COMPLETED_PATTERN.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            if (available > 0) StatusKind.Success else StatusKind.Danger
        }
        text.startsWith("检测已停止") -> StatusKind.Warning
        text.startsWith("已检测") || text.startsWith("已成功拉取模型") -> StatusKind.Info
        text.startsWith("请先补充") -> StatusKind.Danger
        else -> StatusKind.Danger
    }
}

/** 配置整体状态的短标签：徽标里只放能一眼看懂的四五个字，完整文案在展开区。 */
private fun configStatusLabel(status: String): String {
    val text = status.trim()
    return when {
        text.isEmpty() || text == "未检测" -> "未检测"
        text.startsWith("检测完成") -> {
            val available = COMPLETED_PATTERN.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            if (available > 0) "有可用模型" else "全部不可用"
        }
        text.startsWith("检测已停止") -> "已停止"
        text.startsWith("已成功拉取模型") -> "拉取完成"
        text.startsWith("已检测") -> "检测中"
        text.startsWith("请先补充") -> "缺少配置"
        else -> "检测失败"
    }
}

/** 「检测完成：n/m 个模型可用」里的 n。 */
private val COMPLETED_PATTERN = Regex("：(\\d+)/")