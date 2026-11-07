# Aivora UI 组件契约（重构期间团队共用）

> 本文件是重构期间的**唯一接口约定**。所有页面必须从 `app.aivora.vault.ui.theme` 与
> `app.aivora.vault.ui.kit` 取样式，**禁止**再写 `fontSize = 13.sp`、`RoundedCornerShape(14.dp)`、
> `Color(0xFF...)` 这类裸值。

---

## 一、令牌（`app.aivora.vault.ui.theme`）

### 取色

```kotlin
val c = AivoraThemeColors.colors   // Composable 内
```

`AivoraColors` 字段：

| 字段 | 用途 |
|---|---|
| `background` | 页面底色 |
| `surface` | Block 容器底 |
| `subtle` | 弱化面板底（说明、技术井） |
| `raised` | 抬升面（选中、小控件底） |
| `text` | 主文字 |
| `textSecondary` | 次文字：说明、描述 |
| `textTertiary` | 三级文字：时间戳、计数、占位 |
| `outline` | 结构描边 |
| `divider` | 分隔线 |
| `accent` / `onAccent` | 唯一强调色及其上的文字 |
| `accentContainer` / `onAccentContainer` | 强调容器 |
| `success` / `warning` / `danger` / `info` | 语义色（前景） |
| `successContainer` / `onSuccessContainer` 等 | 语义容器三件套 |
| `scrim` | 遮罩 |

**禁止**用 `MaterialTheme.colorScheme.*` 取语义色（M3 没有这些槽）；只有用 M3 现成控件
（`Switch`、`RadioButton`、`OutlinedTextField`）时它才会自动跟随。

### 字号

```kotlin
AivoraType.pageTitle   // 22 / Bold
AivoraType.section     // 16 / SemiBold
AivoraType.body        // 15
AivoraType.bodyStrong  // 15 / SemiBold
AivoraType.meta        // 13
AivoraType.label       // 12 / SemiBold + 字距
AivoraType.mono        // 13 等宽（Endpoint / 模型 ID / 凭证 / HTTP 码）
AivoraType.monoStrong  // 13 等宽 SemiBold
AivoraType.monoSmall   // 12 等宽（耗时）
```

**等宽只给技术数据**（Endpoint、Key、凭证、HTTP 码、模型 ID、耗时）。
叙事文本一律用非等宽。**最小 12sp**，禁止 10/11sp。

### 间距 / 形状 / 尺寸 / 动效

```kotlin
AivoraSpace.hair(2) xs(4) sm(8) md(12) lg(16) xl(20) xxl(24) xxxl(32)
AivoraSpace.gutter(20) blockPadding(16) itemVertical(12) sectionGap(20) navSpace(132)

AivoraShape.chip(10) inner(14) block(20) sheet(上两角 20) pill(胶囊)

AivoraStroke.hairline(1) emphasis(1.5)

AivoraSize.button(40) buttonSmall(36) iconButton(20) iconTouch(44) field(52) avatar(40)

AivoraMotion.PRESS_MS(90) QUICK_MS(160) STANDARD_MS(220) SHEET_IN_MS(300) SHEET_OUT_MS(220) PRESSED_SCALE(0.97)
```

---

## 二、容器（`app.aivora.vault.ui.kit`）

三档语义，**不许混用**：

| 组件 | 视觉 | 用在哪 |
|---|---|---|
| `SectionBlock { }` | surface + 1dp outline + 20 圆角 | 页面主功能区块 |
| `RowItem { }` | 透明 + 行内留白 12/16 | 配置 / 模型 / 记录 / 设置行 |
| `SubtlePanel { }` | subtle 底、无描边、14 圆角 | 说明、错误摘要、技术井 |

配套：

```kotlin
BlockHeader(title, icon = ..., trailing = @Composable RowScope.() -> Unit)
ItemDivider()                    // Block 内行间分隔线（只在行之间画）
SectionLabel(text)               // Block 外的小标题
```

