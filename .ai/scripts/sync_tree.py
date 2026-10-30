#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Apex 目录树与 Skill 路由自动同步脚本 (sync_tree.py)
1. 动态提取 YAML Frontmatter (summary / description) 自动生成带自解释注释的真实目录树。
2. 动态扫描 `.agents/skills/` 下所有 SKILL.md，自动提取技能路由并写回 `.cursorrules`、`.windsurfrules` 与 `README.md`。
3. 支持 --watch 原生事件监听模式与强力指纹轮询模式。
"""

import os
import sys
import re
import time

IGNORE_DIRS = {
    ".git", ".idea", ".vscode", "node_modules", "dist", "build",
    "target", "__pycache__", ".venv", "env", ".DS_Store"
}

EMOJI_MAP = {
    ".ai": "🧠",
    ".agents": "🧩",
    ".cursorrules": "🧠",
    ".windsurfrules": "🧠",
    "WORKFLOW_GUIDE.md": "📖",
    "README.md": "📖",
    "base_template": "💻",
    "docs": "📁",
    "design": "🎨",
    "modules": "📦",
    "templates": "📁",
    "src": "💻",
    "sop": "📜",
    "scripts": "🛠️",
}

DEFAULT_DIR_DESCS = {
    ".ai": "⚙️ 统一工作流控制中心 (三层记忆金字塔)",
    ".agents": "🧩 AI 专属技能包库",
    "scripts": "🛠️ 自动化控制与分析脚本库",
    "sop": "📜 全套 16 篇 SOP（0~9 · B1~B6）",
    "base_template": "💻 待开发业务工程 (脚手架)",
    "docs": "📁 本工程 PRD / 架构 / 设计文档",
    "design": "🎨 UI 视觉规范与设计 Token",
    "modules": "📦 业务大模块 PRD 与交互原型",
    "templates": "📁 文档与原型外壳模板",
}

def extract_meta_from_file(file_path):
    """从 Markdown 文件中动态提取 summary/description 或首行标题描述"""
    if not os.path.exists(file_path) or not file_path.endswith('.md'):
        return ""

    try:
        with open(file_path, "r", encoding="utf-8") as f:
            content = f.read()
    except Exception:
        return ""

    # 1. 尝试解析 YAML Frontmatter (--- ... ---)
    yaml_match = re.search(r"^---\s*\n(.*?)\n---", content, re.DOTALL)
    if yaml_match:
        yaml_text = yaml_match.group(1)
        summary_match = re.search(r"^(?:summary|description|name):\s*(.+)$", yaml_text, re.MULTILINE)
        desc_match = re.search(r"^description:\s*(.+)$", yaml_text, re.MULTILINE)
        if desc_match:
            return desc_match.group(1).strip('"\' ')
        elif summary_match:
            return summary_match.group(1).strip('"\' ')

    # 2. 尝试解析 Heading 1 (# ... )
    lines = [line.strip() for line in content.splitlines() if line.strip()]
    for line in lines[:5]:
        if line.startswith("# "):
            title = line.lstrip("# ").strip()
            # 只去掉前导符号/emoji，保留中文与英文正文（旧实现会把中文字符一并吃掉）
            clean_title = re.sub(r"^[^\w\u4e00-\u9fa5\[]+", "", title).strip()
            clean_title = re.sub(r"\s*\([^)]*\)\s*$", "", clean_title).strip()
            return clean_title if clean_title else title

    return ""

def scan_skills_registry():
    """扫描 .agents/skills/ 下所有 SKILL.md，提取 Skill 路由元数据"""
    skills_dir = os.path.join(".agents", "skills")
    if not os.path.exists(skills_dir):
        return []

    skills_list = []
    for item in sorted(os.listdir(skills_dir)):
        skill_path = os.path.join(skills_dir, item)
        skill_md = os.path.join(skill_path, "SKILL.md")
        if os.path.isdir(skill_path) and os.path.exists(skill_md):
            try:
                with open(skill_md, "r", encoding="utf-8") as f:
                    text = f.read()
                name_match = re.search(r"^name:\s*(.+)$", text, re.MULTILINE)
                desc_match = re.search(r"^description:\s*(.+)$", text, re.MULTILINE)
                
                skill_name = name_match.group(1).strip('"\' ') if name_match else item
                skill_desc = desc_match.group(1).strip('"\' ') if desc_match else "未命名技能"
                skills_list.append({
                    "name": skill_name,
                    "desc": skill_desc,
                    "folder": item,
                    "path": f".agents/skills/{item}/SKILL.md"
                })
            except Exception as e:
                print(f"⚠️ 解析 Skill [{item}] 失败: {e}")

    return skills_list

KNOWN_SKILLS_META = {
    "requirement-discovery": {
        "title": "🔍 requirement-discovery",
        "badge": "badge-primary",
        "badge_text": "需求发现",
        "duty": "澄清口语需求，抛出 A/B/C 选择题。",
        "output": "需求基线草案 (5大状态模型)。",
    },
    "archetype-architect": {
        "title": "🏛️ archetype-architect",
        "badge": "badge-success",
        "badge_text": "原型架构",
        "duty": "生成可点击的 HTML 交互原型页面。",
        "output": "<code>mockup.html</code> 原型、模块 PRD。",
    },
    "design-spec-architect": {
        "title": "📐 design-spec-architect",
        "badge": "badge-warning",
        "badge_text": "设计说明书",
        "duty": "撰写 HLD/DLD 设计文档与数据库 SQL。",
        "output": "<code>docs/01~04.md</code>、建表 SQL。",
    },
    "coding-standards": {
        "title": "💻 coding-standards",
        "badge": "badge-primary",
        "badge_text": "代码规范",
        "duty": "开启看门狗，按规范写 Java 和 Vue 代码。",
        "output": "<code>src/</code> 业务源码、第三方联调日志。",
    },
    "quality-verifier": {
        "title": "🧪 quality-verifier",
        "badge": "badge-danger",
        "badge_text": "质量与排错",
        "duty": "小黄鸭探针微创排错、契约碰撞测试，自动沉淀踩坑知识。",
        "output": "小黄鸭诊断报告、微创修复代码、<code>knowledge-cards/postmortem-*.md</code> 踩坑卡片。",
        "extra_html": """
                            <div style="margin-top: 12px; padding: 10px; background: rgba(239, 68, 68, 0.05); border-radius: var(--radius-sm); border: 1px dashed rgba(239, 68, 68, 0.3); font-size: 12.5px;">
                                <strong style="color: var(--danger);">🧠 踩坑知识卡片 3 大核心好处：</strong>
                                <ul style="margin-left: 16px; margin-top: 6px; line-height: 1.6; color: var(--text-muted);">
                                    <li><strong>1. 经验物理存盘</strong>：隐蔽 Bug (如 Spring Boot 3 与 MyBatis-Plus 兼容死穴) 自动归档，团队/AI 永久不踩重复坑。</li>
                                    <li><strong>2. 零 Token 预加载开销</strong>：基于按需延迟加载 (Lazy Loading)，平时完全不读入上下文；只有真实触发 Exception 时才单点调阅 (仅 ~200 Token)，极速且低消耗。</li>
                                    <li><strong>3. 看门狗进化</strong>：自动将踩坑经验反哺演进为全局看门狗规范，让 AI 随项目开发越用越聪明！</li>
                                </ul>
                            </div>"""
    },
    "legacy-archaeologist": {
        "title": "🏛️ legacy-archaeologist",
        "badge": "badge-purple",
        "badge_text": "老项目考古",
        "duty": "剖析老代码路由切片，写行为锁死探针。",
        "output": "<code>legacy_arch.md</code>、行为锁死探针。",
    },
    "code-quality-reviewer": {
        "title": "🔍 code-quality-reviewer",
        "badge": "badge-warning",
        "badge_text": "质量审查",
        "duty": "静态扫描 NPE、内存泄漏、并发隐患、SQL注入与圈复杂度过高代码。",
        "output": "<code>code-quality-report.md</code> 代码审查报告、微创重构建议。",
    },
    "poc-tech-prototype": {
        "title": "⚡ poc-tech-prototype",
        "badge": "badge-danger",
        "badge_text": "技术PoC(按需)",
        "duty": "探明技术迷雾，在隔离沙盒中验证第三方 API、复杂算法与技术选型可行性（简单 CRUD 自动跳过）。",
        "output": "<code>scratch/poc_sandbox/</code> 沙盒验证代码、<code>poc-report.md</code> 技术可行性报告。",
    }
}

def generate_skills_router_text(skills_list):
    """将技能元数据列表格式化为 Markdown 路由条目"""
    if not skills_list:
        return "- （无可用 Skill 注册包）"
    
    lines = []
    for s in skills_list:
        desc = s["desc"]
        # 常驻规则文件要省 token：描述截断到 70 字，完整版仍保留在 SKILL.md
        if len(desc) > 42:
            desc = desc[:42].rstrip() + "…"
        lines.append(f"- **`{s['name']}`**：{desc}")
    return "\n".join(lines)

def generate_skills_html_cards(skills_list):
    cards_html = ['<div class="grid-2">']
    for idx, s in enumerate(skills_list, 1):
        name = s["name"]
        meta = KNOWN_SKILLS_META.get(name, {})
        
        title = meta.get("title", f"🧩 {name}")
        badge_cls = meta.get("badge", "badge-primary")
        badge_txt = meta.get("badge_text", "扩展技能")
        duty = meta.get("duty", s.get("desc", "自定义 AI 扩展技能"))
        output = meta.get("output", f"<code>.agents/skills/{name}/</code> 对应产出物")
        extra_html = meta.get("extra_html", "")

        card = f"""                    <div class="card">
                        <div class="card-header">
                            <div class="card-title">{idx}. {title}</div>
                            <span class="badge {badge_cls}">{badge_txt}</span>
                        </div>
                        <div class="card-body">
                            <p><strong>白话职责</strong>：{duty}</p>
                            <p><strong>核心产出</strong>：{output}</p>{extra_html}
                        </div>
                    </div>"""
        cards_html.append(card)
    
    cards_html.append('                </div>')
    return "\n".join(cards_html)

def generate_skills_html_mermaid(skills_list):
    lines = [
        '<pre class="mermaid">',
        'graph LR'
    ]
    
    known_nodes = {
        "legacy-archaeologist": 'S0["🏛️ legacy-archaeologist<br/>(1.老项目逆向考古)"]',
        "requirement-discovery": 'S1["🔍 requirement-discovery<br/>(2.需求白话澄清)"]',
        "archetype-architect": 'S2["🏛️ archetype-architect<br/>(3.UI交互与业务原型)"]',
        "poc-tech-prototype": 'S7["⚡ poc-tech-prototype<br/>(4.技术PoC探针/按需)"]',
        "design-spec-architect": 'S3["📐 design-spec-architect<br/>(5.DLD详细设计说明书)"]',
        "coding-standards": 'S4["💻 coding-standards<br/>(6.按DLD契约写代码)"]',
        "code-quality-reviewer": 'S6["🔍 code-quality-reviewer<br/>(7.静态质量审查与重构)"]',
        "quality-verifier": 'S5["🧪 quality-verifier<br/>(8.测试碰撞与小黄鸭)"]'
    }

    present_keys = [s["name"] for s in skills_list]
    for key, node_str in known_nodes.items():
        if key in present_keys:
            lines.append(f'    {node_str}')
    
    extra_nodes = []
    for idx, s in enumerate(skills_list):
        if s["name"] not in known_nodes:
            node_id = f'SE{idx}'
            clean_desc = s["desc"][:15] + "..." if len(s["desc"]) > 15 else s["desc"]
            lines.append(f'    {node_id}["🧩 {s["name"]}<br/>({clean_desc})"]')
            extra_nodes.append(node_id)

    if "legacy-archaeologist" in present_keys and "requirement-discovery" in present_keys:
        lines.append('    S0 -->|老代码地图| S1')
    if "requirement-discovery" in present_keys and "archetype-architect" in present_keys:
        lines.append('    S1 -->|澄清需求基线| S2')

    if "archetype-architect" in present_keys and "poc-tech-prototype" in present_keys:
        lines.append('    S2 -->|复杂/迷雾场景| S7')
        if "design-spec-architect" in present_keys:
            lines.append('    S7 -->|PoC可行性报告| S3')
            lines.append('    S2 -.->|简单CRUD跳过PoC| S3')
    elif "archetype-architect" in present_keys and "design-spec-architect" in present_keys:
        lines.append('    S2 -->|生成交互原型| S3')

    if "design-spec-architect" in present_keys and "coding-standards" in present_keys:
        lines.append('    S3 -->|输出 DLD 契约| S4')
    if "coding-standards" in present_keys and "code-quality-reviewer" in present_keys:
        lines.append('    S4 -->|编写业务代码| S6')
    if "code-quality-reviewer" in present_keys and "quality-verifier" in present_keys:
        lines.append('    S6 -->|静态检测与重构| S5')
    elif "coding-standards" in present_keys and "quality-verifier" in present_keys:
        lines.append('    S4 -->|编写业务代码| S5')
    
    if "quality-verifier" in present_keys:
        lines.append('    S5 -->|发现 BUG 小黄鸭排错| S4')
        lines.append('    S5 -->|验收打钩同步文档| S3')

    last_node = "S5" if "quality-verifier" in present_keys else ("S4" if "coding-standards" in present_keys else "S1")
    for ex_id in extra_nodes:
        lines.append(f'    {last_node} -->|增强扩展协作| {ex_id}')
        lines.append(f'    {ex_id} -.->|反哺工作流| S3')

    lines.append('</pre>')
    return "\n".join(lines)

# ---------------------------------------------------------------------------
# SOP 叙述自动注入 (Method A)：从 WORKFLOW_GUIDE.md 的 ### [KEY] 子章节渲染注入 html
# ---------------------------------------------------------------------------

def _inline_md(text):
    """处理行内 **bold** 与 `code` 标记（先做 HTML 转义，再补标签）。"""
    text = text.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    text = re.sub(r'`([^`]+)`', r'<code>\1</code>', text)
    text = re.sub(r'\*\*([^*]+)\*\*', r'<strong>\1</strong>', text)
    # 允许换行标签（Markdown 表格单元格里常用），其余 HTML 仍保持转义
    text = re.sub(r'&lt;br\s*/?&gt;', '<br/>', text, flags=re.IGNORECASE)
    return text


def _render_md_table(rows):
    """将 Markdown 表格行渲染为带 manual-table 类的 HTML 表格。"""
    data = [r for r in rows if not re.match(r'^\s*\|?[\s:\-|]+\|?\s*$', r)]
    if len(data) < 1:
        return ''
    out = ['<table class="manual-table">']
    header_cells = [c.strip() for c in data[0].strip().strip('|').split('|')]
    out.append('<thead><tr>' + ''.join(f'<th>{_inline_md(c)}</th>' for c in header_cells) + '</tr></thead>')
    out.append('<tbody>')
    for row in data[1:]:
        cells = [c.strip() for c in row.strip().strip('|').split('|')]
        out.append('<tr>' + ''.join(f'<td>{_inline_md(c)}</td>' for c in cells) + '</tr>')
    out.append('</tbody></table>')
    return '\n'.join(out)


def render_md_to_html(md):
    """聚焦型 Markdown → HTML 渲染器，覆盖 SOP 叙述所需的语法子集。

    v2：连续普通文本行会合并进同一个 <p>（Markdown 软换行语义），
    避免"每行一个段落"导致的碎片化排版。
    """
    lines = md.splitlines()
    out = []
    in_list = None
    in_code = False
    code_buf = []
    code_lang = ''
    table_buf = []
    para_buf = []

    def close_list():
        nonlocal in_list
        if in_list:
            out.append(f'</{in_list}>')
            in_list = None

    def flush_table():
        nonlocal table_buf
        if table_buf:
            out.append(_render_md_table(table_buf))
            table_buf = []

    def flush_para():
        nonlocal para_buf
        if para_buf:
            out.append('<p>' + ' '.join(_inline_md(x) for x in para_buf) + '</p>')
            para_buf = []

    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        if stripped.startswith('```'):
            if not in_code:
                close_list(); flush_table(); flush_para()
                in_code = True; code_buf = []; code_lang = stripped[3:].strip()
            else:
                close_list()
                code_text = '\n'.join(code_buf)
                if code_lang == 'mermaid':
                    # 必须与页面 JS（renderPanelMermaid）约定一致：container > viewport > pre
                    out.append(
                        '<div class="mermaid-container">'
                        '<div class="mermaid-toolbar">'
                        '<span class="pan-tip">💡 支持鼠标拖拽平移 &amp; 滚轮按需缩放</span>'
                        '<div class="toolbar-group">'
                        '<button class="tool-btn" onclick="zoomMermaid(this, 1.25)">🔍 放大</button>'
                        '<button class="tool-btn" onclick="zoomMermaid(this, 0.8)">🔍 缩小</button>'
                        '<button class="tool-btn" onclick="resetMermaid(this)">🔄 重置</button>'
                        '<button class="tool-btn" onclick="openModalMermaid(this)">🖥️ 全屏查看</button>'
                        '</div></div>'
                        '<div class="mermaid-viewport">'
                        f'<pre class="mermaid">\n{code_text}\n</pre>'
                        '</div></div>'
                    )
                else:
                    esc = code_text.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
                    out.append(f'<pre><code>{esc}</code></pre>')
                in_code = False; code_lang = ''
            i += 1; continue
        if in_code:
            code_buf.append(line); i += 1; continue
        if '|' in stripped and stripped.startswith('|'):
            close_list(); flush_para()
            table_buf.append(stripped); i += 1; continue
        if table_buf and not stripped.startswith('|'):
            flush_table()
        m = re.match(r'^(#{2,4})\s+(.*)$', line)
        if m:
            close_list(); flush_table(); flush_para()
            tag = 'h%d' % min(len(m.group(1)) + 1, 5)
            out.append(f'<{tag}>{_inline_md(m.group(2))}</{tag}>')
            i += 1; continue
        if re.match(r'^---+$', stripped):
            close_list(); flush_table(); flush_para(); out.append('<hr/>'); i += 1; continue
        lm = re.match(r'^(\s*)[-*]\s+(.*)$', line)
        if lm:
            flush_table(); flush_para()
            if in_list != 'ul':
                close_list(); out.append('<ul>'); in_list = 'ul'
            out.append(f'<li>{_inline_md(lm.group(2))}</li>')
            i += 1; continue
        om = re.match(r'^(\s*)\d+\.\s+(.*)$', line)
        if om:
            flush_table(); flush_para()
            if in_list != 'ol':
                close_list(); out.append('<ol>'); in_list = 'ol'
            out.append(f'<li>{_inline_md(om.group(2))}</li>')
            i += 1; continue
        if stripped == '':
            close_list(); flush_table(); flush_para(); i += 1; continue
        # 普通文本：累积到段落缓冲，等遇到结构性行再整体输出
        para_buf.append(stripped)
        i += 1
    close_list(); flush_table(); flush_para()
    return '\n'.join(out)


SOP_FILES = [
    # (指令, 文件stem, 轨道, 轨道中文)  —— 唯一顺序来源
    ("0",  "00-sop-overview",     "meta",    "元 · 导航"),
    ("1",  "01-sop-planning",     "main",    "主链"),
    ("2",  "02-sop-tech-design",  "main",    "主链"),
    ("3",  "03-sop-coding",       "main",    "主链"),
    ("4",  "04-sop-code-review",  "main",    "主链"),
    ("5",  "05-sop-review",       "main",    "主链"),
    ("6",  "06-sop-integration",  "main",    "主链"),
    ("7",  "07-sop-acceptance",   "main",    "主链"),
    ("8",  "08-sop-deploy",       "deliver", "交付链"),
    ("9",  "09-sop-git-release",  "deliver", "交付链"),
    ("B1", "B1-sop-legacy",       "bypass",  "旁路"),
    ("B2", "B2-sop-fasttrack",    "bypass",  "旁路"),
    ("B3", "B3-sop-debug",        "bypass",  "旁路"),
    ("B4", "B4-sop-troubleshoot", "bypass",  "旁路"),
    ("B5", "B5-sop-archive",      "bypass",  "旁路"),
    ("B6", "B6-sop-resume",       "bypass",  "旁路"),
]
TRACK_BADGE = {"meta": "badge-primary", "main": "badge-success",
               "deliver": "badge-warning", "bypass": "badge-purple"}
TRACK_TITLE = {"meta": "🧭 元 · 导航",
               "main": "🔄 主链 1~7（按序）",
               "deliver": "🚀 交付链 8~9",
               "bypass": "↩️ 旁路 B1~B6（不改状态）"}


def _read_sop(stem):
    path = os.path.join(".ai", "sop", stem + ".md")
    if not os.path.exists(path):
        return ""
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def parse_sop_stem(stem):
    """解析单篇 SOP：返回 (title, subtitle, body_md)。"""
    text = _read_sop(stem)
    if not text:
        return stem, "", ""
    m = re.search(r"^#\s+(.+)$", text, re.MULTILINE)
    title = m.group(1).strip() if m else stem
    title = re.sub(r"^\[[^\]]+\]\s*", "", title).strip()
    title = re.sub(r"\s*\([^)]*\)\s*$", "", title).strip()
    subtitle = ""
    for line in text.splitlines():
        if line.startswith("> ") and "触发" in line:
            subtitle = line[2:].strip().replace("**", "")
            break
    idx = text.find("\n## ")
    body = text[idx + 1:] if idx > 0 else text
    return title, subtitle, body


def scan_sop_registry():
    """按 SOP_FILES 顺序解析全部 SOP，返回 [{key, stem, track, title, goal}]。"""
    rows = []
    for key, stem, track, _label in SOP_FILES:
        text = _read_sop(stem)
        if not text:
            print(f"⚠️ 缺失 SOP 文件: {stem}.md")
            continue
        title, _sub, _body = parse_sop_stem(stem)
        goal = ""
        gm = re.search(r"##\s*📌\s*核心目标\s*\n+(.+?)(?:\n\s*\n|\n#)", text, re.DOTALL)
        if not gm:
            gm = re.search(r"^##\s+[^\n]+\n+(.+?)(?:\n\s*\n|\n#)", text, re.MULTILINE | re.DOTALL)
        if gm:
            flat = re.sub(r"\s+", " ", gm.group(1)).strip()
            flat = re.sub(r"^[-*>]\s*", "", flat)
            goal = re.split(r"[。；;]", flat)[0].strip()
            if len(goal) > 46:
                goal = goal[:46] + "…"
        rows.append({"key": key, "stem": stem, "track": track,
                     "title": title, "goal": goal or "—"})
    return rows


def generate_sop_index_table(rows):
    """渲染 SOP 索引 Markdown 表格（含轨道列）。"""
    track_short = {"meta": "元", "main": "主链", "deliver": "交付", "bypass": "旁路"}
    lines = [
        "| 指令 | 轨道 | 规范文件 | 主题 | 一句话核心目标 |",
        "| :---: | :---: | :--- | :--- | :--- |",
    ]
    for r in rows:
        lines.append(
            f"| **`[{r['key']}]`** | {track_short.get(r['track'], '')} "
            f"| [`{r['stem']}.md`](.ai/sop/{r['stem']}.md) "
            f"| {r['title']} | {r['goal']} |"
        )
    lines.append("")
    lines.append(f"> 🤖 本表由 `sync_tree.py` 从 `.ai/sop/` 实时生成（共 {len(rows)} 篇），**请勿手抄**。")
    return "\n".join(lines)


def update_sop_index():
    """把自动生成的 SOP 索引表注入 WORKFLOW_GUIDE.md 的 AUTO-SOP-INDEX 锚点。"""
    rows = scan_sop_registry()
    if not rows:
        print("⚠️ 未在 .ai/sop 下解析到任何 SOP，跳过索引生成")
        return
    ok = update_file_anchor("WORKFLOW_GUIDE.md", "AUTO-SOP-INDEX",
                            generate_sop_index_table(rows), is_codeblock=False)
    if ok:
        print(f"🟢 已刷新 SOP 索引表（{len(rows)} 篇）→ WORKFLOW_GUIDE.md")
    else:
        print("⚠️ WORKFLOW_GUIDE.md 缺少 AUTO-SOP-INDEX 锚点，跳过 SOP 索引注入")


def extract_panel_block(md_text):
    """取出 md 中 <!-- PANEL:START --> ... <!-- PANEL:END --> 之间的内容。"""
    m = re.search(r"<!--\s*PANEL:START\s*-->(.*?)<!--\s*PANEL:END\s*-->", md_text, re.DOTALL)
    return m.group(1).strip() if m else md_text.strip()


def update_handbook_panel():
    """把 BEGINNER_HANDBOOK.md 的上手篇渲染注入 html 的 AUTO-HANDBOOK 锚点。"""
    md_path, html_path = "BEGINNER_HANDBOOK.md", "workflow_guide.html"
    if not os.path.exists(md_path) or not os.path.exists(html_path):
        print("⚠️ 缺少 BEGINNER_HANDBOOK.md 或 workflow_guide.html，跳过手册注入")
        return
    with open(md_path, "r", encoding="utf-8") as f:
        body = extract_panel_block(f.read())
    rendered = render_md_to_html(body)
    ok = update_file_anchor(html_path, "AUTO-HANDBOOK", rendered, is_codeblock=False)
    if ok:
        print("🟢 已把《新手辅助开发手册》注入 workflow_guide.html 的 AUTO-HANDBOOK 面板")
    else:
        print("⚠️ workflow_guide.html 缺少 AUTO-HANDBOOK 锚点，跳过手册注入")


def _normalize_sop_key(key):
    return key.replace('+', 'plus')


def _sop_nav_html():
    """按轨道分组生成侧边栏菜单。"""
    out = []
    order = ["meta", "main", "deliver", "bypass"]
    for track in order:
        items = [r for r in SOP_FILES if r[2] == track]
        if not items:
            continue
        out.append('            <div class="menu-section">')
        out.append(f'                <div class="menu-title">{TRACK_TITLE[track]}</div>')
        for key, stem, _t, _l in items:
            title, _sub, _body = parse_sop_stem(stem)
            out.append(f"""                <div class="menu-item" onclick="switchPanel('panel-sop-{key}', this)">
                    <span>[{key}] {title}</span>
                    <span class="code-tag">[{key}]</span>
                </div>""")
            if key == "1":
                out.append("""                <div class="menu-item" onclick="switchPanel('panel-sop-1', this)">
                    <span>[1+] 📦 新大模块 + 原型</span>
                    <span class="code-tag">[1+]</span>
                </div>""")
        out.append("            </div>")
        out.append("")
    return "\n".join(out).rstrip()


def _sop_panels_html():
    """从 .ai/sop/*.md 直接渲染全部 SOP 面板（单一事实源）。"""
    parts = []
    for key, stem, track, label in SOP_FILES:
        title, sub, body = parse_sop_stem(stem)
        rendered = render_md_to_html(body)
        badge = TRACK_BADGE.get(track, "badge-primary")
        sub_html = _inline_md(sub) if sub else ""
        parts.append(f"""            <div id="panel-sop-{key}" class="content-panel">
                <div class="panel-header">
                    <div class="panel-title-group">
                        <h1 class="panel-title">[{key}] {title}</h1>
                        <span class="badge {badge}">{label}</span>
                    </div>
                    <p class="panel-subtitle">{sub_html}</p>
                </div>

                <div class="card">
                    <div class="card-header">
                        <div class="card-title">📄 规范正文（自动同步 · 单一事实源）</div>
                        <span class="badge badge-primary">{stem}.md</span>
                    </div>
                    <div class="card-body">
                        <div class="sop-body">
{rendered}
                        </div>
                    </div>
                </div>
            </div>""")
    return "\n\n".join(parts)


def update_sop_stage_panels():
    """把侧边栏导航与全部 SOP 面板注入 workflow_guide.html（内容源 = .ai/sop/*.md）。"""
    html_path = "workflow_guide.html"
    if not os.path.exists(html_path):
        print("⚠️ 缺少 workflow_guide.html，跳过 SOP 面板注入")
        return
    ok_nav = update_file_anchor(html_path, "AUTO-SOP-NAV", _sop_nav_html(), is_codeblock=False)
    ok_panel = update_file_anchor(html_path, "AUTO-SOP-PANELS", _sop_panels_html(), is_codeblock=False)
    if ok_nav and ok_panel:
        print(f"🟢 已重建 {len(SOP_FILES)} 个 SOP 面板与侧边栏导航（源：.ai/sop/*.md）")
    else:
        print("⚠️ workflow_guide.html 缺少 AUTO-SOP-NAV / AUTO-SOP-PANELS 锚点，跳过")


CAP_FILE = os.path.join(".ai", "sop", "_capability-baseline.md")
CAP_ID = re.compile(r"C-[0-9A-Za-z]+-\d+")


def _parse_cap_rows(text):
    """解析基线明细表：返回 [(id, desc, owner)]，保持原始顺序。"""
    rows = []
    for line in text.splitlines():
        if not line.strip().startswith("|"):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) < 3:
            continue
        m = CAP_ID.search(cells[0])
        if not m:
            continue
        rows.append((m.group(0), cells[1], cells[2].strip("` ")))
    return rows


MODULE_ROOTS = ["base_template/docs/modules", "docs/modules"]


def update_module_index():
    """扫描各模块目录，自动生成 tier2_modules.md 的模块矩阵（派生视图，禁止手写）。"""
    target = os.path.join(".ai", "tier2_modules.md")
    if not os.path.exists(target):
        print("⚠️ 缺少 .ai/tier2_modules.md，跳过模块索引生成")
        return

    rows = []
    for root in MODULE_ROOTS:
        if not os.path.isdir(root):
            continue
        docs_base = os.path.dirname(root)          # 例：base_template/docs
        for name in sorted(os.listdir(root)):
            mdir = os.path.join(root, name)
            if not os.path.isdir(mdir):
                continue
            files = os.listdir(mdir)
            prd = next((f for f in files if f.startswith("prd-") and f.endswith(".md")), None)
            if not prd:
                continue

            assets = []
            if any(f.startswith("mockup") for f in files):
                assets.append("原型")
            if any(f.startswith("dld-") for f in files):
                assets.append("DLD")
            if os.path.exists(os.path.join(docs_base, "srs", name)):
                assets.append("SRS")
            if os.path.exists(os.path.join(docs_base, "test-cases", f"{name}-testcase.md")):
                assets.append("TC")
            if os.path.exists(os.path.join(docs_base, "design", name)):
                assets.append("Design")

            title = extract_meta_from_file(os.path.join(mdir, prd)) or name
            title = re.sub(r"^\[[^\]]*\]\s*", "", title)                     # 去 [模块: xxx]
            title = re.sub(r"^[A-Z][A-Z0-9_.\-]{3,}\s*[:：]?\s*", "", title)     # 去 PRD-XXX-01: 代码前缀
            title = re.sub(r"\s*[-–—:：]\s*", " ", title).strip()
            title = re.sub(r"\s+PRD\s*$", "", title, flags=re.IGNORECASE).strip()
            title = re.sub(r"\s*需求规格说明书\s*$", "", title).strip()
            if len(title) > 26:
                title = title[:26] + "…"
            prd_path = os.path.join(mdir, prd)
            link = os.path.relpath(prd_path, ".ai")
            total = 1 + len(assets)
            mark = "🟢" if total >= 5 else ("🟡" if total >= 2 else "⚪")
            rows.append((name, title, link, f"{mark} {total}/6", " · ".join(["PRD"] + assets) or "PRD"))

    if not rows:
        body = "> 📭 当前未发现任何模块 PRD。新建模块时把 `prd-<module>-v1.md` 放进 `docs/modules/<module>/` 即可自动出现。"
    else:
        lines = ["| 模块 ID | 标题 | PRD | 齐备度 | 已具备资产 |",
                 "| :--- | :--- | :--- | :---: | :--- |"]
        for name, title, link, ratio, assets in rows:
            lines.append(f"| `{name}` | {title} | [{os.path.basename(link)}]({link}) | {ratio} | {assets} |")
        lines.append("")
        lines.append(f"> 🤖 共 {len(rows)} 个模块，由 `sync_tree.py` 扫描 "
                     + " / ".join(f"`{r}`" for r in MODULE_ROOTS if os.path.isdir(r)) + " 自动汇总。")
        body = "\n".join(lines)

    if update_file_anchor(target, "AUTO-MODULE-INDEX", body, is_codeblock=False):
        print(f"🟢 已刷新模块索引：{len(rows)} 个模块 → .ai/tier2_modules.md")


PLAYBOOK_MD = os.path.join("docs", "playbooks", "new-module-playbook.md")
GLOSSARY_MD = os.path.join("docs", "playbooks", "glossary.md")
DEMO_MD = os.path.join("docs", "playbooks", "demo-scripts.md")

DEMO_RE = re.compile(
    r"<!--\s*DEMO\s*\|\s*(?P<pid>[^|]+?)\s*\|\s*(?P<title>[^|]+?)\s*\|\s*"
    r"(?P<badge>[^|]+?)\s*\|\s*(?P<sub>[^|]+?)\s*-->(?P<body>.*?)<!--\s*/DEMO\s*-->",
    re.DOTALL,
)
STEP_RE = re.compile(
    r"<!--\s*STEP\s*\|\s*(?P<num>[^|]+?)\s*\|\s*(?P<title>[^|]+?)\s*-->"
    r"(?P<body>.*?)<!--\s*/STEP\s*-->",
    re.DOTALL,
)


