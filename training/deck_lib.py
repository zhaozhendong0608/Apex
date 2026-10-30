# -*- coding: utf-8 -*-
"""排版工具库：统一字号、间距、卡片与表格样式，并做文本溢出自检。"""

import os
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE
from pptx.oxml.ns import qn, nsdecls
from pptx.oxml import parse_xml
from PIL import ImageFont

# ---------------------------------------------------------------- 设计变量
FONT = "PingFang SC"
MONO = "Menlo"

BG       = RGBColor(0xF7, 0xF5, 0xF0)
INK      = RGBColor(0x1A, 0x1C, 0x1E)
INK_SOFT = RGBColor(0x3D, 0x41, 0x48)
MUTED    = RGBColor(0x7A, 0x7E, 0x85)
LINE     = RGBColor(0xD8, 0xD4, 0xCA)
LINE_DK  = RGBColor(0x3A, 0x40, 0x44)
ACCENT   = RGBColor(0xC2, 0x41, 0x0C)
ACCENT_L = RGBColor(0xF6, 0xE3, 0xD8)
TEAL     = RGBColor(0x14, 0x53, 0x5B)
TEAL_L   = RGBColor(0xDD, 0xE9, 0xE9)
CARD     = RGBColor(0xFF, 0xFF, 0xFF)
DARK     = RGBColor(0x14, 0x18, 0x1A)
DARK_2   = RGBColor(0x22, 0x27, 0x2A)
PAPER    = RGBColor(0xF2, 0xEF, 0xE8)
WHITE    = RGBColor(0xFF, 0xFF, 0xFF)

SW, SH = 13.333, 7.5
ML, MR = 0.75, 12.58

# 文本自检收集器
_WARN = []


def new_deck():
    prs = Presentation()
    prs.slide_width = Inches(SW)
    prs.slide_height = Inches(SH)
    return prs


def blank(prs, color=BG):
    s = prs.slides.add_slide(prs.slide_layouts[6])
    f = s.background.fill
    f.solid()
    f.fore_color.rgb = color
    return s


def _noline(shp):
    shp.line.fill.background()
    shp.shadow.inherit = False
    return shp


def rect(slide, x, y, w, h, fill=None, line=None, line_w=1.0, radius=None):
    shape_type = MSO_SHAPE.ROUNDED_RECTANGLE if radius else MSO_SHAPE.RECTANGLE
    shp = slide.shapes.add_shape(shape_type, Inches(x), Inches(y), Inches(w), Inches(h))
    if radius:
        shp.adjustments[0] = radius
    if fill is None:
        shp.fill.background()
    else:
        shp.fill.solid()
        shp.fill.fore_color.rgb = fill
    if line is None:
        shp.line.fill.background()
    else:
        shp.line.color.rgb = line
        shp.line.width = Pt(line_w)
    shp.shadow.inherit = False
    shp.text_frame.word_wrap = True
    return shp


def line_shape(slide, x1, y1, x2, y2, color=LINE, width=1.0):
    from pptx.enum.shapes import MSO_CONNECTOR
    ln = slide.shapes.add_connector(MSO_CONNECTOR.STRAIGHT, Inches(x1), Inches(y1),
                                    Inches(x2), Inches(y2))
    ln.line.color.rgb = color
    ln.line.width = Pt(width)
    return ln


def text(slide, x, y, w, h, paras, anchor=MSO_ANCHOR.TOP,
         margins=(0, 0, 0, 0), wrap=True, autofit=False, check=True, tag=""):
    """paras: list of dict(t=, size=, bold=, color=, font=, before=, after=, line=, align=)"""
    tb = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    tf = tb.text_frame
    tf.word_wrap = wrap
    tf.vertical_anchor = anchor
    tf.margin_left, tf.margin_right, tf.margin_top, tf.margin_bottom = [Inches(m) for m in margins]
    if not autofit:
        tf.auto_size = None

    for i, p in enumerate(paras):
        para = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        para.alignment = p.get("align", PP_ALIGN.LEFT)
        if p.get("before") is not None:
            para.space_before = Pt(p["before"])
        if p.get("after") is not None:
            para.space_after = Pt(p["after"])
        if p.get("line"):
            para.line_spacing = p["line"]
        run = para.add_run()
        run.text = p["t"]
        f = run.font
        f.name = p.get("font", FONT)
        f.size = Pt(p.get("size", 14))
        f.bold = p.get("bold", False)
        f.italic = p.get("italic", False)
        f.color.rgb = p.get("color", INK)

    if check:
        _check_fit(slide, paras, w, h, tag)
    return tb


def _measure(txt, size, bold, font=FONT):
    try:
        import fontpath
        fp = fontpath.load_mono(int(size * 4)) if font == MONO else fontpath.load(int(size * 4), bold)
        return fp.getlength(txt) / 4.0
    except Exception:
        return len(txt) * size * 0.6


