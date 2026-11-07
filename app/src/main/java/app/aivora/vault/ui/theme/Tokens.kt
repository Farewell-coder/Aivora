package app.aivora.vault.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Aivora 设计令牌（Design Token）唯一来源。
 *
 * 【为什么要有这一层】v0.1.2 已有一份 `Tokens`，但只覆盖圆角与字号，颜色仍散落在
 * `lightColorScheme(...)` 的 20 多个字面量里，状态色（成功 / 警告 / 失败 / 进行中）
 * 根本没有槽位，于是界面只能靠一句灰色小字说明「可用 / 不可用」。结果是：
 * 一个管理端点与密钥的工具，最该被看清的 HTTP 码、耗时、错误全被压成 11sp 灰字。
 *
 * 这一版把「颜色 / 字号 / 形状 / 间距 / 动效」全部收进本文件，页面与组件一律从这里取值，
 * 禁止再写 `Color(0x...)`、`fontSize = 13.sp`、`RoundedCornerShape(14.dp)` 这类裸值。
 *
 * 【数值怎么来的】所有前景 / 背景配对都按 WCAG 2.1 公式实算，判据见文件末尾注释：
 * 正文 ≥ 4.5、容器上的文字 ≥ 7、非文字描边 ≥ 1.35、面层之间 ≥ 1.06。
 * 不是凭感觉调的色，改之前请先复算（scripts/palette_contrast.py）。
 */

/**
 * 一套配色。浅色与深色是**两套独立设计**，不是同一套色值的明暗反转：
 * 深色档的强调色必须提亮（`#2F5F8F` 放到深底上会糊成一团），并且因为强调色变浅，
 * 其上的文字要换成深色而不是白色 —— 白字压在 `#7FA8D9` 上只有 2.47:1，不可读。
 */
data class AivoraColors(
    val dark: Boolean,

    /** 页面底色。 */
    val background: Color,
    /** 主表面：Block 级容器。 */
    val surface: Color,
    /** 弱化表面：说明块、技术信息井、内嵌面板。 */
    val subtle: Color,
    /** 抬升表面：选中态、行内小控件底色。 */
    val raised: Color,

    /** 主文字：标题、正文。 */
    val text: Color,
    /** 次文字：说明、注释、设置项描述。 */
    val textSecondary: Color,
    /** 三级文字：时间戳、计数、占位符。仍 ≥ 4.5，可承载信息。 */
    val textTertiary: Color,

    /** 结构描边：需要被看见的边界（输入框、卡片）。 */
    val outline: Color,
    /** 分隔线：行与行之间，装饰性但需可辨识（≥ 1.2）。 */
    val divider: Color,

    /** 唯一强调色：主操作、选中态、进行中。 */
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,

    /** 语义色：成功（可用 / 已验证）。 */
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    /** 语义色：警告（超时 / 待确认 / 快照缺失）。 */
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    /** 语义色：危险（不可用 / 删除 / 密钥外泄提示）。 */
    val danger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    /** 语义色：信息（未检测 / 中性提示）。与 accent 同族但语义独立。 */
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,

    /** 遮罩。 */
    val scrim: Color,
)

/** 浅色：中性冷灰白，强调色取低饱和石板蓝。 */
val AivoraLightColors = AivoraColors(
    dark = false,
    background = Color(0xFFF6F7F9),
    surface = Color(0xFFFFFFFF),
    subtle = Color(0xFFE9EDF3),
    raised = Color(0xFFE1E7EE),
    text = Color(0xFF14161A),
    textSecondary = Color(0xFF48505C),
    textTertiary = Color(0xFF636B77),
    outline = Color(0xFFD3D9E2),
    divider = Color(0xFFE4E8EE),
    accent = Color(0xFF2F5F8F),
    onAccent = Color(0xFFFFFFFF),
    accentContainer = Color(0xFFE7EFF8),
    onAccentContainer = Color(0xFF1C4A78),
    success = Color(0xFF1F6B4A),
    successContainer = Color(0xFFE4F0E9),
    onSuccessContainer = Color(0xFF154C34),
    warning = Color(0xFF8A5A12),
    warningContainer = Color(0xFFF6EDDD),
    onWarningContainer = Color(0xFF68440D),
    danger = Color(0xFF9E3B36),
    dangerContainer = Color(0xFFF8E7E5),
    onDangerContainer = Color(0xFF742B27),
    info = Color(0xFF2F5F8F),
    infoContainer = Color(0xFFE7EFF8),
    onInfoContainer = Color(0xFF1C4A78),
    scrim = Color(0x520B152D),
)

