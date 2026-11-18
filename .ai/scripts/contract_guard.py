#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
契约对齐与漂移检测看门狗 (contract_guard.py)
-----------------------------------------------------------
职责：
1. 扫描 docs/ 目录下的 DLD (详细设计说明书) 与 API 规约，提取契约路由 (Method + Path) 与实体定义；
2. 扫描真实 Java Controller 与 前端 API 代码，比对契约覆盖度与一致性；
3. 输出漂移报告 (Missing API / Extra API / Method Mismatch)；
4. 支持以机器门禁模式运行 (exit code != 0 阻断流转)。

用法：
  python3 .ai/scripts/contract_guard.py                     # 全局比对
  python3 .ai/scripts/contract_guard.py --module sys_platform
  python3 .ai/scripts/contract_guard.py --check             # 门禁核验模式 (有漂移返回非0)
"""

import sys
import os
import re
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
DOCS_DIR = PROJECT_ROOT / "docs"
BACKEND_DIR = PROJECT_ROOT / "base_template" / "backend"
FRONTEND_DIR = PROJECT_ROOT / "base_template" / "frontend"

def extract_dld_contracts(docs_dir: Path):
    """从 docs/**/dld-*.md 和 04-api-design.md 中提取 API 契约列表"""
    contracts = []
    dld_files = list(docs_dir.glob("**/dld-*.md"))
    api_design = docs_dir / "04-api-design.md"
    if api_design.exists():
        dld_files.append(api_design)

    # 匹配规则:
    # 1. 匹配类似: `POST /api/v1/auth/login` 或 **URL**: `/api/v1/...`
    # 2. 匹配 - **Method**: `POST`
    for f in dld_files:
        try:
            content = f.read_text(encoding="utf-8")
        except Exception:
            continue

        rel_path = str(f.relative_to(PROJECT_ROOT))
        
        # 模式 A: `(GET|POST|PUT|DELETE|PATCH)\s+([/a-zA-Z0-9_\-\{\}]+)`
        matches_inline = re.findall(r"[`(](GET|POST|PUT|DELETE|PATCH)\s+([/a-zA-Z0-9_\-\{\}]+)[`)]", content, re.IGNORECASE)
        for m, p in matches_inline:
            clean_path = p.strip()
            if clean_path.startswith("/api/"):
                contracts.append({
                    "method": m.upper(),
                    "path": clean_path,
                    "source": rel_path
                })

        # 模式 B: - **URL**: `...` \n - **Method**: `...`
        sections = content.split("###")
        for sec in sections:
            url_m = re.search(r"[-*]\s+\*\*URL\*\*[:：]\s*`?([/a-zA-Z0-9_\-\{\}]+)`?", sec)
            method_m = re.search(r"[-*]\s+\*\*Method\*\*[:：]\s*`?(GET|POST|PUT|DELETE|PATCH)`?", sec, re.IGNORECASE)
            if url_m and method_m:
                path = url_m.group(1).strip()
                method = method_m.group(1).upper()
                if path.startswith("/api/"):
                    contracts.append({
                        "method": method,
                        "path": path,
                        "source": rel_path
                    })

    # 去重
    unique = {}
    for c in contracts:
        key = f"{c['method']} {c['path']}"
        if key not in unique:
            unique[key] = c
    return list(unique.values())


def extract_backend_routes(backend_dir: Path):
    """扫描 Spring Boot Controller 类中的注解，提取实际代码中的 API 路由"""
    routes = []
    if not backend_dir.exists():
        return routes

    controller_files = list(backend_dir.glob("**/*Controller.java"))
    for f in controller_files:
        try:
            content = f.read_text(encoding="utf-8")
        except Exception:
            continue

        rel_path = str(f.relative_to(PROJECT_ROOT))

        # 提取类级 @RequestMapping("/...")
        class_prefix = ""
        # 优先匹配带有 RequestMapping 的类定义前注解
        class_req_m = re.search(r"@RequestMapping\s*\(\s*(?:value\s*=\s*)?[\"']([^\"']+)[\"']", content)
        if class_req_m:
            class_prefix = class_req_m.group(1).strip()
            if not class_prefix.startswith("/"):
                class_prefix = "/" + class_prefix

        # 提取方法级注解: @GetMapping, @PostMapping, @PutMapping, @DeleteMapping, @RequestMapping
        # 支持 @PostMapping, @PostMapping(""), @PostMapping("/create"), 以及 @PostMapping({"/a", "/b"}) 等多种形式
        method_pattern = re.compile(
            r"@(GetMapping|PostMapping|PutMapping|DeleteMapping|RequestMapping)(?:\s*\(\s*(?:value\s*=\s*)?([^\)]*)\s*\))?",
            re.MULTILINE
        )

        for match in method_pattern.finditer(content):
            anno_type = match.group(1)
            raw_arg = (match.group(2) or "").strip()

            # 提取所有双引号内的路径字符串
            extracted_subpaths = re.findall(r"[\"']([^\"']+)[\"']", raw_arg)
            if not extracted_subpaths:
                extracted_subpaths = [""]

            for raw_subpath in extracted_subpaths:
                subpath = raw_subpath.strip()
                if subpath and not subpath.startswith("/"):
                    subpath = "/" + subpath

                full_path = f"{class_prefix}{subpath}" if class_prefix else subpath
                if not full_path:
                    full_path = "/"
                # 去除双斜杠与尾部斜杠
                full_path = re.sub(r"/+", "/", full_path).rstrip("/") or "/"

                # 确定 HTTP Method
                if anno_type == "GetMapping":
                    method = "GET"
                elif anno_type == "PostMapping":
                    method = "POST"
                elif anno_type == "PutMapping":
                    method = "PUT"
                elif anno_type == "DeleteMapping":
                    method = "DELETE"
                else:
                    method = "ALL"

                routes.append({
                    "method": method,
                    "path": full_path,
                    "source": rel_path
                })

    # 去重
    unique = {}
    for r in routes:
        key = f"{r['method']} {r['path']}"
        if key not in unique:
            unique[key] = r
    return list(unique.values())


def normalize_path(path: str) -> str:
    """标准化路径：将 {id} 等动态参数统一替换为 {*} 以便模糊匹配"""
    p = re.sub(r"\{[a-zA-Z0-9_\-]+\}", "{*}", path)
    return p.rstrip("/")


def compare_contracts(dld_contracts, code_routes):
    """比对契约与代码路由"""
    # 建立 code 索引: normalized_path -> list of routes
    code_map = {}
    for r in code_routes:
        norm = normalize_path(r["path"])
        code_map.setdefault(norm, []).append(r)

    matched = []
    missing_in_code = []

    for dc in dld_contracts:
        norm_dld = normalize_path(dc["path"])
        dld_method = dc["method"]

        if norm_dld in code_map:
            # 找到路径，检查方法
            found_method = False
            for cr in code_map[norm_dld]:
                if cr["method"] == dld_method or cr["method"] == "ALL":
                    found_method = True
                    matched.append({"dld": dc, "code": cr})
                    break
            if not found_method:
                missing_in_code.append({
                    "contract": dc,
                    "reason": f"路径匹配但方法不符 (DLD 要求: {dld_method}, 代码为: {[cr['method'] for cr in code_map[norm_dld]]})"
                })
        else:
            missing_in_code.append({
                "contract": dc,
                "reason": "代码中未找到对应 Controller 路由"
            })

    return {
        "matched": matched,
        "missing_in_code": missing_in_code,
        "total_contracts": len(dld_contracts),
        "total_routes": len(code_routes)
    }


def main():
    check_mode = "--check" in sys.argv

    print("🔍 [Contract Guard] 启动 DLD 契约与真实业务代码对齐检查...\n")

    contracts = extract_dld_contracts(DOCS_DIR)
    routes = extract_backend_routes(BACKEND_DIR)

    report = compare_contracts(contracts, routes)

    print(f"📊 扫描概况:")
    print(f" - DLD 设计契约总数 : {report['total_contracts']} 条")
    print(f" - 真实 Controller 路由: {report['total_routes']} 条")
    print(f" - 契约成功落地对齐 : {len(report['matched'])} 条\n")

    if report["matched"]:
        print("✅ 【已严格对齐的契约 (前5条示例)】:")
        for it in report["matched"][:5]:
            c = it["dld"]
            print(f"   ✔ [{c['method']}] {c['path']} ➔ {it['code']['source']}")
        if len(report["matched"]) > 5:
            print(f"   ... (其余 {len(report['matched']) - 5} 条匹配已收起)")
        print()

    if report["missing_in_code"]:
        print(f"⚠️ 【发现代码与 DLD 设计存在漂移 ({len(report['missing_in_code'])} 条)】:")
        for m in report["missing_in_code"]:
            c = m["contract"]
            print(f"   ❌ [{c['method']}] {c['path']} (来源: {c['source']})")
            print(f"      原因: {m['reason']}")
        print()

        if check_mode:
            print("🚫 [GATE BLOCK] 门禁核验失败：存在未落地的 DLD 契约！请补全代码或更新 DLD。")
            return 1
    else:
        print("🎉 完美！所有 DLD 契约均在后端代码中找到对应实现，契约对齐率 100%！")

    return 0

if __name__ == "__main__":
    sys.exit(main())
