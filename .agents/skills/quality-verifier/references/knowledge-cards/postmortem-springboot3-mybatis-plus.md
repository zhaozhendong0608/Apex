# 💥 踩坑卡片: Spring Boot 3.2+ 与 MyBatis-Plus 兼容死穴及 SQL 脚本加载解析

> **归档日期**：2026-10-07
> **触发场景**：Spring Boot 3.2.4 升级、Lombok 注解处理与外部 SQL 自动化加载

---

## 🚨 1. 现象与堆栈信息 (Symptom)

### 现象 A：FactoryBean 属性类型不匹配
```text
java.lang.IllegalArgumentException: Invalid value type for attribute 'factoryBeanObjectType': java.lang.String
	at org.springframework.beans.factory.support.FactoryBeanRegistrySupport.getTypeForFactoryBeanFromAttributes
```

### 现象 B：相对路径 SQL 脚本找不到
```text
Caused by: java.sql.SQLSyntaxErrorException / java.lang.IllegalStateException: No schema scripts found at location 'file:../docs/db/base.sql'
```

---

## 🔍 2. 根因剖析 (Root Cause)

1. **Spring 6.1 反射重构**：Spring Boot 3.2 将 `factoryBeanObjectType` 从 String 改为了 `Class<?>` 对象，旧版 MyBatis-Plus `3.5.5` 未处理此变更。
2. **Cwd 路径不稳定性**：`file:../` 强依赖执行 `java` 命令时的当前 Working Directory，在 IDE 中不同启动模式下会发生路径偏移。

---

## 🛠️ 3. 标准解决防线 (Solution Guardrail)

1. **强制包替换**：Spring Boot 3 项目必须使用 `mybatis-plus-spring-boot3-starter` (版本 `≥ 3.5.7`)。
2. **资源打包挂载**：在 `pom.xml` 中将外部 `docs/db/*.sql` 映射进 `classpath:db/`，在 `application.yml` 中使用 `classpath:db/base.sql` 进行稳定加载。
