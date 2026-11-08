# Apex 项目长期约定

## 隐藏目录 = 核心上下文

- `.ai/`（三层记忆 + 16 篇 SOP + 脚本）、`.agents/`（技能包）、`.cursorrules`（规则单一事实源）是项目一等公民；路径以 `.` 开头**不影响读取**。
- 项目结构快照不列隐藏目录，仅是展示层限制；需要时按绝对路径直接读，不要推断「看不到 = 不存在」。
- 已在 `.codebuddy/rules/apex-hidden-context/RULE.mdc` 建立 WorkBuddy 项目级 `alwaysApply` 规则入口，让新会话自动感知隐藏体系；该规则只做索引，不复制 SOP 正文。新规则需新建会话才生效。
- 加载策略：`.cursorrules §1` 路由 → 一次只读命中的单篇 SOP；`[B6]` 复活按 `tier1_snapshot` → `tier3_status` → `tier3_handover`。
- `.ai/tier3_status.md` 仅 `board.py` 可写；`.windsurfrules` 与 `tier2_modules.md` 为派生视图，勿手改。

## 在 WorkBuddy 里跑 SOP 的接入方式（2026-11-06 建立）

- `.codebuddy/CODEBUDDY.md`：项目级记忆，全文自动注入，只做导航（隐藏目录地图、三种触发方式、脚本命令、口径差异、模式建议），不复制 SOP 正文。
- `.codebuddy/commands/apex.md`：通用斜杠命令 `/apex <编号> [内容]`，作为手动指定任意编号的兜底。
- **桌面端 `/` 补全的真实机制（2026-11-06 排查确认）**：WorkBuddy 客户端的 `/` 列表只读「系统命令 + 已安装 Skill（`~/.workbuddy/skills/<name>/SKILL.md`）+ 设置里的自定义快捷指令」，**不扫描 `.codebuddy/commands/`**（那是 CodeBuddy Code CLI 的能力）。
- `~/.workbuddy/skills/apex-<编号>/SKILL.md`：**桌面端主入口**，16 个技能命令（`/apex-0`…`/apex-b6`）。`SKILL.md` 只需 `name` + `description` frontmatter，不需要 `_user_meta.json`（现有 `ppt-master` 等已验证）。新装技能需新建会话，必要时重启客户端。
- `.codebuddy/commands/apex-<编号>.md`：同 16 个节点，供 CodeBuddy Code CLI 使用；桌面端看不到，保留不删。
- 两套产物都由 `.codebuddy/scripts/gen_sop_commands.py` 从 `.ai/sop/*.md` 标题 + 触发行自动派生（触发词写入 description），属派生视图**勿手改**，改 SOP 后重跑脚本。
- `.codebuddy/CODEBUDDY.md` 内有「斜杠命令通用执行规程」8 条，供 16 个薄封装命令共同引用（路由 / 门禁 / board.py / B6 / 人工放行 / 卡片纪律）。
- `.codebuddy/rules/apex-hidden-context/RULE.mdc`：`alwaysApply` 行为约束（禁止跳过隐藏文件、按需加载、铁律）。三者职责分离：记忆=导航、命令=触发、规则=约束。
- 自然语言触发词、纯编号、`/apex <编号>` 三种方式等价；脚本（`board.py` / `gate.py` / `sync_tree.py`）由 AI 代跑，用户不必手敲。
- 状态唯一事实源是 `.ai/tier3_status.md` + `board.py`；WorkBuddy 会话内任务列表只是进度视图，不可代替看板。

## 项目口径差异（勿踩）

- `BEGINNER_HANDBOOK.md` 用旧编号：`F`→`[B2]`、`0L`→`[B1]`、`3`（排错）→`[B3]`；一律以 `.cursorrules` 现行编号为准。
- `[B6]` 原文的 `sync_tree.py --watch` 后台监听不适合 WorkBuddy（会话结束进程不保活），改为按需跑一次。
- `[7]` 验收必须用户明确点头，AI 不得自行 `complete --accept`。
- 2026-11-06 实测看板：`ACTIVE 0 / TODO 0 / DONE 9`，pre-commit 钩子已装；不能从 `[3]` 起步，起点是 `[1]` 或 `[B6]`。

## 用户偏好

- 希望 WorkBuddy 能像 Cursor / Windsurf 一样自动读到隐藏的 SOP 体系，不需要每次手动 @ 文件。
- 偏好**对话框输入 `/` 就自动提示 16 个 SOP 工作流节点**，选中即执行；不喜欢记编号或用单一通用命令。
