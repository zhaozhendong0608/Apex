# 📋 反向质询 (Grill-Me Protocol) 10 大盲区清查指南

在分析静态 HTML 原型并向用户抛出 3~5 个选择题时，优先围绕以下 10 大架构盲区进行质询：

---

## 1. 认证凭证与状态持久化 (Auth & Storage)
- **清查点**：Token 存在 `localStorage`（持久化）还是 `sessionStorage`（标签关闭失效）？
- **清查点**：勾选“记住我”时的过期时间阶梯。

## 2. 路由重定向与现场恢复 (Routing & Restore)
- **清查点**：登录/提交成功后跳固定页 (`/dashboard`) 还是读取 `redirect_url` 返回上一步？
- **清查点**：表单填写一半打断后切回来，已填数据是否恢复？

## 3. 防重提交与并发风控 (Debounce & Idempotency)
- **清查点**：按钮点击后是立即 Disabled + Spinner 遮罩，还是做 500ms 防抖？
- **清查点**：连续点击是否支持接口幂等 Token (Idempotency Key)？

## 4. 列表与筛选器联动 (Filter & Pagination)
- **清查点**：切换 Header 筛选器时，列表是纯前端 Filter 还是触发后端 REST API 重拉？
- **清查点**：筛选条件改变后，当前页码 (Page Index) 是否重置为第 1 页？

## 5. 模态框 (Modal) 生命周期与数据刷盘
- **清查点**：Modal 关闭时是物理 `unmount`（销毁 DOM）还是 `display:none`？
- **清查点**：Modal 内操作成功后，父页面表格是静默局部刷，还是全量 Fetch？

## 6. 全局状态同步 (Global State Store)
- **清查点**：模块 A 修改的数据（如购物车数量/个人头像），需要同步更新全局 Pinia/Redux 的哪个 Store 节点？

## 7. 定时器与后台倒计时 (Timer & Polling)
- **清查点**：手机验证码 60s 倒计时期间，切换页面 Tab 倒计时是否需要在后台保持运行？

## 8. 错误边界与兜底体验 (Error Boundary)
- **清查点**：接口返回 401 (Unauthorized)、403 (Forbidden) 或 500 时，前端是 Toast 提示，还是全局跳转 Error 页面？

## 9. 真实数据 vs Mock 假数据界限
- **清查点**：【原型演练阶段】(mockup.html) 允许全量 Mock 交互；【源码编码阶段】(src/) 严格禁止硬编码假数据，必须打通真实 API！

## 10. 多角色与 RBAC 权限遮罩
- **清查点**：普通用户 (Role: USER) 与管理员 (Role: ADMIN) 看到的页面 DOM 元素/操作按钮是否有物理隐去或禁用？
