#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Legacy-Archaeologist 自动化代码切片与链路分析脚本 (Code Slicer)
解析 Java / Node / Python 文件中的方法定义、路由注解及依赖类调用链。
"""

import sys
import os
import re

def parse_code_slice(file_path, target_method=None):
    if not os.path.exists(file_path):
        print(f"❌ 错误: 找不到文件 {file_path}")
        return

    with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
        lines = f.readlines()

    filename = os.path.basename(file_path)
    print(f"🕵️ 开始提取代码切片: [{filename}] (共 {len(lines)} 行)\n" + "="*50)

    # 提取路由和类信息
    routes = []
    dependencies = []
    methods = []

    for idx, line in enumerate(lines, 1):
        # 匹配路由注解 (Java / Node)
        if "@RequestMapping" in line or "@PostMapping" in line or "@GetMapping" in line or "router." in line:
            routes.append((idx, line.strip()))

        # 匹配依赖注入 (Service / Mapper)
        if "@Autowired" in line or "@Resource" in line or "private" in line and ("Service" in line or "Mapper" in line or "Dao" in line):
            dependencies.append((idx, line.strip()))

        # 匹配方法定义
        if re.search(r'(public|protected|private)\s+[\w<>]+\s+\w+\s*\(', line):
            methods.append((idx, line.strip()))

    print("📌 [1. 发现的路由/接口入口]")
    for line_num, route in routes:
        print(f"  Line {line_num:4d}: {route}")
    if not routes:
        print("  (未检测到显式路由注解)")

    print("\n📦 [2. 发现的依赖服务与持久层]")
    for line_num, dep in dependencies:
        print(f"  Line {line_num:4d}: {dep}")
    if not dependencies:
        print("  (未检测到显式依赖声明)")

    print("\n⚙️ [3. 发现的业务方法切片]")
    for line_num, method in methods[:10]: # 展示前 10 个核心方法
        print(f"  Line {line_num:4d}: {method}")

    print("="*50)
    print("💡 建议：配合 `python3 .ai/scripts/arch.py analyze` 注册上述切片至 .ai/tier2_legacy_arch.md 图谱。")

def main():
    if len(sys.argv) < 2:
        print("用法: python3 slice_code.py <file_path> [target_method]")
        print("示例: python3 slice_code.py src/main/java/com/demo/OrderController.java")
        return

    file_path = sys.argv[1]
    target_method = sys.argv[2] if len(sys.argv) > 2 else None
    parse_code_slice(file_path, target_method)

if __name__ == "__main__":
    main()
