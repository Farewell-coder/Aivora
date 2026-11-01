# AEX Changelog

协议版本独立于 Aivora 应用版本演进。此处只记录**协议规范**的变更，应用更新日志见 [README 更新日志](../README.md#更新日志)。

## 1.0 — Draft（未冻结）

初始草案，尚未在 Aivora 发布版本中实现。

- 确立 `.akcp` 作为唯一标准交换文件格式，MIME `application/vnd.aivora.akcp`。
- 容器结构：8 字节 Magic + 4 字节头部长度 + JSON 头部 + AES-256-GCM 密文。
- 加密方案：AES-256-GCM + PBKDF2-HMAC-SHA256，600000 次迭代，16 字节 Salt，12 字节 Nonce，128 位认证标签。
- 头部整体作为 AAD，防止协议版本与加密参数被篡改。
- Payload 采用 `profiles` 数组，统一承载单个、多个与全量配置。
- 定义 9 个错误码与四级兼容识别规则。
- 明确旧版 AKCP 1.0 / `.aivora` 作为历史格式保留，不与 AEX 共享凭证。

### 冻结前待确认

- 头部序列化：RFC 8785 JSON（当前草案）vs 紧凑二进制。
- PBKDF2 迭代次数 600000 是否按真机实测调整。
- `apiKey` 是否允许空串。
- `extra` 是否允许嵌套对象及深度上限。
- 是否需要可选的 `signature` 字段用于发布方身份证明。

## 历史格式

- **AKCP 1.0**（2026-11-01，已实现）：`.aivora` 后缀，Argon2id + AES-256-GCM，独立 Base32 凭证。文档见 [AKCP 目录](../AKCP/)。AEX 上线后仍保留导入与导出能力，不再扩展新特性。