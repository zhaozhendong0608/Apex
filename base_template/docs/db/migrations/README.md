# 🗄️ 增量业务 SQL 演进目录 (db/migrations)

本目录用于存放基于 Flyway / Liquibase 标准规范的**增量业务 Migration 脚本**。

---

## 📐 命名规范

```plaintext
docs/db/migrations/
├── V1_1_0__add_order_tables.sql       # 正向演进 SQL (格式: V{Major}_{Minor}_{Patch}__{Desc}.sql)
└── undo/
    └── U1_1_0__add_order_tables.sql   # 对应回滚 SQL (格式: U{Major}_{Minor}_{Patch}__{Desc}.sql)
```

## 🛡️ 三铁律
1. **严禁修改既有脚本**：一旦正向脚本 `V...sql` 已在开发/测试数据库执行，绝不能直接去改原有脚本文件，必须通过新建更高版本号的 `V...sql` 增量平滑演进。
2. **严禁 `DROP TABLE`**：日常业务变更禁止删除已有表。
3. **1:1 双向对称**：每个正向脚本在 `undo/` 下必须有对应的撤销脚本。
