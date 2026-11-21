# 📦 [第三层：微观原子任务层] 历史交接黑匣子 (tier3_handover.md) [DEMO 示例 / 样例模板]

> 🏛️ **【三层记忆金字塔 - 第三层：微观历史交接黑匣子 (看过去)】**
> 💡 **使用与读取规约**：本文件记录项目开发过程中每次输入 [B5] 归档时追加的项目 Changelog、关键代码改动与技术决议。作为冷数据备份存储。

---

## 📦 归档交接 [2026-10-24 15:14:08]
* 🎯 完成任务: [Task-201 ~ Task-206] 网络拓扑仿真系统(Module-Network-Sim)全栈闭环
* 🛠️ 本次改动核心文件: backend/pom.xml, module-network-sim/, frontend/src/modules/network-sim/
* 💡 关键决策: Maven多模块重构，纯Java BFS+ACL寻路引擎，X6原生SVG图元，双向属性检查器，通用区域底板与右键删除功能

---

---

## 📦 归档交接 [2026-10-24 18:23:00]
* 🎯 完成任务: [Task-207] 静态路由表(LPM)转发决策与多分支探索日志完全隔离
* 🛠️ 本次改动核心文件: backend/module-network-sim/model/RouteEntry.java, engine/BfsRoutingEngine.java, frontend/src/modules/network-sim/components/PropertyInspector.vue, views/NetworkSimView.vue
* 💡 关键决策: 路由器三层单播下一跳严格锁定避免广播漫游；引入 PathState 结构完全消除多分支探索日志相互串染；明确后续 P0 为画布流动光效与阻断红圈

---

---

## 📦 归档交接 [2026-10-28 14:49:07]
* 🎯 完成任务: [Task-400] [S1-Bone] 工作流 v2 内核加固（脚本/文档/手册）
* 🧭 白话目标: 让 board.py 具备 verify+--accept 与原子归档、gate.py 补齐 4 类门禁与元门禁、全仓文档口径一致并落地新手手册
* 🛠️ 本次改动核心文件: .ai/scripts/board.py, .ai/scripts/gate.py, .ai/scripts/sync_tree.py, BEGINNER_HANDBOOK.md, workflow_guide.html
* ✅ 终验: `board.py verify` 机器核验通过 + 用户显式放行 (--accept)


---

## 📦 归档交接 [2026-10-29 14:24:34]
* 🎯 完成任务: [Task-501] [S1-Bone] 数据库实体类与 MyBatis-Plus 映射层
* 🧭 白话目标: 针对 service.sql 中的 7 张表生成 Java Entity、Mapper 与基础 CRUD 骨架
* 🛠️ 本次改动核心文件: base_template/backend/platform-server/src/main/java/com/apex/req
* ✅ 终验: `board.py verify` 机器核验通过 + 用户显式放行 (--accept)

---

## 📦 归档交接 [2026-10-29 14:29:02]
* 🎯 完成任务: [Task-502] [S1-Bone] 原型静态沙盒目录托管与 SHA256 引擎
* 🧭 白话目标: 实现原型 ZIP 包解压、本地静态托管服务与全目录 SHA256 计算引擎
* 🛠️ 本次改动核心文件: base_template/backend/platform-server/src/main/java/com/apex/req/service/PrototypeService.java
* ✅ 终验: `board.py verify` 机器核验通过 + 用户显式放行 (--accept)

---

## 📦 归档交接 [2026-10-29 14:31:37]
* 🎯 完成任务: [Task-503] [S2-Muscle] 需求与四层清单核心服务及实时代价计算器
* 🧭 白话目标: 实现需求录入、清单项决策切换、人天代价实时代价折算与持久化
* 🛠️ 本次改动核心文件: base_template/backend/platform-server/src/main/java/com/apex/req/service/RequirementService.java
* ✅ 终验: `board.py verify` 机器核验通过 + 用户显式放行 (--accept)

---

## 📦 归档交接 [2026-10-29 14:34:02]
* 🎯 完成任务: [Task-504] [S2-Muscle] 客户免登录签名令牌与防伪电子回执引擎
* 🧭 白话目标: 实现 HMAC 一次性令牌发放、4位防窥码校验、一句话复述存证与防伪回执生成
* 🛠️ 本次改动核心文件: base_template/backend/platform-server/src/main/java/com/apex/req/service/ApprovalService.java
* ✅ 终验: `board.py verify` 机器核验通过 + 用户显式放行 (--accept)

