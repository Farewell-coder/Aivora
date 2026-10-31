package app.aivora.vault

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import app.aivora.vault.data.*
import app.aivora.vault.protocol.Akcp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.encodeToString
import java.text.DateFormat
import java.util.Date

private val Purple = Color(0xFF7466DC)
private val Bg = Color(0xFFF5F5FA)
private val Muted = Color(0xFF9292A5)
private val Green = Color(0xFF319475)
private val Orange = Color(0xFFBD8439)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { AivoraApp((application as AivoraApplication).repository) } }
}
enum class Page(val title: String, val icon: ImageVector) {
    Home("总览", Icons.Default.Home), Configs("服务配置", Icons.Default.Dns), Transfer("配置交换", Icons.Default.SwapHoriz),
    Diagnostics("密钥检测", Icons.Default.MonitorHeart), Protocol("AKCP 协议", Icons.Default.Description),
    Settings("设置与增强", Icons.Default.Settings), About("关于", Icons.Default.Info)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AivoraApp(repo: VaultRepository) {
    var page by remember { mutableStateOf(Page.Home) }; var configs by remember { mutableStateOf(listOf<ServiceConfig>()) }
    var editing by remember { mutableStateOf<ServiceConfig?>(null) }; var showEditor by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf("") }; var dark by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope(); val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { repo.dao.configs().collectLatest { configs = it } }
    LaunchedEffect(toast) { if (toast.isNotEmpty()) { snack.showSnackbar(toast); toast = "" } }
    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFFB9AEFF)) else lightColorScheme(primary = Purple, background = Bg, surface = Color.White)) {
        Scaffold(snackbarHost = { SnackbarHost(snack) }, bottomBar = {
            NavigationBar { Page.entries.filter { it != Page.About }.take(5).forEach { p -> NavigationBarItem(page == p, { page = p; showEditor = false }, { Icon(p.icon, null) }, label = { Text(p.title, fontSize = 10.sp) }) } }
        }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Purple, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(44.dp)) { Box(contentAlignment = Alignment.Center) { Text("A", color = Color.White, fontWeight = FontWeight.Black, fontSize = 23.sp) } }
                    Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Aivora", fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("LOCAL AI CONFIGURATION", fontSize = 9.sp, color = Muted) }
                    IconButton({ page = Page.About }) { Icon(Icons.Default.Info, "关于") }
                }
                Spacer(Modifier.height(20.dp)); Text(page.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("● 本地模式运行中", color = Green, fontSize = 12.sp); Spacer(Modifier.height(18.dp))
                when (page) {
                    Page.Home -> Home(configs, { page = it }, { showEditor = true; editing = null; page = Page.Configs })
                    Page.Configs -> ConfigPage(configs, repo, { editing = it; showEditor = true }, { editing = null; showEditor = true }, { toast = it })
                    Page.Transfer -> TransferPage(configs, repo) { toast = it }
                    Page.Diagnostics -> DiagnosticsPage(configs, repo) { toast = it }
                    Page.Protocol -> ProtocolPage()
                    Page.Settings -> SettingsPage(dark, { dark = it }, { page = Page.Transfer })
                    Page.About -> AboutPage()
                }
                Spacer(Modifier.height(30.dp))
            }
        }
        if (showEditor) ConfigEditor(editing, repo, { showEditor = false }, { toast = it })
    }
}
@Composable fun Card(content: @Composable ColumnScope.() -> Unit) = Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) { Column(Modifier.padding(18.dp), content = content) }
@Composable fun Home(configs: List<ServiceConfig>, navigate: (Page) -> Unit, add: () -> Unit) {
    Button(add, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text(" 添加配置") }
    Spacer(Modifier.height(14.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("服务配置" to configs.size, "已保存密钥" to configs.count { it.encryptedKey.isNotEmpty() }, "启用配置" to configs.count { it.enabled }).forEach { (label, n) ->
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) { Column(Modifier.padding(15.dp)) { Text(label, fontSize = 11.sp, color = Muted); Text("$n", fontSize = 28.sp, fontWeight = FontWeight.Bold) } }
        }
    }; Spacer(Modifier.height(14.dp))
    Card { Text("最近的服务配置", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); if (configs.isEmpty()) Empty("还没有服务配置") else configs.take(4).forEach { ConfigRow(it) } }
    Card { Text("快速操作", fontWeight = FontWeight.Bold); ActionRow("导出配置", "生成加密 .aivora 文件") { navigate(Page.Transfer) }; ActionRow("检测密钥", "检查服务连接与密钥状态") { navigate(Page.Diagnostics) }; ActionRow("开放协议", "查看 AKCP 适配说明") { navigate(Page.Protocol) } }
}
@Composable fun ConfigRow(c: ServiceConfig) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = Purple.copy(.1f), shape = RoundedCornerShape(12.dp), modifier = Modifier.size(42.dp)) { Box(contentAlignment = Alignment.Center) { Text(c.name.take(1).uppercase(), color = Purple, fontWeight = FontWeight.Bold) } }
        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(c.name, fontWeight = FontWeight.Bold); Text("${c.provider} · ${c.defaultModel.ifEmpty { "未设置模型" }}", fontSize = 11.sp, color = Muted, maxLines = 1) }
        Status(if (c.enabled) "已启用" else "已停用", c.enabled)
    }
}
@Composable fun ConfigPage(configs: List<ServiceConfig>, repo: VaultRepository, edit: (ServiceConfig) -> Unit, add: () -> Unit, toast: (String) -> Unit) {
    var search by remember { mutableStateOf("") }; var delete by remember { mutableStateOf<ServiceConfig?>(null) }; val scope = rememberCoroutineScope()
    OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("搜索名称、服务商或模型") }, singleLine = true)
    Spacer(Modifier.height(10.dp)); Button(add, Modifier.fillMaxWidth()) { Text("＋ 新建配置") }; Spacer(Modifier.height(14.dp))
    val filtered = configs.filter { "${it.name} ${it.provider} ${it.defaultModel}".contains(search, true) }
    if (filtered.isEmpty()) Card { Empty("没有匹配的配置") } else filtered.forEach { c -> Card {
        ConfigRow(c); Text("Endpoint：${c.endpoint}", fontSize = 11.sp, color = Muted, maxLines = 1); Spacer(Modifier.height(10.dp))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip({ edit(c) }, { Text("编辑/详情") }); AssistChip({ scope.launch { repo.dao.save(c.copy(enabled = !c.enabled)) } }, { Text(if (c.enabled) "停用" else "启用") })
            AssistChip({ scope.launch { repo.dao.clearDefaults(); repo.dao.setDefault(c.id, true); toast("已设为默认配置") } }, { Text(if (c.isDefault) "默认配置" else "设为默认") }); AssistChip({ delete = c }, { Text("删除", color = MaterialTheme.colorScheme.error) })
        }
    } }
    delete?.let { c -> AlertDialog({ delete = null }, confirmButton = { TextButton({ scope.launch { repo.dao.delete(c); delete = null; toast("配置已删除") } }) { Text("确认删除") } }, dismissButton = { TextButton({ delete = null }) { Text("取消") } }, title = { Text("删除服务配置") }, text = { Text("确定删除「${c.name}」吗？此操作无法撤销。") }) }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ConfigEditor(old: ServiceConfig?, repo: VaultRepository, close: () -> Unit, toast: (String) -> Unit) {
    var name by remember { mutableStateOf(old?.name.orEmpty()) }; var provider by remember { mutableStateOf(old?.provider.orEmpty()) }
    var templateExpanded by remember { mutableStateOf(false) }
    var endpoint by remember { mutableStateOf(old?.endpoint.orEmpty()) }; var key by remember { mutableStateOf("") }
    var models by remember { mutableStateOf(old?.models.orEmpty()) }; var model by remember { mutableStateOf(old?.defaultModel.orEmpty()) }; var notes by remember { mutableStateOf(old?.notes.orEmpty()) }
    var visible by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope(); val clipboard = LocalClipboardManager.current
    LaunchedEffect(old) { if (old != null) key = repo.crypto.decrypt(old.encryptedKey) }
    AlertDialog(close, confirmButton = { Button({ if (name.isBlank() || provider.isBlank() || endpoint.isBlank() || key.isBlank()) toast("请完整填写名称、服务商、Endpoint 和 Key") else scope.launch { repo.dao.save(ServiceConfig(old?.id ?: 0, name, provider, repo.crypto.encrypt(key), endpoint, models, model, notes, old?.enabled ?: true, old?.isDefault ?: false, old?.createdAt ?: System.currentTimeMillis(), old?.lastCheckedAt ?: 0, old?.checkStatus ?: "未检测", old?.httpStatus, old?.extensionsJson ?: "{}", old?.modelResultsJson ?: "[]")); close(); toast("配置已保存") } }) { Text("保存") } }, dismissButton = { TextButton(close) { Text("取消") } }, title = { Text(if (old == null) "添加服务配置" else "配置详情与编辑") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) {
        Field("配置名称", name) { name = it }
        ExposedDropdownMenuBox(templateExpanded, { templateExpanded = it }) {
            OutlinedTextField(provider, {}, Modifier.fillMaxWidth().menuAnchor(), readOnly = true,
                label = { Text("服务商模板（选择后可修改）") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(templateExpanded) },
                placeholder = { Text("选择模板") })
            ExposedDropdownMenu(templateExpanded, { templateExpanded = false }) {
                providerTemplates.forEach { template -> DropdownMenuItem(text = { Text(template.name) }, onClick = {
                    provider = if (template.name == "空白模板" || template.name == "自定义") "" else template.name
                    endpoint = template.endpoint; templateExpanded = false
                }) }
            }
        }
        Field("服务商", provider) { provider = it }; Field("API Endpoint", endpoint) { endpoint = it }
        OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("API Key") }, visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { Row { IconButton({ visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) }; IconButton({ clipboard.setText(androidx.compose.ui.text.AnnotatedString(key)); toast("API Key 已复制") }) { Icon(Icons.Default.ContentCopy, null) } } })
        Field("模型列表（逗号分隔）", models) { models = it }; Field("默认模型", model) { model = it }; Field("备注", notes) { notes = it }
        if (old != null) { Spacer(Modifier.height(10.dp)); Text("创建时间：${DateFormat.getDateTimeInstance().format(Date(old.createdAt))}", fontSize = 11.sp, color = Muted); Text("最近检测：${if (old.lastCheckedAt > 0) DateFormat.getDateTimeInstance().format(Date(old.lastCheckedAt)) else "未检测"}", fontSize = 11.sp, color = Muted); TextButton({ clipboard.setText(androidx.compose.ui.text.AnnotatedString(endpoint)); toast("Endpoint 已复制") }) { Text("复制 Endpoint") }; TextButton({ clipboard.setText(androidx.compose.ui.text.AnnotatedString(model)); toast("模型名称已复制") }) { Text("复制模型名称") } }
    } })
}
@Composable fun Field(label: String, value: String, change: (String) -> Unit) { OutlinedTextField(value, change, Modifier.fillMaxWidth().padding(vertical = 4.dp), label = { Text(label) }, singleLine = true) }
@Composable fun TransferPage(configs: List<ServiceConfig>, repo: VaultRepository, toast: (String) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }; var selected by remember { mutableStateOf(setOf<Long>()) }; var credential by remember { mutableStateOf("") }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }; var pendingName by remember { mutableStateOf("") }; var imported by remember { mutableStateOf(listOf<Akcp.Config>()) }
    val scope = rememberCoroutineScope(); val context = LocalContext.current; val clipboard = LocalClipboardManager.current
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if (uri != null) pendingBytes?.let { scope.launch(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { out -> out.write(it) }; withContext(Dispatchers.Main) { repo.dao.recordExport(ExportRecord(fileName = pendingName, encryptedCredential = repo.crypto.encrypt(credential), configCount = selected.size)); pendingBytes = null; toast("导出成功，凭证已加密保存") } } } }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) scope.launch(Dispatchers.IO) { try { val name = context.contentResolver.query(uri, null, null, null, null)?.use { if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else "" } ?: ""; Akcp.requireFilename(name); val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }!!; imported = Akcp.import(bytes, credential); withContext(Dispatchers.Main) { toast("验证成功，请确认导入预览") } } catch (e: Exception) { withContext(Dispatchers.Main) { toast(e.message ?: "文件读取失败") } } } }
    TabRow(tab) { Tab(tab == 0, { tab = 0 }, text = { Text("导出配置") }); Tab(tab == 1, { tab = 1 }, text = { Text("导入配置") }) }; Spacer(Modifier.height(15.dp))
    if (tab == 0) Card {
        Text("导出 AKCP 配置", fontWeight = FontWeight.Bold); Text("每次导出自动生成独立 256-bit Base32 凭证", fontSize = 11.sp, color = Muted)
        TextButton({ selected = if (selected.size == configs.size) emptySet() else configs.map { it.id }.toSet() }) { Text(if (selected.size == configs.size) "取消全选" else "全选") }
        configs.forEach { c -> Row(Modifier.clickable { selected = selected.toMutableSet().apply { if (!add(c.id)) remove(c.id) } }, verticalAlignment = Alignment.CenterVertically) { Checkbox(c.id in selected, null); Text(c.name, Modifier.weight(1f)) } }
        Button({ if (selected.isEmpty()) toast("请选择配置") else scope.launch(Dispatchers.IO) { val list = configs.filter { it.id in selected }.map(repo::toAkcp); val result = Akcp.export(list); credential = result.credential; pendingBytes = result.bytes; pendingName = "Aivora-${System.currentTimeMillis()}.aivora"; withContext(Dispatchers.Main) { saveLauncher.launch(pendingName) } } }, Modifier.fillMaxWidth()) { Text("生成加密配置") }
        if (credential.isNotEmpty()) { Spacer(Modifier.height(15.dp)); Text("导出凭证（请安全备份）", fontWeight = FontWeight.Bold); SelectionContainer { Text(credential, fontSize = 12.sp) }; TextButton({ clipboard.setText(androidx.compose.ui.text.AnnotatedString(credential)); toast("凭证已复制") }) { Text("复制凭证") } }
    } else Card {
        Text("仅支持 .aivora 文件", fontWeight = FontWeight.Bold); OutlinedTextField(credential, { credential = it }, Modifier.fillMaxWidth(), label = { Text("导出凭证") }, visualTransformation = PasswordVisualTransformation())
        Button({ if (credential.isBlank()) toast("请输入导出凭证") else openLauncher.launch(arrayOf("application/octet-stream", "*/*")) }, Modifier.fillMaxWidth().padding(vertical = 10.dp)) { Text("选择并验证文件") }
        if (imported.isNotEmpty()) { Text("导入预览（API Key 已隐藏）", fontWeight = FontWeight.Bold); imported.forEach { Text("• ${it.config_name} · ${it.provider} · ${it.endpoint}", fontSize = 12.sp) }
            Button({ scope.launch { imported.forEach { item -> val duplicate = configs.firstOrNull { it.name == item.config_name && it.endpoint == item.endpoint }; if (duplicate != null) repo.dao.delete(duplicate); repo.dao.save(repo.fromAkcp(item)) }; toast("已导入 ${imported.size} 条配置；同名同 Endpoint 配置已在本次确认后替换"); imported = emptyList() } }, Modifier.fillMaxWidth()) { Text("确认导入并替换重复项") }
            OutlinedButton({ scope.launch { imported.forEach { repo.dao.save(repo.fromAkcp(it)) }; toast("已另存 ${imported.size} 条新配置"); imported = emptyList() } }, Modifier.fillMaxWidth()) { Text("全部另存为新配置") }
        }
    }
}
@Composable fun DiagnosticsPage(configs: List<ServiceConfig>, repo: VaultRepository, toast: (String) -> Unit) {
    val scope = rememberCoroutineScope(); val checker = remember { KeyDiagnostics() }
    var job by remember { mutableStateOf<Job?>(null) }; var currentId by remember { mutableStateOf<Long?>(null) }
    var results by remember { mutableStateOf(mapOf<Long, List<ModelResult>>()) }
    LaunchedEffect(configs) {
        if (job?.isActive != true) {
            val codec = kotlinx.serialization.json.Json
            results = configs.associate { c -> c.id to try { codec.decodeFromString<List<ModelResult>>(c.modelResultsJson) } catch (_: Exception) { emptyList() } }
        }
    }
    suspend fun diagnose(c: ServiceConfig) {
        currentId = c.id
        try {
            val key = repo.crypto.decrypt(c.encryptedKey)
            val models = checker.fetchModels(c, key)
            repo.dao.save(c.copy(models = models.joinToString(","), checkStatus = "正在检测 ${models.size} 个模型", modelResultsJson = "[]"))
            results = results + (c.id to models.map { ModelResult(it, "等待检测") })
            models.forEachIndexed { index, model ->
                currentCoroutineContext().ensureActive()
                results = results + (c.id to results.getValue(c.id).mapIndexed { i, r -> if (i == index) r.copy(status = "检测中") else r })
                val result = checker.testModel(c, key, model)
                results = results + (c.id to results.getValue(c.id).mapIndexed { i, r -> if (i == index) result else r })
                val snapshot = results.getValue(c.id)
                repo.dao.save(repo.dao.byId(c.id)!!.copy(checkStatus = "已检测 ${index + 1}/${models.size}", lastCheckedAt = System.currentTimeMillis(), modelResultsJson = kotlinx.serialization.json.Json.encodeToString(snapshot)))
            }
            repo.dao.save(repo.dao.byId(c.id)!!.copy(checkStatus = "检测完成：${results.getValue(c.id).count { it.status == "可用" }}/${models.size} 个模型可用", lastCheckedAt = System.currentTimeMillis()))
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            repo.dao.save(c.copy(checkStatus = e.message ?: "密钥或模型列表检测失败", lastCheckedAt = System.currentTimeMillis()))
        }
    }
    fun start(list: List<ServiceConfig>) { job = scope.launch { list.forEach { diagnose(it) }; currentId = null; toast("模型检测完成") } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button({ start(configs.filter { it.enabled }) }, Modifier.weight(1f), enabled = job?.isActive != true && configs.any { it.enabled }) { Text(if (job?.isActive == true) "正在逐项检测…" else "检测全部配置") }
        if (job?.isActive == true) OutlinedButton({ job?.cancel(); job = null; currentId = null }) { Text("停止") }
    }
    Spacer(Modifier.height(14.dp))
    if (configs.isEmpty()) Card { Empty("暂无可检测配置") } else configs.forEach { c -> Card {
        ConfigRow(c)
        Text("状态：${c.checkStatus}")
        Text("检测时间：${if (c.lastCheckedAt > 0) DateFormat.getDateTimeInstance().format(Date(c.lastCheckedAt)) else "未检测"}", fontSize = 11.sp, color = Muted)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ start(listOf(c)) }, enabled = job?.isActive != true) { Text("检测密钥及全部模型") }
        }
        val items = results[c.id].orEmpty()
        if (items.isNotEmpty()) {
            Text("模型结果（${items.count { it.status == "可用" }} 可用 / ${items.size} 总计）", fontWeight = FontWeight.Bold)
            items.forEach { r -> Surface(Modifier.fillMaxWidth().padding(vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(r.status, color = if (r.status == "可用") Green else if (r.status == "检测中") Orange else MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    Text("HTTP ${r.httpStatus ?: "—"} · ${r.latencyMs?.let { "$it ms" } ?: "—"}", fontSize = 11.sp, color = Muted)
                    if (r.detail.isNotEmpty()) Text(r.detail, fontSize = 12.sp)
                    if (r.status == "不可用") TextButton({ job = scope.launch {
                        val key = repo.crypto.decrypt(c.encryptedKey); val updated = checker.testModel(c, key, r.name)
                        results = results + (c.id to results.getValue(c.id).map { if (it.name == r.name) updated else it })
                        repo.dao.save(repo.dao.byId(c.id)!!.copy(modelResultsJson = kotlinx.serialization.json.Json.encodeToString(results.getValue(c.id))))
                    } }, enabled = job?.isActive != true) { Text("重测此模型") }
                }
            } }
        }
    } }
    Card { Text("检测说明", fontWeight = FontWeight.Bold); Text("仅在点击后联网：先获取 /models，再串行对每个模型发送一次最小 chat/completions 请求（max_tokens=1）。展示 HTTP 状态、耗时和返回摘要。实际请求可能消耗少量 API 额度；取消仅阻止后续模型，已发出的请求可能继续完成。结果保存在当前设备。", fontSize = 12.sp) }
}
@Composable fun ProtocolPage() {
    Card { Text("AKCP 开放配置协议", fontWeight = FontWeight.Bold); Text("AKCP 1.0 · 加密方案 1 · Aivora 0.0.1\n三者版本独立管理。\n\n专用扩展名：.aivora\nAES-256-GCM + Argon2id\n独立 256-bit Base32 导出凭证\n二进制混合容器 + JSON 加密载荷", lineHeight = 23.sp) }
    Card { Text("第三方适配", fontWeight = FontWeight.Bold); Text("第三方仅依据公开规范即可实现识别、解密和映射。仅接收 .aivora 文件，随后验证 Magic、长度、版本和认证标签。无需官方 SDK、云端或 Aivora 在线服务。\n\n未知字段应尽可能保留；无法保留时必须提示用户。认证失败不得输出任何部分明文。") }
}
@Composable fun SettingsPage(dark: Boolean, change: (Boolean) -> Unit, transfer: () -> Unit) {
    Card { Text("本地安全", fontWeight = FontWeight.Bold); SettingRow("深色模式", "仅影响应用显示", dark, change); Text("API Key 默认隐藏并使用 Android Keystore 保护的数据密钥加密。应用数据不参与普通云备份。") }
    Card { Text("系统增强 · 可选", fontWeight = FontWeight.Bold); val running = remember { ShizukuStatus.running() }; val granted = remember { ShizukuStatus.granted() }; SettingRow("Shizuku 服务", if (running) "运行中" else "未运行 / 未安装", running) {}; if (running && !granted) Button({ ShizukuStatus.request() }) { Text("请求 Shizuku 授权") }; Text("Shizuku 不等于 Root，不能绕过应用沙盒。核心功能不依赖 Shizuku。", fontSize = 12.sp, color = Muted) }
    Card { Text("数据管理", fontWeight = FontWeight.Bold); Button(transfer) { Text("打开配置交换") } }
}
@Composable fun AboutPage() {
    val uri = LocalUriHandler.current
    Card { Text("Aivora", fontSize = 25.sp, fontWeight = FontWeight.Bold); Text("本地 AI 配置管理工具\n版本 0.0.1 · AKCP 1.0\n数据仅保存在当前设备，不依赖云端账户。") }
    Card { Text("开放生态", fontWeight = FontWeight.Bold); Button({ uri.openUri("https://github.com/Farewell-coder/Aivora/blob/main/AKCP/README.md") }, Modifier.fillMaxWidth()) { Text("适配协议") }; OutlinedButton({ uri.openUri("https://github.com/Farewell-coder/Aivora") }, Modifier.fillMaxWidth()) { Text("GitHub 项目") } }
    Card { Text("安全边界", fontWeight = FontWeight.Bold); Text("协议公开不代表文件可无凭证解密。第三方成功解密后可接触明文 API Key。设备被完全控制时，本地加密不能保证绝对安全。", fontSize = 12.sp) }
}
@Composable fun SettingRow(title: String, subtitle: String, value: Boolean, change: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, fontSize = 11.sp, color = Muted) }; Switch(value, change) } }
@Composable fun ActionRow(title: String, subtitle: String, click: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(vertical = 12.dp)) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, fontSize = 11.sp, color = Muted) }; Icon(Icons.Default.ChevronRight, null) } }
@Composable fun Status(text: String, good: Boolean) { Surface(color = (if (good) Green else Orange).copy(.1f), shape = RoundedCornerShape(20.dp)) { Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = if (good) Green else Orange, fontSize = 10.sp) } }
@Composable fun Empty(text: String) { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { Text(text, color = Muted) } }