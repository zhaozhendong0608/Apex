# 🏛️ 老代码解密与契约逆向提纯核对清单 (Legacy Archeology Checklist)

> 💡 **使用规约**：在对缺乏文档的老代码进行“考古”时，对照本清单逐项检查，将隐性逻辑、数据结构与隐藏分支提纯为可读的 `legacy_hld.md` 文档。

---

## 📌 1. 入口与接口契约核对

- [ ] **请求路径 (URL & HTTP Method)**：提取 `@RequestMapping` / `@PostMapping` / `@GetMapping` 声明的精准 URL 路径。
- [ ] **隐藏请求头 (Headers)**：检查是否有强依赖的 Header 字段（如 `Authorization`, `X-Tenant-ID`, `User-Agent` 过滤）。
- [ ] **入参对象 (Request Schema)**：
  - [ ] 提取 Form 表单 / Query Param / JSON Body 字典结构。
  - [ ] 标记必填项与可选项。
  - [ ] 检查入参是否有硬编码默认值（如：若为空则默认赋值为 0 或 1）。
- [ ] **返参对象 (Response Schema)**：
  - [ ] 提取 HTTP 状态码（200, 400, 403, 500）。
  - [ ] 提取返回 JSON 字段列表及数据类型（String, Integer, Double, Boolean, Array, Object）。

---

## 🗄️ 2. 数据库与持久化核对

- [ ] **主读写数据表**：识别 MyBatis XML / JPA Repository / SQL 中的真实表名 (`t_...`)。
- [ ] **关联数据表 (Joins & Subqueries)**：识别关联多表查询，记录外键与关联条件。
- [ ] **隐藏字段逻辑**：
  - [ ] 逻辑删除字段：如 `is_deleted = 0` / `status = -1`。
  - [ ] 自动时间字段：如 `create_time`, `update_time`。
  - [ ] 租户隔离字段：如 `tenant_id`。

---

## ⚠️ 3. 隐性业务逻辑与埋坑防爆核对

- [ ] **分支条件硬编码 (Hardcoded Flags)**：检查是否存在根据固定字符串或魔法数字做分支判断的代码（例：`if ("VIP".equals(userType))`）。
- [ ] **空指针盲区 (Null Hazards)**：检查对象调属性前是否有 `null` 校验；数组/集合是否有 `.get(0)` 未判定越界。
- [ ] **隐式并发与事务**：检查方法是否有 `@Transactional` 事务注解，是否存在悲观锁 (`FOR UPDATE`) 或 Redis 分布式锁。
- [ ] **外部服务依赖 (External Services)**：检查方法中是否调用了第三方 HTTP 接口、RPC 服务、MQ 消息队列或存储过程。

---

## 📝 4. 逆向落盘成果模板 (`docs/legacy/legacy_hld_<module>.md`)

```markdown
# 🏛️ 逆向解密文档: <module_name>

- **模块描述**：[解密出的核心业务]
- **逆向 Controller/Service**：`UserController.java`, `UserServiceImpl.java`
- **读写数据表**：`t_user` (读写), `t_user_role` (只读)
- **API 接口清单**：
  - `POST /api/legacy/user/login` -> 登录校验，返回 JWT Token
- **已发现的隐患与坑点**：
  - 1. 密码传输使用 Base64 未加盐加密。
  - 2. 查询未加分页限制，可能产生全表扫描风险。
```
