package app.aivora.vault

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import app.aivora.vault.data.ExportRecord
import app.aivora.vault.data.ServiceConfig
import app.aivora.vault.data.VaultRepository
import app.aivora.vault.protocol.Backup
import app.aivora.vault.ui.ConfigEditor
import app.aivora.vault.ui.DiagnosticsPage
import app.aivora.vault.ui.kit.AivoraField
import app.aivora.vault.ui.kit.AivoraScaffold
import app.aivora.vault.ui.kit.ConfirmDialog
import app.aivora.vault.ui.kit.CopyAction
import app.aivora.vault.ui.kit.DangerHint
import app.aivora.vault.ui.kit.DataRow
import app.aivora.vault.ui.kit.HintText
import app.aivora.vault.ui.kit.ItemDivider
import app.aivora.vault.ui.kit.LoadingBlock
import app.aivora.vault.ui.kit.LocalSelectedTab
import app.aivora.vault.ui.kit.LocalTabSelect
import app.aivora.vault.ui.kit.NavigationRow
import app.aivora.vault.ui.kit.PrimaryButton
import app.aivora.vault.ui.kit.RadioRow
import app.aivora.vault.ui.kit.ScrollColumn
import app.aivora.vault.ui.kit.SecondaryButton
import app.aivora.vault.ui.kit.SectionBlock
import app.aivora.vault.ui.kit.SectionLabel
import app.aivora.vault.ui.kit.SettingRow
import app.aivora.vault.ui.kit.SubtlePanel
import app.aivora.vault.ui.kit.SwitchRow
import app.aivora.vault.ui.kit.TechWell
import app.aivora.vault.ui.kit.TextAction
import app.aivora.vault.ui.kit.ExpandableBlock
import app.aivora.vault.ui.theme.AivoraShape
import app.aivora.vault.ui.theme.AivoraSpace
import app.aivora.vault.ui.theme.AivoraThemeColors
import app.aivora.vault.ui.theme.AivoraType
import app.aivora.vault.ui.theme.ProvideAivoraTheme
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 应用入口。
 *
 * 这里只做三件事：edge-to-edge 初始化、解析外观设置、把内容包进主题。
 * 页面、组件、令牌分别住在 `ui/` 下，本文件不再堆 UI。
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repo = (application as AivoraApplication).repository
        val prefs = getSharedPreferences("appearance", Context.MODE_PRIVATE)
        setContent {
            // 锁定界面与主界面走同一个主题入口：此前锁定页被包在裸 MaterialTheme 里，
            // 跟随的是系统深浅而不是应用设置，于是「应用设成深色、系统仍是浅色」时锁定页是亮的。
            val dark = resolveDark(prefs)
            ProvideAivoraTheme(dark) {
                // 状态栏 / 导航栏图标颜色必须随主题实时切换：只在 onCreate 里设一次会在切主题后失效。
                SystemBars(dark)
                var unlocked by remember { mutableStateOf(!prefs.getBoolean("appLock", false)) }
                Surface(Modifier.fillMaxSize(), color = AivoraThemeColors.colors.background) {
                    if (unlocked) AivoraApp(repo) else LockScreen { authenticate(this) { unlocked = it } }
                }
            }
        }
    }
}

/** 让系统栏图标颜色跟随当前主题。 */
@Composable
private fun SystemBars(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    androidx.compose.runtime.SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

/** 读取外观设置并解析出「当前是否深色」。跟随系统时用 isSystemInDarkTheme 的等价判定。 */
@Composable
private fun resolveDark(prefs: android.content.SharedPreferences): Boolean {
    val mode = runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)
    return when (mode) {
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private fun authenticate(activity: FragmentActivity, done: (Boolean) -> Unit) {
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = done(true)
        override fun onAuthenticationError(code: Int, message: CharSequence) { done(false) }
    })
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("验证身份以打开 Aivora")
            .setSubtitle("使用生物识别或设备锁屏密码")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build(),
    )
}

