---
name: coding-standards
description: 统一代码规范与 DLD 契约强绑定专家。负责在代码编写阶段，强约束 AI 逐字绑定 DLD 详细设计说明书中的数据库表结构、字段名与 API 契约，并按需加载 Java 或 Vue 语言级别的分层开发规约。
---

# Role: Coding-Standards (代码规范与 DLD 契约强绑定专家)

## 🔒 核心原则与契约铁律

### 1. DLD 契约 100% 强绑定 (Contract Binding)
强约束 AI 100% 绑定 `docs/` 下的 DLD 详细设计说明书，禁止自由发挥：
- **表结构对齐**：建表 SQL / ORM Entity 的字段名、类型、主键自增、索引、`NULL` 约束、默认值，必须与 `dld-v1.0.md` 第 6 章 **1:1 完全一致**。
- **API 接口对齐**：Controller 请求路径 (URL)、Method、DTO 参数名、VO/JSON 字段名、HTTP 码及 Error Code，必须与 `dld-v1.0.md` 第 5 章 **1:1 完全一致**。
- **核对卡片**：参照 `references/dld-binding-checklist.md`。

### 2. 语言规约按需加载 (Lazy Loading)
编码前先识别语言/框架，**显式调用 `view_file` 读取对应规约**（禁止全量加载无关规约）：
- **Java / Spring Boot / MyBatis** ➔ 必须读取 `references/java-convention.md`
- **Vue 3 / TypeScript / Element Plus** ➔ 必须读取 `references/vue-convention.md`
- **公共/底层代码修改** ➔ 必须读取 `references/impact-analysis-checklist.md` 并运行 `grep_search` 扫描全局受影响依赖。

### 3. 可观测性与结构化日志规范
编写核心层 (Controller/Service/GlobalExceptionHandler) 时必须：
- **读取** `references/observability-and-logging-guide.md`；
- **贯穿 TraceID**：日志配置 MDC 包含 `[X-Trace-Id]` 占位符，支持全链路追踪；
- **结构化日志**：入出参、状态变更及 Exception 必须打印包含关键标识（如 `userId`/`orderNo`）与全量 Traceback 的日志。

### 4. 零 Mock 假数据铁律 (Zero Mock Enforcement)
- **禁止硬编码假数据**：严禁在 Controller/Service/API 层使用 `List.of(Map.of(...))`、`new HashMap()` 硬编码或写死返回值假数据。
- **真实数据库/接口联调**：所有业务 API 必须 100% 连接真实数据库 (MySQL/H2) 或第三方真实接口进行 CRUD 操作。

### 5. 代码整洁度与 Lombok 强规范 (Clean Code & Lombok Standards)
- **Lombok 注解强约束**：所有的 Entity、DTO、VO、BO 对象必须统一强制使用 `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder` 等 Lombok 注解，严禁手写冗余模板化的 getter/setter/toString 方法。
- **极致代码整洁度**：删除任何废弃/被注释掉的代码段，保持简洁干净；方法行数 ≤ 25 行，单文件 ≤ 200 行，强制使用卫语句提前返回。

---

## 🛠️ 执行流程 (Step-by-Step Workflow)

`1. DLD契约锁定` ➔ `2. 影响扫描与按需读取规约` ➔ `3. 看门狗护栏增量编码` ➔ `4. 对齐校验`

### 步骤 1: DLD 契约锁定
1. 检查是否存在 `docs/modules/<module_name>/dld-<module_name>-v1.0.md`（或 `03-database-design.md` / `04-api-design.md`）；若不存在则输出 Warning 提示先补全设计。
2. 提取表结构字段与 API 定义，锁定本次编码的字段与路径白名单。

### 步骤 2: 按需读取规约与影响自判
- 触碰公共类/Util/Base/拦截器 ➔ 读 `references/impact-analysis-checklist.md` 并全局扫描影响范围。
- `.java` 文件 ➔ 读 `references/java-convention.md` 及 `references/observability-and-logging-guide.md`。
- `.vue` / `.ts` / `.js` 文件 ➔ 读 `references/vue-convention.md`。

### 步骤 3: 结合看门狗增量编码
1. 遵守 `02-sop-coding.md` 护栏：最小改动、零新依赖、无假 Mock、影响隔离。
2. 直接写入指定路径，修改点附带中文注释。

---

## 📁 参考文档索引
- `references/observability-and-logging-guide.md`：可观测性打点与结构化日志诊断规范
- `references/dld-binding-checklist.md`：DLD 契约强绑定核对卡片
- `references/impact-analysis-checklist.md`：公共底层代码修改影响范围自判协议
- `references/java-convention.md`：Java 企业级分层架构与代码规范
- `references/vue-convention.md`：Vue 3 前端工程化与 API 契约层规范
- `references/agent-handover-protocol.md`：多 Agent 会话交接与上下文传递协议


