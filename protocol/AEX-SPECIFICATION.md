# AEX 1.0 Specification

> **状态：1.0 已冻结，Aivora 0.0.5 起实现。** 后续不兼容变更需升主版本。
> 关键词 MUST / SHOULD / MAY 按 RFC 2119 解释。

## 1. 概述

AEX（Aivora Exchange Protocol）是跨软件 AI API 配置交换协议，规范一个配置集合如何在两个互不信任的应用之间以加密文件形式传递。协议只定义**文件格式**与**加解密流程**，不定义任何网络通信；整个交换过程离线完成。

- 正式名称：Aivora Exchange Protocol
- 简称：AEX
- 版本：1.0
- 文件扩展名：`.akcp`
- MIME Type：`application/vnd.aivora.akcp`
- 字符编码：UTF-8

JSON 是 `.akcp` 文件内部的序列化方式，**不是第二种导出格式**。用户始终只面对 `.akcp` 一种文件。

## 2. 文件结构

`.akcp` 为单一容器，由三段顺序拼接，无填充、无尾部多余字节：

| 偏移 | 长度 | 含义 |
|---|---|---|
| 0 | 8 | 固定格式标识 `41 45 58 31 0D 0A 1A 0A`（ASCII `AEX1` + CRLF + SUB + LF） |
| 8 | 4 | 头部长度 `H`，无符号大端 32 位 |
| 12 | H | 规范化 JSON 头部（UTF-8，RFC 8785 序列化） |
| 12+H | N | AES-256-GCM 密文，末尾 16 字节为认证标签 |

约束：

- 完整文件 MUST NOT 超过 16 MiB。
- 头部长度 `H` MUST 在 64 至 8192 字节之间。
- 读取方 MUST 在解析头部前校验 Magic、`H` 范围与总长度，避免整数溢出。
- 密文长度 MUST 由 `totalSize − 12 − H` 推导并与头部 `ciphertextLength` 一致，不一致视为损坏。

### 2.1 头部字段

头部为 JSON 对象，**不含任何敏感信息**（不得出现配置名称、Endpoint、API Key、模型名）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `format` | string | 是 | 固定 `"aivora.exchange/1"` |
| `protocolVersion` | string | 是 | `"1.0"` |
| `kdf` | string | 是 | 固定 `"PBKDF2-HMAC-SHA256"` |
| `iterations` | integer | 是 | 600000，合法范围 100000–2000000 |
| `salt` | string | 是 | 16 字节随机值的 Base64（标准字母表，带 `=` 填充） |
| `cipher` | string | 是 | 固定 `"AES-256-GCM"` |
| `nonce` | string | 是 | 12 字节随机值的 Base64 |
| `tagBits` | integer | 是 | 固定 `128` |
| `ciphertextLength` | integer | 是 | 密文（含标签）字节数 |
| `payloadEncoding` | string | 是 | 固定 `"json"` |

未知头部字段 MUST 被忽略而非报错，但 MUST NOT 影响认证结果——头部整体作为 AAD 参与认证，任何篡改都会导致解密失败。

> 头部编码采用 JSON 而非紧凑二进制：第三方用通用 JSON 解析器即可读取，也便于未来扩展。1.0 已冻结，该选择不变。

## 3. 加密流程

导出方 MUST 按以下顺序执行：

1. 构造 Payload 对象（见第 4 节），按 RFC 8785 规范序列化为 UTF-8 字节。
2. 生成 16 字节 CSPRNG Salt、12 字节 CSPRNG Nonce，**每次导出必须全新生成**，禁止复用同一密钥下的 Nonce。
3. 由用户凭证经 PBKDF2-HMAC-SHA256 派生 32 字节密钥：`PBKDF2(password = 规范化凭证, salt = Salt, iterations = 600000, dkLen = 32)`。
4. 以 `Magic ‖ 头部长度 ‖ 头部 JSON 原始字节` 作为 AAD，AES-256-GCM 加密 Payload，输出密文与 16 字节标签。
5. 拼接为完整 `.akcp` 文件。

导入方 MUST 按以下顺序执行，且**任何一步失败都不得输出部分明文**：

1. 校验扩展名、文件大小上限、Magic、头部长度。
2. 解析头部，校验 `format`、`protocolVersion`、`kdf`、`cipher`、`tagBits`、`iterations` 是否在支持范围内；不支持的组合 MUST 直接拒绝，不得"尽力尝试"。
3. 校验 `salt`、`nonce` 的 Base64 解码长度是否分别为 16、12 字节。
4. 重新计算 AAD 为**文件中头部原始字节**（不得重新序列化后再计算）。
5. 派生密钥、GCM 解密；认证标签校验失败 MUST 统一报 `AUTHENTICATION_FAILED`，不得区分"头部被改"与"密钥错误"等细节。
6. 按 [AEX-SCHEMA.json](AEX-SCHEMA.json) 校验 Payload，再映射到自身数据模型。