/** 锁定页：全屏独立页面，内容避开状态栏与导航栏。 */
@Composable
private fun LockScreen(auth: () -> Unit) {
    LaunchedEffect(Unit) { auth() }
    val c = AivoraThemeColors.colors
    Box(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(AivoraSpace.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .background(c.accentContainer, AivoraShape.inner),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lucide.Lock, null, Modifier.size(28.dp), tint = c.accent)
            }
            Spacer(Modifier.height(AivoraSpace.lg))
            Text("Aivora 已锁定", style = AivoraType.pageTitle, color = c.text)
            Spacer(Modifier.height(AivoraSpace.xs))
            Text(
                "配置已加密保存，验证身份后才能查看", style = AivoraType.meta,
                color = c.textSecondary,
            )
            Spacer(Modifier.height(AivoraSpace.xxl))
            PrimaryButton("验证身份", auth, icon = Lucide.Lock)
        }
    }
}

/**
 * 主界面。
 *
 * 导航状态由 [NavState] 管理：Tab 决定底栏，Leaf 决定二级页与返回链。
 * 「底栏只在看 Tab 本体时显示」这条规则只在 [AivoraScaffold] 里写一次。
 */
@Composable
fun AivoraApp(repo: VaultRepository) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val prefs = remember { context.getSharedPreferences("appearance", Context.MODE_PRIVATE) }
    var themeMode by remember { mutableStateOf(runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)) }
    var locked by remember { mutableStateOf(prefs.getBoolean("appLock", false)) }
    val nav = rememberNavState()
    var configs by remember { mutableStateOf(listOf<ServiceConfig>()) }
    var records by remember { mutableStateOf(listOf<ExportRecord>()) }
    // 「端点纠正」开关：默认开启。关闭后既不规范化也不试候选路径，仅影响未来操作。
    var endpointFix by remember { mutableStateOf(prefs.getBoolean("endpointFix", true)) }
    var editing by remember { mutableStateOf<ServiceConfig?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { repo.dao.configs().collectLatest { configs = it } }
    LaunchedEffect(Unit) { repo.dao.exports().collectLatest { records = it } }
    LaunchedEffect(message) { if (message.isNotEmpty()) { snack.showSnackbar(message); message = "" } }

    fun changeLock(enabled: Boolean) {
        if (enabled) {
            val available = BiometricManager.from(context).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            if (available != BiometricManager.BIOMETRIC_SUCCESS) {
                android.widget.Toast.makeText(context, "请先在系统中设置锁屏密码或生物识别", android.widget.Toast.LENGTH_LONG).show()
                return
            }
            authenticate(activity) { if (it) { prefs.edit().putBoolean("appLock", true).apply(); locked = true } }
        } else {
            authenticate(activity) { if (it) { prefs.edit().putBoolean("appLock", false).apply(); locked = false } }
        }
    }

    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    ProvideAivoraTheme(dark) {
        SystemBars(dark)
        val current = nav.state
        // 二级页返回：先回父级，已到根则让系统处理（退出应用）。
        BackHandler(enabled = current.leaf != null) { nav.update { it.back() ?: it } }
        CompositionLocalProvider(
            LocalSelectedTab provides current.tab,
            LocalTabSelect provides { tab -> nav.update { it.switchTo(tab) } },
        ) {
            AivoraScaffold(
                title = current.title,
                showsBack = current.showsBack,
                onBack = { nav.update { it.back() ?: it } },
                showsBottomBar = current.showsBottomBar,
                snackbarHostState = snack,
            ) {
                when (current.leaf) {
                    null -> when (current.tab) {
                        Tab.Configs -> ConfigPage(
                            configs = configs,
                            repo = repo,
                            appVersion = packageVersion(LocalContext.current),
                            endpointFix = endpointFix,
                            edit = { editing = it; showEditor = true },
                            add = { editing = null; showEditor = true },
                            onOpenSettings = { nav.update { it.switchTo(Tab.Settings) } },
                        ) { message = it }
                        Tab.Diagnostics -> DiagnosticsPage(configs, repo, packageVersion(LocalContext.current)) { message = it }
                        Tab.Records -> RecordsPage(records, configs, repo, packageVersion(LocalContext.current)) { message = it }
                        Tab.Settings -> SettingsPage(
                            repo = repo,
                            prefs = prefs,
                            mode = themeMode,
                            change = { themeMode = it; prefs.edit().putString("theme", it.name).apply() },
                            locked = locked,
                            lockChange = ::changeLock,
                            endpointFix = endpointFix,
                            endpointFixChange = { endpointFix = it; prefs.edit().putBoolean("endpointFix", it).apply() },
                            about = { nav.update { it.open(Leaf.About) } },
                        ) { message = it }
                    }
                    Leaf.About -> AboutPage { nav.update { it.open(Leaf.Protocol) } }
                    Leaf.Protocol -> ProtocolPage()
                }
            }
        }
        if (showEditor) {
            ConfigEditor(editing, repo, endpointFix, { showEditor = false }) { message = it }
        }
    }
}

