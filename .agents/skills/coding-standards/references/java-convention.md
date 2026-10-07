# ☕ Java 企业级分层规约与性能防线 (Java Convention)

> **核心原则**：契约优先、防爆护栏、高性能低开销、零冗余废话。

---

## 🏛️ 1. 经典 4 层架构规范

```plaintext
com.yourpackage.module/
├── controller/   # 🌐 接口层：Valid 校验、路由映射、返回 R<VO>（代码 ≤ 15 行/方法）
├── service/      # 🧠 业务编排层：跨 Manager 调度、最小化事务控制
├── manager/      # 🛠️ 通用业务块：RPC 调用、组合多 DAO、Redis 缓存下沉、分布式锁
├── mapper/       # 🗄️ 持久层：MyBatis-Plus Mapper 接口
├── model/        # 📦 模型层：Entity (表 1:1)、DTO (入参)、VO (出参)
└── converter/    # 🔄 转换层：MapStruct 静态 Converter (严禁 BeanUtils 反射拷贝)
```

---

## ✅ 2. 硬性规约 (Do - 必须遵循)

| 维度         | 硬性要求 (Strict Rules)                                                                                                                                                                           |
| :----------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **NPE 防护** | POJO/Entity/DTO/VO 属性**强制使用包装类型** (`Long`/`Integer`)；字符串 `equals` **常量置左** (`"PAID".equals(status)`)。                                                                          |
| **性能防爆** | 集合已知元素大小时**必须指定 Capacity** (如 `new HashMap<>(135)`)；批量操作必须使用批量 SQL/`saveBatch()`；`pageSize` 强制上限约束 (`Math.min(pageSize, 100)`)。                                  |
| **并发安全** | 线程池必须用 `ThreadPoolExecutor` 手动创建并指定有界队列与拒绝策略；时间解析必须使用 `LocalDateTime`。                                                                                            |
| **事务控制** | 严禁在 `@Transactional` 中包含网络 HTTP / RPC / 大文件 IO；推荐 `TransactionTemplate` 编排最小化事务。                                                                                            |
| **Lombok 规范**| 所有 POJO/Entity/DTO/VO/BO 对象**强制使用 Lombok 注解** (`@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`)，**严禁手写模板化 getter/setter/toString/hashCode**；日志打印强制使用 `@Slf4j`。 |
| **代码整洁** | 所有类引用**必须在顶部 `import`**，严禁代码正文出现内联全限定路径 (FQCN)；单个方法体 **≤ 25 行**，长逻辑抽取为 `private` 私有方法；嵌套 **≤ 3 层**，强制使用**卫语句 (Guard Clauses) 提前返回**；禁止残留被注释掉的废弃代码段。 |

---

## 🚫 3. 红线禁令 (Don't - 一票否决)

1. ❌ **严禁在 Controller/Service/DAO 层硬编码 Mock 假数据** (如 `List.of(Map.of(...))` 或假 Bean)，所有 API 必须 100% 连接真实数据库或外部微服务。
2. ❌ **严禁手写繁琐模板化的 Getter/Setter 方法** (强制使用 Lombok 注解)。
3. ❌ **严禁代码正文出现全类名内联** (如 `com.example.UserVo`) 与 **`import *` 星号盲目导入**。
4. ❌ **严禁使用 `Executors` 创建无界线程池** 与 **使用线程不安全的 `SimpleDateFormat`**。
5. ❌ **严禁在生产环境使用 `BeanUtils.copyProperties`** (强制 MapStruct)。
6. ❌ **严禁在循环体内使用 `+` 拼接字符串** 或 **频繁 compile 正则表达式** (必须预编译为 `private static final` 常量)。
7. ❌ **严禁在热点循环中逐条 SQL 操作 DB** (导致 N 次网络 RTT 开销)。
8. ❌ **严禁在线程池环境使用 `ThreadLocal` 忘记在 `finally` 块中 `.remove()`** (防内存泄漏与数据污染)。
9. ❌ **严禁 `System.out` 打印** (强制 `@Slf4j`) 与 **空 `catch` 块吞异常**。

---

## 💡 4. 精炼代码范例

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderManager orderManager;
    private final OrderConverter orderConverter;
    private final TransactionTemplate transactionTemplate;

    @Override
    public OrderVO createOrder(OrderCreateDTO dto) {
        // 1. 卫语句提前返回/抛异常，消除 if-else 缩进
        if (dto == null || dto.getAmount() <= 0) {
            throw new BusinessException("订单参数非法");
        }
        
        // 2. 编排私有小方法 (主方法 < 20 行)
        OrderEntity entity = orderConverter.toEntity(dto);
        
        // 3. 最小化事务编排 (无网络 IO)
        transactionTemplate.executeWithoutResult(status -> orderManager.saveOrderAndLog(entity));
        
        // 4. 异步/RPC 置于事务外
        orderManager.sendPayNoticeAsync(entity);
        return orderConverter.toVO(entity);
    }
}
```
