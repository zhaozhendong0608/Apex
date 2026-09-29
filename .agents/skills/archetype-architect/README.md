# 🎨 Archetype-Architect (原型架构与模块关系梳理专家)

> **技能定位**：专门解析静态 HTML 原型，借助 Python 探针抓取 DOM 结构，通过反向质询（Grill Protocol）补全隐藏业务逻辑，按场景选配生成视觉高对比度 Mermaid 图表并强制落盘归档为模块 PRD。

---

## 📂 目录结构与资源索引

- **[SKILL.md](./SKILL.md)**：技能核心定义文件（包含 Role Profile、Phase 1~4 执行标准流程）。
- **`examples/`**：
  - **[auth_flow_baseline.md](./examples/auth_flow_baseline.md)**：理想标杆范例（从输入原型到完整 PRD 输出）。
- **`references/`**：
  - **`grill_checklist.md`**：反向质询盲区清单（Token持久化、并发防重、状态切换等）。
  - **`mermaid_standards.md`**：Mermaid 图表高对比度规范与语法标准。
- **`scripts/`**：
  - **`parse_html.py`**：HTML 静态原型确定性 DOM 解析探针脚本。
  - **`validate_mermaid.py`**：Mermaid 语法格式与节点对比度校验脚本。
- **`templates/`**：
  - **`module_prd_template.md`**：模块 PRD 标准落盘模板。

---

## 🚀 典型使用场景

1. 当用户输入 **`[1+] 模块名`** 或声明“新增全新的 XX 模块”时被激活。
2. 用户提供静态 HTML 线框图/原型页面（`mockup.html`），需要提取 API 契约与交互规则时。
3. 建立第二层模块大图 (`.ai/tier2_modules.md`) 并准备拆解 `[Task-XXX]` 前。
