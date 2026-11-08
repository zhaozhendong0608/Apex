# Apex · 在 WorkBuddy 中运行 SOP 工作流

> 本文件是 WorkBuddy 的项目级记忆（每个会话自动全文注入），**只做导航**。规范正文以 `.ai/sop/*.md` 为唯一事实源，行为铁律以 `.cursorrules` 为唯一事实源，此处不复制第二份。

## 1. 三种触发方式

| 方式 | 怎么写 | 适用 |
| :--- | :--- | :--- |
| 自然语言 | `1 我想让运维看到交换机在线状态…` / `继续` / `报错了：<完整堆栈>` | 日常最常用，按 `.cursorrules §1` 触发词就近匹配 |
| 斜杠命令（推荐） | 输入 `/` 从技能列表选 `/apex-1` … `/apex-b6` | 16 个节点各有独立技能与描述；桌面端入口在 `~/.workbuddy/skills/` |
| 纯编号 | `1` / `2` / `7` / `B2` | 与 Cursor 时代习惯一致，可继续用 |

路由表在 `.cursorrules §1`：**一次只读命中的那一篇 SOP**，禁止预读全套。主链 `1→2→3→4→5→6→7`，交付链 `8→9`，旁路 `B1~B6` 随时插入且不改 ACTIVE。

### 斜杠命令通用执行规程（16 个节点共用）

`.codebuddy/scripts/gen_sop_commands.py` 从 `.ai/sop/*.md` 自动生成两套入口；改了 SOP 就重跑脚本，**不要手改产物**：

- `~/.workbuddy/skills/apex-<编号>/SKILL.md` —— **WorkBuddy 桌面端主入口**。桌面端 `/` 补全只读「系统命令 + 已安装 Skill + 自定义快捷指令」，技能装在这里才会出现。
- `.codebuddy/commands/apex-<编号>.md` —— CodeBuddy Code CLI 的自定义斜杠命令；**桌面端不扫描此目录**。
- `/apex <编号>` 是手动指定任意编号的兜底命令。

新装技能需要**新建会话**才会进入补全列表；若仍不显示，重启一次 WorkBuddy 客户端。

1. 按 `.cursorrules §1` 路由；旧编号按迁移表解释：`F`→`[B2]`、`0L`→`[B1]`、用 `3` 表示排错→`[B3]`。
2. 只读命令指向的那一篇 SOP；读到 `<!-- ⛔ EXEC-STOP -->` 立即停，禁止预读其它 SOP 或 `WORKFLOW_GUIDE.md`（除非 `[0]` / `[B1]`）。
3. 进入 `[3]` / `[4]` / `[6]` / `[7]` 前先跑 `python3 .ai/scripts/gate.py --stage <n>`；不通过就说明缺什么，不闷头干。被要求跳步先给「后果 + 最小补救 + 二选一」。
4. 状态一律由 `board.py <list|verify|complete --accept|block|unblock|status>` 修改，**严禁手改** `.ai/tier3_status.md`。
5. `[B6]` 按 `tier1_snapshot` → `tier3_status` → `tier3_handover` 顺序读；**不启动** `sync_tree.py --watch`，需要时跑一次同步脚本。
6. 命中 Skill 场景按需读 `.agents/skills/<name>/SKILL.md`（路由见 `.cursorrules §6`），禁止全量装载。
7. `[7]` 给出 4 维清单后**等待用户明确放行**，不得自行 `complete --accept`。
8. 输出遵守 `.cursorrules §5`：卡片 ≤6 行，不复述需求，不重复 SOP 正文。

## 2. 隐藏目录地图（以 `.` 开头，全部可直接读）

| 路径 | 用途 |
| :--- | :--- |
| `.cursorrules` | 规则单一事实源：路由 / 五条铁律 / 加载策略 / Skill 路由 |
| `.ai/sop/` | 16 篇 SOP 正文，一次只读命中篇 |
| `.ai/tier1_snapshot.md` | 第一层记忆（<30 行），复活时优先且只读它 |
| `.ai/tier3_status.md` | 任务状态机，**仅 `board.py` 可写** |
| `.ai/tier3_handover.md` | 上次交接决策，按需读最近几条 |
| `.ai/scripts/` | `board.py` / `gate.py` / `sync_tree.py` 等工作流脚本 |
| `.agents/skills/<name>/SKILL.md` | 8 个技能包，按 `.cursorrules §6` 按需读 |

会话开头的项目结构快照**不展示隐藏目录**，这只是展示层行为，不代表文件不可读；用户 @ 隐藏文件时直接按绝对路径读。

## 3. 脚本由 AI 代跑，不用手敲

```bash
python3 .ai/scripts/board.py list                          # 看板现状
python3 .ai/scripts/board.py verify "Task-401"             # 能否验收
python3 .ai/scripts/board.py complete "Task-401" --accept  # 唯一标绿出口
python3 .ai/scripts/gate.py --stage 3                      # 阶段前置门禁
python3 .ai/scripts/board.py status                        # 钩子与四态自检
```

`pre-commit` 钩子会自动跑 `gate --staged / --refs / --sop`。**状态只由脚本改**，WorkBuddy 的会话任务列表只是进度视图，不能代替看板。

## 4. 当前看板状态（2026-11-06 实测）

`ACTIVE 0 / BLOCKED 0 / TODO 0 / DONE 9`，pre-commit 钩子已安装。
→ 现在**不能从 `[3]` 起步**（会被门禁拦），正确起点是 `[1]` 规划新需求，或 `[B6]` 复活（会提示「看板全空，准备新一轮规划」）。

## 5. 口径差异与坑（必读）

- `BEGINNER_HANDBOOK.md` 仍是**旧编号**：`F` → `[B2]`、`0L` → `[B1]`、用 `3` 表示排错 → `[B3]`；一律以 `.cursorrules` 现行编号为准。
- `[B6]` 原文要求后台常驻 `sync_tree.py --watch`；WorkBuddy 会话结束不保证后台进程存活，**改为按需跑一次** `python3 .ai/scripts/sync_tree.py`。
- `[7]` 验收必须由用户本人点头放行，AI 不得自行 `complete --accept`。
- 归档唯一出口 `[7]`；禁假 Mock；零新依赖；最小改动；生产写操作（部署 / 发布 / 回滚）需二次确认。

## 6. WorkBuddy 模式建议

`Plan` 适合 `[1]` `[2]` `[5]`（先想清楚、少改文件）；`Craft` 适合 `[3]` `[B2]` `[B3]`（直接动手）；`Ask` 适合 `[0]` 咨询。模式只影响交互方式，**不改变 SOP 状态机**。
