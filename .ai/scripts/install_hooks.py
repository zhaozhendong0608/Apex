#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
安装 Apex 的 git 钩子 (install_hooks.py)
=========================================
用法：
    python3 .ai/scripts/install_hooks.py          # 安装 pre-commit
    python3 .ai/scripts/install_hooks.py --check  # 只检查是否已安装
    python3 .ai/scripts/install_hooks.py --remove # 卸载

为什么要有这个脚本：
    .git/hooks/ 不入版本库，新克隆的仓库默认没有钩子。
    把钩子内容托管在 .ai/scripts/ 里 + 一个安装命令，才能让门禁"真的生效"。
"""

import os
import stat
import sys
from pathlib import Path

AI_DIR = Path(__file__).resolve().parent.parent
PROJECT_ROOT = AI_DIR.parent
HOOK_PATH = PROJECT_ROOT / ".git" / "hooks" / "pre-commit"

HOOK_BODY = """#!/bin/sh
# ---- Apex 工作流门禁（由 .ai/scripts/install_hooks.py 生成，请勿手改）----
ROOT="$(git rev-parse --show-toplevel)"
exec python3 "$ROOT/.ai/scripts/pre_commit.py"
"""


def is_installed():
    if not HOOK_PATH.exists():
        return False
    try:
        return "Apex 工作流门禁" in HOOK_PATH.read_text(encoding="utf-8")
    except Exception:
        return False


def install():
    if not (PROJECT_ROOT / ".git").is_dir():
        print("❌ 当前目录不是 git 仓库，无法安装钩子")
        return 1
    HOOK_PATH.parent.mkdir(parents=True, exist_ok=True)

    if HOOK_PATH.exists() and not is_installed():
        backup = HOOK_PATH.with_suffix(".bak")
        HOOK_PATH.replace(backup)
        print(f"⚠️  已存在自定义 pre-commit，备份为 {backup.name}")

    HOOK_PATH.write_text(HOOK_BODY, encoding="utf-8")
    HOOK_PATH.chmod(HOOK_PATH.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
    print("✅ 已安装 pre-commit 钩子：.git/hooks/pre-commit")
    print("   以后每次 git commit 都会自动跑：gate --staged / --refs / --sop")
    print("   跳过方式：git commit --no-verify（不建议）")
    return 0


def check():
    if is_installed():
        print("✅ pre-commit 已安装")
        return 0
    print("❌ pre-commit 未安装 —— 门禁目前只是「建议」，不会自动执行")
    print("   安装：python3 .ai/scripts/install_hooks.py")
    return 1


def remove():
    if is_installed():
        HOOK_PATH.unlink()
        print("🗑️  已卸载 pre-commit 钩子")
        return 0
    print("ℹ️  pre-commit 未安装，无需卸载")
    return 0


if __name__ == "__main__":
    arg = sys.argv[1] if len(sys.argv) > 1 else ""
    if arg == "--check":
        sys.exit(check())
    if arg == "--remove":
        sys.exit(remove())
    sys.exit(install())
