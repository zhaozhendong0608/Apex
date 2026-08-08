# 🌐 04 - API 接口详细设计规范

> [!NOTE]
> ⚠️ **【参考示例模板说明】**：本文件为 Base 工程脚手架预置的 API 接口设计范例与规范示例。实际项目开发时，请根据真实项目的接口契约进行修改替换。

## 1. 接口通用约定
- **协议**：HTTP / HTTPS
- **请求数据格式**：`application/json; charset=utf-8`
- **响应通用结构**：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

---

## 2. 接口列表清单

### 2.1 用户登录 (`POST /api/v1/auth/login`)
- **请求 Body**：
```json
{
  "username": "admin",
  "password": "secret_password"
}
```
- **响应 Body**：
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsIn...",
    "expires_in": 7200
  }
}
```
