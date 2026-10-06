# 🦆 小黄鸭探针日志排错与根因分析协议 (Rubber Duck Debugging Protocol)

> 💡 **核心宗旨**：当出现编译失败、单元测试断言失败、运行时 Exception 或 HTTP 500 时，必须激活本小黄鸭排错协议。**严禁未经日志分析与根因推导擅自瞎猜修改代码！**

---

## 🛑 排错三大禁用避坑法则 (The 3 Antipatterns)

1. **禁用“掩耳盗铃”式 Try-Catch**：严禁为了让报错消失，使用 `try { ... } catch (Exception e) { return null; }` 或强行将异常吞掉/返回空壳假数据。
2. **禁用“盲人摸象”式修改**：严禁未查看完整 Exception StackTrace 就盲目尝试修改 3~5 个不同地方的代码试运气。
3. **禁用删测试断言**：严禁在单元测试失败时直接注释或删除 failing test。

---

## 🦆 小黄鸭排错四步走流程

### 步骤 1：日志与堆栈确定性提取 (Traceability First)
遇到报错，第一步**必须且只能**是提取完整的错误上下文：
- 抓取后端控制台日志（StackTrace 最内层的 `Caused by:` 所在文件和精确行号）。
- 抓取前端 Console 报错（Axios response status / error body / network error）。
- 识别关键信息：是什么 Exception？（如 `NullPointerException`, `KeyError`, `DataIntegrityViolationException`）。

### 步骤 2：对小黄鸭进行反思质问 (Duck Inquiry)
在分析代码前，AI 必须向“小黄鸭”阐述并回答以下 4 个问题：
1. **预期行为**：这行代码原本应该得到什么数据？
2. **实际输入**：崩掉的那一时刻，传入的真实变量值（Input Parameter）是什么？
3. **崩在哪一行**：哪一个点语法（如 `a.b.c`）或哪一个 API 调用触发了报错？
4. **上游溯源**：这个错误的入参是谁传进来的？（前端没发？数据库存的是 null？还是前置中间件拦截了？）

### 步骤 3：最小化隔离复现 (Minimal Reproduction)
在修改代码前，先构造一个能 100% 稳定复现问题的最小场景：
- 单独运行触发报错的这一个 `cURL` 请求或单元测试用例。
- 确认问题稳定复现，而不是偶发网络波动。

### 步骤 4：微创手术式修补与回归测试 (Surgical Patch & Verification)
- 找到真正的根因后，只在源头进行精确修补（例如在 upstream 数据入口增加防御性非空校验或纠正字段映射）。
- **必须重新运行质量碰撞测试**：使用 `quality-verifier` 重新跑一遍测试用例，确保：
  - 🟡 发生的 Bug 已彻底修复并通过。
  - 🟢 原有的主干正向用例未受影响（无回归风险）。

---

## 📋 典型错误根因排查矩阵

| 报错现象 (Exception / Log) | 常见根因 (Root Cause) | 小黄鸭推荐排查点 |
| :--- | :--- | :--- |
| `NullPointerException` | 变量/对象未经非空校验即调用属性；数据库查询为空 | 检查对象初始化链，在上游或入口增加校验 |
| `HTTP 400 Bad Request` | 前端发送的 JSON 字段名/类型与后端 DTO 不匹配 | 对照 DLD API 契约与 DTO 属性名，检查拼写 |
| `HTTP 401 / 403` | Token 缺失、过效或请求头名称传错 (`Authorization`) | 检查请求头与后端 JWT 拦截器配置 |
| `DataTruncation / 500` | 写入数据库的数据长度超过表结构字段定义 | 对照 DLD 数据库设计，检查 Column length 约束 |
| `Cors Error (CORS)` | 后端跨域头未配置或未允许 OPTIONS 预检请求 | 检查后端 WebMvcConfigurer / CORS filter 配置 |
