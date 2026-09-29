# 🎯 SOP: 需求规划与任务拆解 (01-sop-planning)

## 📌 核心目标
作为需求规划中枢，根据需求体量提供分流规划机制：小微/普通需求快速 Grill 并拆解 Task 写入看板；全新大模块联动调用 `archetype-architect` 专家 Skill 生成 PRD、交互原型与 Mermaid 架构图。

---

## 🤖 AI 执行流程与产出规约

### 1. 需求分流与 Grill-Me 依赖审计

根据用户指令选择适合的规划路径：

#### 🟢 路径 A：普通需求/增量功能 (输入 [1])
- **轻量 Grill-Me**：抛出 1~2 个最关键的选择题（A/B 选项），澄清接口/存储决策。
- **直接拆解**：无需生成大型 PRD，直接切入【3. Pizza Slicing 原子化拆解】。

#### 🔴 路径 B：全新大模块/复杂系统 (输入 [1+] 模块名)
- **激活专家 Skill**：加载 `.agents/skills/archetype-architect/SKILL.md`。
- **专家联动的四大产出**：
  1. **确定性 DOM 解析**：运行 `parse_html.py` 解析静态原型。
  2. **深度 Grill 盲区对撞**：针对状态、Token、防重抛出 3~4 个硬核决策问题。
  3. **标准 PRD & Mermaid 图表**：落盘 `docs/modules/<module_name>/prd-<module_name>-v1.md`，追加注册 `.ai/tier2_modules.md`。
  4. **🖥️ 可交互 HTML 原型 (`mockup.html`)**：继承 `base-shell-template.html` 布局外壳在 `docs/modules/<module_name>/mockup.html` 生成高保真交互原型。

---

### 2. Pizza Slicing 原子化拆解 (15~30 分钟/Task)
无论是路径 A 还是路径 B，最终均需收敛拆解为以下三层原子任务：
- `S1-Bone` (骨架层)：数据结构、API 契约、数据库 Migration。
- `S2-Muscle` (肌肉层)：基于 `mockup.html` 或现有页面接入真实业务逻辑与统一样式。
- `S3-Stub` (测试桩)：核心算法单测或联调探针。

---

### 3. 静默调用脚本写入看板
```bash
python3 .ai/scripts/board.py add "Task-001" "[S1-Bone] 标题" "白话目标" "涉及文件"
```

---

## 💬 最终回复卡片

✨ **需求规划与任务拆解已完成：**
- 📄 **PRD 文档**：`docs/modules/<module_name>/prd-v1.md` *(路径 B 大模块产出)*
- 🖥️ **交互原型**：[mockup.html](docs/modules/<module_name>/mockup.html) (*路径 B 大模块产出，双击体验*)
- 📋 **[Task-001]**：[S1-Bone] [数据/接口骨架] (目标: [白话目标])
👉 **任务已写入 `.ai/tier3_status.md` 看板，回复 [2] 开始编写代码！**

