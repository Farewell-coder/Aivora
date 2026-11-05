# AEX 深链导出（Deeplink）适配指南

> 状态：**1.1 新增，Aivora 0.0.8 起实现。**
> 适用对象：任何想「一键接收」Aivora 单个配置的软件。
> 阅读前提：不需要读过 [AEX-SPECIFICATION](AEX-SPECIFICATION.md)，也不需要实现任何加密。

---

## 0. 三十秒版本

Aivora 会向你的应用发出一条**明文**链接：

```
你的scheme://你的host?d=<base64url 无填充的 JSON>&v=1
```

你把它 `base64url` 解码，得到一个 JSON，里面是 `name` / `baseUrl` / `apiKey`。
校验字段 → 弹确认框 → 用户同意后入库 → 自己请求 `/models` 拿模型清单。

**没有加密，没有凭证，没有签名。** 安全边界完全依赖「用户在选择列表里明确点选了你」这一动作。

---

## 1. 与 `.akcp` 文件的区别

| 维度 | `.akcp` 文件 | 深链 |
|---|---|---|
| 载体 | 文件，走系统分享/网盘/聊天 | URI，直接投递给目标应用 |
| 加密 | AES-256-GCM + PBKDF2 600k | **无** |
| 凭证 | 52 字符，需用户另行传递 | **无** |
| 配置数量 | 1 个或多个 | **仅 1 个** |
| 导出记录 | Aivora 写一条 | **不写** |
| 用户操作 | 选文件 → 输凭证 → 确认 | 选软件 → 对方确认 |
| 适合场景 | 跨设备、发给朋友、留档 | 本机已装软件的快速搬运 |

两者不互相替代。深链解决的是「同一个手机上两个 App 之间传配置，不想折腾文件和凭证」。

---

## 2. 链接格式

```
<scheme>://<host>?d=<payload>&v=<envelope-version>
```

| 参数 | 必填 | 说明 |
|---|---|---|
| `scheme` | 是 | 由接收方定义，须在 Aivora 的注册表中登记 |
| `host` | 是 | 同上，用于区分同一 scheme 下的不同入口 |
| `d` | 是 | base64url（RFC 4648 §5）编码的 JSON，**不带 `=` 填充** |
| `v` | 是 | 信封版本，当前为 `1`。与 AEX `protocolVersion` 相互独立 |

当前已登记的唯一接收方：

```
dollhouse://aivora-import?d=...&v=1
```

> **你的软件想接入？** 提一个 Issue，标题 `[AEX Deeplink] <软件名>`，写明 scheme 与 host。登记后 Aivora 会把它加进选择列表——只有真正实现了解析的软件才登记，因为"列表里出现却打不开"比"不出现"更糟。

---

## 3. 载荷结构

`d` 解码后是 UTF-8 的 JSON 对象：

```json
{
  "kind": "aivora.provider",
  "ver": 1,
  "schema": "AEX",
  "protocolVersion": "1.1",
  "name": "我的中转站",
  "provider": "custom",
  "baseUrl": "https://api.example.com/v1",
  "apiKey": "sk-xxxxxxxx",
  "description": "备注",
  "exportedAt": "2026-10-29T12:00:00Z",
  "exporter": { "name": "Aivora", "version": "0.0.8" }
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `kind` | string | 是 | 固定 `"aivora.provider"`，**必须先校验**，防止 scheme 被别的意图复用 |
| `ver` | integer | 是 | 信封版本，当前 `1` |
| `schema` | string | 是 | 固定 `"AEX"` |
| `protocolVersion` | string | 是 | `"1.1"` |
| `name` | string | 是 | 配置名称，非空 |
| `provider` | string | 否 | 服务商标识，如 `"openai"`、`"deepseek"`、`"custom"` |
| `baseUrl` | string | 是 | Endpoint |
| `apiKey` | string | 是 | 明文密钥（可为空串，表示用户没填） |
| `description` | string | 否 | 备注 |
| `exportedAt` | string | 是 | RFC 3339 UTC 时间 |
| `exporter` | object | 是 | `{name, version}` |

**没有 `models`，也没有 `defaultModel`。** 与 AEX 1.1 保持一致——模型清单由你自己拉。

未知字段一律忽略，不要报错（便于未来加字段）。

---

## 4. 接收方实施步骤

### 4.1 声明 intent-filter

```xml
<activity
    android:name=".ui.DeeplinkImportActivity"
    android:exported="true"
    android:launchMode="singleTask">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="dollhouse" android:host="aivora-import" />
    </intent-filter>
