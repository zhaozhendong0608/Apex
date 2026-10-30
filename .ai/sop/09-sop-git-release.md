# [9] Git 提交与发布回滚 (09-sop-git-release)
> **触发**：`[9]`/`发布`/`提交`/`tag`/`回滚` ｜ **前置**：`[7]` 已归档 ｜ **产出**：原子提交 + Tag + 三级回滚预案

## 📌 核心目标
让提交历史可追溯、发布可回溯、事故可回滚。

## 🛡️ 3 大 Git 铁律
1. **原子提交**：一次提交只对应一个业务切片或一个 Bug 修复；禁把代码、临时测试、配置、IDE 产物混在一包。
2. **红线过滤**：提交前审文件清单，禁把无关历史源码/临时产物纳入暂存区；禁提交明文密码与 Token（敏感配置留在 `.env` 且被 `.gitignore` 忽略）。
3. **库码版本绑定**：任何表结构变更的 commit，必须同时包含 `docs/db/migrations/` 下的 DDL 与 `rollback_*.sql`。

## 📝 提交规范
```text
<type>(<scope>): <subject>

[可选正文：背景与动机]
[可选脚注：Ref: Task-102]
```
`type`：`feat`/`fix`/`refactor`/`test`/`docs`/`ops`/`perf`；`scope`：`core`/`gateway`/`api`/`ui`/`deploy`/`db`。

## 🚀 发布
1. 导出可执行包或离线镜像，前端打离线静态资源。
2. 打 Tag `v<主>.<次>.<修订>`（如 `v1.2.0`）。
3. 从提交史提取 CHANGELOG（功能 / 修复 / DB 增量脚本路径）。

## 🛟 三级回滚
1. **容器层**：切回上一版本镜像 Tag 重启。
```bash
docker compose -f docs/deploy/docker-compose.yml down
docker compose -f docs/deploy/docker-compose.yml up -d <prev-version>
```
2. **数据库层**：执行对应版本 `rollback_V<x.y.z>__*.sql`。
3. **代码层**：建 `hotfix/rollback-<version>` 分支记录原因，同步更新看板与交接卡。

## 🧷 能力点
`C-9-01` 原子提交；`C-9-02` 敏感信息过滤；`C-9-03` DDL 与代码版本绑定；`C-9-04` Conventional Commits；`C-9-05` Tag 与 CHANGELOG；`C-9-06` 三级回滚。

## 📤 交付
卡片 ≤5 行：提交类型与信息 / 关联 Task / 变更文件（+X -Y）/ 回滚保障（有无 DDL）。
