package app.aivora.vault.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import app.aivora.vault.Lucide
import app.aivora.vault.ui.theme.AivoraMotion
import app.aivora.vault.ui.theme.AivoraShape
import app.aivora.vault.ui.theme.AivoraSize
import app.aivora.vault.ui.theme.AivoraSpace
import app.aivora.vault.ui.theme.AivoraStroke
import app.aivora.vault.ui.theme.AivoraThemeColors
import app.aivora.vault.ui.theme.AivoraType
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 表单与操作控件。
 *
 * 全部从 token 取值，业务代码不再出现 `fontSize` / `RoundedCornerShape` / 颜色字面量。
 */

/**
 * 统一输入框。
 *
 * 只保留 `label`（Material3 浮动标签）：未输入时贴在框内，聚焦或有内容后缩到框上方并常驻，
 * 不会出现「内容一填进去提示就消失」。`placeholder` 只作为聚焦后的补充说明。
 *
 * 高度、圆角、聚焦描边全部由 token 决定；`supportingText` 用来承载端点纠正提示这类与字段绑定的说明。
 */
@Composable
fun AivoraField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    shape: Shape = AivoraShape.inner,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val c = AivoraThemeColors.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        readOnly = readOnly,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText?.let { { Text(it, style = AivoraType.meta) } },
        isError = isError,
        visualTransformation = visualTransformation,
        singleLine = singleLine,
        shape = shape,
        keyboardOptions = keyboardOptions,
        textStyle = AivoraType.body,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent,
            unfocusedBorderColor = c.outline,
            focusedLabelColor = c.accent,
            unfocusedLabelColor = c.textTertiary,
            cursorColor = c.accent,
            focusedTextColor = c.text,
            unfocusedTextColor = c.text,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            errorBorderColor = c.danger,
            errorLabelColor = c.danger,
            focusedSupportingTextColor = c.textSecondary,
            unfocusedSupportingTextColor = c.textTertiary,
        ),
    )
}

/** 主操作按钮：唯一强调色填充，一屏最多一个。 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val c = AivoraThemeColors.colors
    val bg = if (enabled && !loading) c.accent else c.raised
    val fg = if (enabled && !loading) c.onAccent else c.textTertiary
    val source = remember { MutableInteractionSource() }
    val scale = pressScaleOf(source)
    Row(
        modifier
            .heightIn(min = AivoraSize.button)
            .clip(AivoraShape.inner)
            .background(bg)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled && !loading,
                onClick = onClick,
            )
            .padding(horizontal = AivoraSpace.lg, vertical = AivoraSpace.sm)
            .pressScaleApply(scale),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = fg)
            Spacer(Modifier.width(AivoraSpace.sm))
        } else if (icon != null) {
            Icon(icon, null, Modifier.size(AivoraSize.iconButton), tint = fg)
            Spacer(Modifier.width(AivoraSpace.xs + 2.dp))
        }
        Text(text, style = AivoraType.bodyStrong, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 次操作按钮：描边 + 透明底，不抢主操作的注意力。 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val c = AivoraThemeColors.colors
    val fg = when {
        !enabled -> c.textTertiary
        danger -> c.danger
        else -> c.text
    }
    val borderColor = when {
        !enabled -> c.divider
        danger -> c.danger.copy(alpha = 0.6f)
        else -> c.outline
    }
    val source = remember { MutableInteractionSource() }
    val scale = pressScaleOf(source)
    Row(
        modifier
            .heightIn(min = AivoraSize.button)
            .clip(AivoraShape.inner)
            .background(c.surface)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = AivoraSpace.lg, vertical = AivoraSpace.sm)
            .pressScaleApply(scale)
            .then(Modifier)
            .border1(borderColor),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(AivoraSize.iconButton), tint = fg)
            Spacer(Modifier.width(AivoraSpace.xs + 2.dp))
        }
        Text(text, style = AivoraType.bodyStrong, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 内部：给 Modifier 加 1dp 描边，保持链式写法干净。 */
private fun Modifier.border1(color: Color): Modifier =
    this.border(AivoraStroke.hairline, color, AivoraShape.inner)

/** 文字型操作：低视觉重量，用于「取消」「关闭」「全选」。 */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val c = AivoraThemeColors.colors
    val color = when {
        !enabled -> c.textTertiary
        danger -> c.danger
        else -> c.accent
    }
    Box(
        modifier
            .heightIn(min = AivoraSize.buttonSmall)
            .clip(AivoraShape.chip)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = AivoraSpace.md, vertical = AivoraSpace.xs + 2.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = AivoraType.bodyStrong, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** 图标按钮：触控区 44，视觉 20。 */
@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    enabled: Boolean = true,
) {
    val c = AivoraThemeColors.colors
    val color = when {
        !enabled -> c.textTertiary
        tint != null -> tint
        else -> c.textSecondary
    }
    Box(
        modifier
            .size(AivoraSize.iconTouch)
            .clip(AivoraShape.chip)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, Modifier.size(AivoraSize.iconButton), tint = color) }
}

