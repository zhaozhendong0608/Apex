# 🚀 通用工程 Base 脚手架 (Vibe Coding 3.0 Standard)

本工程基于 **Vibe Coding 3.0 自动化工程体系** 打造，集成了确定的任务状态机、自动看板控制脚本、数字 SOP 规约矩阵以及老项目破局归档机制。

---

## ⚡ 数字指令速查表 (Quick SOP Router)

在对话框中输入对应的数字或关键词，AI 将自动调阅对应 SOP 规约：

- **`[1]` 或 `规划`**：启动 Grill-Me 需求拷问与原子任务拆解（自动写入 `.ai/status.md`）。
- **`[2]` 或 `编码`**：锁死当前唯一的 `IN_PROGRESS` 任务，按看门狗规约单焦点开发。
- **`[3]` 或 `排错`**：开启小黄鸭根因分析与微创代码修补。
- **`[F]` 或 `快修`**：极速微创修补（免状态机审批），直修代码并静默刷新名片卡。
- **`[4]` 或 `验收`**：目标终验、写审分离复核，静默标记任务为 `DONE`。
- **`[5]` 或 `归档`**：压缩对话上下文，将改动与架构决策追加写入 `.ai/handover.md`。
- **`[6]` 或 `恢复`**：新窗口极速复活记忆，一键无缝续传接续开发。

---

## 📁 目录结构说明

```plaintext
my-project/
├── .cursorrules               # AI 行为约束与数字路由总纲
├── .gitignore                 # 垃圾与敏感文件过滤规约
├── .env.example               # 环境变量占位模版
├── README.md                  # 本说明文档
│
├── .ai/                       # 🧠 AI 动态协作与状态控制中心
│   ├── status.md              # 实时任务看板
│   ├── handover.md            # 历史交接黑匣子 (追加式 Changelog)
│   ├── legacy_arch.md         # (老项目专有) 老代码模块与接口切片索引库
│   ├── scripts/
│   │   └── board.py           # 确定性状态修改 Python 脚本
│   ├── sop/                   # 数字 SOP 规则矩阵
│   │   ├── 01-sop-planning.md # [1] 需求拆解与 Grill-Me
│   │   ├── 02-sop-coding.md   # [2] 单焦点护栏编码
│   │   ├── 03-sop-debug.md    # [3] 小黄鸭探针排错
│   │   ├── 03_fast-sop-fasttrack.md # [F] ⚡ 极速微创修补 (免状态机)
│   │   ├── 04-sop-review.md   # [4] 目标终验与文档更新
│   │   ├── 05-sop-archive.md  # [5] 上下文归档
│   │   └── 06-sop-resume.md   # [6] 🛟 续航重连
│   └── templates/             # 软著与使用手册模版
│
├── docs/                      # 📁 静态工程架构与软著资产库
│   ├── 01-requirements.md     # 🗺️ 需求全景 MAP 导航中心
│   ├── 02-architecture.md     # 架构设计与模块关系图
│   ├── 03-database-design.md  # 数据库设计与 ER 图
│   ├── 04-api-design.md       # 接口详细设计
│   ├── modules/               # 📦 业务模块化 PRD 文件夹
│   │   ├── auth/              # 认证与权限 PRD
│   │   └── order/             # 订单交易 PRD
│   └── user-manual.md         # 产品使用手册
│
└── src/                       # 业务源码目录
```

---

## 🛠️ 项目启动与使用

1. **新项目使用**：直接拷贝本工程结构作为起始脚手架，发送 `1` 给 AI 开始需求规划。
2. **老项目接入**：将 `.cursorrules`、`.ai/`、`docs/` 追加放入老项目根目录，发送 `1` 或让 AI 跑“老项目破局四步法”建立架构索引。
