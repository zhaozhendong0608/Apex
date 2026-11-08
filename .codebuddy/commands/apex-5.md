---
description: "Apex SOP [5] 人工架构与契约评审 · 评审报告（不改状态、不归档）"
argument-hint: "<可留空：出人工评审报告>"
allowed-tools: Read, Write, Edit, Bash, Grep, Glob
---
按 Apex SOP `5`（人工架构与契约评审）执行：$ARGUMENTS

1. 只读 `.ai/sop/05-sop-review.md`；读到 `<!-- ⛔ EXEC-STOP -->` 立即停，禁止预读其它 SOP。
2. 遵守 `.codebuddy/CODEBUDDY.md` 的「斜杠命令通用执行规程」：旧编号迁移、`gate.py` 前置检查、`board.py` 状态约束、`[7]` 人工放行、卡片 ≤6 行。
3. 本阶段交付：评审报告（不改状态、不归档）。
