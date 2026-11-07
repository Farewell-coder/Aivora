package app.aivora.vault

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import app.aivora.vault.data.ServiceConfig
import app.aivora.vault.data.ThirdParty
import app.aivora.vault.data.VaultRepository
import app.aivora.vault.protocol.Aex
import app.aivora.vault.protocol.Akcp
import app.aivora.vault.ui.kit.AivoraField
import app.aivora.vault.ui.kit.DangerHint
import app.aivora.vault.ui.kit.HintText
import app.aivora.vault.ui.kit.PrimaryButton
import app.aivora.vault.ui.kit.StatusBadge
import app.aivora.vault.ui.kit.StatusKind
import app.aivora.vault.ui.kit.SubtlePanel
import app.aivora.vault.ui.kit.TextAction
import app.aivora.vault.ui.theme.AivoraShape
import app.aivora.vault.ui.theme.AivoraSpace
import app.aivora.vault.ui.theme.AivoraThemeColors
import app.aivora.vault.ui.theme.AivoraType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 导入流程：识别 → 分流 → 预览 → 写入。
 *
 * 分流规则（按内容判断，不看扩展名）：
 *  - `Aex.detect() == AEX`     → AEX 1.0 容器，必须输入凭证
 *  - `Aex.detect() == LEGACY`  → 旧 AKCP 容器，必须输入凭证
 *  - 其余                      → 第三方明文格式，按各自规则解析，多数无需凭证
 */
object ImportFlow {

    /** 识别阶段结果。 */
    sealed class Detected {
        /** AEX 1.0 或旧 AKCP：需要凭证。[legacy] 为 true 时走 [Akcp.import]。 */
        data class Encrypted(val bytes: ByteArray, val fileName: String, val legacy: Boolean) : Detected()
        /** 第三方格式：已解析出候选，等待用户在预览页勾选。 */
        data class Plain(val candidates: List<ThirdParty.Candidate>) : Detected()
        /** 识别或解析失败。 */
        data class Failed(val reason: String, val showSupported: Boolean = true) : Detected()
    }

    /** 只读文件并识别格式，不写库。 */
    suspend fun detect(context: Context, uri: Uri, fileName: String): Detected = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext Detected.Failed("文件读取失败", showSupported = false)

        if (bytes.isEmpty()) return@withContext Detected.Failed("文件为空", showSupported = false)
        if (bytes.size > Aex.MAX_FILE_SIZE) return@withContext Detected.Failed("文件超过 16 MiB", showSupported = false)

        when (Aex.detect(bytes)) {
            Aex.Detected.AEX -> Detected.Encrypted(bytes, fileName, legacy = false)
            Aex.Detected.LEGACY -> Detected.Encrypted(bytes, fileName, legacy = true)
            Aex.Detected.UNKNOWN -> when (val parsed = ThirdParty.parse(bytes, fileName)) {
                is ThirdParty.Parsed.Ok -> Detected.Plain(parsed.candidates)
                is ThirdParty.Parsed.Failed -> Detected.Failed(parsed.reason)
            }
        }
    }

    /** 用凭证解密 AEX / 旧 AKCP 容器，返回可落库的配置。 */
    suspend fun openEncrypted(
        bytes: ByteArray,
        credential: String,
        legacy: Boolean,
        repo: VaultRepository
    ): List<ServiceConfig> = withContext(Dispatchers.IO) {
        if (legacy) {
            Akcp.import(bytes, credential).map { repo.fromAkcp(it) }
        } else {
            Aex.import(bytes, credential).map { profile ->
                ServiceConfig(
                    name = profile.name,
                    provider = profile.provider,
                    // AEX 允许空 Key（仅交换端点信息），空字符串直接落到空字段。
                    encryptedKey = if (profile.apiKey.isBlank()) "" else repo.crypto.encrypt(profile.apiKey),
                    endpoint = profile.baseUrl,
                    models = profile.models.joinToString(","),
                    defaultModel = profile.defaultModel,
                    notes = profile.description,
                    extensionsJson = profile.extra.toString(),
                    source = "AEX"
                )
            }
        }
    }

    /** 第三方候选转成可落库配置，[source] 写入角标字段。 */
    fun toConfig(candidate: ThirdParty.Candidate, name: String, repo: VaultRepository): ServiceConfig =
        ServiceConfig(
            name = name.ifBlank { candidate.name },
            provider = candidate.provider,
            encryptedKey = if (candidate.apiKey.isBlank()) "" else repo.crypto.encrypt(candidate.apiKey),
            endpoint = app.aivora.vault.data.Endpoint.normalize(candidate.endpoint).endpoint,
            models = candidate.models.joinToString(","),
            defaultModel = candidate.defaultModel,
            notes = candidate.notes,
            source = candidate.source
        )

    /** 写入前按 name + endpoint 去重：同名同端点视为同一条，覆盖而不是叠加。 */
    suspend fun commit(
        repo: VaultRepository,
        configs: List<ServiceConfig>,
        existing: List<ServiceConfig>
    ) = withContext(Dispatchers.IO) {
        var count = 0
        configs.forEach { item ->
            existing.firstOrNull { it.name == item.name && it.endpoint == item.endpoint }
                ?.let { repo.dao.delete(it) }
            repo.dao.save(item)
            count++
        }
        count
    }
}

