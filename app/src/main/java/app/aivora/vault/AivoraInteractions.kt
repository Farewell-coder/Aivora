package app.aivora.vault

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import app.aivora.vault.ui.theme.AivoraShape

/**
 * 全局交互原语。
 *
 * 这里的每个函数都只负责「反馈」这一件事，不持有业务状态：
 *  - [pressScale]：按压缩放，给所有可点击表面一致的下压手感
 *  - [tappable]：按压 + 波纹 + 可选触感，替代裸 clickable
 *  - [CircularRevealBox]：以底部为圆心的扩散转场
 *  - [rememberHaptics]：集中管理触感调用，避免各页面各写一套
 *
 * 约束：不引入第三方依赖，全部基于 Compose 自带能力；动画时长控制在
 * 160~320ms，保证「轻」而不是「炫」。
 */
object Motion {
    /** 按压缩放比例：够明显但不夸张。 */
    const val PRESSED_SCALE = 0.965f

    /** 展开 / 收起的内容动画时长。 */
    const val EXPAND_MS = 220

    /** 页面圆形扩散转场时长。 */
    const val REVEAL_MS = 320
}

/** 按压反馈：读取 interactionSource 的按压态并缩放。 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = Motion.PRESSED_SCALE,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 统一的可点击表面：按压缩放 + 波纹 + 可选触感。
 *
 * 缩放与波纹共享同一个 interactionSource，不会出现「有波纹没缩放」的割裂。
 */
@Composable
fun Modifier.tappable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    shape: RoundedCornerShape = AivoraShape.chip,
    haptic: Boolean = false,
): Modifier {
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }
    return this
        .clip(shape)
        .pressScale(source)
        .clickable(
            interactionSource = source,
            indication = ripple(),
            enabled = enabled,
        ) {
            if (haptic) haptics.toggle()
            onClick()
        }
}

/**
 * 圆形扩散转场容器。
 *
 * 每次 [revealKey] 变化时，从容器底部中心扩散出内容；动画结束后不再裁剪，
 * 避免长期停留在 clip 状态带来额外的绘制开销和点击区域异常。
 */
@Composable
fun CircularRevealBox(
    revealKey: Any,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val progress = remember(revealKey) { Animatable(0f) }
    LaunchedEffect(revealKey) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = Motion.REVEAL_MS, easing = FastOutSlowInEasing),
        )
    }
    Box(
        modifier
            .fillMaxSize()
            .drawWithContent {
                if (progress.value >= 0.999f) {
                    drawContent()
                } else {
                    val radius = progress.value * (size.width + size.height) * 0.75f
                    val path = Path().apply {
                        addOval(Rect(center = Offset(size.width / 2f, size.height), radius = radius))
                    }
                    clipPath(path) { this@drawWithContent.drawContent() }
                }
            }
    ) {
        content()
    }
}

/** 触感调用的集中入口，保证全应用只有一套强度约定。 */
@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}

class Haptics internal constructor(private val feedback: HapticFeedback) {
    /** 进入选择模式、长按这类「状态切换」。 */
    fun toggle() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)

    /** 轻点反馈，例如底栏切换。 */
    fun tap() = feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
}
