<div align="center">

# Aivora

**本地优先的 Android AI 配置管理器**

<br>

<a href="https://github.com/Farewell-coder/Aivora/releases/latest"><img src="https://img.shields.io/github/v/release/Farewell-coder/Aivora?label=release" alt="Release"></a>
&nbsp;
<a href="LICENSE"><img src="https://img.shields.io/github/license/Farewell-coder/Aivora" alt="License"></a>
&nbsp;
<a href="README.en.md"><img src="https://img.shields.io/badge/%E8%AF%AD%E8%A8%80-English-lightgrey?style=flat" alt="English"></a>

</div>

---

Aivora 是一款**本地优先**的 Android AI 服务配置管理器。你可以把常用的 AI 服务（Endpoint、API Key、模型列表、备注）集中保存到一台设备上，加密存储、随时检索、按需检测可用性，并通过加密文件与其它软件交换配置。

没有账号、没有云端同步、没有密钥代理、没有自有服务端。数据只存在你的设备里。

## 目录

- [项目介绍](#项目介绍)
- [功能展示](#功能展示)
- [下载与安装](#下载与安装)
- [AEX 配置交换协议](#aex-配置交换协议)
- [第三方开发者入口](#第三方开发者入口)
- [生态适配](#生态适配)
- [参与贡献](#参与贡献)
- [更新日志](#更新日志)
- [许可证](#许可证)

## 项目介绍

### 解决什么问题

同时用多个 AI 服务的人，通常会把 Endpoint 和 API Key 散落在聊天记录、备忘录、浏览器书签里。换手机、换客户端、给朋友推荐服务时，就要反复翻找和复制。Aivora 把这些配置收拢到一个本地应用里，并把“跨软件搬运配置”做成了一个有规范、有加密、可验证的动作。

### 核心功能

| 能力 | 说明 |
|---|---|
| 本地加密存储 | API Key 使用 Android Keystore 保护的数据密钥加密后落库，不以明文形式写入数据库 |
| 配置管理 | 新建、编辑、搜索、删除（带确认），仅配置名称必填 |
| 服务商模板 | 内置主流服务商的 Endpoint 模板，也可完全自定义 |
| 模型检测 | 自动拉取 `/models`，串行检测每个模型可用性，展示状态、HTTP 码、耗时与错误摘要，支持中断与单项重测 |
| 配置交换 | 导出时可加密生成独立凭证保护的文件，导入需凭证验证 |
| 全量备份 | Aivora 专用加密备份，跨设备恢复时用新设备密钥重新加密，导入前二次确认 |
| 界面 | Material 3，浅色 / 深色 / 跟随系统三种外观，支持系统身份验证 |

### 设计理念

- **本地优先**：所有数据留在设备内，功能不依赖任何服务器可用性。
- **不假装安全**：协议、算法、源码全部公开，安全性来自加密本身，不靠隐藏实现或代码混淆。
- **格式统一**：一个协议、一种文件、一套凭证，不收口于实施细节的碎片化。
- **开放交换**：配置应当能在软件之间流转，而不是被锁在某个应用里。

## 功能展示

### 服务配置

集中管理所有 AI 服务配置，卡片上方显示名称与最近检测状态，下方为操作按钮。

典型使用场景：把 DeepSeek、自建网关、某个中转服务的密钥都存进来，用备注区分用途，随时搜索定位。

### 密钥检测

点击检测后自动拉取模型列表并逐个试跑，失败项显示真实 HTTP 状态与服务端返回的原始错误信息，而不是笼统的“密钥无效”。

典型使用场景：新拿到一个第三方网关的密钥，先批量测一遍，看清哪些模型真的可用、哪些限流、哪些余额不足。

> 检测会发送一次极小的真实请求（`max_tokens = 1`），可能消耗少量额度。

### 导出与分享

配置卡片右下角的导出按钮会打开一个窗口，里面提供两个并列的动作：「导出」把 `.akcp` 保存到你自选的位置，「分享」直接交给系统分享面板发给其他软件。两者生成的文件字节完全相同。

窗口里同时显示这枚文件专属的 52 字符凭证。**文件本身不含凭证**，所以无论导出还是分享，接收方都必须另外拿到这串凭证才能解密。

典型使用场景：把它发到自己的另一台设备或交给某个客户端，凭证另行口述或用密码管理器同步，文件即使被中途看到也读不出内容。

### 设置与备份

设置页集中提供外观选择（浅色 / 深色 / 跟随系统）、应用锁与全量加密备份。备份文件使用独立凭证保护，跨设备恢复时用新设备的密钥重新加密，导入前二次确认防止误覆盖。

典型使用场景：换机前导出一份加密备份，新设备上导入并在确认后恢复全部配置。

## 下载与安装

### 最新版本

当前发布版本：**0.0.5**

- 下载页面：[github.com/Farewell-coder/Aivora/releases/latest](https://github.com/Farewell-coder/Aivora/releases/latest)
- 安装包命名：`Aivora-v0.0.5.apk`

### 安装说明

1. 在下载页面获取对应版本的 `.apk` 文件。
2. 系统提示“未知来源”时，允许当前浏览器或文件管理器安装应用。
3. 打开 Aivora，无需注册或登录即可使用。

系统要求：**Android 8.0（API 26）及以上**，arm64 设备。

### 关于签名

正式包使用固定证书签名，可覆盖升级旧版本。若此前安装的是自行构建的 debug 包，因签名不同无法直接覆盖，需要先卸载（会清除应用内数据，请先导出备份）。

## AEX 配置交换协议

> **状态：AEX 1.0 已冻结，自 0.0.5 起在 Aivora 中实现。**
> 配置卡片与批量选择均生成 `.akcp` 文件，经 Android 系统分享面板交给接收方。

### 设计目的

让“把一个 AI 服务配置从 A 软件搬到 B 软件”变成一件有标准可循的事。第三方软件不需要适配 Aivora 的私有格式，也不依赖 Aivora 的服务器，只要按公开规范实现解密即可接收。

AEX 是 Aivora 的**完整、独立、唯一**的配置交换协议。不存在平行的第二套交换格式，也不再规划。

### `.akcp` 文件

`.akcp` 是 AEX 的**唯一**标准交换文件格式。

- 扩展名：`.akcp`
- MIME Type：`application/vnd.aivora.akcp`
- 结构：固定 Magic + JSON 头部 + AES-256-GCM 密文
- 加密：AES-256-GCM + PBKDF2-HMAC-SHA256（600000 次迭代）
- 头部整体作为 AAD，篡改协议参数或加密参数会直接导致解密失败
- 文件头不暴露配置名称、Endpoint 或 API Key

内部数据是 JSON，但这只是文件内部的序列化方式，**不是第二种导出格式**——用户始终只面对 `.akcp` 一种文件。

### 配置分享能力

导出流程：

```text
配置页 → 导出 → 生成 .akcp（独立凭证加密）→ 系统分享面板 → 选择目标软件
```

- **两个入口**：「导出」保存到用户自选的位置，「分享」直接调用系统分享面板；两者生成的 `.akcp` 字节完全相同
- 使用 Android 原生 Sharesheet，不自绘仿系统分享面板
- 分享时文件写入应用私有缓存目录，通过 FileProvider 生成 `content://` URI，不暴露真实路径
- 文件本身不含凭证：接收方**必须另外输入 52 字符凭证**才能解密，凭证不会随文件一起发出
- 分享前明确提示文件包含完整 API Key

### 单个与批量配置

一个 `.akcp` 文件可以承载单个配置、多个配置或完整配置集合，统一使用 `profiles` 数组，**不为批量导出另建格式**，加密流程完全一致。

### 第三方适配机制

第三方软件只需：

1. 校验扩展名、大小与 Magic；
2. 解析头部并校验加密参数；
3. 请用户输入凭证（52 字符 Base32）；
4. 用 PBKDF2 派生密钥、AES-GCM 解密（头部为 AAD）；
5. 按 Schema 校验 Payload，打码预览后由用户确认导入。

不需要 Aivora SDK、账号、服务器或源码。

## 第三方开发者入口

- **协议总览**：[protocol/README.md](protocol/README.md)
- **完整规范**：[protocol/AEX-SPECIFICATION.md](protocol/AEX-SPECIFICATION.md)
- **加密与安全**：[protocol/AEX-SECURITY.md](protocol/AEX-SECURITY.md)
- **数据结构 Schema**：[protocol/AEX-SCHEMA.json](protocol/AEX-SCHEMA.json)
- **适配指南**：[protocol/AEX-ADAPTER-GUIDE.md](protocol/AEX-ADAPTER-GUIDE.md)
- **测试与验证**：[protocol/AEX-TEST-VECTORS.md](protocol/AEX-TEST-VECTORS.md)
- **官方测试向量**：[protocol/test-vectors/](protocol/test-vectors/)
- **协议更新记录**：[protocol/AEX-CHANGELOG.md](protocol/AEX-CHANGELOG.md)
- **示例**：[单配置](protocol/examples/single-profile.md) · [多配置](protocol/examples/multiple-profiles.md) · [加密包结构](protocol/examples/encrypted-package.md)

## 生态适配

AEX 1.0 已随 Aivora 发布版本实现，可以开始联调。**目前尚无第三方软件完成适配**，列表为空。

适配列表与登记格式见 [protocol/ECOSYSTEM.md](protocol/ECOSYSTEM.md)。列表中的项目会明确标注适配状态：

| 状态 | 含义 |
|---|---|
| 已验证 | 维护者用公开测试向量实际跑通，且有可复现记录 |
| 自行声明 | 提交方声明完成适配，未独立验证 |
| 开发中 | 已知正在适配 |
| 计划中 | 已登记意向 |

未经核实的项目不会被标记为已完整支持。

未适配 AEX 的软件不受影响，原有功能保持不变，只是无法直接接收 `.akcp` 文件。Aivora 不强制任何软件适配。

## 参与贡献

### 反馈问题

- **Bug**：请开 Issue 并附上复现步骤、Aivora 版本、Android 版本与相关错误信息。
- **不要**在 Issue 中粘贴真实 API Key 或真实配置文件。

### 功能建议

开 Issue 描述使用场景与期望行为即可。Aivora 的定位是本地工具，不会引入云同步、账号体系或密钥托管功能。

### 协议适配贡献

按 [适配指南](protocol/AEX-ADAPTER-GUIDE.md) 实现后，用 [ECOSYSTEM.md](protocol/ECOSYSTEM.md) 的模板提交登记。协议本身有异议或发现歧义，请开 Issue 并标注 `protocol` 标签。

### 代码贡献

```bash
git clone https://github.com/Farewell-coder/Aivora.git
cd Aivora
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

需要 JDK 17 与 Android SDK 35。提交前请确保单元测试通过，并避免引入与本次改动无关的重构。

## 更新日志

### 0.0.5

- **修复**：使用 `http://` 的第三方 Endpoint 无法拉取模型的问题。此前请求会被 Android 明文流量策略拦截，错误提示为 `CLEARTEXT communication not permitted by network security policy`，请求根本没有到达服务端。现通过网络安全配置文件允许明文 HTTP，同时保持系统证书校验不变。
- **AEX 协议**：实现 AEX 1.0（`.akcp`，AES-256-GCM + PBKDF2-HMAC-SHA256 600000 次迭代，头部作为 AAD），导出后经 FileProvider 调用系统分享面板，支持单配置、多选与全量三种范围。
- **文档**：新增 AEX 协议文档目录 `protocol/`，重构主 README。

### 0.0.4

- 修复系统返回键在「关于」「适配协议」页直接退出应用的问题。
- 模型拉取失败时显示真实 HTTP 状态与服务端返回信息，不再笼统提示“密钥无效”。
- 密钥首尾空白字符在保存与发送前自动清理。
- 编辑配置后清空旧模型与旧检测结果，避免残留错误。
- 修复导出凭证为空仍提示“已复制”、单模型重测时的空指针、生物识别失败后界面卡死等问题。
- 安装包体积从 3,984,449 字节降至 2,705,722 字节（约 −32.1%），未减少功能、未更换加密实现。

### 0.0.3

- 新增 Aivora 专用全量加密备份与跨设备恢复。
- 优化配置卡片操作区布局，改为图标按钮。

### 0.0.2

- 完善配置新建与编辑流程、界面细节。

### 0.0.1

- 首个版本：本地加密配置管理、密钥检测、配置导入导出。

## 许可证

Aivora 采用 **AGPL-3.0-only**，详见 [LICENSE](LICENSE)。

版本说明：应用版本与协议版本**独立演进**。当前为应用 0.0.5 · AEX 1.0（已冻结）。