/**
 * 深色：近黑中性（不是纯黑 —— 纯黑底上的亮字长时间看会晕），
 * 面层靠 4~6 级明度差分层，强调色提亮到 `#7FA8D9` 并用深底字。
 */
val AivoraDarkColors = AivoraColors(
    dark = true,
    background = Color(0xFF0F1115),
    surface = Color(0xFF16191F),
    subtle = Color(0xFF1C2027),
    raised = Color(0xFF242931),
    text = Color(0xFFE8EAEE),
    textSecondary = Color(0xFFA7AEBA),
    textTertiary = Color(0xFF848C99),
    outline = Color(0xFF2C323B),
    divider = Color(0xFF23282F),
    accent = Color(0xFF7FA8D9),
    onAccent = Color(0xFF0F1115),
    accentContainer = Color(0xFF1A2735),
    onAccentContainer = Color(0xFFBFD6EE),
    success = Color(0xFF5FBF8F),
    successContainer = Color(0xFF16281F),
    onSuccessContainer = Color(0xFF9FD8BC),
    warning = Color(0xFFD9A45B),
    warningContainer = Color(0xFF2A2116),
    onWarningContainer = Color(0xFFEBCB9C),
    danger = Color(0xFFE08A84),
    dangerContainer = Color(0xFF2C1917),
    onDangerContainer = Color(0xFFF3BDB8),
    info = Color(0xFF7FA8D9),
    infoContainer = Color(0xFF1A2735),
    onInfoContainer = Color(0xFFBFD6EE),
    scrim = Color(0x8C03060D),
)

/**
 * 间距阶梯：4 的倍数为主，2 用于紧凑行内。页面左右留白 20（沿用 v0.1.x 已调好的口径）。
 */
object AivoraSpace {
    val hair = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    /** 页面左右留白。 */
    val gutter = 20.dp

    /** Block 卡片内边距。 */
    val blockPadding = 16.dp

    /** Item 行内边距。 */
    val itemVertical = 12.dp

    /** 区块与区块之间的间隔。 */
    val sectionGap = 20.dp

    /** 悬浮底栏占位，列表底部要留出来。 */
    val navSpace = 132.dp

    /** 页面内容两侧都加 gutter 的写法收敛成一个 Modifier。 */
    val pageHorizontal = 20.dp
}

/**
 * 形状：只有三档圆角 + 胶囊。
 *
 * | 档 | 半径 | 用在哪 |
 * |---|---|---|
 * | chip | 10 | 状态标签、小控件、输入框内的小块 |
 * | inner | 14 | 弱化面板、行内嵌套容器 |
 * | block | 20 | 页面主区块卡片 |
 * | sheet | 20（仅上两角） | 底部弹层 |
 * | pill | 半圆端头 | 悬浮导航、搜索框、胶囊按钮 |
 */
object AivoraShape {
    val chip = RoundedCornerShape(10.dp)
    val inner = RoundedCornerShape(14.dp)
    val block = RoundedCornerShape(20.dp)
    val sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    /** 胶囊：半径给足够大即可，交给 RoundedCornerShape 按高度收敛。 */
    val pill = RoundedCornerShape(percent = 50)
}

/**
 * 描边宽度。
 */
object AivoraStroke {
    /** 常规边界：卡片、弱化面板。 */
    val hairline = 1.dp

    /** 强调：输入框聚焦、选中态。 */
    val emphasis = 1.5.dp
}

/**
 * 控件高度。触屏最小可点击区 48，视觉高度可以更小但必须留出触控区。
 */
object AivoraSize {
    val button = 40.dp
    val buttonSmall = 36.dp
    /** 图标按钮视觉尺寸。 */
    val iconButton = 20.dp
    /** 图标按钮触控区。 */
    val iconTouch = 44.dp
    val field = 52.dp
    /** 行内头像 / 首字母块。 */
    val avatar = 40.dp
}

