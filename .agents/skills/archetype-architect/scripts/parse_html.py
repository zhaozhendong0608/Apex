#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Archetype-Architect DOM 扫描器 (parse_html.py)
用于确定性解析 HTML 原型文件，提取节点、交互触发点 (Button, Form, Input, Modal, Tab) 与数据属性。
"""

import sys
import os
import json
from html.parser import HTMLParser

class DOMScanner(HTMLParser):
    def __init__(self):
        super().__init__()
        self.interactive_elements = []
        self.modals = []
        self.forms = []
        self.tabs = []
        self.current_tag = None
        self.current_attrs = {}
        self.current_text = ""

    def handle_starttag(self, tag, attrs):
        attr_dict = dict(attrs)
        self.current_tag = tag
        self.current_attrs = attr_dict
        self.current_text = ""

        # 识别按钮/交互触发点
        if tag in ["button", "a", "input", "select", "textarea"] or "onclick" in attr_dict or attr_dict.get("role") == "button":
            self.interactive_elements.append({
                "tag": tag,
                "id": attr_dict.get("id", ""),
                "class": attr_dict.get("class", ""),
                "type": attr_dict.get("type", ""),
                "name": attr_dict.get("name", ""),
                "placeholder": attr_dict.get("placeholder", ""),
                "text": ""  # 待闭合时填充
            })

        # 识别表单
        if tag == "form":
            self.forms.append({
                "id": attr_dict.get("id", ""),
                "action": attr_dict.get("action", ""),
                "method": attr_dict.get("method", "POST")
            })

        # 识别 Modal 弹窗或 Tab
        class_str = attr_dict.get("class", "").lower()
        if "modal" in class_str or "dialog" in class_str or attr_dict.get("role") == "dialog":
            self.modals.append({
                "id": attr_dict.get("id", ""),
                "class": attr_dict.get("class", "")
            })

        if "tab" in class_str or attr_dict.get("role") == "tab":
            self.tabs.append({
                "id": attr_dict.get("id", ""),
                "text": ""
            })

    def handle_data(self, data):
        text = data.strip()
        if text:
            self.current_text += text
            if self.interactive_elements and not self.interactive_elements[-1]["text"]:
                self.interactive_elements[-1]["text"] = text

def scan_html_file(file_path):
    if not os.path.exists(file_path):
        print(f"❌ 错误: 文件不存在: {file_path}")
        return

    with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
        content = f.read()

    parser = DOMScanner()
    parser.feed(content)

    result = {
        "file": file_path,
        "forms": parser.forms,
        "modals": parser.modals,
        "tabs": parser.tabs,
        "interactive_elements_count": len(parser.interactive_elements),
        "interactive_elements": parser.interactive_elements[:30]  # 前30个关键元素
    }

    print(json.dumps(result, ensure_ascii=False, indent=2))

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("用法: python3 parse_html.py <path_to_html_file>")
        sys.exit(1)

    html_file = sys.argv[1]
    scan_html_file(html_file)
