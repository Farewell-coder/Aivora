# AEX — Aivora Exchange Protocol

**AEX**（Aivora Exchange Protocol）是 Aivora 定义的跨软件 AI API 配置交换协议。协议与参考实现解耦：任何软件都可以只依据本目录的公开文档完成适配，不需要阅读 Aivora 源码、不需要接入 Aivora 服务器、不需要 Aivora 账号。

- 协议版本：**1.0**
- 状态：**Draft（规划中，尚未冻结）**
- 文件扩展名：`.akcp`（唯一标准交换格式）
- MIME Type：`application/vnd.aivora.akcp`
- 加密：AES-256-GCM + PBKDF2-HMAC-SHA256

> **实现状态说明**
> Aivora 当前发布版本（0.0.5）的配置交换仍使用 AKCP 1.0 与 `.aivora` 文件，AEX 的文件生成、系统分享面板与第三方接收链路**尚未实现**。本目录内容为协议设计草案，供第三方提前评估；在协议冻结并在 Aivora 发布版本中可用之前，请勿依据本文档声称已“支持 AEX”。

## 文档索引

| 文档 | 内容 |
|---|---|
| [AEX-SPECIFICATION](AEX-SPECIFICATION.md) | 完整协议规范：容器结构、字段、版本规则、错误码 |
| [AEX-SECURITY](AEX-SECURITY.md) | 加密参数、密钥派生、认证标签验证、安全边界 |
| [AEX-SCHEMA.json](AEX-SCHEMA.json) | 配置数据的 JSON Schema 定义 |
| [AEX-CHANGELOG](AEX-CHANGELOG.md) | 协议版本更新记录 |
| [AEX-ADAPTER-GUIDE](AEX-ADAPTER-GUIDE.md) | 第三方软件接入指南（含 Android 接收建议） |
| [AEX-TEST-VECTORS](AEX-TEST-VECTORS.md) | 协议测试数据与跨语言互操作验证方法 |
| [示例](examples/) | 单配置、多配置、加密包三个示例 |
| [兼容性](compatibility/legacy-format.md) | 旧版 AKCP 1.0 / `.aivora` 兼容规则 |
| [生态适配](ECOSYSTEM.md) | 已适配软件列表与提交方式 |

旧版协议文档保留在 [AKCP 目录](../AKCP/)，仅作历史兼容格式参考。

## 一句话概括

Aivora 生成一个加密的 `.akcp` 文件并通过 Android 系统分享面板交给目标软件；目标软件用用户提供的独立凭证解密、校验并导入配置。整个过程离线完成，无服务器参与。

## 参与协议讨论

对草案有异议、发现歧义或需要补充字段，请开 Issue 并标注 `protocol` 标签。协议在冻结前不保证向后兼容。