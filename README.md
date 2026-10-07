# 🚀 Apex - Universal AI Vibecoding 3.0 Standard Workflow

> **Apex** 是一个开箱即用、强约束、低门槛的通用 AI 驱动开发脚手架与工作流标准框架（Vibe Coding 3.0 Standard）。支持新项目极速开发闭环与巨型老项目破局归档。

---

## ✨ 核心特性

- **📱 极简数字指令驱动 (1~6 数字 SOP)**：无需记忆繁琐命令，通过简单的按键数字控制 AI 完成从规划、编码、排错到验收归档的全流程。
- **🧠 确定性看板与双轨记忆 (`board.py`)**：底层通过确定性 Python 脚本控制任务状态机，结合 `status.md` 焦点看板与 `handover.md` 长效黑匣子，防止 AI 幻觉与上下文丢失。
- **🛡️ 看门狗护栏与防腐规范**：
  - 最小改动原则 (Minimum Surgery Protocol，不限文件数，严禁凭 AI 想象力多写代码)
  - 零新依赖原则（授权后安装）
  - 关键第三方 API 联调日志规约（入参/返参必须可追溯）
  - 严禁自主补充保底硬编码 Mock 假数据
  - 融合 Sentinel Kernel v3.0 的 Solid 核心资产保护与 Reverse Testing 反向验证
- **🏛️ 老项目路由元数据图谱解密**：路由入口切入 ➔ 4D 元数据提取 ➔ 自动交集计算 ➔ Mermaid 全局关系图谱生成。

---

## 📂 项目目录结构

<!-- AUTO-TREE-MACRO:START -->
```plaintext
Apex/                                 # 🚀 顶层工作区 (IDE 打开的根目录)
├── 🧩 .agents/                        # 🧩 AI 专属技能包库
│   └── 📁 skills/
├── 🧠 .ai/                            # ⚙️ 统一工作流控制中心 (三层记忆金字塔)
│   ├── 🛠️ scripts/                   # 🛠️ 自动化控制与分析脚本库
│   ├── 📜 sop/                        # 📜 全套 00~06 数字 SOP 规则矩阵
│   ├── 📄 tier1_snapshot.md           # [第一层：极简快照名片层] 项目动态快照 (tier1_snapshot.md) [DEMO 示例 / 样例模板]
│   ├── 📄 tier2_legacy_arch.md        # (tier2_legacy_arch.md)
│   ├── 📄 tier2_modules.md            # [第二层：宏观业务大模块总览层] 大模块矩阵索引表 (tier2_modules.md) [DEMO 示例 / 样例模板]
│   ├── 📄 tier3_handover.md           # [第三层：微观原子任务层] 历史交接黑匣子 (tier3_handover.md) [DEMO 示例 / 样例模板]
│   └── 📄 tier3_status.md             # 📋 [第三层：微观原子任务层] 实时任务看板 (tier3_status.md) [DEMO 示例 / 样例模板]
├── 🧠 .cursorrules                    # 🧠 AI 行为约束与数字路由表
├── 🧠 .windsurfrules                  # 🧠 IDE 规则适配文件
├── 📖 README.md                       # 📖 项目门面与使用指南
├── 📖 WORKFLOW_GUIDE.md               # 📖 完整工作流与设计指南
├── 💻 base_template/                  # [业务项目名称 / Project Name]
│   ├── 🧩 .agents/                    # 🧩 AI 专属技能包库
│   ├── 🧠 .cursorrules                # 🧠 AI 行为约束与数字路由表
│   ├── 🧠 .windsurfrules              # 🧠 IDE 规则适配文件
│   ├── 📖 README.md                   # 📖 项目门面与使用指南
│   ├── 📁 backend/
│   ├── 📁 docs/                       # 📁 本工程 PRD / 架构 / 设计文档
│   └── 📁 frontend/
├── 🛠️ scripts/                       # 🛠️ 自动化控制与分析脚本库
│   └── 📁 tests/
├── 📄 workflow_guide.html
└── 📄 工作流问题待办.md                      # 📋 Apex 工作流问题诊断与优化待办清单 (Workflow Issue & Action Plan)
```
<!-- AUTO-TREE-MACRO:END -->

---

## 🔄 1~6 数字打卡使用速查

| 用户发送 | 触发 SOP | 核心动作 |
| :--- | :--- | :--- |
| **`1`** 或 `规划` | `01-sop-planning.md` | 需求对撞 (Grill-Me A/B/C 选择题) ➔ Pizza 拆解 Task ➔ 自动落盘看板 |
| **`2`** 或 `开始` | `02-sop-coding.md` | 锁定唯一 `ACTIVE` 任务 ➔ 看门狗护栏代码编写 ➔ 生成白话验证指引 |
| **`3`** 或 `报错` | `03-sop-debug.md` | 小黄鸭根因分析 ➔ 微创切口修补 ➔ 禁止伪造保底数据 |
| **`F`** 或 `快修` | `03_fast-sop-fasttrack.md` | 极速微创修补（免 board.py 审批） ➔ 直修代码 ➔ 静默刷新名片卡 |
| **`4`** 或 `验收` | `04-sop-review.md` | 白话目标终验 ➔ 静默标记 DONE ➔ 同步更新 docs/ ➔ 推荐下一 Task |
| **`5`** 或 `归档` | `05-sop-archive.md` | 对话压缩 ➔ 追加写入 handover.md ➔ 可安全关闭会话 |
| **`6`** 或 `恢复` | `06-sop-resume.md` | 新会话唤醒 ➔ 读取 status.md + handover.md ➔ 一键复活断点 |

> 💡 **关于全套 6 大 Skill 技能包与每一步的物理输出产物 (Output Artifacts) 明细**，请参考 [WORKFLOW_GUIDE.md#8-工作流全阶段skill-绑定映射与输出产物字典](file:///Users/up_dong/Documents/java_workspace/Apex/WORKFLOW_GUIDE.md#8-工作流全阶段skill-绑定映射与输出产物字典-workflow--skill-output-spec)。


---

## ⚡ 快速上手

1. **克隆本仓库**：
   ```bash
   git clone https://github.com/zhaozhendong0608/Apex.git
   ```
2. **在 AI 对话框中输入 `1`**：开始规划你的第一个功能需求！