</activity>
```

`exported="true"` 必须；`launchMode="singleTask"` 建议，避免用户重复点击时堆出多个实例。

### 4.2 解码与校验

```kotlin
data class AexDeepLinkPayload(
    val kind: String,
    val ver: Int,
    val schema: String,
    val protocolVersion: String,
    val name: String,
    val provider: String?,
    val baseUrl: String,
    val apiKey: String,
    val description: String?,
    val exportedAt: String?
)

fun parse(uri: Uri): Result<AexDeepLinkPayload> {
    if (uri.scheme != "dollhouse" || uri.host != "aivora-import")
        return Result.failure(IllegalArgumentException("不是 Aivora 深链"))

    val raw = uri.getQueryParameter("d")
        ?: return Result.failure(IllegalArgumentException("缺少 d 参数"))

    // base64url 无填充 → 需补齐到 4 的倍数，且把 -_ 换回 +/
    val json = runCatching {
        val padded = raw.replace('-', '+').replace('_', '/')
            .let { it + "=".repeat((4 - it.length % 4) % 4) }
        String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
    }.getOrElse { return Result.failure(it) }

    val obj = runCatching {
        Json.parseToJsonElement(json).jsonObject
    }.getOrElse { return Result.failure(it) }

    // 必填校验：缺一即拒
    val kind = obj["kind"]?.jsonPrimitive?.contentOrNull
    if (kind != "aivora.provider") return Result.failure(IllegalArgumentException("kind 不匹配"))
    val schema = obj["schema"]?.jsonPrimitive?.contentOrNull
    if (schema != "AEX") return Result.failure(IllegalArgumentException("schema 不匹配"))
    val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: return Result.failure(IllegalArgumentException("name 为空"))
    val baseUrl = obj["baseUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: return Result.failure(IllegalArgumentException("baseUrl 为空"))
    val apiKey = obj["apiKey"]?.jsonPrimitive?.contentOrNull.orEmpty()

    return Result.success(
        AexDeepLinkPayload(
            kind = kind, ver = obj["ver"]?.jsonPrimitive?.int ?: 1,
            schema = schema,
            protocolVersion = obj["protocolVersion"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            name = name, provider = obj["provider"]?.jsonPrimitive?.contentOrNull,
            baseUrl = baseUrl, apiKey = apiKey,
            description = obj["description"]?.jsonPrimitive?.contentOrNull,
            exportedAt = obj["exportedAt"]?.jsonPrimitive?.contentOrNull
        )
    )
}
```

### 4.3 版本协商

```kotlin
when {
    payload.ver > 1 ->
        // 信封比你知道的新，可能含未知字段 → 提示「链接来自更新版本，建议升级」
        reject(NEWER_ENVELOPE)

    payload.protocolVersion.substringBefore('.').toIntOrNull() != 1 ->
        // AEX 主版本不认识 → 直接拒绝
        reject(UNSUPPORTED_PROTOCOL)

    else -> accept()   // 1.x 均接受；1.1 是当前版本
}
```

**注意与文件通道的差异**：`.akcp` 采用精确匹配（1.1 实现拒绝 1.0 文件），但深链载荷没有加密、也没有"结构损坏"的风险，因此**按主版本放行、次版本容忍**更合适。字段缺失就是没填，不构成误导。

### 4.4 拉取模型列表

```kotlin
suspend fun fetchModels(baseUrl: String, apiKey: String): List<String> = try {
    withContext(Dispatchers.IO) {
        val resp = OkHttpClient().newCall(
            Request.Builder()
                .url("${baseUrl.trimEnd('/')}/models")
                .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey") }
                .build()
        ).execute()
        if (!resp.isSuccessful) emptyList()
        else parseModelIds(resp.body?.string().orEmpty())   // 兼容 data[].id
    }
} catch (e: Exception) {
    emptyList()
}
```

处理原则：

- 拉取成功 → 展示列表让用户选一个默认模型
- 拉取失败（网络、Key 无权限、接口不规范）→ **仍然完成导入**，模型留空，提示「模型列表获取失败，可稍后手动添加」
- **绝不因为这一步失败就丢弃配置**

### 4.5 安全与交互要求

| 事项 | 要求 |
|---|---|
| 日志 | **禁止**把完整 URI、`d` 参数、`apiKey` 写进日志或崩溃上报 |
| 展示 | 确认页的 Key 只显示尾 4 位（`sk-****abcd`） |
| 确认 | 必须由用户点「导入」后才入库，不得静默保存 |
| 来源标识 | 确认页标明「来自外部链接」 |
| 存储 | Key 落盘须用你自己的加密方案（Aivora 用 Android Keystore 保护的数据密钥） |
| 去重 | 链接可重复使用。如需防重，自己比对时间戳，协议不做规定 |
| 内存 | 导入完成后及时清掉明文引用 |

---

## 5. 错误处理

深链没有错误码表（不像 `.akcp` 有 9 个）。所有异常都归为「链接无效」，给用户看的提示要具体、不要说术语：

| 情况 | 建议提示 |
|---|---|
| scheme/host 不匹配 | 不是 Aivora 的导入链接 |
| 缺 `d` | 链接不完整，请重新从 Aivora 导出 |
| base64 解不开 | 链接已损坏，请重新导出 |
| JSON 解析失败 | 链接已损坏，请重新导出 |
| `kind` / `schema` 不匹配 | 不是 Aivora 的导入链接 |
| `name` 或 `baseUrl` 为空 | 链接内容不完整，请重新导出 |
| `ver` 高于支持 | 链接来自更新版本，请升级软件后再试 |

**不要**把技术细节（如 `json parse error at offset 37`）直接抛给用户。

---

## 6. 联调方法

不用装 Aivora，用 adb 直接模拟：

```bash
JSON='{"kind":"aivora.provider","ver":1,"schema":"AEX","protocolVersion":"1.1","name":"测试配置","provider":"custom","baseUrl":"https://api.test.com/v1","apiKey":"sk-test123","exportedAt":"2026-10-29T12:00:00Z","exporter":{"name":"Aivora","version":"0.0.8"}}'
D=$(printf '%s' "$JSON" | base64 -w0 | tr '+/' '-_' | tr -d '=')

adb shell am start -a android.intent.action.VIEW \
  -d "dollhouse://aivora-import?d=$D&v=1"
```

### 必测用例

| 用例 | 期望 |
|---|---|
| 正常链接 | 弹确认页，显示名称 / Endpoint / Key 尾 4 位 |
| 缺 `d` | 提示链接不完整，不崩溃 |
| `d` 是乱码 | 提示链接已损坏，不崩溃 |
| `d` 解出的不是 JSON | 提示链接已损坏，不崩溃 |
| `kind` 改为 `other` | 提示不是 Aivora 链接，不崩溃 |
| `ver=2` | 提示请升级，不崩溃 |
| `baseUrl` 缺失 | 提示内容不完整，不崩溃 |
| Key 无效 | 导入成功，模型为空 + 提示手动添加 |
| 连续点同一链接两次 | 每次都能导入（或按你的去重策略） |
| 超长 `d`（>8 KiB） | 不崩溃，可拒绝 |

---

## 7. 长度与限制

- Aivora 单条配置生成的链接通常在 **300–800 字节**之间。
- 协议不设硬上限，但接收方 SHOULD 拒绝超过 **64 KiB** 的 URI（Android 的 `Intent` 传递本身有 Binder 限制，超长 URL 会被截断）。
- 一个链接只承载 **1 个配置**。需要传多个请用 `.akcp` 文件通道。

---

## 8. 安全说明（必读）

深链是**明文**通道，这是有意为之，不是遗漏：

1. **不加加密的理由**：加上凭证就等于变成 `.akcp`，用户还要多输一次 52 个字符，失去了深链"一键"的意义。
2. **风险边界**：任何能读到这条 URI 的实体都能拿到 API Key。因此：
   - Aivora **不**把链接放进剪贴板、**不**写文件、**不**记日志、**不**写导出记录。
   - 链接只通过 `startActivity` 直接投递给用户明确选中的那个应用。
3. **接收方的义务**：不落日志、不转存明文、展示时打码、入库加密。
4. **不做来源校验**：任何人都能构造链接（和用户手动粘贴一段文本没有区别）。所以**必须**有用户确认步骤，不能自动入库。

不要在文档或宣传里把深链描述成"安全传输"——它是一次**方便**的传输，安全性由用户的选择行为和双方的日志纪律来保证。

---

## 9. 与文件通道的取舍建议

| 你的场景 | 建议 |
|---|---|
| 只做深链 | 够用，实现成本最低 |
| 只做文件 | 也能用，但用户要输 52 字符凭证 |
| 两个都做 | 最佳：本机快传用深链，跨设备/留档用文件 |

两个通道共用同一套字段语义（`name` / `baseUrl` / `apiKey` / `provider` / `description`），解析后可以映射到同一个内部模型，不需要写两套入库逻辑。
