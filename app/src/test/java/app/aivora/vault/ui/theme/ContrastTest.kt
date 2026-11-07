package app.aivora.vault.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 配色对比度回归测试。
 *
 * 【为什么要有】配色是全软件最容易「悄悄退化」的地方：把某条灰字调淡一格、
 * 把强调色换成更鲜艳的一版，界面看起来仍然正常，但对比度可能已经跌破可读线。
 * 这种退化在代码评审里看不出来，只能靠数值。
 *
 * 判据来自 WCAG 2.1：
 *  - 正文 / 副文 / 三级文字 ≥ 4.5（AA 常规文本）
 *  - 容器上的文字 ≥ 7（容器本身是彩色底，需要更高对比）
 *  - 结构描边 ≥ 1.35（非文本元素，需可辨识；WCAG 建议 3:1 对 1dp 线过严）
 *  - 面层之间 ≥ 1.06（「几乎看不出但足以分出层次」的下限）
 *
 * 这里直接对 [AivoraLightColors] / [AivoraDarkColors] 的真实值计算，
 * 改配色后跑 `./gradlew testDebugUnitTest` 就会立刻失败，而不是等用户反馈「看不清」。
 */
class ContrastTest {

    /** WCAG 2.1 相对亮度。 */
    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.03928) d / 12.92 else Math.pow((d + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    /** 对比度 (L1 + 0.05) / (L2 + 0.05)。 */
    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertContrast(actual: Double, min: Double, what: String) {
        assertTrue(
            "$what 对比度 ${"%.2f".format(actual)} < 要求的 $min",
            actual >= min,
        )
    }

    // ── 浅色档 ────────────────────────────────────────────────

    @Test
    fun light_textHierarchyIsReadable() {
        val c = AivoraLightColors
        assertContrast(contrast(c.text, c.background), 4.5, "浅色 主文字/页面底")
        assertContrast(contrast(c.textSecondary, c.background), 4.5, "浅色 次文字/页面底")
        assertContrast(contrast(c.textTertiary, c.background), 4.5, "浅色 三级文字/页面底")
        assertContrast(contrast(c.textTertiary, c.subtle), 4.5, "浅色 三级文字/弱化面板")
        assertContrast(contrast(c.text, c.surface), 4.5, "浅色 主文字/卡片")
        assertContrast(contrast(c.textSecondary, c.surface), 4.5, "浅色 次文字/卡片")
        assertContrast(contrast(c.textTertiary, c.surface), 4.5, "浅色 三级文字/卡片")
    }

    @Test
    fun light_accentAndOnColorsAreReadable() {
        val c = AivoraLightColors
        assertContrast(contrast(c.accent, c.background), 4.5, "浅色 强调色/页面底")
        assertContrast(contrast(c.accent, c.surface), 4.5, "浅色 强调色/卡片")
        assertContrast(contrast(c.onAccent, c.accent), 4.5, "浅色 强调色上的文字")
        assertContrast(contrast(c.onAccentContainer, c.accentContainer), 7.0, "浅色 强调容器上的文字")
    }

    @Test
    fun light_semanticColorsAreReadable() {
        val c = AivoraLightColors
        assertContrast(contrast(c.success, c.background), 4.5, "浅色 成功色/页面底")
        assertContrast(contrast(c.warning, c.background), 4.5, "浅色 警告色/页面底")
        assertContrast(contrast(c.danger, c.background), 4.5, "浅色 危险色/页面底")
        assertContrast(contrast(c.info, c.background), 4.5, "浅色 信息色/页面底")

        assertContrast(contrast(c.onSuccessContainer, c.successContainer), 7.0, "浅色 成功容器字")
        assertContrast(contrast(c.onWarningContainer, c.warningContainer), 7.0, "浅色 警告容器字")
        assertContrast(contrast(c.onDangerContainer, c.dangerContainer), 7.0, "浅色 危险容器字")
        assertContrast(contrast(c.onInfoContainer, c.infoContainer), 7.0, "浅色 信息容器字")
    }

    @Test
    fun light_surfacesAreDistinguishable() {
        val c = AivoraLightColors
        assertContrast(contrast(c.surface, c.background), 1.06, "浅色 卡片/页面底")
        assertContrast(contrast(c.subtle, c.background), 1.06, "浅色 弱化面板/页面底")
        assertContrast(contrast(c.raised, c.surface), 1.06, "浅色 抬升面/卡片")
        assertContrast(contrast(c.outline, c.surface), 1.35, "浅色 描边/卡片")
        assertContrast(contrast(c.divider, c.surface), 1.15, "浅色 分隔线/卡片")
    }

    // ── 深色档 ────────────────────────────────────────────────

    @Test
    fun dark_textHierarchyIsReadable() {
        val c = AivoraDarkColors
        assertContrast(contrast(c.text, c.background), 4.5, "深色 主文字/页面底")
        assertContrast(contrast(c.textSecondary, c.background), 4.5, "深色 次文字/页面底")
        assertContrast(contrast(c.textTertiary, c.background), 4.5, "深色 三级文字/页面底")
        assertContrast(contrast(c.textTertiary, c.subtle), 4.5, "深色 三级文字/弱化面板")
        assertContrast(contrast(c.text, c.surface), 4.5, "深色 主文字/卡片")
        assertContrast(contrast(c.textSecondary, c.surface), 4.5, "深色 次文字/卡片")
        assertContrast(contrast(c.textTertiary, c.surface), 4.5, "深色 三级文字/卡片")
    }

    @Test
    fun dark_accentAndOnColorsAreReadable() {
        val c = AivoraDarkColors
        assertContrast(contrast(c.accent, c.background), 4.5, "深色 强调色/页面底")
        assertContrast(contrast(c.accent, c.surface), 4.5, "深色 强调色/卡片")
        // 深色档强调色是浅色，其上的文字必须是深底字 —— 白字在这里只有 2.47。
        assertContrast(contrast(c.onAccent, c.accent), 4.5, "深色 强调色上的文字")
        assertContrast(contrast(c.onAccentContainer, c.accentContainer), 7.0, "深色 强调容器上的文字")
    }

    @Test
    fun dark_semanticColorsAreReadable() {
        val c = AivoraDarkColors
        assertContrast(contrast(c.success, c.background), 4.5, "深色 成功色/页面底")
        assertContrast(contrast(c.warning, c.background), 4.5, "深色 警告色/页面底")
        assertContrast(contrast(c.danger, c.background), 4.5, "深色 危险色/页面底")
        assertContrast(contrast(c.info, c.background), 4.5, "深色 信息色/页面底")

        assertContrast(contrast(c.onSuccessContainer, c.successContainer), 7.0, "深色 成功容器字")
        assertContrast(contrast(c.onWarningContainer, c.warningContainer), 7.0, "深色 警告容器字")
        assertContrast(contrast(c.onDangerContainer, c.dangerContainer), 7.0, "深色 危险容器字")
        assertContrast(contrast(c.onInfoContainer, c.infoContainer), 7.0, "深色 信息容器字")
    }

    @Test
    fun dark_surfacesAreDistinguishable() {
        val c = AivoraDarkColors
        assertContrast(contrast(c.surface, c.background), 1.06, "深色 卡片/页面底")
        assertContrast(contrast(c.subtle, c.background), 1.06, "深色 弱化面板/页面底")
        assertContrast(contrast(c.raised, c.surface), 1.06, "深色 抬升面/卡片")
        assertContrast(contrast(c.outline, c.surface), 1.35, "深色 描边/卡片")
        assertContrast(contrast(c.divider, c.surface), 1.15, "深色 分隔线/卡片")
    }

    // ── 主题本身 ──────────────────────────────────────────────

    @Test
    fun darkSchemeIsNotAnInversionOfLight() {
        // 深色不是浅色的反色：要求至少有一个面层不是简单取反（对比度差异明显）。
        val light = AivoraLightColors
        val dark = AivoraDarkColors
        val lightSurfaceLum = luminance(light.surface)
        val darkSurfaceLum = luminance(dark.surface)
        assertTrue("深色卡片不该是浅色卡片的简单取反", darkSurfaceLum < lightSurfaceLum * 0.25)
        assertTrue("深色底必须是近黑而不是纯黑", dark.background.red > 0.02f || dark.background.green > 0.02f || dark.background.blue > 0.02f)
    }

    @Test
    fun backgroundMatchesLaunchWindowColor() {
        // 启动窗口的 windowBackground 必须与 Kotlin 侧 background 一致，否则冷启动会闪。
        // values/colors.xml: #F6F7F9 ；values-night/colors.xml: #0F1115
        assertTrue(
            "浅色 background 应为 #F6F7F9（与 values/colors.xml 一致）",
            AivoraLightColors.background.value == 0xFFF6F7F9uL,
        )
        assertTrue(
            "深色 background 应为 #0F1115（与 values-night/colors.xml 一致）",
            AivoraDarkColors.background.value == 0xFF0F1115uL,
        )
    }
}