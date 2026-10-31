package app.aivora.vault

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.aivora.vault.data.*
import app.aivora.vault.protocol.Akcp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.encodeToString
import java.text.DateFormat
import java.util.Date

private val Brand = Color(0xFF6750A4)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AivoraApp((application as AivoraApplication).repository) }
    }
}
enum class Page(val title: String) { Configs("服务配置"), Diagnostics("密钥检测"), Settings("设置"), About("关于") }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable fun AivoraApp(repo: VaultRepository) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("appearance", Context.MODE_PRIVATE) }
    var themeMode by remember { mutableStateOf(ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!)) }
    var page by remember { mutableStateOf(Page.Configs) }
    var configs by remember { mutableStateOf(listOf<ServiceConfig>()) }
    var editing by remember { mutableStateOf<ServiceConfig?>(null) }; var showEditor by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }; val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { repo.dao.configs().collectLatest { configs = it } }
    LaunchedEffect(message) { if (message.isNotEmpty()) { snack.showSnackbar(message); message = "" } }
    val dark = when (themeMode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    val scheme = if (dark) darkColorScheme(primary = Color(0xFFD0BCFF), primaryContainer = Color(0xFF4F378B)) else lightColorScheme(primary = Brand, background = Color(0xFFFEF7FF), surface = Color.White)
    MaterialTheme(scheme, shapes = Shapes(medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp))) {
        Scaffold(snackbarHost = { SnackbarHost(snack) }, containerColor = scheme.background, bottomBar = { if (page == Page.Configs || page == Page.Diagnostics) FloatingNav(page) { page = it } }) { pad ->
            Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(20.dp)); PageHeader(page) { page = Page.Configs }
                when (page) {
                    Page.Configs -> ConfigPage(configs, repo, { editing = it; showEditor = true }, { editing = null; showEditor = true }, { page = it }) { message = it }
                    Page.Diagnostics -> DiagnosticsPage(configs, repo) { message = it }
                    Page.Settings -> SettingsPage(themeMode) { themeMode = it; prefs.edit().putString("theme", it.name).apply() }
                    Page.About -> AboutPage()
                }; Spacer(Modifier.height(36.dp))
            }
        }; if (showEditor) ConfigEditor(editing, repo, { showEditor = false }) { message = it }
    }
}
@Composable private fun PageHeader(page: Page, back: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
        if (page == Page.Settings || page == Page.About) { IconButton(back) { Icon(Lucide.ArrowLeft, "返回") }; Spacer(Modifier.width(8.dp)) }
        else { Surface(Modifier.size(42.dp), color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(14.dp)) { Box(contentAlignment = Alignment.Center) { Text("A", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) } }; Spacer(Modifier.width(12.dp)) }
        Text(page.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }; Spacer(Modifier.height(18.dp))
}
@Composable private fun FloatingNav(selected: Page, select: (Page) -> Unit) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 14.dp), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(.94f), tonalElevation = 5.dp, shadowElevation = 8.dp, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(.55f))) {
            Row(Modifier.padding(5.dp)) { listOf(Page.Configs to Lucide.List, Page.Diagnostics to Lucide.Activity).forEach { (p, icon) ->
                val active = selected == p
                Surface(Modifier.clip(CircleShape).clickable { select(p) }, color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, shape = CircleShape) {
                    Column(Modifier.width(116.dp).padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, p.title, Modifier.size(21.dp), tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(3.dp)); Text(p.title, fontSize = 11.sp, maxLines = 1, softWrap = false) }
                }
            } }
        }
    }
}
@Composable fun AppCard(content: @Composable ColumnScope.() -> Unit) = Surface(Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(.45f))) { Column(Modifier.padding(16.dp), content = content) }
@Composable fun LabelButton(onClick: () -> Unit, icon: ImageVector, text: String, modifier: Modifier = Modifier, enabled: Boolean = true) = FilledTonalButton(onClick, modifier, enabled = enabled, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text(text, maxLines = 1) }
@Composable fun ConfigSummary(c: ServiceConfig) {
    Row(verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(42.dp), color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(13.dp)) { Box(contentAlignment = Alignment.Center) { Text(c.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer) } }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(c.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(listOf(c.provider, c.endpoint).filter(String::isNotEmpty).joinToString(" · ").ifEmpty { "尚未填写 Endpoint" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
}
@Composable fun ConfigPage(configs: List<ServiceConfig>, repo: VaultRepository, edit: (ServiceConfig) -> Unit, add: () -> Unit, navigate: (Page) -> Unit, toast: (String) -> Unit) {
    var search by remember { mutableStateOf("") }; var deleting by remember { mutableStateOf<ServiceConfig?>(null) }; var exporting by remember { mutableStateOf<ServiceConfig?>(null) }
    val scope = rememberCoroutineScope(); val context = LocalContext.current
    var importFile by remember { mutableStateOf<Pair<Uri, String>?>(null) }; var importCredential by remember { mutableStateOf("") }; var checkingImport by remember { mutableStateOf(false) }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> val name = context.contentResolver.query(uri, null, null, null, null)?.use { cur -> if (cur.moveToFirst()) cur.getString(cur.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else "" } ?: ""; importFile = uri to name; importCredential = "" } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { LabelButton(add, Lucide.Plus, "新建配置", Modifier.weight(1f)); LabelButton({ openLauncher.launch(arrayOf("application/octet-stream", "*/*")) }, Lucide.Import, "导入", Modifier.weight(1f)) }
    Spacer(Modifier.height(12.dp)); OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), leadingIcon = { Icon(Lucide.Search, null) }, placeholder = { Text("搜索名称、服务商或 Endpoint") }, singleLine = true, shape = CircleShape); Spacer(Modifier.height(14.dp))
    val filtered = configs.filter { "${it.name} ${it.provider} ${it.endpoint}".contains(search, true) }
    if (filtered.isEmpty()) AppCard { Empty("还没有匹配的服务配置") } else filtered.forEach { c -> AppCard { ConfigSummary(c); Spacer(Modifier.height(14.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) { LabelButton({ edit(c) }, Lucide.Edit, "编辑", Modifier.weight(1f)); LabelButton({ exporting = c }, Lucide.Export, "导出", Modifier.weight(1f)); LabelButton({ deleting = c }, Lucide.Trash, "删除", Modifier.weight(1f)) } } }
    AppCard { Row(Modifier.fillMaxWidth().clickable { navigate(Page.Settings) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Lucide.Settings, null); Spacer(Modifier.width(12.dp)); Text("设置", Modifier.weight(1f), fontWeight = FontWeight.Medium); Icon(Lucide.ChevronRight, null, Modifier.size(19.dp)) }; HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant); Row(Modifier.fillMaxWidth().clickable { navigate(Page.About) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Lucide.Info, null); Spacer(Modifier.width(12.dp)); Text("关于", Modifier.weight(1f), fontWeight = FontWeight.Medium); Icon(Lucide.ChevronRight, null, Modifier.size(19.dp)) } }
    deleting?.let { c -> AlertDialog({ deleting = null }, title = { Text("删除服务配置") }, text = { Text("确定删除「${c.name}」吗？此操作无法撤销。") }, confirmButton = { TextButton({ scope.launch { repo.dao.delete(c); deleting = null; toast("配置已删除") } }) { Text("确认删除") } }, dismissButton = { TextButton({ deleting = null }) { Text("取消") } }) }
    exporting?.let { c -> ExportDialog(c, repo, { exporting = null }, toast) }
    importFile?.let { (uri, name) -> AlertDialog({ if (!checkingImport) importFile = null }, title = { Text("输入导入凭证") }, text = { Column { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(10.dp)); OutlinedTextField(importCredential, { importCredential = it }, Modifier.fillMaxWidth(), label = { Text("导出凭证") }, singleLine = true, visualTransformation = PasswordVisualTransformation()) } }, confirmButton = { Button({ if (importCredential.isBlank()) toast("请输入导出凭证") else scope.launch { checkingImport = true; try { val data = withContext(Dispatchers.IO) { Akcp.requireFilename(name); val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("文件读取失败"); Akcp.import(bytes, importCredential) }; data.forEach { item -> configs.firstOrNull { it.name == item.config_name && it.endpoint == item.endpoint }?.let { repo.dao.delete(it) }; repo.dao.save(repo.fromAkcp(item)) }; importFile = null; toast("已导入 ${data.size} 条配置") } catch (e: Exception) { toast(e.message ?: "凭证错误或文件无效") } finally { checkingImport = false } } }, enabled = !checkingImport) { Text(if (checkingImport) "验证中…" else "导入") } }, dismissButton = { TextButton({ importFile = null }, enabled = !checkingImport) { Text("取消") } }) }
}
@Composable fun ExportDialog(config: ServiceConfig, repo: VaultRepository, close: () -> Unit, toast: (String) -> Unit) {
    val context = LocalContext.current; val clipboard = LocalClipboardManager.current; val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }; var pending by remember { mutableStateOf<Pair<ByteArray, String>?>(null) }
    val result = remember { if (config.provider.isBlank() || config.endpoint.isBlank() || config.encryptedKey.isBlank()) null else Akcp.export(listOf(repo.toAkcp(config))) }
    val filename = remember { "Aivora-${config.name.replace(Regex("[^\\p{L}\\p{N}_-]"), "_")}.aivora" }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if (uri != null) pending?.let { (bytes, credential) -> scope.launch(Dispatchers.IO) { try { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("无法写入文件"); repo.dao.recordExport(ExportRecord(fileName = filename, encryptedCredential = repo.crypto.encrypt(credential), configCount = 1)); withContext(Dispatchers.Main) { pending = null; close(); toast("导出成功") } } catch (e: Exception) { withContext(Dispatchers.Main) { toast(e.message ?: "导出失败") } } } } }
    AlertDialog(close, title = { Text("导出「${config.name}」") }, text = { Column { Text("导入此文件时需要以下凭证，请妥善保存。", color = MaterialTheme.colorScheme.onSurfaceVariant); Surface(Modifier.fillMaxWidth().padding(vertical = 14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(14.dp)) { SelectionContainer { Text(result?.credential ?: "请先补全服务商、Endpoint 和 API Key", Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall) } }; LabelButton({ result?.let { clipboard.setText(AnnotatedString(it.credential)) }; toast("凭证已复制") }, Lucide.Copy, "复制凭证") } }, confirmButton = { Button({ scope.launch { working = true; try { if (config.provider.isBlank() || config.endpoint.isBlank() || config.encryptedKey.isBlank()) error("配置信息不完整"); KeyDiagnostics().fetchModels(config, repo.crypto.decrypt(config.encryptedKey)); val exported = result ?: error("配置信息不完整"); pending = exported.bytes to exported.credential; saveLauncher.launch(filename) } catch (e: Exception) { toast("连接验证失败，未导出：${e.message ?: "无法连接"}") } finally { working = false } } }, enabled = !working) { if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else { Icon(Lucide.Export, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text("导出") } } }, dismissButton = { TextButton(close, enabled = !working) { Text("取消") } })
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ConfigEditor(old: ServiceConfig?, repo: VaultRepository, close: () -> Unit, toast: (String) -> Unit) {
    var name by remember { mutableStateOf(old?.name.orEmpty()) }; var provider by remember { mutableStateOf(old?.provider.orEmpty()) }; var expanded by remember { mutableStateOf(false) }; var endpoint by remember { mutableStateOf(old?.endpoint.orEmpty()) }; var key by remember { mutableStateOf("") }; var notes by remember { mutableStateOf(old?.notes.orEmpty()) }; var visible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope(); val clipboard = LocalClipboardManager.current
    LaunchedEffect(old) { if (old?.encryptedKey?.isNotEmpty() == true) key = repo.crypto.decrypt(old.encryptedKey) }
    AlertDialog(close, title = { Text(if (old == null) "添加服务配置" else "编辑服务配置") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { Field("配置名称", name) { name = it }; ExposedDropdownMenuBox(expanded, { expanded = it }) { OutlinedTextField(provider, {}, Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable), readOnly = true, label = { Text("服务商模板") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }); ExposedDropdownMenu(expanded, { expanded = false }) { providerTemplates.forEach { t -> DropdownMenuItem(text = { Text(t.name) }, onClick = { provider = if (t.name == "空白模板" || t.name == "自定义") "" else t.name; endpoint = t.endpoint; expanded = false }) } } }; Field("API Endpoint（选填）", endpoint) { endpoint = it }; OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("API Key（选填）") }, singleLine = true, visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { Row { IconButton({ visible = !visible }) { Icon(if (visible) Lucide.EyeOff else Lucide.Eye, "显示或隐藏密钥") }; IconButton({ clipboard.setText(AnnotatedString(key)); toast("API Key 已复制") }) { Icon(Lucide.Copy, "复制密钥") } } }); Field("备注（选填）", notes) { notes = it } } }, confirmButton = { Button({ if (name.isBlank()) toast("请填写配置名称") else scope.launch { repo.dao.save(ServiceConfig(old?.id ?: 0, name.trim(), provider, if (key.isBlank()) "" else repo.crypto.encrypt(key), endpoint.trim(), "", "", notes, old?.enabled ?: true, old?.isDefault ?: false, old?.createdAt ?: System.currentTimeMillis(), old?.lastCheckedAt ?: 0, old?.checkStatus ?: "未检测", old?.httpStatus, old?.extensionsJson ?: "{}", old?.modelResultsJson ?: "[]")); close(); toast("配置已保存") } }) { Text("保存") } }, dismissButton = { TextButton(close) { Text("取消") } })
}
@Composable fun Field(label: String, value: String, change: (String) -> Unit) = OutlinedTextField(value, change, Modifier.fillMaxWidth().padding(vertical = 5.dp), label = { Text(label) }, singleLine = true)
@Composable fun DiagnosticsPage(configs: List<ServiceConfig>, repo: VaultRepository, toast: (String) -> Unit) {
    val scope = rememberCoroutineScope(); val checker = remember { KeyDiagnostics() }; var job by remember { mutableStateOf<Job?>(null) }; var currentId by remember { mutableStateOf<Long?>(null) }; var results by remember { mutableStateOf(mapOf<Long, List<ModelResult>>()) }; var expanded by remember { mutableStateOf(setOf<Long>()) }
    LaunchedEffect(configs) { if (job?.isActive != true) results = configs.associate { it.id to try { kotlinx.serialization.json.Json.decodeFromString<List<ModelResult>>(it.modelResultsJson) } catch (_: Exception) { emptyList() } } }
    suspend fun diagnose(c: ServiceConfig) {
        currentId = c.id
        if (c.endpoint.isBlank() || c.encryptedKey.isBlank()) { repo.dao.save(c.copy(checkStatus = "请先补充 Endpoint 和 API Key")); toast("请先补充 Endpoint 和 API Key"); return }
        try { val key = repo.crypto.decrypt(c.encryptedKey); val models = checker.fetchModels(c, key); repo.dao.save(c.copy(models = models.joinToString(","), checkStatus = "正在检测 ${models.size} 个模型", modelResultsJson = "[]")); results += c.id to models.map { ModelResult(it, "等待检测") }; models.forEachIndexed { index, model -> currentCoroutineContext().ensureActive(); results += c.id to results.getValue(c.id).mapIndexed { i, r -> if (i == index) r.copy(status = "检测中") else r }; val result = checker.testModel(c, key, model); results += c.id to results.getValue(c.id).mapIndexed { i, r -> if (i == index) result else r }; repo.dao.save(repo.dao.byId(c.id)!!.copy(checkStatus = "已检测 ${index + 1}/${models.size}", lastCheckedAt = System.currentTimeMillis(), modelResultsJson = kotlinx.serialization.json.Json.encodeToString(results.getValue(c.id)))) }; repo.dao.save(repo.dao.byId(c.id)!!.copy(checkStatus = "检测完成：${results.getValue(c.id).count { it.status == "可用" }}/${models.size} 个模型可用", lastCheckedAt = System.currentTimeMillis())) } catch (e: CancellationException) { throw e } catch (e: Exception) { repo.dao.save(c.copy(checkStatus = e.message ?: "检测失败", lastCheckedAt = System.currentTimeMillis())) }
    }
    fun start(c: ServiceConfig) { job = scope.launch { diagnose(c); currentId = null; job = null } }
    if (configs.isEmpty()) AppCard { Empty("暂无可检测配置") } else configs.forEach { c -> AppCard {
        Row(Modifier.fillMaxWidth().clickable { expanded = expanded.toMutableSet().apply { if (!add(c.id)) remove(c.id) } }, verticalAlignment = Alignment.CenterVertically) { Box(Modifier.weight(1f)) { ConfigSummary(c) }; Icon(if (c.id in expanded) Lucide.ChevronUp else Lucide.ChevronDown, "展开或收起") }
        Text(c.checkStatus, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp); Text("检测时间：${if (c.lastCheckedAt > 0) DateFormat.getDateTimeInstance().format(Date(c.lastCheckedAt)) else "未检测"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { LabelButton({ expanded += c.id; start(c) }, Lucide.Play, if (currentId == c.id && job?.isActive == true) "检测中" else "检测", enabled = job?.isActive != true); if (currentId == c.id && job?.isActive == true) LabelButton({ job?.cancel(); job = null; currentId = null }, Lucide.Stop, "停止") }
        AnimatedVisibility(c.id in expanded) { Column { val items = results[c.id].orEmpty(); if (items.isEmpty()) Text("点击检测后自动拉取并展示全部模型", Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp); items.forEach { r -> Surface(Modifier.fillMaxWidth().padding(vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(12.dp)) { Row { Text(r.name, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Text(r.status, fontSize = 12.sp, color = if (r.status == "可用") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }; Text("HTTP ${r.httpStatus ?: "—"} · ${r.latencyMs?.let { "$it ms" } ?: "—"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); if (r.detail.isNotEmpty()) Text(r.detail, fontSize = 12.sp); if (r.status == "不可用") LabelButton({ job = scope.launch { try { val updated = checker.testModel(c, repo.crypto.decrypt(c.encryptedKey), r.name); results += c.id to results.getValue(c.id).map { if (it.name == r.name) updated else it }; repo.dao.save(repo.dao.byId(c.id)!!.copy(modelResultsJson = kotlinx.serialization.json.Json.encodeToString(results.getValue(c.id)))) } finally { job = null } } }, Lucide.Refresh, "重测此模型", enabled = job?.isActive != true) } } } } }
    } }; AppCard { Text("仅在点击后联网，串行检测该配置的全部模型；可能消耗少量 API 额度。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
@Composable fun SettingsPage(mode: ThemeMode, change: (ThemeMode) -> Unit) {
    Text("外观", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); AppCard { listOf(Triple(ThemeMode.SYSTEM, "跟随系统", Lucide.Monitor), Triple(ThemeMode.LIGHT, "浅色模式", Lucide.Sun), Triple(ThemeMode.DARK, "深色模式", Lucide.Moon)).forEach { (value, label, icon) -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { change(value) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null); Spacer(Modifier.width(12.dp)); Text(label, Modifier.weight(1f)); RadioButton(mode == value, { change(value) }) } } }; Text("设置会保存在当前设备，重启后保持。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
}
@Composable fun AboutPage() {
    val uri = LocalUriHandler.current
    AppCard { Text("Aivora", fontSize = 25.sp, fontWeight = FontWeight.Bold); Text("本地 AI 配置管理工具\n版本 0.0.2 · AKCP 1.0\nAGPL-3.0-only\n数据仅保存在当前设备，不依赖云端账户。") }
    AppCard { Text("开放生态", fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); LabelButton({ uri.openUri("https://github.com/Farewell-coder/Aivora/blob/main/AKCP/README.md") }, Lucide.File, "适配协议", Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp)); LabelButton({ uri.openUri("https://github.com/Farewell-coder/Aivora") }, Lucide.Github, "GitHub 项目", Modifier.fillMaxWidth()) }
    AppCard { Text("安全边界", fontWeight = FontWeight.Bold); Text("凭证用于加密导出文件。第三方成功解密后可接触明文 API Key；请仅向可信服务提交密钥。", fontSize = 12.sp) }
}
@Composable fun Empty(text: String) = Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) }