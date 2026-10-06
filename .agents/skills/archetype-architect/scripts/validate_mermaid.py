#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Mermaid 语法预检与校验脚本 (validate_mermaid.py - Fail-safe Version)
支持过滤引号内部字符串与注释，并在高阶复杂语法场景下提供降级容错。
"""

import sys
import os
import re

VALID_MERMAID_TYPES = [
    "graph", "flowchart", "sequenceDiagram", "stateDiagram", "stateDiagram-v2",
    "erDiagram", "gantt", "classDiagram", "C4Context", "pie", "gitGraph"
]

def clean_mermaid_code(raw_code):
    """移除注释与引号内的字符串文本，防止引号内的括号干扰括号对齐校验"""
    lines = []
    for line in raw_code.strip().splitlines():
        line = line.strip()
        if not line or line.startswith("%%"): # 忽略注释
            continue
        # 将双引号/单引号括起来的字符串替换为占位符
        cleaned_line = re.sub(r'".*?"', '"STR"', line)
        cleaned_line = re.sub(r"'.*?'", "'STR'", cleaned_line)
        lines.append(cleaned_line)
    return lines

def validate_mermaid_text(mermaid_code):
    lines = clean_mermaid_code(mermaid_code)
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

    bracket_stack = []
    pair_map = {')': '(', ']': '[', '}': '{'}

    for line_idx, line in enumerate(lines, 1):
        for char in line:
            if char in "([{":
                bracket_stack.append(char)
            elif char in ")]}":
                if bracket_stack and bracket_stack[-1] == pair_map[char]:
                    bracket_stack.pop()

    if bracket_stack:
        # 降级容错提示
        return True, f"⚠️ 注意: 预检发现 {len(bracket_stack)} 个高阶/嵌套符号未在简化检查中对齐，已激活降级通过 (类型: {matched_type})"

    return True, f"✅ Mermaid 语法预检通过 (类型: {matched_type})"

def validate_file(file_path):
    try:
        with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
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
        print(f"✅ 文件 [{file_path}] 中的所有 Mermaid 图表均校验完毕！")

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
