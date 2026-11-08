---
description: Apex SOP 指令入口，/apex 1 需求描述、/apex B6 继续、/apex 7 验收、/apex 0 总览
argument-hint: "<编号或触发词> [需求 / 报错 / 说明]"
allowed-tools: Read, Write, Edit, Bash, Grep, Glob
---

按 Apex 工作流执行以下请求：$ARGUMENTS

执行步骤：

1. 读 `.cursorrules` 的「指令路由」表，把 `$ARGUMENTS` 的首个词映射到**唯一一篇** `.ai/sop/*.md`。收到旧编号按迁移表解释：`F` → `[B2]`、`0L` → `[B1]`、用 `3` 表示排错 → `[B3]`。
2. 只读命中的那一篇 SOP；读到 `<!-- ⛔ EXEC-STOP -->` 立即停。禁止预读其它 SOP，禁止读 `WORKFLOW_GUIDE.md`（除非 `[0]` / `[B1]`）。
3. 进入 `[3]` / `[4]` / `[6]` / `[7]` 前先跑 `python3 .ai/scripts/gate.py --stage <n>`；不通过就说明缺什么，不要闷头干。被要求跳步时先给「后果 + 最小补救 + 二选一」。
4. 任务状态一律由 `python3 .ai/scripts/board.py <list|verify|complete --accept|block|unblock|status>` 修改，**严禁手改** `.ai/tier3_status.md`。
5. `[B6]` 复活按 `.ai/tier1_snapshot.md` → `.ai/tier3_status.md` → `.ai/tier3_handover.md` 顺序读，产出 ≤5 行复活卡片。不要启动 `sync_tree.py --watch` 后台进程；需要同步时跑一次 `python3 .ai/scripts/sync_tree.py`。
6. 命中 Skill 场景时，按需读 `.agents/skills/<name>/SKILL.md`（路由见 `.cursorrules §6`），禁止全量装载。
7. `[7]` 验收给出 4 维清单后**等待用户明确放行**，不得自行 `complete --accept`。
8. 输出遵守 `.cursorrules §5`：卡片 ≤6 行，不复述需求，不重复 SOP 正文。