def _demo_steps_html(body):
    cards = []
    for m in STEP_RE.finditer(body):
        num = m.group("num").strip()
        title = _inline_md(m.group("title").strip())
        you, ai, outs = "", "", []
        for line in m.group("body").strip().splitlines():
            line = line.strip()
            if line.startswith("- YOU:"):
                you = line[len("- YOU:"):].strip()
            elif line.startswith("- AI:"):
                ai = line[len("- AI:"):].strip()
            elif line.startswith("- OUT:"):
                outs.append(line[len("- OUT:"):].strip())
        out_html = "<br/>".join("• " + _inline_md(o) for o in outs) or "—"
        cards.append(
            '                <div class="demo-step-card">\n'
            '                    <div class="demo-step-header">\n'
            '                        <span class="demo-step-num">%s</span>\n'
            '                        <span class="demo-step-title">%s</span>\n'
            '                    </div>\n'
            '                    <div class="chat-flow">\n'
            '                        <div class="chat-msg user">\n'
            '                            <div class="chat-avatar">👤</div>\n'
            '                            <div class="chat-content"><strong>你：</strong><br/>%s</div>\n'
            '                        </div>\n'
            '                        <div class="chat-msg ai">\n'
            '                            <div class="chat-avatar">🤖</div>\n'
            '                            <div class="chat-content"><strong>AI：</strong><br/>%s</div>\n'
            '                        </div>\n'
            '                    </div>\n'
            '                    <div class="chat-output-box">\n'
            '                        <div class="chat-output-title">📄 本步落盘产出：</div>\n'
            '                        %s\n'
            '                    </div>\n'
            '                </div>\n\n'
            % (num, title, _inline_md(you) if you else "—", _inline_md(ai) if ai else "—", out_html)
        )

    for note in re.findall(r"^- NOTE:\s*(.+)$", body, re.MULTILINE):
        cards.append(
            '                <div class="chat-output-box" style="border-left-color:#8b5cf6;">\n'
            '                    <div class="chat-output-title">↩️ 旁路说明</div>\n'
            '                    %s\n'
            '                </div>\n\n' % _inline_md(note.strip())
        )
    return "".join(cards)


