# 📐 Design-Spec-Architect (架构设计说明书与变更同步专家)

> **技能定位**：基于 PRD 和原型推演系统分层架构（HLD）与详细设计（DLD，严格遵循钧天科技 V1.0 8大章节标准），并在需求变更时自动对比旧版文档执行增量 Diff 更新与 Revision History 日志记录。

---

## 📂 目录结构与资源索引

- **[SKILL.md](./SKILL.md)**：技能核心定义文件（包含 8 大标准章节生成规约与变更同步机制）。
- **`examples/`**：
  - **[user_design_baseline.md](./examples/user_design_baseline.md)**：标杆范例（用户管理模块 HLD 与 DLD 完整产出示范）。
- **`references/`**：
  - **[change_sync_rules.md](./references/change_sync_rules.md)**：需求变更增量 Diff 与修订历史追加规约。
- **`templates/`**：
  - **[hld_template.md](./templates/hld_template.md)**：概要设计说明书 (High-Level Design) 模版（钧天科技 V1.0 标准）。
  - **[dld_template.md](./templates/dld_template.md)**：详细设计说明书 (Detailed Design) 模版（钧天科技 V1.0 标准）。

---

## 🚀 典型使用场景

1. 当完成模块 PRD 或原型设计后，自动推演并生成标准化 **概要设计说明书 (HLD)** 与 **详细设计说明书 (DLD)**。
2. 准备开始数据库 Migration / 建表与 API 接口落地前，需要确立数据字典与接口契约时。
3. 当发生 **需求变更 (Change Request)** 时，自动对比新老需求，在原有设计说明书中执行 **局部增量 Diff 修正** 并更新修订历史页。