def _wrap_lines(txt, size, bold, width_pt, font=FONT):
    """按可用宽度折算行数（CJK 逐字断行，ASCII 按词）。"""
    if not txt:
        return 1
    lines, cur = 0, ""
    tokens = []
    buf = ""
    for ch in txt:
        if ord(ch) < 128 and ch not in "，。、；：":
            buf += ch
        else:
            if buf:
                tokens.append(buf)
                buf = ""
            tokens.append(ch)
    if buf:
        tokens.append(buf)
    for tok in tokens:
        cand = cur + tok
        if _measure(cand, size, bold, font) <= width_pt or not cur:
            cur = cand
        else:
            lines += 1
            cur = tok
    return lines + 1


def _check_fit(slide, paras, w_in, h_in, tag):
    width_pt = (w_in - 0.06) * 72
    need = 0.0
    for p in paras:
        sz = p.get("size", 14)
        n = _wrap_lines(p.get("t", ""), sz, p.get("bold", False), width_pt, p.get("font", FONT))
        lh = sz * (p.get("line") or 1.25) * 1.2
        need += n * lh + (p.get("before") or 0) + (p.get("after") or 0)
    need_in = need / 72.0
    if need_in > h_in + 0.04:
        _WARN.append(f"[溢出] {tag or paras[0].get('t','')[:20]!r} 需要 {need_in:.2f}in / 可用 {h_in:.2f}in")


def report_warnings():
    return _WARN


# ---------------------------------------------------------------- 组件
def header(slide, num, kicker, title, sub=None, dark=False, title_size=29):
    fg = PAPER if dark else INK
    soft = RGBColor(0x9A, 0x9E, 0xA2) if dark else MUTED
    ln = LINE_DK if dark else LINE

    text(slide, ML, 0.42, 0.5, 0.26,
         [dict(t=num, size=10.5, color=ACCENT, font=MONO, bold=True)], check=False)
    text(slide, ML + 0.52, 0.42, 8.0, 0.26,
         [dict(t=kicker, size=10.5, color=soft, bold=False)], check=False)
    text(slide, ML, 0.78, MR - ML, 0.62,
         [dict(t=title, size=title_size, bold=True, color=fg, line=1.1)], check=False)
    if sub:
        text(slide, ML, 1.44, MR - ML - 0.2, 0.34,
             [dict(t=sub, size=13, color=soft, line=1.2)], check=False)
        line_shape(slide, ML, 1.92, MR, 1.92, ln, 0.9)
        return 2.14
    line_shape(slide, ML, 1.55, MR, 1.55, ln, 0.9)
    return 1.80


def footer(slide, idx, total, label="Apex 工作流 · 内部培训", dark=False):
    c = RGBColor(0x6E, 0x72, 0x76) if dark else MUTED
    text(slide, ML, 7.02, 6.0, 0.24, [dict(t=label, size=8.5, color=c)], check=False)
    text(slide, MR - 1.2, 7.02, 1.2, 0.24,
         [dict(t=f"{idx:02d} / {total:02d}", size=8.5, color=c, font=MONO, align=PP_ALIGN.RIGHT)],
         check=False)


def notes(slide, txt):
    slide.notes_slide.notes_text_frame.text = txt


def table(slide, x, y, w, rows, col_ratios, row_h=0.46, header_h=0.40,
          font=11.5, head_font=10.5, head_bg=INK, head_fg=WHITE,
          body_bg=CARD, zebra=None, first_bold=True, align=None, radius=None):
    nrow, ncol = len(rows), len(rows[0])
    total_h = header_h + row_h * (nrow - 1)
    gshape = slide.shapes.add_table(nrow, ncol, Inches(x), Inches(y), Inches(w), Inches(total_h))
    tbl = gshape.table
    tbl.first_row = False
    tbl.horz_banding = False
    tbl.vert_banding = False

    # 去掉默认样式表格的边框与填充干扰
    for i, r in enumerate(col_ratios):
        tbl.columns[i].width = Inches(w * r)
    tbl.rows[0].height = Inches(header_h)
    for i in range(1, nrow):
        tbl.rows[i].height = Inches(row_h)

    for ri, row in enumerate(rows):
        for ci, val in enumerate(row):
            cell = tbl.cell(ri, ci)
            cell.margin_left = Inches(0.14)
            cell.margin_right = Inches(0.10)
            cell.margin_top = Inches(0.05)
            cell.margin_bottom = Inches(0.05)
            cell.vertical_anchor = MSO_ANCHOR.MIDDLE
            bg = head_bg if ri == 0 else body_bg
            if ri > 0 and zebra and ri % 2 == 0:
                bg = zebra
            cell.fill.solid()
            cell.fill.fore_color.rgb = bg
            tf = cell.text_frame
            tf.word_wrap = True
            p = tf.paragraphs[0]
            p.alignment = (align[ci] if align else PP_ALIGN.LEFT)
            run = p.add_run()
            run.text = str(val)
            f = run.font
            f.name = FONT
            f.size = Pt(head_font if ri == 0 else font)
            f.bold = (ri == 0) or (first_bold and ci == 0)
            f.color.rgb = head_fg if ri == 0 else INK
            if ri > 0:
                _cell_border(cell, "B", "E2DED4", 9525)
    return tbl


