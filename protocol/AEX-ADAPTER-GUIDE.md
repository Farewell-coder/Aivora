# AEX 第三方适配指南

> 状态：1.0 已冻结，Aivora 0.0.5 起实现。Aivora 尚未发布可生成 `.akcp` 的版本，现在适配会无法获得测试文件；建议先阅读规范、预留接口，等协议冻结后再联调。

本指南面向想接收 AEX 配置文件的软件开发者。你不需要 Aivora 账号、SDK、服务器，也不需要阅读 Aivora 源码。

## 1. 适配能带来什么

用户在 Aivora 中整理好 AI 服务配置（Endpoint、API Key、模型列表），点击导出 → 选择你的软件 → 你的软件解密并导入。用户不必手动抄写密钥。

## 2. 最小实现路径

```
选择 .akcp 文件
   ↓ 校验扩展名 / 大小 / Magic
   ↓ 解析 JSON 头部，校验参数
   ↓ 请用户输入凭证（52 字符 Base32）
   ↓ PBKDF2-HMAC-SHA256 派生密钥
   ↓ AES-256-GCM 解密（头部为 AAD）
   ↓ 校验认证标签
   ↓ JSON Schema 校验 Payload
   ↓ 打码预览 → 用户确认 → 写入本地
```

## 3. 各步骤要点

### 3.1 文件入口

- 只接受 `.akcp` 后缀，MIME 建议同时接受 `application/vnd.aivora.akcp` 与 `application/octet-stream`（部分文件管理器不传 MIME）。
- 拒绝超过 16 MiB 的文件。
- 在 Android 上通过 `ACTION_VIEW` / `ACTION_SEND` 的 `content://` URI 读取，不要要求 `READ_EXTERNAL_STORAGE`。

### 3.2 头部解析

Magic 必须为 `41 45 58 31 0D 0A 1A 0A`。其后 4 字节大端整数是头部长度，范围 64–8192。头部是 UTF-8 JSON，只含协议参数，不含用户数据。

必须先校验 `kdf` / `cipher` / `tagBits` / `iterations` 是否为你支持的组合，再进入密钥派生——否则恶意文件可以用 2000000 次迭代拖垮你的应用。

### 3.3 凭证输入

- 提示用户粘贴 52 字符凭证，允许带连字符和空格。
- 规范化：去空白 → 去 `-` → 转大写。
- 规范化后不是 52 个 `A–Z2–7` 字符就立即报错，不要尝试纠错。

### 3.4 解密

关键点：**AAD 必须是文件中头部 JSON 的原始字节**，不能解析后再重新序列化。重新序列化会改变空白和键序，导致认证失败。

解密失败统一报"凭证错误或文件已损坏"，不要区分具体原因。

### 3.5 数据映射

按 [AEX-SCHEMA.json](AEX-SCHEMA.json) 校验，然后映射到你自己的模型。注意：

- `profiles` 是数组，即使只有一个配置也是数组。
- `models` 顺序要保留。
- 未知字段尽量保存到旁路存储；存不下就在导入前明确告诉用户"以下字段将丢失"。
- 不要覆盖已有配置，提供"跳过 / 另存为副本 / 替换"三选一。

## 4. 错误码

| 错误码 | 常见原因 | 建议给用户的提示 |
|---|---|---|
| `INVALID_EXTENSION` | 文件后缀不对 | 请选择 `.akcp` 文件 |
| `FILE_TOO_LARGE` | 文件 > 16 MiB | 文件过大，可能已损坏 |
| `INVALID_MAGIC` | 不是 AEX 文件 | 无法识别的文件格式 |
| `INVALID_HEADER` | 头部 JSON 损坏或字段越界 | 文件头部损坏 |
| `UNSUPPORTED_VERSION` | 需要更新版本 | 该文件由更新版本生成，请升级 |
| `UNSUPPORTED_PARAMETER` | 加密参数不支持 | 不支持的加密参数 |
| `AUTHENTICATION_FAILED` | 凭证错误或内容被改 | 凭证错误或文件已损坏 |
| `INVALID_PAYLOAD` | 解密成功但结构不符 | 文件内容格式异常 |
| `WRITE_FAILED` | 写入失败 | 保存失败，请检查存储空间 |

## 5. 兼容等级

| 等级 | 能力 |
|---|---|
| 1 | 读 `.akcp`、解密、核心字段导入、预览确认 |
| 2 | 全部字段、未知字段保留、冲突处理、完整错误码 |
| 3 | `extra` 扩展字段、未来加密套件、可选的导出能力 |

等级 1 即可宣称"支持 AEX 导入"，但必须在文档中注明等级。

## 6. Android 接收建议

- 在 `AndroidManifest.xml` 中声明 `intent-filter`，接收 `application/vnd.aivora.akcp` 与 `content://` scheme 的 `ACTION_VIEW`。
- 用 `ContentResolver.openInputStream(uri)` 读取，不要试图还原真实路径。
- 解密与密钥派生放到后台线程（`Dispatchers.IO`），UI 显示进度，避免 ANR。
- 导入成功后立即清除内存中的明文密钥引用，落盘时使用你自己的加密方案（Aivora 使用 Android Keystore 保护的数据密钥）。

## 7. 联调与验证

- 使用 [AEX-TEST-VECTORS](AEX-TEST-VECTORS.md) 中的合成数据自测。
- 确认错误凭证被拒绝、密文被改一个字节后被拒绝、头部被改后被拒绝。
- 不要用真实用户配置做测试文件。

## 8. 提交适配信息

完成适配后，按 [ECOSYSTEM.md](ECOSYSTEM.md) 的格式提 Issue 登记。未经核实的适配不会被列入"已支持"列表，只会标注为"自行声明"。