/**
 * 字体阶梯：**叙事文本**与**技术数据**分开。
 *
 * | 角色 | 规格 | 用途 |
 * |---|---|---|
 * | pageTitle | 22 / 700 | 页面标题 |
 * | section | 16 / 600 | 区块标题 |
 * | body | 15 / 400 | 说明、提示、设置项描述 |
 * | bodyStrong | 15 / 600 | 配置名、记录名、设置项名 |
 * | meta | 13 / 400 | 时间、计数、辅助说明 |
 * | label | 12 / 600 + 字距 | 分组小标题、字段名 |
 * | mono | 13 / 等宽 | Endpoint、模型 ID、凭证、HTTP 码 |
 * | monoSmall | 12 / 等宽 | 耗时、少量元信息 |
 *
 * 【等宽只给技术数据】Endpoint / Key / 凭证 / HTTP 码 / 模型 ID 需要逐字符比对，
 * 等宽能让 `0/O`、`1/l` 分得清、列能对齐；正文用等宽反而降低阅读速度，禁止滥用。
 * 【下限 12sp】v0.1.2 有一批 10sp 与 11sp 的元信息（HTTP 码、导入来源标签），
 * 手机上要凑近才看得清，全部提到 12 起。
 */
object AivoraType {
    val pageTitle = TextStyle(
        fontSize = 22.sp, lineHeight = 30.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 0.sp,
    )
    val section = TextStyle(
        fontSize = 16.sp, lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val body = TextStyle(fontSize = 15.sp, lineHeight = 22.sp)
    val bodyStrong = TextStyle(
        fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold,
    )
    val meta = TextStyle(fontSize = 13.sp, lineHeight = 19.sp)
    val label = TextStyle(
        fontSize = 12.sp, lineHeight = 17.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp,
    )

    /** 技术数据：Endpoint、模型 ID、凭证、HTTP 码。 */
    val mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp, lineHeight = 19.sp,
        fontFeatureSettings = "tnum",
    )

    /** 技术数据的强调版：状态码这类需要一眼看到的值。 */
    val monoStrong = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp, lineHeight = 19.sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum",
    )

    /** 技术数据的次要行：耗时、字节数。仍是 12sp 下限。 */
    val monoSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp, lineHeight = 18.sp,
        fontFeatureSettings = "tnum",
    )
}

/**
 * 动效：安静、短、跟手。
 *
 * - 反馈类（按压、状态切换）≤ 160ms，快到「感觉不到在等」；
 * - 结构类（折叠、弹层）220–300ms，慢到「看得出从哪来」。
 *
 * 系统开启「移除动画」时，Compose 会自动把时长压到 0，这里不需要额外判断。
 */
object AivoraMotion {
    /** 按压、状态色过渡。 */
    const val PRESS_MS = 90
    /** 淡入淡出、图标切换。 */
    const val QUICK_MS = 160
    /** 折叠展开、列表项进出。 */
    const val STANDARD_MS = 220
    /** 弹层进。 */
    const val SHEET_IN_MS = 300
    /** 弹层出。 */
    const val SHEET_OUT_MS = 220
    /** 按压缩放比例。 */
    const val PRESSED_SCALE = 0.97f
}

/*
 * ═══════════ 对比度实测（WCAG 2.1，改色前请复算） ═══════════
 *
 * 浅色档：
 *   text/bg 16.90 · text2/bg 7.60 · text3/bg 5.02 · text3/subtle 4.75
 *   accent/bg 6.21 · accent/surface 6.66 · onAccent/accent 6.66
 *   onAccentContainer/accentContainer 7.87 · onSuccessContainer/successContainer 8.48
 *   onWarningContainer/warningContainer 7.47 · onDangerContainer/dangerContainer 8.29
 *   outline/surface 1.42 · divider/surface 1.23 · subtle/bg 1.06
 *
 * 深色档：
 *   text/bg 15.69 · text2/bg 8.46 · text3/bg 5.57 · text3/subtle 4.82
 *   accent/bg 7.65 · accent/surface 7.13 · onAccent/accent 7.65
 *   onAccentContainer/accentContainer 10.15 · onSuccessContainer/successContainer 9.59
 *   onWarningContainer/warningContainer 10.21 · onDangerContainer/dangerContainer 10.16
 *   outline/surface 1.36 · divider/surface 1.19 · subtle/bg 1.16
 *
 * 注意：白字压深色档 accent 只有 2.47，所以 onAccent 在深色档是深底字，不是白字。
 */