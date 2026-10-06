# 💻 Coding-Standards (统一代码规范与 DLD 契约绑定专家)

> **技能定位**：负责在代码编写阶段，强约束 AI 逐字绑定 DLD 详细设计说明书中的数据库表结构、字段名与 API 契约，并按需加载 Java 或 Vue 语言级别的分层开发规约。

---

## 📂 目录结构与资源索引

- **[SKILL.md](./SKILL.md)**：技能核心定义文件（包含 DLD 契约强绑定原则、按需加载路由及 Step-by-Step 编码流程）。
- **`references/`**：
  - **[dld-binding-checklist.md](./references/dld-binding-checklist.md)**：DLD 表结构、字段名及 RESTful API 契约强绑定 1:1 对齐核对清单。
  - **[java-convention.md](./references/java-convention.md)**：Java (Spring Boot / MyBatis-Plus / DTO / VO / Entity / 统一响应 `R<T>`) 企业级分层架构规约。
  - **[vue-convention.md](./references/vue-convention.md)**：Vue 3 (Composition API / TypeScript / Pinia / Element Plus / Axios API 层解耦) 前端规范。

---

## 🚀 典型使用场景

1. **通用编码阶段挂载**：在 `.ai/sop/02-sop-coding.md` 编码流程中被自动激活与挂载。
2. **后中段代码落地**：在完成 DLD 架构设计说明书（`design-spec-architect`）后，准备真正编写 Java 后端实体/Controller 或 Vue 前端页面与 API 请求层时。
3. **语言规范校验**：确保生成的 Java / Vue 代码对齐企业级质量规约（禁用 `System.out`、禁用 `any` 滥用、禁用硬编码伪造 Mock 假数据）。
