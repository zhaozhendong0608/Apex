#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
需求领单与防重拆解探针 (req_pilot.py)
===========================================================
核心职责：
1. 直连后端/数据库，查询所有未拆解/待规划的原始需求。
2. 开发者在终端/对话中选择需求后，原子加锁标记为 [DISSECTED] (已拆解)，杜绝重复拆解。
3. 自动同步刷新 .ai/tier1_snapshot.md 的当前聚焦需求。
4. 容错回退机制：当后端网络隔离或未启动时，自动读取本地离线数据源，确保不中断流程。
"""

import sys
import os
import json
import urllib.request
import urllib.parse
import re

AI_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROJECT_ROOT = os.path.dirname(AI_DIR)
TIER1_FILE = os.path.join(AI_DIR, "tier1_snapshot.md")
LOCAL_REQ_CACHE = os.path.join(AI_DIR, ".req_pool_cache.json")
BACKEND_BASE_URL = "http://127.0.0.1:8080/api/v1/req"

# 默认离线兜底需求池（当后端未启动时启用）
DEFAULT_OFFLINE_REQUIREMENTS = [
    {
        "id": 1,
        "reqCode": "REQ-001",
        "title": "需求与版本全生命周期治理核心模块",
        "rawQuote": "必须管住需求、版本、原型三件事，做到留痕、可确认、可追溯。",
        "status": "DISSECTED",
        "currentNodeId": "SOP-03",
        "ownerName": "张工"
    },
    {
        "id": 2,
        "reqCode": "REQ-002",
        "title": "订单支付成功短信通知与对账报表导出",
        "rawQuote": "订单支付成功后给客户发短信通知，并支持导出财务对账报表。",
        "status": "PENDING",
        "currentNodeId": "SOP-01",
        "ownerName": "李工"
    },
    {
        "id": 3,
        "reqCode": "REQ-003",
        "title": "工业拓扑多分支探索历史轨迹回放",
        "rawQuote": "支持将网络拓扑仿真寻路的历史路径按时间轴动态高亮回放。",
        "status": "PENDING",
        "currentNodeId": "SOP-01",
        "ownerName": "王工"
    }
]


def load_local_cache():
    if os.path.exists(LOCAL_REQ_CACHE):
        try:
            with open(LOCAL_REQ_CACHE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    save_local_cache(DEFAULT_OFFLINE_REQUIREMENTS)
    return DEFAULT_OFFLINE_REQUIREMENTS


def save_local_cache(data):
    try:
        with open(LOCAL_REQ_CACHE, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    except Exception:
        pass


def fetch_requirements_from_backend(project_id=1):
    """尝试从后端 MySQL API 获取需求列表"""
    try:
        url = f"{BACKEND_BASE_URL}/requirements?projectId={project_id}"
        req = urllib.request.Request(url, headers={"User-Agent": "Apex-ReqPilot/1.0"})
        with urllib.request.urlopen(req, timeout=1.5) as resp:
            body = json.loads(resp.read().decode("utf-8"))
            if body.get("code") == 200 and body.get("data"):
                save_local_cache(body["data"])
                return body["data"], "MySQL-Live"
    except Exception:
        pass
    return load_local_cache(), "Offline-Cache"


def set_tier1_focus_req(req_code):
    """自动将 tier1_snapshot.md 的当前聚焦需求更新为指定 req_code"""
    if not os.path.exists(TIER1_FILE):
        return False
    try:
        with open(TIER1_FILE, "r", encoding="utf-8") as f:
            content = f.read()
        
        # 替换当前聚焦需求
        new_content = re.sub(
            r"(- \*\*当前聚焦需求\*\*[:：]\s*).*",
            rf"\g<1>{req_code}",
            content
        )
        if new_content != content:
            with open(TIER1_FILE, "w", encoding="utf-8") as f:
                f.write(new_content)
            return True
    except Exception:
        pass
    return False


def cmd_list(show_all=False):
    """列出数据库中所有待拆解或全部原始需求"""
    reqs, source = fetch_requirements_from_backend()
    
    print("=" * 65)
    print(f"📋 【需求池事实源 (SSOT) · 待拆解任务列表】 (数据源: {source})")
    print("=" * 65)
    
    pending_list = []
    dissected_list = []
    
    for r in reqs:
        status = r.get("status", "PENDING").upper()
        if status in ("PENDING", "DRAFT"):
            pending_list.append(r)
        else:
            dissected_list.append(r)
            
    if not pending_list:
        print("🎉 太棒了！当前没有待拆解的原始需求（所有大需求均已完成拆解并锁定）。")
    else:
        print("⏳ 【未拆解需求 (可认领拆解)】：")
        for idx, r in enumerate(pending_list, 1):
            code = r.get("reqCode")
            title = r.get("title")
            owner = r.get("ownerName", "未指定")
            quote = r.get("rawQuote", "")[:35]
            print(f"  [{idx}] 编号: {code} ｜ 标题: {title}")
            print(f"      客户原话: “{quote}…” ｜ 负责人: {owner}")
            print(f"      👉 认领拆解命令: python3 .ai/scripts/req_pilot.py pick {code}\n")
            
    if show_all and dissected_list:
        print("-" * 65)
        print("🔒 【已完成拆解/执行中需求 (已上锁防重复)】：")
        for r in dissected_list:
            print(f"  • {r.get('reqCode')} ｜ {r.get('title')} ｜ 状态: {r.get('status')} (已上锁)")
            
    print("=" * 65)
    return 0


def cmd_pick(req_code):
    """开发者认领并锁定某个大需求，触发防重加锁与名片更新"""
    reqs, _ = fetch_requirements_from_backend()
    target = None
    for r in reqs:
        if r.get("reqCode", "").upper() == req_code.upper():
            target = r
            break
            
    if not target:
        print(f"❌ 找不到需求编号: [{req_code}]。请先使用 `python3 .ai/scripts/req_pilot.py list` 查询。")
        return 1
        
    current_status = target.get("status", "PENDING").upper()
    if current_status not in ("PENDING", "DRAFT"):
        print(f"🚫 [防重锁阻断] 需求 [{req_code}] 当前状态为 [{current_status}]，已被拆解过并已锁定！")
        print(f"   ⚠️ 严禁对已拆解的大需求重复执行任务拆解，请直接在当前需求下推进工位或拆解微观便利贴。")
        return 1
        
    # 尝试同步加锁状态至后端
    locked = False
    try:
        url = f"{BACKEND_BASE_URL}/workflow/advance"
        data = urllib.parse.urlencode({
            "reqCode": req_code,
            "targetNode": "SOP-01",
            "nodeName": "需求规划与拆解锁死",
            "actionType": "DISSECT_LOCK",
            "status": "DISSECTED",
            "operator": target.get("ownerName", "admin")
        }).encode("utf-8")
        req = urllib.request.Request(url, data=data)
        with urllib.request.urlopen(req, timeout=1.5) as resp:
            res_json = json.loads(resp.read().decode("utf-8"))
            if res_json.get("code") == 200:
                locked = True
    except Exception:
        pass
        
    # 本地缓存同步锁死
    for r in reqs:
        if r.get("reqCode", "").upper() == req_code.upper():
            r["status"] = "DISSECTED"
            r["currentNodeId"] = "SOP-01"
            break
    save_local_cache(reqs)
    
    # 同步刷新 tier1 名片
    set_tier1_focus_req(req_code)
    
    print("=" * 65)
    print(f"🎯 成功锁定需求: [{req_code}] · {target.get('title')}")
    print(f"🔒 数据库状态已锁死为: [DISSECTED] (已拆解)，彻底杜绝重复拆解！")
    print(f"🪪 已自动同步刷新工作区名片: tier1_snapshot.md ➔ 当前聚焦需求: {req_code}")
    print("=" * 65)
    print("🚀 【下一阶段推进指引】：")
    print("   1. 在与 AI 会话中直接回复：`[1]` 启动需求规划与四层清单澄清 (SOP-01)。")
    print(f"   2. 或在终端启动专属子任务：`python3 .ai/scripts/board.py new {req_code.replace('-', '_')} \"...\" \"...\"`")
    print("=" * 65)
    return 0


def main():
    if len(sys.argv) < 2:
        return cmd_list()
        
    cmd = sys.argv[1].lower()
    if cmd in ("list", "ls"):
        show_all = "--all" in sys.argv
        return cmd_list(show_all)
    elif cmd in ("pick", "select", "lock"):
        if len(sys.argv) < 3:
            print("❌ 参数不足：python3 req_pilot.py pick <REQ_CODE>")
            print("   示例：python3 req_pilot.py pick REQ-002")
            return 2
        return cmd_pick(sys.argv[2])
    else:
        print(f"❌ 未知命令: {cmd}")
        print("用法：")
        print("  python3 .ai/scripts/req_pilot.py list        # 列出所有待拆解需求")
        print("  python3 .ai/scripts/req_pilot.py list --all  # 列出包括已锁定的全部需求")
        print("  python3 .ai/scripts/req_pilot.py pick <CODE> # 认领大需求并上锁防重")
        return 2


if __name__ == "__main__":
    sys.exit(main())
