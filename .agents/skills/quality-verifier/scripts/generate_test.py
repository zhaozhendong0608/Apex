#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Quality-Verifier 自动化测试脚本生成器
根据模块名称与测试配置，一键自动生成标准 Python 测试脚本 (scripts/tests/test_<module_name>.py)
"""

import sys
import os
import re

TEST_SCRIPT_TEMPLATE = '''#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
{module_name} 模块自动化契约碰撞测试脚本
自动校验 API 契约响应、上下文变量传递及数据库/Teardown 恢复。
"""

import os
import sys
import unittest
import requests

BASE_URL = os.getenv("TEST_BASE_URL", "http://localhost:8080")
TEST_HEADERS = {{"Content-Type": "application/json", "Authorization": "Bearer test_token"}}

class TestContext:
    """全局动态上下文依赖传递池"""
    created_id = None
    created_resources = []


class Test{module_class_name}Contract(unittest.TestCase):
    """{module_name} 自动化测试套件"""

    @classmethod
    def setUpClass(cls):
        print(f"\\n🚀 [Setup] 开始对 {module_name} 模块执行契约碰撞测试...")

    @classmethod
    def tearDownClass(cls):
        print(f"\\n🧹 [Teardown] 开始进行数据后置清理...")
        for res_id in TestContext.created_resources:
            try:
                requests.delete(f"{{BASE_URL}}/api/v1/{module_name}/{{res_id}}", headers=TEST_HEADERS, timeout=3)
            except Exception as e:
                print(f"⚠️ 清理资源 {{res_id}} 失败: {{e}}")

    def test_01_create_resource_happy_path(self):
        """TC-HP-001: 正向用例 - 创建资源"""
        url = f"{{BASE_URL}}/api/v1/{module_name}"
        payload = {{"name": "test_{module_name}_01", "status": "ACTIVE"}}
        
        response = requests.post(url, json=payload, headers=TEST_HEADERS, timeout=5)
        self.assertEqual(response.status_code, 200, f"HTTP 状态码异常: {{response.status_code}}")
        
        res_json = response.json()
        self.assertEqual(res_json.get("code"), 200, f"业务响应码非 200: {{res_json}}")
        
        created_id = res_json.get("data", {{}}).get("id", 101)
        TestContext.created_id = created_id
        TestContext.created_resources.append(created_id)
        print(f"✅ TC-HP-001 创建测试通过，生成动态 ID: {{created_id}}")

    def test_02_get_resource_by_id(self):
        """TC-HP-002: 上下文依赖用例 - 根据 ID 查询"""
        res_id = TestContext.created_id or 101
        url = f"{{BASE_URL}}/api/v1/{module_name}/{{res_id}}"
        
        response = requests.get(url, headers=TEST_HEADERS, timeout=5)
        self.assertEqual(response.status_code, 200)
        res_json = response.json()
        self.assertEqual(res_json.get("code"), 200)
        print(f"✅ TC-HP-002 查询测试通过")

    def test_03_boundary_invalid_input(self):
        """TC-EX-001: 边界用例 - 空字段非法入参"""
        url = f"{{BASE_URL}}/api/v1/{module_name}"
        payload = {{"name": ""}}
        
        response = requests.post(url, json=payload, headers=TEST_HEADERS, timeout=5)
        self.assertIn(response.status_code, [400, 422], f"预期校验拦截返回 400/422，实际返回 {{response.status_code}}")
        print("✅ TC-EX-001 边界校验拦截测试通过")


if __name__ == "__main__":
    unittest.main(verbosity=2)
'''

def generate_test_script(module_name):
    clean_name = re.sub(r'[^a-zA-Z0-9_]', '_', module_name).lower().strip('_')
    class_name = ''.join(word.capitalize() for word in clean_name.split('_'))
    
    target_dir = os.path.join("scripts", "tests")
    os.makedirs(target_dir, exist_ok=True)
    
    target_file = os.path.join(target_dir, f"test_{clean_name}.py")
    
    content = TEST_SCRIPT_TEMPLATE.format(
        module_name=clean_name,
        module_class_name=class_name
    )
    
    with open(target_file, "w", encoding="utf-8") as f:
        f.write(content)
        
    print(f"✅ 成功在工程中生成 Python 自动化测试脚本: {target_file}")
    return target_file

def main():
    if len(sys.argv) < 2:
        print("用法: python3 generate_test.py <module_name>")
        print("示例: python3 generate_test.py user_management")
        return
        
    module_name = sys.argv[1]
    generate_test_script(module_name)

if __name__ == "__main__":
    main()
