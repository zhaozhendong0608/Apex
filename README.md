# 🚀 Apex - Universal AI Vibecoding 3.0 Standard Workflow

> **Apex** 是一个开箱即用、强约束、低门槛的通用 AI 驱动开发脚手架与工作流标准框架（Vibe Coding 3.0 Standard）。支持新项目极速开发闭环与巨型老项目破局归档。

---

## ✨ 核心特性

- **📱 极简数字指令驱动 (0~13 数字 SOP)**：无需记忆繁琐命令，通过简单的按键数字控制 AI 完成从规划、编码、门禁、集成、验收到部署发布的工业级全流程（新增 `[0]` 总览 / `[B1]` 破局 / `[4]` CR 门禁 / `[6]` 集成 / `[7]` 验收 / `[2]` 方案 / `[8]` 部署 / `[9]` 发布 / `[B4]` 排障）。
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
Apex/                                                # 🚀 顶层工作区 (IDE 打开的根目录)
├── 🧩 .agents/                                       # 🧩 AI 专属技能包库
│   └── 📁 skills/
├── 🧠 .ai/                                           # ⚙️ 统一工作流控制中心（三层记忆金字塔）
│   ├── 🛠️ scripts/                                  # 🛠️ 自动化控制与分析脚本库
│   ├── 📜 sop/                                       # 📜 全套 16 篇 SOP（主链 0~9 · 旁路 B1~B6）
│   ├── 📄 tier1_snapshot.md                          # ① 极简快照名片（<30 行，[B6] 复活只读它）
│   ├── 📄 tier2_legacy_arch.md                       # ② 老项目切片与拓扑图谱元数据池
│   ├── 📄 tier2_modules.md                           # ② 大模块矩阵索引
│   ├── 📄 tier3_handover.md                          # ③ 交接黑匣子（append-only 事件日志）
│   └── 📄 tier3_status.md                            # ③ 任务看板状态机（仅 board.py 可写）
├── 🧠 .cursorrules                                   # 🧠 AI 行为约束与数字路由表
├── 🧠 .windsurfrules                                 # 🧠 IDE 规则适配文件
├── 📄 BEGINNER_HANDBOOK.md                           # 🚀 新手辅助开发手册（保姆级上手）
├── 📖 README.md                                      # 📖 项目门面与使用指南
├── 📖 WORKFLOW_GUIDE.md                              # 📖 完整工作流与设计指南
├── 📁 assets/
│   └── 📄 mermaid.min.js
├── 💻 base_template/                                 # 💻 业务工程脚手架（真实开发在这里）
│   ├── 🧩 .agents/                                   # 🧩 AI 专属技能包库
│   ├── 🧠 .cursorrules                               # 🧠 AI 行为约束与数字路由表
│   ├── 🧠 .windsurfrules                             # 🧠 IDE 规则适配文件
│   ├── 📖 README.md                                  # 📖 项目门面与使用指南
│   ├── 📁 backend/                                   # 后端工程
│   ├── 📁 frontend/                                  # 前端工程
│   └── 🛠️ scripts/                                  # 🛠️ 自动化控制与分析脚本库
├── 📁 docs/                                          # 📁 需求 / 设计 / 计划 / 交接文档
│   ├── 📄 01-requirements.md                         # 01 - 业务需求全景地图与 PRD 导航中心
│   ├── 📄 02-architecture.md                         # 02 - 概要设计与系统架构图
│   ├── 📄 03-database-design.md                      # 03 - 数据库设计与 ER 关系规约
│   ├── 📄 04-api-design.md                           # 04 - API 接口详细设计规范
│   ├── 📄 05-known-limitations.md                    # 工作流缺陷与改进追溯看板
│   ├── 📄 06-legacy-graph-analysis.md                # 老项目路由元数据拆解与关系图谱分析指南
│   ├── 📁 db/
│   ├── 🎨 design/                                    # UI/UX 设计规范与主题资产库
│   ├── 📁 handovers/
│   ├── 📦 modules/                                   # 业务大模块 PRD 与交互原型库
│   ├── 📁 plans/
│   ├── 📁 playbooks/
│   ├── 📁 templates/                                 # 文档与原型外壳模板库
│   ├── 📁 test-cases/
│   ├── 📄 user-manual.md                             # 项目产品使用手册
│   └── 📁 相关文件/
├── 📁 station-device-simulator/
│   ├── 📁 simulator-core/
│   ├── 📁 simulator-metadata-mysql/
│   ├── 📁 simulator-metadata-spi/
│   ├── 📁 simulator-sink-jsdb/
│   ├── 📁 simulator-sink-spi/
│   ├── 📁 simulator-starter/
│   └── 📁 simulator-ui/
├── 📁 storage/
│   └── 📁 prototypes/
├── 📁 training/
│   ├── 📄 Apex工作流-内部培训.pptx
│   ├── 📄 build_deck.py
│   ├── 📄 build_deck_v1_90min.py.bak
│   ├── 📄 customer-requirement-confirmation-flow.md  # 客户需求确认流程（一句话需求 → 基线 v0.1）
│   ├── 📄 deck_lib.py
│   ├── 📄 export_slides.applescript
│   ├── 📄 fontpath.py
│   ├── 📄 preview.py
│   └── 📄 需求确认清单模板.md                                # 需求确认清单 v0.1
├── 📄 workflow_guide.html
├── 📄 工作流问题待办.md                                     # Apex 工作流问题诊断与优化待办清单
├── 📄 需求全生命周期管理-蓝皮书.md                               # 需求与版本管理平台 · 蓝皮书 v0.2
└── 📄 需求管理平台-页面清单与线框.md                              # 需求与版本管理平台 · 页面清单与线框
```
<!-- AUTO-TREE-MACRO:END -->

---

## 🔄 0~13 数字打卡使用速查

> 本框架已对齐「数据采集（apex）」工业级全周期严谨版：在原有 `1~6` 易用闭环基础上，补齐 `00` 总览 / `0L` 破局 / `7` CR 机器门禁 / `8` 集成穿透 / `9` 双向验收 / `10` 技术方案 / `11` 部署 / `12` 发布 / `13` 生产排障，并保留 Apex 三层记忆金字塔、KI 自演进、8 大技能包强项。

| 用户发送 | 触发 SOP | 核心动作 |
| :--- | :--- | :--- |
| **`0`** 或 `总览` | `00-sop-overview.md` | 打印轨道总表（主链/交付链/旁路）与指令映射 |

**主链（1~7，推进任务状态，严格按序）**

| 指令 | 触发词 | 核心动作 |
| :---: | :--- | :--- |
| **`1`** | 规划 / 需求 | PA/PD/SRS/TC 四段（双轨并行）➔ Pizza 拆解 Task ➔ 落盘看板 |
| `1+` | 大模块 + 原型 | 模块 PRD + 交互原型 `mockup.html` |
| **`2`** | 技术方案 | 架构拓扑 + 详细施工图（TD），**契约冻结** |
| **`3`** | 编码 / 实现 | 锁定 `ACTIVE` ➔ TDD 红绿重构 + 看门狗 8 底线 |
| **`4`** | 审查 / CR | `gate.py --staged` 机器门禁（6 项 + SQL 配对 + linter）➔ `[LGTM]` |
| **`5`** | 评审 | 人工架构/契约评审（机器判不了的部分）➔ **不改状态、不归档** |
| **`6`** | 集成 / 联调 | 执行 TC 用例矩阵 + 跨服务契约联调 + E2E 离线穿透 |
| **`7`** | 验收 / 标绿 | **唯一归档出口**：`verify` ➔ 人工放行 ➔ `complete --accept` |

**交付链（8~9，里程碑级）**

| 指令 | 触发词 | 核心动作 |
| :---: | :--- | :--- |
| **`8`** | 部署 / 上线 | 离线容器编排、`127.0.0.1` 收敛、幂等脚本、健康探针 |
| **`9`** | 提交 / 发版 / 回滚 | 语义化原子提交、Tag、DDL 回滚绑定、三级生产回滚 |

**旁路（B1~B6，随时插入，不推进状态）**

| 指令 | 触发词 | 核心动作 |
| :---: | :--- | :--- |
| **`B1`** | 老项目 / 接手 | 逆向考古、切片图谱、破局归档 |
| **`B2`** | 快修 / 小改 | ≤20 行微创（免状态机）➔ 直修 ➔ 漂移检查 |
| **`B3`** | 报错 / 崩了 | 开发期小黄鸭根因分析 ➔ 微创修补（禁假数据） |
| **`B4`** | 线上故障 / OOM | 生产排障：日志穿透 + 现场快照 + 防御单测 |
| **`B5`** | 归档 / 下班 | 压缩会话 ➔ 踩坑卡片 ➔ `docs/handovers/` |
| **`B6`** | 继续 / 恢复 | 读 `tier1` 名片 ➔ 一键复活断点 |

## 🪙 Token 预算与按需加载（v2 精简后）

工作流不是"每次都把全部规则灌进上下文"，而是**分层加载**：

| 层 | 内容 | 什么时候进上下文 | ≈token |
| :--- | :--- | :--- | :---: |
| **L0 常驻** | `.cursorrules` / `.windsurfrules`：三轨路由表 + 5 铁律 + 加载策略 | 每轮 | **~1,200** |
| **L1 单篇** | 命中的那一篇 `.ai/sop/*.md` | 只读命中篇 | **~490**（300~760） |
| **L2 记忆** | `tier1_snapshot.md`（默认）/ `tier3_status.md`（改状态时） | 按需 | 416 / 577 |
| **L3 深水** | `WORKFLOW_GUIDE.md`(12k)、`BEGINNER_HANDBOOK.md`(4k)、Skill 的 `SKILL.md` | 仅显式要求时 | 大，日常不读 |
| **机器侧** | `_capability-baseline.md`：**只给 `gate.py` 读，AI 不读** | 不占对话 token | — |

**典型一轮固定开销**（编码）：`1,200 + 486 + 577 ≈ 2,260 token`。
精简前是 `2,241 + 1,330 + 577 ≈ 4,150` —— **每轮省约 50%**，40 轮会话省 ~8 万 token。

> 📏 16 篇 SOP 合计从 **19,222 → 7,780 token（−60%）**，最重一篇从 2,889 → 757。
> 🔒 精简只删字不删能力：**91 个能力点**冻结在 [`.ai/sop/_capability-baseline.md`](.ai/sop/_capability-baseline.md)，
> 由 `python3 .ai/scripts/gate.py --sop` 逐条比对，缺一个即 `[BLOCKING]`。

> 💡 **关于全套 8 大 Skill 技能包与每一步的物理输出产物 (Output Artifacts) 明细**，请参考 [WORKFLOW_GUIDE.md §8 工作流全阶段 Skill 绑定映射](WORKFLOW_GUIDE.md#-8-工作流全阶段skill-绑定映射与输出产物字典-workflow--skill-output-spec)；
> 🧭 **要开一个新模块？直接看 [新模块落地全流程 Playbook](docs/playbooks/new-module-playbook.md)**（节点 → 产出物 → 验收人 → **不合格怎么返工**，含流程图）。
> 📖 **名词看不懂？查 [专有名词表](docs/playbooks/glossary.md)**（68 个术语，含使用场景，可搜索）。
> 🔒 **门禁三层兜底**：本地 pre-commit 钩子 → CI（`.github/workflows/apex-gate.yml`）→ `[7]` 终验。
> 🧹 **代码规约可插拔**：`.ai/scripts/linter_py.py`（零依赖 AST 检查：裸 except / 吞异常 / 可变默认参数 / eval）。
> 🚀 **第一次使用这套工作流？请直接读 [新手辅助开发手册](BEGINNER_HANDBOOK.md)**，它是给「完全没用过这套流程的开发者」写的保姆级上手文档。


---

## ⚡ 快速上手

1. **克隆本仓库**：
   ```bash
   git clone https://github.com/zhaozhendong0608/Apex.git
   ```
2. **安装门禁钩子（只做一次）**：
   ```bash
   python3 .ai/scripts/install_hooks.py
   ```
   装完后每次 `git commit` 都会自动跑门禁；不装的话，门禁只是建议。
3. **在 AI 对话框中输入 `1`**：开始规划你的第一个功能需求！
