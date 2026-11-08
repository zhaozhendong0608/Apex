# 🧪 TC-WFC2-V1.0: 工程流水线拓扑数据驱动改造 测试说明书

> **编码前 (Test-First) 制作，当前状态：🟡 挂起待用**
> 依据 [`prd-workflow-sop-data-driven-v1.0.md`](../modules/req-management/prd-workflow-sop-data-driven-v1.0.md) 与 [`srs.md`](../srs/workflow-sop-data-driven/srs.md)
> **就绪红线**：TC 最迟在 `[8]` 启动前 100% 就绪；本轮与 PRD/SRS 同时挂起，`[3]` 编码时落成自动化探针。

---

## 📌 1. 测试基础信息与契约基线

- **测试模块**：`req-management` / 页面 `/req/workflow` ｜ **需求**：`FR-REQ-WFC-02`
- **测试类型**：接口契约 + 数据库落库断言 + 前端 DOM / 错误态 + 既有画布回归
- **被测对象**：
  - `GET /req/workflow/topology?track=`（流程定义）
  - `GET /req/workflow/state?reqCode=&track=`（工位状态）
  - `POST /req/workflow/advance`（推进落库）
  - `POST /req/workflow/sync-from-sop`（定义同步，若 `[2]` 纳入首版）
  - `.wf-viewport` 360px 画布（Task-510 契约回归）
- **运行环境**：Vite dev `http://localhost:3000/req/workflow` + Spring Boot 后端 + MySQL 8.0，登录态 `admin`
- **基线数据**：现行 16 篇 SOP（`[0]~[9]` + `[B1]~[B6]`），来自 `.ai/sop/*.md`；文案字典来自 `WORKFLOW_GUIDE.md` §8
- **统一口径**：节点数 L 轨 = 10、S 轨 = 6；错误态下画布节点数必须为 0

---

## 📋 2. 测试用例详细矩阵

### 🟢 2.1 业务正向链路用例 (Business Happy Path)

| 用例 ID | 所属功能 | 测试场景与描述 | 输入 / 操作 | 预期断言 | 覆盖规格 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `TC-WFC2-HP-001` | 拓扑取数 | 打开页面，节点/连线/大区全部来自接口 | 进入 `/req/workflow`，抓取网络请求 | 单轨道拓扑请求数 = 1；渲染节点数 = 接口 `nodes.length`；源码无 `pipelineNodes` / `createGraphNode('SOP-` 字面量 | `SRS-01/04` |
| `TC-WFC2-HP-002` | 口径校验 | 节点 ID 严格为现行 16 篇 | 分别请求 `track=STANDARD` / `FASTTRACK` | L 轨集合 = `[0]..[9]`（10 个），S 轨 = `[B1]..[B6]`（6 个）；**不含** `LM-xx` / `GATE-xx` / `TC-GEN` / `SIGN-OFF` | `SRS-05` |
| `TC-WFC2-HP-003` | 内容字典 | 节点详情与指南 §8 一致 | 逐节点读取职责 / Skill / 产物 / 门禁 | 与 `WORKFLOW_GUIDE.md` §8 全景表 16 行逐行一致，无自造内容 | `SRS-06` |
| `TC-WFC2-HP-004` | 状态隔离 | 同一阶段在不同需求下状态不同 | 设 `[3]` 在 REQ-001 = PENDING、REQ-002 = IN_PROGRESS，分别打开两需求 | 页面状态各自正确，互不覆盖；DB 中两条记录键不同 | `SRS-03` |
| `TC-WFC2-HP-005` | 推进落库 | 推进工位后刷新页面状态不丢 | `POST advance(reqCode=REQ-001, stage=[2], status=COMPLETED)` → 刷新 | 返回 200；DB 有该记录；页面 `[2]` 显示已完成；连点两次 DB 仅一条 | `SRS-02/10` |
| `TC-WFC2-HP-006` | 定义同步 | 改一篇 SOP 后同步生效 | 修改 `.ai/sop/09-sop-git-release.md` 标题 → 执行 `sync-from-sop` | 库中 `[9]` 标题同步变化，且总数仍 16；不产生重复行 | `SRS-08` |
| `TC-WFC2-HP-007` | 首次初始化 | 新需求第一次打开 | 用全新 `reqCode=REQ-900` 请求 state | 首节点 IN_PROGRESS、其余 PENDING，且 DB 中有对应记录；前端无写死初始状态 | `SRS-11` |

---

### 🟡 2.2 UI 交互用例 (UI Interaction)

