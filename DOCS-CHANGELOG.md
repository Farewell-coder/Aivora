# 文档修改清单（0.0.5）

本次仅修改文档，**未改动 Android 应用源码的业务逻辑**（除 0.0.5 的明文 HTTP 修复外）。

## 新增文件

| 路径 | 说明 |
|---|---|
| `protocol/README.md` | AEX 协议总览与文档索引 |
| `protocol/AEX-SPECIFICATION.md` | AEX 1.0 完整规范（Draft） |
| `protocol/AEX-SECURITY.md` | 加密与安全规范（Draft） |
| `protocol/AEX-SCHEMA.json` | Payload JSON Schema |
| `protocol/AEX-CHANGELOG.md` | 协议更新记录 |
| `protocol/AEX-ADAPTER-GUIDE.md` | 第三方适配指南 |
| `protocol/AEX-TEST-VECTORS.md` | 测试向量规则与验收清单 |
| `protocol/ECOSYSTEM.md` | 生态适配列表与登记模板 |
| `protocol/compatibility/legacy-format.md` | 旧版 AKCP 1.0 兼容说明 |
| `protocol/examples/single-profile.md` | 单配置示例 |
| `protocol/examples/multiple-profiles.md` | 多配置示例 |
| `protocol/examples/encrypted-package.md` | 加密包结构与构造步骤 |
| `app/src/main/res/xml/network_security_config.xml` | 允许明文 HTTP（0.0.5 修复） |
| `DOCS-CHANGELOG.md` | 本文件 |

## 修改文件

| 路径 | 变更 |
|---|---|
| `README.md` | 全量重写：按项目介绍 / 功能展示 / 下载安装 / AEX 协议 / 开发者入口 / 生态适配 / 参与贡献 / 更新日志 / 许可证 八段组织 |
| `README.en.md` | 与中文版结构对齐的英文重写 |
| `app/src/main/AndroidManifest.xml` | 增加 `networkSecurityConfig` 与 `usesCleartextTraffic` |
| `app/build.gradle.kts` | `versionCode 4 → 5`，`versionName 0.0.4 → 0.0.5` |
| `app/src/main/java/app/aivora/vault/MainActivity.kt` | 关于页版本号文案更新为 0.0.5 |
| `app/src/main/java/app/aivora/vault/data/Diagnostics.kt` | User-Agent 更新为 `Aivora/0.0.5` |

## 未改动

- `AKCP/` 目录全部保留，作为旧版协议文档。
- `LICENSE` 未改动，仍为 AGPL-3.0-only。
- 应用业务逻辑、数据库结构、加密实现未改动。

## 已核实项

- README 中所有相对链接指向的文件均真实存在。
- 版本号 0.0.5 在 README、README.en.md、build.gradle.kts、MainActivity.kt、Diagnostics.kt 五处一致。
- AEX 相关内容全部标注为 Draft，未声称已实现。
- 适配列表为空，未编造任何第三方软件。

## 待补充

- AEX 正式测试向量文件（待协议实现后补充）。