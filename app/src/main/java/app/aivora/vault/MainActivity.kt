package app.aivora.vault

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.aivora.vault.data.*
import app.aivora.vault.protocol.Aex
import app.aivora.vault.protocol.AexShare
import app.aivora.vault.protocol.Akcp
import app.aivora.vault.protocol.Backup
import kotlinx.serialization.json.Json
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.encodeToString
import java.text.DateFormat
import java.util.Date

private val Brand = Color(0xFF6750A4)
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 全局唯一的 edge-to-edge 入口：状态栏 / 导航栏透明，内容延伸至屏幕边缘。
        // 图标颜色不在这里写死，由 AivoraTheme 按当前主题的深浅实时设置，
        // 这样系统深色模式、手动切浅色/深色都能保持一致。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repo = (application as AivoraApplication).repository
        val prefs = getSharedPreferences("appearance", Context.MODE_PRIVATE)
        setContent {
            // 锁定界面此前被包在裸 MaterialTheme 里，跟随的是系统深浅而不是应用设置，
            // 于是出现「夜间模式下锁定页仍是亮的」。改为一并走 AivoraTheme。
            val dark = resolveDark(prefs)
            AivoraTheme(dark) {
                var unlocked by remember { mutableStateOf(!prefs.getBoolean("appLock", false)) }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (unlocked) AivoraApp(repo) else LockScreen { authenticate(this) { unlocked = it } }
                }
            }
        }
    }
}

/** 读取外观设置并解析出「当前是否深色」。跟随系统时用 isSystemInDarkTheme 的等价判定。 */
@Composable
private fun resolveDark(prefs: android.content.SharedPreferences): Boolean {
    val mode = runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)
    return when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

/**
 * 全应用唯一的主题入口。
 *
 * 除了配色，还负责状态栏 / 导航栏图标颜色的跟随：
 *  - 深色主题 → 浅色图标（isAppearanceLightStatusBars = false）
 *  - 浅色主题 → 深色图标（isAppearanceLightStatusBars = true）
 * 必须在 setContent 内部调用，才能随主题变化重新执行；而在 Activity 的
 * onCreate 里设一次会在切换主题后失效，这正是顶部图标看不清的原因。
 */
/**
 * 视觉 token。
 *
 * 之前圆角和配色只在主题里粗放地给了两个值，各页面又各自写 12/13/14/20/28dp 的圆角、
 * 各自挑灰字颜色。这里把层级收敛成三种形状与一组间距常量，新代码只从这里取值。
 */
object Tokens {
    val CardShape = RoundedCornerShape(20.dp)
    val InnerShape = RoundedCornerShape(14.dp)
    val PillShape = RoundedCornerShape(24.dp)

    val GapXs = 4.dp
    val GapS = 8.dp
    val GapM = 12.dp
    val GapL = 16.dp
    val PagePadding = 20.dp

    /** 底部悬浮导航占用的空间，列表底部要留出来。 */
    val NavSpace = 132.dp
}

/** 浅色方案：补齐 surfaceContainer 层级，卡片不再靠边框区分。 */
private val LightScheme = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    background = Color(0xFFFBF8FD),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFE2DCEC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2FA),
    surfaceContainer = Color(0xFFEFEBF5),
    surfaceContainerHigh = Color(0xFFE9E4F0),
    surfaceContainerHighest = Color(0xFFE3DEEA),
    error = Color(0xFFB3261E),
)

/** 深色方案：补齐容器层级，避免深色下所有块糊成一片。 */
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF1D1B20),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF3B3742),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF211F26),
    surfaceContainer = Color(0xFF2B2930),
    surfaceContainerHigh = Color(0xFF36343B),
    surfaceContainerHighest = Color(0xFF3F3C45),
    error = Color(0xFFF2B8B5),
)

@Composable
fun AivoraTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            small = Tokens.InnerShape,
            medium = Tokens.CardShape,
            large = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