**规则**：一个 `SectionBlock` 里放多个 `RowItem`，不要每条内容各包一张卡。

---

## 三、数据与状态

```kotlin
enum class StatusKind { Success, Warning, Danger, Info, Neutral }

StatusBadge(kind, text)          // 胶囊 + 状态点 + 文案
DataRow(label, value, mono = true, emphasize = false, valueColor = null, maxLines = 1)
TechWell(value, leadingLabel = "导出凭证", onCopy = { ... })   // 等宽 + 可选中 + 复制
HintText(text)                   // 说明文字
DangerHint(text) / WarningHint(text)
```

**状态判定约定**（全软件统一，不要各自发明）：

| 情形 | kind |
|---|---|
| 模型可用 / 检测通过 / 已验证 | `Success` |
| 模型不可用 / 检测失败 / 删除警告 | `Danger` |
| 超时 / 快照缺失 / 待确认 | `Warning` |
| 未检测 / 进行中 / 中性信息 | `Info` |
| 纯计数、时间戳 | `Neutral` |

---

## 四、控件

```kotlin
AivoraField(label, value, onValueChange, placeholder, supportingText, isError, ...)
PrimaryButton(text, onClick, icon, loading, enabled)      // 强调色填充，一屏最多一个
SecondaryButton(text, onClick, icon, danger, enabled)     // 描边
TextAction(text, onClick, danger)                         // 「取消」「全选」
IconAction(icon, contentDescription, onClick, tint)

SettingRow(title, description, icon, trailing)
SwitchRow(title, checked, onCheckedChange, description, icon)
RadioRow(title, selected, onSelect, icon, description)
NavigationRow(title, onClick, icon, description)

ExpandableBlock(title, icon, subtitle, initiallyExpanded = false) { ... }

ConfirmDialog(title, text, confirmText, onConfirm, onDismiss, dismissText, danger)
```

---

## 五、外壳与导航

```kotlin
AivoraScaffold(
    title = nav.title,
    showsBack = nav.showsBack,
    onBack = { nav.update { it.back() ?: it } },
    showsBottomBar = nav.showsBottomBar,
    snackbarHostState = snack,
) { /* 页面内容 */ }
```

- 底栏只在 `nav.showsBottomBar`（= 在 Tab 本体）时显示，二级页全屏。
- 页面自己要滚动时用 `ScrollColumn(bottomSpace = ...)` 或 `LazyColumn(contentPadding = ...)`，
  **底部留 `AivoraSpace.navSpace`**。
- 非列表页用 `ScrollColumn`。

导航类型在 `app.aivora.vault`：

```kotlin
enum class Tab { Configs, Diagnostics, Records, Settings }
enum class Leaf { About, Protocol }
data class NavState(tab, leaf)   // leaf == null 表示 Tab 本体
```

---

## 六、硬性禁令

1. 禁止 `Color(0x...)`、`fontSize = ..sp`、`RoundedCornerShape(..dp)`、`lineHeight = ..sp` 出现在
   `ui/theme`、`ui/kit` 之外的文件。
2. 禁止 `verticalScroll` 包 `LazyColumn`（会崩）。
3. 列表必须 `LazyColumn` + 稳定 `key`，且**状态（`remember`）要提在页面级**，
   不能放在 item 里 —— 放 item 里滚动出屏就丢。
4. 禁止改动任何 `data/`、`protocol/` 文件，以及以下业务逻辑：
   - 串行检测循环、`persist`、中断与单项重测
   - `Aex` / `Akcp` / `Backup` 的加密与格式
   - `ImportFlow` 的 detect → 分流 → 预览 → commit 四段
   - 导出「先显示凭证，再导出/分享」的顺序
   - `ExportRecord` 的不可变快照语义
5. 禁止弹 `Toast` 以外的自建提示；页面级信息统一用 `toast(...)` 回调。
6. 禁止引入新依赖。