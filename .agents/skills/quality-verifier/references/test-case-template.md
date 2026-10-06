# 🧪 企业级自动化测试用例文档规范模版 (V2.0 Enterprise Standard)

> 💡 **使用规约**：基于 DLD 详细设计说明书中的 API 契约与表结构设计，在编码前（Test-First/TDD）自动在 `docs/test-cases/<module_name>-testcase.md` 路径下生成该文档，并导出配套的 Python 自动化测试脚本 `scripts/tests/test_<module_name>.py`。

---

## 📌 1. 测试基础信息

- **测试模块**：`<module_name>` (例：`carbon-emission` / `user-management`)
- **关联设计文档**：`docs/modules/<module_name>/dld-<module_name>-v1.0.md`
- **契约 API 列表**：
  - `POST /api/v1/<endpoint>` - [接口一简述]
  - `GET /api/v1/<endpoint>/{id}` - [接口二简述]
- **测试环境配置**：
  - Base URL: `${TEST_BASE_URL:-http://localhost:8080}`
  - DB Connection: `${DB_URL:-jdbc:mysql://localhost:3306/test_db}`

---

## 📋 2. 企业级测试用例详细矩阵 (Test Cases Matrix)

### 🟢 2.1 正向与 E2E 链路用例 (Happy Path & E2E Workflow Cases)

| 用例 ID | 所属模块 (Module) | 接口及 Method | 测试场景与名称 | 前置依赖 (Given / Preconditions) | 输入参数 (Input / Request Body) | 动态提取变量 (Extract Vars) | 预期 HTTP 响应 | 数据库副作用断言 (DB Assert) | 后置清理 (Teardown) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `TC-HP-001` | 用户模块 | `POST /api/v1/user` | 正常创建新用户 | 无前置依赖 (数据库空状态) | `{"username": "test_user_01", "role": "ADMIN"}` | `user_id = $.data.id` | `HTTP 200` <br/> `{"code": 200}` | `SELECT count(1) FROM t_user WHERE username='test_user_01' == 1` | 暂不清理 (供下游调用) |
| `TC-HP-002` | 用户模块 | `GET /api/v1/user/{id}` | 按 ID 查询新增用户 | 依赖 `TC-HP-001` 成功 | URL Param: `id={{user_id}}` | `user_role = $.data.role` | `HTTP 200` <br/> `{"code": 200}` | - | 暂不清理 |
| `TC-HP-003` | 用户模块 | `DELETE /api/v1/user/{id}` | 物理/逻辑删除用户 | 依赖 `TC-HP-001` 成功 | URL Param: `id={{user_id}}` | - | `HTTP 200` | `SELECT status FROM t_user WHERE id={{user_id}} == 'DELETED'` | 物理清除 `test_user_01` 记录 |

---

### 🟡 2.2 边界与异常用例 (Boundary & Exception Test Cases)

| 用例 ID | 所属模块 (Module) | 接口及 Method | 测试类型与场景 | 输入参数 (Edge Input) | 预期响应 (Status & Error Code) | 预期错误提示 (Error Message) | 后置清理 (Teardown) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `TC-EX-001` | 用户模块 | `POST /api/v1/user` | 缺失必填项 | `{"username": ""}` | `HTTP 400` / `code: 4001` | "用户名不能为空" | 无需清理 |
| `TC-EX-002` | 用户模块 | `GET /api/v1/user/999999` | 访问不存在资源 | URL Param: `id=999999` | `HTTP 404` / `code: 4004` | "对应用户资源不存在" | 无需清理 |
| `TC-EX-003` | 用户模块 | `POST /api/v1/user` | 鉴权失败 | Header 缺少 `Authorization` | `HTTP 401` | "未认证的非安全请求" | 无需清理 |

---

### 🔴 2.3 业务约束与状态机用例 (Business & State Machine Cases)

| 用例 ID | 所属模块 (Module) | 触发场景 | 触发条件 | 预期系统表现 | 数据库验证 (DB Check) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `TC-BI-001` | 订单模块 | 重复提交防重锁 | 1 秒内使用相同 `X-Request-ID` 连续发送 2 次创建请求 | 第一次 `HTTP 200` 成功，第二次 `HTTP 409` 拒绝或返回同一单号 | 数据库中仅产生 1 条订单记录 |
| `TC-BI-002` | 订单模块 | 非法状态流转 | 对处于“已关闭”状态的订单强行调用“退款申请”接口 | `HTTP 422` 拒绝更新，返回“订单状态不允许退款” | 数据库 `order_status` 字段保持 `CLOSED` 不变 |

