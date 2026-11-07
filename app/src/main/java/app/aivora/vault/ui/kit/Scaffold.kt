package app.aivora.vault.ui.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aivora.vault.Lucide
import app.aivora.vault.Tab
import app.aivora.vault.ui.theme.AivoraMotion
import app.aivora.vault.ui.theme.AivoraShape
import app.aivora.vault.ui.theme.AivoraSpace
import app.aivora.vault.ui.theme.AivoraThemeColors
import app.aivora.vault.ui.theme.AivoraType

/**
 * 页面外壳：状态栏让位 + 标题栏 + 内容 + 悬浮底栏。
 *
 * 【为什么不再用 Scaffold】重构前 Scaffold 的 `contentWindowInsets` 与手写的
 * `Spacer(windowInsetsTopHeight(statusBars))` 重复让位，导致「标题下方莫名多一截空白」。
 * v0.1.0 修过一次，但两处让位并存的结构没变，换个页面还会再犯。
 * 现在「背景铺到屏幕顶端」与「内容避开状态栏」由本组件一处决定。
 *
 * @param showsBottomBar 只在看 Tab 本体时为 true；二级页全屏。
 */
@Composable
fun AivoraScaffold(
    title: String,
    modifier: Modifier = Modifier,
    showsBack: Boolean = false,
    onBack: () -> Unit = {},
    showsBottomBar: Boolean = false,
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable () -> Unit,
) {
    val c = AivoraThemeColors.colors
    Box(modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                AivoraTopBar(title = title, showsBack = showsBack, onBack = onBack)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
        if (snackbarHostState != null) {
            SnackbarHost(
                snackbarHostState,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (showsBottomBar) 96.dp else AivoraSpace.lg),
            )
        }
        if (showsBottomBar) {
            BottomBar(Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** 标题栏：最小高度 56，长标题不换行。 */
@Composable
fun AivoraTopBar(
    title: String,
    showsBack: Boolean = false,
    onBack: () -> Unit = {},
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = AivoraThemeColors.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = AivoraSpace.gutter, vertical = AivoraSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showsBack) {
            IconAction(Lucide.ArrowLeft, "返回", onBack, tint = c.text)
            Spacer(Modifier.width(AivoraSpace.xs))
        } else {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(AivoraShape.chip)
                    .background(c.accentContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text("A", style = AivoraType.bodyStrong, color = c.onAccentContainer)
            }
            Spacer(Modifier.width(AivoraSpace.md))
        }
        Text(
            title,
            Modifier.weight(1f),
            style = AivoraType.pageTitle,
            color = c.text,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        actions?.let { Row(verticalAlignment = Alignment.CenterVertically, content = it) }
    }
}

/**
 * 底部悬浮导航。
 *
 * 【为什么悬浮】贴底时滚到列表末尾会有内容被挡住，各页面得自己留 132dp 白，
 * 而且留多留少全凭手感。悬浮版让「留白」由外壳统一给。
 * 【为什么不做实时模糊】列表滚动时每帧模糊会持续吃 GPU；用半透明面 + 描边 + 单层投影
 * 在观感上等价，成本为零。
 */
@Composable
fun BottomBar(modifier: Modifier = Modifier) {
    val c = AivoraThemeColors.colors
    Column(modifier.fillMaxWidth()) {
        // 渐隐：内容滚到导航后面时柔和过渡，而不是被硬边切断。
        Box(
            Modifier
                .fillMaxWidth()
                .height(AivoraSpace.xxl)
                .background(Brush.verticalGradient(listOf(Color.Transparent, c.background))),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = AivoraSpace.md, end = AivoraSpace.md, bottom = AivoraSpace.sm),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, AivoraShape.pill)
                    .clip(AivoraShape.pill)
                    .background(c.surface.copy(alpha = 0.96f))
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            ) {
                BottomBarContent()
            }
        }
    }
}

/** 当前选中的 Tab，由外壳经 CompositionLocal 提供。 */
val LocalSelectedTab = staticCompositionLocalOf { Tab.Configs }

/** 切换 Tab 的回调。 */
val LocalTabSelect = staticCompositionLocalOf<(Tab) -> Unit> { {} }

/**
 * 底栏内容：四个 Tab 等宽，中心间距严格一致。
 *
 * 必须是 `RowScope` 的扩展：`Modifier.weight(1f)` 只在该作用域内可用。
 * 早期写成普通 Composable，在 forEach 的 lambda 里 loss 了作用域，
 * 编译报 `Unresolved reference 'weight'`。
 */
@Composable
private fun RowScope.BottomBarContent() {
    val c = AivoraThemeColors.colors
    val entries = listOf(
        Tab.Configs to Lucide.List,
        Tab.Diagnostics to Lucide.Activity,
        Tab.Records to Lucide.History,
        Tab.Settings to Lucide.Settings,
    )
    val selected = LocalSelectedTab.current
    val onSelect = LocalTabSelect.current
    entries.forEach { (tab, icon) ->
        val active = selected == tab
        val pill by animateColorAsState(
            targetValue = if (active) c.accentContainer else Color.Transparent,
            animationSpec = tween(AivoraMotion.QUICK_MS),
            label = "navPill",
        )
        val tint = if (active) c.accent else c.textTertiary
        Column(
            Modifier
                .weight(1f)
                .clip(AivoraShape.pill)
                .background(pill)
                .clickable { if (!active) onSelect(tab) }
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, tab.title, Modifier.size(22.dp), tint = tint)
            Spacer(Modifier.height(3.dp))
            // 单行、不设行高：标签高度由内边距决定，设了 lineHeight 反而把字形顶偏。
            Text(tab.title, style = AivoraType.label, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 滚动页：统一左右留白 + 底部为悬浮底栏留空。 */
@Composable
fun ScrollColumn(
    modifier: Modifier = Modifier,
    bottomSpace: Dp = AivoraSpace.navSpace,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AivoraSpace.gutter),
    ) {
        content()
        Spacer(Modifier.height(bottomSpace))
    }
}