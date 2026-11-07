#!/usr/bin/env python3
"""
Kotlin 静态预检：在没有编译器的情况下抓最常见的写坏方式。

【为什么需要】本机不做 Gradle 构建（由推送后的 CI 编译），所以「写错 import、
少一个括号、注解错位」这类错误要等到 CI 才暴露，一轮 7–8 分钟。它们恰好是
纯文本就能查出来的类型，所以先在本地挡掉。

检查项：
  1. 括号 / 花括号 / 方括号是否平衡（忽略字符串与注释里的）
  2. 块注释是否配对（Kotlin 的 `/* */` 可嵌套，写成单层计数会漏）
  3. 引用了但没 import 的常用 Compose / 项目符号（白名单驱动，宁缺勿滥）
  4. import 了但正文没用到的（只报告，不判失败 —— 多半是无害的冗余）
  5. 明显的 API 误用：`AivoraThemeColors.text`（应为 `.colors.text`）

用法：python3 scripts/kotlin_precheck.py [--quiet]
退出码：0 = 无问题
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC_DIRS = [
    ROOT / "app/src/main/java",
    ROOT / "app/src/test/java",
]

QUIET = "--quiet" in sys.argv
problems: list[str] = []
reports: list[str] = []


def kotlin_files() -> list[Path]:
    out = []
    for d in SRC_DIRS:
        if d.exists():
            out.extend(sorted(d.rglob("*.kt")))
    return out


def strip_non_code(text: str) -> str:
    """
    去掉字符串字面量与注释，只留下「真正的代码」，用于括号平衡判断。

    不追求完美的 Kotlin 词法分析，只要不漏、不过度删除即可。
    """
    out = []
    i = 0
    n = len(text)
    in_string = False
    in_char = False
    in_line_comment = False
    block_depth = 0

    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""

        if in_line_comment:
            if ch == "\n":
                in_line_comment = False
                out.append(" ")
            i += 1
            continue

        if block_depth > 0:
            if ch == "/" and nxt == "*":
                block_depth += 1
                i += 2
                continue
            if ch == "*" and nxt == "/":
                block_depth -= 1
                i += 2
                continue
            if ch == "\n":
                out.append(" ")
            i += 1
            continue

        if in_string:
            if ch == "\\":
                i += 2
                continue
            if ch == '"':
                in_string = False
            if ch == "\n":
                out.append(" ")
            i += 1
            continue

        if in_char:
            if ch == "\\":
                i += 2
                continue
            if ch == "'":
                in_char = False
            i += 1
            continue

        if ch == "/" and nxt == "/":
            in_line_comment = True
            i += 2
            continue
        if ch == "/" and nxt == "*":
            block_depth = 1
            i += 2
            continue
        if ch == '"':
            # 三引号字符串
            if text[i:i + 3] == '"""':
                end = text.find('"""', i + 3)
                if end == -1:
                    break
                i = end + 3
                out.append(" ")
                continue
            in_string = True
            i += 1
            continue
        if ch == "'":
            in_char = True
            i += 1
            continue

        out.append(ch)
        i += 1

    return "".join(out)


def check_balance(path: Path) -> None:
    rel = path.relative_to(ROOT)
    code = strip_non_code(path.read_text(encoding="utf-8"))
    for open_c, close_c, label in (("(", ")", "圆括号"), ("{", "}", "花括号"), ("[", "]", "方括号")):
        o, c = code.count(open_c), code.count(close_c)
        if o != c:
            problems.append(f"{rel}: {label}不平衡 {open_c}={o} {close_c}={c}")


def check_block_comments(path: Path) -> None:
    rel = path.relative_to(ROOT)
    text = path.read_text(encoding="utf-8")
    # 逐字符扫，支持嵌套
    depth = 0
    i = 0
    n = len(text)
    in_string = False
    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if in_string:
            if ch == "\\":
                i += 2
                continue
            if ch == '"':
                in_string = False
            i += 1
            continue
        if text[i:i + 3] == '"""':
            end = text.find('"""', i + 3)
            i = (end + 3) if end != -1 else n
            continue
        if ch == '"':
            in_string = True
            i += 1
            continue
        if ch == "/" and nxt == "*":
            depth += 1
            i += 2
            continue
        if ch == "*" and nxt == "/":
            depth -= 1
            if depth < 0:
                problems.append(f"{rel}: 块注释 `*/` 多于 `/*`")
                return
            i += 2
            continue
        i += 1
    if depth != 0:
        problems.append(f"{rel}: 块注释未闭合（剩余嵌套深度 {depth}）")