/**
 * 设置页。
 *
 * 分组逻辑：**影响行为的功能开关**（端点纠正 / 安全）与**数据操作**（备份）分开，
 * 外观单独一组，关于与协议放在最底。之前是 7 个可展开卡片平铺，用户找不到「哪个是设置、
 * 哪个是操作」。
 */
@Composable
fun SettingsPage(
    repo: VaultRepository,
    prefs: android.content.SharedPreferences,
    mode: ThemeMode,
    change: (ThemeMode) -> Unit,
    locked: Boolean,
    lockChange: (Boolean) -> Unit,
    endpointFix: Boolean,
    endpointFixChange: (Boolean) -> Unit,
    about: () -> Unit,
    toast: (String) -> Unit,
) {
    ScrollColumn {
        SectionLabel("行为")
        SectionBlock {
            SwitchRow(
                title = "自动纠正 API 端点",
                checked = endpointFix,
                onCheckedChange = endpointFixChange,
                icon = Lucide.Wand,
                description = "补全 /v1、剥离重复的 /chat/completions 等后缀；官方模板地址不会被改动。",
            )
            ItemDivider()
            SwitchRow(
                title = "打开软件时验证身份",
                checked = locked,
                onCheckedChange = lockChange,
                icon = Lucide.Lock,
                description = "使用系统生物识别或设备锁屏密码，验证通过后才能查看配置。",
            )
        }

        SectionLabel("外观")
        SectionBlock {
            RadioRow("跟随系统", mode == ThemeMode.SYSTEM, { change(ThemeMode.SYSTEM) }, icon = Lucide.Monitor)
            ItemDivider()
            RadioRow("浅色模式", mode == ThemeMode.LIGHT, { change(ThemeMode.LIGHT) }, icon = Lucide.Sun)
            ItemDivider()
            RadioRow("深色模式", mode == ThemeMode.DARK, { change(ThemeMode.DARK) }, icon = Lucide.Moon)
        }

        SectionLabel("数据")
        BackupSection(repo, prefs, change, toast)

        SectionLabel("关于")
        SectionBlock {
            NavigationRow("关于 Aivora", about, icon = Lucide.Info, description = "版本、开源许可与安全边界")
        }
    }
}

/**
 * 备份：导出 / 导入全量加密备份。
 *
 * 备份凭证与导出凭证同等重要 —— 丢失就无法恢复，所以必须能看清、能复制、能选保存位置，
 * 不能只用一句 Toast 打发。
 */
