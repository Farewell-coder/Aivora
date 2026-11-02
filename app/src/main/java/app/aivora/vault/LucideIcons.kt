package app.aivora.vault

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Icons from Lucide (ISC), https://github.com/lucide-icons/lucide. */
object Lucide {
    private fun icon(vararg paths: String): ImageVector = ImageVector.Builder(
        "Lucide", 24.dp, 24.dp, 24f, 24f
    ).apply {
        paths.forEach { data ->
            addPath(
                PathParser().parsePathString(data).toNodes(), fill = null,
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()

    val List = icon("M8 6h13", "M8 12h13", "M8 18h13", "M3 6h.01", "M3 12h.01", "M3 18h.01")
    val Activity = icon("M22 12h-4l-3 9L9 3l-3 9H2")
    val Settings = icon("M4 21v-7", "M4 10V3", "M12 21v-9", "M12 8V3", "M20 21v-5", "M20 12V3", "M1 14h6", "M9 8h6", "M17 16h6")
    val Info = icon("M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20", "M12 16v-4", "M12 8h.01")
    val Plus = icon("M5 12h14", "M12 5v14")
    val Search = icon("M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16", "m21 21-4.3-4.3")
    val Import = icon("M12 3v12", "m7 10 5 5 5-5", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4")
    val Export = icon("M12 15V3", "m7 8 5-5 5 5", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4")
    /** Lucide "share" 图标：方框上方三个连接点。 */
    val Share = icon("M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8", "M16 6l-4-4-4 4", "M12 2v13")
    val Edit = icon("M12 20h9", "M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z")
    val Trash = icon("M3 6h18", "M8 6V4h8v2", "m19 6-1 14H6L5 6", "M10 11v5", "M14 11v5")
    val Copy = icon("M9 9h11v11H9z", "M4 15H3V4h11v1")
    val ChevronDown = icon("m6 9 6 6 6-6")
    val ChevronUp = icon("m18 15-6-6-6 6")
    val ChevronRight = icon("m9 18 6-6-6-6")
    val Play = icon("m6 3 14 9-14 9z")
    val Stop = icon("M5 5h14v14H5z")
    val Refresh = icon("M20 11a8.1 8.1 0 0 0-15.5-2M4 4v5h5", "M4 13a8.1 8.1 0 0 0 15.5 2M20 20v-5h-5")
    val Sun = icon("M12 18a6 6 0 1 0 0-12 6 6 0 0 0 0 12", "M12 2v2", "M12 20v2", "m4.93 4.93 1.42 1.42", "m17.66 17.66 1.41 1.41", "M2 12h2", "M20 12h2", "m6.34 17.66-1.41 1.41", "m19.07 4.93-1.41 1.41")
    val Moon = icon("M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9")
    val Monitor = icon("M2 3h20v14H2z", "M8 21h8", "M12 17v4")
    val ArrowLeft = icon("m12 19-7-7 7-7", "M19 12H5")
    val Check = icon("m20 6-11 11-5-5")
    val Eye = icon("M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12", "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6")
    val EyeOff = icon("m2 2 20 20", "M6.7 6.7C3.7 8.5 2 12 2 12s3.5 7 10 7c1.8 0 3.3-.5 4.6-1.2", "M10.7 5.1c.4-.1.9-.1 1.3-.1 6.5 0 10 7 10 7s.7 1.4 2 3", "M14.1 14.1a3 3 0 0 1-4.2-4.2")
    val Github = icon("M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.1-1.3-.4-2.5-1-3.5.3-1.2.3-2.5 0-3.5 0 0-1 0-3 1.5a10 10 0 0 0-5 0C9 2 8 2 8 2c-.3 1-.3 2.3 0 3.5-.6 1-1.1 2.2-1 3.5C7 13.5 10 15.5 13 15.5c-.6.6-.9 1.3-1 2.5V22", "M9 18c-4.5 2-5-2-7-2")
    val Lock = icon("M5 11h14v10H5z", "M8 11V7a4 4 0 0 1 8 0v4")
    val Database = icon("M12 8c5 0 9-1.1 9-2.5S17 3 12 3 3 4.1 3 5.5 7 8 12 8", "M3 5.5v6C3 12.9 7 14 12 14s9-1.1 9-2.5v-6", "M3 11.5v6C3 18.9 7 20 12 20s9-1.1 9-2.5v-6")
    val File = icon("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z", "M14 2v6h6", "M8 13h8", "M8 17h8")
    /** Lucide "history"：带箭头的时钟。 */
    val History = icon("M3 12a9 9 0 1 0 3-6.7", "M3 3v5h5", "M12 8v4l3 2")
    /** Lucide "wand"：魔法棒，用于端点纠正开关。 */
    val Wand = icon("m15 4 5 5", "M3 21 14 10", "m5 3 1 3 3 1-3 1-1 3-1-3-3-1 3-1z", "m19 14 .8 2.2L22 17l-2.2.8L19 20l-.8-2.2L16 17l2.2-.8z")
}