# 必须带括号调用的作用域扩展函数：`{ items -> }` 这类同名 lambda 参数不算引用。
LAZY_SCOPE_CALLS = {"items", "itemsIndexed", "stickyHeader"}

# 常见「用了但没 import」的符号 → 需要的 import 前缀候选。
# 只列会导致编译失败的强符号，避免误报拖累信噪比。
SYMBOL_IMPORTS = {
    "LazyColumn": ["androidx.compose.foundation.lazy.LazyColumn"],
    "LazyRow": ["androidx.compose.foundation.lazy.LazyRow"],
    "rememberLazyListState": ["androidx.compose.foundation.lazy.rememberLazyListState"],
    "HorizontalDivider": ["androidx.compose.material3.HorizontalDivider"],
    "ModalBottomSheet": ["androidx.compose.material3.ModalBottomSheet"],
    "rememberModalBottomSheetState": ["androidx.compose.material3.rememberModalBottomSheetState"],
    "BottomSheetDefaults": ["androidx.compose.material3.BottomSheetDefaults"],
    "AlertDialog": ["androidx.compose.material3.AlertDialog"],
    "RadioButton": ["androidx.compose.material3.RadioButton"],
    "Checkbox": ["androidx.compose.material3.Checkbox"],
    "TriStateCheckbox": ["androidx.compose.material3.TriStateCheckbox"],
    "OutlinedTextField": ["androidx.compose.material3.OutlinedTextField"],
    "OutlinedTextFieldDefaults": ["androidx.compose.material3.OutlinedTextFieldDefaults"],
    "LinearProgressIndicator": ["androidx.compose.material3.LinearProgressIndicator"],
    "CircularProgressIndicator": ["androidx.compose.material3.CircularProgressIndicator"],
    "DropdownMenu": ["androidx.compose.material3.DropdownMenu"],
    "DropdownMenuItem": ["androidx.compose.material3.DropdownMenuItem"],
    "ExposedDropdownMenuBox": ["androidx.compose.material3.ExposedDropdownMenuBox"],
    "ExposedDropdownMenuDefaults": ["androidx.compose.material3.ExposedDropdownMenuDefaults"],
    "menuAnchor": ["androidx.compose.material3.MenuAnchorType"],
    "SnackbarHost": ["androidx.compose.material3.SnackbarHost"],
    "SnackbarHostState": ["androidx.compose.material3.SnackbarHostState"],
    "Text": ["androidx.compose.material3.Text"],
    "Icon": ["androidx.compose.material3.Icon"],
    "Surface": ["androidx.compose.material3.Surface"],
    "MaterialTheme": ["androidx.compose.material3.MaterialTheme"],
    "SelectionContainer": ["androidx.compose.foundation.text.selection.SelectionContainer"],
    "PasswordVisualTransformation": ["androidx.compose.ui.text.input.PasswordVisualTransformation"],
    "VisualTransformation": ["androidx.compose.ui.text.input.VisualTransformation"],
    "AnnotatedString": ["androidx.compose.ui.text.AnnotatedString"],
    "TextOverflow": ["androidx.compose.ui.text.style.TextOverflow"],
    "TextAlign": ["androidx.compose.ui.text.style.TextAlign"],
    "FontWeight": ["androidx.compose.ui.text.font.FontWeight"],
    "FontFamily": ["androidx.compose.ui.text.font.FontFamily"],
    "ImageVector": ["androidx.compose.ui.graphics.vector.ImageVector"],
    "Color": ["androidx.compose.ui.graphics.Color"],
    "Shape": ["androidx.compose.ui.graphics.Shape"],
    "Alignment": ["androidx.compose.ui.Alignment"],
    "Modifier": ["androidx.compose.ui.Modifier"],
    "Arrangement": ["androidx.compose.foundation.layout.Arrangement"],
    "RoundedCornerShape": ["androidx.compose.foundation.shape.RoundedCornerShape"],
    "combinedClickable": ["androidx.compose.foundation.combinedClickable"],
    "verticalScroll": ["androidx.compose.foundation.verticalScroll"],
    "rememberScrollState": ["androidx.compose.foundation.rememberScrollState"],
    "animateItem": [],
    "rotate": ["androidx.compose.ui.draw.rotate"],
    "graphicsLayer": ["androidx.compose.ui.graphics.graphicsLayer"],
    "Brush": ["androidx.compose.ui.graphics.Brush"],
    "rememberSaveable": ["androidx.compose.runtime.saveable.rememberSaveable"],
    "Saver": ["androidx.compose.runtime.saveable.Saver"],
    "LaunchedEffect": ["androidx.compose.runtime.LaunchedEffect"],
    "CompositionLocalProvider": ["androidx.compose.runtime.CompositionLocalProvider"],
    "staticCompositionLocalOf": ["androidx.compose.runtime.staticCompositionLocalOf"],
    "DisposableEffect": ["androidx.compose.runtime.DisposableEffect"],
    "SideEffect": ["androidx.compose.runtime.SideEffect"],
    "mutableStateOf": ["androidx.compose.runtime.mutableStateOf"],
    "rememberCoroutineScope": ["androidx.compose.runtime.rememberCoroutineScope"],
    "collectAsState": ["androidx.compose.runtime.collectAsState"],
    "LocalContext": ["androidx.compose.ui.platform.LocalContext"],
    "LocalClipboardManager": ["androidx.compose.ui.platform.LocalClipboardManager"],
    "LocalUriHandler": ["androidx.compose.ui.platform.LocalUriHandler"],
    "LocalView": ["androidx.compose.ui.platform.LocalView"],
    "LocalHapticFeedback": ["androidx.compose.ui.hapticfeedback.LocalHapticFeedback"],
    "windowInsetsPadding": ["androidx.compose.foundation.layout.windowInsetsPadding"],
    "navigationBarsPadding": ["androidx.compose.foundation.layout.navigationBarsPadding"],
    "imePadding": ["androidx.compose.foundation.layout.imePadding"],
    "fillMaxWidth": ["androidx.compose.foundation.layout.fillMaxWidth"],
    "fillMaxSize": ["androidx.compose.foundation.layout.fillMaxSize"],
    "heightIn": ["androidx.compose.foundation.layout.heightIn"],
    "PaddingValues": ["androidx.compose.foundation.layout.PaddingValues"],
    "ColumnScope": ["androidx.compose.foundation.layout.ColumnScope"],
    "RowScope": ["androidx.compose.foundation.layout.RowScope"],
    "dp": ["androidx.compose.ui.unit.dp"],
    "sp": ["androidx.compose.ui.unit.sp"],
    "Dp": ["androidx.compose.ui.unit.Dp"],
    "DateFormat": ["java.text.DateFormat"],
    "Date": ["java.util.Date"],
    "Job": ["kotlinx.coroutines.Job"],
    "Uri": ["android.net.Uri"],
    "Toast": ["android.widget.Toast"],
}


