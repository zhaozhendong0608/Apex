#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Apex pre-commit 钩子入口 (pre_commit.py)
==========================================
被 .git/hooks/pre-commit 调用；也可手动运行。

做三件事（都很快，目标 < 3 秒）：
  1. gate.py --staged          —— 看门狗 6 项 + SQL 配对 + linter + 密钥扫描
  2. gate.py --refs            —— 指令引用完整性（编号漂移 / 幽灵引用）
  3. gate.py --sop             —— 能力点基线完整性（防删功能）

放行：全部通过 → exit 0
拦截：任一失败 → exit 1，并打印修复指引（git commit 会被中止）

跳过：`git commit --no-verify`（团队约定：只允许在明确知道后果时用）
"""

import subprocess
import sys
import os
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

AI_DIR = Path(__file__).resolve().parent.parent
PROJECT_ROOT = AI_DIR.parent


def run(label, args):
    print(f"\n{'=' * 56}\n▶ {label}\n{'=' * 56}")
    proc = subprocess.run([sys.executable] + args, cwd=str(PROJECT_ROOT))
    return proc.returncode


def main():
    # 没有暂存文件时直接放行（例如只改 .gitignore 的场景不会走到这里）
    try:
        staged = subprocess.run(
            ["git", "diff", "--cached", "--name-only", "--diff-filter=ACMR"],
            cwd=str(PROJECT_ROOT), capture_output=True, text=True, check=True,
        ).stdout.strip()
    except Exception:
        print("⚠️  不在 git 仓库内，跳过 pre-commit 检查")
        return 0

    if not staged:
        return 0

    gate = str(AI_DIR / "scripts" / "gate.py")
    failures = []

    if run("① 代码门禁（gate --staged）", [gate, "--staged"]) != 0:
        failures.append("gate --staged")
    if run("② 指令引用完整性（gate --refs）", [gate, "--refs"]) != 0:
        failures.append("gate --refs")
    if run("③ 能力点基线（gate --sop）", [gate, "--sop"]) != 0:
        failures.append("gate --sop")

    print(f"\n{'=' * 56}")
    if failures:
        print(f"🚫 [BLOCKING] pre-commit 未通过：{', '.join(failures)}")
        print("   本次 commit 已被中止。修复后重新 git add && git commit。")
        print("   ⚠️  确需跳过请用 git commit --no-verify（团队约定：需在 PR 里说明原因）")
        print("=" * 56)
        return 1

    print("✅ [LGTM] pre-commit 全部通过，允许提交。")
    print("=" * 56)
    return 0


if __name__ == "__main__":
    sys.exit(main())