def update_demo_panels():
    """把 docs/playbooks/demo-scripts.md 渲染成 Demo 面板（注入 AUTO-DEMO 锚点）。"""
    if not os.path.exists(DEMO_MD):
        print("⚠️ 缺少 demo-scripts.md，跳过 Demo 注入")
        return
    with open(DEMO_MD, "r", encoding="utf-8") as f:
        text = f.read()

    demos = list(DEMO_RE.finditer(text))
    if not demos:
        print("⚠️ demo-scripts.md 未解析到 DEMO 块，跳过")
        return

    parts = []
    for m in demos:
        pid = m.group("pid").strip()
        title = _inline_md(m.group("title").strip())
        badge = m.group("badge").strip()
        sub = _inline_md(m.group("sub").strip())
        parts.append(
            '            <div id="%s" class="content-panel">\n'
            '                <div class="panel-header">\n'
            '                    <div class="panel-title-group">\n'
            '                        <h1 class="panel-title">%s</h1>\n'
            '                        <span class="badge badge-success">%s</span>\n'
            '                    </div>\n'
            '                    <p class="panel-subtitle">💡 %s</p>\n'
            '                </div>\n\n'
            '%s'
            '            </div>\n'
            % (pid, title, badge, sub, _demo_steps_html(m.group("body")))
        )

    if update_file_anchor("workflow_guide.html", "AUTO-DEMO", "\n".join(parts).rstrip(), is_codeblock=False):
        print("🟢 已重建 %d 个 Demo 面板（源：docs/playbooks/demo-scripts.md）" % len(parts))