@Composable
private fun BackupSection(
    repo: VaultRepository,
    prefs: android.content.SharedPreferences,
    themeChange: (ThemeMode) -> Unit,
    toast: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var exportResult by remember { mutableStateOf<Backup.Result?>(null) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var credential by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Backup.Payload?>(null) }
    var busy by remember { mutableStateOf(false) }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val data = exportResult
        if (uri != null && data != null) scope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(data.bytes) } ?: error("无法写入文件")
                withContext(Dispatchers.Main) { exportResult = null; toast("全量备份导出成功") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast(e.message ?: "备份失败") }
            }
        }
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { importUri = it; credential = "" }

    SectionBlock {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = AivoraSpace.blockPadding, vertical = AivoraSpace.md),
            horizontalArrangement = Arrangement.spacedBy(AivoraSpace.sm),
        ) {
            SecondaryButton(
                "导出备份",
                icon = Lucide.Export,
                enabled = !busy,
                modifier = Modifier.weight(1f),
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            exportResult = withContext(Dispatchers.IO) { repo.createBackup(prefs.getString("theme", "SYSTEM")!!) }
                        } catch (e: Exception) { toast(e.message ?: "无法生成备份") }
                        busy = false
                    }
                },
            )
            SecondaryButton(
                "导入备份",
                icon = Lucide.Import,
                modifier = Modifier.weight(1f),
                onClick = { opener.launch(arrayOf("application/octet-stream", "*/*")) },
            )
        }
        ItemDivider()
        Box(Modifier.padding(horizontal = AivoraSpace.blockPadding, vertical = AivoraSpace.md)) {
            HintText("备份包含全部配置、检测结果和外观设置。导入会覆盖当前全部数据。")
        }
    }

    // ---- 导出成功：展示凭证并让用户选保存位置。凭证单独传递，不随文件发出。
    exportResult?.let { result ->
        AlertDialog(
            onDismissRequest = { exportResult = null },
            title = { Text("导出全量备份", style = AivoraType.section, color = AivoraThemeColors.colors.text) },
            text = {
                Column {
                    HintText("请保存备份凭证。恢复时必须输入相同凭证，丢失后无法恢复。")
                    Spacer(Modifier.height(AivoraSpace.md))
                    TechWell(
                        value = result.credential,
                        leadingLabel = "备份凭证",
                        onCopy = {
                            clipboard.setText(AnnotatedString(result.credential))
                            toast("备份凭证已复制")
                        },
                    )
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = "选择保存位置",
                    onClick = {
                        saver.launch("Aivora-全量备份-${System.currentTimeMillis()}${Backup.EXTENSION}")
                    },
                )
            },
            dismissButton = {
                TextAction(
                    text = "取消",
                    onClick = { exportResult = null },
                )
            },
            containerColor = AivoraThemeColors.colors.surface,
            shape = AivoraShape.block,
        )
    }

    // ---- 导入：输入凭证
    importUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { importUri = null },
            title = { Text("输入备份凭证", style = AivoraType.section, color = AivoraThemeColors.colors.text) },
            text = {
                AivoraField(
                    label = "备份凭证",
                    value = credential,
                    onValueChange = { credential = it },
                    visualTransformation = PasswordVisualTransformation(),
                )
            },
            confirmButton = {
                PrimaryButton(
                    text = "验证备份",
                    loading = busy,
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                val data = withContext(Dispatchers.IO) {
                                    Backup.import(
                                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                            ?: error("文件读取失败"),
                                        credential,
                                    )
                                }
                                pending = data
                                importUri = null
                            } catch (e: Exception) {
                                toast(e.message ?: "备份无效")
                            }
                            busy = false
                        }
                    },
                )
            },
            dismissButton = {
                TextAction(
                    text = "取消",
                    onClick = { importUri = null },
                )
            },
            containerColor = AivoraThemeColors.colors.surface,
            shape = AivoraShape.block,
        )
    }

    // ---- 导入：确认覆盖
    pending?.let { data ->
        ConfirmDialog(
            title = "覆盖当前全部数据？",
            text = "备份包含 ${data.configs.size} 个配置。恢复后当前配置、检测结果和外观设置将被替换，此操作无法撤销。",
            confirmText = "确认覆盖恢复",
            danger = true,
            onConfirm = {
                scope.launch {
                    try {
                        repo.replaceAll(data, prefs)
                        themeChange(ThemeMode.valueOf(data.theme))
                        pending = null
                        toast("全量数据恢复成功")
                    } catch (e: Exception) { toast(e.message ?: "恢复失败") }
                }
            },
            onDismiss = { pending = null },
        )
    }
}

