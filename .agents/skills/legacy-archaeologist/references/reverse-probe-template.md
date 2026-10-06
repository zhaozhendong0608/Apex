# 🔒 老代码行为锁死探针规范模版 (Reverse Lock-in Probe Template)

> 💡 **核心目标**：在修改、重构或迁移老代码逻辑之前，通过自动化 Python 探针脚本“打桩”测试，固化现有的 API 接口行为与返参数据结构。**确保重构改动不会破坏现有老业务。**

---

## 📌 锁死探针三要素

1. **黑盒断言 (Blackbox Assertions)**：不关注老代码怎么实现的，只断言传入特定输入时，返回的状态码与数据结构 100% 不变。
2. **边缘场景保护 (Edge Case Lock)**：专门为隐性逻辑（如空值处理、特殊错误码）编写探针断言，确保重构后错误响应也不变。
3. **可自动化回归 (Automated Regression)**：放在 `scripts/tests/` 目录中，在改动完老代码后随时重新运行探针。

---

## 🐍 行为锁死探针 Python 代码模版 (`scripts/tests/test_legacy_<module>.py`)

```python
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
老代码 <module_name> 行为锁死与反向验证探针 (Reverse Verification Probe)
目的：锁死现有老接口逻辑，防止重构/改造过中发生旧功能回归事故。
"""

import os
import unittest
import requests

BASE_URL = os.getenv("TEST_BASE_URL", "http://localhost:8080")
TEST_HEADERS = {"Content-Type": "application/json", "Authorization": "Bearer legacy_test_token"}

class TestLegacyBehaviorLock(unittest.TestCase):
    """<module_name> 行为锁死探针套件"""

    def test_legacy_happy_path_lock(self):
        """[锁死用例 01] 正向流程返参格式锁死"""
        url = f"{BASE_URL}/api/legacy/<endpoint>"
        payload = {"id": 1, "type": "NORMAL"}
        
        response = requests.post(url, json=payload, headers=TEST_HEADERS, timeout=5)
        
        # 1. 锁死 HTTP 状态码
        self.assertEqual(response.status_code, 200, "老接口响应状态码发生变化！")
        
        # 2. 锁死老接口关键返参字段 (不允许丢失老字段)
        res_json = response.json()
        self.assertIn("code", res_json, "老接口响应缺少 code 字段")
        self.assertIn("data", res_json, "老接口响应缺少 data 字段")
        
        data = res_json.get("data", {})
        self.assertIn("legacy_id", data, "老接口 data 中缺少 legacy_id 属性")

    def test_legacy_edge_error_lock(self):
        """[锁死用例 02] 异常边界与错误提示锁死"""
        url = f"{BASE_URL}/api/legacy/<endpoint>"
        payload = {"id": -1} # 故意使用非法 ID
        
        response = requests.post(url, json=payload, headers=TEST_HEADERS, timeout=5)
        
        # 锁死老代码的特定错误表现 (防止重构后错误提示改变导致前端解析崩溃)
        res_json = response.json()
        self.assertIn(response.status_code, [400, 500])
        self.assertIn("code", res_json)

if __name__ == "__main__":
    unittest.main(verbosity=2)
```
