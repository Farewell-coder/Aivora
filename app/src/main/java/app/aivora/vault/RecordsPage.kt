package app.aivora.vault

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aivora.vault.data.ExportRecord
import app.aivora.vault.data.VaultRepository
import app.aivora.vault.protocol.Aex
import app.aivora.vault.protocol.AexShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.text.DateFormat
import java.util.Date

/**
 * 导出记录页。
 *
 * 一条记录 = 一次导出动作。记录里保存当时的凭证与完整快照（含 API Key，主密钥加密），
 * 因此「重新导出」能复刻当初那份文件，而不是用配置的当前状态。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordsPage(
    records: List<ExportRecord>,
    configs: List<app.aivora.vault.data.ServiceConfig>,
    repo: VaultRepository,
    appVersion: String,
    toast: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var selecting by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var detail by remember { mutableStateOf<ExportRecord?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    // 顶部：总数 + 选择入口
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("共 ${records.size} 条导出记录", modifier = Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (records.isNotEmpty()) {
            LabelButton(
                { selecting = !selecting; if (!selecting) picked = emptySet() },
                Lucide.Check,
                if (selecting) "取消" else "选择"
            )
        }
    }
    Spacer(Modifier.height(12.dp))

    if (records.isEmpty()) {
        AppCard { Empty("还没有导出记录，导出一次配置后会出现在这里") }
        return
    }

    // 按日期分组
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val grouped = records.groupBy { df.format(Date(it.exportedAt)) }

    grouped.forEach { (day, items) ->
        Text(
            day,
            Modifier.padding(start = 4.dp, top = 6.dp, bottom = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        items.forEach { record ->
            val names = remember(record.id, record.configNames) { parseNames(record.configNames) }
            val missing = remember(record.id, names, configs) {
                // 「原配置已删除」判定：名称在当前配置里找不到。
                if (names.isEmpty()) false else names.all { n -> configs.none { it.name == n } }
            }
            AppCard {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .combinedClickable(
                            onClick = { if (selecting) picked = picked.toggle(record.id) else detail = record },
                            onLongClick = { selecting = true; picked = picked + record.id }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selecting) {
                        Checkbox(record.id in picked, { on -> picked = if (on) picked + record.id else picked - record.id })
                        Spacer(Modifier.width(4.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            record.fileName,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${record.configCount} 个配置 · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(record.exportedAt))}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (names.isNotEmpty()) {
                            Text(
                                names.joinToString("、"),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (missing) {
                            Text("原配置已删除", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f))
                        }
                    }
                    if (!selecting) Icon(Lucide.ChevronRight, null, Modifier.size(19.dp))
                }
            }
        }
    }

    // 选择模式底部操作栏
    if (selecting) {
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("已选 ${picked.size} / ${records.size} 条", Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton({ picked = if (picked.size == records.size) emptySet() else records.map { it.id }.toSet() }) {
                    Text(if (picked.size == records.size) "取消全选" else "全选")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                { confirmDelete = true },
                Modifier.fillMaxWidth(),
                enabled = picked.isNotEmpty(),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = .7f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Icon(Lucide.Trash, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text("删除所选") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除导出记录") },
            text = { Text("将删除 ${picked.size} 条记录，此操作无法撤销。") },
            confirmButton = {
                TextButton({
                    scope.launch {
                        val ids = picked
                        withContext(Dispatchers.IO) { records.filter { it.id in ids }.forEach { repo.dao.delete(it) } }
                        confirmDelete = false
                        selecting = false
                        picked = emptySet()
                    }
                }) { Text("确认删除") }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("取消") } }
        )
    }

    detail?.let { record ->
        val credential = remember(record.id) { runCatching { repo.crypto.decrypt(record.encryptedCredential) }.getOrNull().orEmpty() }
        val snapshot = remember(record.id) {
            record.encryptedSnapshot?.let { enc -> runCatching { repo.crypto.decrypt(enc) }.getOrNull() }
        }
        val profiles = remember(record.id) { snapshot?.let { Aex.fromSnapshot(it) }.orEmpty() }
        val names = remember(record.id, record.configNames) { parseNames(record.configNames) }

        // 「重新导出」= 让用户选保存位置，与首次导出的「导出」按钮一致。
        var pendingBytes by remember(record.id) { mutableStateOf<ByteArray?>(null) }
        val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Aex.MIME_TYPE)) { uri ->
            val data = pendingBytes; pendingBytes = null
            if (uri == null || data == null) return@rememberLauncherForActivityResult
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(data) } ?: error("无法写入文件")
                    }
                    detail = null
                    toast("已导出 .akcp 文件")
                } catch (e: Exception) { toast(e.message ?: "导出失败") }
            }
        }

        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text("导出记录") },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    Text(record.fileName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(record.exportedAt)),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))

                    if (names.isNotEmpty()) {
                        Text("包含配置", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        names.forEach { Text("· $it", fontSize = 12.sp) }
                        Spacer(Modifier.height(10.dp))
                    }

                    if (snapshot == null) {
                        Text("早期记录：未保存快照，无法重新导出。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Text("导出凭证", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                credential.ifBlank { "—" },
                                Modifier.padding(14.dp),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LabelButton(
                        {
                            if (credential.isBlank()) toast("凭证不可用") else {
                                clipboard.setText(AnnotatedString(credential))
                                toast("凭证已复制")
                            }
                        },
                        Lucide.Copy, "复制凭证"
                    )

                    if (snapshot != null && profiles.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                {
                                    scope.launch {
                                        if (busy) return@launch
                                        busy = true
                                        try {
                                            val data = withContext(Dispatchers.Default) { Aex.export(profiles, appVersion, credential) }
                                            val prepared = AexShare.prepare(context, data, record.fileName, "Aivora 配置（${profiles.size} 项）")
                                                .getOrElse { error(it.message ?: "无法生成临时文件") }
                                            // 与导出弹窗一致：不用 canShare() 硬拦，交给系统 chooser；
                                            // 真的没有接收方时 startActivity 会抛 ActivityNotFoundException。
                                            try {
                                                context.startActivity(prepared.first)
                                            } catch (_: android.content.ActivityNotFoundException) {
                                                error("没有可接收分享的应用")
                                            }
                                            detail = null
                                        } catch (e: Exception) { toast(e.message ?: "分享失败") } finally { busy = false }
                                    }
                                },
                                Modifier.weight(1f), enabled = !busy
                            ) { Text("重新分享") }
                            OutlinedButton(
                                {
                                    scope.launch {
                                        if (busy) return@launch
                                        busy = true
                                        try {
                                            val data = withContext(Dispatchers.Default) { Aex.export(profiles, appVersion, credential) }
                                            pendingBytes = data.bytes
                                            saveLauncher.launch(record.fileName)
                                        } catch (e: Exception) { toast(e.message ?: "导出失败") } finally { busy = false }
                                    }
                                },
                                Modifier.weight(1f), enabled = !busy
                            ) { Text("重新导出") }
                        }
                        Text(
                            "重新导出使用当时的完整快照与当时的凭证，内容与最初那次完全一致。",
                            Modifier.padding(top = 8.dp),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = { TextButton({ detail = null }) { Text("关闭") } },
            dismissButton = null
        )
    }
}

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

/** 解析 configNames JSON 数组字符串。 */
private fun parseNames(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        Json.parseToJsonElement(raw).jsonArray.mapNotNull { it.jsonPrimitive.content.takeIf(String::isNotBlank) }
    }.getOrDefault(emptyList())
}