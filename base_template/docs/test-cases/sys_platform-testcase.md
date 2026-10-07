# 🧪 TC-SYS-PLATFORM-V1.0: 通用后台管理平台测试说明书 (测试用例矩阵)

> 基于 [dld-sys-platform-v1.0.md](file:///Users/up_dong/Documents/java_workspace/Apex/base_template/docs/modules/sys_platform/dld-sys-platform-v1.0.md) 契约在编码前 (Test-First TDD) 制作

---

## 📌 1. 测试基础信息与契约基线

- **测试模块**：`sys_platform` (通用后台管理平台)
- **关联设计文档**：[dld-sys-platform-v1.0.md](file:///Users/up_dong/Documents/java_workspace/Apex/base_template/docs/modules/sys_platform/dld-sys-platform-v1.0.md)
- **核心验证接口**：
  - `POST /api/v1/sys/auth/login` - [账号密码登录与 JWT 签发]
  - `POST /api/v1/sys/users` - [管理员开户创建新用户]
  - `GET /api/v1/sys/menus/routers` - [获取当前登录用户的动态菜单树]
- **自动化测试脚本**：`scripts/tests/test_sys_platform.py`

---

## 📋 2. 测试用例详细矩阵 (Test Cases Matrix)

### 🟢 2.1 正向与 E2E 链路测试用例 (Happy Path Cases)

| 用例 ID | 所属模块 | 接口及 Method | 测试场景与描述 | 输入参数 | 预期 HTTP 响应 | 数据库副作用断言 (DB Assert) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `TC-SYS-HP-001` | 认证模块 | `POST /api/v1/sys/auth/login` | 超级管理员账号成功登录 | `{"username": "admin", "password": "RawPassword123!"}` | `HTTP 200` <br/> `code: 200` <br/> 包含非空 `token` | - |
| `TC-SYS-HP-002` | 用户模块 | `POST /api/v1/sys/users` | 管理员开户创建新账号 `zhangsan` | Header: `Authorization: Bearer <Token>` <br/> `{"username": "zhangsan", "nickname": "张三", "roleIds": [2]}` | `HTTP 200` <br/> `code: 200` <br/> 返回新 `userId` | `SELECT count(1) FROM sys_user WHERE username='zhangsan' == 1` |
| `TC-SYS-HP-003` | 菜单模块 | `GET /api/v1/sys/menus/routers` | 拉取管理员动态路由菜单树 | Header: `Authorization: Bearer <Token>` | `HTTP 200` <br/> `code: 200` <br/> 返回菜单 JSON 数组 | 校验返回列表中包含 `sys:user:list` 权限标识 |

---

### 🟡 2.2 边界与异常防范测试用例 (Boundary & Exception Cases)

| 用例 ID | 所属模块 | 接口及 Method | 测试类型与场景 | 输入参数 (Edge Input) | 预期响应 status & code | 预期错误提示 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `TC-SYS-EX-001` | 认证模块 | `POST /api/v1/sys/auth/login` | 提交错误密码登录 | `{"username": "admin", "password": "WrongPassword"}` | `HTTP 400` / `code: 4001` | "账号或密码错误" |
| `TC-SYS-EX-002` | 用户模块 | `POST /api/v1/sys/users` | 未携带 Token 试图调接口开户 | Header: 无 Authorization 头 | `HTTP 401` | "未认证的非安全请求" |
| `TC-SYS-EX-003` | 用户模块 | `POST /api/v1/sys/users` | 创建重复的用户名 `admin` | `{"username": "admin", "nickname": "重复账号"}` | `HTTP 400` / `code: 4003` | "用户名已被占用" |

---

### 🔴 2.3 核心业务规则与防超越测试用例 (Security Cases)

| 用例 ID | 业务规则场景 | 测试触发条件 | 预期系统安全防范表现 |
| :--- | :--- | :--- | :--- |
| `TC-SYS-SEC-001` | 超级管理员账号防禁用规则 | 试图通过 API 修改 `admin` (id=1001) 的状态为 `status=0` | 系统拦截拒绝操作，返回 `HTTP 403` / "系统内置超级管理员账号禁止禁用" |
| `TC-SYS-SEC-002` | 前台自主注册禁用规则 | 试图发起未鉴权的 `/api/v1/sys/register` 接口 | 接口返回 `HTTP 404` 或 `405` (确认未提供前台公开注册路由) |
