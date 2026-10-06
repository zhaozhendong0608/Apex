# 🎯 SOP: 需求规划与任务拆解 (01-sop-planning)

## 📌 核心目标
作为需求规划中枢，自动加载 `@requirement-discovery` 技能，通过 8 维白话探查消除歧义并收敛需求基线；同步更新 `docs/01-requirements.md` 需求全景 MAP；若为大模块联动调用 `archetype-architect` 专家 Skill 产出原型与架构图；最终拆解 Task 并**静默调用 Python 脚本写入看板**。

---

## 🤖 AI 执行流程与产出规约

### 1. 深度需求发现与澄清 (加载 .agents/skills/requirement-discovery/SKILL.md)
当用户提出新需求或功能想法时，按以下逻辑探查：
- **内部 8 维扫描**：推演【正常/失败/边界/生命周期/上下游/规模/误操作/重启恢复】。
- **白话场景提问**：严禁使用专业技术术语（如幂等、一致性等），将技术考量转化为用户直观感知的白话场景进行询问。
- **动态状态管理**：维护 `已确认` | `AI推断` | `暂定` | `待确认` 状态，对用户无法决策的项提供推荐默认值并标记为 `暂定`。
- **收敛需求基线**：输出结构化的《需求基线总结》。

---

### 2. 静态工程资产沉淀与分流规划 (docs/01-requirements.md)
需求基线达成后，自动更新静态工程文档：
- **沉淀至需求全景地图**：自动更新 `docs/01-requirements.md` 中的 Mermaid 思维导图与业务模块矩阵表。
- **普通需求/增量功能**：直接切入原子化 Task 拆解。
- **全新大模块/复杂系统**：联动调用 `.agents/skills/archetype-architect/SKILL.md`，落盘 `docs/modules/<module_name>/prd-<module_name>-v1.md` 与 `mockup.html` 交互原型。

---

### 3. Pizza Slicing 原子化拆解 (15~30 分钟/Task)
将大需求收敛拆解为互相独立、可独立验证的三层原子任务：
- `S1-Bone` (骨架层)：数据结构、API 契约、数据库 Migration。
- `S2-Muscle` (肌肉层)：基于 `mockup.html` 或现有页面接入真实业务逻辑与统一样式。
- `S3-Stub` (测试桩)：核心算法单测或联调探针。

---

### 4. 静默调用脚本写入看板
```bash
python3 .ai/scripts/board.py add "Task-001" "[S1-Bone] 标题" "白话目标" "涉及文件"
```

---

## 💬 最终回复卡片

✨ **需求探查与规划已完成：**
- 🗺️ **需求全景地图**：已同步更新至 `docs/01-requirements.md`
- 📄 **PRD 文档**：`docs/modules/<module_name>/prd-v1.md` *(大模块产出)*
- 🖥️ **交互原型**：[mockup.html](docs/modules/<module_name>/mockup.html) (*大模块产出*)
- 📋 **[Task-001]**：[S1-Bone] [数据/接口骨架] (目标: [白话目标])

👉 **任务已写入看板，回复 [2] 开始编写代码！**
