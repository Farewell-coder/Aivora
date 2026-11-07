package app.aivora.vault.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aivora.vault.ui.theme.AivoraShape
import app.aivora.vault.ui.theme.AivoraSize
import app.aivora.vault.ui.theme.AivoraSpace
import app.aivora.vault.ui.theme.AivoraStroke
import app.aivora.vault.ui.theme.AivoraThemeColors
import app.aivora.vault.ui.theme.AivoraType

/**
 * 基础组件层。
 *
 * 【为什么需要这一层】重构前每个页面都自己拼 `Surface + 圆角 + 边框 + 内边距`，
 * 结果 17 处调用点各写各的，同一个「卡片」在不同页面高度、留白、底色都不一样，
 * 用户看到的是「全都长一样但没有层次」。
 *
 * 这里把容器收敛成三档，**语义固定，不许混用**：
 *
 * | 组件 | 视觉 | 用在哪 |
 * |---|---|---|
 * | [SectionBlock] | surface 底 + 1dp 描边 + 20 圆角 | 页面主功能区块 |
 * | [RowItem] | 透明底，行间分隔线 | 配置 / 模型 / 记录 / 设置行 |
 * | [SubtlePanel] | subtle 底，无描边 | 说明、错误摘要、技术信息井 |
 *
 * 三档的差别是**底色 + 描边 + 内边距**，不是投影 —— 手机上大面积投影既耗性能又不干净。
 */

/**
 * Block：页面主区块。
 *
 * 一个 Block 里可以放多个 [RowItem]，而不是每条内容各包一张卡。
 * `content` 拿到的是 `ColumnScope`，行与行之间请用 [ItemDivider]，不要自己写 Spacer。
 */
@Composable
fun SectionBlock(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AivoraThemeColors.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AivoraShape.block,
        color = c.surface,
        border = BorderStroke(AivoraStroke.hairline, c.outline),
    ) {
        Column(Modifier.padding(vertical = AivoraSpace.xs), content = content)
    }
}

