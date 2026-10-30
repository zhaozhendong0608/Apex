# [7] 双向验收与唯一归档出口 (07-sop-acceptance)
> **触发**：`[7]`/`验收`/`D`/`标绿` ｜ **前置**：`[6]` 已全绿 ｜ **产出**：任务标绿 + 黑匣子 + 名片刷新

## 📌 核心目标
在标绿 `DONE` 之前逐条核对"目标达成 + 资产同步 + 回滚保障 + 构建通过"，杜绝虚假交付。

## 🛡️ 4 维终验清单（必须向用户呈报证据）
1. **目标与用例**：逐条对照任务的白话验收目标；`docs/test-cases/<m>-testcase.md` 已在 `[6]` 100% 跑通；前端在目标主题下渲染正常。
2. **文档资产同步**：模块 PRD / `docs/srs/<m>/srs.md` / `docs/design/<m>/detail.md` / 用例矩阵已原地演进（含版本变更记录）；若动过服务拓扑或存储选型，`docs/02-architecture.md` 已同步，否则明确"无需变更"。
3. **SQL 回滚保障**：有表结构变更的，`docs/db/migrations/` 必须有对应 `rollback_V<版本>__*.sql`，且能幂等空跑。
4. **0 Error 构建**：前后端构建 0 Error，自动化测试全绿。

## 🤖 三步归档（与 `board.py` v2 严格对齐）

**① 机器核验**
```bash
python3 .ai/scripts/board.py verify "Task-XXX"
```
自动核验 4 项，任一不过即 `[BLOCKING]`、退出码 1：任务在 ACTIVE（**BLOCKED 会被拒绝**）/ 涉及文件真实存在 / SQL 回滚配对 / tier1 焦点一致。

**② 人工放行**
把上面 4 维证据呈报给用户并取得明确确认。**机器通过 ≠ 用户认可**。

**③ 原子归档**
```bash
python3 .ai/scripts/board.py complete "Task-XXX" --accept
```
- 不带 `--accept` → 直接拒绝；带了但 verify 不过 → 内部再核验并拒绝。
- 一条命令原子完成 4 件事：① 标绿 DONE（带完成日期）→ ② 追加 `tier3_handover.md` → ③ 重建 `tier1_snapshot.md`（焦点+里程碑，<30 行）→ ④ 自动把 `TODO` 首个任务激活为 `ACTIVE`。

**④ 确认三件资产**：`tier3_status.md` 在 DONE 区 / `tier3_handover.md` 有新记录 / `tier1_snapshot.md` 焦点已切换。

## 🧷 能力点
`C-7-01` 4 维终验清单；`C-7-02` verify 四项机器核验；`C-7-03` 人工放行 `--accept`；`C-7-04` 原子归档四动作；`C-7-05` SQL 回滚审计；`C-7-06` 文档资产同步审计。

## 📤 交付
卡片 ≤6 行：4 维勾选 + 构建结果 / verify 结论 / 看板前后状态 / 下一个激活焦点。
