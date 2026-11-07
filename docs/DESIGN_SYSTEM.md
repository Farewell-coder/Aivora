# Aivora 设计系统

> 本文是 Aivora v0.2.0 UI 重构的**规范与实现说明**。
> 代码在 `app/src/main/java/app/aivora/vault/ui/`，判据脚本在 `scripts/`。

---

## 0. 一句话概括

**近黑中性基底 + 单一低饱和强调色 + 三档面层 + 叙事文本与技术数据分层。**

三条铁律：

1. **层级只从「底色 + 描边 + 内边距」来**，不靠投影、不靠玻璃模糊堆砌。
2. **对比度不妥协**：正文 ≥ 4.5、容器字 ≥ 7、描边 ≥ 1.35、面层 ≥ 1.06。全部实算，见 §6。
3. **深色是独立设计**，不是浅色的反转。

---

## 1. 为什么重构

旧版（v0.1.2）的问题不是「紫色不好看」，而是**两种内容被迫共用一套排版**：

| 现象 | 实测 |
|---|---|
| 内联字号 | 48 处（12sp×27 / 11sp×13 / 13sp×6 / 10sp×2）；M3 type scale 只用了 5 处 |
| 圆角 | 7 种值并存（8/12/13/14/20/28）|
| 半透明度 | 6 种值（.12/.32/.58/.6/.7/.91）|
| 颜色角色 | `onSurfaceVariant` 用了 32 次，`secondary`/`tertiary` **一次没用** |
| 深度 | 全 App 只有 1 处投影（底栏），其余靠 1dp 描边 |
| 容器 | 只有 `AppCard` 一种，17 个调用点全都长一样 |

后果是「一个管理端点与密钥的工具，最该被看清的 HTTP 码、耗时、错误全被压成 11sp 灰字」，
而「可用 / 不可用」只能靠一句灰字读出来。

---

## 2. 色彩系统

### 2.1 色槽

定义在 `ui/theme/Tokens.kt` 的 `AivoraColors`。

| 槽 | 浅色 | 深色 | 角色 |
|---|---|---|---|
| `background` | `#F6F7F9` | `#0F1115` | 页面底 |
| `surface` | `#FFFFFF` | `#16191F` | Block 容器 |
| `subtle` | `#E9EDF3` | `#1C2027` | 弱化面板、技术井 |
| `raised` | `#E1E7EE` | `#242931` | 抬升面、选中底 |
| `text` | `#14161A` | `#E8EAEE` | 主文字 |
| `textSecondary` | `#48505C` | `#A7AEBA` | 说明、描述 |
| `textTertiary` | `#636B77` | `#848C99` | 时间戳、计数、占位 |
| `outline` | `#D3D9E2` | `#2C323B` | 结构描边 |
| `divider` | `#E4E8EE` | `#23282F` | 行分隔线 |
| `accent` | `#2F5F8F` | `#7FA8D9` | 唯一强调色 |
| `onAccent` | `#FFFFFF` | `#0F1115` | 强调色上的字 |
| `accentContainer` | `#E7EFF8` | `#1A2735` | 强调容器 |
| `onAccentContainer` | `#1C4A78` | `#BFD6EE` | 强调容器字 |
| `success` | `#1F6B4A` | `#5FBF8F` | 可用 / 已验证 |
| `warning` | `#8A5A12` | `#D9A45B` | 超时 / 待确认 / 快照缺失 |
| `danger` | `#9E3B36` | `#E08A84` | 不可用 / 删除 / 密钥外泄 |
| `info` | `#2F5F8F` | `#7FA8D9` | 未检测 / 进行中 |

### 2.2 两个必须记住的判断

**深色档的强调色是浅色，所以其上的字必须是深底字。**
`#7FA8D9` 上的白字只有 **2.47:1**，不可读；用 `#0F1115` 有 7.65:1。这不是风格选择，是硬约束。

**不用 `ColorScheme.fromSeed`。**
M3 的 tonalSpot 会把任何种子推成一套高彩度「现代 App」色阶，恰好抹掉低饱和的初衷。
每个槽手工指定，`AivoraColors.toColorScheme()` 只做映射不做推导。

### 2.3 语义色不是装饰

