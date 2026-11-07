# AEX — Aivora Exchange Protocol

**AEX**（Aivora Exchange Protocol）是 Aivora 定义的跨软件 AI API 配置交换协议。协议与参考实现解耦：任何软件都可以只依据本目录的公开文档完成适配，不需要阅读 Aivora 源码、不需要接入 Aivora 服务器、不需要 Aivora 账号。

- 协议版本：**1.1**
- 状态：**已实现（Aivora 0.0.8 起），1.1 规范已定稿**
- 文件扩展名：`.akcp`（唯一标准交换格式）
- MIME Type：`application/vnd.aivora.akcp`
- 加密：AES-256-GCM + PBKDF2-HMAC-SHA256

> **实现状态说明**
> 自 Aivora 0.0.8 起，AEX 1.1 已在应用中实现：配置卡片与批量选择生成 `.akcp` 文件，单条配置另有「深链」按钮直接投递给已适配的软件。
>
> 关于「接收」：Aivora 的导入入口按文件头 Magic 识别格式（不看扩展名），识别为 AEX 后要求输入凭证、解密、打码预览后导入。
> 但 Aivora **没有**在 `AndroidManifest.xml` 里注册 `.akcp` 的 `ACTION_VIEW` / `ACTION_SEND` 接收方，
> 也就是说别的软件无法通过系统分享面板把 `.akcp`「分享到 Aivora」——接收只能由用户在 Aivora 内主动点「导入」并选文件。
> 这是有意的取舍：AEX 的设计方向是 Aivora → 第三方软件的单向分享。
>
> **1.1 变更摘要**：`profiles[]` 不再包含 `models` / `defaultModel`，接收方自行请求 `GET {baseUrl}/models`；版本号升为 `1.1` 且精确匹配，1.0 文件无法在 1.1 实现上导入。
>
> **历史格式**：0.0.5 之前曾有一套旧容器 AKCP（`.aivora`，Argon2id）。Aivora 至今保留它的**只读导入**兼容，
> 但不再生成该格式，也未对外发布其规范。它不属于 AEX，`.akcp` 依然是唯一标准交换格式。

## 想适配？从这里开始

三步就能完成接入，不需要读完所有文档：

1. **读适配指南** — [AEX-ADAPTER-GUIDE](AEX-ADAPTER-GUIDE.md)（文件通道）或 [AEX-DEEPLINK](AEX-DEEPLINK.md)（深链通道），任选其一；两个都做体验最好。
2. **用测试向量自测** — 拿 [test-vectors/](test-vectors/) 里的文件或 [AEX-DEEPLINK §6](AEX-DEEPLINK.md#6-联调方法) 的 adb 命令跑一遍，确认错误凭证、篡改内容都被正确拒绝。
3. **登记到生态页** — 按 [ECOSYSTEM.md](ECOSYSTEM.md) 的模板提 Issue，写清兼容等级与验证记录。

## 文档索引

| 文档 | 内容 |
|---|---|
| [AEX-SPECIFICATION](AEX-SPECIFICATION.md) | 完整协议规范：容器结构、字段、版本规则、错误码 |
| [AEX-SECURITY](AEX-SECURITY.md) | 加密参数、密钥派生、认证标签验证、安全边界 |
| [AEX-SCHEMA.json](AEX-SCHEMA.json) | 配置数据的 JSON Schema 定义 |
| [AEX-CHANGELOG](AEX-CHANGELOG.md) | 协议版本更新记录 |
| [AEX-ADAPTER-GUIDE](AEX-ADAPTER-GUIDE.md) | 第三方软件接入指南（文件通道，含 Android 接收建议） |
| [AEX-DEEPLINK](AEX-DEEPLINK.md) | 深链通道接入指南（明文链接，无凭证） |
| [AEX-TEST-VECTORS](AEX-TEST-VECTORS.md) | 测试方法、验收清单与负向用例 |
| [test-vectors/](test-vectors/) | 官方测试向量（Python 独立实现生成，附 SHA-256 与期望明文） |
| [示例](examples/) | 单配置、多配置、加密包三个示例 |
| [生态适配](ECOSYSTEM.md) | 已适配软件列表与提交方式 |


## 一句话概括

Aivora 生成一个加密的 `.akcp` 文件并通过 Android 系统分享面板交给目标软件，或者把单个配置组装成明文深链直接投递给已适配的应用；前者需要用户另行传递凭证，后者由用户在软件列表中显式选择接收方。整个过程离线完成，无服务器参与。

## 参与协议讨论

对规范有异议、发现歧义或需要补充字段，请开 Issue 并标注 `protocol` 标签。1.0 已定稿，向后兼容由 [版本规则](AEX-SPECIFICATION.md) 约束。定稿的是协议，不是软件——Aivora 应用版本会继续更新，不影响已导出的文件。