---

## 📦 归档交接 [2026-10-29 14:36:31]
* 🎯 完成任务: [Task-505] [S2-Muscle] 基线冻结看门狗门禁与 JGit Tag 自动化
* 🧭 白话目标: 实现蓝皮书§4.1的4项硬门禁核验、打包归档Zip并在本地代码库自动打标Git Tag
* 🛠️ 本次改动核心文件: base_template/backend/platform-server/src/main/java/com/apex/req/service/BaselineService.java
* ✅ 终验: `board.py verify` 机器核验通过 + 用户显式放行 (--accept)

---

## 📦 归档交接 [2026-10-29 14:38:35]
* 🎯 完成任务: [Task-506] [S3-Stub] 基线增量差异计算引擎与联调探针
* 🧭 白话目标: 实现 BL-v1.0 与 BL-v1.1 的清单/原型/文档三合一增量比对引擎与自动化测试
* 🛠️ 本次改动核心文件: base_template/backend/platform-server/src/main/java/com/apex/req/service/DiffService.java
* ✅ 终验: `board.py verify` 机器核验通过 + 用户显式放行 (--accept)

---

## 📦 归档交接 [2026-10-29 16:10:56]
* 🎯 完成任务: [Task-507] [S2-Muscle] 需求治理平台菜单架构重构与SQL权限初始化
* 🧭 白话目标: 将内嵌项目空间菜单挂载至需求治理平台并补齐数据库菜单权限
* 🛠️ 本次改动核心文件: docs/db/service.sql, docs/db/base.sql, base_template/frontend/src/views/layout/Layout.vue, base_template/frontend/src/router/index.ts, base_template/frontend/src/modules/req-management/views/ReqManagementView.vue
* ✅ 终验: `board.py verify` 机器核验通过 + 用户显式放行 (--accept)

---

## 📦 归档交接 [2026-10-29 16:49:48]
* 🎯 完成任务: [Task-508] [S2-Muscle] 需求工作台全景数据动态对接与项目详情闭环
* 🧭 白话目标: 打通后端Project/Stats真实接口，消除前端假数据与字段映射脱节，实现工作台与需求详情100%动态数据闭环
* 🛠️ 本次改动核心文件: base_template/backend/platform-server/src/main/java/com/apex/req/controller/ProjectController.java, base_template/backend/platform-server/src/main/java/com/apex/req/service/ProjectService.java, base_template/frontend/src/api/req.ts, base_template/frontend/src/modules/req-management/views/ReqManagementView.vue
* ✅ 终验: `board.py verify` 机器核验通过 + 用户显式放行 (--accept)

---

## 📦 归档交接 [2026-10-30 09:22:33]
* 🎯 完成任务: [Task-509] 需求治理平台全链路动态对接与Mock假数据物理清空、项目级联粉碎删除与管理员特权解冻
* 🛠️ 本次改动核心文件: [base_template/frontend/src/modules/req-management/views/ReqManagementView.vue, base_template/frontend/src/api/req.ts, ProjectController.java, ProjectService.java, RequirementController.java, RequirementService.java, docs/db/service.sql]
* 💡 关键决策: 彻底移除前端所有Mock假数据与兜底占位，界面100%直连MySQL真实表与接口；实现项目级联物理粉碎删除与已冻结基线特权解冻闭环；强制utf8mb4字符集确保零乱码。

---

## 📦 归档交接 [2026-11-02 09:40:54]
* 🎯 完成任务: [DAG-Engine] DAG 铁链路标运行时引擎落盘、指南全景升级与三层记忆落库大屏设计
* 🛠️ 本次改动核心文件: .ai/scripts/dag_chain.py, .ai/dag_state.json, workflow_guide.html, ai_memory_dashboard_mockup.html, docs/handovers/handover-20261102-workflow_dag_engine.md
* 💡 关键决策: 明确 AI 编程本质为 DAG 节点属性赋值；落盘 dag_chain.py 实现四大控制原语；workflow_guide.html 引入 Mermaid 标准兼容拓扑；设计带 git_branch 隔离的三层记忆落库模型与大屏原型

---

## 📦 归档交接 [2026-11-10 18:57:05]
* 🎯 完成任务: [Task-513] [S1-Bone] SOP 流程定义与工位状态数据模型 + Migration 配对
* 🧭 白话目标: 新建流程节点/连线/工位状态表并初始化 16 篇 SOP 定义，落 V*.sql 与 rollback_V*.sql 配对，冻结接口字段与错误码
* 🛠️ 本次改动核心文件: docs/db/migrations/, docs/db/service.sql, base_template/backend/platform-server/src/main/java/com/apex/req
* ✅ 终验: `board.py verify` 机器核验通过 + 用户显式放行 (--accept)

