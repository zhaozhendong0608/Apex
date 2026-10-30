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
```

---

## 📊 2. 业务模块矩阵表与 PRD 文件索引 (Module PRD Index Table)

| 业务模块名称 | 模块对应物理工程 | 核心 PRD 关联文件 | 关联需求 ID | 当前开发状态 | 责任角色 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **🏛️ 平台基础设施 & 认证** | `backend/platform-common` + `platform-server` | `docs/modules/sys_platform/prd-sys-platform-v1.0.md` | `FR-SYS-01` | `🟢 [x]` 已就绪 | 架构师 |
| **💡 网络拓扑仿真 (核心)** | `backend/module-network-sim` + `frontend/src/modules/network-sim` | `docs/modules/network_sim/prd-network-sim-v1.0.md` | `FR-NET-01` | `🔴 [/]` 规划中 | 架构师 / 算法工程师 |
| **🔮 英语学习 (预留)** | `backend/module-english` + `frontend/src/modules/english` | `docs/modules/english/prd-english-v1.0.md` | `FR-ENG-01` | `⚪ [ ]` 待规划 | 产品专家 |

---

## 🔴 3. 全局迭代需求池 (Active PRD Backlog)

> 💡 **需求状态说明**：`- [ ]` 待规划/待开发 | `- [/]` 进行中 | `- [x]` 已验收归档

- [x] **FR-SYS-01 [模块: sys_platform]**：统一认证、管理员开户与通用响应骨架 ➔ `docs/modules/sys_platform/` (已归档)
- [/] **FR-NET-01 [模块: network_sim]**：网络拓扑仿真系统 (多模块改造、BFS寻路、ACL规则拦截、X6画布交互) ➔ `docs/modules/network_sim/prd-network-sim-v1.0.md`
- [ ] **FR-ENG-01 [模块: english]**：个人英语学习与复习平台 (后续预留模块)

---

## 📦 4. 已归档需求与历史版本快照 (Archived Requirements)

| 需求编号 | 需求名称 | 所属模块 | 归档版本 / Milestone | 归档日期 | 验证状态 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **FR-SYS-01** | 通用管理平台全栈基础骨架构建 | sys_platform | Milestone-1.3 | 2026-10-07 | 🟢 已归档 |
| **SKILL-01** | 全套技能体系与导航卡片落地 | workflow | Milestone-1.4 | 2026-10-07 | 🟢 已归档 |