GLOSS_BADGE = {
    "指令": "badge-success", "概念": "badge-primary", "状态": "badge-danger",
    "记忆层": "badge-warning", "门禁": "badge-danger", "文档": "badge-primary",
    "方法": "badge-purple", "产物": "badge-success", "技术": "badge-warning",
    "规则": "badge-warning", "原则": "badge-primary", "文件": "badge-success", "工具": "badge-warning",
}


def _parse_glossary_rows():
    """解析 glossary.md 的四列表格：术语 / 分类 / 解释 / 出现位置。"""
    if not os.path.exists(GLOSSARY_MD):
        return []
    with open(GLOSSARY_MD, "r", encoding="utf-8") as f:
        text = f.read()
    rows = []
    for line in text.splitlines():
        line = line.strip()
        if not line.startswith("|") or set(line) <= set("|:- "):
            continue
        cells = [c.strip() for c in line.strip("|").split("|")]
        if len(cells) < 4 or cells[0] in ("术语",):
            continue
        rows.append(cells[:5])
    return rows


def update_windsurfrules_mirror():
    """把 .cursorrules 镜像成 .windsurfrules（消除两份手写规则的漂移）。"""
    src, dst = ".cursorrules", ".windsurfrules"
    if not os.path.exists(src) or not os.path.exists(dst):
        return
    with open(src, "r", encoding="utf-8") as f:
        body = f.read()

    # 换标题 + 去掉内部锚点标记（避免嵌套锚点）
    body = body.replace("(Vibe Coding 3.0 · 精简版)", "(Windsurf · 自动镜像)", 1)
    body = re.sub(r"<!--\s*AUTO-SKILLS-MACRO:(?:START|END)\s*-->\n?", "", body)

    if update_file_anchor(dst, "AUTO-RULES", body.strip(), is_codeblock=False):
        print("🟢 已把 .cursorrules 镜像到 .windsurfrules（单一事实源）")


