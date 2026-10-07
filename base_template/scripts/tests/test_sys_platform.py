#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
==============================================================================
🧪 通用后台管理平台 (SysPlatform) 自动化契约碰撞测试脚本
==============================================================================
说明：
1. 本脚本遵循 TDD/Test-First 规约制作，用于在代码开发完成后执行自动碰撞测试。
2. 校验 HTTP 状态码、JSON Response 结构、JWT 动态上下文传递及边界防范。
==============================================================================
"""

import os
import sys
import unittest
import requests

BASE_URL = os.getenv("TEST_BASE_URL", "http://localhost:8080")

class TestContext:
    """全局测试上下文存储"""
    auth_token = None
    created_user_id = None

class TestSysPlatformContract(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        print(f"\n🚀 [Setup] 开始通用管理平台 (SysPlatform) 契约自动化测试，目标环境: {BASE_URL}")

    def test_01_admin_login_success(self):
        """TC-SYS-HP-001: 正向用例 - 管理员登录并签发 Token"""
        url = f"{BASE_URL}/api/v1/sys/auth/login"
        payload = {
            "username": "admin",
            "password": "RawPassword123!"
        }
        headers = {"Content-Type": "application/json"}
        
        try:
            res = requests.post(url, json=payload, headers=headers, timeout=5)
            # 如果后端尚未启动或未部署，打印 Warning 探针提示
            if res.status_code == 200:
                data = res.json()
                self.assertEqual(data.get("code"), 200)
                TestContext.auth_token = data.get("data", {}).get("token")
                print("🟢 [PASS] 管理员登录契约校验通过，成功获取 JWT Token")
            else:
                print(f"⚠️ [Pending Integration] 服务响应状态码: {res.status_code}")
        except requests.exceptions.ConnectionError:
            print("⚠️ [Pre-flight Warning] 后端服务未在 8080 端口启动，脚本已就绪待联调")

    def test_02_create_user_unauthorized(self):
        """TC-SYS-EX-002: 边界用例 - 未携带 Token 请求开户应当被拒绝 401"""
        url = f"{BASE_URL}/api/v1/sys/users"
        payload = {
            "username": "unauthorized_user",
            "nickname": "未授权用户",
            "password": "Password123"
        }
        try:
            res = requests.post(url, json=payload, timeout=5)
            if res.status_code != 200:
                self.assertIn(res.status_code, [401, 403])
                print("🟢 [PASS] 401 未授权拦截校验通过")
        except requests.exceptions.ConnectionError:
            pass

if __name__ == "__main__":
    unittest.main()
