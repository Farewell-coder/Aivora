# 第三方软件 AKCP 适配指南

本目录是 Aivora 主仓库内公开的第三方适配协议。第三方可以独立实现，不需要 Aivora 账号、服务器、SDK 或专用插件。

## 接入流程

1. 在文件选择器中只接收小写 `.aivora` 后缀；修改后缀的文件必须拒绝。
2. 限制文件不超过 16 MiB，验证 Magic、64 字节头、长度、协议版本和加密参数。
3. 向用户索取该文件独立的 Base32 凭证，移除连字符后转大写。
4. 使用 Argon2id（64 MiB、3 次迭代、并行度 1）派生 256-bit 密钥。
5. 以完整 64 字节头作为 AAD，使用 AES-256-GCM 认证并解密。
6. 认证失败、文件篡改、截断或版本不支持时，不得输出任何部分明文。
7. 按 `schemas/config.schema.json` 验证载荷，映射到第三方自己的数据结构。
8. 展示隐藏 API Key 的导入预览，经用户确认后保存；未知字段尽量保留，不能保留时应明确提示。

## 规范索引

- [AKCP 总览](README.md)
- [完整规范](SPECIFICATION.md)
- [二进制容器](format/container.md)
- [加密定义](cryptography/encryption.md)
- [兼容等级](COMPATIBILITY.md)
- [安全与错误码](SECURITY.md)
- [JSON Schema](schemas/config.schema.json)
- [独立 Python 参考实现](implementations/python/aivora_akcp.py)
- [公开测试向量](test-vectors/)

## 参考实现

`implementations/python/aivora_akcp.py` 是独立实现，不引用 Aivora 应用代码。仓库中的 Kotlin 单测负责读取 Python 导出的样例并生成 Kotlin 样例，再由 Python 反向解密。所有公开向量均使用虚构密钥，不可用于真实服务。

## 安全与许可

`.aivora` 后缀只是生态入口，不是安全机制。协议结构、算法和源码全部公开，安全性依赖凭证保密、Argon2id 与 AES-GCM 认证。第三方成功解密后可接触明文 API Key，应自行负责内存、日志和存储安全。

Aivora 应用及本仓库内容采用 AGPL-3.0-only；参考实现和文档的复制、修改与分发遵循主仓库许可证。