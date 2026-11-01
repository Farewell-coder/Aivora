package app.aivora.vault

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
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
        super.onCreate(savedInstanceState)
        val repo = (application as AivoraApplication).repository
        val prefs = getSharedPreferences("appearance", Context.MODE_PRIVATE)
        setContent {
            var unlocked by remember { mutableStateOf(!prefs.getBoolean("appLock", false)) }
            MaterialTheme { Surface(Modifier.fillMaxSize()) { if (unlocked) AivoraApp(repo) else LockScreen { authenticate(this) { unlocked = it } } } }
        }
    }
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
    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) { Icon(Lucide.Lock, null, Modifier.size(44.dp), tint = Brand); Spacer(Modifier.height(18.dp)); Text("Aivora 已锁定", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(18.dp)); LabelButton(auth, Lucide.Lock, "验证身份") }
}
enum class Page(val title: String) { Configs("服务配置"), Diagnostics("密钥检测"), Settings("设置"), About("关于"), Protocol("适配协议") }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable fun AivoraApp(repo: VaultRepository) {
    val context = LocalContext.current; val activity = context as FragmentActivity
    val prefs = remember { context.getSharedPreferences("appearance", Context.MODE_PRIVATE) }
    var themeMode by remember { mutableStateOf(runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)) }
    var locked by remember { mutableStateOf(prefs.getBoolean("appLock", false)) }
    var page by remember { mutableStateOf(Page.Configs) }
    BackHandler(enabled = page == Page.About || page == Page.Protocol) { page = if (page == Page.Protocol) Page.About else Page.Settings }
    var configs by remember { mutableStateOf(listOf<ServiceConfig>()) }
    var editing by remember { mutableStateOf<ServiceConfig?>(null) }; var showEditor by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }; val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { repo.dao.configs().collectLatest { configs = it } }
    LaunchedEffect(message) { if (message.isNotEmpty()) { snack.showSnackbar(message); message = "" } }
    fun changeLock(enabled: Boolean) {
        if (enabled) {
            val available = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            if (available != BiometricManager.BIOMETRIC_SUCCESS) { android.widget.Toast.makeText(context, "请先在系统中设置锁屏密码或生物识别", android.widget.Toast.LENGTH_LONG).show(); return }
            authenticate(activity) { if (it) { prefs.edit().putBoolean("appLock", true).apply(); locked = true } }
        } else authenticate(activity) { if (it) { prefs.edit().putBoolean("appLock", false).apply(); locked = false } }
    }
    val dark = when (themeMode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    val scheme = if (dark) darkColorScheme(primary = Color(0xFFD0BCFF), primaryContainer = Color(0xFF4F378B)) else lightColorScheme(primary = Brand, background = Color(0xFFFEF7FF), surface = Color.White)
    MaterialTheme(scheme, shapes = Shapes(medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp))) {
        Box(Modifier.fillMaxSize().background(scheme.background)) {
            Scaffold(containerColor = Color.Transparent, snackbarHost = { SnackbarHost(snack) }) { pad ->
                Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(20.dp)); PageHeader(page) { page = if (page == Page.Protocol) Page.About else if (page == Page.About) Page.Settings else Page.Configs }
                    when (page) {
                        Page.Configs -> ConfigPage(configs, repo, packageVersion(LocalContext.current), { editing = it; showEditor = true }, { editing = null; showEditor = true }, { page = it }) { message = it }
                        Page.Diagnostics -> DiagnosticsPage(configs, repo) { message = it }
                        Page.Settings -> SettingsPage(repo, prefs, themeMode, { themeMode = it; prefs.edit().putString("theme", it.name).apply() }, locked, ::changeLock, { page = Page.About }) { message = it }
                        Page.About -> AboutPage { page = Page.Protocol }
                        Page.Protocol -> ProtocolPage()
                    }
                    Spacer(Modifier.height(if (page in listOf(Page.Configs, Page.Diagnostics, Page.Settings)) 124.dp else 36.dp))
                }
            }
            if (page in listOf(Page.Configs, Page.Diagnostics, Page.Settings)) FloatingNav(page, Modifier.align(Alignment.BottomCenter)) { page = it }
        }
        if (showEditor) ConfigEditor(editing, repo, { showEditor = false }) { message = it }
    }
}
@Composable private fun PageHeader(page: Page, back: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
        if (page !in listOf(Page.Configs, Page.Diagnostics, Page.Settings)) { IconButton(back) { Icon(Lucide.ArrowLeft, "返回") }; Spacer(Modifier.width(8.dp)) }
        else { Surface(Modifier.size(42.dp), color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(14.dp)) { Box(contentAlignment = Alignment.Center) { Text("A", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) } }; Spacer(Modifier.width(12.dp)) }
        Text(page.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }; Spacer(Modifier.height(18.dp))
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
                listOf(Page.Configs to Lucide.List, Page.Diagnostics to Lucide.Activity, Page.Settings to Lucide.Settings).forEach { (p, icon) ->
                    val active = selected == p
                    Column(Modifier.clip(RoundedCornerShape(20.dp)).background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else Color.Transparent).clickable { select(p) }.padding(horizontal = 20.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, p.title, Modifier.size(22.dp), tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(3.dp))
                        Text(p.title, fontSize = 11.sp, maxLines = 1, softWrap = false, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
@Composable fun AppCard(content: @Composable ColumnScope.() -> Unit) = Surface(Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(.45f))) { Column(Modifier.padding(16.dp), content = content) }
@Composable fun LabelButton(onClick: () -> Unit, icon: ImageVector, text: String, modifier: Modifier = Modifier, enabled: Boolean = true) = FilledTonalButton(onClick, modifier, enabled = enabled, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text(text, maxLines = 1) }
@Composable fun ConfigSummary(c: ServiceConfig) {
    Row(verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(42.dp), color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(13.dp)) { Box(contentAlignment = Alignment.Center) { Text(c.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer) } }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(c.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); if (c.notes.isNotBlank()) Text(c.notes, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) } }
}
@Composable fun ConfigPage(configs: List<ServiceConfig>, repo: VaultRepository, appVersion: String, edit: (ServiceConfig) -> Unit, add: () -> Unit, navigate: (Page) -> Unit, toast: (String) -> Unit) {
    var search by remember { mutableStateOf("") }; var deleting by remember { mutableStateOf<ServiceConfig?>(null) }; var exporting by remember { mutableStateOf<ServiceConfig?>(null) }
    var selecting by remember { mutableStateOf(false) }; var picked by remember { mutableStateOf<Set<Long>>(emptySet()) }; var sharing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope(); val context = LocalContext.current; val clipboard = LocalClipboardManager.current
    var importFile by remember { mutableStateOf<Pair<Uri, String>?>(null) }; var importCredential by remember { mutableStateOf("") }; var checkingImport by remember { mutableStateOf(false) }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> val name = context.contentResolver.query(uri, null, null, null, null)?.use { cur -> if (cur.moveToFirst()) cur.getString(cur.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else "" } ?: ""; importFile = uri to name; importCredential = "" } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LabelButton(add, Lucide.Plus, "新建配置", Modifier.weight(1f))
        LabelButton({ openLauncher.launch(arrayOf("application/octet-stream", "*/*")) }, Lucide.Import, "导入", Modifier.weight(1f))
        LabelButton({ selecting = !selecting; if (!selecting) picked = emptySet() }, Lucide.Check, if (selecting) "取消" else "选择", Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp)); OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), leadingIcon = { Icon(Lucide.Search, null) }, placeholder = { Text("搜索名称、服务商或 Endpoint") }, singleLine = true, shape = CircleShape); Spacer(Modifier.height(14.dp))
    val filtered = configs.filter { "${it.name} ${it.provider} ${it.endpoint} ${it.notes}".contains(search, true) }
    if (filtered.isEmpty()) AppCard { Empty("还没有匹配的服务配置") } else filtered.forEach { c -> AppCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                Checkbox(checked = c.id in picked, onCheckedChange = { on -> picked = if (on) picked + c.id else picked - c.id })
                Spacer(Modifier.width(4.dp))
            }
            Box(Modifier.weight(1f).clickable(enabled = selecting) { picked = if (c.id in picked) picked - c.id else picked + c.id }) { ConfigSummary(c) }
        }
        Spacer(Modifier.height(6.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        IconButton({ edit(c) }, Modifier.size(40.dp)) { Icon(Lucide.Edit, "编辑", Modifier.size(20.dp)) }; Spacer(Modifier.width(4.dp))
        IconButton({ exporting = c }, Modifier.size(40.dp)) { Icon(Lucide.Export, "导出", Modifier.size(20.dp)) }; Spacer(Modifier.width(4.dp))
        IconButton({ try { if (c.endpoint.isBlank() || c.encryptedKey.isBlank()) error("Endpoint 或 API Key 未填写"); clipboard.setText(AnnotatedString(c.endpoint + "\n" + repo.crypto.decrypt(c.encryptedKey).trim())); Toast.makeText(context, "Endpoint 和 API Key 已复制", Toast.LENGTH_SHORT).show() } catch (e: Exception) { Toast.makeText(context, e.message ?: "配置不完整", Toast.LENGTH_SHORT).show() } }, Modifier.size(40.dp)) { Icon(Lucide.Copy, "复制 Endpoint 和 API Key", Modifier.size(20.dp)) }; Spacer(Modifier.width(4.dp))
        IconButton({ deleting = c }, Modifier.size(40.dp)) { Icon(Lucide.Trash, "删除", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) }
            } } }
    if (selecting) {
        AppCard {
            Text("已选 ${picked.size} / ${configs.size} 个配置", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabelButton({
                    scope.launch {
                        if (sharing) return@launch
                        val targets = configs.filter { it.id in picked }
                        if (targets.isEmpty()) { toast("请先勾选要导出的配置"); return@launch }
                        sharing = true
                        try { shareProfiles(context, repo, appVersion, targets) } catch (e: Exception) { toast(e.message ?: "导出失败") } finally { sharing = false }
                    }
                }, Lucide.Export, if (sharing) "生成中" else "导出所选", Modifier.weight(1f), enabled = !sharing && picked.isNotEmpty())
                LabelButton({
                    scope.launch {
                        if (sharing) return@launch
                        if (configs.isEmpty()) { toast("没有可导出的配置"); return@launch }
                        sharing = true
                        try { shareProfiles(context, repo, appVersion, configs) } catch (e: Exception) { toast(e.message ?: "导出失败") } finally { sharing = false }
                    }
                }, Lucide.Export, "导出全部", Modifier.weight(1f), enabled = !sharing && configs.isNotEmpty())
            }
        }
    }
    deleting?.let { c -> AlertDialog({ deleting = null }, title = { Text("删除服务配置") }, text = { Text("确定删除「${c.name}」吗？此操作无法撤销。") }, confirmButton = { TextButton({ scope.launch { repo.dao.delete(c); deleting = null; toast("配置已删除") } }) { Text("确认删除") } }, dismissButton = { TextButton({ deleting = null }) { Text("取消") } }) }
    exporting?.let { c -> ExportDialog(c, repo, appVersion, { exporting = null }, toast) }
    importFile?.let { (uri, name) -> AlertDialog({ if (!checkingImport) importFile = null }, title = { Text("输入导入凭证") }, text = { Column { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(10.dp)); OutlinedTextField(importCredential, { importCredential = it }, Modifier.fillMaxWidth(), label = { Text("导出凭证") }, singleLine = true, visualTransformation = PasswordVisualTransformation()) } }, confirmButton = { Button({ if (importCredential.isBlank()) toast("请输入导出凭证") else scope.launch { checkingImport = true; try { val data = withContext(Dispatchers.IO) { Akcp.requireFilename(name); val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("文件读取失败"); Akcp.import(bytes, importCredential) }; data.forEach { item -> configs.firstOrNull { it.name == item.config_name && it.endpoint == item.endpoint }?.let { repo.dao.delete(it) }; repo.dao.save(repo.fromAkcp(item)) }; importFile = null; toast("已导入 ${data.size} 条配置") } catch (e: Exception) { toast(e.message ?: "凭证错误或文件无效") } finally { checkingImport = false } } }, enabled = !checkingImport) { Text(if (checkingImport) "验证中…" else "导入") } }, dismissButton = { TextButton({ importFile = null }, enabled = !checkingImport) { Text("取消") } }) }
}
@Composable fun ExportDialog(config: ServiceConfig, repo: VaultRepository, appVersion: String, close: () -> Unit, toast: (String) -> Unit) {
    val context = LocalContext.current; val clipboard = LocalClipboardManager.current; val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    // 派生 600000 次 PBKDF2 需要几百毫秒，放在后台线程算，UI 先显示进度。
    // key 包含配置的全部可见字段，配置被修改后必须换用新的凭证与文件。
    var exported by remember { mutableStateOf<Aex.Export?>(null) }
    var generating by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(config.id, config.name, config.provider, config.endpoint, config.encryptedKey, config.notes, config.models, config.defaultModel) {
        exported = null
        if (config.endpoint.isBlank() || config.encryptedKey.isBlank()) return@LaunchedEffect
        generating = true
        try { exported = withContext(Dispatchers.Default) { runCatching { Aex.export(listOf(repo.toAex(config)), appVersion) }.getOrNull() } }
        finally { generating = false }
    }
    val displayCredential = exported?.credential
    val fileName = remember(config.name) { Aex.sanitizeFileName(config.name) }
    // 「导出」= 用户自选位置保存到本地；「分享」= 交给系统分享面板。两者生成的文件完全相同。
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Aex.MIME_TYPE)) { uri ->
        val bytes = pendingSave; pendingSave = null
        if (uri == null || bytes == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("无法写入文件")
                    repo.dao.recordExport(ExportRecord(fileName = fileName, encryptedCredential = repo.crypto.encrypt(displayCredential.orEmpty()), configCount = 1))
                }
                close(); toast("已导出 .akcp 文件")
            } catch (e: Exception) { toast(e.message ?: "导出失败") }
        }
    }
    AlertDialog(close, title = { Text("导出「${config.name}」") }, text = {
        Column {
            Text("保存到本机，或直接分享给其他软件。接收方必须输入下面的凭证才能读取配置。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(Modifier.fillMaxWidth().padding(vertical = 14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(14.dp)) {
                SelectionContainer { Text(displayCredential ?: if (generating) "正在生成凭证…" else "请先填写 Endpoint 和 API Key", Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall) }
            }
            Text("文件已加密，凭证单独传递；不会随文件一起发出。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("该文件包含完整 API Key，接收应用解密后可读取明文，请仅分享给可信应用。", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            LabelButton({ val value = displayCredential; if (value.isNullOrBlank()) toast("请先填写 Endpoint 和 API Key") else { clipboard.setText(AnnotatedString(value)); toast("凭证已复制") } }, Lucide.Copy, "复制凭证")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ scope.launch {
                    if (working) return@launch
                    try {
                        if (config.endpoint.isBlank() || config.encryptedKey.isBlank()) error("请先填写 Endpoint 和 API Key")
                        val data = exported ?: error("导出内容生成失败，请重新打开导出")
                        working = true; pendingSave = data.bytes; saveLauncher.launch(fileName)
                    } catch (e: Exception) { toast(e.message ?: "导出失败") } finally { working = false }
                } }, Modifier.weight(1f), enabled = !working && !generating && exported != null) {
                    if (generating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else { Icon(Lucide.Export, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("导出") }
                }
                Button({ scope.launch {
                    if (working) return@launch
                    working = true
                    try {
                        if (config.endpoint.isBlank() || config.encryptedKey.isBlank()) error("请先填写 Endpoint 和 API Key")
                        val data = exported ?: error("导出内容生成失败，请重新打开导出")
                        val prepared = AexShare.prepare(context, data, fileName, "Aivora 配置：${config.name}").getOrElse { error(it.message ?: "无法生成临时文件") }
                        repo.dao.recordExport(ExportRecord(fileName = fileName, encryptedCredential = repo.crypto.encrypt(data.credential), configCount = data.profileCount))
                        context.startActivity(prepared.first)
                        close(); toast("已生成 .akcp 文件，请选择接收应用")
                    } catch (e: Exception) { toast(e.message ?: "分享失败") } finally { working = false }
                } }, Modifier.weight(1f), enabled = !working && !generating && exported != null) {
                    if (working || generating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else { Icon(Lucide.Share, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("分享") }
                }
            }
        }
    }, confirmButton = { TextButton({ close() }, enabled = !working) { Text("关闭") } }, dismissButton = null)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ConfigEditor(old: ServiceConfig?, repo: VaultRepository, close: () -> Unit, toast: (String) -> Unit) {
    var name by remember { mutableStateOf(old?.name.orEmpty()) }; var provider by remember { mutableStateOf(old?.provider.orEmpty()) }; var expanded by remember { mutableStateOf(false) }; var endpoint by remember { mutableStateOf(old?.endpoint.orEmpty()) }; var key by remember { mutableStateOf("") }; var notes by remember { mutableStateOf(old?.notes.orEmpty()) }; var visible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope(); val clipboard = LocalClipboardManager.current
    LaunchedEffect(old) { if (old?.encryptedKey?.isNotEmpty() == true) key = runCatching { repo.crypto.decrypt(old.encryptedKey) }.getOrElse { "" } }
    AlertDialog(close, title = { Text(if (old == null) "添加服务配置" else "编辑服务配置") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { Field("配置名称", name) { name = it }; ExposedDropdownMenuBox(expanded, { expanded = it }) { OutlinedTextField(provider, {}, Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable), readOnly = true, label = { Text("服务商模板") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }); ExposedDropdownMenu(expanded, { expanded = false }) { providerTemplates.forEach { t -> DropdownMenuItem(text = { Text(t.name) }, onClick = { provider = if (t.name == "空白模板" || t.name == "自定义") "" else t.name; endpoint = t.endpoint; expanded = false }) } } }; Field("API Endpoint（选填）", endpoint) { endpoint = it }; OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("API Key（选填）") }, singleLine = true, visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { Row { IconButton({ visible = !visible }) { Icon(if (visible) Lucide.EyeOff else Lucide.Eye, "显示或隐藏密钥") }; IconButton({ clipboard.setText(AnnotatedString(key)); toast("API Key 已复制") }) { Icon(Lucide.Copy, "复制密钥") } } }); Field("备注（选填）", notes) { notes = it } } }, confirmButton = { Button({ if (name.isBlank()) toast("请填写配置名称") else scope.launch { repo.dao.save(ServiceConfig(old?.id ?: 0, name.trim(), provider, if (key.isBlank()) "" else repo.crypto.encrypt(key.trim()), endpoint.trim(), "", "", notes, old?.enabled ?: true, old?.isDefault ?: false, old?.createdAt ?: System.currentTimeMillis(), 0, "配置已更新，请重新检测", null, old?.extensionsJson ?: "{}", "[]")); close(); toast("配置已保存") } }) { Text("保存") } }, dismissButton = { TextButton(close) { Text("取消") } })
}
@Composable fun Field(label: String, value: String, change: (String) -> Unit) = OutlinedTextField(value, change, Modifier.fillMaxWidth().padding(vertical = 5.dp), label = { Text(label) }, singleLine = true)
@Composable fun DiagnosticsPage(configs: List<ServiceConfig>, repo: VaultRepository, toast: (String) -> Unit) {
    val scope = rememberCoroutineScope(); val checker = remember { KeyDiagnostics() }; var job by remember { mutableStateOf<Job?>(null) }; var currentId by remember { mutableStateOf<Long?>(null) }; var results by remember { mutableStateOf(mapOf<Long, List<ModelResult>>()) }; var expanded by remember { mutableStateOf(setOf<Long>()) }
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
        Text(c.checkStatus, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp); Text("检测时间：${if (c.lastCheckedAt > 0) DateFormat.getDateTimeInstance().format(Date(c.lastCheckedAt)) else "未检测"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { LabelButton({ expanded += c.id; start(c) }, Lucide.Play, if (currentId == c.id && job?.isActive == true) "检测中" else "检测", enabled = job?.isActive != true); if (currentId == c.id && job?.isActive == true) LabelButton({ job?.cancel(); job = null; currentId = null }, Lucide.Stop, "停止") }
        AnimatedVisibility(c.id in expanded) { Column { val items = results[c.id].orEmpty(); if (items.isEmpty()) Text("点击检测后自动拉取并展示全部模型", Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp); items.forEach { r -> Surface(Modifier.fillMaxWidth().padding(vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(12.dp)) { Row { Text(r.name, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Text(r.status, fontSize = 12.sp, color = if (r.status == "可用") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }; Text("HTTP ${r.httpStatus ?: "—"} · ${r.latencyMs?.let { "$it ms" } ?: "—"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); if (r.detail.isNotEmpty()) Text(r.detail, fontSize = 12.sp); if (r.status == "不可用") LabelButton({ job = scope.launch { try { val updated = checker.testModel(c, repo.crypto.decrypt(c.encryptedKey), r.name); val list = results.getValue(c.id).map { if (it.name == r.name) updated else it }; results = results + (c.id to list); repo.dao.byId(c.id)?.let { repo.dao.save(it.copy(modelResultsJson = Json.encodeToString(list))) } } finally { job = null } } }, Lucide.Refresh, "重测此模型", enabled = job?.isActive != true) } } } } }
    } }; AppCard { Text("仅在点击后联网，串行检测该配置的全部模型；可能消耗少量 API 额度。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
@Composable private fun ExpandableCard(title: String, icon: ImageVector, subtitle: String = "", content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    AppCard {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { expanded = !expanded }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null); Spacer(Modifier.width(12.dp)); Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(if (expanded) Lucide.ChevronUp else Lucide.ChevronDown, "展开或收起")
        }
        AnimatedVisibility(expanded) { Column(Modifier.padding(top = 12.dp), content = content) }
    }
}
@Composable fun SettingsPage(repo: VaultRepository, prefs: android.content.SharedPreferences, mode: ThemeMode, change: (ThemeMode) -> Unit, locked: Boolean, lockChange: (Boolean) -> Unit, about: () -> Unit, toast: (String) -> Unit) {
    ExpandableCard("外观", Lucide.Sun, when (mode) { ThemeMode.SYSTEM -> "跟随系统"; ThemeMode.LIGHT -> "浅色模式"; ThemeMode.DARK -> "深色模式" }) {
        listOf(Triple(ThemeMode.SYSTEM, "跟随系统", Lucide.Monitor), Triple(ThemeMode.LIGHT, "浅色模式", Lucide.Sun), Triple(ThemeMode.DARK, "深色模式", Lucide.Moon)).forEach { (value, label, icon) ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { change(value) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null); Spacer(Modifier.width(12.dp)); Text(label, Modifier.weight(1f)); RadioButton(mode == value, { change(value) }) }
        }
    }
    ExpandableCard("安全", Lucide.Lock) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text("打开软件时验证身份", Modifier.weight(1f)); Switch(locked, lockChange) }
        Text("使用系统生物识别或设备锁屏密码，验证通过后才能查看配置。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text("备份包含全部配置、检测结果和外观设置。导入会覆盖当前全部数据。", Modifier.padding(top = 10.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    exportResult?.let { result -> AlertDialog({ exportResult = null }, title = { Text("导出全量备份") }, text = { Column { Text("请保存备份凭证。恢复时必须输入相同凭证。"); SelectionContainer { Text(result.credential, Modifier.padding(vertical = 14.dp), style = MaterialTheme.typography.bodySmall) }; LabelButton({ clipboard.setText(AnnotatedString(result.credential)); Toast.makeText(context, "备份凭证已复制", Toast.LENGTH_SHORT).show() }, Lucide.Copy, "复制凭证") } }, confirmButton = { Button({ saver.launch("Aivora-全量备份-${System.currentTimeMillis()}${Backup.EXTENSION}") }) { Text("选择保存位置") } }, dismissButton = { TextButton({ exportResult = null }) { Text("取消") } }) }
    importUri?.let { uri -> AlertDialog({ importUri = null }, title = { Text("输入备份凭证") }, text = { OutlinedTextField(credential, { credential = it }, Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), label = { Text("备份凭证") }, singleLine = true) }, confirmButton = { Button({ scope.launch { busy = true; try { val data = withContext(Dispatchers.IO) { Backup.import(context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("文件读取失败"), credential) }; pending = data; importUri = null } catch (e: Exception) { toast(e.message ?: "备份无效") }; busy = false } }, enabled = !busy) { Text("验证备份") } }, dismissButton = { TextButton({ importUri = null }) { Text("取消") } }) }
    pending?.let { data -> AlertDialog({ pending = null }, title = { Text("覆盖当前全部数据？") }, text = { Text("备份包含 ${data.configs.size} 个配置。恢复后当前配置、检测结果和外观设置将被替换，此操作无法撤销。") }, confirmButton = { Button({ scope.launch { try { repo.replaceAll(data, prefs); themeChange(ThemeMode.valueOf(data.theme)); pending = null; toast("全量数据恢复成功") } catch (e: Exception) { toast(e.message ?: "恢复失败") } } }) { Text("确认覆盖恢复") } }, dismissButton = { TextButton({ pending = null }) { Text("取消") } }) }
}
@Composable fun AboutPage(protocol: () -> Unit) {
    val uri = LocalUriHandler.current
    AppCard { Text("Aivora", fontSize = 25.sp, fontWeight = FontWeight.Bold); Text("本地 AI 配置管理工具\n版本 0.0.5 · AEX 1.0\nAGPL-3.0-only\n数据仅保存在当前设备，不依赖云端账户。") }
    AppCard { Text("开放生态", fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); LabelButton(protocol, Lucide.File, "适配协议", Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp)); LabelButton({ uri.openUri("https://github.com/Farewell-coder/Aivora") }, Lucide.Github, "GitHub 项目", Modifier.fillMaxWidth()) }
    AppCard { Text("安全边界", fontWeight = FontWeight.Bold); Text("凭证用于加密导出文件。第三方成功解密后可接触明文 API Key；请仅向可信服务提交密钥。", fontSize = 12.sp) }
}
@Composable fun ProtocolPage() {
    AppCard { Text("AKCP 1.0", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Aivora Key Configuration Protocol 是开放的配置交换协议，版本与加密方案独立演进。") }
    AppCard { Text("文件与加密", fontWeight = FontWeight.Bold); Text("文件后缀：.aivora\n容器：AKCP 二进制容器\n凭证：随机 256 位 Base32 字符串\n密钥派生：Argon2id\n内容加密：AES-256-GCM\n凭证不写入文件，文件通过认证标签校验完整性。") }
    AppCard { Text("导入与导出", fontWeight = FontWeight.Bold); Text("导出时将单个配置加密为 .aivora 文件；导入时选择文件并输入对应凭证。凭证错误、文件被修改或格式不支持时不会写入配置。\n\n全量备份使用独立的 .aivora-backup 格式，仅用于 Aivora 数据恢复，不与 AKCP 配置交换文件混用。") }
}
@Composable fun Empty(text: String) = Column(Modifier.fillMaxWidth().padding(vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Lucide.Search, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f)); Spacer(Modifier.height(10.dp)); Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }

/** Reads versionName from the installed package; falls back to a neutral label. */
fun packageVersion(context: android.content.Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull().orEmpty().ifBlank { "unknown" }

/** Generates one .akcp for [targets] and opens the system share sheet. */
suspend fun shareProfiles(context: android.content.Context, repo: VaultRepository, appVersion: String, targets: List<ServiceConfig>) {
    val profiles = targets.map { repo.toAex(it) }
    val exported = Aex.export(profiles, appVersion)
    val fileName = if (targets.size == 1) AexShare.sanitize(targets.first().name) else "Aivora-" + targets.size + "-configs" + Aex.EXTENSION
    val prepared = AexShare.prepare(context, exported, fileName, "Aivora 配置（${targets.size} 项）")
        .getOrElse { throw IllegalStateException(it.message ?: "无法生成临时文件") }
    repo.dao.recordExport(ExportRecord(fileName = fileName, encryptedCredential = repo.crypto.encrypt(exported.credential), configCount = exported.profileCount))
    context.startActivity(prepared.first)
}
