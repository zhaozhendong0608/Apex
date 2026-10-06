#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Apex 目录树与 Skill 路由自动同步脚本 (sync_tree.py)
1. 动态提取 YAML Frontmatter (summary / description) 自动生成带自解释注释的真实目录树。
2. 动态扫描 `.agents/skills/` 下所有 SKILL.md，自动提取技能路由并写回 `.cursorrules`、`.windsurfrules` 与 `README.md`。
3. 支持 --watch 原生事件监听模式与强力指纹轮询模式。
"""

import os
import sys
import re
import time

IGNORE_DIRS = {
    ".git", ".idea", ".vscode", "node_modules", "dist", "build",
    "target", "__pycache__", ".venv", "env", ".DS_Store"
}

EMOJI_MAP = {
    ".ai": "🧠",
    ".agents": "🧩",
    ".cursorrules": "🧠",
    ".windsurfrules": "🧠",
    "WORKFLOW_GUIDE.md": "📖",
    "README.md": "📖",
    "base_template": "💻",
    "docs": "📁",
    "design": "🎨",
    "modules": "📦",
    "templates": "📁",
    "src": "💻",
    "sop": "📜",
    "scripts": "🛠️",
}

DEFAULT_DIR_DESCS = {
    ".ai": "⚙️ 统一工作流控制中心 (三层记忆金字塔)",
    ".agents": "🧩 AI 专属技能包库",
    "scripts": "🛠️ 自动化控制与分析脚本库",
    "sop": "📜 全套 00~06 数字 SOP 规则矩阵",
    "base_template": "💻 待开发业务工程 (脚手架)",
    "docs": "📁 本工程 PRD / 架构 / 设计文档",
    "design": "🎨 UI 视觉规范与设计 Token",
    "modules": "📦 业务大模块 PRD 与交互原型",
    "templates": "📁 文档与原型外壳模板",
}

def extract_meta_from_file(file_path):
    """从 Markdown 文件中动态提取 summary/description 或首行标题描述"""
    if not os.path.exists(file_path) or not file_path.endswith('.md'):
        return ""

    try:
        with open(file_path, "r", encoding="utf-8") as f:
            content = f.read()
    except Exception:
        return ""

    # 1. 尝试解析 YAML Frontmatter (--- ... ---)
    yaml_match = re.search(r"^---\s*\n(.*?)\n---", content, re.DOTALL)
    if yaml_match:
        yaml_text = yaml_match.group(1)
        summary_match = re.search(r"^(?:summary|description|name):\s*(.+)$", yaml_text, re.MULTILINE)
        desc_match = re.search(r"^description:\s*(.+)$", yaml_text, re.MULTILINE)
        if desc_match:
            return desc_match.group(1).strip('"\' ')
        elif summary_match:
            return summary_match.group(1).strip('"\' ')

    # 2. 尝试解析 Heading 1 (# ... )
    lines = [line.strip() for line in content.splitlines() if line.strip()]
    for line in lines[:5]:
        if line.startswith("# "):
            title = line.lstrip("# ").strip()
            clean_title = re.sub(r"^[\#\s\w\:\-─\🚀\🏁\📦\⚡\🏛️\🎯\💻\🐞\🛟\📖\🎨\🧩]+", "", title).strip()
            return clean_title if clean_title else title

    return ""

def scan_skills_registry():
    """扫描 .agents/skills/ 下所有 SKILL.md，提取 Skill 路由元数据"""
    skills_dir = os.path.join(".agents", "skills")
    if not os.path.exists(skills_dir):
        return []

    skills_list = []
    for item in sorted(os.listdir(skills_dir)):
        skill_path = os.path.join(skills_dir, item)
        skill_md = os.path.join(skill_path, "SKILL.md")
        if os.path.isdir(skill_path) and os.path.exists(skill_md):
            try:
                with open(skill_md, "r", encoding="utf-8") as f:
                    text = f.read()
                name_match = re.search(r"^name:\s*(.+)$", text, re.MULTILINE)
                desc_match = re.search(r"^description:\s*(.+)$", text, re.MULTILINE)
                
                skill_name = name_match.group(1).strip('"\' ') if name_match else item
                skill_desc = desc_match.group(1).strip('"\' ') if desc_match else "未命名技能"
                skills_list.append({
                    "name": skill_name,
                    "desc": skill_desc,
                    "path": f".agents/skills/{item}/SKILL.md"
                })
            except Exception as e:
                print(f"⚠️ 解析 Skill [{item}] 失败: {e}")

    return skills_list

def generate_skills_router_text(skills_list):
    """将技能元数据列表格式化为 Markdown 路由条目"""
    if not skills_list:
        return "- （无可用 Skill 注册包）"
    
    lines = []
    for s in skills_list:
        lines.append(f"- **`{s['name']}`**：{s['desc']}")
    return "\n".join(lines)

def get_node_info(path):
    name = os.path.basename(path)
    is_dir = os.path.isdir(path)
    icon = EMOJI_MAP.get(name, "📁" if is_dir else "📄")

    label = f"{icon} {name}/" if is_dir else f"{icon} {name}"
    desc = ""

    if is_dir:
        readme_path = os.path.join(path, "README.md")
        if os.path.exists(readme_path):
            desc = extract_meta_from_file(readme_path)
        if not desc:
            desc = DEFAULT_DIR_DESCS.get(name, "")
    else:
        if name == "README.md":
            desc = "📖 项目门面与使用指南"
        elif name == "WORKFLOW_GUIDE.md":
            desc = "📖 完整工作流与设计指南"
        elif name == ".cursorrules":
            desc = "🧠 AI 行为约束与数字路由表"
        elif name == ".windsurfrules":
            desc = "🧠 IDE 规则适配文件"
        elif name.endswith(".md"):
            desc = extract_meta_from_file(path)
        elif name == "board.py":
            desc = "📊 确定性看板与任务状态控制脚本"
        elif name == "arch.py":
            desc = "🏛️ 老项目拓扑图谱分析脚本"
        elif name == "sync_tree.py":
            desc = "🔄 目录树与 Skill 路由自动同步脚本"

    return label, desc

def generate_custom_tree(max_depth=3):
    tree_items = []
    workspace_name = os.path.basename(os.getcwd())
    workspace_desc = "🚀 顶层工作区 (IDE 打开的根目录)"
    tree_items.append((f"{workspace_name}/", workspace_desc))

    def walk_dir(path, depth, prefix=""):
        if depth > max_depth:
            return
        try:
            entries = os.listdir(path)
        except OSError:
            return

        filtered = []
        for e in sorted(entries):
            if e in IGNORE_DIRS or e.startswith('.DS_Store'):
                continue
            if e.startswith('.') and e not in {".ai", ".agents", ".cursorrules", ".windsurfrules"}:
                continue
            filtered.append(e)

        for i, entry in enumerate(filtered):
            is_last = (i == len(filtered) - 1)
            full_path = os.path.join(path, entry)
            is_dir = os.path.isdir(full_path)

            branch = "└── " if is_last else "├── "
            label, desc = get_node_info(full_path)
            node_str = f"{prefix}{branch}{label}"

            tree_items.append((node_str, desc))

            if is_dir and depth < max_depth:
                new_prefix = prefix + ("    " if is_last else "│   ")
                walk_dir(full_path, depth + 1, new_prefix)

    walk_dir(".", 1)

    max_len = max(len(item[0]) for item in tree_items) + 2
    max_len = max(max_len, 38)

    formatted_lines = []
    for node_str, desc in tree_items:
        if desc:
            padding = " " * (max_len - len(node_str))
            formatted_lines.append(f"{node_str}{padding}# {desc}")
        else:
            formatted_lines.append(node_str)

    return "\n".join(formatted_lines)

def update_file_anchor(file_path, anchor_tag, new_content, is_codeblock=True):
    """替换目标 Markdown 文件中的锚点内容"""
    if not os.path.exists(file_path):
        return False

    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    start_pattern = f"<!-- {anchor_tag}:START -->"
    end_pattern = f"<!-- {anchor_tag}:END -->"

    pattern = re.compile(
        re.escape(start_pattern) + r".*?" + re.escape(end_pattern),
        re.DOTALL
    )

    if is_codeblock:
        replacement = f"{start_pattern}\n```plaintext\n{new_content}\n```\n{end_pattern}"
    else:
        replacement = f"{start_pattern}\n{new_content}\n{end_pattern}"

    if pattern.search(content):
        updated_content = pattern.sub(replacement, content)
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(updated_content)
        print(f"🟢 已成功刷新 [{file_path}] 的 {anchor_tag} 动态锚点！")
        return True
    else:
        return False

def sync_all():
    """刷新所有文档中的树锚点与 Skills 路由表"""
    print("🔄 开始动态扫描文件、YAML 元数据并同步目录树与 Skill 路由表...")
    
    # 1. 刷新目录树
    macro_tree = generate_custom_tree(max_depth=2)
    full_tree = generate_custom_tree(max_depth=4)
    update_file_anchor("README.md", "AUTO-TREE-MACRO", macro_tree, is_codeblock=True)
    update_file_anchor("WORKFLOW_GUIDE.md", "AUTO-TREE-FULL", full_tree, is_codeblock=True)

    # 2. 刷新 Skills 路由表
    skills_list = scan_skills_registry()
    skills_text = generate_skills_router_text(skills_list)
    
    update_file_anchor(".cursorrules", "AUTO-SKILLS-MACRO", skills_text, is_codeblock=False)
    update_file_anchor(".windsurfrules", "AUTO-SKILLS-MACRO", skills_text, is_codeblock=False)
    update_file_anchor("README.md", "AUTO-SKILLS-MACRO", skills_text, is_codeblock=False)

def get_workspace_fingerprint():
    state = []
    for root, dirs, files in os.walk("."):
        dirs[:] = [d for d in dirs if d not in IGNORE_DIRS and not d.startswith('.DS_Store')]
        if any(ign in root for ign in IGNORE_DIRS):
            continue
        valid_files = sorted([f for f in files if f not in IGNORE_DIRS and f != ".DS_Store"])
        try:
            dir_mtime = os.path.getmtime(root)
            state.append((root, dir_mtime, tuple(valid_files)))
        except OSError:
            pass
    return hash(tuple(state))

def watch_mode():
    print("👀 启动 --watch 自动化守护模式 (同步目录树与 Skill 路由)...")
    sync_all()

    try:
        from watchdog.observers import Observer
        from watchdog.events import FileSystemEventHandler

        class ChangeHandler(FileSystemEventHandler):
            def __init__(self):
                self.last_sync = 0

            def on_any_event(self, event):
                if event.is_directory or any(ign in event.src_path for ign in IGNORE_DIRS):
                    return
                now = time.time()
                if now - self.last_sync > 1.5:
                    self.last_sync = now
                    print(f"\n⚡ 检测到文件/Skill变动 ({os.path.basename(event.src_path)})，自动同步...")
                    sync_all()

        event_handler = ChangeHandler()
        observer = Observer()
        observer.schedule(event_handler, path=".", recursive=True)
        observer.start()
        print("✅ Watchdog 事件监听守护进程运行中...")
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            observer.stop()
        observer.join()

    except ImportError:
        print("💡 未安装 watchdog 库，已启用【全能状态指纹轮询守护】...")
        last_fp = get_workspace_fingerprint()
        try:
            while True:
                time.sleep(1.5)
                current_fp = get_workspace_fingerprint()
                if current_fp != last_fp:
                    print("\n⚡ 检测到文件/Skill变动，自动同步刷新...")
                    sync_all()
                    last_fp = current_fp
        except KeyboardInterrupt:
            print("🛑 守护已停止。")

if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--watch":
        watch_mode()
    else:
        sync_all()