---

## 📦 归档交接 [2026-11-10 18:58:53]
* 🎯 完成任务: [Task-514] [S2-Muscle] 后端拓扑/状态/推进接口与 SOP 定义同步
* 🧭 白话目标: 提供 topology/state/advance/sync-from-sop 接口，停用 .ai/dag_state.json 文件读写，统一 current_node 字段并对失败返回真实错误
* 🛠️ 本次改动核心文件: base_template/backend/platform-server/src/main/java/com/apex/req/controller/WorkflowController.java, base_template/frontend/src/api/req.ts
* ✅ 终验: `board.py verify` 机器核验通过 + 用户显式放行 (--accept)

---

## 📦 归档交接 [2026-11-10 19:00:15]
* 🎯 完成任务: [Task-515] [S2-Muscle] 前端数据驱动渲染与错误态改造
* 🧭 白话目标: 删除 pipelineNodes/fasttrackNodes 硬编码，L/S 双轨全量由接口渲染，补错误态与重试，修掉假成功提示与兜底假值
* 🛠️ 本次改动核心文件: base_template/frontend/src/modules/req-management/views/ReqManagementView.vue
* ✅ 终验: `board.py verify` 机器核验通过 + 用户显式放行 (--accept)

---

## 📦 归档交接 [2026-11-10 19:24:10]
* 🎯 完成任务: [Task-516] [S3-Stub] 数据驱动回归探针与 TC 挂接
* 🧭 白话目标: 落 TC-WFC2 三类矩阵自动化探针，含接口失败不降级、状态落库、字段错位回归，并复跑 Task-510 画布 8 条探针
* 🛠️ 本次改动核心文件: scripts/tests/, docs/test-cases/workflow-sop-data-driven-testcase.md
* ✅ 终验: `board.py verify` 机器核验通过 + 用户显式放行 (--accept)

---

## 📦 归档交接 [2026-11-10 20:35:14]
* 🎯 完成任务: [Task-517] 拓扑图元连线动态驱动与消灭兜底数据
* 🧭 白话目标: 从后端模板读取拓扑定义并清除张工/固定日期等兜底假数据
* 🛠️ 本次改动核心文件: base_template/frontend/src/modules/req-management/views/ReqManagementView.vue, base_template/backend/platform-server/src/main/java/com/apex/req/controller/WorkflowController.java
* ✅ 终验: `board.py verify` 机器核验通过 + 用户显式放行 (--accept)

---

## 📦 归档交接 [2026-11-10 21:01:48]
完成了工程流水线与工作流模板的数据库驱动闭环、消灭兜底数据、左右分栏可折叠抽屉重构以及工位详情抽屉暗色高质感配色升级

---

## 📦 归档交接 [2026-11-17 17:15:21]
* 🎯 完成任务: [Task-518] 闭环打通验证任务
* 🧭 白话目标: 打通对话状态落库与全自动契约测试门禁
* 🛠️ 本次改动核心文件: .ai/scripts/board.py, .ai/scripts/contract_guard.py, .ai/scripts/test_runner.py
* ✅ 终验: `board.py verify` 机器核验通过 + 用户显式放行 (--accept)

---

## 📦 归档交接 [2026-11-18 10:45:12]
🎯 完成工程流水线闭环改造：打通对话双写存库、DLD契约看门狗、全自动化测试门禁与DAG智能路标提示

---

## 📦 归档交接 [2026-11-21 16:33:37]
🎯 [B5归档] 优化工作流认知护栏、重构前端需求管理平台交互与视觉：
1. 规则加固：.cursorrules 与 .codebuddy 新增阶段解耦、批判者协议与防疲劳熔断，注入新会话自动恢复钩子
2. 视觉重构：移除各业务子页面突兀粗笨的二级黑条，规约与脚本透视抽屉调整为明亮高质感白底排版
3. 交互升级：引入全局可拖拽智能快捷悬浮球，支持快速录入需求（带项目选择联动）与客户免登视角切换
4. 缺陷修复：解决 /req/workflow 首次打开时 X6 DAG 拓扑图异步挂载竞态与渲染黑屏问题，统一全站科技蓝按钮配色
