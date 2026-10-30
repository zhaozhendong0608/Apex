# -*- coding: utf-8 -*-
"""《Apex 工作流 · 内部培训》60 分钟版：6 个大概念 + 每个概念挂 2~3 张详情页。"""

from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR

from deck_lib import *  # noqa
import deck_lib as L

TOTAL = 25
OUT = "Apex工作流-内部培训.pptx"

GREEN = RGBColor(0x2E, 0x6B, 0x4A)
GREEN_L = RGBColor(0xE3, 0xEE, 0xE7)
GREY_L = RGBColor(0xE8, 0xE5, 0xDD)

prs = new_deck()
CW = MR - ML
W2 = (CW - 0.32) / 2
W3 = (CW - 0.56) / 3

CONCEPTS = ["工序", "验收", "契约", "状态", "成本", "包工头"]


# ------------------------------------------------------------------ 模板
def concept(s, idx, cnum, name, lines, sub):
    """大概念页：深色、一句话、无细节。"""
    text(s, ML, 0.92, 4.0, 0.3,
         [dict(t=f"大概念 {cnum:02d} / 06", size=10.5, color=ACCENT, bold=True)], check=False)
    text(s, ML + 2.05, 0.92, 5.0, 0.3,
         [dict(t="· " + name, size=10.5, color=RGBColor(0x8C, 0x91, 0x96))], check=False)

    # 背景水印数字
    text(s, 9.15, 0.55, 3.8, 2.2,
         [dict(t=f"0{cnum}", size=170, bold=True, color=RGBColor(0x20, 0x25, 0x28), align=PP_ALIGN.RIGHT)],
         check=False)

    text(s, ML, 1.95, 9.0, 2.0, [dict(t=t, size=33, bold=True, color=c, line=1.42) for t, c in lines],
         check=True, tag=f"concept{cnum}")
    line_shape(s, ML, 4.35, ML + 1.1, 4.35, ACCENT, 2.0)
    text(s, ML, 4.58, 8.6, 0.9, [dict(t=sub, size=13.5, color=RGBColor(0xB4, 0xB8, 0xBB), line=1.65)],
         check=True, tag=f"csub{cnum}")

    # 六个进度点
    for i in range(6):
        cx = ML + i * 0.30
        filled = (i + 1) == cnum
        rect(s, cx, 6.42, 0.13, 0.13, fill=(ACCENT if filled else RGBColor(0x3A, 0x40, 0x44)),
             radius=0.5)

    footer(s, idx, TOTAL, dark=True)


def detail(s, idx, kicker, title, sub=None, human_note=None, dark=False):
    top = header(s, f"{idx:02d}", kicker, title, sub=sub, dark=dark)
    if human_note:
        rect(s, ML, 6.26, CW, 0.60, fill=(DARK_2 if dark else PAPER), radius=0.03)
        chip(s, ML + 0.16, 6.34, 0.94, 0.44, "用人话说", fill=ACCENT, fg=WHITE, size=9)
        text(s, ML + 1.24, 6.33, CW - 1.46, 0.44,
             [dict(t=human_note, size=10.8, color=(PAPER if dark else INK_SOFT), line=1.3)],
             check=True, tag="human")
    footer(s, idx, TOTAL, dark=dark)
    return top


# ============================================================ 01 封面
s = blank(prs, DARK)
text(s, ML, 0.78, 8.0, 0.3,
     [dict(t="INTERNAL TRAINING  ·  6 个大概念  ·  60 分钟", size=10,
           color=RGBColor(0x8C, 0x91, 0x96))], check=False)

text(s, ML, 1.62, 8.6, 2.6, [
    dict(t="用 AI 写代码，", size=38, bold=True, color=PAPER, line=1.35),
    dict(t="缺的不是能力", size=38, bold=True, color=PAPER, line=1.35),
    dict(t="缺的是能验收的工序", size=38, bold=True, color=ACCENT, line=1.35),
], check=False)

text(s, ML, 4.55, 7.4, 0.9, [
    dict(t="Apex 工作流 · Vibe Coding 3.0", size=14, bold=True, color=PAPER, line=1.6),
    dict(t="不讲 16 篇 SOP，只讲 6 个能记住的概念，和每个概念下最该知道的那几件事。", size=12,
         color=RGBColor(0xA8, 0xAD, 0xB1), line=1.6),
], check=False)

line_shape(s, ML, 6.32, 8.4, 6.32, LINE_DK, 0.9)
text(s, ML, 6.5, 8.2, 0.3, [dict(t="面向 开发 / 测试 / 产品  ·  2026.10", size=10,
                                 color=RGBColor(0x8C, 0x91, 0x96))], check=False)

# 右侧目录：六个概念
for i, name in enumerate(CONCEPTS):
    yy = 1.62 + i * 0.62
    text(s, 9.70, yy - 0.13, 0.5, 0.3, [dict(t=f"0{i+1}", size=9.5, color=ACCENT, font=MONO, bold=True)],
         check=False)
    text(s, 10.22, yy - 0.17, 1.9, 0.34, [dict(t=name, size=13, color=PAPER)], check=False)
    line_shape(s, 11.85, yy, 12.55, yy, RGBColor(0x33, 0x39, 0x3C), 0.9)

footer(s, 1, TOTAL, dark=True)
notes(s, "开场 1 分钟。先给结构：6 个概念，每个概念后面跟 2~3 张小页，每页只讲一件事。\n"
         "不要求记住指令编号，只要记住「为什么会这么做」。")

# ============================================================ 02 翻车现场
s = blank(prs)
top = detail(s, 2, "开场 · 先对齐一下体感", "这四句话，你一定听过其中三句",
             human_note="这四件事，没有一件是「模型不够聪明」造成的 —— 都是没有工序。")

