# 🛠️ 环境预检与 DB Migration 渐进式变更规范 (Pre-flight & Migration Protocol)

## ⚡ 一、 环境探针预检协议 (Pre-flight Healthcheck Protocol)

碰撞测试或请求 API 前，必须预检基础设施连通性：
1. **数据库连通性**：MySQL (3306) / PostgreSQL (5432) 端口响应。
2. **缓存/中间件**：Redis (6379) / RabbitMQ (5672) ping 通。
3. **应用服务端口**：后端应用 (8080/3000) 处于 `LISTEN` 监听状态。

### 降级处理 (Fallback on Unready)
预检失败时，探针必须拦截报错并输出 Warning 告警，禁止误判为代码逻辑 Bug：
```markdown
⚠️ **环境未就绪告警 (Pre-flight Warning)**：
- **故障节点**：MySQL (端口 3306) 无法连通。
- **可能原因**：本地数据库服务未启动或连接密码配置错误。
- **推荐操作**：请先启动数据库（如 `docker-compose up -d` 或启动 MySQL 服务），然后再运行测试脚本！
```

---

## 🗄️ 二、 DB Migration 渐进式变更规范 (DB Migration Standard)

凡涉及数据库**建表、字段变更与初始数据导入**，遵守 **【全量基线 `base.sql` + 增量 Migration 双轮驱动】** 规范。

### 1. 核心架构与目录划分

```plaintext
docs/db/
├── base.sql                               # 1. 全局通用底座 SQL (sys_user, sys_role, sys_dict 等)
├── service.sql                            # 2. 本业务工程/服务专属全量 SQL (订单、商品、支付等表结构)
└── migrations/                            # 3. 增量业务功能演进脚本
    ├── V1_1_0__add_order_tables.sql       # 1.1.0 增量业务建表 SQL
    ├── V1_1_1__add_payment_column.sql     # 1.1.1 增量业务加字段 SQL
    └── undo/
        ├── U1_1_0__add_order_tables.sql   # 对应 1.1.0 的撤销/回滚 SQL
        └── U1_1_1__add_payment_column.sql
```

### 2. 表结构变更规约 (0-to-1 vs 1-to-N Rules)

1. **新模块首次建表 (0 ➔ 1)**：通用表直写入 `docs/db/base.sql`；业务专属表直写入 `docs/db/service.sql`。
2. **已有稳定表二次调整 (1 ➔ N)**：表建立并生效后，新增字段/改类型/增索引时，**严禁修改既有 `base.sql` 或 `service.sql`**，必须在 `docs/db/migrations/` 下新建 `V...sql` 增量脚本进行平滑迁移。
3. **大版本归档合并 (Release Consolidation)**：重大版本（如 `V1.1.0`）归档发布时，AI 自动将已验证的 `V1_x` 增量 SQL **合并更新至 `service.sql` / `base.sql`** 快照。

#### 命名格式：
* **正向脚本**：`V{Major}_{Minor}_{Patch}__{Short_Description}.sql` （双下划线分隔）
* **回滚脚本**：`U{Major}_{Minor}_{Patch}__{Short_Description}.sql` （双下划线分隔）

---

### 3. DDL 编写三铁律 (DDL Iron Rules)

1. **铁律一：严禁 `DROP TABLE`**：非初始化场景禁止编写删除表 DDL；修改字段使用 `ALTER TABLE` 平滑迁移。
2. **铁律二：必须显式注释与默认值**：所有新建表/字段必须包含中文注释 (`COMMENT`)、字符集 (`DEFAULT CHARSET=utf8mb4`) 及主键/索引。
3. **铁律三：双向 1:1 对称回滚**：每一个正向演进脚本 (`V...sql`) 必须对应一个可逆的撤销脚本 (`U...sql`)（例：`ADD COLUMN` 对应 `DROP COLUMN`）。

---

## 📝 三、 流水线与自动化脚本集成

1. **需求/设计阶段**：`design-spec-architect` 生成 DLD 数据库设计时，自动在 `docs/db/migrations/` 初始化对应 `V1_x__*.sql` 与 `undo/U1_x__*.sql`。
2. **零人工目录树刷新**：AI 每次创建/修改 `docs/db/` 下的 SQL 后，自动触发运行：
   ```bash
   python3 .ai/scripts/sync_tree.py
   ```
   自动扫描磁盘最新 SQL 并动态刷新追加更新至 `docs/db/README.md` 与 `.ai/tier2_modules.md`，实现 100% 零人工维护。
3. **编码验证阶段**：SOP-02 修改 DB 模型时通过编辑 `V...sql` 追加变更；`quality-verifier` 先跑预检探针确认数据库可用，再发起 API 契约碰撞。

