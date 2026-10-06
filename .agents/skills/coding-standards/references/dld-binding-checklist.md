# 📋 DLD 契约强绑定核对清单 (DLD Binding Checklist)

> **核心法则**：设计即代码，代码即契约。AI 在编码时不得擅自修改任何在 DLD 中已沉淀的命名、数据类型或响应结构。

---

## 🗄️ 1. 数据库与数据模型 (Database & Models)

在编写 SQL Migration、Java Entity、Vue Form/Model 时，检查以下各项：

- [ ] **表名与类名对应**：`sys_user` ➔ `UserEntity`（严格按照驼峰或下划线规约转换）。
- [ ] **主键自增与类型**：主键是 `BIGINT` 还是 `UUID`，必须 1:1 对齐 DLD 6.2 章节。
- [ ] **字段命名 100% 对应**：
  - DLD 中定义为 `user_status`，代码中严禁拼错或简写为 `status` 或 `u_status`。
- [ ] **数据类型与长度**：
  - `VARCHAR(64)` ➔ Java `String` / Vue `string`；
  - `TINYINT` ➔ Java `Integer` / `Byte` / Enum；
  - `DECIMAL(10,2)` ➔ Java `BigDecimal` / Vue `number`。
- [ ] **空值与默认值**：是否 `NOT NULL`，默认值是 `0` 还是 `CURRENT_TIMESTAMP`。
- [ ] **索引定义**：唯⼀索引、复合索引的字段顺序必须一致。

---

## 🔌 2. RESTful API 接口契约 (API Contracts)

在编写 Controller 路由处理函数、Axios API 请求方法时，检查以下各项：

- [ ] **HTTP Method**：请求方法必须与 DLD 严格一致（`GET`, `POST`, `PUT`, `DELETE`）。
- [ ] **请求 URL 路径**：
  - 路径前缀（如 `/api/v1/users/{userId}`）与路径变量名称必须 1:1 匹配。
- [ ] **Query / Header / Body 参数**：
  - Body 入参属性名必须完全一致（如 `accountName` 不能写成 `account_name` 或 `username`）。
- [ ] **统一响应包装 (Response Format)**：
  - 必须包裹统一响应体（如 `R<T>` / `Result<T>`）：
    ```json
    {
      "code": 200,
      "message": "操作成功",
      "data": { ... }
    }
    ```
- [ ] **错误码 (Error Codes)**：异常场景返回的错误码（如 `40001: 账号已被冻结`）必须符合 DLD 接口异常说明。

---

## 🛡️ 3. 违反契约的防腐拦截 (Anti-Corruption Rule)

若 AI 在编码过程中发现“现有 DLD 设计缺少某个字段”或“设计不合理”：

1. **绝对禁止** 悄悄在代码里偷偷多写或少写字段！
2. **强制规则**：
   - 先停止改动代码。
   - 明确提示用户：“*在写代码时发现 DLD 缺少 `xxx` 字段，建议先在 DLD 中追加，或经确认后再行修改*”。