fails = [("01", "「AI 说做完了」", "一跑就崩。它只验证了自己想象的那条路径。"),
         ("02", "「先跑起来再说」", "页面里是写死的假数据，联调那天集体爆炸。"),
         ("03", "「顺手帮我优化下」", "改了 9 个文件，review 时没人看得下去。"),
         ("04", "「聊到第二十五轮」", "它开始忘掉前面定的规则，越改越歪。")]
for i, (num, q, cost) in enumerate(fails):
    xx = ML + (i % 2) * (W2 + 0.32)
    yy = top + 0.10 + (i // 2) * 1.86
    rect(s, xx, yy, W2, 1.66, fill=CARD, line=LINE, line_w=0.75, radius=0.03)
    text(s, xx + 0.28, yy + 0.22, 0.4, 0.28, [dict(t=num, size=9.5, color=ACCENT, font=MONO, bold=True)],
         check=False)
    text(s, xx + 0.72, yy + 0.18, W2 - 1.0, 0.4, [dict(t=q, size=14.5, bold=True, color=INK)], check=False)
    text(s, xx + 0.72, yy + 0.72, W2 - 1.0, 0.7, [dict(t=cost, size=10.8, color=INK_SOFT, line=1.55)],
         check=True, tag=f"fail{i}")

rect(s, ML, 5.72, CW, 0.42, fill=INK, radius=0.03)
text(s, ML, 5.72, CW, 0.42, [dict(t="四个坑，一个共同点：中间没有任何一步「被检查过」。", size=11.5, bold=True,
                                  color=PAPER, align=PP_ALIGN.CENTER)], anchor=MSO_ANCHOR.MIDDLE, check=False)
notes(s, "4 分钟。逐条念，看有没有人点头，现场收集一个真实案例替换掉第 2 或第 3 条。\n"
         "这一页的目的不是讲流程，是先让大家承认：我们都踩过。")

# ============================================================ 03 概念 01
s = blank(prs, DARK)
concept(s, 3, 1, "工序",
        [("把「写个功能」，", PAPER), ("拆成七道能检查的工序", ACCENT)],
        "你不缺更聪明的模型，缺的是一条不会跳步的流水线。\n每一步都留下可以被别人检查的东西，坏消息就会在便宜的时候被发现。")
notes(s, "3 分钟。这是全场第一个概念，要慢。\n"
         "类比：装修不会「直接开始刷墙」—— 量房、出图、水电、瓦工、验收，每一步做完都能被看见。\n"
         "停下来问一句：你们现在让 AI 写功能，中间有几道检查？")

# ============================================================ 04 详情：七道工序
s = blank(prs)
top = detail(s, 4, "详情 · 工序 01/03", "七道工序，各有各的产物",
             human_note="就像装修：量房 → 出图 → 水电 → 瓦工 → 验收，没人会「直接开始刷墙」。")

items = [("1", "规划", "PRD / 原型 / 任务"), ("2", "方案", "接口 / 表结构"), ("3", "编码", "源码 + 单测"),
         ("4", "门禁", "LGTM"), ("5", "评审", "评审意见"), ("6", "集成", "用例矩阵"), ("7", "验收", "标绿归档")]
rail(s, ML + 0.05, MR - 0.05, 2.55, items, size=11, sub_color=INK_SOFT)

text(s, ML, 3.74, CW, 0.32, [dict(t="每一道工序结束时，都会在硬盘上留下一个文件或一份输出 —— 而不是「AI 说它做完了」。",
                                  size=11.5, color=INK_SOFT)], check=False)

rect(s, ML, 4.42, W2, 1.44, fill=TEAL_L, radius=0.03)
text(s, ML + 0.26, 4.58, W2 - 0.5, 0.28, [dict(t="数字 1~7：会推进任务状态", size=10.5, bold=True, color=TEAL)],
     check=False)
text(s, ML + 0.26, 4.94, W2 - 0.5, 0.8, [dict(t="对应的 SOP 会自动加载，你看编号就知道自己走到哪了。",
                                             size=10, color=INK_SOFT, line=1.45)], check=True, tag="r1")

rect(s, ML + W2 + 0.32, 4.42, W2, 1.44, fill=ACCENT_L, radius=0.03)
text(s, ML + W2 + 0.58, 4.58, W2 - 0.5, 0.28, [dict(t="5 评审不改状态", size=10.5, bold=True, color=ACCENT)],
     check=False)
text(s, ML + W2 + 0.58, 4.94, W2 - 0.5, 0.8, [dict(t="机器判不了的（分层、命名、可读性）才交给人 —— 人只做判断，不做打卡。",
                                                   size=10, color=INK_SOFT, line=1.45)], check=True, tag="r2")
notes(s, "6 分钟。这是全场的地图，必须讲透。\n"
         "手势：从左往右划 1~7，再指下面两行说明「产物」和「谁签字」。\n"
         "别在这里展开每一步怎么做 —— 后面每个概念会拆。")

# ============================================================ 05 详情：场景 A
s = blank(prs)
top = detail(s, 5, "详情 · 工序 02/03", "最常用的一条路：你只需要发五次",
             human_note="像点菜：你只说想吃什么、忌口什么，厨师不会让你先教他热锅。")

rect(s, ML, top + 0.02, 6.45, 3.86, fill=CARD, line=LINE, line_w=0.75, radius=0.03)
rows = [("你", "1  想让运维在页面上看到每台交换机的在线状态，掉线 1 分钟变红。", ACCENT),
        ("AI", "三个白话问题：要发通知吗？最多多少台？掉线怎么算？", TEAL),
        ("你", "你推荐默认值，标暂定。", ACCENT),
        ("AI", "PRD、原型、用例矩阵已生成，任务已进看板。", TEAL)]
for i, (who, txt, color) in enumerate(rows):
    yy = top + 0.22 + i * 0.80
    text(s, ML + 0.28, yy, 0.5, 0.24, [dict(t=who, size=9.5, bold=True, color=color)], check=False)
    text(s, ML + 0.74, yy - 0.02, 5.4, 0.66, [dict(t=txt, size=11, color=INK, line=1.45)],
         check=True, tag=f"d{i}")

text(s, ML + 0.28, top + 3.32, 5.9, 0.28, [dict(t="之后照抄：2 → 3 → 4 → 7", size=10.5, bold=True,
                                               color=MUTED)], check=False)

text(s, 7.62, top + 0.02, 4.9, 0.3, [dict(t="每一轮，你只做一件事", size=12.5, bold=True, color=INK)],
     check=False)
checks = [("扫一眼 PRD 的业务规则", "写错现在改最便宜"),
          ("看它改了哪些文件", "别让它顺手重构"),
          ("亲眼看到 [LGTM]", "AI 转述的不算"),
          ("verify 之后点头放行", "唯一你拍板的时刻")]
for i, (a, b) in enumerate(checks):
    yy = top + 0.48 + i * 0.84
    text(s, 7.62, yy, 0.32, 0.26, [dict(t=str(i + 1), size=9.5, bold=True, color=ACCENT, font=MONO)], check=False)
    text(s, 7.96, yy - 0.02, 4.4, 0.28, [dict(t=a, size=11, bold=True, color=INK)], check=False)
    text(s, 7.96, yy + 0.26, 4.4, 0.26, [dict(t=b, size=9.5, color=MUTED)], check=False)
notes(s, "6 分钟。可以现场演示这一段，比讲十遍有用。\n"
         "重点演示「你推荐默认值」—— 让新人看到：不会答也能往下走，不会卡住。")

# ============================================================ 06 详情：旁路
s = blank(prs)
top = detail(s, 6, "详情 · 工序 03/03", "不一定要走全程：五条旁路随时插",
             human_note="像看病：小感冒去社区门诊（B2/B3），要做大手术才进三甲（全流程）。")

bypass = [("B1", "老项目", "先建地图，再动刀"), ("B2", "快修", "≤ 20 行，直接改"),
          ("B3", "报错", "贴日志，找根因"), ("B5", "收工", "压缩上下文落盘"), ("B6", "复活", "读名片回现场")]
bw = (CW - 4 * 0.22) / 5
for i, (code, name, desc) in enumerate(bypass):
    xx = ML + i * (bw + 0.22)
    rect(s, xx, top + 0.10, bw, 2.30, fill=CARD, line=LINE, line_w=0.75, radius=0.03)
    text(s, xx + 0.22, top + 0.28, bw - 0.4, 0.28, [dict(t=code, size=10.5, bold=True, color=ACCENT, font=MONO)],
         check=False)
    text(s, xx + 0.22, top + 0.62, bw - 0.4, 0.32, [dict(t=name, size=14, bold=True, color=INK)], check=False)
    text(s, xx + 0.22, top + 1.10, bw - 0.42, 1.0, [dict(t=desc, size=9.8, color=INK_SOFT, line=1.45)],
         check=True, tag=f"by{i}")

rect(s, ML, 5.06, CW, 0.92, fill=PAPER, radius=0.03)
text(s, ML + 0.28, 5.16, 1.4, 0.28, [dict(t="日常八成", size=10, bold=True, color=ACCENT)], check=False)
text(s, ML + 0.28, 5.44, CW - 0.6, 0.44, [
    dict(t="就是 B2 小改 + B3 排错。全流程是给「会影响别人」的改动准备的 —— 接口、表结构、跨服务。",
         size=11.5, color=INK, line=1.3)], check=True, tag="80")
notes(s, "4 分钟。这页决定新人愿不愿意用：先告诉他「有轻量通道」。\n"
         "明确规则：B 开头不推进任务状态，所以不会打乱看板。")

# ============================================================ 07 概念 02
s = blank(prs, DARK)
concept(s, 7, 2, "验收",
        [("「做完了」这三个字，", PAPER), ("不是证据", ACCENT)],
        "证据是能被别人复核的东西：一个能打开的文件、一段能重跑的日志、一个退出码。\n你不能复核的东西，只能叫「它说」。")
notes(s, "3 分钟。这是第二个概念，也是最反直觉的一个。\n"
         "问一句：上周 AI 跟你说过几次「已完成」，你验证过几次？")

# ============================================================ 08 详情：三道闸
s = blank(prs)
top = detail(s, 8, "详情 · 验收 01/02", "三道闸，各查一件事",
             human_note="像寄快递：打包要过机检（机器）、面单要你签字（人）、物流要有记录（机器）。")

gates = [("闸一", "机器查代码", "gate.py", ["有没有私装依赖", "有没有写死假数据", "有没有引公网资源", "改了表有没有回滚"]),
         ("闸二", "人查交付", "4 维清单", ["业务目标对不对", "文档同步了没有", "SQL 能不能回滚", "构建是不是绿的"]),
         ("闸三", "机器查证据", "board.py verify", ["任务真的在 ACTIVE", "声明的文件真的在", "测试真的跑过"])]
for i, (num, ttl, cmd, items) in enumerate(gates):
    xx = ML + i * (W3 + 0.28)
    rect(s, xx, top + 0.06, W3, 3.36, fill=CARD, line=LINE, line_w=0.75, radius=0.03)
    text(s, xx + 0.24, top + 0.22, 0.8, 0.26, [dict(t=num, size=9.5, bold=True, color=ACCENT, font=MONO)],
         check=False)
    text(s, xx + 0.24, top + 0.52, W3 - 0.46, 0.34, [dict(t=ttl, size=14, bold=True, color=INK)], check=False)
    text(s, xx + 0.24, top + 0.94, W3 - 0.46, 0.26, [dict(t=cmd, size=9.5, color=TEAL, font=MONO)], check=False)
    line_shape(s, xx + 0.24, top + 1.28, xx + W3 - 0.24, top + 1.28, LINE, 0.75)
    for j, it in enumerate(items):
        yy = top + 1.42 + j * 0.44
        text(s, xx + 0.24, yy, 0.2, 0.26, [dict(t="–", size=10, color=MUTED)], check=False)
        text(s, xx + 0.44, yy - 0.02, W3 - 0.7, 0.36, [dict(t=it, size=10, color=INK_SOFT)], check=False)
notes(s, "5 分钟。三种视角正好对上开发 / 评审人 / 管理者。\n"
         "强调：三道闸查的东西不重复 —— 机器查规则，人查意图，机器再查证据。")

# ============================================================ 09 详情：LGTM
s = blank(prs)
top = detail(s, 9, "详情 · 验收 02/02", "只有一种输出算通过：LGTM",
             human_note="像体检报告：「基本正常」不是结论，只有「合格」两个字才算。")

outcomes = [("[LGTM]", "全部门禁通过", "全部检查项通过，准予合入", GREEN, GREEN_L, "通过"),
            ("[BLOCKING]", "疑似保底 Mock / 假数据", "代码里写死了假数据，直接拦下", ACCENT, ACCENT_L, "不通过"),
            ("exit 2", "未收到任何待检文件", "没 git add，门禁根本没跑", MUTED, GREY_L, "不通过")]
for i, (code, cn, why, color, bgc, verdict) in enumerate(outcomes):
    yy = top + 0.10 + i * 1.22
    rect(s, ML, yy, CW, 1.04, fill=CARD, line=LINE, line_w=0.75, radius=0.03)
    rect(s, ML, yy, 0.055, 1.04, fill=color)
    rich(s, ML + 0.30, yy + 0.20, 8.2, 0.34, [(code, dict(size=11, bold=True, color=color, font=MONO)),
                                               ("  " + cn, dict(size=12, bold=True, color=INK))])
    text(s, ML + 0.30, yy + 0.62, 8.6, 0.3, [dict(t=why, size=10.5, color=INK_SOFT)], check=False)
    chip(s, MR - 1.30, yy + 0.32, 1.0, 0.40, verdict, fill=bgc, fg=color, size=9.5)

text(s, ML, 5.82, CW, 0.4, [dict(t="exit 2 是最常见的自欺：门禁没跑，不是门禁通过。", size=11.5, color=ACCENT,
                                 bold=True)], check=False)
notes(s, "4 分钟。这页最好现场演：故意写个 mock 字符串，跑一次门禁，让大家看红字长什么样。\n"
         "重点纠正「没报错 = 通过」这个直觉。")

# ============================================================ 10 概念 03
s = blank(prs, DARK)
concept(s, 10, 3, "契约",
        [("先定契约，", PAPER), ("再写代码", ACCENT)],
        "接口、字段、表结构不是「代码细节」，是别人也在用的承诺。\n契约一冻结，改它就要先改文档 —— 这不是形式，是让 AI 和你看到同一份事实。")
notes(s, "3 分钟。契约这个词对产品同事也友好：就是「说好的东西」。\n"
         "举反例：字段名从 userName 改成 user_name，前端还在用旧的 —— 这就是契约被单方面改了。")

# ============================================================ 11 详情：三类契约
s = blank(prs)
top = detail(s, 11, "详情 · 契约 01/02", "只有三类东西，值得停下来先定",
             human_note="像签合同：签之前怎么改都行，签之后再改，就得两边一起改。")

reds = [("接口字段", "路径、参数名、字段拼写", "前端照文档写、后端照代码改 = 联调才发现"),
        ("返回值结构", "层级、类型、错误码", "少一个 null 判断，线上就是白屏"),
        ("数据库字段", "列名、类型、索引、DDL", "没有回滚脚本，出事只能手工修数据")]
for i, (ttl, sub, cost) in enumerate(reds):
    xx = ML + i * (W3 + 0.28)
    rect(s, xx, top + 0.08, W3, 2.30, fill=CARD, line=LINE, line_w=0.75, radius=0.03)
    rect(s, xx, top + 0.08, W3, 0.055, fill=ACCENT)
    text(s, xx + 0.26, top + 0.34, W3 - 0.46, 0.34, [dict(t=ttl, size=14.5, bold=True, color=INK)], check=False)
    text(s, xx + 0.26, top + 0.78, W3 - 0.46, 0.3, [dict(t=sub, size=9.5, color=MUTED)], check=False)
    text(s, xx + 0.26, top + 1.22, W3 - 0.5, 0.9, [dict(t=cost, size=10.5, color=INK_SOFT, line=1.5)],
         check=True, tag=f"ct{i}")

# 顺序
text(s, ML, 4.94, 3.0, 0.28, [dict(t="顺序不能反", size=9.5, bold=True, color=MUTED)], check=False)
for i, a in enumerate(["改文档", "门禁", "改代码", "回归"]):
    xx = ML + i * 1.90
    rect(s, xx, 5.26, 1.62, 0.58, fill=TEAL_L, radius=0.08)
    text(s, xx, 5.26, 1.62, 0.58, [dict(t=a, size=11, bold=True, color=TEAL, align=PP_ALIGN.CENTER)],
         anchor=MSO_ANCHOR.MIDDLE, check=False)
    if i < 3:
        text(s, xx + 1.62, 5.34, 0.28, 0.4, [dict(t="→", size=12, color=TEAL)], check=False)
notes(s, "4 分钟。三类契约要念清楚，这是「什么时候必须停下来」的唯一判据。\n"
         "可以问 QA 同事：你们最怕哪种？多半是返回值结构。")

# ============================================================ 12 详情：漂移
s = blank(prs)
top = detail(s, 12, "详情 · 契约 02/02", "文档和代码一旦不同步，就会打架",
             human_note="像两本地图：一本改了路、一本没改，司机按旧地图开，早晚撞墙。")

rect(s, ML, top + 0.10, W2, 2.20, fill=CARD, line=LINE, line_w=0.75, radius=0.03)
text(s, ML + 0.28, top + 0.26, W2 - 0.5, 0.28, [dict(t="设计文档里写的", size=10, bold=True, color=TEAL)], check=False)
for i, t in enumerate(["user_name   string", "GET /api/v1/topology", "返回 { code, data }"]):
    text(s, ML + 0.28, top + 0.68 + i * 0.44, W2 - 0.5, 0.3,
         [dict(t=t, size=10.5, color=INK_SOFT, font=MONO)], check=False)

text(s, ML + W2 + 0.06, top + 1.02, 0.2, 0.4, [dict(t="≠", size=18, bold=True, color=ACCENT)], check=False)

rect(s, ML + W2 + 0.32, top + 0.10, W2, 2.20, fill=CARD, line=LINE, line_w=0.75, radius=0.03)
text(s, ML + W2 + 0.60, top + 0.26, W2 - 0.5, 0.28, [dict(t="代码里实际的", size=10, bold=True, color=ACCENT)], check=False)
for i, t in enumerate(["username   varchar", "GET /api/topology", "返回 { status, result }"]):
    text(s, ML + W2 + 0.60, top + 0.68 + i * 0.44, W2 - 0.5, 0.3,
         [dict(t=t, size=10.5, color=INK_SOFT, font=MONO)], check=False)

rect(s, ML, 5.00, CW, 0.94, fill=PAPER, radius=0.03)
text(s, ML + 0.28, 5.12, CW - 0.6, 0.72, [
    dict(t="后果不是「现在报错」，是「下次 AI 照旧文档重新写一遍」，你修一次它拆一次。",
         size=11.5, color=INK, line=1.45),
    dict(t="修法只有一条：先改文档 → 再改代码 → 再回归。", size=11.5, bold=True, color=ACCENT, line=1.45),
], check=True, tag="drift")
notes(s, "4 分钟。左右对照很直观，讲完停两秒让大家看差异。\n"
         "强调时间差：不同步的代价不是立刻爆，是下次开工时爆。")

# ============================================================ 13 概念 04
s = blank(prs, DARK)
concept(s, 13, 4, "状态",
        [("记忆在硬盘上，", PAPER), ("不在聊天窗口里", ACCENT)],
        "所以关掉窗口、换台电脑、第二天再来，甚至换个人接手，都能原地复活。\n上下文会衰减，硬盘不会。")
notes(s, "3 分钟。这是所有长期用 AI 的人最痛的点：聊到后面它变傻。\n"
         "一句话点透：不是模型记性差，是你把记忆放在了最不可靠的地方 —— 对话流。")

# ============================================================ 14 详情：三层记忆
s = blank(prs)
top = detail(s, 14, "详情 · 状态 01/02", "三层记忆，AI 每轮只读最上面那层",
             human_note="像工位：桌上便签（名片）、部门组织图（地图）、项目看板（状态）—— 找东西不用翻整个档案室。")

layers = [("第一层", "名片", "27 行：我是谁、干到哪、下一步", "每轮开工都读", ACCENT),
          ("第二层", "模块地图", "哪个模块、哪个路由、哪张表", "问到具体模块才读", TEAL),
          ("第三层", "看板与黑匣子", "任务状态 + 踩坑记录", "改状态时才读", INK_SOFT)]
for i, (lv, name, content, when, color) in enumerate(layers):
    yy = top + 0.10 + i * 1.24
    rect(s, ML, yy, CW, 1.06, fill=CARD, line=LINE, line_w=0.75, radius=0.03)
    rect(s, ML, yy, 0.055, 1.06, fill=color)
    text(s, ML + 0.28, yy + 0.16, 0.9, 0.26, [dict(t=lv, size=9, bold=True, color=color)], check=False)
    text(s, ML + 1.10, yy + 0.14, 1.6, 0.34, [dict(t=name, size=13, bold=True, color=INK)], check=False)
    text(s, ML + 2.86, yy + 0.20, 4.4, 0.3, [dict(t=content, size=10.5, color=INK_SOFT)], check=False)
    text(s, ML + 0.28, yy + 0.66, 3.2, 0.26, [dict(t=when, size=9, color=MUTED)], check=False)

text(s, ML, 5.90, CW, 0.3, [dict(t="这就是为什么它不用「把整个仓库读一遍」—— 那是 7 倍的开销，也是它变傻的原因。",
                                 size=10.5, color=MUTED)], check=False)
notes(s, "4 分钟。用「便签 / 组织图 / 看板」的类比讲，产品同事也能秒懂。\n"
         "重点：分层加载不是为了省钱，是为了让它在每一轮只被最相关的信息占据注意力。")

# ============================================================ 15 详情：B5/B6
s = blank(prs)
top = detail(s, 15, "详情 · 状态 02/02", "你只需要两个动作：B5 和 B6",
             human_note="像交接班：交班本写清楚，下一班不用把你从家里叫回来。")

rect(s, ML, top + 0.20, W2, 2.60, fill=CARD, line=LINE, line_w=0.75, radius=0.03)
text(s, ML + 0.30, top + 0.40, 0.8, 0.3, [dict(t="B5", size=11, bold=True, color=ACCENT, font=MONO)], check=False)
text(s, ML + 0.30, top + 0.76, W2 - 0.6, 0.34, [dict(t="收工：压缩成一张交接卡", size=14, bold=True, color=INK)],
     check=False)
for i, t in enumerate(["今天做了什么、卡在哪", "哪些规则改了", "下次从哪一步接着走"]):
    text(s, ML + 0.30, top + 1.32 + i * 0.40, W2 - 0.6, 0.3, [dict(t="· " + t, size=10.5, color=INK_SOFT)],
         check=False)

text(s, MR - 2.95, top + 1.25, 0.6, 0.4, [dict(t="→", size=20, bold=True, color=MUTED)], check=False)

rect(s, ML + W2 + 0.32, top + 0.20, W2, 2.60, fill=TEAL_L, radius=0.03)
text(s, ML + W2 + 0.62, top + 0.40, 0.8, 0.3, [dict(t="B6", size=11, bold=True, color=TEAL, font=MONO)], check=False)
text(s, ML + W2 + 0.62, top + 0.76, W2 - 0.6, 0.34, [dict(t="开工：读名片回到现场", size=14, bold=True, color=TEAL)],
     check=False)
for i, t in enumerate(["3 秒进入状态", "不用重讲一遍背景", "换个人接手也接得上"]):
    text(s, ML + W2 + 0.62, top + 1.32 + i * 0.40, W2 - 0.6, 0.3, [dict(t="· " + t, size=10.5, color=INK_SOFT)],
         check=False)

text(s, ML, 5.30, CW, 0.3, [dict(t="判据：当你发现自己在重复解释同一件事，就该 B5 了 —— 别硬聊，越聊越贵。",
                                 size=11, color=INK)], check=False)
notes(s, "4 分钟。让所有人现在就试一次 B5 归档，感受「关窗口不丢事」。\n"
         "这是这套东西和「聊天式 AI」最大的差异点，值得多花一分钟。")

# ============================================================ 16 概念 05
s = blank(prs, DARK)
concept(s, 16, 5, "成本",
        [("流程是有成本的，", PAPER), ("所以要看值不值", ACCENT)],
        "不是「有流程就比没流程好」，而是「这一次的返工成本，值不值得走流程」。\n用错方向比不用更浪费：小改动走全程，人就会开始偷偷绕开流程。")
notes(s, "3 分钟。这个概念让方案显得可信 —— 承认成本，才不是宗教。\n"
         "一句话：流程不能消灭返工，只能把返工提前到便宜的时候。")

# ============================================================ 17 详情：三档裁剪
s = blank(prs)
top = detail(s, 17, "详情 · 成本 01/02", "改多大，就走多远",
             human_note="像出行：下楼买酱油骑车，跨省出差坐飞机 —— 没人会为买瓶酱油去机场。")

rows = [["改动规模", "走哪条", "典型例子", "耗时"],
        ["1 个文件 / ≤ 20 行", "B2 快修", "改文案、换颜色、调间距", "5 分钟"],
        ["单模块，不动表结构", "1 → 3 → 4 → 7", "列表加筛选、加一条校验", "半天"],
        ["动接口 / 表 / 跨服务", "全流程 1 → 9", "新增权限表、改返回结构", "1 – 3 天"]]
table(s, ML, 2.10, CW, rows, [0.27, 0.22, 0.37, 0.14], row_h=0.74, header_h=0.44, font=12.5, head_font=10.5)

rect(s, ML, 5.16, CW, 0.88, fill=ACCENT_L, radius=0.03)
text(s, ML + 0.28, 5.26, CW - 0.6, 0.68, [
    dict(t="90% 的日常落在前两行。第三行的判据只有一条：会不会影响别人（前端、别的服务、线上数据）。",
         size=11.5, color=INK, line=1.4)], check=True, tag="tier")
notes(s, "4 分钟。现场举手：最近一周的改动，哪一行最多？\n"
         "这页是「愿意用」的关键 —— 先降低门槛，再谈规范。")

# ============================================================ 18 详情：别用
s = blank(prs)
top = detail(s, 18, "详情 · 成本 02/02", "有些时候，别用这套流程",
             human_note="像急救：先止血，别先填病历。等活下来了，再补记录。")

cases = [("一次性脚本", "跑完就扔，没有下一个人维护"), ("还在探索的原型", "需求自己都没想清，先跑起来看"),
         ("纯样式微调", "改一个 CSS 值，走流程纯属自虐"), ("只为演示的 demo", "明天就要演，先出效果")]
for i, (a, b) in enumerate(cases):
    xx = ML + (i % 2) * (W2 + 0.32)
    yy = top + 0.10 + (i // 2) * 1.40
    rect(s, xx, yy, W2, 1.14, fill=PAPER, radius=0.03)
    text(s, xx + 0.28, yy + 0.18, 5.0, 0.3, [dict(t=a, size=13, bold=True, color=INK)], check=False)
    text(s, xx + 0.28, yy + 0.56, 5.0, 0.3, [dict(t=b, size=10, color=MUTED)], check=False)

rect(s, ML, 5.16, CW, 0.90, fill=TEAL_L, radius=0.03)
text(s, ML + 0.28, 5.26, CW - 0.6, 0.70, [
    dict(t="流程的目的是减少返工，不是增加仪式。当流程成本 > 返工成本，就用 B2，或者干脆不用。",
         size=11.5, bold=True, color=TEAL, line=1.4)], check=True, tag="bound")
notes(s, "3 分钟。这页是可信度的来源，别删。\n"
         "可以自嘲：我自己写一次性脚本，也从来不跑流程。")

# ============================================================ 19 概念 06
s = blank(prs, DARK)
concept(s, 19, 6, "包工头",
        [("你是包工头，", PAPER), ("不是许愿池", ACCENT)],
        "你负责发工序、验收、拍板；它负责干活、出证据、别自作主张。\n把它当工人，你会得到可交付的结果；把它当许愿池，你只会得到「看起来像完成了」。")
notes(s, "3 分钟。最后一个概念，讲的是「人怎么站位」。\n"
         "对产品/测试同事尤其重要：他们不需要写代码，但需要会说这四句话。")

# ============================================================ 20 详情：四句话
s = blank(prs)
top = detail(s, 20, "详情 · 包工头 01/02", "把「帮我写」换掉，四句话就够",
             human_note="像跟装修队说话：先出图、只改这面墙、为什么漏水、验收标准是什么。")

quotes = [("先别写代码，把方案和影响面说清楚。", "省一次返工：先对齐再动手"),
          ("只改这个文件，其他一律不动。", "防顺手重构：改动面越小越安全"),
          ("根因是什么？用白话讲，再给最小修法。", "防瞎试：讲不清根因就是在猜"),
          ("你说做完了 —— 证据是什么？", "防自我认证：文件、门禁、verify")]
for i, (q, why) in enumerate(quotes):
    xx = ML + (i % 2) * (W2 + 0.32)
    yy = top + 0.08 + (i // 2) * 1.86
    rect(s, xx, yy, W2, 1.64, fill=CARD, line=LINE, line_w=0.75, radius=0.03)
    text(s, xx + 0.24, yy + 0.12, 0.4, 0.4, [dict(t="“", size=24, bold=True, color=ACCENT)], check=False)
    text(s, xx + 0.58, yy + 0.20, W2 - 0.86, 0.62, [dict(t=q, size=13, bold=True, color=INK, line=1.45)],
         check=True, tag=f"q{i}")
    text(s, xx + 0.58, yy + 1.14, W2 - 0.86, 0.34, [dict(t=why, size=9.5, color=MUTED)], check=False)
notes(s, "4 分钟。这页是给不写代码的同事用的 —— 他们用这四句话，就能管住 AI。\n"
         "建议现场让大家挑一句抄进便签。")

# ============================================================ 21 详情：五个不许
s = blank(prs, DARK)
top = detail(s, 21, "详情 · 包工头 02/02", "五条红线，做了等于没走流程", dark=True,
             human_note="像工地安全帽：不戴不罚你，但出事的一定是你。")

forbids = [("不许手改状态看板", "锚点一坏，verify / complete 全失效"),
           ("不许跳过 verify 直接 complete", "这是防你跳过验收的保护"),
           ("不许用假数据让页面「看起来能跑」", "唯一零容忍，会把问题推迟到上线"),
           ("不许私自装新依赖", "要装可以，但由人拍板"),
           ("不许改了字段不更新文档", "下次 AI 照旧文档写，你和它打架")]
for i, (ttl, why) in enumerate(forbids):
    yy = top + 0.04 + i * 0.84
    if i:
        line_shape(s, ML, yy - 0.10, MR, yy - 0.10, RGBColor(0x2E, 0x34, 0x37), 0.75)
    text(s, ML, yy + 0.02, 0.4, 0.3, [dict(t=f"0{i+1}", size=9.5, color=ACCENT, font=MONO, bold=True)],
         check=False)
    text(s, ML + 0.50, yy - 0.04, 7.4, 0.36, [dict(t=ttl, size=14, bold=True, color=PAPER)], check=True,
         tag=f"fb{i}")
    text(s, 8.55, yy + 0.02, 4.0, 0.4, [dict(t=why, size=9.5, color=RGBColor(0x9A, 0x9E, 0xA2), line=1.35)],
         check=True, tag=f"fbw{i}")
notes(s, "3 分钟。大字念完即可，别解释太多。\n"
         "建议打印成 A4 贴工位。")

# ============================================================ 22 详情：一天
s = blank(prs)
top = detail(s, 22, "详情 · 落地 01/02", "四个动作，构成一个工作日",
             human_note="像打卡：上班开机、干活、卡住想清楚、收工存档 —— 不用记 16 篇 SOP。")

rows = [["时机", "你说", "它做什么", "为什么值"],
        ["早上开工", "B6", "读名片，恢复昨天现场", "3 秒进状态"],
        ["干活", "3 / B3 / B2", "编码 / 排错 / 小修", "按改动大小选"],
        ["卡住想不清", "2", "先出接口和影响面", "比边写边想省一半"],
        ["收工 / 换话题", "B5", "压缩上下文，写交接卡", "明天还能接上"]]
table(s, ML, 2.06, CW, rows, [0.19, 0.20, 0.36, 0.25], row_h=0.72, header_h=0.42, font=11, head_font=10)

text(s, ML, 5.48, CW, 0.5, [dict(t="建议：把 B5 / B6 设成输入法快捷短语，成本几乎为零。", size=10.5, color=MUTED)],
     check=False)
notes(s, "3 分钟。这页回答「怎么坚持下来」：把流程变成四个日常动作，而不是七步仪式。")

# ============================================================ 23 详情：四周
s = blank(prs)
top = detail(s, 23, "详情 · 落地 02/02", "四周，从一条旁路开始",
             human_note="像健身：第一周只练一个动作，别一上来就上大重量 —— 上大重量的人第三周就不来了。")

weeks = [("第 1 周", "只用 B2 和 B3", ["零学习成本", "小改直接说 B2", "报错贴完整日志"]),
         ("第 2~3 周", "一个需求走 1→3→4→7", ["跳过 2 和 6", "重点是看见 LGTM", "卡住就问，别硬走"]),
         ("第 2 月", "加 [2] 契约冻结", ["接口先定死", "集成跑真实数据"]),
         ("第 3 月", "B1 老项目 + 复盘卡", ["先建地图再动刀", "坑写成复盘卡"])]
for i, (wk, ttl, points) in enumerate(weeks):
    xx = ML + i * 2.98
    rect(s, xx, 2.16, 2.76, 0.04, fill=ACCENT)
    text(s, xx, 1.90, 2.7, 0.26, [dict(t=wk, size=9.5, bold=True, color=ACCENT)], check=False)
    text(s, xx, 2.34, 2.7, 0.62, [dict(t=ttl, size=12.5, bold=True, color=INK, line=1.35)],
         check=True, tag=f"wk{i}")
    for j, pt in enumerate(points):
        text(s, xx, 3.10 + j * 0.50, 2.66, 0.46, [dict(t="· " + pt, size=9.5, color=INK_SOFT, line=1.35)],
             check=True, tag=f"wkp{i}{j}")

rect(s, ML, 4.98, CW, 0.92, fill=CARD, line=LINE, line_w=0.75, radius=0.03)
text(s, ML + 0.28, 5.08, CW - 0.6, 0.28, [dict(t="配套材料（有问题先看这四个）", size=9, bold=True, color=MUTED)], check=False)
text(s, ML + 0.28, 5.38, CW - 0.6, 0.34, [
    dict(t="README  ·  BEGINNER_HANDBOOK  ·  docs/playbooks/demo-scripts.md  ·  workflow_guide.html",
         size=10, color=INK_SOFT, font=MONO)], check=False)
notes(s, "3 分钟。落到「下周做什么」，不要停在「大家要重视」。\n"
         "建议指定一个人当工作流值班人，第一周专答 B2/B3。")

# ============================================================ 24 速查卡
s = blank(prs)
top = detail(s, 24, "带走 · 一页纸", "贴显示器边上",
             human_note="记不住 16 篇 SOP 没关系，记住这张卡就够开工。")

quad = [("日常三连", ["开工   B6", "干活   3 / B3 / B2", "收工   B5"], TEAL, FONT),
        ("主链", ["1 → 2 → 3 → 4 → 5 → 6 → 7", "7 是唯一能标绿的出口"], TEAL, MONO),
        ("三条硬命令", ["board.py list", "board.py verify \"Task-XXX\"", "board.py complete --accept"], INK, MONO),
        ("五个不许", ["不改状态看板", "不跳过 verify", "不写死假数据", "不私装依赖", "不改字段不改文档"], ACCENT, FONT)]
for i, (ttl, lines, color, font) in enumerate(quad):
    xx = ML + (i % 2) * (W2 + 0.32)
    yy = 1.90 + (i // 2) * 2.02
    rect(s, xx, yy, W2, 1.84, fill=CARD, line=LINE, line_w=0.75, radius=0.03)
    rect(s, xx, yy, 0.05, 1.84, fill=color)
    text(s, xx + 0.28, yy + 0.16, 4.8, 0.28, [dict(t=ttl, size=10.5, bold=True, color=color)],
         check=False)
    for j, ln in enumerate(lines):
        text(s, xx + 0.28, yy + 0.54 + j * 0.38, 4.9, 0.32,
             [dict(t=ln, size=9.5 if font == MONO else 10.5, color=INK, font=font, line=1.25)], check=False)
footer(s, 24, TOTAL)
notes(s, "1 分钟。这页单独导成 A4 发群里。")

# ============================================================ 25 结束
s = blank(prs, DARK)
text(s, ML, 1.00, 7.4, 0.3, [dict(t="接下来 24 小时", size=10, color=RGBColor(0x8C, 0x91, 0x96))],
     check=False)
text(s, ML, 1.42, 7.8, 1.5, [
    dict(t="今天只做一件事：", size=27, bold=True, color=PAPER, line=1.4),
    dict(t="先把它当包工头，而不是许愿池。", size=27, bold=True, color=ACCENT, line=1.4)], check=False)

acts = [("01", "明天开工第一句，发 B6", "看它能不能 3 秒接上昨天的现场"),
        ("02", "找一个 ≤ 20 行的改动，用 B2 走一遍", "感受「改完就完」是什么效率"),
        ("03", "下次它说「做完了」，问它证据", "文件 / 门禁 / verify 三样")]
for i, (num, a, b) in enumerate(acts):
    yy = 3.20 + i * 0.90
    text(s, ML, yy + 0.04, 0.5, 0.3, [dict(t=num, size=9.5, color=ACCENT, font=MONO, bold=True)], check=False)
    text(s, ML + 0.56, yy, 7.2, 0.32, [dict(t=a, size=13.5, bold=True, color=PAPER)], check=False)
    text(s, ML + 0.56, yy + 0.32, 7.2, 0.28, [dict(t=b, size=9.5, color=RGBColor(0x9A, 0x9E, 0xA2))], check=False)

rect(s, 8.62, 1.50, 3.96, 4.10, fill=DARK_2, radius=0.03)
text(s, 8.92, 1.70, 3.4, 0.3, [dict(t="有问题先看这四个", size=10.5, bold=True, color=PAPER)], check=False)
res = ["README.md", "BEGINNER_HANDBOOK.md", "docs/playbooks/", "workflow_guide.html"]
for i, r in enumerate(res):
    text(s, 8.92, 2.16 + i * 0.60, 3.4, 0.3, [dict(t=r, size=9, color=RGBColor(0xC9, 0xC5, 0xBA), font=MONO)],
         check=False)
    if i < 3:
        line_shape(s, 8.92, 2.54 + i * 0.60, 12.28, 2.54 + i * 0.60, RGBColor(0x33, 0x39, 0x3C), 0.75)
text(s, 8.92, 4.62, 3.4, 0.8, [dict(t="16 篇 SOP 在 .ai/sop/\n日常只读命中的那一篇", size=9,
                                   color=RGBColor(0x8C, 0x91, 0x96), line=1.6)], check=False)

text(s, ML, 6.36, 7.6, 0.3, [dict(t="谢谢 · 现在可以开始问「我们组怎么用」了", size=10.5,
                                  color=RGBColor(0x8C, 0x91, 0x96))], check=False)
footer(s, 25, TOTAL, dark=True)
notes(s, "最后 6 分钟：Q&A。三个动作任选一个承诺即可，不要要求全做。")

prs.save(OUT)
warn = L.report_warnings()
print(f"saved: {OUT}")
print(f"slides: {len(prs.slides._sldIdLst)}")
print("\n".join(warn) if warn else "no overflow warnings")
