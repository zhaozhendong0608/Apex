# 🚀 Apex - Universal AI Vibecoding 3.0 Standard Workflow

> **Apex** 是一个开箱即用、强约束、低门槛的通用 AI 驱动开发脚手架与工作流标准框架（Vibe Coding 3.0 Standard）。支持新项目极速开发闭环与巨型老项目破局归档。

---

## ✨ 核心特性

- **📱 极简数字指令驱动 (1~6 数字 SOP)**：无需记忆繁琐命令，通过简单的按键数字控制 AI 完成从规划、编码、排错到验收归档的全流程。
- **🧠 确定性看板与双轨记忆 (`board.py`)**：底层通过确定性 Python 脚本控制任务状态机，结合 `status.md` 焦点看板与 `handover.md` 长效黑匣子，防止 AI 幻觉与上下文丢失。
- **🛡️ 看门狗护栏与防腐规范**：
  - 单次修改 ≤ 3 个文件
  - 零新依赖原则（授权后安装）
  - 关键第三方 API 联调日志规约（入参/返参必须可追溯）
  - 严禁自主补充保底硬编码 Mock 假数据
  - 融合 Sentinel Kernel v3.0 的 Solid 核心资产保护与 Reverse Testing 反向验证
- **🏛️ 老项目破局四步法**：鸟瞰全局 ➔ 建立索引 ➔ 单链路切片 ➔ 逆向考古。

---

## 📂 项目目录结构

```plaintext
Apex/
├── .cursorrules               # 🧠 AI 行为约束与数字路由总纲 (Cursor)
├── .windsurfrules             # 🧠 IDE 规则适配文件 (Windsurf)
├── .gitignore                 # 🔒 标准过滤规约
├── .env.example               # 🔒 环境变量示例
├── README.md                  # 📖 项目门面与使用指南
├── WORKFLOW_GUIDE.md          # 📖 完整工作流与设计指南
│
├── .ai/                       # 🧠 AI 动态协作与状态控制中心
│   ├── status.md              # 📊 任务看板 (初始通用模版)
│   ├── handover.md            # 📦 历史交接记忆卡 (初始通用模版)
│   ├── legacy_arch.md         # 🏛️ 老代码切片索引模版
│   ├── scripts/
│   │   └── board.py           # 🛠️ 确定性 Python 看板控制脚本
│   ├── sop/                   # 📜 1~6 号数字 SOP 规约矩阵
│   │   ├── 01-sop-planning.md # [1] 需求规划与 Grill-Me 对撞
│   │   ├── 02-sop-coding.md   # [2] 编码开发与护栏控制
│   │   ├── 03-sop-debug.md    # [3] 报错排错与微创修复
│   │   ├── 04-sop-review.md   # [4] 目标验收与文档同步
│   │   ├── 05-sop-archive.md  # [5] 上下文归档 (写 handover.md)
│   │   └── 06-sop-resume.md   # [6] 🛟 续航重连 (一键复活)
│   └── templates/             # 软著与使用手册模版
│
├── docs/                      # 📁 静态工程架构与软著资产库
│   ├── 01-requirements.md     # 🗺️ 需求全景 MAP 导航中心
│   ├── 02-architecture.md     # 架构设计与模块关系图
│   ├── 03-database-design.md  # 数据库设计与 ER 图
│   └── 04-api-design.md       # 接口详细设计
│
└── base_template/             # 📦 纯净脚手架模版备份包
```

---

## 🔄 1~6 数字打卡使用速查

| 用户发送 | 触发 SOP | 核心动作 |
| :--- | :--- | :--- |
| **`1`** 或 `规划` | `01-sop-planning.md` | 需求对撞 (Grill-Me A/B/C 选择题) ➔ Pizza 拆解 Task ➔ 自动落盘看板 |
| **`2`** 或 `开始` | `02-sop-coding.md` | 锁定唯一 `ACTIVE` 任务 ➔ 看门狗护栏代码编写 ➔ 生成白话验证指引 |
| **`3`** 或 `报错` | `03-sop-debug.md` | 小黄鸭根因分析 ➔ 微创切口修补 ➔ 禁止伪造保底数据 |
| **`4`** 或 `验收` | `04-sop-review.md` | 白话目标终验 ➔ 静默标记 DONE ➔ 同步更新 docs/ ➔ 推荐下一 Task |
| **`5`** 或 `归档` | `05-sop-archive.md` | 对话压缩 ➔ 追加写入 handover.md ➔ 可安全关闭会话 |
| **`6`** 或 `恢复` | `06-sop-resume.md` | 新会话唤醒 ➔ 读取 status.md + handover.md ➔ 一键复活断点 |

---

## ⚡ 快速上手

1. **克隆本仓库**：
   ```bash
   git clone https://github.com/zhaozhendong0608/Apex.git
   ```
2. **在 AI 对话框中输入 `1`**：开始规划你的第一个功能需求！
