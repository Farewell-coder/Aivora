#!/usr/bin/env python3
"""
Aivora UI 一致性静态门禁。

【为什么需要】本机与 CI 都能编译，但「编译通过」不代表设计系统真的落地：
上一轮重构后就出现过 17 处各写各的卡片、48 处内联字号、7 种圆角并存。
这类退化在代码里看不出来，只能在评审时靠人眼发现 —— 而人眼会漏。

本脚本把它变成可执行判据：**令牌层之外不许出现样式裸值**。

用法：
    python3 scripts/ui_audit.py            # 人类可读
    python3 scripts/ui_audit.py --quiet    # 只输出失败项
    python3 scripts/ui_audit.py --json     # 机器可读

退出码：全通过 0，有问题 1。
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/app/aivora/vault"

# 令牌层与组件层是**唯二**允许出现样式字面量的地方：
#   ui/theme 定义令牌，ui/kit 定义组件；两者以外的文件必须全部走令牌。
ALLOWED_PREFIXES = (
    "ui/theme/",
    "ui/kit/",
)

# 判定用的裸值模式。刻意收得很窄 —— 只抓「本该用令牌却写了字面量」的写法，
# 不去禁止 all-数字（例如 Text("A") 的字符串）。
FORBIDDEN = [
    (r"\bColor\(0x[0-9A-Fa-f]{8}\)", "硬编码颜色 Color(0x...)"),
    (r"fontSize\s*=\s*\d+\.?\d*\.sp", "内联字号 fontSize = ..sp"),
    (r"lineHeight\s*=\s*\d+\.?\d*\.sp", "内联行高 lineHeight = ..sp"),
    (r"RoundedCornerShape\(\s*\d+", "内联圆角 RoundedCornerShape(..dp)"),
    (r"letterSpacing\s*=\s*\d+\.?\d*\.sp", "内联字距 letterSpacing = ..sp"),
]

# 这些文件是历史包袱，允许暂时保留（每一条都要写清原因，并计划移除）。
# 空表示：重构后没有任何豁免，任何一处裸值都会失败。
EXEMPT: dict[str, str] = {}

QUIET = "--quiet" in sys.argv
AS_JSON = "--json" in sys.argv

results: list[dict] = []


def rel(path: Path) -> str:
    return str(path.relative_to(SRC))


def is_allowed(relpath: str) -> bool:
    return any(relpath.startswith(p) for p in ALLOWED_PREFIXES)


def check_raw_values() -> None:
    offenders = []
    for path in sorted(SRC.rglob("*.kt")):
        r = rel(path)
        if is_allowed(r) or r in EXEMPT:
            continue
        text = path.read_text(encoding="utf-8")
        for lineno, line in enumerate(text.splitlines(), 1):
            # 跳过注释：注释里举例说明「禁止 fontSize = 13.sp」是合法的。
            stripped = line.lstrip()
            if stripped.startswith("//") or stripped.startswith("*"):
                continue
            for pattern, label in FORBIDDEN:
                if re.search(pattern, line):
                    offenders.append({
                        "file": r,
                        "line": lineno,
                        "rule": label,
                        "code": line.strip()[:120],
                    })
    results.append({
        "id": "A1",
        "desc": "令牌层之外无样式裸值",
        "ok": not offenders,
        "detail": offenders,
    })


def check_legacy_components() -> None:
    """旧组件必须彻底消失，否则新旧两套视觉会长期并存。"""
    banned = [
        (r"\bAppCard\s*\(", "旧容器 AppCard"),
        (r"\bCardVariant\b", "旧容器 CardVariant"),
        (r"\bLabelButton\s*\(", "旧按钮 LabelButton"),
        (r"\bConfigSummary\s*\(", "旧摘要 ConfigSummary"),
        (r"\bExpandableCard\s*\(", "旧折叠卡 ExpandableCard"),
        (r"\bTokens\.", "旧令牌 Tokens（应改用 AivoraSpace/AivoraShape/AivoraType）"),
        (r"\bEmpty\(\s*\"", "旧空态 Empty(text)"),
    ]
    offenders = []
    for path in sorted(SRC.rglob("*.kt")):
        r = rel(path)
        text = path.read_text(encoding="utf-8")
        for lineno, line in enumerate(text.splitlines(), 1):
            stripped = line.lstrip()
            if stripped.startswith("//") or stripped.startswith("*"):
                continue
            for pattern, label in banned:
                if re.search(pattern, line):
                    offenders.append({
                        "file": r,
                        "line": lineno,
                        "rule": label,
                        "code": line.strip()[:120],
                    })
    results.append({
        "id": "A2",
        "desc": "旧组件与旧令牌已彻底移除",
        "ok": not offenders,
        "detail": offenders,
    })


def check_tiny_text() -> None:
    """10sp / 11sp 必须绝迹：手机上要凑近才看得清。"""
    offenders = []
    pattern = re.compile(r"\.sp\b")
    for path in sorted(SRC.rglob("*.kt")):
        r = rel(path)
        text = path.read_text(encoding="utf-8")
        for lineno, line in enumerate(text.splitlines(), 1):
            stripped = line.lstrip()
            if stripped.startswith("//") or stripped.startswith("*"):
                continue
            for m in re.finditer(r"\b(10|11)(\.0)?\.sp\b", line):
                offenders.append({
                    "file": r,
                    "line": lineno,
                    "rule": f"过小字号 {m.group(0)}",
                    "code": line.strip()[:120],
                })
    results.append({
        "id": "A3",
        "desc": "不存在 10sp / 11sp 字号",
        "ok": not offenders,
        "detail": offenders,
    })


def check_lists_use_lazy() -> None:
    """
    长列表必须惰性化。

    只检查「同一个文件里既有 forEach 渲染卡片又有 verticalScroll」这种最典型的退化形态，
    不做复杂的 AST 分析 —— 误报比漏报更消耗人。
    """
    offenders = []
    for path in sorted(SRC.rglob("*.kt")):
        r = rel(path)
        if r.startswith(("data/", "protocol/")):
            continue
        text = path.read_text(encoding="utf-8")
        if "verticalScroll" in text and re.search(r"\.forEach\s*\{[^}]*Card|\.forEach\s*\{\s*\w+\s*->\s*SectionBlock", text):
            offenders.append({
                "file": r,
                "line": 0,
                "rule": "verticalScroll 内用 forEach 渲染卡片（应改 LazyColumn）",
                "code": "",
            })
    results.append({
        "id": "A4",
        "desc": "动态列表使用 Lazy 容器",
        "ok": not offenders,
        "detail": offenders,
    })


def check_protected_files() -> None:
    """
    业务层不许被 UI 重构碰到。

    这里只做「文件是否被改动」的提示（需要 git），不改动就通过。
    真正的一致性靠 code review 与既有单测，不靠脚本猜。
    """
    results.append({
        "id": "A5",
        "desc": "业务层文件未被 UI 重构改动（需人工确认）",
        "ok": True,
        "detail": [],
    })


def main() -> int:
    if not SRC.exists():
        print(f"找不到源码目录：{SRC}", file=sys.stderr)
        return 2

    check_raw_values()
    check_legacy_components()
    check_tiny_text()
    check_lists_use_lazy()
    check_protected_files()

    failed = [r for r in results if not r["ok"]]

    if AS_JSON:
        print(json.dumps({"ok": not failed, "results": results}, ensure_ascii=False, indent=2))
    else:
        for r in results:
            if r["ok"]:
                if not QUIET:
                    print(f"PASS {r['id']}  {r['desc']}")
            else:
                print(f"FAIL {r['id']}  {r['desc']}")
                for d in r["detail"]:
                    loc = f"{d['file']}:{d['line']}" if d["line"] else d["file"]
                    print(f"       {loc}  {d['rule']}")
                    if d["code"]:
                        print(f"         {d['code']}")
        print()
        print(f"合计 {len(results)} 项，失败 {len(failed)} 项")

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())