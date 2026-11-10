# 📦 项目交接与架构归档卡片 (handover-20261110-workflow_refactor.md)

## 📌 1. 本次会话核心成果与关键决策

1. **工程流水线拓扑数据驱动闭环（Task-513 ~ Task-516）**：
   - 落地新表 `t_req_workflow_node_state`（按 `req_code`, `track_mode`, `node_id` 三元组唯一物理隔离）；
   - 配对 `V1.2__workflow_node_state.sql` 与 `rollback_V1.2__workflow_node_state.sql`，并通过 `gate.py --sql` 机器门禁；
   - 补齐 Java Entity `ReqWorkflowNodeState` 与 MyBatis-Plus Mapper；打通 `state`、`topology`、`advance` 接口；
   - 拔除前端假成功提示与猜测状态逻辑，节点颜色 100% 由 MySQL 真实状态驱动，推进时同步反向投影写入本地 `.ai/tier1_snapshot.md`；
   - 落盘 `scripts/tests/test_workflow_data_driven.py` 自动化回归探针（4/4 PASS 100% 全绿）。

2. **消灭所有兜底假数据与死数据（Task-517）**：
   - 彻底移除了 `ownerName || '张工'`、`updatedAt || '2026-10-29'` 以及硬编码金句兜底，无数据时诚实显示“未指定”或“—”；
   - 流水线大屏的算子元数据与绑定 Skill 统一由后端模板动态提供。

3. **流水线交互与视觉重构（左右分栏 + 折叠抽屉）**：
   - 将原堆叠于底部的需求列表重塑为**左侧「待办需求列表」抽屉**，支持一键折叠为 46px 竖栏以极大释放画布视野；
   - 将原本挤在下方的「当前工位详情」和「审计履历」全部移入**右侧滑出抽屉（Drawer）**，支持从 X6 节点点击直接唤起；
   - 右侧抽屉进行了**现代深海科技渐变、药丸微拟物胶囊切换 (Pill Tabs) 与毛玻璃微质感**全面升级，视觉对比与排版层次大幅提升。

---

## 🛠️ 2. 本次改动核心文件清单

1. **数据库与迁移脚本**：
   - `docs/db/service.sql` (标准全量底座同步入库)
   - `docs/db/migrations/V1.2__workflow_node_state.sql` (增量建表与种子数据)
   - `docs/db/migrations/rollback_V1.2__workflow_node_state.sql` (严格配对回滚)
2. **后端服务层**：
   - `ReqWorkflowNodeState.java` (工位状态实体)
   - `ReqWorkflowNodeStateMapper.java` (持久层 Mapper)
   - `WorkflowController.java` (打通真实 state, topology, advance 接口与模板联动)
3. **前端视图与 API 层**：
   - `base_template/frontend/src/api/req.ts` (导出 getWorkflowStateApi 等)
   - `base_template/frontend/src/modules/req-management/views/ReqManagementView.vue` (左右分栏、抽屉化、配色升级、消灭兜底)
4. **自动化测试与记忆体系**：
   - `scripts/tests/test_workflow_data_driven.py` (自动化回归探针)
   - `.ai/tier1_snapshot.md` / `.ai/tier3_status.md` / `.ai/tier3_handover.md` (三层记忆金字塔同步)

---

## 📊 3. 当前任务状态
- **ACTIVE**: 0 个
- **TODO**: 0 个 (本阶段规划任务 100% 完成)
- **DONE**: 14 个
- **工作区状态**: 前端 `npm run build` 打包全绿无警告，后端单元测试就绪。

---

## 🚀 4. 下一次会话启动指引
- 打开会话后直接输入 `[B6]` 即可读取 `.ai/tier1_snapshot.md` 极速恢复断点；
- 可直接规划下一阶段的任务（如业务模块网络拓扑仿真排版或 DevOps 部署容器化规范）。

