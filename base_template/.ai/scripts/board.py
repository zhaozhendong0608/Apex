#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Vibe Coding 3.0 自动化看板与交接管理脚本 (Anchored Version)
使用 HTML 注释锚点 (Comment Anchor Protocol) 彻底杀死正则表达式强结合 Markdown 文本的脆弱性。
"""

import sys
import os
import re
import datetime

STATUS_FILE = os.path.join(".ai", "tier3_status.md")
HANDOVER_FILE = os.path.join(".ai", "tier3_handover.md")

# HTML 隐藏注释锚点定义
ACTIVE_START = "<!-- SECTION:ACTIVE:START -->"
ACTIVE_END = "<!-- SECTION:ACTIVE:END -->"
TODO_START = "<!-- SECTION:TODO:START -->"
TODO_END = "<!-- SECTION:TODO:END -->"
DONE_START = "<!-- SECTION:DONE:START -->"
DONE_END = "<!-- SECTION:DONE:END -->"

DEFAULT_STATUS_TEMPLATE = f"""# 📋 任务看板 (Task Board)

---

{ACTIVE_START}
## 🔴 进行中 (ACTIVE)
{ACTIVE_END}

---

{TODO_START}
## ⚪ 待办中 (TODO)
{TODO_END}

---

{DONE_START}
## 🟢 已完成 (DONE)
{DONE_END}
"""

def ensure_file_exists():
    """确保 .ai 目录及状态文件存在，并自动植入容错锚点 (Self-Healing)"""
    os.makedirs(".ai", exist_ok=True)
    if not os.path.exists(STATUS_FILE):
        with open(STATUS_FILE, "w", encoding="utf-8") as f:
            f.write(DEFAULT_STATUS_TEMPLATE)
        return

    # 如果文件存在但缺少锚点，自动进行自愈修复
    with open(STATUS_FILE, "r", encoding="utf-8") as f:
        content = f.read()

    need_update = False

    if ACTIVE_START not in content:
        content = re.sub(r"(##\s*.*进行中.*|##\s*.*ACTIVE.*)", f"{ACTIVE_START}\n\\1", content, flags=re.IGNORECASE)
        content = re.sub(r"(##\s*.*待办.*|##\s*.*TODO.*)", f"{ACTIVE_END}\n\n\\1", content, flags=re.IGNORECASE)
        need_update = True

    if TODO_START not in content:
        content = re.sub(r"(##\s*.*待办.*|##\s*.*TODO.*)", f"{TODO_START}\n\\1", content, flags=re.IGNORECASE)
        content = re.sub(r"(##\s*.*已完成.*|##\s*.*DONE.*)", f"{TODO_END}\n\n\\1", content, flags=re.IGNORECASE)
        need_update = True

    if DONE_START not in content:
        content = re.sub(r"(##\s*.*已完成.*|##\s*.*DONE.*)", f"{DONE_START}\n\\1", content, flags=re.IGNORECASE)
        content += f"\n{DONE_END}"
        need_update = True

    if need_update:
        with open(STATUS_FILE, "w", encoding="utf-8") as f:
            f.write(content)

def read_status():
    ensure_file_exists()
    with open(STATUS_FILE, "r", encoding="utf-8") as f:
        return f.read()

def write_status(content):
    ensure_file_exists()
    with open(STATUS_FILE, "w", encoding="utf-8") as f:
        f.write(content)

def add_task(task_id, title, goal, files="未指定"):
    """在 TODO 锚点区域追加新任务"""
    content = read_status()
    task_block = f"""
