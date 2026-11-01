# AEX — Aivora Exchange Protocol

**AEX**（Aivora Exchange Protocol）是 Aivora 定义的跨软件 AI API 配置交换协议。协议与参考实现解耦：任何软件都可以只依据本目录的公开文档完成适配，不需要阅读 Aivora 源码、不需要接入 Aivora 服务器、不需要 Aivora 账号。

- 协议版本：**1.0**
- 状态：**已实现（Aivora 0.0.5 起），1.0 规范已定稿**
- 文件扩展名：`.akcp`（唯一标准交换格式）
- MIME Type：`application/vnd.aivora.akcp`
- 加密：AES-256-GCM + PBKDF2-HMAC-SHA256

> **实现状态说明**
> 自 Aivora 0.0.5 起，AEX 1.0 已在应用中实现：配置卡片与批量选择均生成 `.akcp` 文件，并通过 Android 系统分享面板交给接收方。Aivora 自身不提供 `.akcp` 接收入口。

## 文档索引

| 文档 | 内容 |
|---|---|
| [AEX-SPECIFICATION](AEX-SPECIFICATION.md) | 完整协议规范：容器结构、字段、版本规则、错误码 |
| [AEX-SECURITY](AEX-SECURITY.md) | 加密参数、密钥派生、认证标签验证、安全边界 |
| [AEX-SCHEMA.json](AEX-SCHEMA.json) | 配置数据的 JSON Schema 定义 |
| [AEX-CHANGELOG](AEX-CHANGELOG.md) | 协议版本更新记录 |
| [AEX-ADAPTER-GUIDE](AEX-ADAPTER-GUIDE.md) | 第三方软件接入指南（含 Android 接收建议） |
| [AEX-TEST-VECTORS](AEX-TEST-VECTORS.md) | 测试方法、验收清单与负向用例 |
| [test-vectors/](test-vectors/) | 官方测试向量（Python 独立实现生成，附 SHA-256 与期望明文） |
| [示例](examples/) | 单配置、多配置、加密包三个示例 |
| [生态适配](ECOSYSTEM.md) | 已适配软件列表与提交方式 |


## 一句话概括

Aivora 生成一个加密的 `.akcp` 文件并通过 Android 系统分享面板交给目标软件；目标软件用用户提供的独立凭证解密、校验并导入配置。整个过程离线完成，无服务器参与。

## 参与协议讨论

对规范有异议、发现歧义或需要补充字段，请开 Issue 并标注 `protocol` 标签。1.0 已定稿，向后兼容由 [版本规则](AEX-SPECIFICATION.md) 约束。定稿的是协议，不是软件——Aivora 应用版本会继续更新，不影响已导出的文件。