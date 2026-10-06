#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Quality-Verifier 契约测试执行与小黄鸭日志捕获器
一键运行工程中的 Python 自动化契约测试，测试失败时提取 Traceback 供小黄鸭探针分析。
"""

import sys
import os
import subprocess

def run_tests(module_name=None):
    test_dir = os.path.join("scripts", "tests")
    if not os.path.exists(test_dir):
        print(f"⚠️ 未找到测试目录 `{test_dir}`，请先运行 generate_test.py 生成测试脚本。")
        return False

    if module_name:
        clean_name = module_name.replace("test_", "").replace(".py", "")
        target_script = os.path.join(test_dir, f"test_{clean_name}.py")
        if not os.path.exists(target_script):
            print(f"❌ 找不到对应测试脚本: {target_script}")
            return False
        cmd = [sys.executable, target_script]
    else:
        cmd = [sys.executable, "-m", "unittest", "discover", "-s", test_dir, "-p", "test_*.py"]

    print(f"🚀 开始执行测试命令: {' '.join(cmd)}\n" + "="*50)
    
    result = subprocess.run(cmd, capture_output=True, text=True)
    
    print(result.stdout)
    if result.stderr:
        print(result.stderr)
        
    print("="*50)
    if result.returncode == 0:
        print("🎉 所有自动化契约测试均通过！(ALL PASSED)")
        return True
    else:
        print("🔴 发现测试用例失败！自动提取以下 Traceback 喂给小黄鸭探针:\n")
        print("---------------- [小黄鸭日志探针捕获区] ----------------")
        for line in result.stderr.splitlines():
            if "FAIL:" in line or "ERROR:" in line or "AssertionError" in line or "File " in line:
                print(f"  📌 {line}")
        print("-------------------------------------------------------")
        print("👉 请加载 @.agents/skills/quality-verifier 激活小黄鸭排错协议进行修复。")
        return False

def main():
    module_name = sys.argv[1] if len(sys.argv) > 1 else None
    success = run_tests(module_name)
    sys.exit(0 if success else 1)

if __name__ == "__main__":
    main()