[{task_id}] {title}
- **Status**: TODO
- **功能目标**: {goal}
- **涉及文件**: {files}
"""
    # 匹配 TODO 锚点区间
    pattern = rf"({re.escape(TODO_START)}.*?)({re.escape(TODO_END)})"
    match = re.search(pattern, content, re.DOTALL)

    if match:
        header_and_body = match.group(1).rstrip()
        new_section = f"{header_and_body}\n{task_block}\n{TODO_END}"
        new_content = content[:match.start()] + new_section + content[match.end():]
        write_status(new_content)
        print(f"✅ 成功添加任务: [{task_id}] {title}")
    else:
        print("❌ 报错: 未能在 status.md 中定位到 TODO 锚点区域")

def start_task(task_id):
    """将指定 Task 移入 ACTIVE 锚点区域 (单焦点原则)"""
    content = read_status()
    
    # 查找任务块
    pattern = r"(\[\s*" + re.escape(task_id) + r"\s*\].*?)(?=\n\[|\n#|\n<!--|\Z)"
    match = re.search(pattern, content, re.DOTALL)
    
    if not match:
        print(f"❌ 报错: 找不到任务 [{task_id}]")
        return

    task_block = match.group(1).strip()
    
    # 从原有位置移除
    content = content.replace(task_block, "").strip()

    # 更新状态属性
    task_block_active = re.sub(r"- \*\*Status\*\*: \w+", "- **Status**: IN_PROGRESS", task_block)

    # 插入到 ACTIVE 锚点区域
    active_pattern = rf"({re.escape(ACTIVE_START)}.*?)({re.escape(ACTIVE_END)})"
    active_match = re.search(active_pattern, content, re.DOTALL)

    if active_match:
        header_and_body = active_match.group(1).rstrip()
        new_section = f"{header_and_body}\n\n{task_block_active}\n\n{ACTIVE_END}"
        new_content = content[:active_match.start()] + new_section + content[active_match.end():]
        # 清理多余空行
        new_content = re.sub(r"\n{3,}", "\n\n", new_content)
        write_status(new_content)
        print(f"🚀 任务 [{task_id}] 已激活为 IN_PROGRESS (ACTIVE)！")
    else:
        print("❌ 报错: 未能在 status.md 中定位到 ACTIVE 锚点区域")

def complete_task(task_id):
    """将指定 Task 移入 DONE 锚点区域，并标记完成日期"""
    content = read_status()
    
    pattern = r"(\[\s*" + re.escape(task_id) + r"\s*\].*?)(?=\n\[|\n#|\n<!--|\Z)"
    match = re.search(pattern, content, re.DOTALL)
    
    if not match:
        print(f"❌ 报错: 找不到任务 [{task_id}]")
        return

    task_block = match.group(1).strip()
    content = content.replace(task_block, "").strip()

    today_str = datetime.date.today().strftime("%Y-%m-%d")
    
    # 修改状态与标题追加日期
    task_block_done = re.sub(r"- \*\*Status\*\*: \w+", "- **Status**: DONE", task_block)
    if "(Done:" not in task_block_done:
        task_block_done = re.sub(r"(\[\s*" + re.escape(task_id) + r"\s*\][^\n]*)", r"\1 (Done: " + today_str + ")", task_block_done, count=1)

    # 插入到 DONE 锚点区域
    done_pattern = rf"({re.escape(DONE_START)}.*?)({re.escape(DONE_END)})"
    done_match = re.search(done_pattern, content, re.DOTALL)

    if done_match:
        header_and_body = done_match.group(1).rstrip()
        new_section = f"{header_and_body}\n\n{task_block_done}\n\n{DONE_END}"
        new_content = content[:done_match.start()] + new_section + content[done_match.end():]
        new_content = re.sub(r"\n{3,}", "\n\n", new_content)
        write_status(new_content)
        print(f"🎉 任务 [{task_id}] 已完成归档 (DONE)！")
    else:
        print("❌ 报错: 未能在 status.md 中定位到 DONE 锚点区域")

def archive_handover(summary_text):
    """追加写入 .ai/handover.md 交接日志"""
    os.makedirs(".ai", exist_ok=True)
    now_str = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    
    if not os.path.exists(HANDOVER_FILE):
        header = "# 📦 项目历史交接记忆卡 (Project Handover Logs)\n\n> 本文件记录项目开发过程中的核心架构决策、关键代码改动及环境变更。用于新会话无缝断点续航。\n"
        with open(HANDOVER_FILE, "w", encoding="utf-8") as f:
            f.write(header)
            
    handover_block = f"""
---

## 📦 归档交接 [{now_str}]
{summary_text}
"""
    with open(HANDOVER_FILE, "a", encoding="utf-8") as f:
        f.write(handover_block)
    print(f"💾 交接记忆卡已追加保存至 {HANDOVER_FILE}")

def main():
    if len(sys.argv) < 2:
        print("用法: python board.py [add|start|complete|done|archive|list]")
        print("  python board.py add <task_id> <title> <goal> [files]")
        print("  python board.py start <task_id>")
        print("  python board.py complete <task_id>")
        print("  python board.py archive <summary>")
        return

    cmd = sys.argv[1].lower()

    if cmd == "add":
        if len(sys.argv) < 4:
            print("错误: 参数不足。需要 task_id, title, goal")
            return
        task_id = sys.argv[2]
        title = sys.argv[3]
        goal = sys.argv[4]
        files = sys.argv[5] if len(sys.argv) > 5 else "未指定"
        add_task(task_id, title, goal, files)

    elif cmd == "start":
        if len(sys.argv) < 3:
            print("错误: 参数不足。需要 task_id")
            return
        start_task(sys.argv[2])

    elif cmd in ("complete", "done"):
        if len(sys.argv) < 3:
            print("错误: 参数不足。需要 task_id")
            return
        complete_task(sys.argv[2])

    elif cmd == "archive":
        summary = sys.argv[2] if len(sys.argv) > 2 else "* 常规阶段性归档"
        archive_handover(summary)

    elif cmd == "list":
        print(read_status())

    else:
        print(f"未知指令: {cmd}")

if __name__ == "__main__":
    main()