private fun authenticate(activity: FragmentActivity, done: (Boolean) -> Unit) {
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = done(true)
        override fun onAuthenticationError(code: Int, message: CharSequence) { done(false) }
    })
    prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("验证身份以打开 Aivora").setSubtitle("使用生物识别或设备锁屏密码").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL).build())
}
@Composable private fun LockScreen(auth: () -> Unit) {
    LaunchedEffect(Unit) { auth() }
    // 锁定页是全屏独立页面，同样走 edge-to-edge；用 safeDrawing 让内容避开状态栏与导航栏。
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) { Icon(Lucide.Lock, null, Modifier.size(44.dp), tint = Brand); Spacer(Modifier.height(18.dp)); Text("Aivora 已锁定", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(18.dp)); LabelButton(auth, Lucide.Lock, "验证身份") }
    }
}
enum class Page(val title: String) { Configs("服务配置"), Diagnostics("密钥检测"), Records("导出记录"), Settings("设置"), About("关于"), Protocol("适配协议") }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable fun AivoraApp(repo: VaultRepository) {
    val context = LocalContext.current; val activity = context as FragmentActivity
    val prefs = remember { context.getSharedPreferences("appearance", Context.MODE_PRIVATE) }
    var themeMode by remember { mutableStateOf(runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)) }
    var locked by remember { mutableStateOf(prefs.getBoolean("appLock", false)) }
    var page by remember { mutableStateOf(Page.Configs) }
    BackHandler(enabled = page == Page.About || page == Page.Protocol) { page = if (page == Page.Protocol) Page.About else Page.Settings }
    var configs by remember { mutableStateOf(listOf<ServiceConfig>()) }
    var records by remember { mutableStateOf(listOf<ExportRecord>()) }
    // 「端点纠正」开关：默认开启。关闭后既不规范化也不试候选路径，仅影响未来操作。
    var endpointFix by remember { mutableStateOf(prefs.getBoolean("endpointFix", true)) }
    var editing by remember { mutableStateOf<ServiceConfig?>(null) }; var showEditor by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }; val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { repo.dao.configs().collectLatest { configs = it } }
    LaunchedEffect(Unit) { repo.dao.exports().collectLatest { records = it } }
    LaunchedEffect(message) { if (message.isNotEmpty()) { snack.showSnackbar(message); message = "" } }
    fun changeLock(enabled: Boolean) {
        if (enabled) {
            val available = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            if (available != BiometricManager.BIOMETRIC_SUCCESS) { android.widget.Toast.makeText(context, "请先在系统中设置锁屏密码或生物识别", android.widget.Toast.LENGTH_LONG).show(); return }
            authenticate(activity) { if (it) { prefs.edit().putBoolean("appLock", true).apply(); locked = true } }
        } else authenticate(activity) { if (it) { prefs.edit().putBoolean("appLock", false).apply(); locked = false } }
    }
    val dark = when (themeMode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    AivoraTheme(dark) {
        Box(Modifier.fillMaxSize().background(Color.Transparent)) {
            // 背景延伸到状态栏后面，页面内容则按 insets 让位，避免被时间/电量图标压住。
            Scaffold(containerColor = Color.Transparent, snackbarHost = { SnackbarHost(snack) }) { pad ->
                Column(Modifier.padding(pad).fillMaxSize()) {
                    // 【v0.1.0·修「顶部多出一段空白」】这里原本还有一条
                    //   `Spacer(windowInsetsTopHeight(statusBars))`，理由是「edge-to-edge 下
                    //   Scaffold 不吃状态栏 inset」。实测**已经吃过了**：上面的 `pad` 就来自
                    //   Scaffold 的 contentWindowInsets，再补一条等于把状态栏高度算两遍，
                    //   表现为标题下方莫名多一截空白、长列表首屏少显示一行。
                    //   现在只保留 `pad` 一处让位；背景仍由外层 Box 延伸到状态栏后面。
                    // 标题栏固定：之前它跟着内容一起滚走，长列表里切页会失去方位感。
                    Column(Modifier.padding(horizontal = Tokens.PagePadding)) {
                        Spacer(Modifier.height(20.dp))
                        PageHeader(page) { page = if (page == Page.Protocol) Page.About else if (page == Page.About) Page.Settings else Page.Configs }
                    }
                    // 页面内容自己滚动；切换时以底部为圆心扩散铺开，避免硬切。
                    CircularRevealBox(revealKey = page, modifier = Modifier.weight(1f)) {
                        when (page) {
                            Page.Configs -> ConfigPage(configs, repo, packageVersion(LocalContext.current), endpointFix, { editing = it; showEditor = true }, { editing = null; showEditor = true }, { page = it }) { message = it }
                            Page.Diagnostics -> ScrollPage { DiagnosticsPage(configs, repo, packageVersion(LocalContext.current)) { message = it } }
                            Page.Records -> RecordsPage(records, configs, repo, packageVersion(LocalContext.current)) { message = it }
                            Page.Settings -> ScrollPage { SettingsPage(repo, prefs, themeMode, { themeMode = it; prefs.edit().putString("theme", it.name).apply() }, locked, ::changeLock, endpointFix, { endpointFix = it; prefs.edit().putBoolean("endpointFix", it).apply() }, { page = Page.About }) { message = it } }
                            Page.About -> ScrollPage(bottomSpace = 36.dp) { AboutPage { page = Page.Protocol } }
                            Page.Protocol -> ScrollPage(bottomSpace = 36.dp) { ProtocolPage() }
                        }
                    }
                }
            }
            if (page in listOf(Page.Configs, Page.Diagnostics, Page.Records, Page.Settings)) FloatingNav(page, Modifier.align(Alignment.BottomCenter)) { page = it }
        }
        if (showEditor) ConfigEditor(editing, repo, endpointFix, { showEditor = false }) { message = it }
    }
}
@Composable private fun PageHeader(page: Page, back: () -> Unit) {
    // 【v0.1.0】54dp 是给 1.0 倍字体量身定的死高：系统字体放大到 1.3 后，
    //   headlineSmall + 加粗会把行高顶到 54 以上，标题被裁、下划线与图标错位。
    //   改成 heightIn(min=) —— 正常字号下高度完全一致，大字号时才自动撑开。
    Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
        if (page !in listOf(Page.Configs, Page.Diagnostics, Page.Settings)) { IconButton(back) { Icon(Lucide.ArrowLeft, "返回") }; Spacer(Modifier.width(8.dp)) }
        else { Surface(Modifier.size(42.dp), color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(14.dp)) { Box(contentAlignment = Alignment.Center) { Text("A", color = Color.White, fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold) } }; Spacer(Modifier.width(12.dp)) }
        Text(page.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }; Spacer(Modifier.height(18.dp))
}
/** 非列表页的滚动容器：统一页边距，并在底部留出悬浮导航的空间。 */
@Composable
private fun ScrollPage(
    bottomSpace: Dp = Tokens.NavSpace,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.PagePadding)
    ) {
        content()
        Spacer(Modifier.height(bottomSpace))
    }
}