def update_glossary_panel():
    """把 glossary.md 渲染成可搜索的卡片式术语表（注入 AUTO-GLOSSARY 锚点）。"""
    rows = _parse_glossary_rows()
    if not rows:
        print("⚠️ 未解析到术语表，跳过")
        return

    cards, cats = [], []
    for row in rows:
        term, cat, desc = row[0], row[1], row[2]
        scene = row[3] if len(row) > 3 else ""
        where = row[4] if len(row) > 4 else ""
        if cat not in cats:
            cats.append(cat)
        badge = GLOSS_BADGE.get(cat, "badge-primary")
        cards.append(
            '                            <div class="gloss-card" data-cat="%s">\n'
            '                                <div class="gloss-card-head">\n'
            '                                    <span class="gloss-term">%s</span>\n'
            '                                    <span class="badge %s">%s</span>\n'
            '                                </div>\n'
            '                                <p class="gloss-desc">%s</p>\n'
            '                                <p class="gloss-scene"><span class="gloss-label">什么时候用</span>%s</p>\n'
            '                                <p class="gloss-where">📍 %s</p>\n'
            '                            </div>'
            % (cat, _inline_md(term), badge, cat,
               _inline_md(desc), _inline_md(scene) or "—", _inline_md(where) or "—")
        )

    chips = "\n".join(
        '                                <button class="gloss-chip" data-cat="%s" '
        'onclick="filterGlossary(\'%s\', this)">%s</button>' % (c, c, c)
        for c in cats
    )

    html = (
        '                        <div class="gloss-toolbar">\n'
        '                            <input id="glossSearch" type="search" '
        'placeholder="🔍 输入术语或关键字，例如 LGTM / tier / 回滚 / 原型"\n'
        '                                   oninput="filterGlossary(null, null)">\n'
        '                            <div class="gloss-chips">\n'
        '                                <button class="gloss-chip active" data-cat="全部" '
        'onclick="filterGlossary(\'全部\', this)">全部</button>\n'
        + chips + '\n'
        '                            </div>\n'
        '                            <div class="gloss-count">共 <b id="glossCount">' + str(len(rows))
        + '</b> 个术语</div>\n'
        '                        </div>\n'
        '                        <div class="gloss-grid">\n'
        + "\n".join(cards) + '\n'
        '                        </div>'
    )

    if update_file_anchor("workflow_guide.html", "AUTO-GLOSSARY", html, is_codeblock=False):
        print("🟢 已注入术语表：%d 个术语 / %d 个分类（卡片式，含使用场景）" % (len(rows), len(cats)))