def _cell_border(cell, edge, color_hex, width_emu):
    tag = {"L": "a:lnL", "R": "a:lnR", "T": "a:lnT", "B": "a:lnB"}[edge]
    xml = (f'<{tag} {nsdecls("a")} w="{width_emu}" cap="flat" cmpd="sng" algn="ctr">'
           f'<a:solidFill><a:srgbClr val="{color_hex}"/></a:solidFill>'
           f'<a:prstDash val="solid"/></{tag}>')
    ln = parse_xml(xml)
    tcPr = cell._tc.get_or_add_tcPr()
    for e in tcPr.findall(qn(tag)):
        tcPr.remove(e)
    order = ["a:lnL", "a:lnR", "a:lnT", "a:lnB"]
    idx = 0
    for i, child in enumerate(list(tcPr)):
        if child.tag in [qn(t) for t in order]:
            idx = i + 1
    tcPr.insert(idx, ln)


def code_block(slide, x, y, w, h, lines, size=11.5, bg=DARK_2, label=None):
    rect(slide, x, y, w, h, fill=bg, radius=0.05)
    if label:
        text(slide, x + 0.22, y + 0.14, w - 0.44, 0.22,
             [dict(t=label, size=8.5, color=RGBColor(0x8A, 0x8F, 0x94), font=MONO)], check=False)
    paras = [dict(t=l, size=size, color=RGBColor(0xE8, 0xE4, 0xDA), font=MONO, line=1.5)
             for l in lines]
    text(slide, x + 0.22, y + (0.46 if label else 0.20), w - 0.44, h - (0.6 if label else 0.3),
         paras, check=True, tag="code")


def chip(slide, x, y, w, h, label, fill=ACCENT_L, fg=ACCENT, size=10, bold=True, radius=0.5):
    rect(slide, x, y, w, h, fill=fill, radius=radius)
    text(slide, x, y, w, h, [dict(t=label, size=size, color=fg, bold=bold, align=PP_ALIGN.CENTER)],
         anchor=MSO_ANCHOR.MIDDLE, check=False)


def numbered_card(slide, x, y, w, h, num, title, body, accent=TEAL, bg=CARD,
                  num_color=None, title_size=15, body_size=11.5, body_color=INK_SOFT):
    rect(slide, x, y, w, h, fill=bg, line=LINE, line_w=0.75, radius=0.04)
    # 左侧色条
    rect(slide, x, y + 0.001, 0.045, h - 0.002, fill=accent)
    text(slide, x + 0.26, y + 0.20, 0.6, 0.3,
         [dict(t=num, size=10.5, color=num_color or accent, font=MONO, bold=True)], check=False)
    text(slide, x + 0.26, y + 0.50, w - 0.46, 0.34,
         [dict(t=title, size=title_size, bold=True, color=INK, line=1.15)], check=False)
    text(slide, x + 0.26, y + 0.94, w - 0.46, h - 1.06,
         [dict(t=body, size=body_size, color=body_color, line=1.5)], check=True, tag=title)


def rail(slide, x0, x1, y, items, accent=TEAL, label_color=INK, sub_color=MUTED, size=11):
    """编号方框 + 横轨 + 下方标签的三段式流程图。"""
    n = len(items)
    gap = (x1 - x0) / n
    cy = y
    line_shape(slide, x0 + gap / 2, cy, x1 - gap / 2, cy, color=LINE, width=1.5)
    bw = 0.42
    for i, (num, label, sub) in enumerate(items):
        cx = x0 + gap * i + gap / 2
        box = rect(slide, cx - bw / 2, cy - bw / 2, bw, bw, fill=accent, radius=0.18)
        text(slide, cx - bw / 2, cy - 0.135, bw, 0.28,
             [dict(t=num, size=11, bold=True, color=WHITE, font=MONO, align=PP_ALIGN.CENTER)],
             check=False)
        text(slide, cx - gap / 2 + 0.04, cy + 0.40, gap - 0.08, 0.30,
             [dict(t=label, size=size, bold=True, color=label_color, align=PP_ALIGN.CENTER)],
             check=False)
        if sub:
            text(slide, cx - gap / 2 + 0.04, cy + 0.72, gap - 0.08, 0.30,
                 [dict(t=sub, size=9, color=sub_color, align=PP_ALIGN.CENTER)], check=False)

def rich(slide, x, y, w, h, segs, anchor=MSO_ANCHOR.TOP, margins=(0, 0, 0, 0),
         line=1.35, align=PP_ALIGN.LEFT, size=11, color=None):
    """一段文字里混排多种字体（例如 [LGTM] 用等宽、后面的中文用黑体）。"""
    tb = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    tf = tb.text_frame
    tf.word_wrap = True
    tf.vertical_anchor = anchor
    tf.margin_left, tf.margin_right, tf.margin_top, tf.margin_bottom = [Inches(m) for m in margins]
    tf.auto_size = None
    para = tf.paragraphs[0]
    para.alignment = align
    para.line_spacing = line
    for t, st in segs:
        run = para.add_run()
        run.text = t
        f = run.font
        f.name = st.get("font", FONT)
        f.size = Pt(st.get("size", size))
        f.bold = st.get("bold", False)
        f.color.rgb = st.get("color", color or INK)
    return tb
