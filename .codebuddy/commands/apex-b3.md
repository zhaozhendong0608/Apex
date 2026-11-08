---
description: "Apex SOP [B3] 开发期排错 · 根因 + 最小切口修复"
argument-hint: "<粘贴完整报错堆栈>"
allowed-tools: Read, Write, Edit, Bash, Grep, Glob
---
按 Apex SOP `B3`（开发期排错）执行：$ARGUMENTS

1. 只读 `.ai/sop/B3-sop-debug.md`；读到 `<!-- ⛔ EXEC-STOP -->` 立即停，禁止预读其它 SOP。
2. 遵守 `.codebuddy/CODEBUDDY.md` 的「斜杠命令通用执行规程」：旧编号迁移、`gate.py` 前置检查、`board.py` 状态约束、`[7]` 人工放行、卡片 ≤6 行。
3. 本阶段交付：根因 + 最小切口修复。
