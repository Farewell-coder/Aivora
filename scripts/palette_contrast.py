#!/usr/bin/env python3
"""
配色对比度复算（纯 Python，不需要 kotlinc / Gradle）。

【为什么要在 Kotlin 单测之外再算一遍】
`app/src/test/.../ui/theme/ContrastTest.kt` 已经是权威判据，但它要等构建才能跑。
本机不做构建，所以需要一条「改完配色立刻能验证」的通路：
本脚本直接解析 `ui/theme/Tokens.kt` 里 `AivoraLightColors` / `AivoraDarkColors`
的**真实色值**（不是期望值），按 WCAG 2.1 算关键配对。

两处口径必须一致：本脚本的判据与 ContrastTest 完全相同，改一处记得同步另一处。

用法：python3 scripts/palette_contrast.py [--quiet]
退出码：0 = 全部达标
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TOKENS = ROOT / "app/src/main/java/app/aivora/vault/ui/theme/Tokens.kt"

QUIET = "--quiet" in sys.argv

# 判据：(前景字段, 背景字段, 最低对比度, 说明)
CHECKS = [
    ("text", "background", 4.5, "主文字 / 页面底"),
    ("textSecondary", "background", 4.5, "次文字 / 页面底"),
    ("textTertiary", "background", 4.5, "三级文字 / 页面底"),
    ("textTertiary", "subtle", 4.5, "三级文字 / 弱化面板"),
    ("text", "surface", 4.5, "主文字 / 卡片"),
    ("textSecondary", "surface", 4.5, "次文字 / 卡片"),
    ("textTertiary", "surface", 4.5, "三级文字 / 卡片"),
    ("accent", "background", 4.5, "强调色 / 页面底"),
    ("accent", "surface", 4.5, "强调色 / 卡片"),
    ("onAccent", "accent", 4.5, "强调色上的文字"),
    ("onAccentContainer", "accentContainer", 7.0, "强调容器上的文字"),
    ("success", "background", 4.5, "成功色 / 页面底"),
    ("warning", "background", 4.5, "警告色 / 页面底"),
    ("danger", "background", 4.5, "危险色 / 页面底"),
    ("info", "background", 4.5, "信息色 / 页面底"),
    ("onSuccessContainer", "successContainer", 7.0, "成功容器字"),
    ("onWarningContainer", "warningContainer", 7.0, "警告容器字"),
    ("onDangerContainer", "dangerContainer", 7.0, "危险容器字"),
    ("onInfoContainer", "infoContainer", 7.0, "信息容器字"),
    ("surface", "background", 1.06, "卡片 / 页面底"),
    ("subtle", "background", 1.06, "弱化面板 / 页面底"),
    ("raised", "surface", 1.06, "抬升面 / 卡片"),
    ("outline", "surface", 1.35, "描边 / 卡片"),
    ("divider", "surface", 1.15, "分隔线 / 卡片"),
]

COLOR_RE = re.compile(r"^\s*(\w+)\s*=\s*Color\((0x[0-9A-Fa-f]{8})\)\s*,?\s*$")


def parse_scheme(name: str) -> dict[str, str]:
    """从 Tokens.kt 里抽出某个 val 的 字段 → #RRGGBB。"""
    text = TOKENS.read_text(encoding="utf-8")
    # 找到 `val AivoraLightColors = AivoraColors(` 到匹配的 `)` 的段落
    start = text.find(f"val {name} = AivoraColors(")
    if start == -1:
        raise SystemExit(f"在 Tokens.kt 中找不到 {name}")
    depth = 0
    i = text.find("(", start)
    begin = i + 1
    while i < len(text):
        if text[i] == "(":
            depth += 1
        elif text[i] == ")":
            depth -= 1
            if depth == 0:
                break
        i += 1
    block = text[begin:i]

    out: dict[str, str] = {}
    for line in block.splitlines():
        m = COLOR_RE.match(line)
        if m:
            field, argb = m.group(1), m.group(2)
            # 0xAARRGGBB → #RRGGBB（忽略 alpha：对比度只对不透明前景有意义）
            out[field] = "#" + argb[4:]
    return out


def luminance(hex_color: str) -> float:
    h = hex_color.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))

    def lin(c: float) -> float:
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4

    return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)


def contrast(fg: str, bg: str) -> float:
    lf, lb = luminance(fg), luminance(bg)
    hi, lo = max(lf, lb), min(lf, lb)
    return (hi + 0.05) / (lo + 0.05)


def main() -> int:
    if not TOKENS.exists():
        print(f"找不到 {TOKENS}", file=sys.stderr)
        return 2

    failures = 0
    for scheme in ("AivoraLightColors", "AivoraDarkColors"):
        colors = parse_scheme(scheme)
        label = "浅色" if "Light" in scheme else "深色"
        if not QUIET:
            print(f"== {label}（{scheme}）共解析 {len(colors)} 个色值")
        for fg, bg, need, desc in CHECKS:
            if fg not in colors or bg not in colors:
                print(f"FAIL {label} 缺少色槽：{fg} 或 {bg}（用于「{desc}」）")
                failures += 1
                continue
            actual = contrast(colors[fg], colors[bg])
            if actual >= need:
                if not QUIET:
                    print(f"   PASS {desc:24s} {actual:6.2f} ≥ {need}")
            else:
                print(f"   FAIL {desc:24s} {actual:6.2f} < {need}  ({colors[fg]} on {colors[bg]})")
                failures += 1

    print()
    print(f"对比度检查：失败 {failures} 项")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())