---

## 🐍 3. Python 自动化测试脚本标准规范 (`test_<module_name>.py`)

> 💡 所有测试脚本统一通过 **Python 3** 编写，使用 `requests` + `unittest` / `pytest` 编写，支持**动态上下文变量传递、数据库副作用断言及 Teardown 自动恢复**。

自动化测试脚本路径：`scripts/tests/test_<module_name>.py`

```python
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
<module_name> 自动化契约碰撞测试脚本 (Python Standard Version)
自动校验 API 契约响应、上下文变量传递、数据库副作用落盘及 Teardown 清理。
"""

import os
import sys
import unittest
import requests

# 测试环境全局配置
BASE_URL = os.getenv("TEST_BASE_URL", "http://localhost:8080")
TEST_HEADERS = {"Content-Type": "application/json", "Authorization": "Bearer test_token"}

class TestContext:
    """全局动态变量上下文传递池"""
    user_id = None
    created_resources = []


class TestModuleContract(unittest.TestCase):
    """<module_name> 自动化测试用例集"""

    @classmethod
    def setUpClass(cls):
        """测试前置环境准备 (Preconditions)"""
        print(f"\n🚀 [Setup] 启动 <module_name> 测试套件，目标环境: {BASE_URL}")

    @classmethod
    def tearDownClass(cls):
        """测试后置环境清理 (Teardown & Cleanup)"""
        print(f"\n🧹 [Teardown] 开始清理测试产生的脏数据，受影响 ID: {TestContext.created_resources}")
        for resource_id in TestContext.created_resources:
            try:
                requests.delete(f"{BASE_URL}/api/v1/user/{resource_id}", headers=TEST_HEADERS, timeout=5)
            except Exception as e:
                print(f"⚠️ 清理资源 {resource_id} 失败: {e}")

    def test_01_create_user_happy_path(self):
        """TC-HP-001: [用户模块] 正向用例 - 正常创建用户"""
        url = f"{BASE_URL}/api/v1/user"
        payload = {"username": "py_test_user_01", "role": "ADMIN"}
        
        response = requests.post(url, json=payload, headers=TEST_HEADERS, timeout=5)
        
        # 1. 响应状态码与 JSON 契约断言
        self.assertEqual(response.status_code, 200, f"预期 200，实际返回 {response.status_code}")
        res_json = response.json()
        self.assertEqual(res_json.get("code"), 200, f"业务响应码异常: {res_json}")
        
        # 2. 动态提取变量至全局上下文
        created_id = res_json.get("data", {}).get("id")
        self.assertIsNotNone(created_id, "返回数据中必须包含非空用户 ID")
        TestContext.user_id = created_id
        TestContext.created_resources.append(created_id)
        print(f"✅ TC-HP-001 通过，提取动态 ID: {created_id}")

    def test_02_get_user_by_id(self):
        """TC-HP-002: [用户模块] 正向用例 - 上下文依赖查询用户"""
        self.assertIsNotNone(TestContext.user_id, "前置依赖失败: user_id 未生成")
        
        url = f"{BASE_URL}/api/v1/user/{TestContext.user_id}"
        response = requests.get(url, headers=TEST_HEADERS, timeout=5)
        
        self.assertEqual(response.status_code, 200)
        res_json = response.json()
        self.assertEqual(res_json.get("data", {}).get("username"), "py_test_user_01")
        print(f"✅ TC-HP-002 上下文依赖查询测试通过")

    def test_03_create_user_missing_required_field(self):
        """TC-EX-001: [用户模块] 边界用例 - 缺失必填项"""
        url = f"{BASE_URL}/api/v1/user"
        payload = {"username": ""} # 空用户名
        
        response = requests.post(url, json=payload, headers=TEST_HEADERS, timeout=5)
        self.assertEqual(response.status_code, 400, f"预期 400 校验拦截，实际返回 {response.status_code}")
        print("✅ TC-EX-001 边界校验拦截测试通过")


if __name__ == "__main__":
    unittest.main(verbosity=2)
```
