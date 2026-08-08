#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Vibe Coding 3.0 老项目架构切片与目录树确定性控制脚本 (arch.py)
用于 100% 确定性地抓取前 N 层真实目录树、解析包依赖并安全追加 .ai/legacy_arch.md 索引，防止 AI 产生目录幻觉。
"""

import sys
import os
import re
import json

ARCH_FILE = os.path.join(".ai", "legacy_arch.md")

IGNORE_DIRS = {
    ".git", ".idea", ".vscode", "node_modules", "dist", "build",
    "target", "__pycache__", ".venv", "env", ".DS_Store"
}

def get_directory_tree(startpath=".", max_depth=3):
    """确定性抓取前 max_depth 层真实目录树，跳过无关重型目录"""
    tree_lines = []
    startpath = os.path.abspath(startpath)
    base_depth = startpath.rstrip(os.sep).count(os.sep)

    for root, dirs, files in os.walk(startpath):
        # 过滤忽略的目录
        dirs[:] = [d for d in dirs if d not in IGNORE_DIRS and not d.startswith('.')]
        
        current_depth = root.rstrip(os.sep).count(os.sep) - base_depth
        if current_depth >= max_depth:
            dirs.clear() # 不再递归更深层
            continue

        indent = "│   " * current_depth
        folder = os.path.basename(root)
        if current_depth == 0:
            tree_lines.append(f"📁 {folder}/")
        else:
            tree_lines.append(f"{indent}├── 📁 {folder}/")

        if current_depth < max_depth - 1:
            sub_indent = "│   " * (current_depth + 1)
            for f in sorted(files):
                if not f.startswith('.') and not f.endswith(('.pyc', '.log', '.tmp')):
                    tree_lines.append(f"{sub_indent}├── 📄 {f}")

    return "\n".join(tree_lines[:150]) # 限制前 150 行防止文本过长

def parse_dependencies():
    """确定性检测并提取常见包依赖文件"""
    info = []
    
    # 1. Node.js package.json
    if os.path.exists("package.json"):
        try:
            with open("package.json", "r", encoding="utf-8") as f:
                data = json.load(f)
                deps = list(data.get("dependencies", {}).keys())[:10]
                info.append(f"- **Node.js**: {data.get('name', 'project')} (Dependencies: {', '.join(deps)})")
        except Exception:
            pass

    # 2. Java pom.xml
    if os.path.exists("pom.xml"):
        info.append("- **Java Maven**: 包含 pom.xml 构建配置")

    # 3. Go go.mod
    if os.path.exists("go.mod"):
        info.append("- **Go Module**: 包含 go.mod 依赖配置")

    # 4. Python requirements.txt
    if os.path.exists("requirements.txt"):
        info.append("- **Python**: 包含 requirements.txt 依赖配置")

    return "\n".join(info) if info else "未检测到标准包依赖配置文件"

def append_legacy_slice(module_name, files, description):
    """确定性地追加模块切片到 .ai/legacy_arch.md"""
    if not os.path.exists(ARCH_FILE):
        print(f"❌ 找不到 {ARCH_FILE}，请先初始化老项目架构索引。")
        return

    slice_block = f"\n### 🧩 模块切片: {module_name}\n- **关键文件**: `{files}`\n- **业务与架构说明**: {description}\n"
    
    with open(ARCH_FILE, "a", encoding="utf-8") as f:
        f.write(slice_block)
    
    print(f"✅ 成功将模块切片 [{module_name}] 追加写入 {ARCH_FILE}！")

def main():
    if len(sys.argv) < 2:
        print("用法: python .ai/scripts/arch.py [tree|deps|append]")
        sys.exit(1)

    cmd = sys.argv[1]
    if cmd == "tree":
        depth = int(sys.argv[2]) if len(sys.argv) > 2 else 3
        print(get_directory_tree(max_depth=depth))
    elif cmd == "deps":
        print(parse_dependencies())
    elif cmd == "append":
        if len(sys.argv) < 5:
            print("用法: python .ai/scripts/arch.py append <模块名> <关联文件> <说明>")
            sys.exit(1)
        append_legacy_slice(sys.argv[2], sys.argv[3], sys.argv[4])

if __name__ == "__main__":
    main()
