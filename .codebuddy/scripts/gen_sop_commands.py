#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Apex SOP -> 斜杠节点生成器（派生视图，勿手写产物）。

单一事实源是 .ai/sop/*.md：从每篇的标题与「触发」行抽取主题、产出和触发词，
同时生成两套入口：

1. .codebuddy/commands/apex-<编号>.md  —— CodeBuddy Code CLI 的自定义斜杠命令
2. ~/.workbuddy/skills/apex-<编号>/SKILL.md —— WorkBuddy 桌面端「/」补全里的技能命令

WorkBuddy 桌面端的 / 列表只读「系统命令 + 已安装 Skill + 自定义快捷指令」，
不扫描 .codebuddy/commands/，所以第 2 套才是桌面端能看到的入口。

改了 SOP 后重跑：python3 .codebuddy/scripts/gen_sop_commands.py
"""

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOP_DIR = ROOT / ".ai" / "sop"
CMD_DIR = ROOT / ".codebuddy" / "commands"
SKILL_DIR = Path.home() / ".workbuddy" / "skills"

HINTS = {
    "0": "<可留空：打印轨道总表>",
    "1": "<用白话描述新需求>",
    "2": "<可留空：基于当前 PRD 出技术方案>",
    "3": "<可留空：实现当前 ACTIVE 任务>",
    "4": "<可留空：跑机器门禁>",
    "5": "<可留空：出人工评审报告>",
    "6": "<可留空：集成联调与 E2E>",
    "7": "<可留空：终验清单，需你回复确认验收>",
    "8": "<部署目标 / 环境>",
    "9": "<提交说明 / tag>",
    "B1": "<老项目路径或模块名>",
    "B2": "<一句话描述小改动>",
    "B3": "<粘贴完整报错堆栈>",
    "B4": "<线上故障现象 / 日志>",
    "B5": "",
    "B6": "",
}

NAME_RE = re.compile(r"^(?P<num>\d{2}|B\d)-sop-.*\.md$")
TITLE_RE = re.compile(r"#\s*\[[^\]]+\]\s*([^(]+)")
TRIGGER_RE = re.compile(r"`([^`]+)`")


def parse_sop(path):
    lines = path.read_text(encoding="utf-8").splitlines()
    title_match = TITLE_RE.search(lines[0]) if lines else None
    subject = title_match.group(1).strip() if title_match else path.stem
    trigger_line = next((ln for ln in lines[:6] if ln.startswith("> **触发**")), "")
    parts = trigger_line.split("｜")
    output = ""
    for part in parts:
        if "**产出**" in part:
            output = part.split("：", 1)[-1].strip()
    words = []
    for word in TRIGGER_RE.findall(parts[0] if parts else ""):
        if word.startswith("[") or word in words:
            continue
        words.append(word)
    return subject, output or "按 SOP 正文交付", " / ".join(words)


def normalize(num):
    if not num.isdigit():
        return num
    return num.lstrip("0") or "0"


def quote(value):
    return '"' + value.replace('"', "'").replace("\\", "/") + '"'


def command_text(label, subject, output, description, hint, sop_name):
    front = ["---", f"description: {quote(description)}"]
    if hint:
        front.append(f"argument-hint: {quote(hint)}")
    front += ["allowed-tools: Read, Write, Edit, Bash, Grep, Glob", "---"]
    return "\n".join(front) + f"""
按 Apex SOP `{label}`（{subject}）执行：$ARGUMENTS

1. 只读 `.ai/sop/{sop_name}`；读到 `<!-- ⛔ EXEC-STOP -->` 立即停，禁止预读其它 SOP。
2. 遵守 `.codebuddy/CODEBUDDY.md` 的「斜杠命令通用执行规程」：旧编号迁移、`gate.py` 前置检查、`board.py` 状态约束、`[7]` 人工放行、卡片 ≤6 行。
3. 本阶段交付：{output}。
"""


def skill_text(label, subject, output, description, triggers, sop_name):
    name = f"apex-{label.lower()}"
    desc = description + (f"。触发词：{triggers}" if triggers else "")
    return f"""---
name: {name}
description: {quote(desc)}
---

# Apex SOP [{label}] {subject}

用户在 `/{name}` 之后输入的内容（或 $ARGUMENTS）即本次任务；为空时按当前消息正文执行。

## 执行

1. 只读 Apex 项目的 `.ai/sop/{sop_name}`；读到 `<!-- ⛔ EXEC-STOP -->` 立即停，禁止预读其它 SOP。
2. 遵守 `.codebuddy/CODEBUDDY.md` 的「斜杠命令通用执行规程」：旧编号迁移、`gate.py` 前置检查、`board.py` 状态约束、`[7]` 人工放行、卡片 ≤6 行。
3. 本阶段交付：{output}。

## 定位

本技能绑定 Apex 项目根：`{ROOT}`。若当前工作目录不是它，按该绝对路径拼接 `.ai/sop/...` 与 `.ai/scripts/...` 后再读，不要在项目外凭空推断。
"""


def main():
    CMD_DIR.mkdir(parents=True, exist_ok=True)
    SKILL_DIR.mkdir(parents=True, exist_ok=True)
    generated = []
    for sop in sorted(SOP_DIR.glob("*.md")):
        m = NAME_RE.match(sop.name)
        if not m:
            continue
        label = normalize(m.group("num"))
        subject, output, triggers = parse_sop(sop)
        description = f"Apex SOP [{label}] {subject} · {output}"
        hint = HINTS.get(label, "")

        (CMD_DIR / f"apex-{label.lower()}.md").write_text(
            command_text(label, subject, output, description, hint, sop.name),
            encoding="utf-8",
        )
        skill_dir = SKILL_DIR / f"apex-{label.lower()}"
        skill_dir.mkdir(parents=True, exist_ok=True)
        (skill_dir / "SKILL.md").write_text(
            skill_text(label, subject, output, description, triggers, sop.name),
            encoding="utf-8",
        )
        generated.append(f"/apex-{label.lower()}")

    print(f"CLI 斜杠命令 : {len(generated)} 个 -> {CMD_DIR.relative_to(ROOT)}")
    print(f"桌面端 Skill : {len(generated)} 个 -> {SKILL_DIR}")
    print("  " + "  ".join(generated))


if __name__ == "__main__":
    main()