def update_playbook_panel():
    """把 docs/playbooks/new-module-playbook.md 的 PANEL 区块注入 html 的 AUTO-PLAYBOOK 锚点。"""
    html_path = "workflow_guide.html"
    if not os.path.exists(PLAYBOOK_MD) or not os.path.exists(html_path):
        print("⚠️ 缺少 playbook 或 workflow_guide.html，跳过注入")
        return
    with open(PLAYBOOK_MD, "r", encoding="utf-8") as f:
        body = extract_panel_block(f.read())
    ok = update_file_anchor(html_path, "AUTO-PLAYBOOK", render_md_to_html(body), is_codeblock=False)
    if ok:
        print("🟢 已把《新模块落地全流程 Playbook》注入 workflow_guide.html 的 AUTO-PLAYBOOK 面板")
    else:
        print("⚠️ workflow_guide.html 缺少 AUTO-PLAYBOOK 锚点，跳过")


def update_capability_summary():
    """从基线明细表自动汇总各 SOP 的能力点数（防止手写计数漂移）。"""
    if not os.path.exists(CAP_FILE):
        print("⚠️ 未找到能力点基线文件，跳过汇总")
        return
    with open(CAP_FILE, "r", encoding="utf-8") as f:
        text = f.read()

    rows = _parse_cap_rows(text)
    if not rows:
        print("⚠️ 基线明细表解析为空，跳过汇总")
        return

    order, counts = [], {}
    for _cid, _desc, owner in rows:
        if owner not in counts:
            order.append(owner)
            counts[owner] = 0
        counts[owner] += 1

    lines = ["| 归属 SOP | 能力点数 |", "| :--- | :---: |"]
    for owner in order:
        lines.append(f"| `{owner}` | {counts[owner]} |")
    lines.append(f"| **合计** | **{len(rows)}** |")

    if update_file_anchor(CAP_FILE, "AUTO-CAP-SUMMARY", "\n".join(lines), is_codeblock=False):
        print(f"🟢 已刷新能力点汇总表：{len(rows)} 项 / {len(order)} 篇 SOP")


