---
description: "Apex SOP [4] 代码审查机器门禁 · `gate.py` 的 `[LGTM]` 或 `[BLOCKING]`"
argument-hint: "<可留空：跑机器门禁>"
allowed-tools: Read, Write, Edit, Bash, Grep, Glob
---
按 Apex SOP `4`（代码审查机器门禁）执行：$ARGUMENTS

1. 只读 `.ai/sop/04-sop-code-review.md`；读到 `<!-- ⛔ EXEC-STOP -->` 立即停，禁止预读其它 SOP。
2. 遵守 `.codebuddy/CODEBUDDY.md` 的「斜杠命令通用执行规程」：旧编号迁移、`gate.py` 前置检查、`board.py` 状态约束、`[7]` 人工放行、卡片 ≤6 行。
3. 本阶段交付：`gate.py` 的 `[LGTM]` 或 `[BLOCKING]`。
