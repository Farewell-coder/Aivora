#!/usr/bin/env bash
# Aivora 本地质量门禁：在没有 Gradle 编译的情况下先挡住最常见的问题。
#
# 为什么只做静态检查：本机不做构建（编译交给推送后的 CI），
# 但「漏 import / 括号不平衡 / 样式裸值 / 配色对比度退化」这四类问题
# 恰好都能纯文本判定，先在本地挡掉能省掉一轮 7–8 分钟的 CI 往返。
#
# 用法：bash scripts/gate.sh [--quiet]
# 退出码：全部通过 = 0

set -u
cd "$(dirname "$0")/.." || exit 2

QUIET=""
[ "${1:-}" = "--quiet" ] && QUIET="--quiet"

FAILED=0

echo "── 1/4 UI 一致性 ─────────────────────────────────"
if python3 scripts/ui_audit.py $QUIET; then :; else FAILED=1; fi

echo
echo "── 2/4 Kotlin 静态预检 ───────────────────────────"
if python3 scripts/kotlin_precheck.py $QUIET; then :; else FAILED=1; fi

echo
echo "── 3/4 配色对比度（纯 Python 复算） ──────────────"
if python3 scripts/palette_contrast.py $QUIET; then :; else FAILED=1; fi

echo
if [ "$FAILED" -eq 0 ]; then
  echo "✅ 门禁全部通过"
else
  echo "❌ 门禁未通过，见上面的 FAIL 项"
fi
exit "$FAILED"
