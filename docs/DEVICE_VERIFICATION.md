# 真机验证记录（v0.2.0）

> 设备：OPPO PJB110 / Android 14（API 34）/ arm64
> 方式：CI 构建 release APK → `adb install -r` 覆盖安装 → uiautomator 文本树 + 截图像素采样

## 一、已完成的验证

### 1. 构建与安装

| 项 | 结果 |
|---|---|
| 单元测试 | 77 个测试全过（含 32 条配色对比度断言） |
| release 编译 | `BUILD SUCCESSFUL`，产物 2,901,492 字节 |
| 签名与命名 | `apksigner verify` 通过；`Aivora-v0.2.0.apk` |
| 覆盖安装 | 真机 0.1.2 → 0.2.0 升级成功（versionCode 12 → 13） |

### 2. 双主题像素采样（实测值 vs token）

**深色**

| 实测 | token | 出现次数 |
|---|---|---|
| `#0F1115` | background | 164,984 |
| `#16191F` | surface | 77,810 |
| `#7FA8D9` | accent | 8,177 |
| `#1A2735` | accentContainer | 4,395 |
| `#1C2027` | subtle | 2,911 |
| `#2C323B` | outline | 2,509 |
| `#E8EAEE` | text | 1,155 |
| `#848C99` | textTertiary | 902 |

**浅色**

| 实测 | token | 出现次数 |
|---|---|---|
| `#FFFFFF` | surface | 141,421 |
| `#F6F7F9` | background | 95,581 |
| `#E7EFF8` | accentContainer | 4,452 |
| `#D3D9E2` | outline | 3,267 |
| `#14161A` | text | 2,028 |
| `#48505C` | textSecondary | 1,588 |

结论：**两套主题都是真实渲染的独立配色，不是同一套色值的反色。**

### 3. 逐页走查（文本树 + 截图）

| 页面 / 组件 | 验证点 | 结果 |
|---|---|---|
| 锁定页 | 标题、副标题、验证按钮 | ✅ 深浅两档均正确 |
| 服务配置页（空态） | 图标 + 标题 + 说明 + 操作、胶囊搜索框、四标签底栏 | ✅ |
| 服务配置页（有数据） | 首字母方块 + 名称 + `服务商 · Endpoint`（mono） + 计数 | ✅ |
| 更多操作菜单 | 三点图标、导出 / 复制 Endpoint 与 Key / 删除 | ✅ |
| 配置编辑弹层 | ModalBottomSheet、五个字段、说明、保存/取消 | ✅ |
| 服务商模板下拉 | 11 个模板，选中后 Endpoint 自动填入 | ✅ |
| 键盘避让 | 键盘弹出后弹层 `imePadding` 生效 | ✅ |
| 密钥检测页 | 状态徽标、检测时间、检测按钮、空态提示 | ✅ |
| 状态语义色 | 「缺少配置」徽标底色 `#2C1917`=dangerContainer、字 `#F3BDB8`=onDangerContainer（对比度 10.16） | ✅ 非灰字 |
| 检测流程 | 无 Key 时正确落到「缺少配置」分支并持久化 | ✅ |
| 导出记录页（空态） | 标题、说明、四标签底栏 | ✅ |
| 导出弹层 | 凭证区底色 `#1C2027`=subtle、字 `#E8EAEE`=text；危险提示 `#2C1917`=dangerContainer | ✅ 等宽可选中 |
| 设置页 | 四组分组（行为/外观/数据/关于）、开关行、单选行、说明 | ✅ |
| 主题切换 | 设置页点「浅色模式」即时生效，无需重启 | ✅ |

## 二、发现的真实问题

走查过程中由真机输入触发的现象，**不是 UI 缺陷**，记录备查：

1. **OPPO 微信输入法在拼音模式下会把 `adb input text` 的内容转成中文**（`123` → 「特区」）。
   验证时改用系统 Gboard 规避。真实用户使用正常输入法，不受影响。
2. 配置名出现了全角连字符与多余空格（`Open AI－Prod`），同样是输入法转换所致。

## 三、验证覆盖率的诚实说明

本轮覆盖：**空态与有数据两种形态下的 6 个页面、5 个弹层/浮层、双主题、状态语义色、键盘避让、菜单、下拉。**

未覆盖（需要真实网络与真实密钥，本轮不具备条件）：

- 模型检测的**实际成功/失败行**渲染（需要可用的 Endpoint + API Key）
- 模型行的 HTTP 码 / 耗时 / 错误摘要（同上）
- 导入预览弹层（需要第三方向导出的配置文件）
- 记录详情弹层（需要先产生一条含快照的导出记录）
- 备份导出/导入弹层

这些路径的 UI 代码与已验证路径**共用同一套组件**（`RowItem` / `StatusBadge` / `DataRow` / `TechWell` / `ModalBottomSheet`），
组件层已通过像素采样与文本树验证；但**页面级组合仍需真实数据才能完整确认**。

## 四、复现命令

```bash
# 静态门禁
bash scripts/gate.sh

# 配色对比度
python3 scripts/palette_contrast.py

# 真机（需先安装 APK）
adb shell uiautomator dump /sdcard/u.xml && adb pull /sdcard/u.xml
adb exec-out screencap -p > shot.png
```