/**
 * 设置行：标题 + 可选说明 + 右侧控件。
 *
 * 设置页以前是「一张卡一个开关」，现在统一成 Block 里的行，行间用 [ItemDivider]。
 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = AivoraThemeColors.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = AivoraSpace.blockPadding, vertical = AivoraSpace.itemVertical),
        verticalAlignment = if (description == null) Alignment.CenterVertically else Alignment.Top,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = c.textSecondary)
            Spacer(Modifier.width(AivoraSpace.md))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = AivoraType.bodyStrong,
                color = c.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                // 说明是解释性长文本，允许换行；不设 maxLines 上限，让它自然折行而不是被裁。
                Text(description, style = AivoraType.meta, color = c.textSecondary)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(AivoraSpace.md))
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
    }
}

/** 开关行。 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    SettingRow(title, modifier, description, icon) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** 单选行：整行可点，单选圈只是指示。 */
@Composable
fun RadioRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    description: String? = null,
) {
    val c = AivoraThemeColors.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(AivoraShape.chip)
            .clickable(onClick = onSelect)
            .padding(horizontal = AivoraSpace.blockPadding, vertical = AivoraSpace.itemVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = if (selected) c.accent else c.textSecondary)
            Spacer(Modifier.width(AivoraSpace.md))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = AivoraType.body,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(description, style = AivoraType.meta, color = c.textSecondary)
            }
        }
        RadioButton(selected = selected, onClick = onSelect)
    }
}

/** 可跳转行：标题 + 右侧箭头。 */
@Composable
fun NavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    description: String? = null,
) {
    val c = AivoraThemeColors.colors
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = AivoraSpace.blockPadding, vertical = AivoraSpace.itemVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = c.textSecondary)
            Spacer(Modifier.width(AivoraSpace.md))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = AivoraType.bodyStrong,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(description, style = AivoraType.meta, color = c.textSecondary)
            }
        }
        Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = c.textTertiary)
    }
}

/**
 * 可展开区块：标题行 + 折叠内容。
 *
 * 箭头跟着展开进度旋转，而不是两帧硬切图标；整行可点，不再只有小箭头有效。
 */
@Composable
fun ExpandableBlock(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AivoraThemeColors.colors
    var expanded = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initiallyExpanded) }
    val chevron by animateFloatAsState(
        targetValue = if (expanded.value) 180f else 0f,
        animationSpec = tween(durationMillis = AivoraMotion.STANDARD_MS),
        label = "chevron",
    )
    SectionBlock(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded.value = !expanded.value }
                .padding(horizontal = AivoraSpace.blockPadding, vertical = AivoraSpace.itemVertical),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(18.dp), tint = c.textSecondary)
                Spacer(Modifier.width(AivoraSpace.md))
            }
            Text(
                title,
                Modifier.weight(1f),
                style = AivoraType.section,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                // 副标题（如「已开启」「跟随系统」）在行尾参与横向竞争：
                // 不限宽时标题与副标题会互相挤压，副标题被挤出容器。
                Text(
                    subtitle,
                    Modifier.padding(end = AivoraSpace.sm).widthIn(max = 120.dp),
                    style = AivoraType.meta,
                    color = c.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Lucide.ChevronDown,
                "展开或收起",
                Modifier.size(18.dp).rotate(chevron),
                tint = c.textTertiary,
            )
        }
        AnimatedVisibility(
            visible = expanded.value,
            enter = expandVertically(
                animationSpec = tween(durationMillis = AivoraMotion.STANDARD_MS),
                expandFrom = Alignment.Top,
            ) + fadeIn(tween(AivoraMotion.QUICK_MS)),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = AivoraMotion.STANDARD_MS),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(tween(AivoraMotion.QUICK_MS)),
        ) {
            Column(
                Modifier.padding(
                    start = AivoraSpace.blockPadding,
                    end = AivoraSpace.blockPadding,
                    bottom = AivoraSpace.md,
                ),
                content = content,
            )
        }
    }
}

/**
 * 确认弹窗。
 *
 * 危险操作（删除、覆盖）用 danger 色确认键，且**默认焦点不在确认键上**，
 * 避免误触。普通确认用 accent。
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String = "取消",
    danger: Boolean = false,
) {
    val c = AivoraThemeColors.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = AivoraType.section, color = c.text) },
        text = { Text(text, style = AivoraType.body, color = c.textSecondary) },
        confirmButton = {
            TextAction(
                confirmText,
                onClick = onConfirm,
                danger = danger,
            )
        },
        dismissButton = {
            TextAction(dismissText, onClick = onDismiss)
        },
        containerColor = c.surface,
        shape = AivoraShape.block,
    )
}

/** 按压缩放的共享实现，供自定义表面按钮使用。 */
@Composable
private fun pressScaleOf(source: MutableInteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    return if (pressed) AivoraMotion.PRESSED_SCALE else 1f
}

/** 把按压缩放应用到 Modifier 链上。 */
private fun Modifier.pressScaleApply(scale: Float): Modifier =
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }