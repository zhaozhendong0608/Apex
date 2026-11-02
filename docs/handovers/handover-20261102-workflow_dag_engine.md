# 📦 项目交接与架构变更卡片 (handover-20261102-workflow_dag_engine.md)

## 📌 1. 本次会话核心成果与关键决策
- **AI 辅助编程有向无环图 (DAG) 认知体系确立**：
  - 深度剖析 AI 辅助编程的底层数学模型——本质为有向无环图（DAG），大模型为图上各工位临时调度的专职工人。
  - 规范了四大控制原语：`ADVANCE`（顺推）、`SKIP`（跨站放行）、`EXPLORE`（原地推演）、`SUSPEND/RESUME`（插播挂起与恢复），实现零额外 Token 损耗与自动路标引导。
- **DAG 铁链路标状态机运行时引擎落盘**：
  - 编写并落地 `.ai/scripts/dag_chain.py`，实现 `status`、`advance`、`skip`、`explore`、`freeze`、`set` 等原子工位流转命令。
  - 建立 `.ai/dag_state.json` 极简物理状态文件，每次交互仅读写极小 JSON 指针，从根本上解决 Token 膨胀。
- **工作流可视化指南全景升级**：
  - 更新 `workflow_guide.html`，新增置顶「⛓️ DAG 铁链路标引擎 (核心)」面板。
  - 采用兼容 Mermaid 10.9.8 的标准 `graph TD` 语法绘制 16 篇 SOP 全量映射的有向无环图拓扑，支持自由平移与缩放。
  - 详实整理了节点属性数据模型（Node Attribute Schema）与运行时控制原语对照表。
- **三层记忆数据库模型与研发大屏原型**：
  - 梳理了本地 5 大记忆文件向数据库演进的 4 张核心表结构（`t_ai_project_snapshot`, `t_ai_module_index`, `t_ai_task_board`, `t_ai_handover_log`），并融入 `git_branch` 彻底避免分支撕裂。
  - 落地 `ai_memory_dashboard_mockup.html` 高保真深色研发治理大屏原型，直观呈现三层记忆与 DAG 状态机动态大屏效果。

---

## 🛠️ 2. 本次改动核心文件清单
1. **工作流中枢与执行脚本**：
   - `.ai/scripts/dag_chain.py` (DAG 铁链路标状态机运行时脚本)
   - `.ai/dag_state.json` (DAG 工位状态持久化文件)
2. **可视化指南与大屏原型**：
   - `workflow_guide.html` (新增 DAG 引擎面板、Mermaid 拓扑修复与属性模型)
   - `ai_memory_dashboard_mockup.html` (三层记忆与 DAG 工位研发大屏高保真原型)

---

## 💡 3. 关键架构决策与踩坑预防 (KI)
1. **Mermaid 兼容性守则**：内嵌 Mermaid 10.9.8 引擎中，避免使用实验性的加粗箭头 `==>`、`== "文本" ==>` 以及子图内部的 `direction TB`，必须统一使用标准 `-->|文本|` 连接，防止语法异常中断渲染。
2. **状态与代码同源绑定**：工作流任务状态若要落库，必须引入 `git_branch` 作为关键复合主键，防止切换 Git 分支时本地代码与数据库状态产生不可调和的撕裂（State Divergence）。
3. **单节点按需加载**：大模型对话时严格按工位指针只挂载当前单篇 SOP，严禁在普通微创或插播问答中全量注入全局 SOP，确保上下文干净并压降 Token 成本。

---

## 🚀 4. 下一步工作建议
- 当前工作区干净，看板无未完成任务，DAG 状态机就绪（`LM-01` 待命）。
- 新会话打开后，直接输入 `[B6]` 即可读取快照快速切入工作。
