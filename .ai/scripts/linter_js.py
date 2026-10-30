#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Apex 可插拔 JS/TS/Vue Linter (linter_js.py)
==============================================
gate.py 会自动调用（`.js` / `.ts` / `.tsx` / `.jsx` / `.vue` 共用本规则集）：
    python3 .ai/scripts/linter_js.py <file1> <file2> ...

零依赖（纯文本规则，不依赖 eslint 是否安装）。检查两类：

  错误级（E，退出码 1，阻断提交）
    E001  debugger               —— 提交前必须删除，否则线上会真的断住
    E002  eval() / new Function() —— 代码注入风险
    E003  v-html / innerHTML=     —— XSS 风险（必须确认内容已转义）

  警告级（W，不阻断）
    W001  console.log 等调试输出（按文件聚合）
    W002  == / !=（应使用 === / !==）
    W003  var 声明（应使用 let / const）
    W004  行宽 > 160
    W005  TODO / FIXME 悬空

用法：
    python3 .ai/scripts/linter_js.py a.ts b.vue
"""

import re
import sys
import os

MAX_LINE_WIDTH = 160

E_PATTERNS = [
    (re.compile(r"\bdebugger\b"), "E001", "debugger 语句必须删除（会真的断住执行）"),
    (re.compile(r"\beval\s*\("), "E002", "eval() 存在代码注入风险"),
    (re.compile(r"\bnew\s+Function\s*\("), "E002", "new Function() 等价于 eval，存在注入风险"),
    (re.compile(r"\bv-html\b"), "E003", "v-html 会绕过转义，确认内容可信或改用插值"),
    (re.compile(r"\.innerHTML\s*="), "E003", "innerHTML 赋值存在 XSS 风险，优先 textContent"),
]

W_PATTERNS = [
    (re.compile(r"console\.(log|debug|info)\s*\("), "W001", "console 调试输出"),
    (re.compile(r"(?<![=!<>])=(?!=)"), None, None),  # 占位，见下方专门判断
    (re.compile(r"\bvar\s+[A-Za-z_$]"), "W003", "使用 var，建议 let/const"),
]

EQ_PATTERN = re.compile(r"(?<![=!<>+\-*/])(==|!=)(?!=)")


def strip_comment(line):
    """粗剥行注释（保留 http:// 这类），避免注释里的词误报。"""
    m = re.search(r"(?<!:)//", line)
    if m:
        return line[:m.start()]
    return line


def check_file(path):
    try:
        with open(path, "r", encoding="utf-8") as f:
            src = f.read()
    except Exception as e:
        warnings.append(f"⚠️  {os.path.basename(path)} [W000] 无法读取: {e}")
        return

    name = os.path.basename(path)
    console_hits = []

    for lineno, raw in enumerate(src.splitlines(), 1):
        line = strip_comment(raw)
        if not line.strip():
            continue

        if len(raw) > MAX_LINE_WIDTH:
            warnings.append(f"⚠️  {name}:{lineno} [W004] 行宽 {len(raw)} > {MAX_LINE_WIDTH}")

        if "TODO" in line or "FIXME" in line:
            warnings.append(f"⚠️  {name}:{lineno} [W005] 悬空标记: {line.strip()[:60]}")

        for pat, code, msg in E_PATTERNS:
            if pat.search(line):
                errors.append(f"🔴 {name}:{lineno} [{code}] {msg}：{line.strip()[:70]}")

        for pat, code, msg in W_PATTERNS:
            if code is None or not pat.search(line):
                continue
            if code == "W001":
                console_hits.append(lineno)
            else:
                warnings.append(f"⚠️  {name}:{lineno} [{code}] {msg}：{line.strip()[:60]}")

        if EQ_PATTERN.search(line):
            warnings.append(f"⚠️  {name}:{lineno} [W002] 使用 == / !=，建议 === / !==：{line.strip()[:60]}")

    if console_hits:
        warnings.append(f"⚠️  {name}:{console_hits[0]} [W001] 共 {len(console_hits)} 处 console 调试输出"
                        f"（首处行 {console_hits[0]}）")


errors = []
warnings = []


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if not args:
        print("用法: python3 .ai/scripts/linter_js.py <file...>")
        return 2

    exts = {".js", ".ts", ".tsx", ".jsx", ".vue"}
    targets = [a for a in args if os.path.splitext(a)[1] in exts and os.path.isfile(a)]
    for p in targets:
        check_file(p)

    for line in errors:
        print(line)
    for line in warnings[:40]:
        print(line)
    if len(warnings) > 40:
        print(f"… 另有 {len(warnings) - 40} 条警告未显示")

    print(f"\n📊 检查 {len(targets)} 个前端文件：错误 {len(errors)} 个 / 警告 {len(warnings)} 个")
    if errors:
        print("🚫 存在错误级问题，lint 未通过")
        return 1
    print("✅ lint 通过（警告不阻断）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
