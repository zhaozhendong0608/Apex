#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
自动化测试探针套件统一运行器 (test_runner.py)
-------------------------------------------------------------
核心职责：
1. 动态发现并执行项目内所有测试用例与回归探针；
2. 支持纯逻辑单元测试与集成接口冒烟测试；
3. 输出结构化测试报告与失败原因；
4. 供 gate.py 门禁与 board.py 验收时调用，实现机器全自动质量闭环。
"""

import sys
import os
import subprocess
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent

# 注册的核心测试探针清单
TEST_SUITES = [
    {
        "id": "TC-NET-SIM",
        "name": "网络拓扑仿真与 BFS 算法契约测试",
        "script": "base_template/scripts/tests/test_network_sim.py",
        "type": "UNIT_LOGIC"
    },
    {
        "id": "TC-WFC-DATA",
        "name": "工作流数据驱动与数据库持久化回归探针",
        "script": "scripts/tests/test_workflow_data_driven.py",
        "type": "REGRESSION"
    },
    {
        "id": "TC-CONTRACT-GUARD",
        "name": "DLD 契约与业务代码对齐看门狗",
        "script": ".ai/scripts/contract_guard.py",
        "args": ["--check"],
        "type": "CONTRACT"
    }
]

def run_suite(suite):
    rel_path = suite["script"]
    full_path = PROJECT_ROOT / rel_path
    if not full_path.exists():
        return False, f"测试脚本不存在: {rel_path}"

    cmd = [sys.executable, str(full_path)] + suite.get("args", [])
    try:
        proc = subprocess.run(
            cmd,
            cwd=str(PROJECT_ROOT),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            timeout=15
        )
        passed = (proc.returncode == 0)
        output = proc.stdout if proc.stdout else proc.stderr
        return passed, output.strip()
    except Exception as e:
        return False, f"执行异常: {e}"

def main():
    print("=" * 60)
    print("🧪 [Test Runner] 启动全量自动化测试探针与契约质量门禁")
    print("=" * 60)

    total = len(TEST_SUITES)
    passed_count = 0
    failed_suites = []

    for idx, suite in enumerate(TEST_SUITES, 1):
        print(f"\n▶ [{idx}/{total}] 正在执行: [{suite['id']}] {suite['name']} ...")
        ok, out = run_suite(suite)
        if ok:
            passed_count += 1
            print(f"  🟢 [PASS] 通过")
            # 打印前 2 行摘要
            for l in out.splitlines()[-3:]:
                if l.strip():
                    print(f"     {l.strip()}")
        else:
            failed_suites.append((suite, out))
            print(f"  🔴 [FAIL] 未通过")
            for l in out.splitlines()[-6:]:
                if l.strip():
                    print(f"     {l.strip()}")

    print("\n" + "=" * 60)
    print(f"📊 测试结果汇总: 总计 {total} 项探针, 通过 {passed_count} 项, 失败 {len(failed_suites)} 项")
    print("=" * 60)

    if failed_suites:
        print("\n🚫 [BLOCKING] 质量门禁拦截：存在未跑通的测试用例！")
        return 1
    else:
        print("\n🎉 [ALL GREEN] 全量自动化测试探针 100% 跑绿通过！准予放行与归档。")
        return 0

if __name__ == "__main__":
    sys.exit(main())