@Composable private fun FloatingNav(selected: Page, modifier: Modifier = Modifier, select: (Page) -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(30.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = .58f)))))
        Surface(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .91f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .32f)),
            shadowElevation = 8.dp
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceAround) {
                listOf(Page.Configs to Lucide.List, Page.Diagnostics to Lucide.Activity, Page.Records to Lucide.History, Page.Settings to Lucide.Settings).forEach { (p, icon) ->
                    val active = selected == p
                    // 底色过渡而不是硬切，切换时能看出「从哪个走到哪个」。
                    val pill by animateColorAsState(
                        targetValue = if (active) MaterialTheme.colorScheme.primary.copy(alpha = .14f) else Color.Transparent,
                        animationSpec = tween(200),
                        label = "navPill",
                    )
                    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(pill)
                            .tappable(
                                onClick = { if (!active) select(p) },
                                shape = RoundedCornerShape(18.dp),
                                haptic = true,
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(icon, p.title, Modifier.size(22.dp), tint = tint)
                        Spacer(Modifier.height(3.dp))
                        Text(p.title, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1, softWrap = false, color = tint)
                    }
                }
            }
        }
    }
}
/** 卡片层级：Muted 用底色分层，Outlined 用于需要强调或被选中的块。 */
enum class CardVariant { Muted, Outlined }