/** Block 内标题：贴在图标的右侧或独立成行。 */
@Composable
fun BlockHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = AivoraThemeColors.colors
    Row(
        modifier.fillMaxWidth().padding(
            start = AivoraSpace.blockPadding,
            end = AivoraSpace.blockPadding,
            top = AivoraSpace.md,
            bottom = AivoraSpace.sm,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = c.textSecondary)
            Spacer(Modifier.width(AivoraSpace.sm))
        }
        Text(title, style = AivoraType.section, color = c.text, modifier = Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

/** Block 内行与行之间的分隔线：只在行之间画，首尾不画。 */
@Composable
fun ItemDivider() {
    HorizontalDivider(
        Modifier.padding(start = AivoraSpace.blockPadding, end = AivoraSpace.blockPadding),
        thickness = 1.dp,
        color = AivoraThemeColors.colors.divider,
    )
}

/**
 * Item：列表条目。
 *
 * 不再给每一条包卡片 —— 行直接铺在 Block 里，靠分隔线与底色区分。
 * 按下时用 `raised` 底色做反馈，比波纹更克制，也不会有「圆角方框里溢出波纹」的问题。
 *
 * @param onClick 为空表示不可点击（纯展示行）。
 */
@Composable
fun RowItem(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(
        horizontal = AivoraSpace.blockPadding,
        vertical = AivoraSpace.itemVertical,
    ),
    content: @Composable RowScope.() -> Unit,
) {
    // 【调用方传入的 modifier 必须在链首】它承载 hover/长按外层 Box、weight、
    //   动画修饰符等。早期版本写成 `Row(clipped...)` 把它整个丢了，
    //   表现是「长按进入选择模式」静默失效 —— 属于最难查的一类 bug。
    val base = modifier.fillMaxWidth().clip(AivoraShape.chip)
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base

    Row(
        clickable.padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Subtle：弱化面板。
 *
 * 用于说明文字、错误摘要、技术信息井。**不加描边** —— 靠比页面略深的底色区分，
 * 这样它就不会跟 Block 抢「这是一个新卡片」的注意力。
 */
@Composable
fun SubtlePanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = AivoraShape.inner,
    color: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AivoraThemeColors.colors
    Box(
        modifier.fillMaxWidth().clip(shape).background(color ?: c.subtle),
    ) {
        Column(Modifier.padding(AivoraSpace.md), content = content)
    }
}

/**
 * 分组小标题：放在 Block 之外，用 label 档 + 字距，不与正文抢层级。
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(
            start = AivoraSpace.xs,
            top = AivoraSpace.lg,
            bottom = AivoraSpace.sm,
        ),
        style = AivoraType.label,
        color = AivoraThemeColors.colors.textTertiary,
    )
}

/**
 * 状态语义。
 *
 * 【为什么不用纯文字】重构前「可用 / 不可用 / 等待检测 / 检测中」全是同一档灰字，
 * 用户必须逐个读字才能分辨。状态必须有**颜色 + 形状**两个通道，
 * 色觉障碍用户也能靠「实心/空心点 + 文案」区分。
 */
enum class StatusKind { Success, Warning, Danger, Info, Neutral }

/** 状态对应的前景色。 */
@Composable
fun StatusKind.foreground(): Color {
    val c = AivoraThemeColors.colors
    return when (this) {
        StatusKind.Success -> c.success
        StatusKind.Warning -> c.warning
        StatusKind.Danger -> c.danger
        StatusKind.Info -> c.info
        StatusKind.Neutral -> c.textTertiary
    }
}

/** 状态对应的容器底色。 */
@Composable
fun StatusKind.container(): Color {
    val c = AivoraThemeColors.colors
    return when (this) {
        StatusKind.Success -> c.successContainer
        StatusKind.Warning -> c.warningContainer
        StatusKind.Danger -> c.dangerContainer
        StatusKind.Info -> c.infoContainer
        StatusKind.Neutral -> c.subtle
    }
}

/** 状态对应的容器文字色。 */
@Composable
fun StatusKind.onContainer(): Color {
    val c = AivoraThemeColors.colors
    return when (this) {
        StatusKind.Success -> c.onSuccessContainer
        StatusKind.Warning -> c.onWarningContainer
        StatusKind.Danger -> c.onDangerContainer
        StatusKind.Info -> c.onInfoContainer
        StatusKind.Neutral -> c.textSecondary
    }
}

/**
 * 状态标签：小胶囊 + 状态点 + 文案。
 *
 * 状态点用**实心圆**表示确定性状态（成功/失败），**空心圆**表示过程态（进行中/未检测），
 * 这样不依赖颜色也能分辨。
 */
@Composable
fun StatusBadge(
    kind: StatusKind,
    text: String,
    modifier: Modifier = Modifier,
) {
    val fg = kind.foreground()
    val filled = kind == StatusKind.Success || kind == StatusKind.Danger || kind == StatusKind.Warning
    Box(
        modifier
            .clip(AivoraShape.pill)
            .background(kind.container())
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(AivoraShape.pill)
                    .then(if (filled) Modifier.background(fg) else Modifier.border(1.dp, fg, AivoraShape.pill)),
            )
            Spacer(Modifier.width(5.dp))
            Text(text, style = AivoraType.label, color = kind.onContainer(), maxLines = 1)
        }
    }
}

/**
 * 数据行：左侧标签，右侧技术值。
 *
 * 值走等宽 —— Endpoint、模型 ID、HTTP 码这类内容需要逐字符比对。
 *
 * @param mono 是否用等宽（技术值 true，普通文本 false）
 * @param emphasize 是否加粗强调（HTTP 码、关键状态）
 */