def check_missing_imports(path: Path) -> None:
    rel = path.relative_to(ROOT)
    text = path.read_text(encoding="utf-8")
    code = strip_non_code(text)

    # 收集该文件所有 import 的末段名（`import a.b.C` → C；`import a.b.*` → *）
    imports = set()
    wildcard = False
    for m in re.finditer(r"^import\s+([\w.*]+)(?:\s+as\s+(\w+))?$", text, re.M):
        full, alias = m.group(1), m.group(2)
        if full.endswith("*"):
            wildcard = True
        else:
            imports.add(alias or full.split(".")[-1])

    if wildcard:
        # 通配 import 太多，跳过（星号导入的文件里漏报风险极低）
        return

    own_package = re.search(r"^package\s+([\w.]+)$", text, re.M)
    own_names = set()
    if own_package:
        # 同包内的符号不需要 import —— 通过扫描同目录其它文件的顶层声明近似判断。
        pkg_dir = path.parent
        for other in pkg_dir.glob("*.kt"):
            if other == path:
                continue
            for m in re.finditer(r"^(?:@\w+\s+)*(?:public\s+|internal\s+|private\s+)?(?:fun|val|var|class|object|interface|enum class)\s+(\w+)", other.read_text(encoding="utf-8"), re.M):
                own_names.add(m.group(1))

    for symbol, candidates in SYMBOL_IMPORTS.items():
        if not candidates:
            continue
        if symbol in imports or symbol in own_names:
            continue
        # 只有当符号真的被「当作标识符调用/引用」时才报。手工做边界判断，
        # 不用正则 —— 要排除的假阳性类型（命名参数、成员访问、字符串内容）
        # 用正则表达会撞上反斜杠转义，反而更难维护。
        idx = 0
        while True:
            idx = code.find(symbol, idx)
            if idx == -1:
                break
            start, end = idx, idx + len(symbol)

            prev_char = code[start - 1] if start > 0 else ""
            next_char = code[end] if end < len(code) else ""

            # 前一个字符是标识符 / 点 / 引号 → 不是顶层引用
            if prev_char.isalnum() or prev_char in "_.\"'@":
                idx = end
                continue
            # 后一个字符是标识符 / 引号 → 是更长名字或字符串的一部分
            if next_char.isalnum() or next_char in "_\"":
                idx = end
                continue

            # 向后跳过空白
            j = end
            while j < len(code) and code[j] in " \t\n":
                j += 1
            if j < len(code) and code[j] == "=":
                # 命名参数 / 赋值：`background = ...`
                idx = end
                continue
            # 对 LazyListScope 扩展（items / itemsIndexed）这类必须带括号调用的符号，
            # 只有紧跟 `(` 才算真的用到了；lambda 参数名 `{ items -> }` 不算。
            if symbol in LAZY_SCOPE_CALLS and (j >= len(code) or code[j] != "("):
                idx = end
                continue

            problems.append(
                f"{rel}: 使用了 `{symbol}` 但未 import（可能需要 {candidates[0]}）"
            )
            break