| 情形 | kind | 视觉 |
|---|---|---|
| 模型可用 / 检测通过 / 已验证 | `Success` | 实心点 + successContainer 底 |
| 模型不可用 / 检测失败 | `Danger` | 实心点 + dangerContainer 底 |
| 超时 / 快照缺失 / 待确认 | `Warning` | 实心点 + warningContainer 底 |
| 未检测 / 进行中 | `Info` | **空心点** + infoContainer 底 |
| 纯计数、时间戳 | `Neutral` | 空心点 + subtle 底 |

**实心点 = 已确定的终态，空心点 = 过程态。** 这样不依赖颜色也能分辨，色觉障碍友好。

---

## 3. 字体系统

定义在 `AivoraType`（`ui/theme/Tokens.kt`）。

| 角色 | 规格 | 用途 |
|---|---|---|
| `pageTitle` | 22 / 700 / lh 30 | 页面标题 |
| `section` | 16 / 600 / lh 24 | 区块标题 |
| `body` | 15 / 400 / lh 22 | 说明、描述 |
| `bodyStrong` | 15 / 600 / lh 22 | 配置名、设置项名 |
| `meta` | 13 / 400 / lh 19 | 时间、计数、辅助说明 |
| `label` | 12 / 600 / ls 0.4 | 分组小标题、状态标签 |
| `mono` | 13 / 等宽 / tnum | Endpoint、模型 ID、凭证 |
| `monoStrong` | 13 / 等宽 600 | HTTP 状态码 |
| `monoSmall` | 12 / 等宽 / tnum | 响应耗时 |

**等宽只给技术数据。** Endpoint、Key、凭证、HTTP 码、模型 ID 需要逐字符比对，
等宽能让 `0/O`、`1/l` 分得清、列能对齐。正文用等宽反而降低阅读速度。

**最小 12sp。** 旧版有一批 10sp / 11sp 的元信息，手机上要凑近才看得清。

**不打包中文字体。** 国产 ROM（MIUI/HyperOS、ColorOS、OriginOS、HarmonyOS）的系统字体
（MiSans、OPPO Sans、vivo Sans、HarmonyOS Sans）质量都很好且自带配套拉丁字形，
跟着系统走最协调；打包思源黑体单字重又要 +10~20MB。等宽用 Android 系统 `Monospace`。

---

## 4. 面层与形状

### 4.1 三档容器（`ui/kit/Components.kt`）

| 组件 | 视觉 | 用在哪 |
|---|---|---|
| `SectionBlock` | `surface` + 1dp `outline` + 20 圆角 | 页面主功能区块 |
| `RowItem` | 透明底 + 行内留白 12/16 + `ItemDivider` | 配置 / 模型 / 记录 / 设置行 |
| `SubtlePanel` | `subtle` 底、**无描边**、14 圆角 | 说明、错误摘要、技术井 |

**核心决策：不再给每条内容包一张卡。**
旧版 17 个 `AppCard` 调用点让所有内容长得一样，视觉上无法区分重要度。
现在一个 `SectionBlock` 里放多个 `RowItem`，靠分隔线与底色区分。

### 4.2 形状

| 档 | 半径 | 用途 |
|---|---|---|
| `chip` | 10 | 状态标签、小控件 |
| `inner` | 14 | 弱化面板、输入框、按钮、嵌套容器 |
| `block` | 20 | 页面主区块 |
| `sheet` | 上两角 20 | 底部弹层 |
| `pill` | 半圆端头 | 悬浮导航、搜索框、胶囊 |

### 4.3 间距

`hair 2 / xs 4 / sm 8 / md 12 / lg 16 / xl 20 / xxl 24 / xxxl 32`

页面左右 `gutter 20`；Block 内边距 16；行内竖向 12；区块之间 20；底栏占位 132。

### 4.4 悬浮底栏

**为什么悬浮**：贴底时滚到列表末尾会被挡住，各页面得自己留 132dp 白，留多留少全凭手感。
悬浮版让留白由外壳统一给。

**为什么不做实时模糊**：列表滚动时每帧模糊持续吃 GPU。半透明面（alpha .96）+ 1dp 描边
+ 单层 8dp 投影在观感上等价，成本为零。

---

## 5. 组件清单（`ui/kit`）

### 容器
`SectionBlock` · `BlockHeader` · `RowItem` · `ItemDivider` · `SubtlePanel` · `SectionLabel`

