# 🎯 SOP: 需求规划与任务拆解 (01-sop-planning)

## 📌 核心目标
将用户模糊的“想法”或“需求”，通过主动拷问（Grill-Me）与领域依赖雷达消除歧义，拆解为 15~30 分钟即可完成的原子化任务，并**静默调用 Python 脚本落盘至 `status.md`**。

---

## 🤖 AI 执行流程

### 阶段一：Grill-Me 与领域雷达 (Expert Audit & Radar)
当用户提出新需求或功能想法时，**严禁直接动手写代码**，按以下步骤审计：

1. **依赖雷达与 Solid 触碰检测**：
   * 检查是否触碰 **Solid 核心资产**（如数据库 Schema、公共 API 契约、`.env`、核心依赖配置）。若触碰，需高亮标记预警。
   * 识别上下游模块 (Upstream/Downstream) 的潜在依赖冲突。
2. **主动拷问 (Grill-Me)**：一次性向用户抛出 **2~4 个定型选择题（提供 A/B/C 选项）**，引导用户做决策。
3. **收敛共识**：形成最终的“需求澄清总结与技术路径”。

---

## 阶段二：大模块 PRD 与【可交互 HTML 原型】确定性产出 (Deliverables)
当用户使用 `1+ [模块名]` 或发起全新大模块规划时，AI 必须**确定性生成 3 大规划产出物**：

1. **第二层静态 PRD**：在 `docs/modules/<module_name>/prd-<module_name>-v1.md` 生成标准需求与 API 契约文档（明确入参返参）。
2. **第二层大模块索引**：在 `.ai/tier2_modules.md` 自动追加一行该大模块的注册记录。
3. **🖥️ 第二层可交互 HTML 原型 (`mockup.html`)**：在 `docs/modules/<module_name>/mockup.html` 自动生成**包含真实前端交互逻辑的线框原型页面**！

### 🎨 可交互 HTML 原型 (`mockup.html`) 制作规约：
- **零依赖解耦**：采用纯 Vanilla HTML5 + Vanilla JS + 语义化 Class（如 `.btn`, `.modal`, `.table`, `.form-input`），便于后续通用主题直接换肤。
- **必须具备真实交互效果**：
  - 点击按钮 ➔ 必须真的支持**弹窗 Modal 打开/关闭**、**Tab 选项卡切换**；
  - 点击表单提交 ➔ 必须具备**前端空校验与模拟成功提示**；
  - 列表数据 ➔ 必须具备**动态数据渲染与行选中状态**。
- **双击即用**：保证双击即可在本地浏览器中完美运行，作为团队需求评审与 Sign-off 的物理交互标准。

---

## 阶段三：Task Decomposer（Pizza Slicing 原子化拆解）
在需求澄清与原型生成后，将大需求按 **Pizza Slicing 范式** 拆解为互相独立、可独立验证的小任务：

* **颗粒度原则**：每个 Task 预计耗时 15~30 分钟，只改动 1~3 个文件。
* **分片规范 (Pizza Slicing)**：
  * **`S1-Bone` (骨架层)**：数据结构、API 入参返参契约、数据库 Migration。
  * **`S2-Muscle` (肌肉层)**：基于 `mockup.html` 原型接入真实业务逻辑与样式。
  * **`S3-Stub` (测试桩/探针)**：核心算法单测或联调探针。

---

## 阶段四：静默调用脚本落盘 (Automation)
拆解完成后，AI 必须在后台**自动执行 Python 脚本**，将任务写入看板：

1. **调用指令**：
   ```bash
   python3 .ai/scripts/board.py add "Task-001" "[S1-Bone] 任务标题" "白话验收目标描述" "涉及文件"
   ```

2. **确认写入**：检查 `.ai/tier3_status.md` 是否已正确更新。

---

## 💬 最终输出格式 (给用户的反馈)

当后台脚本与原型生成完毕后，向用户回复以下卡片：

✨ **需求规划与交互原型已生成！**

- 📄 **第二层 PRD**：[prd-v1.md](docs/modules/<module_name>/prd-v1.md)
- 🖥️ **可交互 HTML 原型**：[mockup.html](docs/modules/<module_name>/mockup.html) (👈 *可直接双击用浏览器打开体验动态交互！*)
- 🧩 **第二层模块索引**：已在 `.ai/tier2_modules.md` 追加注册
- 📋 **[Task-001]**：[S1-Bone] [数据/接口骨架] (目标: [白话目标])
- 📋 **[Task-002]**：[S2-Muscle] [业务实现] (目标: [白话目标])

---
👉 **任务已静默写入 `.ai/tier3_status.md`！**
👉 **请回复 [2] 或 “开始”，我们将自动开启 Task-001 的编写！**