| 用例 ID | 所属功能 | 测试场景与描述 | 输入 / 操作 | 预期断言 | 覆盖规格 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `TC-WFC2-UI-001` | 双轨切换 | L ↔ S 连续切换 3 轮 | 点击「大需求全链路」/「小Bug极速修补」 | 每轮节点 ID 集合分别等于 10 / 6 篇；DOM 中无上一轨残留节点 | `SRS-07` |
| `TC-WFC2-UI-002` | 不串轨 | L 轨推进后切 S 轨再切回 | L 轨推进 `[2]` → 切 S → 切回 L | S 轨状态不受 L 轨影响；切回 L 后 `[2]` 状态仍在 | `SRS-12` |
| `TC-WFC2-UI-003` | 切换需求 | 下拉切换 REQ-001/002/003 | 依次切换三个需求 | 每次重新取 `state`，节点状态色随之变化；无旧需求状态残留 | `SRS-09` |
| `TC-WFC2-UI-004` | 错误态与重试 | 接口失败后点重试 | 拦截接口返回 500 → 观察 → 恢复接口 → 点「重试」 | 失败时只有错误提示与重试按钮、画布节点数 0；重试成功后正常渲染且错误态消失 | `SRS-14/16/17` |
| `TC-WFC2-UI-005` | 画布兼容 | 滚动 / 平移 / 拖拽 / 缩放 | 在 360px 可视框内逐项操作 | 可视框恒 360px、3 秒尺寸零增长；滚动可达；平移不改 `scrollTop` | `SRS-13` |
| `TC-WFC2-UI-006` | 状态可视化 | 三种状态的视觉区分 | 构造 COMPLETED / IN_PROGRESS / PENDING 节点 | 三种状态颜色/文案可区分，且与 DB 状态一致 | `SRS-09` |

---

### 🔴 2.3 异常防御与红线用例 (Exception & Gatekeeper)

| 用例 ID | 防御场景 | 触发条件 | 预期系统表现 | 覆盖规格 |
| :--- | :--- | :--- | :--- | :--- |
| `TC-WFC2-EX-001` | **禁止兜底节点（核心红线）** | `topology` 返回 500 / 超时 / `data: null` / `nodes: []` | 画布节点数 **0**，显示明确错误态与重试入口；**不得**回退到任何本地节点数组 | `SRS-14` |
| `TC-WFC2-EX-002` | **禁止假成功** | `POST advance` 返回 500 | 页面显示失败原因，可重试；**不得**出现 `ElMessage.success('...已确认')` 之类提示 | `SRS-15` |
| `TC-WFC2-EX-003` | 加载态诚实 | 人为节流接口 2 秒 | 加载中无「先画一批假节点再替换」的中间态 | `SRS-17` |
| `TC-WFC2-EX-004` | **状态必须落库** | 推进成功后把 `.ai/dag_state.json` 改名 / 删除并刷新 | 页面状态不变（仍从库读）；源码无 `ObjectMapper` 读写 `.ai/dags/*.json` | `SRS-02` |
| `TC-WFC2-EX-005` | **字段错位回归** | 检查 `state` 响应与前端读取字段 | 前后端使用同一字段名；前端不再读 `activeNode`；节点 ID 不再出现 `LM-xx` / `GATE-xx` | `SRS-05/15` |
| `TC-WFC2-EX-006` | **禁止兜底假值** | 构造 DB 缺 `ownerName` / `updatedAt` / `reqCode` 的记录 | 显示占位符或报错，**不得**显示 `'张工'` / `'2026-10-29'` / `'REQ-001'` 等写死值 | `SRS-15` |
| `TC-WFC2-EX-007` | Task-510 契约回归 | 复跑 `scripts/tests/test_workflow_canvas_viewport.py` | 8/8 GREEN；360px / `autoResize:false` / 两层 DOM 未被破坏 | `SRS-13` |
| `TC-WFC2-EX-008` | **SQL 配对门禁** | 检查 `docs/db/migrations/` 并跑 `gate.py --sql` | 存在 `V*.sql` 与同版本 `rollback_V*.sql` 且均非空；门禁 `[LGTM]` | `SRS-18` |
| `TC-WFC2-EX-009` | 零新依赖 / 范围红线 | 检查 `[3]` 提交 diff | `package.json` 无新增依赖、`board.py` 语义未改、`.ai/sop/*.md` 正文未改、全屏 `overflow:hidden` 未放开 | `SRS-19/20` |

---

## 📌 3. 回归探针与执行顺序

1. `[2]` 冻结契约后，先把 `TC-WFC2-EX-001/002/005/006` 写成红灯探针（**故意让接口失败，确认页面当前会降级/假成功 → 真红**）。
2. `[3]` 按 TDD 逐条转绿：数据层（表 + Migration）→ 接口层 → 前端渲染 → 错误态。
3. `[6]` 联调按 `HP → UI → EX` 顺序全量跑一遍；`EX` 任一失败视为 `[BLOCKING]`，不得进入 `[7]`。
4. 每轮收尾复跑 `TC-WFC2-EX-007`（Task-510 画布探针），确保旧契约零回归。

---

## 📌 4. 挂起说明

- 本矩阵在 `[1]` 阶段完成设计并挂起，**不在本阶段执行**；接口字段与错误码在 `[2]` 冻结后需回填本文件的入参与断言。
- `[7]` 终验需用户本人给出 4 维清单确认并显式放行，AI 不得自行 `complete --accept`。
- 看板当前 `ACTIVE 1 = Task-510`，本需求 Task 仅登记 TODO，不抢占单焦点。