### 数据与状态
`StatusKind`（5 态）· `StatusBadge` · `DataRow` · `TechWell` · `HintText` · `DangerHint` · `WarningHint`

### 控件
`AivoraField` · `PrimaryButton` · `SecondaryButton` · `TextAction` · `IconAction`
`SettingRow` · `SwitchRow` · `RadioRow` · `NavigationRow` · `ExpandableBlock` · `ConfirmDialog`

### 壳与态
`AivoraScaffold` · `AivoraTopBar` · `BottomBar` · `ScrollColumn` · `LoadingBlock` · `EmptyState`

### 关键组件的设计理由

**`TechWell`**：凭证是**一等公民**。它必须能完整看清、能选中、能复制，
所以内部用 `SelectionContainer` + 等宽 13 + `subtle` 底 + 复制按钮。
凭证丢失就无法恢复数据 —— 这个组件的存在意义是「不许拿 Toast 打发凭证」。

**`StatusBadge`**：状态必须有**颜色 + 形状**两个通道（见 §2.3）。旧版四种状态共用一档灰字。

**`ConfirmDialog`**：危险操作（删除、覆盖）用 `danger` 色确认键，且**默认焦点不在确认键**上。

**`ExpandableBlock`**：箭头跟着展开进度旋转，而不是两帧硬切图标；整行可点，
不再只有小箭头有效（旧版 `ExpandableCard` 的问题）。

---

## 6. 对比度实算（WCAG 2.1）

判据：正文/副文/三级文字 ≥ 4.5；容器上的文字 ≥ 7；结构描边 ≥ 1.35；面层之间 ≥ 1.06。

### 浅色

| 配对 | 实测 |
|---|---|
| text / background | 16.90 |
| textSecondary / background | 7.60 |
| textTertiary / background | 5.02 |
| textTertiary / subtle | 4.58 |
| accent / background | 6.21 |
| onAccent / accent | 6.66 |
| onAccentContainer / accentContainer | 7.87 |
| onSuccessContainer / successContainer | 8.48 |
| onWarningContainer / warningContainer | 7.47 |
| onDangerContainer / dangerContainer | 8.29 |
| surface / background | 1.07 |
| subtle / background | 1.10 |
| raised / surface | 1.25 |
| outline / surface | 1.42 |
| divider / surface | 1.23 |

### 深色

| 配对 | 实测 |
|---|---|
| text / background | 15.69 |
| textSecondary / background | 8.46 |
| textTertiary / background | 5.57 |
| textTertiary / subtle | 4.82 |
| accent / background | 7.65 |
| onAccent / accent | 7.65 |
| onAccentContainer / accentContainer | 10.15 |
| onSuccessContainer / successContainer | 9.59 |
| onWarningContainer / warningContainer | 10.21 |
| onDangerContainer / dangerContainer | 10.16 |
| surface / background | 1.07 |
| subtle / background | 1.16 |
| raised / surface | 1.20 |
| outline / surface | 1.36 |
| divider / surface | 1.19 |

**注意浅色 `subtle` 从 `#EEF1F5` 改到 `#E9EDF3` 的原因**：
原始值对页面底只有 1.057，弱化面板几乎看不出是一块面（脚本判据 1.06 未过）。
加深后 1.10，同时三级文字在其上仍有 4.58（≥ 4.5），两边的约束同时满足。

---

## 7. 导航

旧版是一个 `Page` 枚举装 6 个值（4 个 Tab + 2 个二级页），
导致「这是不是主导航」的判断被复制三处：底栏显不显示、返回链怎么走、标题栏要不要返回箭头。

现在（`ui/Navigation.kt`）：

```kotlin
enum class Tab { Configs, Diagnostics, Records, Settings }
enum class Leaf { About, Protocol }
data class NavState(tab, leaf)    // leaf == null 表示在 Tab 本体

NavState.showsBottomBar   // 只在 Tab 本体 true
NavState.showsBack        // 二级页 true
NavState.back()           // 返回父级，null 表示已在根
```

「底栏只在 `leaf == null` 时显示」这条规则**只写一次**（在 `AivoraScaffold`）。
`NavState` 用 `rememberSaveable` 保存，进程被回收后能恢复，不会跳回首页。

---

## 8. 列表