def update_workflow_guide_html(skills_list):
    html_path = "workflow_guide.html"
    if not os.path.exists(html_path):
        return

    count = len(skills_list)
    menu_text = f"<span>📚 {count} 大 Skill 协作拓扑图</span>"
    title_text = f'<h1 class="panel-title">📚 全套 {count} 大 Skill 技能包协作拓扑图谱</h1>'
    subtitle_text = f'<div class="card-title">📊 {count} 大 Skill 互相协作网络 Mermaid 关系图</div>'
    mermaid_text = generate_skills_html_mermaid(skills_list)
    cards_text = generate_skills_html_cards(skills_list)

    update_file_anchor(html_path, "AUTO-SKILLS-MENU", menu_text, is_codeblock=False)
    update_file_anchor(html_path, "AUTO-SKILLS-TITLE", title_text, is_codeblock=False)
    update_file_anchor(html_path, "AUTO-SKILLS-SUBTITLE", subtitle_text, is_codeblock=False)
    update_file_anchor(html_path, "AUTO-SKILLS-MERMAID", mermaid_text, is_codeblock=False)
    update_file_anchor(html_path, "AUTO-SKILLS-CARDS", cards_text, is_codeblock=False)

NODE_DESC_OVERRIDE = {
    # 目录：优先于该目录下的 README（避免示例工程的占位标题污染树）
    "base_template": "💻 业务工程脚手架（真实开发在这里）",
    ".ai": "⚙️ 统一工作流控制中心（三层记忆金字塔）",
    ".agents": "🧩 AI 专属技能包库",
    "scripts": "🛠️ 自动化控制与分析脚本库",
    "sop": "📜 全套 16 篇 SOP（主链 0~9 · 旁路 B1~B6）",
    "docs": "📁 需求 / 设计 / 计划 / 交接文档",
    # 文件：优先于 Markdown 首行标题解析
    "README.md": "📖 项目门面与使用指南",
    "WORKFLOW_GUIDE.md": "📖 完整工作流与设计指南",
    "BEGINNER_HANDBOOK.md": "🚀 新手辅助开发手册（保姆级上手）",
    ".cursorrules": "🧠 AI 行为约束与数字路由表",
    ".windsurfrules": "🧠 IDE 规则适配文件",
    "tier1_snapshot.md": "① 极简快照名片（<30 行，[B6] 复活只读它）",
    "tier2_modules.md": "② 大模块矩阵索引",
    "tier2_legacy_arch.md": "② 老项目切片与拓扑图谱元数据池",
    "tier3_status.md": "③ 任务看板状态机（仅 board.py 可写）",
    "tier3_handover.md": "③ 交接黑匣子（append-only 事件日志）",
    "board.py": "📊 确定性看板与任务状态控制脚本",
    "arch.py": "🏛️ 老项目拓扑图谱分析脚本",
    "sync_tree.py": "🔄 目录树 / Skill 路由 / 手册自动同步脚本",
    "gate.py": "🚦 统一质量门禁（6 项看门狗 + SQL 配对 + 元门禁）",
}


def get_node_info(path):
    name = os.path.basename(path)
    is_dir = os.path.isdir(path)
    icon = EMOJI_MAP.get(name, "📁" if is_dir else "📄")

    label = f"{icon} {name}/" if is_dir else f"{icon} {name}"
    desc = NODE_DESC_OVERRIDE.get(name, "")

    if not desc:
        if is_dir:
            readme_path = os.path.join(path, "README.md")
            if os.path.exists(readme_path):
                desc = extract_meta_from_file(readme_path)
            if not desc:
                desc = DEFAULT_DIR_DESCS.get(name, "")
        elif name.endswith(".md"):
            desc = extract_meta_from_file(path)

    if desc and len(desc) > 42:
        desc = desc[:42] + "…"
    return label, desc


