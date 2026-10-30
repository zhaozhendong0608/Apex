# [B5] 上下文清理与记忆交接 (B5-sop-archive)
> **触发**：`[B5]`/`归档`/`下班` ｜ **前置**：无 ｜ **产出**：黑匣子记录 + Handover 文档 + 踩坑卡片

## 📌 核心目标
会话要结束（上下文过长/准备清空）时，把**进展与决策压缩落盘**，让新会话能 100% 复活。

## ✅ 五步
1. **读全盘**：`tier3_status.md` 取 DONE 与 TODO；回顾本会话的关键决策（改了表结构？新增环境变量？架构调整？）。
2. **清垃圾**：丢掉排错过程中的临时日志、长堆栈、无效对话；只留真正改过的核心文件清单。
3. **KI 反哺**：参照 `.agents/skills/quality-verifier/references/self-evolution-and-ki-guide.md`，把高价值踩坑写成 `.agents/skills/quality-verifier/references/knowledge-cards/postmortem-*.md`，并把预防规则反哺进校验规范。
4. **落 Handover**：参照 `.agents/skills/coding-standards/references/agent-handover-protocol.md`，生成 `docs/handovers/handover-{YYYYMMDD}-{MODULE}.md`。
5. **追加黑匣子 + 刷树**：
```bash
python3 .ai/scripts/board.py archive "* 🎯 完成任务: [Task-XXX]
* 🛠️ 本次改动核心文件: [...]
* 💡 关键决策: [...]"
python3 .ai/scripts/sync_tree.py
pkill -f "sync_tree.py --watch" || true
# 黑匣子超 300 行时轮转（旧记录移到 docs/handovers/archive-handover-<年>.md）
python3 .ai/scripts/board.py archive --rotate 40
```

## 🛡️ 与 `[7]` 防重复
- 走 `[7] complete --accept` 时，黑匣子**已自动追加**，此处**不要再 archive**，只补 `docs/handovers/` 文档。
- `archive` 只用于"没有任务、纯阶段性收工"。

## 🧷 能力点
`C-B5-01` 全盘状态扫描；`C-B5-02` 垃圾清理与核心文件提炼；`C-B5-03` KI 踩坑卡片反哺；`C-B5-04` Handover 文档落盘；`C-B5-05` 与 `[7]` 防重复归档。

## 📤 交付
卡片 ≤6 行：归档时间 / 进度（已完成 X 剩余 Y）/ Handover 路径 / 改动文件 / 关键决策 / "可安全关窗，新会话发 `6`"。
