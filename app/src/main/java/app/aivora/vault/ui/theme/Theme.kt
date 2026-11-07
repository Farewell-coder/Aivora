package app.aivora.vault.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * 主题装配：把 [AivoraColors] 同时投到两处。
 *
 * 1. **Material3 `ColorScheme`** —— 让 `Button` / `OutlinedTextField` / `Switch` 这些
 *    现成控件自动跟随 token，不需要我们重写它们的内部配色。
 * 2. **`LocalAivoraColors`** —— 语义色（success / warning / danger / info / 三档文字）
 *    M3 的槽位里没有，必须自带一份，否则「可用 / 不可用」又只能写成一句灰字。
 *
 * 页面与组件一律通过 [AivoraThemeColors] 取色；禁止直接 `Color(0xFF...)`。
 */
private val LocalAivoraColors = staticCompositionLocalOf { AivoraLightColors }

/** 读取当前主题的语义色。名字避免与 `MaterialTheme` 混淆。 */
object AivoraThemeColors {
    val colors: AivoraColors
        @Composable @ReadOnlyComposable get() = LocalAivoraColors.current
}

/**
 * 把 token 映射成 M3 的 ColorScheme。
 *
 * 只映射 M3 真正用到且我们有把握的槽位；其余留默认值也不会露馅，
 * 因为所有可见面都从 [AivoraColors] 取。
 * 深色档的 `onPrimary` 取深底字 —— `#7FA8D9` 上的白字只有 2.47:1。
 */
private fun AivoraColors.toColorScheme() = if (dark) {
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = onAccentContainer,
        secondary = textSecondary,
        onSecondary = background,
        secondaryContainer = raised,
        onSecondaryContainer = text,
        tertiary = info,
        onTertiary = onInfoContainer,
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = subtle,
        onSurfaceVariant = textSecondary,
        surfaceContainerLowest = background,
        surfaceContainerLow = subtle,
        surfaceContainer = raised,
        surfaceContainerHigh = Color(0xFF2B313A),
        surfaceContainerHighest = Color(0xFF333A44),
        outline = outline,
        outlineVariant = divider,
        error = danger,
        onError = onDangerContainer,
        errorContainer = dangerContainer,
        onErrorContainer = onDangerContainer,
        scrim = scrim,
    )
} else {
    lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = onAccentContainer,
        secondary = textSecondary,
        onSecondary = surface,
        secondaryContainer = raised,
        onSecondaryContainer = text,
        tertiary = info,
        onTertiary = onInfoContainer,
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = subtle,
        onSurfaceVariant = textSecondary,
        surfaceContainerLowest = surface,
        surfaceContainerLow = subtle,
        surfaceContainer = raised,
        surfaceContainerHigh = Color(0xFFDFE4EB),
        surfaceContainerHighest = Color(0xFFD6DCE5),
        outline = outline,
        outlineVariant = divider,
        error = danger,
        onError = onAccent,
        errorContainer = dangerContainer,
        onErrorContainer = onDangerContainer,
        scrim = scrim,
    )
}

/**
 * 把 [AivoraType] 映射到 M3 的 15 个 Typography 槽位。
 *
 * M3 槽位名与我们的语义名对不上，映射原则是「用最接近的那个」，
 * 业务代码想按语义取字时优先用 `AivoraType.xxx`，而不是 `MaterialTheme.typography.xxx`。
 */
private fun aivoraTypography() = Typography(
    headlineSmall = AivoraType.pageTitle,
    titleLarge = AivoraType.section,
    titleMedium = AivoraType.bodyStrong,
    titleSmall = AivoraType.label,
    bodyLarge = AivoraType.body,
    bodyMedium = AivoraType.meta,
    bodySmall = AivoraType.monoSmall.let { it.copy(fontFamily = null) },
    labelLarge = AivoraType.bodyStrong,
    labelMedium = TextStyle(fontSize = AivoraType.label.fontSize, fontWeight = AivoraType.label.fontWeight),
    labelSmall = AivoraType.label,
)

private fun aivoraShapes() = Shapes(
    extraSmall = AivoraShape.chip,
    small = AivoraShape.chip,
    medium = AivoraShape.inner,
    large = AivoraShape.block,
    extraLarge = AivoraShape.block,
)

/**
 * 全应用唯一的主题入口。
 *
 * 注意：状态栏 / 导航栏图标颜色**不在这里**设 —— 那一处留在 `AivoraTheme` 的调用方
 * （`MainActivity` 里的 `SystemBars(dark)`），因为它需要拿到 Activity 的 window。
 */
@Composable
fun ProvideAivoraTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) AivoraDarkColors else AivoraLightColors
    CompositionLocalProvider(LocalAivoraColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            typography = aivoraTypography(),
            shapes = aivoraShapes(),
            content = content,
        )
    }
}