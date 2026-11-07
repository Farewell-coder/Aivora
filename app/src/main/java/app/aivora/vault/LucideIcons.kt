package app.aivora.vault

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.File
import com.composables.icons.lucide.FileInput
import com.composables.icons.lucide.FileOutput
import com.composables.icons.lucide.Github
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Link2
import com.composables.icons.lucide.List
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Monitor
import com.composables.icons.lucide.Ellipsis
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.SquarePen
import com.composables.icons.lucide.Sun
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.Upload
import com.composables.icons.lucide.WandSparkles
import com.composables.icons.lucide.X
import com.composables.icons.lucide.Lucide as LucideSet

/**
 * Lucide 图标外观层（v0.1.0 起）。
 *
 * 【来源】`com.composables:icons-lucide-android:1.1.0`，图标几何与
 *   [lucide-icons/lucide](https://github.com/lucide-icons/lucide) 官方 SVG 逐字节一致，
 *   MIT 许可（见 NOTICE）。
 *
 * 【为什么还留这一层】改造前这里是一份**手写近似路径**（65 行），复制图标画成了畸形方框、
 *   分享图标其实是上传箭头、设置图标是滑杆 —— 用户反馈的「UI 错乱」里，图标占了很大一部分。
 *   现在全部换成官方几何，但调用点仍是 `Lucide.Copy` 这种写法，业务代码一行不用改，
 *   后续要再换图标库也只动这一个文件。
 *
 * 【尺寸】图标统一 24dp 视口 / 线宽 2（库内自带），实际渲染尺寸由调用方给，
 *   规范是：按钮内 18/20dp、导航 22/24dp、空状态 26dp；同一屏内不要混用三档以上。
 */
object Lucide {

    // ---- 导航 ----
    val List: ImageVector get() = LucideSet.List
    val Activity: ImageVector get() = LucideSet.Activity
    val Settings: ImageVector get() = LucideSet.Settings
    val Info: ImageVector get() = LucideSet.Info
    val ArrowLeft: ImageVector get() = LucideSet.ArrowLeft
    val ChevronRight: ImageVector get() = LucideSet.ChevronRight
    val ChevronDown: ImageVector get() = LucideSet.ChevronDown
    val ChevronUp: ImageVector get() = LucideSet.ChevronUp

    // ---- 基础操作 ----
    val Plus: ImageVector get() = LucideSet.Plus
    val X: ImageVector get() = LucideSet.X
    val Check: ImageVector get() = LucideSet.Check
    val Search: ImageVector get() = LucideSet.Search
    val Copy: ImageVector get() = LucideSet.Copy
    val Trash: ImageVector get() = LucideSet.Trash2
    val Edit: ImageVector get() = LucideSet.SquarePen
    val Refresh: ImageVector get() = LucideSet.RefreshCw

    // ---- 收发 / 交换 ----
    /** 导入：箭头**向下**入盘，语义是「把外部内容收进来」。 */
    val Import: ImageVector get() = LucideSet.Download
    /** 导出：箭头**向上**出盘，与 [Import] 严格成对。 */
    val Export: ImageVector get() = LucideSet.Upload
    /** 分享：官方 share-2（三个节点 + 连线），此前误用的是上传箭头。 */
    val Share: ImageVector get() = LucideSet.Share2
    /** 深链：官方 link-2。 */
    val Link: ImageVector get() = LucideSet.Link2
    /** 单个文件。 */
    val File: ImageVector get() = LucideSet.File
    /** 文件输入（AEX / 深链接收语义）。 */
    val FileInput: ImageVector get() = LucideSet.FileInput
    /** 文件输出（导出记录语义）。 */
    val FileOutput: ImageVector get() = LucideSet.FileOutput

    // ---- 安全 / 服务 ----
    /** 锁：官方 lock（此前画成了直角矩形）。 */
    val Lock: ImageVector get() = LucideSet.Lock
    /** 密钥：比锁更贴近「API Key 检测」。 */
    val KeyRound: ImageVector get() = LucideSet.KeyRound
    /** 服务端：端点 / 网关。 */
    val Server: ImageVector get() = LucideSet.Server
    /** 数据库：本地存储语义。 */
    val Database: ImageVector get() = LucideSet.Database
    /** 显隐密钥。 */
    val Eye: ImageVector get() = LucideSet.Eye
    val EyeOff: ImageVector get() = LucideSet.EyeOff

    // ---- 状态 ----
    /** 成功。 */
    val CircleCheck: ImageVector get() = LucideSet.CircleCheck
    /** 警告：导入失败 / 不可用模型。 */
    val TriangleAlert: ImageVector get() = LucideSet.TriangleAlert
    /** 运行中。 */
    val Play: ImageVector get() = LucideSet.Play
    /** 停止：官方 square（实心方块），此前是自绘的 Stop。 */
    val Stop: ImageVector get() = LucideSet.Square

    // ---- 外观 ----
    val Sun: ImageVector get() = LucideSet.Sun
    val Moon: ImageVector get() = LucideSet.Moon
    val Monitor: ImageVector get() = LucideSet.Monitor

    // ---- 其他语义 ----
    /** 端点纠正：官方 wand-sparkles。 */
    val Wand: ImageVector get() = LucideSet.WandSparkles
    /** 导出历史。 */
    val History: ImageVector get() = LucideSet.History
    /** 项目仓库。 */
    val Github: ImageVector get() = LucideSet.Github

    /**
     * 更多操作：官方 ellipsis（横向三点）。
     *
     * 之前「更多」菜单借用 [ChevronDown]，视觉上读作「展开本行」，与「打开操作菜单」
     * 是两种不同预期；而且行尾箭头与折叠卡的箭头同形，用户分不清点哪个会发生什么。
     */
    val More: ImageVector get() = LucideSet.Ellipsis
}