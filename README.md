# Aivora

**简体中文** | [English](README.en.md)

Aivora 是一款基于 Kotlin 与 Jetpack Compose 的本地 Android AI 服务配置管理工具。API Key 使用 Android Keystore 保护的数据密钥加密，配置通过开放的 AKCP 协议和专用 `.aivora` 文件交换。

## 功能

- 仅配置名称必填，Endpoint、API Key 和备注可稍后补充。
- 空白、自定义及主流服务商模板，Endpoint 可编辑。
- 本地加密、配置搜索和删除确认。
- 按配置展开模型检测：自动获取 `/models`，串行检测全部模型，展示状态、HTTP 码、耗时及响应/错误摘要，支持停止和单项重测。
- 使用 OpenAI 兼容 `/models` 与 `/chat/completions`；实际检测可能消耗少量 API 额度。
- 顶部导入 `.aivora` 并验证凭证；单配置导出、连接校验、独立 Base32 凭证与自选保存位置。
- Material 3 浅色/深色/跟随系统界面、Lucide 图标、三项底部导航与系统身份验证。
- 设置内提供 Aivora 专用全量加密备份；跨设备恢复后使用本机密钥重新加密，导入前二次确认覆盖。

无云端账号、云同步、密钥代理、本地 API 服务或 Aivora 后端。

## 构建

需要 Android 8.0+、JDK 17 与 Android SDK 35。

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

## 开放协议

[AKCP 目录](AKCP/)包含完整规范、[中文第三方适配指南](AKCP/ADAPTATION.zh-CN.md)、Schema、错误码、独立 Python 参考实现及双向互操作测试向量。兼容应用只接收 `.aivora`，并继续验证二进制容器与认证标签；安全性不依赖隐藏源码或后缀。

## 许可证

Aivora 采用 **AGPL-3.0-only**，详见 [LICENSE](LICENSE)。

当前版本：0.0.4。AKCP：1.0。加密方案：1。三者独立演进。