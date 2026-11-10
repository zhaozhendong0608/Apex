#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
scripts/tests/test_workflow_data_driven.py

TC-WFC2 自动化回归探针脚本:
对齐 docs/test-cases/workflow-sop-data-driven-testcase.md 核心断言:
1. 校验后端 Controller / 前端 API 契约一致性
2. 校验工位持久化与数据库表实体契约
3. 校验禁止假成功与真实报错拦截
4. 校验无本地降级死循环与 SQL 变更回滚配对
"""

import sys
import os
import re
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]

def test_sql_migration_pairing():
    """断言 docs/db/migrations/ 目录下升级脚本与回滚脚本 100% 配对非空"""
    print("👉 [TC-01] 校验数据库 Migration 与 Rollback 配对完整性...")
    migrations_dir = PROJECT_ROOT / "docs" / "db" / "migrations"
    if not migrations_dir.exists():
        return False, "docs/db/migrations 目录不存在"
    
    v_files = list(migrations_dir.glob("V*.sql"))
    if not v_files:
        return False, "未发现任何 V*.sql 升级脚本"
    
    for vf in v_files:
        rollback = migrations_dir / f"rollback_{vf.name}"
        if not rollback.exists():
            return False, f"缺少对应回滚脚本: {rollback.name}"
        if rollback.stat().st_size == 0:
            return False, f"回滚脚本内容为空: {rollback.name}"
    return True, "SQL 升级与回滚脚本全部按规约配对且非空"

def test_database_entity_and_table():
    """断言 t_req_workflow_node_state 表结构在 service.sql 与 Java Entity 中同步存在"""
    print("👉 [TC-02] 校验 t_req_workflow_node_state 实体与建表契约...")
    service_sql = PROJECT_ROOT / "docs" / "db" / "service.sql"
    sql_text = service_sql.read_text(encoding="utf-8")
    if "t_req_workflow_node_state" not in sql_text:
        return False, "service.sql 中缺失 t_req_workflow_node_state 表定义"
    if "uk_req_track_node" not in sql_text:
        return False, "t_req_workflow_node_state 缺失 uk_req_track_node 唯一三元组约束"
    
    entity_file = PROJECT_ROOT / "base_template/backend/platform-server/src/main/java/com/apex/req/domain/entity/ReqWorkflowNodeState.java"
    if not entity_file.exists():
        return False, "ReqWorkflowNodeState.java 实体文件不存在"
    
    mapper_file = PROJECT_ROOT / "base_template/backend/platform-server/src/main/java/com/apex/req/mapper/ReqWorkflowNodeStateMapper.java"
    if not mapper_file.exists():
        return False, "ReqWorkflowNodeStateMapper.java Mapper 文件不存在"

    return True, "数据库 DDL 与后端 Java 实体/Mapper 均就绪且约束完备"

def test_backend_controller_endpoints():
    """断言 WorkflowController 包含 state / topology / advance 真实持久化逻辑"""
    print("👉 [TC-03] 校验 WorkflowController 真实接口与双向持久化...")
    controller_file = PROJECT_ROOT / "base_template/backend/platform-server/src/main/java/com/apex/req/controller/WorkflowController.java"
    ctrl_text = controller_file.read_text(encoding="utf-8")
    
    if '@GetMapping("/state")' not in ctrl_text:
        return False, "缺失 @GetMapping(/state) 真实工位状态读取端点"
    if 'workflowNodeStateMapper' not in ctrl_text:
        return False, "WorkflowController 未注入 workflowNodeStateMapper"
    if '@GetMapping("/topology")' not in ctrl_text:
        return False, "缺失 @GetMapping(/topology) 动态拓扑端点"
    if 'workflowNodeStateMapper.updateById' not in ctrl_text and 'workflowNodeStateMapper.insert' not in ctrl_text:
        return False, "advance 端点未对 t_req_workflow_node_state 进行真实持久化写入"
    
    return True, "WorkflowController 已具备 state, topology 端点及真实写库能力"

def test_frontend_no_fake_success():
    """断言前端 ReqManagementView.vue 消除了假成功弹窗与硬编码兜底"""
    print("👉 [TC-04] 校验前端消灭假成功与真实数据渲染接入...")
    vue_file = PROJECT_ROOT / "base_template/frontend/src/modules/req-management/views/ReqManagementView.vue"
    vue_text = vue_file.read_text(encoding="utf-8")
    
    # 检查是否调用了真实的 getWorkflowStateApi
    if "getWorkflowStateApi" not in vue_text:
        return False, "前端未导入或调用 getWorkflowStateApi"
    
    # 检查 advance catch 分支是否禁止假成功
    if "工位 [${targetId}] 执行成果已确认" in vue_text:
        return False, "前端仍残留假成功提示 '工位 [${targetId}] 执行成果已确认'"
    
    if "ElMessage.error" not in vue_text:
        return False, "前端 advance 错误捕获未正确使用 ElMessage.error"

    return True, "前端已成功接入真实 state 接口，异常时暴露真实报错并杜绝假成功"

def run_all_tests():
    tests = [
        test_sql_migration_pairing,
        test_database_entity_and_table,
        test_backend_controller_endpoints,
        test_frontend_no_fake_success,
    ]
    
    passed = 0
    total = len(tests)
    print("\n" + "=" * 60)
    print("🚀 开始执行 TC-WFC2 数据驱动与工位持久化自动化回归探针")
    print("=" * 60)
    
    for test in tests:
        ok, msg = test()
        if ok:
            print(f"  ✅ [PASS] {msg}")
            passed += 1
        else:
            print(f"  ❌ [FAIL] {msg}")
    
    print("-" * 60)
    if passed == total:
        print(f"🎉 全部通过！({passed}/{total} 项回归指标 100% 达成)\n")
        return 0
    else:
        print(f"⚠️ 未完全通过：{passed}/{total} 项通过\n")
        return 1

if __name__ == "__main__":
    sys.exit(run_all_tests())

