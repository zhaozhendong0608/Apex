#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
⛓️ DAG 铁链路标状态机引擎 (dag_chain.py)
-------------------------------------------------------------
核心职能：
1. 作为确定性有向无环图 (DAG) 的运行时引擎，维护各个模块与任务的工位状态；
2. 消除人脑记忆 SOP 编号的负担，每次只读取当前工位并生成极简「下一步路标」；
3. 严格实现 4 种运行时原语：
   - advance: 顺推至下一工位 (Node N -> Node N+1)
   - skip: 显式跳过某工位并记录原因 (Node N -> Node N+k)
   - explore: 原地深入探讨/推演 (mode = EXPLORING)
   - freeze: 拍板冻结当前探讨成果 (mode = FROZEN)
   - suspend/resume: 临时插话挂起与主线恢复
4. 自动生成并同步输出终端极简 Breadcrumbs 提示卡片。
"""

import sys
import os
import json
import argparse
from pathlib import Path
from typing import Dict, Any, List, Optional

PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
AI_DIR = PROJECT_ROOT / ".ai"
DAG_STATE_FILE = AI_DIR / "dag_state.json"
TIER1_FILE = AI_DIR / "tier1_snapshot.md"

# 16 篇 SOP 映射的标准 DAG 节点序列 (工位铁链)
DAG_NODES = [
    {
        "id": "LM-00",
        "name": "意图路由与模式判定",
        "sop": ".ai/sop/00-sop-overview.md",
        "desc": "用户意图自适应识别，分流主轨与快轨",
        "gate": None,
        "next": ["LM-01"]
    },
    {
        "id": "LM-01",
        "name": "需求切片与边界规划",
        "sop": ".ai/sop/01-sop-planning.md",
        "desc": "输出 PRD、白话需求四态模型与 S1-S3 披萨切片清单",
        "gate": None,
        "next": ["LM-02"]
    },
    {
        "id": "LM-02",
        "name": "双轨延迟汇合定义 (SRS/TC)",
        "sop": ".ai/sop/01-sop-planning.md",
        "desc": "研发规格 SRS 冻结 & 测试用例矩阵 TC 冻结",
        "gate": None,
        "next": ["LM-03"]
    },
    {
        "id": "LM-03",
        "name": "方案设计与契约冻结",
        "sop": ".ai/sop/02-sop-tech-design.md",
        "desc": "生成 detail.md、API契约标准与 service.sql+rollback.sql 配对",
        "gate": "python3 .ai/scripts/gate.py --sql",
        "next": ["LM-04"]
    },
    {
        "id": "LM-04",
        "name": "TDD 红绿循环编码",
        "sop": ".ai/sop/03-sop-coding.md",
        "desc": "红灯单测先导 ➔ 最小化业务实现 ➔ 单测全绿，严禁假 Mock",
        "gate": None,
        "next": ["GATE-01"]
    },
    {
        "id": "GATE-01",
        "name": "机器门禁自动化扫描",
        "sop": ".ai/sop/04-sop-code-review.md",
        "desc": "gate.py 8 项静态规则扫描与契约一致性物理核验",
        "gate": "python3 .ai/scripts/gate.py --staged",
        "next": ["LM-05"]
    },
    {
        "id": "LM-05",
        "name": "架构与契约评审",
        "sop": ".ai/sop/05-sop-review.md",
        "desc": "三门禁核验，防架构分层破坏与结构性劣化",
        "gate": None,
        "next": ["LM-06"]
    },
    {
        "id": "LM-06",
        "name": "集成联调与真实穿透",
        "sop": ".ai/sop/06-sop-integration.md",
        "desc": "TC 矩阵真实穿透、前后端 0-Mock 联调与 0-Error 构建",
        "gate": None,
        "next": ["GATE-02"]
    },
    {
        "id": "GATE-02",
        "name": "双向终验与唯一归档出口",
        "sop": ".ai/sop/07-sop-acceptance.md",
        "desc": "board.py verify 4项检查通过 + 用户显式 --accept 放行",
        "gate": "python3 .ai/scripts/board.py verify",
        "next": ["LM-07"]
    },
    {
        "id": "LM-07",
        "name": "零公网离线部署",
        "sop": ".ai/sop/08-sop-deploy.md",
        "desc": "离线镜像、端口收敛与最小权限验证",
        "gate": None,
        "next": ["LM-08"]
    },
    {
        "id": "LM-08",
        "name": "Git 发布与版本冻结",
        "sop": ".ai/sop/09-sop-git-release.md",
        "desc": "JGit Tag 自动化打标与 Release 归档",
        "gate": None,
        "next": []
    }
]

NODE_DICT = {node["id"]: node for node in DAG_NODES}
NODE_ORDER = [node["id"] for node in DAG_NODES]


def load_state() -> Dict[str, Any]:
    """读取当前 DAG 状态，若不存在则初始化"""
    if DAG_STATE_FILE.exists():
        try:
            with open(DAG_STATE_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass

    # 默认初始状态
    initial_state = {
        "active_module": "req-management",
        "current_task": "Task-Init: 工作流启动",
        "current_node": "LM-01",
        "mode": "FROZEN",  # EXPLORING or FROZEN
        "suspended_context": None,
        "nodes": {
            node["id"]: {
                "status": "COMPLETED" if i == 0 else ("IN_PROGRESS" if i == 1 else "PENDING"),
                "skip_reason": None,
                "artifacts": []
            }
            for i, node in enumerate(DAG_NODES)
        }
    }
    save_state(initial_state)
    return initial_state


def save_state(state: Dict[str, Any]) -> None:
    """持久化保存 DAG 状态"""
    with open(DAG_STATE_FILE, "w", encoding="utf-8") as f:
        json.dump(state, f, indent=2, ensure_ascii=False)


def get_next_node_id(current_id: str) -> Optional[str]:
    """获取铁链上的紧邻下一节点"""
    if current_id not in NODE_ORDER:
        return None
    idx = NODE_ORDER.index(current_id)
    if idx + 1 < len(NODE_ORDER):
        return NODE_ORDER[idx + 1]
    return None


def cmd_status(args) -> int:
    """打印当前 DAG 工位路标与全景链条"""
    state = load_state()
    current_id = state.get("current_node", "LM-01")
    current_node = NODE_DICT.get(current_id, DAG_NODES[0])
    mode = state.get("mode", "FROZEN")
    active_module = state.get("active_module", "default")
    current_task = state.get("current_task", "无")
    next_id = get_next_node_id(current_id)
    next_node = NODE_DICT.get(next_id) if next_id else None

    # 输出链条状态可视化
    print(f"\n{'=' * 68}")
    print(f"⛓️  DAG 铁链路标引擎 · 当前工位全景 [模块: {active_module}]")
    print(f"🎯 当前焦点任务: {current_task}")
    print(f"{'=' * 68}")

    chain_visual = []
    for node in DAG_NODES:
        n_id = node["id"]
        n_stat = state["nodes"].get(n_id, {}).get("status", "PENDING")
        if n_id == current_id:
            chain_visual.append(f"【👉 {n_id} (当前工位)】")
        elif n_stat == "COMPLETED":
            chain_visual.append(f"{n_id}🟢")
        elif n_stat == "SKIPPED":
            chain_visual.append(f"{n_id}⏭️")
        else:
            chain_visual.append(f"{n_id}⚪")

    print(" ➔ ".join(chain_visual))
    print(f"{'-' * 68}")
    print(f"📍 当前工位名称: [{current_id}] {current_node['name']}")
    print(f"📜 对应指导SOP: {current_node['sop']}")
    print(f"🧭 节点工位职责: {current_node['desc']}")
    print(f"⚙️ 模式状态: {'🔍 EXPLORING (深入推演中)' if mode == 'EXPLORING' else '🧊 FROZEN (已就绪/待流转)'}")

    if current_node.get("gate"):
        print(f"🚦 单向物理门禁: `{current_node['gate']}`")

    print(f"\n🧭 下一步建议操作 (Breadcrumbs):")
    if next_node:
        print(f"  1. 顺推到下一站 ➔ python3 .ai/scripts/dag_chain.py advance")
        print(f"     目标: [{next_node['id']}] {next_node['name']}")
    else:
        print(f"  🎉 当前已到达 DAG 交付终点站！")

    print(f"  2. 原地深入推演 ➔ python3 .ai/scripts/dag_chain.py explore")
    print(f"  3. 显式跳过本站 ➔ python3 .ai/scripts/dag_chain.py skip --reason '跳过说明'")
    print(f"{'=' * 68}\n")
    return 0


def cmd_advance(args) -> int:
    """顺向推进到下一工位 (ADVANCE)"""
    state = load_state()
    current_id = state.get("current_node", "LM-01")
    next_id = get_next_node_id(current_id)

    if not next_id:
        print(f"⚠️  当前已是终点站 [{current_id}]，无法继续推进。")
        return 0

    # 标记当前节点完成
    state["nodes"][current_id]["status"] = "COMPLETED"
    if args.artifact:
        state["nodes"][current_id]["artifacts"].append(args.artifact)

    # 激活下一节点
    state["current_node"] = next_id
    state["mode"] = "FROZEN"
    state["nodes"][next_id]["status"] = "IN_PROGRESS"
    save_state(state)

    next_node = NODE_DICT[next_id]
    print(f"✅ [ADVANCE] 成功顺推！")
    print(f"   离开工位: [{current_id}] {NODE_DICT[current_id]['name']} ➔ COMPLETED 🟢")
    print(f"   进入工位: [{next_id}] {next_node['name']} ➔ IN_PROGRESS 🚀")
    print(f"   对应加载 SOP: {next_node['sop']}")
    return 0


def cmd_skip(args) -> int:
    """显式跳过工位 (SKIP)"""
    state = load_state()
    current_id = state.get("current_node", "LM-01")
    next_id = get_next_node_id(current_id)

    if not next_id:
        print(f"⚠️  终点站不允许跳过。")
        return 1

    reason = args.reason or "User skipped without reason"
    state["nodes"][current_id]["status"] = "SKIPPED"
    state["nodes"][current_id]["skip_reason"] = reason

    # 激活下一节点
    state["current_node"] = next_id
    state["mode"] = "FROZEN"
    state["nodes"][next_id]["status"] = "IN_PROGRESS"
    save_state(state)

    next_node = NODE_DICT[next_id]
    print(f"⏭️  [SKIP] 成功跳过工位 [{current_id}]！")
    print(f"   登记原因: {reason}")
    print(f"   自动切入下一工位: [{next_id}] {next_node['name']} 🚀")
    return 0


def cmd_explore(args) -> int:
    """进入原地深入探讨模式 (EXPLORE)"""
    state = load_state()
    state["mode"] = "EXPLORING"
    save_state(state)
    current_id = state.get("current_node", "LM-01")
    print(f"🔍 [EXPLORE] 当前工位 [{current_id}] 已置为深入探讨模式 (EXPLORING)！")
    print(f"   AI 将全力作为技术智囊协助推演，暂停催促进度。满意后可执行 freeze 冻结。")
    return 0


def cmd_freeze(args) -> int:
    """探讨完成，拍板冻结 (FREEZE)"""
    state = load_state()
    state["mode"] = "FROZEN"
    save_state(state)
    current_id = state.get("current_node", "LM-01")
    print(f"🧊 [FREEZE] 当前工位 [{current_id}] 探讨成果已拍板冻结 (FROZEN)！")
    print(f"   随时可执行 advance 进入下一工位。")
    return 0


def cmd_set(args) -> int:
    """直接将当前工位定位到指定节点"""
    target_id = args.node
    if target_id not in NODE_DICT:
        print(f"❌ 错误: 未知节点 ID [{target_id}]。可选节点: {', '.join(NODE_ORDER)}")
        return 1

    state = load_state()
    old_id = state.get("current_node")
    state["current_node"] = target_id
    state["mode"] = "FROZEN"
    state["nodes"][target_id]["status"] = "IN_PROGRESS"
    if args.task:
        state["current_task"] = args.task
    if args.module:
        state["active_module"] = args.module
    save_state(state)

    print(f"🎯 [SET] 工位指针已重定向: [{old_id}] ➔ [{target_id}] ({NODE_DICT[target_id]['name']})")
    return 0


def main():
    parser = argparse.ArgumentParser(description="DAG 铁链路标状态机运行时引擎")
    subparsers = parser.add_subparsers(dest="subcommand", help="子命令")

    # status
    sub = subparsers.add_parser("status", help="查看当前 DAG 铁链路标状态与下一步建议")

    # advance
    adv = subparsers.add_parser("advance", help="顺向推进至下一工位 (Node N -> N+1)")
    adv.add_argument("--artifact", help="本次工位交付的物料文件路径")

    # skip
    skp = subparsers.add_parser("skip", help="显式跳过当前工位并记录原因")
    skp.add_argument("--reason", required=True, help="跳过本工位的审计原因")

    # explore
    subparsers.add_parser("explore", help="切换至原地深入探讨模式")

    # freeze
    subparsers.add_parser("freeze", help="探讨完成，拍板冻结成果")

    # set
    st = subparsers.add_parser("set", help="手动重定向当前工位指针")
    st.add_argument("--node", required=True, help=f"目标节点 ID (如 LM-03)")
    st.add_argument("--task", help="绑定的任务标题")
    st.add_argument("--module", help="所属业务模块名")

    args = parser.parse_args()

    if not args.subcommand or args.subcommand == "status":
        return cmd_status(args)
    elif args.subcommand == "advance":
        return cmd_advance(args)
    elif args.subcommand == "skip":
        return cmd_skip(args)
    elif args.subcommand == "explore":
        return cmd_explore(args)
    elif args.subcommand == "freeze":
        return cmd_freeze(args)
    elif args.subcommand == "set":
        return cmd_set(args)
    else:
        parser.print_help()
        return 1


if __name__ == "__main__":
    sys.exit(main())