@Composable
fun DataRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    mono: Boolean = true,
    emphasize: Boolean = false,
    valueColor: Color? = null,
    maxLines: Int = 1,
) {
    val c = AivoraThemeColors.colors
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(
            label,
            Modifier.width(88.dp),
            style = AivoraType.meta,
            color = c.textTertiary,
        )
        SelectionContainer(Modifier.weight(1f)) {
            Text(
                value,
                style = when {
                    mono && emphasize -> AivoraType.monoStrong
                    mono -> AivoraType.mono
                    emphasize -> AivoraType.bodyStrong
                    else -> AivoraType.body
                },
                color = valueColor ?: if (emphasize) c.text else c.textSecondary,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 技术信息井：等宽内容 + 底色 + 可选复制。
 *
 * 凭证、Endpoint、错误详情放这里。凭证是**一等公民**，必须能完整看清、能选中、能复制，
 * 所以这里用 `SelectionContainer` 而不是可点击复制的普通文本。
 */
@Composable
fun TechWell(
    value: String,
    modifier: Modifier = Modifier,
    leadingLabel: String? = null,
    onCopy: (() -> Unit)? = null,
) {
    val c = AivoraThemeColors.colors
    val clipboard = LocalClipboardManager.current
    Column(modifier.fillMaxWidth()) {
        if (leadingLabel != null) {
            Text(
                leadingLabel,
                Modifier.padding(bottom = AivoraSpace.xs),
                style = AivoraType.label,
                color = c.textTertiary,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(AivoraShape.inner)
                .background(c.subtle)
                .border(AivoraStroke.hairline, c.divider, AivoraShape.inner)
                .padding(AivoraSpace.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectionContainer(Modifier.weight(1f)) {
                    Text(value, style = AivoraType.mono, color = c.text)
                }
                if (onCopy != null) {
                    Spacer(Modifier.width(AivoraSpace.sm))
                    CopyAction {
                        clipboard.setText(AnnotatedString(value))
                        onCopy()
                    }
                }
            }
        }
    }
}

/** 复制小按钮：统一外观与触控区。 */
@Composable
fun CopyAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AivoraThemeColors.colors
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(AivoraSize.iconTouch)
            .clip(AivoraShape.chip)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(app.aivora.vault.Lucide.Copy, "复制", Modifier.size(18.dp), tint = c.accent)
    }
}

/** 说明文字：比正文小一档，用次文字色，仍保证 ≥ 4.5。 */
@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.fillMaxWidth(),
        style = AivoraType.meta,
        color = AivoraThemeColors.colors.textSecondary,
    )
}

/** 危险提示：错误摘要、密钥外泄警告。 */
@Composable
fun DangerHint(text: String, modifier: Modifier = Modifier) {
    val c = AivoraThemeColors.colors
    SubtlePanel(modifier, color = c.dangerContainer) {
        Text(text, style = AivoraType.meta, color = c.onDangerContainer)
    }
}

/** 警告提示：快照缺失、待确认。 */
@Composable
fun WarningHint(text: String, modifier: Modifier = Modifier) {
    val c = AivoraThemeColors.colors
    SubtlePanel(modifier, color = c.warningContainer) {
        Text(text, style = AivoraType.meta, color = c.onWarningContainer)
    }
}

/**
 * 空状态。
 *
 * 之前各页面都是一个放大镜图标 + 一句话，用户看不出「这里本该有什么、下一步做什么」。
 * 现在必须有标题 + 说明，并尽量给出下一步操作。
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val c = AivoraThemeColors.colors
    Column(
        modifier.fillMaxWidth().padding(vertical = AivoraSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(AivoraShape.inner)
                .background(c.subtle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(24.dp), tint = c.textTertiary)
        }
        Spacer(Modifier.height(AivoraSpace.md))
        Text(title, style = AivoraType.bodyStrong, color = c.text, textAlign = TextAlign.Center)
        if (hint != null) {
            Spacer(Modifier.height(AivoraSpace.xs))
            Text(
                hint,
                style = AivoraType.meta,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = AivoraSpace.lg),
            )
        }
        if (action != null) {
            Spacer(Modifier.height(AivoraSpace.lg))
            action()
        }
    }
}

/** 加载态：整块骨架替代裸 CircularProgressIndicator 撑在页面中间。 */
@Composable
fun LoadingBlock(text: String, modifier: Modifier = Modifier) {
    val c = AivoraThemeColors.colors
    Row(
        modifier.fillMaxWidth().padding(vertical = AivoraSpace.xxl),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = c.accent)
        Spacer(Modifier.width(AivoraSpace.sm))
        Text(text, style = AivoraType.meta, color = c.textSecondary)
    }
}

/** 危险色文字（用于确认按钮的文案着色）。 */
@Composable
fun dangerColor(): Color = AivoraThemeColors.colors.danger

/** 让 RowItem 内部控件默认用次文字色，避免每条都显式传色。 */
@Composable
fun MutedContent(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides AivoraThemeColors.colors.textSecondary) {
        content()
    }
}