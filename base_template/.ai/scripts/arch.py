#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Vibe Coding 3.0 老项目架构切片与拓扑关系图谱分析脚本 (arch.py)
基于路由切入 + 元数据池 + 拓扑关联分析（Obsidian 范式），彻底消除老项目架构安全感假象。
"""

import sys
import os
import re
import json

ARCH_FILE = os.path.join(".ai", "tier2_legacy_arch.md")

IGNORE_DIRS = {
    ".git", ".idea", ".vscode", "node_modules", "dist", "build",
    "target", "__pycache__", ".venv", "env", ".DS_Store"
}

def ensure_arch_file():
    """确保 .ai/tier2_legacy_arch.md 存在并初始化标准元数据池结构"""
    os.makedirs(".ai", exist_ok=True)
    if not os.path.exists(ARCH_FILE):
        header = """# 🏛️ 老项目模块切片与关系图谱元数据池 (Legacy Metadata Graph Pool)

> 本文件为老项目解密的核心元数据池。通过路由切入提取元数据，自动计算模块间的共享数据表与接口交集，生成动态关系图谱。

---

## 🕸️ 全局拓扑关系图谱 (Global Topology Graph)

```mermaid
graph TD
    %% 自动计算的模块拓扑将在此处更新
    START["🚀 架构索引初始化"]
```

---

## 🧩 模块元数据注册池 (Metadata Pool)

"""
        with open(ARCH_FILE, "w", encoding="utf-8") as f:
            f.write(header)

def get_directory_tree(startpath=".", max_depth=3):
    """确定性抓取前 max_depth 层真实目录树"""
    tree_lines = []
    startpath = os.path.abspath(startpath)
    base_depth = startpath.rstrip(os.sep).count(os.sep)

    for root, dirs, files in os.walk(startpath):
        dirs[:] = [d for d in dirs if d not in IGNORE_DIRS and not d.startswith('.')]
        current_depth = root.rstrip(os.sep).count(os.sep) - base_depth
        if current_depth >= max_depth:
            dirs.clear()
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

    return "\n".join(tree_lines[:150])

def find_files_by_keyword(keyword, startpath="."):
    """深度穿透查找包含指定关键词的文件（穿透层级限制）"""
    matched_files = []
    for root, dirs, files in os.walk(startpath):
        dirs[:] = [d for d in dirs if d not in IGNORE_DIRS and not d.startswith('.')]
        for f in files:
            if keyword.lower() in f.lower():
                rel_path = os.path.relpath(os.path.join(root, f), startpath)
                matched_files.append(rel_path)
    return matched_files[:30]

def analyze_metadata_and_register(route, module_name, files_str, tables_str, apis_str):
    """提取模块元数据，计算共享数据库表/API交集，追加至元数据池并生成 Mermaid 图谱"""
    ensure_arch_file()
    
    with open(ARCH_FILE, "r", encoding="utf-8") as f:
        content = f.read()

    files = [f.strip() for f in files_str.split(",") if f.strip()]
    tables = [t.strip() for t.strip().replace("，", ",").split(",") if t.strip()]
    apis = [a.strip() for a in apis_str.split(",") if a.strip()]

    # 1. 查找现有元数据中的数据表交集 (共享表/共享依赖)
    shared_connections = []
    for table in tables:
        if table and table in content:
            # 在内容中寻找提及该表的其它模块
            matches = re.findall(rf"###\s*🧩\s*模块:\s*(.+?)\n.*?数据表.*?:.*?\b{re.escape(table)}\b", content, re.DOTALL)
            for existing_module in matches:
                existing_module = existing_module.strip()
                if existing_module != module_name:
                    shared_connections.append((existing_module, table))

    # 2. 构建本模块的 Mermaid 节点
    safe_mod_id = re.sub(r"[^\w]", "_", module_name)
    mermaid_snippet = f"\n    R_{safe_mod_id}[\"🌐 路由: {route}\"] --> M_{safe_mod_id}[\"📦 模块: {module_name}\"]\n"
    
    for t in tables:
        safe_t_id = re.sub(r"[^\w]", "_", t)
        mermaid_snippet += f"    M_{safe_mod_id} --> T_{safe_t_id}[(\"🗄️ 表: {t}\")]\n"

    # 绘制交叉关联关系
    for ext_mod, shared_t in shared_connections:
        safe_ext_id = re.sub(r"[^\w]", "_", ext_mod)
        safe_t_id = re.sub(r"[^\w]", "_", shared_t)
        mermaid_snippet += f"    M_{safe_ext_id} -. 共享数据表 .-> T_{safe_t_id}\n"

    # 3. 更新 Mermaid 全局图谱
    graph_pattern = r"(```mermaid\s*graph TD)(.*?)(```)"
    if re.search(graph_pattern, content, re.DOTALL):
        content = re.sub(graph_pattern, r"\1\2" + mermaid_snippet + r"\3", content, count=1)

    # 4. 构建模块元数据卡片
    meta_card = f"""
### 🧩 模块: {module_name}
- **路由入口**: `{route}`
- **关键文件**: `{", ".join(files)}`
- **绑定 API**: `{", ".join(apis)}`
- **关联数据表**: `{", ".join(tables)}`
"""
    if shared_connections:
        meta_card += "- **🔗 检测到跨模块共享连接**:\n"
        for ext_mod, shared_t in shared_connections:
            meta_card += f"  - 与 `[{ext_mod}]` 模块共享表 `{shared_t}`\n"

    content += meta_card

    with open(ARCH_FILE, "w", encoding="utf-8") as f:
        f.write(content)

    print(f"✅ 成功完成元数据分析并注册模块 [{module_name}] 到 {ARCH_FILE}！")
    if shared_connections:
        print(f"🔗 发现 {len(shared_connections)} 处跨模块共享连接，拓扑图谱已更新！")

def main():
    if len(sys.argv) < 2:
        print("用法: python .ai/scripts/arch.py [tree|find|analyze]")
        print("  python .ai/scripts/arch.py tree [depth]")
        print("  python .ai/scripts/arch.py find <keyword>")
        print("  python .ai/scripts/arch.py analyze <route> <module_name> <files> <tables> <apis>")
        sys.exit(1)

    cmd = sys.argv[1]
    if cmd == "tree":
        depth = int(sys.argv[2]) if len(sys.argv) > 2 else 3
        print(get_directory_tree(max_depth=depth))
    elif cmd == "find":
        if len(sys.argv) < 3:
            print("错误: 请提供搜索关键词，例如 python arch.py find order")
            sys.exit(1)
        keyword = sys.argv[2]
        results = find_files_by_keyword(keyword)
        print(f"🔍 深度搜索 [{keyword}] 匹配到的文件:")
        for r in results:
            print(f"  📄 {r}")
    elif cmd == "analyze":
        if len(sys.argv) < 6:
            print("错误: 参数不足。用法: python arch.py analyze <route> <module_name> <files> <tables> <apis>")
            sys.exit(1)
        analyze_metadata_and_register(sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5], sys.argv[6])
    else:
        print(f"未知指令: {cmd}")

if __name__ == "__main__":
    main()