/**
 * 导入预览弹层。
 *
 * 第三方格式解析完后必须先经过这里：用户能看到将要导入哪些条目、来源是什么，
 * 并可以取消勾选误识别的项或改名。AEX 容器不经过此页（凭证验证通过即导入）。
 *
 * 【为什么从 AlertDialog 换成 ModalBottomSheet + LazyColumn】候选可能几十条，
 * 旧的 `Column + verticalScroll + forEach` 会一次性组合全部条目；现在用惰性列表，
 * 并且每条只有「名称 / Endpoint / 来源 / 是否含密钥」四件事，一眼能扫完。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewDialog(
    candidates: List<ThirdParty.Candidate>,
    close: () -> Unit,
    confirm: (List<Pair<ThirdParty.Candidate, String>>) -> Unit
) {
    val c = AivoraThemeColors.colors
    // 默认全选；名称可编辑。状态提在弹层顶层，不放进 LazyColumn 的 item 里。
    var picked by remember { mutableStateOf(candidates.indices.toSet()) }
    val names = remember { mutableStateListOf<String>().apply { candidates.forEach { add(it.name) } } }
    val allPicked = picked.size == candidates.size

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = close,
        sheetState = sheetState,
        containerColor = c.surface,
        shape = AivoraShape.sheet,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .imePadding()
                .padding(horizontal = AivoraSpace.gutter),
        ) {
            Text("导入预览", style = AivoraType.section, color = c.text)
            Spacer(Modifier.height(AivoraSpace.xs))
            Text(
                "识别到 ${candidates.size} 条可导入配置，勾选后写入。",
                style = AivoraType.meta,
                color = c.textSecondary,
            )
            Spacer(Modifier.height(AivoraSpace.sm))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextAction(
                    text = if (allPicked) "取消全选" else "全选",
                    onClick = { picked = if (allPicked) emptySet() else candidates.indices.toSet() },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "已选 ${picked.size}/${candidates.size}",
                    style = AivoraType.monoSmall,
                    color = c.textSecondary,
                )
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = AivoraSpace.sm),
            ) {
                itemsIndexed(candidates, key = { index, candidate -> "$index-${candidate.source}-${candidate.name}" }) { index, candidate ->
                    CandidateCard(
                        candidate = candidate,
                        name = names.getOrNull(index) ?: candidate.name,
                        checked = index in picked,
                        onCheckedChange = { on ->
                            picked = if (on) picked + index else picked - index
                        },
                        onNameChange = { value -> if (index in names.indices) names[index] = value },
                    )
                }
            }

            Spacer(Modifier.height(AivoraSpace.sm))
            HintText("导入后可用「密钥检测」逐项验证；API Key 会先由本机主密钥加密再入库。")
            Spacer(Modifier.height(AivoraSpace.md))
            Row(
                Modifier.fillMaxWidth().padding(bottom = AivoraSpace.xxl),
                horizontalArrangement = Arrangement.spacedBy(AivoraSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PrimaryButton(
                    text = "导入 ${picked.size} 条",
                    onClick = {
                        confirm(
                            picked.sorted().mapNotNull { i ->
                                candidates.getOrNull(i)?.let { candidate -> candidate to (names.getOrNull(i) ?: candidate.name) }
                            }
                        )
                    },
                    modifier = Modifier.weight(1f),
                    icon = Lucide.Import,
                    enabled = picked.isNotEmpty(),
                )
                TextAction("取消", close)
            }
        }
    }
}

/** 一条候选配置：勾选 + 可改名 + Endpoint（等宽）+ 来源 chip + 含密钥状态。 */
@Composable
private fun CandidateCard(
    candidate: ThirdParty.Candidate,
    name: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onNameChange: (String) -> Unit,
) {
    val c = AivoraThemeColors.colors
    SubtlePanel(Modifier.padding(vertical = AivoraSpace.xs)) {
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Column(Modifier.weight(1f)) {
                AivoraField(
                    label = "配置名称",
                    value = name,
                    onValueChange = onNameChange,
                )
                Spacer(Modifier.height(AivoraSpace.xs))
                Text(
                    candidate.endpoint.ifBlank { "（无端点）" },
                    style = AivoraType.mono,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(AivoraSpace.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 来源 chip：用强调容器色，明确「这条是从哪个软件解析出来的」。
                    Box(
                        Modifier
                            .clip(AivoraShape.chip)
                            .background(c.accentContainer)
                            .padding(horizontal = AivoraSpace.sm, vertical = AivoraSpace.hair),
                    ) {
                        Text(candidate.source, style = AivoraType.label, color = c.onAccentContainer)
                    }
                    Spacer(Modifier.width(AivoraSpace.sm))
                    // 含密钥是风险信息（导入后立即接触明文 Key），用 Warning；无密钥用 Neutral。
                    StatusBadge(
                        kind = if (candidate.apiKey.isBlank()) StatusKind.Neutral else StatusKind.Warning,
                        text = if (candidate.apiKey.isBlank()) "无密钥" else "含密钥",
                    )
                    Spacer(Modifier.weight(1f))
                    if (candidate.models.isNotEmpty()) {
                        Text(
                            "${candidate.models.size} 个模型",
                            style = AivoraType.monoSmall,
                            color = c.textTertiary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * AEX / 旧 AKCP 的凭证输入弹层。
 *
 * 凭证是 52 字符的 Base32，输入框必须能整段粘贴并显示错误原因；
 * 失败时错误原因走字段的 [AivoraField] `supportingText`，跟着输入框走，而不是一条孤立红字。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialDialog(
    fileName: String,
    busy: Boolean,
    error: String,
    close: () -> Unit,
    confirm: (String) -> Unit
) {
    val c = AivoraThemeColors.colors
    var credential by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { if (!busy) close() },
        sheetState = sheetState,
        containerColor = c.surface,
        shape = AivoraShape.sheet,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = AivoraSpace.gutter)
                .padding(bottom = AivoraSpace.xxl),
        ) {
            Text("输入导入凭证", style = AivoraType.section, color = c.text)
            Spacer(Modifier.height(AivoraSpace.xs))
            Text(fileName, style = AivoraType.mono, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(AivoraSpace.md))
            AivoraField(
                label = "导出凭证",
                value = credential,
                onValueChange = { credential = it },
                placeholder = "粘贴 52 位凭证",
                visualTransformation = PasswordVisualTransformation(),
                isError = error.isNotEmpty(),
                supportingText = error.takeIf { it.isNotEmpty() },
            )
            Spacer(Modifier.height(AivoraSpace.sm))
            HintText("凭证由导出方单独提供，不在文件里；空格与连字符会自动忽略。")
            Spacer(Modifier.height(AivoraSpace.lg))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AivoraSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PrimaryButton(
                    text = "导入",
                    onClick = { confirm(credential) },
                    modifier = Modifier.weight(1f),
                    // 空凭证无从校验，直接禁用（与旧实现「空串不触发导入」等价）。
                    enabled = !busy && credential.isNotBlank(),
                    loading = busy,
                )
                TextAction("取消", close, enabled = !busy)
            }
        }
    }
}

/** 识别失败提示：列出支持的格式，避免用户只看到「无法识别」。 */
@Composable
fun ImportFailedDialog(reason: String, showSupported: Boolean, close: () -> Unit) {
    val c = AivoraThemeColors.colors
    AlertDialog(
        onDismissRequest = close,
        title = { Text("无法导入", style = AivoraType.section, color = c.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DangerHint(reason)
                if (showSupported) {
                    Spacer(Modifier.height(AivoraSpace.md))
                    Text("支持以下格式", style = AivoraType.label, color = c.textTertiary)
                    Spacer(Modifier.height(AivoraSpace.xs))
                    SubtlePanel {
                        Text("· Aivora 导出文件（.akcp，需要凭证）", style = AivoraType.meta, color = c.textSecondary)
                        ThirdParty.SUPPORTED.forEach { item ->
                            Spacer(Modifier.height(AivoraSpace.hair))
                            Text("· $item", style = AivoraType.meta, color = c.textSecondary)
                        }
                    }
                }
            }
        },
        confirmButton = { TextAction("知道了", close) },
        containerColor = c.surface,
        shape = AivoraShape.block,
    )
}
