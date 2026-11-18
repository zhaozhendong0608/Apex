#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
工程流水线 DAG 智能引路探针 (dag_pilot.py)
-------------------------------------------------------------
核心职责：
1. 从后端 MySQL (SSOT) 实时读取当前需求的最新工位与 DAG 拓扑定义；
2. 依据当前工位算子属性，动态计算出可通往的后继分支、推荐 AI Skill 与机器门禁；
3. 输出对话框标准【🧭 下一步路标卡】与指令快捷键；
4. 支持一键推进 (advance) 并同步持久化至 MySQL，触发大屏实时点亮。
"""

import sys
import os
import json
import urllib.request
import urllib.parse
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
AI_DIR = PROJECT_ROOT / ".ai"
TIER1_FILE = AI_DIR / "tier1_snapshot.md"

BASE_URL = "http://127.0.0.1:8080/api/v1/req/workflow"
# 本地回环调用禁用系统代理，避免 Clash/VPN 代理劫持 127.0.0.1 导致超时降级
LOCAL_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))

# 算子指令速查映射表
NODE_CMD_MAP = {
    "SOP-00": "[0] 体系总览",
    "SOP-01": "[1] 需求规划",
    "SOP-02": "[2] 技术方案",
    "TC-GEN": "[TC] 测试矩阵",
    "SOP-03": "[3] TDD编码",
    "SOP-04": "[4] 机器门禁",
    "SOP-05": "[5] 人工评审",
    "SOP-06": "[6] 集成测试",
    "SOP-07": "[7] 双向验收",
    "SOP-08": "[8] 容器部署",
    "SOP-09": "[9] Git发布",
    "SOP-B1": "[B1] 老代码考古",
    "SOP-B2": "[B2] 极速微创",
    "SOP-B3": "[B3] 小黄鸭排错",
    "SOP-B4": "[B4] 故障急救",
    "SOP-B5": "[B5] 记忆归档",
    "SOP-B6": "[B6] 断点续传",
}

# 默认拓扑算子序列（降级兜底）
DEFAULT_NODES = [
    {"id": "SOP-00", "name": "体系总览", "role": "全员", "skill": "-", "gate": "自适应分流", "next": ["SOP-01"]},
    {"id": "SOP-01", "name": "需求规划", "role": "产品/张工", "skill": "requirement-discovery", "gate": "四层清单齐备", "next": ["SOP-02", "TC-GEN"]},
    {"id": "SOP-02", "name": "方案设计", "role": "研发/李工", "skill": "design-spec-architect", "gate": "python3 gate.py --sql", "next": ["SOP-03"]},
    {"id": "TC-GEN", "name": "测试用例矩阵", "role": "测试/王工", "skill": "quality-verifier", "gate": "用例覆盖100%", "next": ["SOP-03"]},
    {"id": "SOP-03", "name": "TDD编码", "role": "研发/李工", "skill": "coding-standards", "gate": "单元测试全绿", "next": ["SOP-04"]},
    {"id": "SOP-04", "name": "机器门禁", "role": "看门狗探针", "skill": "code-quality-reviewer", "gate": "python3 gate.py --staged", "next": ["SOP-05", "SOP-06"]},
    {"id": "SOP-05", "name": "人工评审", "role": "架构师/程工", "skill": "code-quality-reviewer", "gate": "架构师签字核准", "next": ["SOP-06"]},
    {"id": "SOP-06", "name": "集成测试", "role": "测试/王工", "skill": "quality-verifier", "gate": "TC用例100%通过", "next": ["SOP-07"]},
    {"id": "SOP-07", "name": "双向验收", "role": "看门狗探针", "skill": "quality-verifier", "gate": "board.py verify放行", "next": ["SOP-08", "SOP-09"]},
    {"id": "SOP-08", "name": "容器部署", "role": "运维/李工", "skill": "-", "gate": "健康检查通过", "next": ["SOP-09"]},
    {"id": "SOP-09", "name": "Git发布", "role": "交付/张工", "skill": "-", "gate": "Release资产落盘", "next": []},
]

def get_current_req_code():
    req_code = "REQ-001"
    if TIER1_FILE.exists():
        try:
            txt = TIER1_FILE.read_text(encoding="utf-8")
            for line in txt.splitlines():
                if "当前聚焦需求" in line:
                    parts = line.split(":")
                    if len(parts) > 1:
                        req_code = parts[1].strip()
                        break
        except Exception:
            pass
    return req_code

def fetch_dag_status(req_code="REQ-001"):
    """优先从 MySQL 后端获取当前需求状态与拓扑定义"""
    current_node = "SOP-01"
    nodes = DEFAULT_NODES

    try:
        url_state = f"{BASE_URL}/dag-state?reqCode={req_code}"
        req = urllib.request.Request(url_state)
        with LOCAL_OPENER.open(req, timeout=2.0) as resp:
            data = json.loads(resp.read().decode())
            if data.get("code") == 200 and data.get("data"):
                current_node = data["data"].get("current_node", current_node)
    except Exception:
        # 降级：从本地 tier1 读取
        if TIER1_FILE.exists():
            txt = TIER1_FILE.read_text(encoding="utf-8")
            import re
            m = re.search(r"当前工位\*\*[:：]\s*\[([A-Za-z0-9_\-]+)\]", txt)
            if m:
                current_node = m.group(1).strip()

    try:
        url_topo = f"{BASE_URL}/topology"
        req = urllib.request.Request(url_topo)
        with LOCAL_OPENER.open(req, timeout=2.0) as resp:
            data = json.loads(resp.read().decode())
            if data.get("code") == 200 and data.get("data"):
                raw_nodes = json.loads(data["data"].get("nodesConfig", "[]"))
                raw_edges = json.loads(data["data"].get("edgesConfig", "[]"))
                if raw_nodes:
                    # 优先使用数据库真实 edgesConfig 构建有向后继邻接表
                    adj = {}
                    for edge in raw_edges:
                        src = edge.get("source")
                        tgt = edge.get("target")
                        if src and tgt:
                            adj.setdefault(src, [])
                            if tgt not in adj[src]:
                                adj[src].append(tgt)
                    for idx, n in enumerate(raw_nodes):
                        nid = n.get("id")
                        if nid in adj:
                            n["next"] = adj[nid]
                        elif "next" not in n:
                            n["next"] = [raw_nodes[idx + 1]["id"]] if idx + 1 < len(raw_nodes) else []
                    nodes = raw_nodes
    except Exception:
        pass

    return current_node, nodes


def advance_dag_node(req_code, target_node, status="COMPLETED", track_mode="STANDARD", operator="admin"):
    """一键推进需求工位并持久化至 MySQL (SSOT)，触发大屏实时点亮"""
    _, nodes = fetch_dag_status(req_code)
    node_map = {n["id"]: n for n in nodes}
    info = node_map.get(target_node, {})
    node_name = info.get("name", target_node)
    skill_name = info.get("skill", "")
    resolved_track = info.get("track", track_mode)

    params = urllib.parse.urlencode({
        "reqCode": req_code,
        "targetNode": target_node,
        "nodeName": node_name,
        "trackMode": resolved_track,
        "skillName": "" if skill_name == "-" else skill_name,
        "actionType": "ADVANCE",
        "status": status,
        "operator": operator,
    }).encode("utf-8")

    req = urllib.request.Request(f"{BASE_URL}/advance", data=params, method="POST")
    req.add_header("Content-Type", "application/x-www-form-urlencoded")
    try:
        with LOCAL_OPENER.open(req, timeout=3.0) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
            if payload.get("code") == 200:
                print(f"✅ [MySQL SSOT 已落库] 需求 [{req_code}] 工位 [{target_node} {node_name}] 状态已更新为 {status}！")
                return True
            else:
                print(f"❌ 推进失败: {payload.get('message', payload)}")
                return False
    except Exception as e:
        print(f"❌ 无法连接后端推进接口 ({BASE_URL}/advance): {e}")
        return False

def render_breadcrumbs(req_code, current_node, nodes):
    """根据当前算子和拓扑，生成智能路标指引卡片"""
    node_map = {n["id"]: n for n in nodes}
    curr = node_map.get(current_node, {"name": current_node, "role": "责任人", "skill": "-", "gate": "门禁待定", "next": []})

    next_nodes = curr.get("next", [])
    if not next_nodes:
        # 兜底查找默认序列
        for idx, n in enumerate(DEFAULT_NODES):
            if n["id"] == current_node and idx + 1 < len(DEFAULT_NODES):
                next_nodes = [DEFAULT_NODES[idx + 1]["id"]]
                break

    lines = []
    lines.append(f"🧭 **【工程流水线 · 算子调度路标】** (需求: `{req_code}`)")
    lines.append(f"📍 当前工位: **`[{current_node}] {curr.get('name')}`** ｜ 责任: `{curr.get('role', '研发')}`")
    if curr.get('gate') and curr.get('gate') != '-':
        lines.append(f"🛡️ 离开门禁: `{curr.get('gate')}`")
    lines.append("──────────────────────────────────────────")
    lines.append("👉 **下一步建议推进的分支算子：**")

    if next_nodes:
        for idx, nxt_id in enumerate(next_nodes):
            nxt = node_map.get(nxt_id, {"name": nxt_id, "skill": "-"})
            cmd = NODE_CMD_MAP.get(nxt_id, f"[{nxt_id}]")
            skill_tip = f" (推荐AI专家: `{nxt.get('skill')}`)" if nxt.get('skill') and nxt.get('skill') != '-' else ""
            lines.append(f"  • 回复 **`{cmd.split()[0]}`** ➔ 调度 **{cmd}** · {nxt.get('name')}{skill_tip}")
    else:
        lines.append("  🎉 当前已到达交付终点工位，可通过 `[9]` 发布或归档。")

    lines.append("  • 突发报错/微调 ➔ 回复 **`[B3]`** (小黄鸭精准排错) 或 **`[B2]`** (极速微创修补)")
    return "\n".join(lines)

def main():
    req_code = get_current_req_code()

    # 支持子命令: python3 dag_pilot.py advance <NODE_ID> [REQ_CODE] [--status COMPLETED|IN_PROGRESS] [--track STANDARD|FASTTRACK]
    if len(sys.argv) >= 3 and sys.argv[1] == "advance":
        target_node = sys.argv[2]
        status = "COMPLETED"
        track_mode = "STANDARD"
        operator = "admin"
        rest = sys.argv[3:]
        i = 0
        while i < len(rest):
            if rest[i] == "--status" and i + 1 < len(rest):
                status = rest[i + 1]
                i += 2
            elif rest[i] == "--track" and i + 1 < len(rest):
                track_mode = rest[i + 1]
                i += 2
            elif rest[i] == "--operator" and i + 1 < len(rest):
                operator = rest[i + 1]
                i += 2
            elif not rest[i].startswith("--"):
                req_code = rest[i]
                i += 1
            else:
                i += 1
        ok = advance_dag_node(req_code, target_node, status=status, track_mode=track_mode, operator=operator)
        if not ok:
            return 1
        curr_node, nodes = fetch_dag_status(req_code)
        print(render_breadcrumbs(req_code, curr_node, nodes))
        return 0

    if len(sys.argv) > 1 and not sys.argv[1].startswith("--"):
        req_code = sys.argv[1]

    curr_node, nodes = fetch_dag_status(req_code)
    card = render_breadcrumbs(req_code, curr_node, nodes)
    print(card)
    return 0

if __name__ == "__main__":
    sys.exit(main())
