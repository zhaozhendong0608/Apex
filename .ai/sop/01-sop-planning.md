# 🎯 SOP: 需求规划与任务拆解 (01-sop-planning)

## 📌 核心目标
通过主动拷问 (Grill-Me) 消除需求歧义，确定性生成第二层 PRD 与可交互 HTML 原型 `mockup.html`，按 Pizza Slicing 拆解任务并静默运行 `board.py add` 写入看板。

---

## 🤖 AI 执行流程与产出规约

### 1. Grill-Me 对撞与依赖审计
* 检查是否触碰 Solid 核心资产（Schema/API契约/.env）。
* 一次性抛出 2~4 个定型选择题（A/B/C 选项）引导用户做决策。

### 2. 第二层大模块 PRD 与【可交互 HTML 原型】确定性产出 (`1+ 模块名`)
当使用 `1+ [模块名]` 时，静默生成以下三大产出物：
- **第二层 PRD**：在 `docs/modules/<module_name>/prd-<module_name>-v1.md` 生成需求与 API 契约文档（含入参返参）。
- **第二层模块索引**：在 `.ai/tier2_modules.md` 追加注册该大模块。
- **🖥️ 可交互 HTML 原型 (`mockup.html`)**：在 `docs/modules/<module_name>/mockup.html` 生成可点击测试的原型页面。
  - **外壳继承死命令**：**必须强行继承 `docs/templates/base-shell-template.html` 布局外壳**（Header+Sidebar），严禁改变 App 大布局！仅填充 `Main Content` 区域。
  - **交互要求**：具备真实前端交互（弹窗 Modal 开合、Tab 切换、表单空校验与模拟成功提示）。双击即用。

### 3. Pizza Slicing 原子化拆解 (15~30 分钟/Task)
- `S1-Bone` (骨架层)：数据结构、API 契约、数据库 Migration。
- `S2-Muscle` (肌肉层)：基于 `mockup.html` 接入真实业务逻辑与统一样式。
- `S3-Stub` (测试桩)：核心算法单测或联调探针。

### 4. 静默调用脚本落盘
```bash
python3 .ai/scripts/board.py add "Task-001" "[S1-Bone] 标题" "白话目标" "涉及文件"
```

---

## 💬 最终回复卡片
✨ **需求规划与交互原型已生成：**
- 📄 PRD：`docs/modules/<module_name>/prd-v1.md`
- 🖥️ 交互原型：[mockup.html](docs/modules/<module_name>/mockup.html) (*双击用浏览器打开体验动态交互*)
- 📋 **[Task-001]**：[S1-Bone] [数据/接口骨架] (目标: [白话目标])
👉 **已写入 `.ai/tier3_status.md`，回复 [2] 开始编写代码！**