def check_common_mistakes(path: Path) -> None:
    rel = path.relative_to(ROOT)
    text = path.read_text(encoding="utf-8")
    if re.search(r"AivoraThemeColors\.(?!colors)\w", text):
        problems.append(f"{rel}: `AivoraThemeColors.xxx` 应为 `AivoraThemeColors.colors.xxx`")
    # 只有「同一个 Composable 函数体内」同时出现才是真问题：
    # 跨函数的共存是合法的（例如预览用 LazyColumn、凭证输入用 verticalScroll）。
    # 判据：找出每个 fun 的区间，看谁的区间里两者都有。
    for m in re.finditer(r"^\s*(?:@\w+\s*)*fun\s+\w+", text, re.M):
        start = m.start()
        depth, i = 0, m.end()
        while i < len(text) and not (text[i] == "{" and depth == 0):
            i += 1
        if i >= len(text):
            continue
        depth = 0
        j = i
        while j < len(text):
            if text[j] == "{":
                depth += 1
            elif text[j] == "}":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        body = text[i:j]
        if "verticalScroll" in body and "LazyColumn" in body:
            line = text.count("\n", 0, start) + 1
            problems.append(f"{rel}:{line}: 同一函数内 verticalScroll 套 LazyColumn（运行期会崩：无限高度）")


def check_unused_imports(path: Path) -> None:
    rel = path.relative_to(ROOT)
    text = path.read_text(encoding="utf-8")
    code = strip_non_code(text)
    for m in re.finditer(r"^import\s+([\w.*]+)(?:\s+as\s+(\w+))?$", text, re.M):
        full, alias = m.group(1), m.group(2)
        if full.endswith("*"):
            continue
        name = alias or full.split(".")[-1]
        if not re.search(rf"(?<![\w.]){re.escape(name)}(?![\w])", code):
            reports.append(f"{rel}: import `{full}` 未被使用")


def main() -> int:
    files = kotlin_files()
    if not files:
        print("找不到 Kotlin 源文件", file=sys.stderr)
        return 2

    for path in files:
        check_balance(path)
        check_block_comments(path)
        check_missing_imports(path)
        check_common_mistakes(path)
        check_unused_imports(path)

    if not QUIET:
        for r in reports:
            print(f"NOTE {r}")

    for p in problems:
        print(f"FAIL {p}")

    print()
    print(f"扫描 {len(files)} 个文件，硬问题 {len(problems)} 个，提示 {len(reports)} 个")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())