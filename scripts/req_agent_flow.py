#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
==============================================================================
📌 需求智能拆解与契约流转 CLI 工具 (req_agent_flow.py)
------------------------------------------------------------------------------
定位：
1. 向上连接数据库/后端 API：自动抓取「待澄清、高优先级、未拆解」的原始需求列表；
2. 呈现给用户进行人机交互确认（选定需求 ➔ AI 结合工程代码与架构上下文进行拆解）；
3. 拆解完成后以清晰四层格式呈现（范围/规则/异常/验收/不做 + 代价人天）；
4. 用户审核确认无误后，一键持久化落库 (t_req_confirm_item)！
==============================================================================
"""

import sys
import os
import json
import urllib.request
import urllib.error
import argparse

API_BASE_URL = os.environ.get("APEX_API_BASE", "http://localhost:8080/api/v1")

def request_api(path, method="GET", data=None):
    url = f"{API_BASE_URL}{path}"
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json",
        "X-Operator": "admin"
    }
    body = json.dumps(data).encode("utf-8") if data else None
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            content = resp.read().decode("utf-8")
            return json.loads(content)
    except urllib.error.URLError as e:
        return {"code": 500, "message": f"连接后端服务失败: {url} ({e})", "data": None}
    except Exception as e:
        return {"code": 500, "message": f"请求异常: {str(e)}", "data": None}

def list_pending_requirements():
    """抓取待办、高优先级、未拆解的需求列表"""
    print("\n🔍 正在从系统底座拉取待拆解的原始需求清单...")
    res = request_api("/req/requirements")
    if res.get("code") != 200 or not res.get("data"):
        print(f"⚠️ 获取需求列表失败或当前无可用需求: {res.get('message')}")
        return []

    all_reqs = res.get("data", [])
    
    # 过滤与优先级排序逻辑：
    # 状态处于 DRAFT(草稿) 或 CLARIFYING(澄清中) 且 四层清单尚未齐备
    priority_order = {"P0": 0, "P1": 1, "P2": 2, "P3": 3}
    
    pending_list = []
    for r in all_reqs:
        # 只要不是已冻结基线，都可以进行拆解
        if r.get("status") in ["DRAFT", "CLARIFYING"]:
            pending_list.append(r)
    
    # 按优先级从高到低排序 (P0 -> P1 -> P2)
    pending_list.sort(key=lambda x: priority_order.get(x.get("priority", "P2"), 9))
    return pending_list

def display_requirements_table(reqs):
    if not reqs:
        print("🎉 恭喜！当前所有高优先级需求均已完成基线与拆解，暂无待办需求。")
        return
    
    print("\n" + "=" * 92)
    print(f"{'序号':<4} | {'需求编号':<10} | {'优先级':<6} | {'当前状态':<10} | {'负责人':<8} | {'需求标题'}")
    print("-" * 92)
    for idx, r in enumerate(reqs, start=1):
        code = r.get("reqCode") or r.get("reqNo") or f"ID-{r.get('id')}"
        prio = r.get("priority") or "P1"
        status = r.get("status") or "DRAFT"
        status_text = "澄清中" if status == "CLARIFYING" else ("草稿" if status == "DRAFT" else status)
        owner = r.get("ownerName") or r.get("owner") or "待指定"
        title = r.get("title") or "无标题"
        print(f"[{idx:02d}] | {code:<10} | {prio:<6} | {status_text:<10} | {owner:<8} | {title}")
    print("=" * 92)

def save_items_to_db(requirement_id, items):
    """将拆解后的确认清单持久化到数据库 (t_req_confirm_item)"""
    payload = {
        "items": items
    }
    res = request_api(f"/req/requirements/{requirement_id}/confirm-items", method="PUT", data=payload)
    if res.get("code") == 200:
        print("\n🎉 成功！拆解任务与四层清单已正式持久化至数据库 (t_req_confirm_item)！")
        cost_data = res.get("data", {})
        print(f"📊 实时共识人天代价更新为: {cost_data.get('totalCostDays', '0.0')} 人天")
        print("👉 现在可以在浏览器刷新 http://localhost:3000/req/detail 直接审阅或发起客户签字！")
        return True
    else:
        print(f"❌ 写入数据库失败: {res.get('message')}")
        return False

def main():
    parser = argparse.ArgumentParser(description="Apex 需求智能拆解与契约流转 CLI")
    parser.add_argument("--list", action="store_true", help="列出当前待办高优先级需求")
    parser.add_argument("--inject", type=str, help="从 JSON 文件中读取拆解结果并写入指定需求 ID")
    parser.add_argument("--req-id", type=int, help="指定需求 ID")
    args = parser.parse_args()

    if args.inject:
        if not args.req_id:
            print("❌ 错误: --inject 模式必须配合 --req-id 指定需求 ID")
            sys.exit(1)
        if not os.path.exists(args.inject):
            print(f"❌ 错误: 指定的 JSON 文件不存在: {args.inject}")
            sys.exit(1)
        with open(args.inject, "r", encoding="utf-8") as f:
            items = json.load(f)
        save_items_to_db(args.req_id, items)
        return

    # 默认模式：列出待办需求并输出人机交互建议
    reqs = list_pending_requirements()
    display_requirements_table(reqs)

    if reqs:
        print("\n💡【AI 智能拆解建议指令】：")
        print(f"在与大模型对话时，你可以直接对我说：")
        first_req = reqs[0]
        code = first_req.get('reqCode') or 'REQ-001'
        print(f"  👉 \"帮我分析待办需求 [{code}]，结合工程源码推演四层清单与代价，并执行落库。\"\n")

if __name__ == "__main__":
    main()

