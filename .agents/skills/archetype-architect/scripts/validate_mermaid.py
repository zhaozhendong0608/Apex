#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Mermaid 语法预检与校验脚本 (validate_mermaid.py)
用于在落盘 PRD 文档前自动校验 Mermaid 代码块的语法合规性。
"""

import sys
import re

VALID_MERMAID_TYPES = [
    "graph", "flowchart", "sequenceDiagram", "stateDiagram", "stateDiagram-v2",
    "erDiagram", "gantt", "classDiagram", "C4Context"
]

def validate_mermaid_text(mermaid_code):
    lines = [line.strip() for line in mermaid_code.strip().splitlines() if line.strip()]
    if not lines:
        return False, "❌ Mermaid 代码为空"

    first_line = lines[0]
    matched_type = None
    for m_type in VALID_MERMAID_TYPES:
        if first_line.startswith(m_type):
            matched_type = m_type
            break

    if not matched_type:
        return False, f"❌ 未能识别合规的 Mermaid 图表类型，首行为: {first_line}"

    # 括号与括号对称检查
    bracket_stack = []
    pair_map = {')': '(', ']': '[', '}': '{'}

    for line_idx, line in enumerate(lines, 1):
        for char in line:
            if char in "([{":
                bracket_stack.append(char)
            elif char in ")]}":
                if not bracket_stack or bracket_stack[-1] != pair_map[char]:
                    return False, f"❌ 第 {line_idx} 行存在未对齐或不匹配的括号: '{char}'"
                bracket_stack.pop()

    if bracket_stack:
        return False, "❌ Mermaid 代码中存在未闭合的括号"

    return True, f"✅ Mermaid 语法预检通过 (类型: {matched_type})"

def validate_file(file_path):
    try:
        with open(file_path, "r", encoding="utf-8") as f:
            content = f.read()
    except Exception as e:
        print(f"❌ 读取文件失败: {e}")
        return

    blocks = re.findall(r"```mermaid\s*\n(.*?)\n```", content, re.DOTALL)
    if not blocks:
        print(f"ℹ️ [{file_path}] 中未找到 ```mermaid 代码块")
        return

    all_passed = True
    for idx, block in enumerate(blocks, 1):
        ok, msg = validate_mermaid_text(block)
        if ok:
            print(f"🟢 Mermaid 图表 #{idx}: {msg}")
        else:
            print(f"🔴 Mermaid 图表 #{idx}: {msg}")
            all_passed = False

    if all_passed:
        print(f"✅ 文件 [{file_path}] 中的所有 Mermaid 图表均符合语法规范！")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("用法: python3 validate_mermaid.py <path_to_md_file_or_code>")
        sys.exit(1)

    arg = sys.argv[1]
    if os.path.exists(arg):
        validate_file(arg)
    else:
        ok, msg = validate_mermaid_text(arg)
        print(msg)