def generate_custom_tree(max_depth=3, max_files=0):
    """生成目录树；max_files>0 时每个目录最多展示 N 个条目，其余折叠为一行。"""
    tree_items = []
    workspace_name = os.path.basename(os.getcwd())
    workspace_desc = "🚀 顶层工作区 (IDE 打开的根目录)"
    tree_items.append((f"{workspace_name}/", workspace_desc))

    def walk_dir(path, depth, prefix=""):
        if depth > max_depth:
            return
        try:
            entries = os.listdir(path)
        except OSError:
            return

        filtered = []
        for e in sorted(entries):
            if e in IGNORE_DIRS or e.startswith('.DS_Store'):
                continue
            if e.startswith('.') and e not in {".ai", ".agents", ".cursorrules", ".windsurfrules"}:
                continue
            filtered.append(e)

        hidden = 0
        if max_files and len(filtered) > max_files:
            hidden = len(filtered) - max_files
            filtered = filtered[:max_files]

        for i, entry in enumerate(filtered):
            is_last = (i == len(filtered) - 1)
            full_path = os.path.join(path, entry)
            is_dir = os.path.isdir(full_path)

            branch = "└── " if is_last else "├── "
            label, desc = get_node_info(full_path)
            node_str = f"{prefix}{branch}{label}"

            tree_items.append((node_str, desc))

            if is_dir and depth < max_depth:
                new_prefix = prefix + ("    " if is_last else "│   ")
                walk_dir(full_path, depth + 1, new_prefix)

        if hidden:
            tree_items.append((f"{prefix}└── …(另有 {hidden} 项)", ""))

    walk_dir(".", 1)

    max_len = max(len(item[0]) for item in tree_items) + 2
    max_len = max(max_len, 38)

    formatted_lines = []
    for node_str, desc in tree_items:
        if desc:
            padding = " " * (max_len - len(node_str))
            formatted_lines.append(f"{node_str}{padding}# {desc}")
        else:
            formatted_lines.append(node_str)

    return "\n".join(formatted_lines)

def update_file_anchor(file_path, anchor_tag, new_content, is_codeblock=True):
    """替换目标 Markdown 文件中的锚点内容"""
    if not os.path.exists(file_path):
        return False

    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    start_pattern = f"<!-- {anchor_tag}:START -->"
    end_pattern = f"<!-- {anchor_tag}:END -->"

    pattern = re.compile(
        re.escape(start_pattern) + r".*?" + re.escape(end_pattern),
        re.DOTALL
    )

    if is_codeblock:
        replacement = f"{start_pattern}\n```plaintext\n{new_content}\n```\n{end_pattern}"
    else:
        replacement = f"{start_pattern}\n{new_content}\n{end_pattern}"

    if pattern.search(content):
        updated_content = pattern.sub(replacement, content)
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(updated_content)
        print(f"🟢 已成功刷新 [{file_path}] 的 {anchor_tag} 动态锚点！")
        return True
    else:
        return False

def sync_all():
    """刷新所有文档中的树锚点与 Skills 路由表"""
    print("🔄 开始动态扫描文件、YAML 元数据并同步目录树与 Skill 路由表...")
    
    # 1. 刷新目录树
    macro_tree = generate_custom_tree(max_depth=2)
    full_tree = generate_custom_tree(max_depth=3, max_files=8)
    update_file_anchor("README.md", "AUTO-TREE-MACRO", macro_tree, is_codeblock=True)
    update_file_anchor("WORKFLOW_GUIDE.md", "AUTO-TREE-FULL", full_tree, is_codeblock=True)

    # 2. 刷新 Skills 路由表与 HTML 图谱
    skills_list = scan_skills_registry()
    skills_text = generate_skills_router_text(skills_list)
    
    update_file_anchor(".cursorrules", "AUTO-SKILLS-MACRO", skills_text, is_codeblock=False)
    update_file_anchor("README.md", "AUTO-SKILLS-MACRO", skills_text, is_codeblock=False)
    
    # 3. 刷新 HTML 交互指南中的 Skills 图谱与卡片
    update_workflow_guide_html(skills_list)

    # 4. 刷新 HTML 中 SOP 叙述面板 (Method A：从 WORKFLOW_GUIDE.md 自动注入)
    update_sop_stage_panels()

    # 5. 从 .ai/sop/ 生成 SOP 索引表（单一事实源，禁止手抄）
    update_sop_index()

    # 6. 把《新手辅助开发手册》注入 HTML 面板
    update_handbook_panel()

    # 7. 从基线明细自动汇总能力点计数（防止手写数字漂移）
    update_capability_summary()

    # 8. 扫描模块目录，生成第二层模块矩阵（派生视图，禁止手写）
    update_module_index()

    # 9. 注入《新模块落地全流程》面板
    update_playbook_panel()

    # 10. 注入可搜索的术语表
    update_glossary_panel()

    # 11. 从剧本 md 重建两个 Demo 面板
    update_demo_panels()

    # 12. 把 .cursorrules 镜像成 .windsurfrules（最后执行，保证内容已填充）
    update_windsurfrules_mirror()

def get_workspace_fingerprint():
    state = []
    for root, dirs, files in os.walk("."):
        dirs[:] = [d for d in dirs if d not in IGNORE_DIRS and not d.startswith('.DS_Store')]
        if any(ign in root for ign in IGNORE_DIRS):
            continue
        valid_files = sorted([f for f in files if f not in IGNORE_DIRS and f != ".DS_Store"])
        try:
            dir_mtime = os.path.getmtime(root)
            state.append((root, dir_mtime, tuple(valid_files)))
        except OSError as e:
            # 单个目录不可读不该中断整棵树的指纹计算，但留一条痕迹便于排查
            print(f"⚠️  跳过不可读目录 {root}: {e}")
    return hash(tuple(state))

def watch_mode():
    print("👀 启动 --watch 自动化守护模式 (同步目录树与 Skill 路由)...")
    sync_all()

    try:
        from watchdog.observers import Observer
        from watchdog.events import FileSystemEventHandler

        class ChangeHandler(FileSystemEventHandler):
            def __init__(self):
                self.last_sync = 0

            def on_any_event(self, event):
                if event.is_directory or any(ign in event.src_path for ign in IGNORE_DIRS):
                    return
                now = time.time()
                if now - self.last_sync > 1.5:
                    self.last_sync = now
                    print(f"\n⚡ 检测到文件/Skill变动 ({os.path.basename(event.src_path)})，自动同步...")
                    sync_all()

        event_handler = ChangeHandler()
        observer = Observer()
        observer.schedule(event_handler, path=".", recursive=True)
        observer.start()
        print("✅ Watchdog 事件监听守护进程运行中...")
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            observer.stop()
        observer.join()

    except ImportError:
        print("💡 未安装 watchdog 库，已启用【全能状态指纹轮询守护】...")
        last_fp = get_workspace_fingerprint()
        try:
            while True:
                time.sleep(1.5)
                current_fp = get_workspace_fingerprint()
                if current_fp != last_fp:
                    print("\n⚡ 检测到文件/Skill变动，自动同步刷新...")
                    sync_all()
                    last_fp = current_fp
        except KeyboardInterrupt:
            print("🛑 守护已停止。")

if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--watch":
        watch_mode()
    else:
        sync_all()
