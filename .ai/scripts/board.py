#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Apex 任务看板与交接记忆脚本 (board.py) v2.0
===========================================================
设计原则
--------
1. **唯一合法入口**：`.ai/tier3_status.md` 只允许通过本脚本修改，AI/人严禁手改。
2. **锚点协议**：用 HTML 注释锚点 (SECTION:ACTIVE/TODO/DONE) 划分区块，
   彻底摆脱"靠 Markdown 标题正则"的脆弱性。
3. **幂等自愈**：任何命令都可以重复执行；缺锚点会重建标准骨架，
   历史内容整体下沉到附录区，绝不产生重复垃圾行。
4. **原子归档**：`complete --accept` 一次性完成 4 件事：
   标绿 DONE → 追加交接黑匣子 → 重建 tier1 名片 → 自动激活下一个 TODO。
5. **先验后收**：`verify` 提供机器可判定的终验证据，`complete` 必须带
   `--accept` 才放行（对应 [7] 终验阶段的人工放行标志）。

用法
----
  python3 .ai/scripts/board.py list                        # 查看看板
  python3 .ai/scripts/board.py add <id> <title> <goal> [files]
  python3 .ai/scripts/board.py start <id>                  # 锁定为 ACTIVE（单焦点）
  python3 .ai/scripts/board.py verify <id>                 # 终验机器人核验（PASS/BLOCKING）
  python3 .ai/scripts/board.py complete <id> --accept       # [7] 专属：终验放行 + 原子归档
  python3 .ai/scripts/board.py archive "<摘要>"             # 仅追加交接黑匣子
  python3 .ai/scripts/board.py archive --rotate [keep]     # 轮转黑匣子（旧的移到 docs/handovers/）
  python3 .ai/scripts/board.py block <id> "<原因>"           # 标记阻塞（释放焦点）
  python3 .ai/scripts/board.py unblock <id>                 # 解除阻塞 → TODO
  python3 .ai/scripts/board.py rebuild-tier1               # 仅重建第一层名片
  python3 .ai/scripts/board.py init                        # 强制重建标准骨架（幂等）
"""

import sys
import os
import re
import datetime

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

# ---- 路径：以脚本自身定位 .ai 根，杜绝 CWD 漂移 ----
AI_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROJECT_ROOT = os.path.dirname(AI_DIR)
STATUS_FILE = os.path.join(AI_DIR, "tier3_status.md")
HANDOVER_FILE = os.path.join(AI_DIR, "tier3_handover.md")
TIER1_FILE = os.path.join(AI_DIR, "tier1_snapshot.md")

# ---- 看板区块锚点 ----
ACTIVE_START = "<!-- SECTION:ACTIVE:START -->"
ACTIVE_END = "<!-- SECTION:ACTIVE:END -->"
TODO_START = "<!-- SECTION:TODO:START -->"
TODO_END = "<!-- SECTION:TODO:END -->"
DONE_START = "<!-- SECTION:DONE:START -->"
DONE_END = "<!-- SECTION:DONE:END -->"
BLOCKED_START = "<!-- SECTION:BLOCKED:START -->"
BLOCKED_END = "<!-- SECTION:BLOCKED:END -->"

SECTIONS = (
    ("active", ACTIVE_START, ACTIVE_END),
    ("blocked", BLOCKED_START, BLOCKED_END),
    ("todo", TODO_START, TODO_END),
    ("done", DONE_START, DONE_END),
)
# 区块在文件中的出现顺序
SECTION_ORDER = ("active", "blocked", "todo", "done")
SECTION_META = {
    "active": (ACTIVE_START, ACTIVE_END, "## 🔴 进行中 (ACTIVE)"),
    "blocked": (BLOCKED_START, BLOCKED_END, "## 🟠 阻塞中 (BLOCKED)"),
    "todo": (TODO_START, TODO_END, "## ⚪ 待办中 (TODO)"),
    "done": (DONE_START, DONE_END, "## 🟢 已完成 (DONE)"),
}
ALL_ANCHORS = [a for _, s, e in SECTIONS for a in (s, e)]

# ---- 名片卡锚点（tier1 存在才刷新，不存在则跳过并告警） ----
TIER1_ACTIVE_START = "<!-- TIER1-ACTIVE:START -->"
TIER1_ACTIVE_END = "<!-- TIER1-ACTIVE:END -->"
TIER1_MILESTONE_START = "<!-- TIER1-MILESTONE:START -->"
TIER1_MILESTONE_END = "<!-- TIER1-MILESTONE:END -->"

DEFAULT_STATUS_TEMPLATE = """# 📋 任务看板 (Task Board)

