# 🗺️ 01 - 业务需求全景地图与 PRD 导航中心 (Requirements Master MAP)

> 本文档是整个个人多模块学习平台 (Learning Platform) 的**需求全景总导航地图**。通过“模块可视化拓扑”与“模块矩阵表”，快速检索各业务模块下的具体 PRD 文件。

---

## 🧭 1. 模块化需求可视化思维地图 (Mindmap Visual Navigation)

```mermaid
mindmap
  root((个人多模块学习平台 Learning Platform))
    core[🏛️ 核心平台支撑 Platform Core]
      prd_sys_01["📄 [PRD-SYS-01] 账号认证与管理员开户"]
      prd_sys_02["📄 [PRD-SYS-02] 角色权限与平台通用基础设施"]
    net_sim[💡 核心模块一：网络拓扑仿真系统 Network Sim]
      prd_net_01["📄 [PRD-NET-01] AntV X6 拖拽式拓扑绘制与序列化持久化"]
      prd_net_02["📄 [PRD-NET-02] BFS 连通性寻路与 ACL 防火墙拦截算法引擎"]
      prd_net_03["📄 [PRD-NET-03] TDengine 仿真指标与时序日志流存储"]
      prd_net_04["📄 [PRD-NET-04] Linux 容器网络沙箱互联 (高级演进)"]
    english[🔮 核心模块二 (预留)：英语学习模块 English]
      prd_eng_01["📄 [PRD-ENG-01] 词汇图谱与情景语料演练"]
    req_mgmt[📦 核心模块三：需求治理平台 Req Management]
      prd_req_01["📄 [PRD-REQ-01] 需求与版本全生命周期治理"]
      prd_req_02["📄 [FR-REQ-WFC-01] 工程流水线拓扑画布可视区治理"]
      prd_req_03["📄 [FR-REQ-WFC-02] 拓扑数据驱动改造 (16篇 SOP 入库)"]
```

---

## 📊 2. 业务模块矩阵表与 PRD 文件索引 (Module PRD Index Table)

| 业务模块名称 | 模块对应物理工程 | 核心 PRD 关联文件 | 关联需求 ID | 当前开发状态 | 责任角色 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **🏛️ 平台基础设施 & 认证** | `backend/platform-common` + `platform-server` | `docs/modules/sys_platform/prd-sys-platform-v1.0.md` | `FR-SYS-01` | `🟢 [x]` 已就绪 | 架构师 |
| **💡 网络拓扑仿真 (核心)** | `backend/module-network-sim` + `frontend/src/modules/network-sim` | `docs/modules/network_sim/prd-network-sim-v1.0.md` | `FR-NET-01` | `🔴 [/]` 规划中 | 架构师 / 算法工程师 |
| **🔮 英语学习 (预留)** | `backend/module-english` + `frontend/src/modules/english` | `docs/modules/english/prd-english-v1.0.md` | `FR-ENG-01` | `⚪ [ ]` 待规划 | 产品专家 |
| **📦 需求治理平台** | `backend/platform-server/req` + `frontend/src/modules/req-management` | `docs/modules/req-management/prd-req-management-v1.0.md` | `FR-REQ-01` | `🟢 [x]` 已就绪 | 架构师 |
| **🗺️ 流水线拓扑画布可视区** | `frontend/src/modules/req-management/views/ReqManagementView.vue` | `docs/modules/req-management/prd-workflow-canvas-viewport-v1.0.md` | `FR-REQ-WFC-01` | `🔴 [/]` 规划中 | 前端 / 架构师 |
| **🔌 拓扑数据驱动改造** | `docs/db/migrations/` + `backend/.../req/` + `frontend/.../req-management` | `docs/modules/req-management/prd-workflow-sop-data-driven-v1.0.md` | `FR-REQ-WFC-02` | `🔴 [/]` 规划中 | 架构师 / 前端 / 后端 |

---

## 🔴 3. 全局迭代需求池 (Active PRD Backlog)

> 💡 **需求状态说明**：`- [ ]` 待规划/待开发 | `- [/]` 进行中 | `- [x]` 已验收归档

- [x] **FR-SYS-01 [模块: sys_platform]**：统一认证、管理员开户与通用响应骨架 ➔ `docs/modules/sys_platform/` (已归档)
- [/] **FR-NET-01 [模块: network_sim]**：网络拓扑仿真系统 (多模块改造、BFS寻路、ACL规则拦截、X6画布交互) ➔ `docs/modules/network_sim/prd-network-sim-v1.0.md`
- [ ] **FR-ENG-01 [模块: english]**：个人英语学习与复习平台 (后续预留模块)
- [/] **FR-REQ-01 [模块: req-management]**：需求与版本全生命周期治理 (四层清单 / 基线冻结 / 客户免登签署) ➔ `docs/modules/req-management/prd-req-management-v1.0.md`
- [/] **FR-REQ-WFC-01 [模块: req-management]**：工程流水线拓扑画布固定 360px 可视区 + 滚动条 + 平移拖拽共存 (消除 X6 尺寸回写导致的无限膨胀) ➔ `docs/modules/req-management/prd-workflow-canvas-viewport-v1.0.md` ｜ 原型 `mockup-workflow-canvas-viewport.html` ｜ SRS `docs/srs/workflow-canvas-viewport/srs.md` ｜ TC 已挂起
- [/] **FR-REQ-WFC-02 [模块: req-management]**：拓扑数据驱动改造 —— 流程定义 + 每需求工位状态全量入库，参考 `WORKFLOW_GUIDE.md` §2/§8 的 0~13 全流程并以现行 16 篇 SOP 为口径，保留 L/S 双轨、接口失败禁止降级 ➔ `docs/modules/req-management/prd-workflow-sop-data-driven-v1.0.md` ｜ 原型 `mockup-workflow-sop-data-driven.html` ｜ SRS `docs/srs/workflow-sop-data-driven/srs.md` ｜ TC 已挂起

---

## 📦 4. 已归档需求与历史版本快照 (Archived Requirements)

| 需求编号 | 需求名称 | 所属模块 | 归档版本 / Milestone | 归档日期 | 验证状态 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **FR-SYS-01** | 通用管理平台全栈基础骨架构建 | sys_platform | Milestone-1.3 | 2026-10-07 | 🟢 已归档 |
| **SKILL-01** | 全套技能体系与导航卡片落地 | workflow | Milestone-1.4 | 2026-10-07 | 🟢 已归档 |
