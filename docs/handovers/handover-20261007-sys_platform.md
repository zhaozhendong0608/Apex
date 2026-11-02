# 📦 项目交接归档文档 (Handover Document)

> 归档时间: 2026-10-07 18:26:00
> 所属模块: 通用后台管理平台 (SysPlatform) 全栈重构与闭环

---

## 🎯 1. 阶段性完成成果复盘

### 1.1 前端 Vue 3 全套工程 (Task-103)
- 完成 Vite + Vue 3 + TypeScript + Pinia + Element Plus 架构搭建。
- 整合 5 大苹果级高奢多主题 (`apple-blue`, `emerald-green`, `crimson-red`, `royal-violet`, `aurora-dark`) 动态切换器。
- 实现登录页 `LoginView.vue`、Layout 框架 `Layout.vue`、用户开户管理 `UserManagementView.vue`、角色权限 `RoleManagementView.vue`。

### 1.2 后端 Java 全量真实归档 (Task-104)
- 补齐 Spring Boot 3 主启动类 `SysApplication.java` 与 Maven `pom.xml`。
- **100% 消灭 Controller/Service 硬编码假 Mock 数据**，全面连通真实 MySQL 8.0 `apex` 数据库。
- 配置官网推荐标准 **Lombok 1.18.38** 注解驱动，彻底清理模板化废代码。

---

## 💡 2. 核心技术踩坑与解决方案 (Postmortem Checklist)

1. **Spring Boot 3.2.x 与 MyBatis-Plus 兼容死穴**：
   - *问题*：旧版 `mybatis-plus-boot-starter` (3.5.5) 会报 `java.lang.IllegalArgumentException: Invalid value type for attribute 'factoryBeanObjectType': java.lang.String`。
   - *解法*：必须在 `pom.xml` 中使用专为 Spring Boot 3 打造的 `mybatis-plus-spring-boot3-starter` (3.5.7)。

2. **Spring Boot SQL 脚本自动加载**：
   - *问题*：使用 `file:../docs/db/base.sql` 相对路径在不同工作目录下会报 `No schema scripts found`。
   - *解法*：在 `pom.xml` 的 `<resources>` 节点配置将 `../docs/db/*.sql` 动态打包至 `target/classes/db/`，在 `application.yml` 中使用标准的 `classpath:db/base.sql` 与 `classpath:db/service.sql` 装载！

---

## 🛠️ 3. 核心文件变更列表

- `base_template/backend/pom.xml` (Lombok 1.18.38 & MyBatis-Plus 3.5.7)
- `base_template/backend/src/main/resources/application.yml` (MySQL 8.0 挂载与 SQL 自动化)
- `base_template/backend/src/main/java/com/apex/sys/SysApplication.java` (主启动类)
- `base_template/docs/db/base.sql` 与 `service.sql` (系统底座与扩展业务 SQL 分层)
- `base_template/frontend/src/` (全套 Vue 3 视图、API 与 Store)
