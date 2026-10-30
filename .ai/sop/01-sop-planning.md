# [1] 需求规划与任务拆解 (01-sop-planning)
> **触发**：`[1]`/`规划`/`需求`/`梳理` ｜ **前置**：无（老项目先跑 `0L`）｜ **产出**：PRD + 原型 + SRS + TC 矩阵 + 看板 Task

## 📌 核心目标
把模糊需求收敛成可执行资产，并完成 **PA→PD→(SRS ∥ TC)** 四段与 Pizza 切片。

> 🧭 开新模块时先对照 [`docs/playbooks/new-module-playbook.md`](../../docs/playbooks/new-module-playbook.md)：它列清了本节点必须产出哪些文件、下游 `[2]` 会依赖什么。

## ✅ 步骤

**① 需求发现**（加载 `requirement-discovery`）
- 内部 8 维扫描：正常 / 失败 / 边界 / 生命周期 / 上下游 / 规模 / 误操作 / 重启恢复。
- 只问**用户能答的事实或取舍**；术语先转白话场景（禁说"幂等""一致性"）。
- 每条信息标状态：`已确认` / `AI推断` / `暂定` / `待确认`；用户不会答就给推荐默认值标 `暂定`。
- 收尾输出《需求基线总结》。

**② PA → PD**
- PA：Epic → User Story → Use Case，划清业务边界。
- PD：结构化 PRD（业务规则 + 交互流程）；大模块调 `archetype-architect` 落 `docs/modules/<m>/prd-<m>-v1.md` + `mockup.html`。
- 同步 `docs/01-requirements.md` 全景图（Mermaid + 模块矩阵）。

**③ SRS 与 TC 双轨（并行，非串行）**
- 研发轨：PRD → 软件规格 → Grill-Me 对撞补隐性逻辑 → `docs/srs/<m>/srs.md`。
- 测试轨：三类矩阵（业务 / UI 交互 / 异常防御）→ `docs/test-cases/<m>-testcase.md`。
- 🚨 **TC 最迟必须在 `8` 启动前 100% 就绪**，提前设计、挂起待用。

**④ Pizza 切片**（15~30 分钟/Task）
- `S1-Bone`：数据结构 / API 契约 / Migration
- `S2-Muscle`：接真实业务逻辑与样式
- `S3-Stub`：核心算法单测或联调探针
- 按 S/M/L 定级，写进标题前缀（如 `[S2-Muscle] xxx`）

**⑤ 登记看板**
```bash
python3 .ai/scripts/board.py add "Task-001" "[S1-Bone] 标题" "白话目标" "涉及文件"
```

## 🛡️ 硬约束
- 需求未稳定前**不进入技术选型**。
- 复合指令 `1+2`/`3+4` 只合并沟通轮次，PRD/SRS/TC 必须 100% 落盘。
- 纯后端任务：原型豁免，TC 免 UI 态。

## 🧷 能力点
`C-1-01` 8 维扫描与白话提问；`C-1-02` 四态管理与默认值；`C-1-03` PA 三层拆解；`C-1-04` PRD+原型与纯后端豁免；`C-1-05` SRS 研发轨；`C-1-06` TC 三类矩阵；`C-1-07` Pizza 三层切片；`C-1-08` 看板登记。

## 📤 交付
卡片 ≤5 行：需求地图已更新 / PRD 路径 / 原型路径 / 首批 Task / TC 已挂起。结尾指向 `[2]` 或 `[3]`。