/** 关于页。 */
@Composable
fun AboutPage(protocol: () -> Unit) {
    val uri = LocalUriHandler.current
    val c = AivoraThemeColors.colors
    ScrollColumn(bottomSpace = AivoraSpace.xxxl) {
        Spacer(Modifier.height(AivoraSpace.sm))
        SectionBlock {
            Column(Modifier.padding(AivoraSpace.blockPadding)) {
                Text("Aivora", style = AivoraType.pageTitle, color = c.text)
                Spacer(Modifier.height(AivoraSpace.xs))
                Text("本地 AI 配置管理工具", style = AivoraType.body, color = c.textSecondary)
                Spacer(Modifier.height(AivoraSpace.md))
                DataRow("版本", packageVersion(LocalContext.current), mono = true)
                DataRow("协议", "AEX 1.1", mono = true)
                DataRow("许可", "AGPL-3.0-only", mono = true)
                Spacer(Modifier.height(AivoraSpace.md))
                HintText("数据仅保存在当前设备，不依赖云端账户。")
            }
        }

        SectionLabel("开放生态")
        SectionBlock {
            NavigationRow("适配协议", protocol, icon = Lucide.File, description = "AEX 交换格式与加密参数")
            ItemDivider()
            NavigationRow(
                "GitHub 项目",
                { uri.openUri("https://github.com/Farewell-coder/Aivora") },
                icon = Lucide.Github,
                description = "源码、协议文档与版本记录",
            )
        }

        SectionLabel("安全边界")
        SectionBlock {
            Box(Modifier.padding(AivoraSpace.blockPadding)) {
                DangerHint("凭证用于加密导出文件。第三方成功解密后可接触明文 API Key，请仅向可信服务提交密钥。")
            }
        }
    }
}

/** 适配协议页：把协议参数拆成可读的数据行，而不是一整段换行文本。 */
@Composable
fun ProtocolPage() {
    val c = AivoraThemeColors.colors
    ScrollColumn(bottomSpace = AivoraSpace.xxxl) {
        Spacer(Modifier.height(AivoraSpace.sm))
        SectionBlock {
            Column(Modifier.padding(AivoraSpace.blockPadding)) {
                Text("AEX 1.1", style = AivoraType.pageTitle, color = c.text)
                Spacer(Modifier.height(AivoraSpace.xs))
                Text("Aivora Exchange Protocol 是开放的配置交换协议，版本与加密方案独立演进。", style = AivoraType.body, color = c.textSecondary)
            }
        }

        SectionLabel("文件与加密")
        SectionBlock {
            Column(Modifier.padding(AivoraSpace.blockPadding)) {
                DataRow("文件后缀", ".akcp")
                DataRow("容器", "Magic(8) + 长度(4) + JSON 头部 + 密文", maxLines = 2)
                DataRow("凭证", "256 位 Base32，52 字符", maxLines = 1)
                DataRow("密钥派生", "PBKDF2-HMAC-SHA256 × 600000", maxLines = 1)
                DataRow("内容加密", "AES-256-GCM / 128 位标签", maxLines = 1)
                DataRow("完整性", "整个头部作为 AAD", maxLines = 1)
                Spacer(Modifier.height(AivoraSpace.md))
                HintText("凭证不写入文件，仅用于派生密钥。篡改协议版本或加密参数会直接导致解密失败。")
            }
        }

        SectionLabel("导入与导出")
        SectionBlock {
            Box(Modifier.padding(AivoraSpace.blockPadding)) {
                Column {
                    Text("导出", style = AivoraType.bodyStrong, color = c.text)
                    Spacer(Modifier.height(AivoraSpace.xs))
                    Text("可把单个或多个配置加密为 .akcp 文件；凭证单独展示、单独传递，不随文件发出。", style = AivoraType.body, color = c.textSecondary)
                    Spacer(Modifier.height(AivoraSpace.md))
                    Text("导入", style = AivoraType.bodyStrong, color = c.text)
                    Spacer(Modifier.height(AivoraSpace.xs))
                    Text("选择文件后自动识别格式，加密文件需输入凭证；凭证错误、文件被修改时不写入任何配置。", style = AivoraType.body, color = c.textSecondary)
                    Spacer(Modifier.height(AivoraSpace.md))
                    Text("第三方格式", style = AivoraType.bodyStrong, color = c.text)
                    Spacer(Modifier.height(AivoraSpace.xs))
                    Text("Chatbox、Continue、LobeChat、CC Switch、New API、Cherry Studio、.env 按各自规则明文解析，无需凭证。", style = AivoraType.body, color = c.textSecondary)
                }
            }
            ItemDivider()
            Box(Modifier.padding(AivoraSpace.blockPadding)) {
                HintText("全量备份使用独立的 .aivora-backup 格式，仅用于 Aivora 数据恢复，不与 AEX 配置交换文件混用。")
            }
        }
    }
}

/** Reads versionName from the installed package; falls back to a neutral label. */
fun packageVersion(context: android.content.Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull().orEmpty().ifBlank { "unknown" }