> ⚠️ **本文件是任务状态机的唯一事实源，只允许通过 `python3 .ai/scripts/board.py` 修改。**
> 区块由 HTML 注释锚点守护；AI 与人均严禁手工编辑，否则 `verify` / `complete` 会失效。

---

{active_start}
## 🔴 进行中 (ACTIVE)
{active_end}

---

{blocked_start}
## 🟠 阻塞中 (BLOCKED)
{blocked_end}

---

{todo_start}
## ⚪ 待办中 (TODO)
{todo_end}

---

{done_start}
## 🟢 已完成 (DONE)
{done_end}
""".format(
    active_start=ACTIVE_START, active_end=ACTIVE_END,
    blocked_start=BLOCKED_START, blocked_end=BLOCKED_END,
    todo_start=TODO_START, todo_end=TODO_END,
    done_start=DONE_START, done_end=DONE_END,
)

# SQL 脚本可能落盘的目录（与 gate.py / SOP 09/10 保持同源）
SQL_DIRS = ("docs/db/migrations", "docs/db", "docs/sql", "docs/deploy")

TASK_ID_RE = re.compile(r"^\[\s*(Task-[A-Za-z0-9_\-]+)\s*\]", re.MULTILINE)


# ===========================================================================
# 基础 IO
# ===========================================================================

def _read(path, default=""):
    if not os.path.exists(path):
        return default
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def _write_if_changed(path, new_content):
    """仅在内容真正变化时落盘，保证幂等。返回是否写入。"""
    old = _read(path, default=None)
    if old == new_content:
        return False
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(new_content)
    return True


# ===========================================================================
# 区块解析
# ===========================================================================

def read_sections(content):
    """返回 {name: body}，缺失的区块为 None。"""
    out = {}
    for name, start, end in SECTIONS:
        m = re.search(re.escape(start) + r"(.*?)" + re.escape(end), content, re.DOTALL)
        out[name] = m.group(1) if m else None
    return out


def set_section(content, name, body):
    start, end = next((s, e) for n, s, e in SECTIONS if n == name)
    pattern = re.escape(start) + r".*?" + re.escape(end)
    replacement = f"{start}{body}{end}"
    if re.search(pattern, content, re.DOTALL):
        return re.sub(pattern, lambda _m: replacement, content, count=1, flags=re.DOTALL)
    return content


def split_tasks(body):
    """把区块正文切成 [(task_id, block_text)]。"""
    if not body:
        return []
    matches = list(TASK_ID_RE.finditer(body))
    tasks = []
    for i, m in enumerate(matches):
        start = m.start()
        finish = matches[i + 1].start() if i + 1 < len(matches) else len(body)
        tasks.append((m.group(1), body[start:finish].strip()))
    return tasks


def task_status(block):
    m = re.search(r"\*\*Status\*\*[:：]\s*([A-Z_]+)", block)
    return m.group(1) if m else "UNKNOWN"


def task_title(block):
    m = re.search(r"^\[\s*Task-[A-Za-z0-9_\-]+\s*\]\s*(.+)$", block, re.MULTILINE)
    return m.group(1).strip() if m else "(无标题)"


def task_goal(block):
    m = re.search(r"\*\*功能目标\*\*[:：]\s*(.+)", block)
    return m.group(1).strip() if m else "(未填写)"


def task_files(block):
    m = re.search(r"\*\*涉及文件\*\*[:：]\s*(.+)", block)
    raw = m.group(1).strip() if m else ""
    if not raw or raw in ("未指定", "无", "-", "—"):
        return []
    parts = re.split(r"[,，、;；]+", raw)
    return [p.strip().strip("`").strip() for p in parts if p.strip().strip("`").strip()]


def find_task(content, task_id):
    """在三个区块里查找任务，返回 (section_name, block) 或 (None, None)。"""
    sections = read_sections(content)
    for name in ("active", "blocked", "todo", "done"):
        for tid, block in split_tasks(sections.get(name)):
            if tid == task_id:
                return name, block
    return None, None


# ===========================================================================
# 骨架自愈（幂等）
# ===========================================================================

def _strip_anchors(text):
    lines = [ln for ln in text.splitlines() if ln.strip() not in ALL_ANCHORS]
    cleaned = "\n".join(lines).strip()
    return re.sub(r"\n{3,}", "\n\n", cleaned)


def _has_substance(text):
    """判断遗留正文是否有实质内容（排除空模板骨架）。"""
    body = _strip_anchors(text)
    body = re.sub(r"^#+\s.*$", "", body, flags=re.MULTILINE)   # 去标题
    return len(body.strip()) > 20


def ensure_board(force=False):
    """确保看板存在且带完整锚点。缺失锚点时重建骨架，旧正文下沉附录。返回 (content, changed)。"""
    if not os.path.exists(STATUS_FILE):
        _write_if_changed(STATUS_FILE, DEFAULT_STATUS_TEMPLATE)
        return DEFAULT_STATUS_TEMPLATE, True

    content = _read(STATUS_FILE)
    sections = read_sections(content)

    if not force and all(v is not None for v in sections.values()):
        return content, False

    # 已有锚点、只是新增了区块（比如从 3 区块升级到 4 区块）→ 增量插入，绝不重建
    present = [k for k, _s, _e in SECTIONS if sections.get(k) is not None]
    if present and not force:
        for name in SECTION_ORDER:
            if sections.get(name) is not None:
                continue
            start, end, title = SECTION_META[name]
            block = f"\n---\n\n{start}\n{title}\n{end}\n"
            # 插到它应有的位置：找前一个存在的区块的 END 之后
            idx = SECTION_ORDER.index(name)
            anchor_after = None
            for prev in reversed(SECTION_ORDER[:idx]):
                if sections.get(prev) is not None:
                    anchor_after = SECTION_META[prev][1]
                    break
            if anchor_after and anchor_after in content:
                content = content.replace(anchor_after, anchor_after + "\n" + block, 1)
            else:
                content = content.rstrip() + "\n" + block
        _write_if_changed(STATUS_FILE, content)
        return content, True

    legacy = _strip_anchors(content)
    new_content = DEFAULT_STATUS_TEMPLATE
    if _has_substance(content):
        new_content += (
            "\n---\n\n"
            "## 📌 附录：迁移保留的历史内容（请人工整理后删除）\n\n"
            "> 本区块由 board.py 在修复锚点时自动下沉，不属于状态机管辖。\n\n"
            + legacy + "\n"
        )
    _write_if_changed(STATUS_FILE, new_content)
    return new_content, True


def load_board():
    content, changed = ensure_board()
    if changed:
        print("🛠️  已自动修复看板结构（补齐缺失区块，原有内容保持不变）")
    return content


# ===========================================================================
# 命令实现
# ===========================================================================

def add_task(task_id, title, goal, files="未指定"):
    if not re.match(r"^Task-[A-Za-z0-9_\-]+$", task_id):
        print(f"❌ task_id 建议使用 `Task-<数字>` 形式，当前为 [{task_id}]")
    content = load_board()
    if find_task(content, task_id)[0]:
        print(f"❌ 任务 [{task_id}] 已存在，请勿重复添加")
        return 1

    block = (
        f"\n[{task_id}] {title}\n"
        f"- **Status**: TODO\n"
        f"- **功能目标**: {goal}\n"
        f"- **涉及文件**: {files}\n"
    )
    sections = read_sections(content)
    body = (sections["todo"] or "").rstrip() + "\n" + block
    content = set_section(content, "todo", body)
    _write_if_changed(STATUS_FILE, content)
    print(f"✅ 已加入 TODO: [{task_id}] {title}")
    return 0


def start_task(task_id, force=False, quiet=False):
    content = load_board()
    section, block = find_task(content, task_id)
    if not section:
        print(f"❌ 找不到任务 [{task_id}]。先用 `board.py list` 确认任务编号。")
        return 1
    if section == "done":
        print(f"❌ [{task_id}] 已归档为 DONE，不能重新激活。如需返工请新建 Task。")
        return 1
    if section == "active":
        print(f"ℹ️  [{task_id}] 已经是 ACTIVE 焦点。")
        return 0

    sections = read_sections(content)
    current_active = split_tasks(sections["active"])
    if current_active and not force:
        cur_id = current_active[0][0]
        print(f"🚫 单焦点原则：当前已有 ACTIVE 任务 [{cur_id}]。")
        print(f"   请先执行 [7] 验收归档，或显式使用 --force 抢占焦点。")
        return 1

    # 从原区块移除
    for name in ("active", "blocked", "todo", "done"):
        blocks = [b for tid, b in split_tasks(sections[name]) if tid != task_id]
        content = set_section(content, name, _render_blocks(blocks))

    block_active = re.sub(r"- \*\*Status\*\*[:：]\s*\w+", "- **Status**: IN_PROGRESS", block)
    content = set_section(content, "active", _render_blocks([block_active]))
    _write_if_changed(STATUS_FILE, content)
    print(f"🚀 已锁定焦点: [{task_id}] → IN_PROGRESS (ACTIVE)")
    print(f"   👉 下一步：回复 [2] 出技术方案 或 [3] 直接 TDD 编码。")
    rebuild_tier1(quiet=quiet)
    return 0


def _render_blocks(blocks):
    if not blocks:
        return "\n\n"
    return "\n\n" + "\n\n".join(b.strip() for b in blocks) + "\n\n"


# ---------------------------------------------------------------------------
# verify —— 终验机器核验
# ---------------------------------------------------------------------------

def _check_sql_pairs():
    """扫描 SQL 升级脚本是否都配了非空 rollback_ 脚本。返回 (ok, details)。"""
    targets = []
    for rel in SQL_DIRS:
        base = os.path.join(PROJECT_ROOT, rel)
        if not os.path.isdir(base):
            continue
        for root, _dirs, files in os.walk(base):
            for f in files:
                if f.lower().endswith(".sql"):
                    targets.append(os.path.join(root, f))

    if not targets:
        return True, ["⏭️  [SQL] 未发现任何 SQL 脚本（无库变更，跳过配对校验）"]

    details, missing = [], []
    for path in sorted(targets):
        name = os.path.basename(path)
        lowered = name.lower()
        if lowered.startswith("rollback_") or lowered.startswith("init-") or "init" in os.path.dirname(path).lower():
            continue
        # base.sql 与 service.sql 为全量底座脚本，免回滚配对约束
        if lowered in ("base.sql", "service.sql"):
            details.append(f"✅ {rel} → 全量底座脚本 (免配对)")
            continue
        rb = os.path.join(os.path.dirname(path), f"rollback_{name}")
        rel = os.path.relpath(path, PROJECT_ROOT)
        if not os.path.exists(rb):
            missing.append(f"🔴 {rel} → 缺少 rollback_{name}")
        elif os.path.getsize(rb) == 0:
            missing.append(f"🔴 {rel} → rollback_{name} 为空文件")
        else:
            details.append(f"✅ {rel} → 已配对 rollback_{name}")
    if missing:
        return False, missing
    return True, details or ["✅ [SQL] 全部升级脚本均已配对非空回滚脚本"]


def _verify_report(task_id):
    """生成终验报告。返回 (ok: bool, lines: list[str])。"""
    content = load_board()
    section, block = find_task(content, task_id)
    if not section:
        return False, [f"❌ 找不到任务 [{task_id}]。"]

    lines = []
    blockers = []

    lines.append("── 1. 任务存在性与状态 ──")
    lines.append(f"   ✅ 定位成功：区块 = {section.upper()}，Status = {task_status(block)}")
    lines.append(f"   📌 标题：{task_title(block)}")
    lines.append(f"   🎯 目标：{task_goal(block)}")
    if section == "blocked":
        blockers.append(f"任务处于 BLOCKED（阻塞中），需先 `board.py unblock {task_id}` 才能继续")
    elif section != "active":
        blockers.append(f"任务不在 ACTIVE 区块（当前在 {section.upper()}），请先 `board.py start {task_id}`")

    lines.append("")
    lines.append("── 2. 涉及文件存在性 ──")
    files = task_files(block)
    if not files:
        lines.append("   ⚠️  未声明涉及文件，跳过存在性校验（建议补齐 `**涉及文件**` 以提高可追溯性）")
    else:
        missing = []
        for f in files:
            if any(ch in f for ch in "*?<>") or "..." in f:
                lines.append(f"   ⏭️  跳过通配/描述性条目: {f}")
                continue
            target = f if os.path.isabs(f) else os.path.join(PROJECT_ROOT, f)
            if os.path.exists(target):
                lines.append(f"   ✅ 存在: {f}")
            else:
                lines.append(f"   🔴 不存在: {f}")
                missing.append(f)
        if missing:
            blockers.append(f"{len(missing)} 个声明文件不存在：{', '.join(missing[:3])}")

    lines.append("")
    lines.append("── 3. SQL 回滚配对 ──")
    sql_ok, sql_msgs = _check_sql_pairs()
    lines.extend("   " + m for m in sql_msgs)
    if not sql_ok:
        blockers.append("存在未配对回滚脚本的 SQL 变更")

    lines.append("")
    lines.append("── 4. tier1 名片一致性 ──")
    t1 = _read(TIER1_FILE)
    if TIER1_ACTIVE_START not in t1:
        lines.append("   ⚠️  tier1 未植入 TIER1-ACTIVE 锚点，跳过一致性校验")
    else:
        m = re.search(re.escape(TIER1_ACTIVE_START) + r"(.*?)" + re.escape(TIER1_ACTIVE_END), t1, re.DOTALL)
        block_text = m.group(1) if m else ""
        # 必须命中「当前焦点」那一行，避免只匹配到「下一待办」造成假通过
        focus_hit = re.search(
            r"当前焦点\*\*\s*[：:]\s*`\[" + re.escape(task_id) + r"\]`", block_text
        )
        if focus_hit:
            lines.append(f"   ✅ tier1 当前焦点指向 [{task_id}]")
        elif task_id in block_text:
            lines.append(f"   ⚠️  tier1 仅在「下一待办」中提及 [{task_id}]，焦点尚未切换"
                         f"（先 `board.py start {task_id}`）")
        else:
            lines.append(f"   ⚠️  tier1 焦点与 [{task_id}] 不一致（执行 `board.py rebuild-tier1` 可修复）")

    lines.append("")
    if blockers:
        lines.append("🚫 [BLOCKING] 终验未通过，禁止 complete：")
        lines.extend("   - " + b for b in blockers)
    else:
        lines.append("✅ [PASS] 机器可判定项全部通过。")
        lines.append("   ⚠️  机器核验不代替人工核对——请仍按 09 的 4 维清单向用户呈报证据。")
    return (len(blockers) == 0), lines


def verify_task(task_id):
    ok, lines = _verify_report(task_id)
    print("=" * 60)
    print(f"🔎 终验机器核验 (verify): [{task_id}]")
    print("=" * 60)
    for line in lines:
        print(line)
    print("=" * 60)
    return 0 if ok else 1


# ---------------------------------------------------------------------------
# complete —— 原子归档
# ---------------------------------------------------------------------------

def complete_task(task_id, accept=False):
    if not accept:
        print("🚫 [BLOCKING] `complete` 必须携带 `--accept` 终验放行标志。")
        print("   归档是 [7] 双向验收阶段的专属动作：")
        print(f"   1) 先运行：python3 .ai/scripts/board.py verify \"{task_id}\"")
        print("   2) 再向用户呈报 4 维终验清单并取得明确放行")
        print(f"   3) 最后运行：python3 .ai/scripts/board.py complete \"{task_id}\" --accept")
        return 1

    content = load_board()
    section, block = find_task(content, task_id)
    if not section:
        print(f"❌ 找不到任务 [{task_id}]。")
        return 1
    if section == "done":
        print(f"ℹ️  [{task_id}] 已是 DONE，无需重复归档。")
        return 0
    if section == "blocked":
        print(f"🚫 [{task_id}] 处于 BLOCKED（阻塞中），不能归档。")
        print(f"   先解决阻塞并执行：python3 .ai/scripts/board.py unblock \"{task_id}\"")
        return 1

    # 强制执行机器核验：verify 不通过一律拒绝归档（不允许跳过终验）
    ok, report = _verify_report(task_id)
    if not ok:
        print("🚫 [BLOCKING] 机器核验未通过，`complete` 被拒绝：")
        for line in report[-9:]:
            print(line)
        print(f"\n   修复后重跑：python3 .ai/scripts/board.py verify \"{task_id}\"")
        return 1

    today = datetime.date.today().strftime("%Y-%m-%d")
    now = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    sections = read_sections(content)

    # 1) 从原区块移除
    for name in ("active", "blocked", "todo", "done"):
        kept = [b for tid, b in split_tasks(sections[name]) if tid != task_id]
        content = set_section(content, name, _render_blocks(kept))

    # 2) 写入 DONE 区块
    block_done = re.sub(r"- \*\*Status\*\*[:：]\s*\w+", "- **Status**: DONE", block)
    if "(Done:" not in block_done:
        block_done = re.sub(
            r"(\[\s*" + re.escape(task_id) + r"\s*\][^\n]*)",
            lambda m: m.group(1) + f" (Done: {today})",
            block_done, count=1,
        )
    sections = read_sections(content)
    done_blocks = [block_done] + [b for _t, b in split_tasks(sections["done"])]
    content = set_section(content, "done", _render_blocks(done_blocks))
    _write_if_changed(STATUS_FILE, content)
    print(f"🎉 已标绿 DONE: [{task_id}] {task_title(block)}")

    # 3) 追加交接黑匣子
    summary = (
        f"* 🎯 完成任务: [{task_id}] {task_title(block)}\n"
        f"* 🧭 白话目标: {task_goal(block)}\n"
        f"* 🛠️ 本次改动核心文件: {', '.join(task_files(block)) or '未声明'}\n"
        f"* ✅ 终验: `board.py verify` 机器核验通过 + 用户显式放行 (--accept)"
    )
    archive_handover(summary, quiet=True)

    # 4) 自动激活下一个 TODO（内部会刷新 tier1）
    content = _read(STATUS_FILE)
    todo_blocks = split_tasks(read_sections(content)["todo"])
    if todo_blocks:
        start_task(todo_blocks[0][0], quiet=True)
    else:
        print("📭 TODO 已清空：请用 `[1]` 规划新需求，或 `[B5]` 归档后收工。")

    # 5) 统一刷新一次 tier1（无下一任务时也要更新焦点/里程碑）
    rebuild_tier1()

    print(f"\n💾 三类资产已同步：tier3_status.md / tier3_handover.md / tier1_snapshot.md")
    return 0


# ---------------------------------------------------------------------------
# 交接黑匣子 & tier1 名片
# ---------------------------------------------------------------------------

def block_task(task_id, reason):
    """把任务标记为阻塞：移入 BLOCKED 区，记录原因与时间。"""
    content = load_board()
    section, block = find_task(content, task_id)
    if not section:
        print(f"❌ 找不到任务 [{task_id}]")
        return 1
    if section == "done":
        print(f"❌ [{task_id}] 已归档 DONE，不能再阻塞。如需返工请新建 Task。")
        return 1
    if section == "blocked":
        print(f"ℹ️  [{task_id}] 已经是 BLOCKED。")
        return 0

    today = datetime.date.today().strftime("%Y-%m-%d")
    sections = read_sections(content)
    for name in ("active", "blocked", "todo", "done"):
        kept = [b for tid, b in split_tasks(sections[name]) if tid != task_id]
        content = set_section(content, name, _render_blocks(kept))

    blk = re.sub(r"- \*\*Status\*\*[:：]\s*\w+", "- **Status**: BLOCKED", block)
    blk = blk.rstrip() + f"\n- **阻塞原因**: {reason}\n- **阻塞时间**: {today}\n"
    sections = read_sections(content)
    existing = [b for _t, b in split_tasks(sections["blocked"])]
    content = set_section(content, "blocked", _render_blocks(existing + [blk]))
    _write_if_changed(STATUS_FILE, content)
    rebuild_tier1()
    print(f"🟠 已标记阻塞: [{task_id}] → BLOCKED")
    print(f"   原因: {reason}")
    print(f"   焦点已释放，可以 start 下一个任务；解除用 board.py unblock \"{task_id}\"")
    return 0


def unblock_task(task_id):
    """解除阻塞：移回 TODO 区。"""
    content = load_board()
    section, block = find_task(content, task_id)
    if not section:
        print(f"❌ 找不到任务 [{task_id}]")
        return 1
    if section != "blocked":
        print(f"ℹ️  [{task_id}] 不在 BLOCKED 区（当前 {section.upper()}），无需解阻。")
        return 0

    today = datetime.date.today().strftime("%Y-%m-%d")
    sections = read_sections(content)
    for name in ("active", "blocked", "todo", "done"):
        kept = [b for tid, b in split_tasks(sections[name]) if tid != task_id]
        content = set_section(content, name, _render_blocks(kept))

    todo_block = re.sub(r"- \*\*Status\*\*[:：]\s*\w+", "- **Status**: TODO", block)
    todo_block = re.sub(r"\n- \*\*阻塞时间\*\*[:：].*", "", todo_block)
    todo_block = todo_block.rstrip() + f"\n- **解除阻塞**: {today}\n"
    sections = read_sections(content)
    todo_blocks = [b for _t, b in split_tasks(sections["todo"])]
    content = set_section(content, "todo", _render_blocks(todo_blocks + [todo_block]))
    _write_if_changed(STATUS_FILE, content)
    rebuild_tier1()
    print(f"🟢 已解除阻塞: [{task_id}] → TODO")
    print(f"   想立刻开工：board.py start \"{task_id}\"")
    return 0


def archive_handover(summary_text, quiet=False):
    now_str = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    header = (
        "# 📦 项目历史交接记忆卡 (Project Handover Logs)\n\n"
        "> 本文件记录项目开发过程中的核心架构决策、关键代码改动及环境变更，"
        "用于新会话无缝断点续航。**只追加，不覆盖**。\n"
    )
    if not os.path.exists(HANDOVER_FILE):
        _write_if_changed(HANDOVER_FILE, header)

    with open(HANDOVER_FILE, "a", encoding="utf-8") as f:
        f.write(f"\n---\n\n## 📦 归档交接 [{now_str}]\n{summary_text}\n")
    if not quiet:
        print(f"💾 交接记忆卡已追加至 {os.path.relpath(HANDOVER_FILE, PROJECT_ROOT)}")

    # 黑匣子只增不减：超过阈值时提醒轮转，避免 [B6] 复活时整读变大
    try:
        with open(HANDOVER_FILE, "r", encoding="utf-8") as f:
            lines = f.read().count("\n") + 1
        if lines > 300:
            print(f"⚠️  黑匣子已 {lines} 行（只增不减）。建议轮转：把今年以前的条目"
                  f"移到 docs/handovers/archive-handover-<年份>.md，只保留近 200 行。")
    except OSError as e:
        # 行数统计失败不影响归档本身，但必须留痕（不许静默 pass）
        print(f"⚠️  黑匣子行数统计失败（不影响归档）: {e}")
    return os.path.relpath(HANDOVER_FILE, PROJECT_ROOT)


def rotate_handover(keep=40):
    """黑匣子轮转：把超出 keep 条的旧记录移到 docs/handovers/archive-handover-<年>.md。"""
    if not os.path.exists(HANDOVER_FILE):
        print("ℹ️  黑匣子不存在，无需轮转")
        return 0
    text = _read(HANDOVER_FILE)
    head_end = text.find("\n---\n")
    header = text[:head_end] if head_end > 0 else ""
    body = text[head_end:] if head_end > 0 else text

    blocks = re.split(r"\n(?=## 📦 归档交接)", body)
    blocks = [b.strip() for b in blocks if b.strip().startswith("## 📦 归档交接")]
    if len(blocks) <= keep:
        print(f"ℹ️  黑匣子 {len(blocks)} 条，未超过阈值 {keep}，无需轮转")
        return 0

    move, stay = blocks[:-keep], blocks[-keep:]
    years = {}
    for b in move:
        m = re.search(r"\[(\d{4})-", b)
        year = m.group(1) if m else "unknown"
        years.setdefault(year, []).append(b)

    archive_dir = os.path.join(PROJECT_ROOT, "docs", "handovers")
    os.makedirs(archive_dir, exist_ok=True)
    for year, items in years.items():
        fp = os.path.join(archive_dir, f"archive-handover-{year}.md")
        new_file = not os.path.exists(fp)
        with open(fp, "a", encoding="utf-8") as f:
            if new_file:
                f.write(f"# 📦 归档交接 · {year} 年（从 tier3_handover.md 轮转）\n\n"
                        f"> 本文件是历史冷数据，`[B6]` 复活时不读。\n\n---\n\n")
            for it in items:
                f.write(it + "\n\n---\n\n")
        print(f"📦 已归档 {len(items)} 条 → docs/handovers/archive-handover-{year}.md")

    rebuilt = (header or "# 📦 项目历史交接记忆卡 (Project Handover Logs)\n") + "\n"
    for b in stay:
        rebuilt += "---\n\n" + b + "\n\n"
    _write_if_changed(HANDOVER_FILE, rebuilt)
    print(f"✅ 轮转完成：保留最近 {len(stay)} 条，移出 {len(move)} 条")
    return 0


def rebuild_tier1(quiet=False):
    """从看板反推并刷新第一层名片（幂等；无锚点时跳过并告警）。"""
    def log(msg):
        if not quiet:
            print(msg)

    if not os.path.exists(TIER1_FILE):
        log("⚠️  未找到 .ai/tier1_snapshot.md，跳过名片重建")
        return False
    content = _read(STATUS_FILE)
    sections = read_sections(content)
    t1 = _read(TIER1_FILE)

    active = split_tasks(sections["active"])
    todo = split_tasks(sections["todo"])
    done = split_tasks(sections["done"])

    if active:
        tid, block = active[0]
        active_text = (
            f"- **当前焦点**：`[{tid}]` {task_title(block)}（Status: IN_PROGRESS）\n"
            f"- **白话目标**：{task_goal(block)}\n"
            f"- **涉及文件**：{', '.join(task_files(block)) or '未声明'}\n"
        )
    else:
        active_text = "- **当前焦点**：无（没有 ACTIVE 任务）\n"

    blocked = split_tasks(sections["blocked"])
    if blocked:
        bid, bblock = blocked[0]
        reason = re.search(r"\*\*阻塞原因\*\*[:：]\s*(.+)", bblock)
        active_text += (f"- **阻塞中**：{len(blocked)} 个（`[{bid}]`"
                        + (f"：{reason.group(1).strip()[:20]}" if reason else "")
                        + "…）\n")

    if todo:
        nid, nblock = todo[0]
        active_text += f"- **下一待办**：`[{nid}]` {task_title(nblock)}\n"
    else:
        active_text += "- **下一待办**：暂无 —— 需回复 `[1]` 规划新需求\n"

    if TIER1_ACTIVE_START in t1:
        t1 = re.sub(
            re.escape(TIER1_ACTIVE_START) + r".*?" + re.escape(TIER1_ACTIVE_END),
            lambda _m: f"{TIER1_ACTIVE_START}\n{active_text}{TIER1_ACTIVE_END}",
            t1, count=1, flags=re.DOTALL,
        )
    else:
        log("⚠️  tier1 缺少 TIER1-ACTIVE 锚点，焦点区块未自动刷新")

    # 里程碑只在 DONE 区非空时重写，避免把人工沉淀的历史里程碑清空
    if TIER1_MILESTONE_START in t1 and done:
        lines = []
        for tid, block in done[:3]:
            date = re.search(r"\(Done:\s*([0-9\-]+)\)", block)
            stamp = date.group(1) if date else "-"
            clean_title = re.sub(r"\s*\(Done:.*?\)", "", task_title(block)).strip()
            lines.append(f"- **[{stamp}]** `[{tid}]` {clean_title} 🟢")
        milestone_text = "\n".join(lines) if lines else "- 暂无已归档里程碑"
        t1 = re.sub(
            re.escape(TIER1_MILESTONE_START) + r".*?" + re.escape(TIER1_MILESTONE_END),
            lambda _m: f"{TIER1_MILESTONE_START}\n{milestone_text}\n{TIER1_MILESTONE_END}",
            t1, count=1, flags=re.DOTALL,
        )

    changed = _write_if_changed(TIER1_FILE, t1)
    if changed:
        log("🪪 第一层名片 tier1_snapshot.md 已刷新")
    return changed


# ===========================================================================
# 入口
# ===========================================================================

def check_hooks_hint():
    """工作流自检：钩子是否安装 + 看板概况。"""
    hook = os.path.join(PROJECT_ROOT, ".git", "hooks", "pre-commit")
    installed = os.path.exists(hook) and "Apex" in _read(hook)

    content = load_board()
    secs = read_sections(content)
    print("🔧 工作流自检")
    print(f"   pre-commit 钩子 : {'✅ 已安装（门禁会自动执行）' if installed else '❌ 未安装（门禁只是建议）'}")
    if not installed:
        print("      修复：python3 .ai/scripts/install_hooks.py")
    for name, label in (("active", "ACTIVE"), ("blocked", "BLOCKED"), ("todo", "TODO"), ("done", "DONE")):
        n = len(split_tasks(secs.get(name)))
        print(f"   {label:<8}: {n} 个")
    return 0


def print_usage():
    print(__doc__)
    print("  list | add | start | block | unblock | verify | complete | archive | rebuild-tier1 | init")


def main():
    if len(sys.argv) < 2 or sys.argv[1] in ("-h", "--help", "help"):
        print_usage()
        return 0

    cmd = sys.argv[1].lower()
    positional = [a for a in sys.argv[2:] if not a.startswith("--")]
    flags = {a for a in sys.argv[2:] if a.startswith("--")}

    if cmd == "list":
        print(load_board())
        return 0

    if cmd == "add":
        if len(positional) < 3:
            print("❌ 参数不足：board.py add <task_id> <title> <goal> [files]")
            return 2
        files = positional[3] if len(positional) > 3 else "未指定"
        return add_task(positional[0], positional[1], positional[2], files)

    if cmd == "start":
        if not positional:
            print("❌ 参数不足：board.py start <task_id>")
            return 2
        return start_task(positional[0], force="--force" in flags)

    if cmd == "verify":
        if not positional:
            print("❌ 参数不足：board.py verify <task_id>")
            return 2
        return verify_task(positional[0])

    if cmd in ("complete", "done"):
        if not positional:
            print("❌ 参数不足：board.py complete <task_id> --accept")
            return 2
        return complete_task(positional[0], accept="--accept" in flags)

    if cmd == "archive":
        if "--rotate" in flags:
            try:
                keep = int(next((args for args in positional if args.isdigit()), "40"))
            except ValueError:
                keep = 40
            return rotate_handover(keep)
        summary = positional[0] if positional else "* 常规阶段性归档"
        archive_handover(summary)
        return 0

    if cmd == "block":
        if len(positional) < 1:
            print("❌ 参数不足：board.py block <task_id> \"<阻塞原因>\"")
            return 2
        reason = positional[1] if len(positional) > 1 else "未说明原因"
        return block_task(positional[0], reason)

    if cmd == "unblock":
        if not positional:
            print("❌ 参数不足：board.py unblock <task_id>")
            return 2
        return unblock_task(positional[0])

    if cmd == "status":
        return check_hooks_hint()

    if cmd in ("rebuild-tier1", "tier1"):
        rebuild_tier1()
        return 0

    if cmd == "init":
        _content, changed = ensure_board(force=True)
        rebuild_tier1()
        print("✅ 看板骨架已重建" if changed else "ℹ️ 看板骨架无变化")
        return 0

    print(f"❌ 未知指令: {cmd}\n")
    print_usage()
    return 2


if __name__ == "__main__":
    sys.exit(main())