@Composable
fun AppCard(
    variant: CardVariant = CardVariant.Muted,
    content: @Composable ColumnScope.() -> Unit,
) = Surface(
    Modifier.fillMaxWidth().padding(bottom = Tokens.GapM),
    shape = Tokens.CardShape,
    color = if (variant == CardVariant.Outlined) MaterialTheme.colorScheme.surface
    else MaterialTheme.colorScheme.surfaceContainerLow,
    border = if (variant == CardVariant.Outlined) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(.6f))
    } else null,
) { Column(Modifier.padding(Tokens.GapL), content = content) }

/**
 * 统一空状态：图标 + 主文案 + 一句说明 + 可选主操作。
 * 各页面之前都用一个放大镜图标配一句话，用户看不出「这里本该有什么、下一步做什么」。
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    hint: String? = null,
    action: (@Composable () -> Unit)? = null,
) = Column(
    Modifier.fillMaxWidth().padding(vertical = 36.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Surface(
        Modifier.size(56.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
    Spacer(Modifier.height(14.dp))
    Text(
        title,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        // 【v0.1.0】空状态标题居中说明「这页是空的」，不居中会左对齐贴在圆形图标下方，
        //   与整体对称构图割裂。
        textAlign = TextAlign.Center,
    )
    if (hint != null) {
        Spacer(Modifier.height(6.dp))
        Text(hint, fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
    if (action != null) { Spacer(Modifier.height(16.dp)); action() }
}
@Composable
fun LabelButton(
    onClick: () -> Unit,
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // 按钮自己管按压反馈：Material 按钮默认只有波纹，没有下压位移。
    val source = remember { MutableInteractionSource() }
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.pressScale(source),
        enabled = enabled,
        interactionSource = source,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, maxLines = 1)
    }
}
@Composable fun ConfigSummary(c: ServiceConfig) {
    Row(verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(42.dp), color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(13.dp)) { Box(contentAlignment = Alignment.Center) { Text(c.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer) } }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(c.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); if (c.notes.isNotBlank()) Text(c.notes, fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) } }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ConfigEditor(old: ServiceConfig?, repo: VaultRepository, endpointFix: Boolean, close: () -> Unit, toast: (String) -> Unit) {
    var name by remember { mutableStateOf(old?.name.orEmpty()) }; var provider by remember { mutableStateOf(old?.provider.orEmpty()) }; var expanded by remember { mutableStateOf(false) }; var endpoint by remember { mutableStateOf(old?.endpoint.orEmpty()) }; var key by remember { mutableStateOf("") }; var notes by remember { mutableStateOf(old?.notes.orEmpty()) }; var visible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope(); val clipboard = LocalClipboardManager.current
    LaunchedEffect(old) { if (old?.encryptedKey?.isNotEmpty() == true) key = runCatching { repo.crypto.decrypt(old.encryptedKey) }.getOrElse { "" } }
    // 端点纠正结果：只在输入框下方显示灰字，不改动已保存配置，保存时才写回。
    val normalized = remember(endpoint, endpointFix) {
        if (endpoint.isBlank()) null else Endpoint.normalize(endpoint, endpointFix)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = close,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                if (old == null) "添加服务配置" else "编辑服务配置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Field("配置名称", name) { name = it }
            ExposedDropdownMenuBox(expanded, { expanded = it }) {
                OutlinedTextField(
                    provider, {}, Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    readOnly = true,
                    label = { Text("服务商模板") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                )
                ExposedDropdownMenu(expanded, { expanded = false }) {
                    providerTemplates.forEach { t ->
                        DropdownMenuItem(text = { Text(t.name) }, onClick = {
                            provider = if (t.name == "空白模板" || t.name == "自定义") "" else t.name
                            endpoint = t.endpoint
                            expanded = false
                        })
                    }
                }
            }
            Field("API Endpoint（选填）", endpoint) { endpoint = it }
            normalized?.let { r ->
                if (r.note.isNotEmpty()) Text(
                    r.note,
                    Modifier.padding(start = 4.dp, bottom = 4.dp),
                    fontSize = 11.sp, lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                key, { key = it }, Modifier.fillMaxWidth(),
                label = { Text("API Key（选填）") },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Row {
                        IconButton({ visible = !visible }) { Icon(if (visible) Lucide.EyeOff else Lucide.Eye, "显示或隐藏密钥") }
                        IconButton({ clipboard.setText(AnnotatedString(key)); toast("API Key 已复制") }) { Icon(Lucide.Copy, "复制密钥") }
                    }
                },
            )
            Field("备注（选填）", notes) { notes = it }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(close, Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = {
                        if (name.isBlank()) toast("请填写配置名称")
                        else scope.launch {
                            val finalEndpoint = if (endpoint.isBlank()) "" else Endpoint.normalize(endpoint, endpointFix).endpoint
                            repo.dao.save(ServiceConfig(old?.id ?: 0, name.trim(), provider, if (key.isBlank()) "" else repo.crypto.encrypt(key.trim()), finalEndpoint, "", "", notes, old?.enabled ?: true, old?.isDefault ?: false, old?.createdAt ?: System.currentTimeMillis(), 0, "配置已更新，请重新检测", null, old?.extensionsJson ?: "{}", "[]", old?.source))
                            close()
                            toast("配置已保存")
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("保存") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
@Composable fun Field(label: String, value: String, change: (String) -> Unit) = OutlinedTextField(value, change, Modifier.fillMaxWidth().padding(vertical = 5.dp), label = { Text(label) }, singleLine = true)
@Composable fun DiagnosticsPage(configs: List<ServiceConfig>, repo: VaultRepository, appVersion: String, toast: (String) -> Unit) {
    val scope = rememberCoroutineScope(); val checker = remember(appVersion) { KeyDiagnostics(appVersion) }; var job by remember { mutableStateOf<Job?>(null) }; var currentId by remember { mutableStateOf<Long?>(null) }; var results by remember { mutableStateOf(mapOf<Long, List<ModelResult>>()) }; var expanded by remember { mutableStateOf(setOf<Long>()) }
    LaunchedEffect(configs) { if (job?.isActive != true) results = configs.associate { it.id to try { Json.decodeFromString<List<ModelResult>>(it.modelResultsJson) } catch (_: Exception) { emptyList() } } }
    suspend fun persist(c: ServiceConfig, status: String, items: List<ModelResult>) { repo.dao.save((repo.dao.byId(c.id) ?: c).copy(checkStatus = status, lastCheckedAt = System.currentTimeMillis(), modelResultsJson = Json.encodeToString(items))) }
    suspend fun diagnose(c: ServiceConfig) {
        currentId = c.id
        if (c.endpoint.isBlank() || c.encryptedKey.isBlank()) { repo.dao.save(c.copy(checkStatus = "请先补充 Endpoint 和 API Key")); toast("请先补充 Endpoint 和 API Key"); return }
        try {
            val key = repo.crypto.decrypt(c.encryptedKey); val modelIds = checker.fetchModels(c, key)
            val initial = modelIds.map { ModelResult(it, "等待检测") }; results = results + (c.id to initial)
            repo.dao.save(c.copy(models = modelIds.joinToString(","), checkStatus = "已成功拉取模型", modelResultsJson = Json.encodeToString(initial)))
            modelIds.forEachIndexed { index, model ->
                currentCoroutineContext().ensureActive()
                results = results + (c.id to results.getValue(c.id).mapIndexed { i, r -> if (i == index) r.copy(status = "检测中") else r })
                val result = checker.testModel(c, key, model)
                val updated = results.getValue(c.id).mapIndexed { i, r -> if (i == index) result else r }
                results = results + (c.id to updated); persist(c, "已检测 ${index + 1}/${modelIds.size}", updated)
            }
            persist(c, "检测完成：${results.getValue(c.id).count { it.status == "可用" }}/${modelIds.size} 个模型可用", results.getValue(c.id))
        } catch (e: CancellationException) { repo.dao.save((repo.dao.byId(c.id) ?: c).copy(checkStatus = "检测已停止")); throw e }
        catch (e: Exception) { repo.dao.save((repo.dao.byId(c.id) ?: c).copy(checkStatus = e.message ?: "检测失败", lastCheckedAt = System.currentTimeMillis())) }
    }
    fun start(c: ServiceConfig) { job = scope.launch { diagnose(c); currentId = null; job = null } }
    if (configs.isEmpty()) AppCard { Empty("暂无可检测配置") } else configs.forEach { c -> AppCard {
        Row(Modifier.fillMaxWidth().clickable { expanded = expanded.toMutableSet().apply { if (!add(c.id)) remove(c.id) } }, verticalAlignment = Alignment.CenterVertically) { Box(Modifier.weight(1f)) { ConfigSummary(c) }; Icon(if (c.id in expanded) Lucide.ChevronUp else Lucide.ChevronDown, "展开或收起") }
        Text(c.checkStatus, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp); Text("检测时间：${if (c.lastCheckedAt > 0) DateFormat.getDateTimeInstance().format(Date(c.lastCheckedAt)) else "未检测"}", fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { LabelButton({ expanded += c.id; start(c) }, Lucide.Play, if (currentId == c.id && job?.isActive == true) "检测中" else "检测", enabled = job?.isActive != true); if (currentId == c.id && job?.isActive == true) LabelButton({ job?.cancel(); job = null; currentId = null }, Lucide.Stop, "停止") }
        AnimatedVisibility(c.id in expanded) { Column { val items = results[c.id].orEmpty(); if (items.isEmpty()) Text("点击检测后自动拉取并展示全部模型", Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp); items.forEach { r -> Surface(Modifier.fillMaxWidth().padding(vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(12.dp)) { Row { Text(r.name, Modifier.weight(1f), fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold); Text(r.status, fontSize = 12.sp, lineHeight = 18.sp, color = if (r.status == "可用") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }; Text("HTTP ${r.httpStatus ?: "—"} · ${r.latencyMs?.let { "$it ms" } ?: "—"}", fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); if (r.detail.isNotEmpty()) Text(r.detail, fontSize = 12.sp, lineHeight = 18.sp); if (r.status == "不可用") LabelButton({ job = scope.launch { try { val updated = checker.testModel(c, repo.crypto.decrypt(c.encryptedKey), r.name); val list = results.getValue(c.id).map { if (it.name == r.name) updated else it }; results = results + (c.id to list); repo.dao.byId(c.id)?.let { repo.dao.save(it.copy(modelResultsJson = Json.encodeToString(list))) } } finally { job = null } } }, Lucide.Refresh, "重测此模型", enabled = job?.isActive != true) } } } } }
    } }; AppCard { Text("仅在点击后联网，串行检测该配置的全部模型；可能消耗少量 API 额度。", fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
@Composable private fun ExpandableCard(title: String, icon: ImageVector, subtitle: String = "", content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    // 箭头跟着展开进度旋转，而不是两帧硬切图标；整行可点，不再只有小箭头有效。
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = Motion.EXPAND_MS),
        label = "chevron",
    )
    AppCard {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Tokens.InnerShape)
                .tappable(onClick = { expanded = !expanded }, shape = Tokens.InnerShape, haptic = true)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null); Spacer(Modifier.width(12.dp))
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    Modifier.padding(end = 8.dp),
                    fontSize = 12.sp, lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Lucide.ChevronDown, "展开或收起", Modifier.rotate(chevron))
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(durationMillis = Motion.EXPAND_MS),
                expandFrom = Alignment.Top,
            ) + fadeIn(tween(160)),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = Motion.EXPAND_MS),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(tween(120)),
        ) { Column(Modifier.padding(top = 12.dp), content = content) }
    }
}
@Composable fun SettingsPage(repo: VaultRepository, prefs: android.content.SharedPreferences, mode: ThemeMode, change: (ThemeMode) -> Unit, locked: Boolean, lockChange: (Boolean) -> Unit, endpointFix: Boolean, endpointFixChange: (Boolean) -> Unit, about: () -> Unit, toast: (String) -> Unit) {
    // 端点纠正卡片置顶：影响所有新建/编辑配置与密钥检测的行为。
    ExpandableCard("端点纠正", Lucide.Wand, if (endpointFix) "已开启" else "已关闭") {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text("自动纠正 API 端点", Modifier.weight(1f)); Switch(endpointFix, endpointFixChange) }
        Text("补全 /v1、剥离重复的 /chat/completions 等接口后缀；检测失败时自动尝试候选路径。官方模板地址不会被改动。", fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ExpandableCard("外观", Lucide.Sun, when (mode) { ThemeMode.SYSTEM -> "跟随系统"; ThemeMode.LIGHT -> "浅色模式"; ThemeMode.DARK -> "深色模式" }) {
        listOf(Triple(ThemeMode.SYSTEM, "跟随系统", Lucide.Monitor), Triple(ThemeMode.LIGHT, "浅色模式", Lucide.Sun), Triple(ThemeMode.DARK, "深色模式", Lucide.Moon)).forEach { (value, label, icon) ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { change(value) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null); Spacer(Modifier.width(12.dp)); Text(label, Modifier.weight(1f)); RadioButton(mode == value, { change(value) }) }
        }
    }
    ExpandableCard("安全", Lucide.Lock) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text("打开软件时验证身份", Modifier.weight(1f)); Switch(locked, lockChange) }
        Text("使用系统生物识别或设备锁屏密码，验证通过后才能查看配置。", fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    BackupSection(repo, prefs, change, toast)
    AppCard { Row(Modifier.fillMaxWidth().clickable { about() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Lucide.Info, null); Spacer(Modifier.width(12.dp)); Text("关于", Modifier.weight(1f), fontWeight = FontWeight.Medium); Icon(Lucide.ChevronRight, null, Modifier.size(19.dp)) } }
}
@Composable private fun BackupSection(repo: VaultRepository, prefs: android.content.SharedPreferences, themeChange: (ThemeMode) -> Unit, toast: (String) -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val clipboard = LocalClipboardManager.current
    var exportResult by remember { mutableStateOf<Backup.Result?>(null) }; var importUri by remember { mutableStateOf<Uri?>(null) }; var credential by remember { mutableStateOf("") }; var pending by remember { mutableStateOf<Backup.Payload?>(null) }; var busy by remember { mutableStateOf(false) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> val data = exportResult; if (uri != null && data != null) scope.launch(Dispatchers.IO) { try { context.contentResolver.openOutputStream(uri)?.use { it.write(data.bytes) } ?: error("无法写入文件"); withContext(Dispatchers.Main) { exportResult = null; toast("全量备份导出成功") } } catch (e: Exception) { withContext(Dispatchers.Main) { toast(e.message ?: "备份失败") } } } }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { importUri = it; credential = "" }
    ExpandableCard("备份", Lucide.Database) {
        LabelButton({ scope.launch { busy = true; try { exportResult = withContext(Dispatchers.IO) { repo.createBackup(prefs.getString("theme", "SYSTEM")!!) } } catch (e: Exception) { toast(e.message ?: "无法生成备份") }; busy = false } }, Lucide.Export, "导出备份", Modifier.fillMaxWidth(), !busy)
        Spacer(Modifier.height(8.dp)); LabelButton({ opener.launch(arrayOf("application/octet-stream", "*/*")) }, Lucide.Import, "导入备份", Modifier.fillMaxWidth())
        Text("备份包含全部配置、检测结果和外观设置。导入会覆盖当前全部数据。", Modifier.padding(top = 10.dp), fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    exportResult?.let { result -> AlertDialog({ exportResult = null }, title = { Text("导出全量备份") }, text = { Column { Text("请保存备份凭证。恢复时必须输入相同凭证。"); SelectionContainer { Text(result.credential, Modifier.padding(vertical = 14.dp), style = MaterialTheme.typography.bodySmall) }; LabelButton({ clipboard.setText(AnnotatedString(result.credential)); Toast.makeText(context, "备份凭证已复制", Toast.LENGTH_SHORT).show() }, Lucide.Copy, "复制凭证") } }, confirmButton = { Button({ saver.launch("Aivora-全量备份-${System.currentTimeMillis()}${Backup.EXTENSION}") }) { Text("选择保存位置") } }, dismissButton = { TextButton({ exportResult = null }) { Text("取消") } }) }
    importUri?.let { uri -> AlertDialog({ importUri = null }, title = { Text("输入备份凭证") }, text = { OutlinedTextField(credential, { credential = it }, Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), label = { Text("备份凭证") }, singleLine = true) }, confirmButton = { Button({ scope.launch { busy = true; try { val data = withContext(Dispatchers.IO) { Backup.import(context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("文件读取失败"), credential) }; pending = data; importUri = null } catch (e: Exception) { toast(e.message ?: "备份无效") }; busy = false } }, enabled = !busy) { Text("验证备份") } }, dismissButton = { TextButton({ importUri = null }) { Text("取消") } }) }
    pending?.let { data -> AlertDialog({ pending = null }, title = { Text("覆盖当前全部数据？") }, text = { Text("备份包含 ${data.configs.size} 个配置。恢复后当前配置、检测结果和外观设置将被替换，此操作无法撤销。") }, confirmButton = { Button({ scope.launch { try { repo.replaceAll(data, prefs); themeChange(ThemeMode.valueOf(data.theme)); pending = null; toast("全量数据恢复成功") } catch (e: Exception) { toast(e.message ?: "恢复失败") } } }) { Text("确认覆盖恢复") } }, dismissButton = { TextButton({ pending = null }) { Text("取消") } }) }
}
@Composable fun AboutPage(protocol: () -> Unit) {
    val uri = LocalUriHandler.current
    AppCard { Text("Aivora", fontSize = 25.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold); Text("本地 AI 配置管理工具\n版本 ${packageVersion(LocalContext.current)} · AEX 1.1\nAGPL-3.0-only\n数据仅保存在当前设备，不依赖云端账户。") }
    AppCard { Text("开放生态", fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); LabelButton(protocol, Lucide.File, "适配协议", Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp)); LabelButton({ uri.openUri("https://github.com/Farewell-coder/Aivora") }, Lucide.Github, "GitHub 项目", Modifier.fillMaxWidth()) }
    AppCard { Text("安全边界", fontWeight = FontWeight.Bold); Text("凭证用于加密导出文件。第三方成功解密后可接触明文 API Key；请仅向可信服务提交密钥。", fontSize = 12.sp, lineHeight = 18.sp) }
}
@Composable fun ProtocolPage() {
    AppCard { Text("AEX 1.1", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Aivora Exchange Protocol 是开放的配置交换协议，版本与加密方案独立演进。") }
    AppCard { Text("文件与加密", fontWeight = FontWeight.Bold); Text("文件后缀：.akcp\n容器：8 字节 Magic + 4 字节头部长度 + UTF-8 JSON 头部 + 密文\n凭证：随机 256 位 Base32 字符串（52 字符，按 8 位分组）\n密钥派生：PBKDF2-HMAC-SHA256，600000 次迭代，16 字节随机 salt\n内容加密：AES-256-GCM，128 位认证标签，12 字节随机 nonce\n完整性：整个头部作为 AAD，篡改协议版本或加密参数会直接导致解密失败\n凭证不写入文件，仅用于派生密钥。") }
    AppCard { Text("导入与导出", fontWeight = FontWeight.Bold); Text("导出时可将单个或多个配置加密为 .akcp 文件；导入时选择文件并输入对应凭证。凭证错误、文件被修改或格式不支持时不会写入配置。\n\n导入同时支持第三方软件导出的明文配置（Chatbox、Continue、LobeChat、CC Switch、New API、Cherry Studio、.env），这些格式按其自身规则解析，无需凭证。\n\n全量备份使用独立的 .aivora-backup 格式，仅用于 Aivora 数据恢复，不与 AEX 配置交换文件混用。") }
}
@Composable fun Empty(text: String) = Column(
    Modifier.fillMaxWidth().padding(vertical = 34.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Icon(
        Lucide.Search,
        null,
        // 【v0.1.0·图标尺寸统一】全 App 只有三档：按钮内 18/20、导航 22/24、空状态 26。
        //   这里原来是 26dp，与 EmptyState 的 24dp 不一致，同一类「空」两种大小。
        Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f),
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        textAlign = TextAlign.Center,
    )
}

/** Reads versionName from the installed package; falls back to a neutral label. */
fun packageVersion(context: android.content.Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull().orEmpty().ifBlank { "unknown" }
