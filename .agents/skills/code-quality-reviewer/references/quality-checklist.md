# 📋 静态代码质量与安全扫描 CheckList (quality-checklist.md)

## 📌 1. Java / Spring Boot 健壮性与安全 CheckList

| 序号 | 检查维度 | 审查点描述 | 规范与修复建议 |
| :--- | :--- | :--- | :--- |
| **J-01** | NPE 防范 | 对象的链式调用（如 `user.getRole().getName()`）无判空保护 | 使用 `Optional.ofNullable(user).map(User::getRole).map(Role::getName).orElse("")` |
| **J-02** | 集合判空 | 直接对集合调用 `.size()` 或 `.get(0)` | 先用 `CollectionUtils.isEmpty(list)` 判空保护 |
| **J-03** | 资源泄露 | `FileInputStream`/`Connection` 等流资源未在 `try-with-resources` 中关闭 | 重构为 `try (InputStream is = ...) { ... }` 自动关闭 |
| **J-04** | 并发安全 | 单例 `@Service` 类中声明了可变的成员变量 | 将状态收容至局部变量或 RequestContext，禁止成员变量存储请求状态 |
| **J-05** | SQL 注入 | MyBatis xml/注解中使用 `${param}` 拼接字符串 | 强制替换为 `#{param}` 预编译参数 |
| **J-06** | 异常吞没 | `catch (Exception e)` 块内部为空或仅有注释 | 强制记录 `log.error("...", e)` 或抛出自定义业务异常 `BizException` |
| **J-07** | 日志规范 | 使用 `e.printStackTrace()` 或 `System.out.println()` | 强制替换为 `log.info()` 或 `log.error()` |
| **J-08** | 复杂度过高 | 单个方法内部 `if-else` 嵌套超过 3 层 | 强制重构为卫语句提前 `return` 或策略模式 |

---

## 📌 2. Vue 3 / TypeScript 前端 CheckList

| 序号 | 检查维度 | 审查点描述 | 规范与修复建议 |
| :--- | :--- | :--- | :--- |
| **V-01** | 类型安全 | 滥用 `any` 类型声明 | 声明明确的 TypeScript `interface` 或 `type` |
| **V-02** | 内存泄露 | `onMounted` 绑定的全局 `window.addEventListener` 或 `setInterval` 在销毁时未清理 | 在 `onUnmounted` 中显式调用 `removeEventListener` 或 `clearInterval` |
| **V-03** | 响应式陷阱 | 解构 Vue `reactive` 对象导致响应式丢失 | 使用 `toRefs` 或保持对象引用 |
