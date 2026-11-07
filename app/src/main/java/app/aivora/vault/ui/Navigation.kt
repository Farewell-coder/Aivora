package app.aivora.vault

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * 导航模型。
 *
 * 【为什么拆成两层】重构前是一个 `Page` 枚举装 6 个值，其中 4 个是主导航（底栏），
 * 2 个是二级页（关于 / 适配协议）。后果是同一份「这是不是主导航」的判断被复制了三处：
 * 底栏显不显示、返回链怎么走、标题栏要不要返回箭头 —— 每加一个页面就要改三个地方，
 * 而且三处口径早晚会漂。
 *
 * 现在拆成：
 *  - [Tab]：底栏的 4 个入口，**唯一**决定底栏显示。
 *  - [Leaf]：二级页，带自己的返回目标（见 [Leaf.parent]）。
 *  - [NavState]：当前 Tab + 当前二级页（null 表示在 Tab 本身）。
 *
 * 「底栏只在 leaf == null 时显示」这类规则因此只写一次。
 */
enum class Tab(val title: String) {
    Configs("服务配置"),
    Diagnostics("密钥检测"),
    Records("导出记录"),
    Settings("设置"),
}

enum class Leaf(val title: String) {
    About("关于"),
    Protocol("适配协议"),
}

/** 二级页的返回目标；null 表示返回所在 Tab。 */
val Leaf.parent: Leaf?
    get() = when (this) {
        Leaf.About -> null
        Leaf.Protocol -> Leaf.About
    }

/**
 * 导航状态。
 *
 * @param tab  当前所在 Tab（二级页也属于某个 Tab，返回时回到它）
 * @param leaf 当前打开的二级页；null 表示正在看 Tab 本体
 */
data class NavState(val tab: Tab = Tab.Configs, val leaf: Leaf? = null) {

    /** 底栏是否显示：只有看 Tab 本体时才显示，二级页全屏。 */
    val showsBottomBar: Boolean get() = leaf == null

    /** 标题栏是否要返回箭头。 */
    val showsBack: Boolean get() = leaf != null

    /** 当前显示的标题。 */
    val title: String get() = leaf?.title ?: tab.title

    /** 返回一步。返回 false 表示已经在根，交给系统处理（退出）。 */
    fun back(): NavState? = when {
        leaf == null -> null
        else -> {
            val parent = leaf!!.parent
            copy(leaf = parent)
        }
    }

    /** 进入某个二级页。路由器只接受属于当前上下文的页面，避免「从配置页跳到关于」。 */
    fun open(target: Leaf) = copy(leaf = target)

    /** 切换底栏 Tab：清掉二级页，回到该 Tab 本体。 */
    fun switchTo(target: Tab) = NavState(tab = target, leaf = null)
}

/**
 * 记住导航状态，并在进程被回收后恢复。
 *
 * 重构前用 `remember`，切到后台被系统回收再回来会跳回「服务配置」；
 * 现在用 `rememberSaveable`，配置、搜索词同理。
 */
@Composable
fun rememberNavState(): MutableNavState {
    var state by rememberSaveable(stateSaver = NavStateSaver) { mutableStateOf(NavState()) }
    return remember { MutableNavState({ state }, { state = it }) }
}

/**
 * 轻量包装，让调用点写 `nav.state` / `nav.update {}`，不必到处传 setter。
 *
 * 【为什么用 getter 而不是把值存进字段】导航是同步的：一次点击里可能连做两步
 * （例如「打开导出弹窗」→「切到深链选择」）。若把 `NavState` 的**值**捕获进对象，
 * 同一帧内的第二次 `update` 会基于过期快照计算，表现为「第二个动作被吞掉」。
 * 传 getter 后每次 `update` 都读当前 Composable 最新的状态。
 */
class MutableNavState(
    private val getter: () -> NavState,
    private val setter: (NavState) -> Unit,
) {
    val state: NavState get() = getter()
    fun update(transform: (NavState) -> NavState) = setter(transform(getter()))
    val current: NavState get() = getter()
}

/** 把 NavState 存成两个字符串，避免 Pomodoro 式的自定义 Parcelable。 */
private val NavStateSaver = Saver<NavState, String>(
    save = { "${it.tab.name}|${it.leaf?.name ?: ""}" },
    restore = { raw ->
        val parts = raw.split('|')
        val tab = runCatching { Tab.valueOf(parts.getOrElse(0) { "Configs" }) }.getOrDefault(Tab.Configs)
        val leaf = parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { runCatching { Leaf.valueOf(it) }.getOrNull() }
        NavState(tab, leaf)
    },
)