全部改 `LazyColumn` + 稳定 `key`：

| 列表 | key |
|---|---|
| 配置 | `it.id` |
| 检测结果（按配置） | `it.id` |
| 模型结果（单配置内） | `model.name` |
| 导出记录 | `record.id` |
| 导入候选 | 索引 + 名称 |

**状态必须提在页面级 `remember`**，不能放 item 内 —— 放 item 里滚动出屏就丢。
这是本次重构最容易引入回归的点，各处已逐一确认。

---

## 9. 门禁

```bash
bash scripts/gate.sh            # 四道门禁全跑
bash scripts/gate.sh --quiet    # 只看失败项
```

| 脚本 | 检查什么 | 判据 |
|---|---|---|
| `scripts/ui_audit.py` | A1 令牌层外无裸值 · A2 旧组件已清除 · A3 无 10/11sp · A4 列表惰性化 | 全文正则 |
| `scripts/kotlin_precheck.py` | 括号/块注释平衡 · 漏 import · `AivoraThemeColors.text` 误用 · `verticalScroll` 套 `LazyColumn` | 词法扫描 |
| `scripts/palette_contrast.py` | §6 全部配对 | 解析 Tokens.kt 真实值 |
| `app/src/test/.../ContrastTest.kt` | 同 §6，作为权威单测（需构建） | JUnit |

`ui_audit.py` 的 `ALLOWED_PREFIXES` 只放行 `ui/theme/` 与 `ui/kit/`——
这是**唯二**允许出现样式字面量的地方，`EXEMPT` 为空表示重构后零豁免。

---

## 10. 明确没做的事

- **没有引入任何新依赖**：动画、图标（Lucide 已在 v0.1.0 接入）、字体全部用现有能力。
- **没有做多套强调色预设**：Aivora 的定位需要稳定而非可玩，只用一套石板蓝。
- **没有用实时模糊玻璃**：性能代价与视觉收益不匹配，见 §4.4。
- **没有改业务层**：`data/`、`protocol/` 与全部加密/协议逻辑一行未动，见 §11。

---

## 11. 业务红线（重构期间逐条对照）

以下**必须**保持不变，任何一条被改动都是缺陷：

- `LocalCrypto`：MasterKey → `data_key` → AES-256-GCM，API Key 不以明文落库
- `Aex` / `Akcp` / `Backup` 的文件格式、KDF 参数、AAD 语义
- `KeyDiagnostics`：**串行**检测（不得改并行）、`/models` 失败才试候选、`max_tokens = 1`
- `ImportFlow`：detect → 分流（加密/明文/失败）→ 预览勾选 → commit，四段不能合并
- `ExportRecord`：**不可变快照**，「重新导出」复刻当初那份文件，不用当前配置
- 导出流程：**先显示凭证，再导出/分享**，凭证不随文件发出
- `Endpoint.normalize` 的调用点与参数
- 数据库 schema、迁移、`applicationId`、`minSdk`/`targetSdk`、签名配置

---

## 12. 顺带修掉的真实缺陷

| # | 缺陷 | 修法 |
|---|---|---|
| 1 | 锁屏锁图标 `tint = Brand`（`#6750A4`），深色底上几乎看不见 | 改 `AivoraThemeColors.colors.accent`，深色档自动用 `#7FA8D9` |
| 2 | 启动窗口底 `#171018` 与 Kotlin 侧深色 background 不一致，冷启动闪色 | 两侧统一为 `#0F1115`（浅色 `#F6F7F9`），并加 `ContrastTest` 断言防回退 |
| 3 | `styles.xml` 写死 `windowLightStatusBar=true`，与运行时的 `SideEffect` 两套机制打架 | XML 只负责启动窗口；运行期统一由 `SystemBars(dark)` 设置，深色另有 `values-night/styles.xml` |
| 4 | 弱化面板 `subtle` 对页面底仅 1.057，几乎看不出是一块面 | 加深至 `#E9EDF3`（1.10），三级文字仍 ≥ 4.5 |
| 5 | 状态只能靠文字说明（四种状态同一档灰） | `StatusBadge` 五态 + 实心/空心点双通道 |
| 6 | HTTP 码、耗时用 11sp 灰字 | `monoStrong` 13sp、`monoSmall` 12sp，等宽对齐 |