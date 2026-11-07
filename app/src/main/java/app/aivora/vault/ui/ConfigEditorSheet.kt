package app.aivora.vault.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import app.aivora.vault.Lucide
import app.aivora.vault.data.Endpoint
import app.aivora.vault.data.ServiceConfig
import app.aivora.vault.data.VaultRepository
import app.aivora.vault.data.providerTemplates
import app.aivora.vault.ui.kit.AivoraField
import app.aivora.vault.ui.kit.HintText
import app.aivora.vault.ui.kit.IconAction
import app.aivora.vault.ui.kit.PrimaryButton
import app.aivora.vault.ui.kit.TextAction
import app.aivora.vault.ui.theme.AivoraShape
import app.aivora.vault.ui.theme.AivoraSpace
import app.aivora.vault.ui.theme.AivoraThemeColors
import app.aivora.vault.ui.theme.AivoraType
import kotlinx.coroutines.launch

/**
 * 服务配置编辑器（底部弹层）。
 *
 * 【为什么是弹层而不是整页】新建 / 编辑一条配置只有 5 个字段，整页会让人以为「离开配置页」；
 * 底部弹层保留列表上下文，取消成本低。
 *
 * 【字段一律 AivoraField】标签常驻、聚焦描边、错误态都走同一套；端点纠正提示并入
 * Endpoint 字段的 `supportingText`，跟着字段走，不再是一条孤立灰字。
 *
 * 【业务逻辑原样保留】`Endpoint.normalize` 的调用位置与参数、`ServiceConfig` 的构造顺序、
 * `repo.crypto.encrypt/decrypt`、保存后的 toast 与关闭顺序都没有改动 —— 这次只换外观。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditor(
    old: ServiceConfig?,
    repo: VaultRepository,
    endpointFix: Boolean,
    close: () -> Unit,
    toast: (String) -> Unit,
) {
    val c = AivoraThemeColors.colors
    var name by remember { mutableStateOf(old?.name.orEmpty()) }
    var provider by remember { mutableStateOf(old?.provider.orEmpty()) }
    var providerMenu by remember { mutableStateOf(false) }
    var endpoint by remember { mutableStateOf(old?.endpoint.orEmpty()) }
    var key by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf(old?.notes.orEmpty()) }
    var visible by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(old) {
        if (old?.encryptedKey?.isNotEmpty() == true) {
            key = runCatching { repo.crypto.decrypt(old.encryptedKey) }.getOrElse { "" }
        }
    }

    // 端点纠正结果：只在输入框下方显示说明，不改动已保存配置，保存时才写回。
    val normalized = remember(endpoint, endpointFix) {
        if (endpoint.isBlank()) null else Endpoint.normalize(endpoint, endpointFix)
    }

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
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = AivoraSpace.gutter)
                .padding(bottom = AivoraSpace.xxl),
        ) {
            Text(
                if (old == null) "添加服务配置" else "编辑服务配置",
                style = AivoraType.section,
                color = c.text,
            )
            Spacer(Modifier.height(AivoraSpace.md))

            AivoraField(
                label = "配置名称",
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.padding(vertical = AivoraSpace.xs),
            )

            // 服务商模板：只读字段 + 下拉，选中即带出该模板的 Endpoint。
            ExposedDropdownMenuBox(providerMenu, { providerMenu = it }) {
                AivoraField(
                    label = "服务商模板",
                    value = provider,
                    onValueChange = {},
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .padding(vertical = AivoraSpace.xs),
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerMenu) },
                )
                ExposedDropdownMenu(providerMenu, { providerMenu = false }) {
                    providerTemplates.forEach { template ->
                        DropdownMenuItem(
                            text = { Text(template.name, style = AivoraType.body, color = c.text) },
                            onClick = {
                                provider =
                                    if (template.name == "空白模板" || template.name == "自定义") ""
                                    else template.name
                                endpoint = template.endpoint
                                providerMenu = false
                            },
                        )
                    }
                }
            }

            AivoraField(
                label = "API Endpoint（选填）",
                value = endpoint,
                onValueChange = { endpoint = it },
                modifier = Modifier.padding(vertical = AivoraSpace.xs),
                placeholder = "https://api.example.com/v1",
                // 纠正提示跟着字段走：用户正看着这个输入框时才需要知道它被改成了什么。
                supportingText = normalized?.note?.takeIf { it.isNotEmpty() },
            )

            AivoraField(
                label = "API Key（选填）",
                value = key,
                onValueChange = { key = it },
                modifier = Modifier.padding(vertical = AivoraSpace.xs),
                placeholder = "sk-…",
                visualTransformation = if (visible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    Row {
                        IconAction(
                            if (visible) Lucide.EyeOff else Lucide.Eye,
                            "显示或隐藏密钥",
                            { visible = !visible },
                            tint = c.textTertiary,
                        )
                        IconAction(
                            Lucide.Copy,
                            "复制密钥",
                            {
                                clipboard.setText(AnnotatedString(key))
                                toast("API Key 已复制")
                            },
                            tint = c.textTertiary,
                        )
                    }
                },
            )

            AivoraField(
                label = "备注（选填）",
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.padding(vertical = AivoraSpace.xs),
            )

            Spacer(Modifier.height(AivoraSpace.sm))
            HintText("Endpoint 与 API Key 只保存在本机，导出时由凭证加密。")

            Spacer(Modifier.height(AivoraSpace.lg))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AivoraSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PrimaryButton(
                    "保存",
                    onClick = {
                        if (name.isBlank()) {
                            toast("请填写配置名称")
                        } else {
                            scope.launch {
                                val finalEndpoint =
                                    if (endpoint.isBlank()) "" else Endpoint.normalize(endpoint, endpointFix).endpoint
                                repo.dao.save(
                                    ServiceConfig(
                                        old?.id ?: 0,
                                        name.trim(),
                                        provider,
                                        if (key.isBlank()) "" else repo.crypto.encrypt(key.trim()),
                                        finalEndpoint,
                                        "",
                                        "",
                                        notes,
                                        old?.enabled ?: true,
                                        old?.isDefault ?: false,
                                        old?.createdAt ?: System.currentTimeMillis(),
                                        0,
                                        "配置已更新，请重新检测",
                                        null,
                                        old?.extensionsJson ?: "{}",
                                        "[]",
                                        old?.source,
                                    ),
                                )
                                close()
                                toast("配置已保存")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                TextAction("取消", close)
            }
            Spacer(Modifier.height(AivoraSpace.sm))
        }
    }
}