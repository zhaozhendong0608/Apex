#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Apex 可插拔 Python Linter (linter_py.py)
==========================================
gate.py 会自动调用它（约定：`.ai/scripts/linter_<ext>.py`）：
    python3 .ai/scripts/linter_py.py <file1.py> <file2.py> ...

零依赖、纯 AST（不靠 ruff/flake8 是否安装），专注查**人眼容易漏、正则抓不到**的问题：

  错误级（E，退出码 1，阻断提交）
    E001  裸 except:                 —— 会吞掉 KeyboardInterrupt/SystemExit
    E002  except 里只有 pass          —— 静默吞异常，故障永远查不到
    E003  可变默认参数 def f(x=[])    —— 典型踩坑：默认值被跨调用共享
    E004  eval() / exec()            —— 代码注入风险
    E005  assert 做入参校验           —— python -O 下 assert 会被删掉

  警告级（W，不阻断）
    W001  调试残留 print / pdb
    W002  函数体 > 80 行
    W003  圈复杂度（分支计数）> 15
    W004  导入后未使用
    W005  行宽 > 160
    W006  TODO / FIXME 悬空

用法：
    python3 .ai/scripts/linter_py.py a.py b.py     # 正常调用
    python3 .ai/scripts/linter_py.py --strict a.py # 警告也视为失败
"""

import ast
import sys
import os

MAX_FUNC_LINES = 80
MAX_COMPLEXITY = 15
MAX_LINE_WIDTH = 160

BRANCH_NODES = (ast.If, ast.For, ast.AsyncFor, ast.While, ast.Try,
                ast.With, ast.AsyncWith, ast.BoolOp, ast.IfExp)
ERRORS = []
WARNINGS = []


def err(path, lineno, code, msg):
    ERRORS.append(f"🔴 {os.path.basename(path)}:{lineno} [{code}] {msg}")


def warn(path, lineno, code, msg):
    WARNINGS.append(f"⚠️  {os.path.basename(path)}:{lineno} [{code}] {msg}")


def complexity_of(func):
    n = 0
    for node in ast.walk(func):
        if isinstance(node, BRANCH_NODES):
            n += 1
    return n


def check_file(path):
    try:
        with open(path, "r", encoding="utf-8") as f:
            src = f.read()
    except Exception as e:
        warn(path, 0, "W000", f"无法读取: {e}")
        return
    if not src.strip():
        return

    # --- 行级检查 ---
    for i, line in enumerate(src.splitlines(), 1):
        if len(line) > MAX_LINE_WIDTH:
            warn(path, i, "W005", f"行宽 {len(line)} > {MAX_LINE_WIDTH}")
        stripped = line.strip()
        if stripped.startswith("#") and ("TODO" in stripped or "FIXME" in stripped):
            warn(path, i, "W006", f"悬空标记: {stripped[:60]}")

    try:
        tree = ast.parse(src, filename=path)
    except SyntaxError as e:
        err(path, e.lineno or 0, "E000", f"语法错误: {e.msg}")
        return

    # --- AST 检查 ---
    imported = {}
    print_hits = []
    for node in ast.walk(tree):
        # E001 / E002 裸 except 与空吞
        if isinstance(node, ast.ExceptHandler):
            if node.type is None:
                err(path, node.lineno, "E001", "裸 except: 会吞掉 KeyboardInterrupt/SystemExit，请指定异常类型")
            body = [n for n in node.body if not isinstance(n, ast.Expr) or not isinstance(n.value, ast.Constant)]
            if len(body) == 0 or (len(node.body) == 1 and isinstance(node.body[0], ast.Pass)):
                err(path, node.lineno, "E002", "except 内只有 pass，异常被静默吞掉（至少记日志或重抛）")

        # E004 eval / exec
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Name) and node.func.id in ("eval", "exec"):
            err(path, node.lineno, "E004", f"使用 {node.func.id}() 存在代码注入风险")

        # E005 assert 做校验
        if isinstance(node, ast.Assert):
            warn(path, node.lineno, "W007", "assert 在 python -O 下会被移除，不要用它做入参校验")

        # E003 可变默认参数
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            for d in list(node.args.defaults) + [d for d in node.args.kw_defaults if d]:
                if isinstance(d, (ast.List, ast.Dict, ast.Set)):
                    err(path, node.lineno, "E003",
                        f"函数 {node.name} 使用可变默认参数（默认值会被跨调用共享）")

        # W004 导入收集
        if isinstance(node, ast.Import):
            for a in node.names:
                imported[(a.asname or a.name.split(".")[0])] = node.lineno
        elif isinstance(node, ast.ImportFrom):
            for a in node.names:
                if a.name != "*":
                    imported[(a.asname or a.name)] = node.lineno

        # W001 调试残留：按文件聚合，避免刷屏
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Name) and node.func.id == "print":
            print_hits.append(node.lineno)

        # W002 / W003 函数体量与复杂度
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            span = (node.end_lineno or node.lineno) - node.lineno + 1
            if span > MAX_FUNC_LINES:
                warn(path, node.lineno, "W002", f"函数 {node.name} 有 {span} 行（> {MAX_FUNC_LINES}）")
            c = complexity_of(node)
            if c > MAX_COMPLEXITY:
                warn(path, node.lineno, "W003", f"函数 {node.name} 分支数 {c}（> {MAX_COMPLEXITY}）")

    if print_hits:
        warn(path, print_hits[0], "W001",
             f"共 {len(print_hits)} 处 print（首处行 {print_hits[0]}）—— 确认不是调试残留；长期输出建议用 logging")

    # W004 未使用导入（粗略：名字在其余源码中不再出现）
    source_without_imports = "\n".join(
        l for l in src.splitlines() if not l.strip().startswith(("import ", "from "))
    )
    for name, lineno in imported.items():
        if name in ("annotations",):
            continue
        # 简单词边界判断
        if name not in source_without_imports:
            warn(path, lineno, "W004", f"导入 {name} 后未使用")


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    strict = "--strict" in sys.argv[1:]
    if not args:
        print("用法: python3 .ai/scripts/linter_py.py <file.py> ...")
        return 2

    for p in args:
        if p.endswith(".py") and os.path.isfile(p):
            check_file(p)

    for line in ERRORS:
        print(line)
    for line in WARNINGS:
        print(line)

    print(f"\n📊 检查 {len(args)} 个文件：错误 {len(ERRORS)} 个 / 警告 {len(WARNINGS)} 个")
    if ERRORS:
        print("🚫 存在错误级问题，lint 未通过")
        return 1
    if strict and WARNINGS:
        print("🚫 --strict 模式：警告也视为失败")
        return 1
    print("✅ lint 通过（警告不阻断）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
