#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Archetype-Architect DOM 扫描器 (parse_html.py - Fail-safe Version)
具备纯文本正则降级容错 (Fail-safe Fallback) 能力，确保在非标/畸形 HTML 中依然能提炼交互节点。
"""

import sys
import os
import json
import re
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

    def handle_starttag(self, tag, attrs):
        attr_dict = dict(attrs)
        self.current_tag = tag
        self.current_attrs = attr_dict

        if tag in ["button", "a", "input", "select", "textarea"] or "onclick" in attr_dict or attr_dict.get("role") == "button":
            self.interactive_elements.append({
                "tag": tag,
                "id": attr_dict.get("id", ""),
                "class": attr_dict.get("class", ""),
                "type": attr_dict.get("type", ""),
                "text": ""
            })

        if tag == "form":
            self.forms.append({
                "id": attr_dict.get("id", ""),
                "action": attr_dict.get("action", ""),
                "method": attr_dict.get("method", "POST")
            })

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
        if text and self.interactive_elements and not self.interactive_elements[-1]["text"]:
            self.interactive_elements[-1]["text"] = text

def fallback_regex_scan(content):
    """降级容错：在 HTML 语法解析失败时使用纯文本正则提取"""
    print("⚠️ 警告: HTML 语法解析遇到异常，自动降级为【纯文本正则提取模式】...")
    elements = []
    
    # 匹配 button / input / a 标签
    btn_pattern = r'<(button|a|input)[^>]*?(?:id=["\'](.*?)["\'])?[^>]*?>(.*?)</\1>'
    matches = re.findall(btn_pattern, content, re.IGNORECASE | re.DOTALL)
    
    for tag, elem_id, text in matches[:30]:
        clean_text = re.sub(r'<[^>]+>', '', text).strip()
        elements.append({
            "tag": tag.lower(),
            "id": elem_id or "",
            "text": clean_text[:20]
        })
        
    return {
        "fallback_mode": True,
        "interactive_elements_count": len(elements),
        "interactive_elements": elements
    }

def scan_html_file(file_path):
    if not os.path.exists(file_path):
        print(f"❌ 错误: 文件不存在: {file_path}")
        return

    try:
        with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
            content = f.read()

        parser = DOMScanner()
        parser.feed(content)

        result = {
            "file": file_path,
            "fallback_mode": False,
            "forms": parser.forms,
            "modals": parser.modals,
            "tabs": parser.tabs,
            "interactive_elements_count": len(parser.interactive_elements),
            "interactive_elements": parser.interactive_elements[:30]
        }
        print(json.dumps(result, ensure_ascii=False, indent=2))

    except Exception as e:
        # 激活降级容错 Protocol
        res = fallback_regex_scan(content)
        res["file"] = file_path
        res["error_reason"] = str(e)
        print(json.dumps(res, ensure_ascii=False, indent=2))

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("用法: python3 parse_html.py <path_to_html_file>")
        sys.exit(1)

    html_file = sys.argv[1]
    scan_html_file(html_file)