详细加密参数见 [AEX-SECURITY](AEX-SECURITY.md)。

## 4. Payload 结构

Root 对象：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `format` | string | 是 | `"aivora.exchange/1"`，与头部一致，二次校验 |
| `protocolVersion` | string | 是 | `"1.0"` |
| `exportedAt` | string | 是 | RFC 3339 UTC 时间，如 `2026-11-01T09:00:00Z` |
| `exporter` | object | 是 | 导出方信息，见下 |
| `profiles` | array | 是 | 配置数组，长度 ≥ 1 |

`exporter` 对象：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | string | 是 | 如 `"Aivora"` |
| `version` | string | 是 | 导出软件版本，如 `"0.0.5"` |
| `platform` | string | 否 | 如 `"android"` |

`profiles[]` 元素：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | string | 是 | 导出方本地标识，接收方可忽略或用于去重 |
| `name` | string | 是 | 配置名称，非空 |
| `provider` | string | 否 | 服务商标识，如 `"openai"`、`"deepseek"`、`"custom"` |
| `baseUrl` | string | 否 | Endpoint，缺失时语义为"未设置" |
| `apiKey` | string | 否 | 明文密钥，缺失时语义为"未设置" |
| `models` | array\<string\> | 否 | 模型列表，**必须保持原有顺序** |
| `defaultModel` | string | 否 | 默认模型名 |
| `description` | string | 否 | 备注 |
| `extra` | object | 否 | 扩展数据，键名必须带命名空间 |

### 4.1 字段语义规则

1. **空值与缺失值不同**：字段不存在表示"未设置"；字段存在但为空字符串表示"用户明确置空"。接收方 MUST 保留该区别，或明确说明无法保留。
2. **未知字段**必须原样保留（放入 `extra` 或内部旁路存储）；无法保留时 MUST 在导入前告知用户，MUST NOT 静默丢弃。
3. **禁止猜测映射**：不得把未知字段自动对应到自身相近字段（例如把 `endpoint` 自动当作 `baseUrl` 之外的字段使用）。
4. `extra` 的键 MUST 使用反域名命名空间，如 `"extra": {"com.example.tool.retryPolicy": 3}`，禁止裸键。

> `apiKey` 允许为空串（表示用户明确清空），缺失表示未设置；`extra` 允许嵌套对象，深度不做强制限制。

## 4.2 凭证与文件分离

`.akcp` 文件内**不包含**任何可用于解密的凭证：

- 文件头只有协议参数（KDF 名、迭代次数、salt、nonce 等），不含凭证。
- 密文载荷只有配置数据。
- 文件名不含凭证。

凭证由导出方在导出界面单独展示，必须由用户在应用之外**另行传递**。协议不定义凭证分发通道，也不提供找回机制。接收方在没有凭证的情况下 MUST 拒绝解密，不得尝试穷举或猜测。

这条规则是 AEX 的核心安全属性：文件可以自由传播，但只有拿到凭证的人才能读。

## 5. 批量配置

单个 `.akcp` 文件可包含 1 个、多个或全部配置，统一使用 `profiles` 数组，**不得为批量导出另立格式或另立加密流程**。

- 单配置：`profiles` 长度 1。
- 多配置：用户自选若干配置。
- 完整集合：导出全部配置；是否包含被停用的配置由导出方在 UI 上明确说明。

## 6. 版本规则

- 主版本（`1`）变化表示不兼容变更。读取方遇到更高主版本 MUST 拒绝并提示"文件由更新版本生成"，**不得降级猜测或尝试兼容解析**。
- 次版本（`1.0` → `1.1`）变化表示向后兼容的增量扩展，读取方可忽略未知字段继续处理。
- 错误码与兼容等级见 [AEX-ADAPTER-GUIDE](AEX-ADAPTER-GUIDE.md#错误码)。

## 7. 格式识别

**禁止仅凭扩展名判断协议版本。** 识别顺序建议：

1. 读取前 12 字节，匹配 Magic `41 45 58 31 0D 0A 1A 0A` → 候选 AEX，进入头部解析。
2. Magic 不匹配 → 拒绝，提示“不是有效的 `.akcp` 文件”。
3. 匹配但 `protocolVersion` 主版本更高 → 不支持的未来版本，明确提示，不得降级猜测。

## 8. 不做的事

- 不定义网络传输、不上传、不依赖 Aivora 服务器。
- 不定义签名证书体系；完整性只由 AES-GCM 认证标签保证。
- 不定义"接收方成功导入"的回执机制；分享完成不等于导入完成。
- 不定义双向同步。当前实现方向是 Aivora → 第三方软件的单向分享。
