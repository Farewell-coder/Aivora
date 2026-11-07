# 真机验证记录（v0.2.0）

> 本文如实记录**已验证**与**未验证**的部分，不粉饰。

## 一、已验证（有硬证据）

| 项 | 证据 |
|---|---|
| 单元测试全过 | CI：`BUILD SUCCESSFUL in 2m 22s`，77 个测试（含 32 条配色对比度断言） |
| release 编译通过 | CI：`BUILD SUCCESSFUL in 2m 12s` |
| 产物签名与命名 | CI：`apksigner verify` 通过；`Aivora-v0.2.0.apk`（2,901,492 字节） |
| 覆盖安装 | 真机 0.1.2 → 0.2.0 升级成功（versionCode 12 → 13） |
| 深色主题落地 | 像素采样：`#0F1115` background ×155,837、`#7FA8D9` accent ×2,375、`#1A2735` accentContainer ×2,075、`#E8EAEE` text ×355、`#A7AEBA` textSecondary ×178 —— **全部与 token 精确匹配** |
| 浅色主题落地 | 像素采样：`#F6F7F9` background ×155,698、`#2F5F8F` accent ×2,375、`#E7EFF8` accentContainer ×2,075、`#14161A` text ×355、`#48505C` textSecondary ×178 —— **全部与 token 精确匹配** |
| 锁定页渲染 | 文本树：`Aivora 已锁定` / `配置已加密保存，验证身份后才能查看` / `验证身份` |

截图：`01-lock-dark.png`、`02-lock-light.png`（见交付目录 screenshots/）。

## 二、未验证（受环境限制，如实标注）

**主界面五个页面的视觉走查未完成。** 原因：

- 正式版在用户设备上**已启用应用锁**，进入主界面必须通过生物识别或锁屏密码；
- 我不会绕过用户设置的应用锁，也不会尝试输入其密码；
- 尝试过的替代路径均被设备拦下：
  1. debug 变体（带 `.vfy` 包名后缀，可与正式版并存）—— 15MB，`adb install` 与 `pm install` 均静默超时（>4 分钟无输出，无 logcat 记录）；
  2. 同样后缀的 release 变体（2.9MB，安装更快）—— 同样静默无进展；
  3. 设备 `package_verifier_enable` 为 null、`verifier_verify_adb_installs` 为 0（校验已关闭），问题不在校验环节，在该 ROM 对 install session 的管控。

**因此以下仍需人工确认**（建议由设备主人解锁后自行走查，或另找一台未装 Aivora 的设备）：

- 服务配置页：行结构、更多菜单、选择模式、批量操作
- 密钥检测页：模型行的状态徽标、HTTP 码、耗时对齐、进度条
- 导出记录页：日期组头、凭证区块、重新导出
- 设置 / 关于 / 适配协议：分组与数据行
- 六个弹层：配置编辑、导出、导入预览、凭证输入、记录详情、备份
- 页面切换动画、返回链、主题切换后状态保持

## 三、可自助验证的替代手段

```bash
# 1) 静态门禁（已全绿）
bash scripts/gate.sh

# 2) 配色对比度（已全绿）
python3 scripts/palette_contrast.py

# 3) 解锁后用 uiautomator 走查文本层级
adb shell uiautomator dump /sdcard/u.xml && adb pull /sdcard/u.xml
```
