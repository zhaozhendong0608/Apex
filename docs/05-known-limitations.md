# ⚠️ Known Limitations & Evolution Roadmap (工作流已知缺陷与 4.0 演进路线图)

> 本文档记载了 Apex Vibe Coding 3.0 Standard 工作流在实际工程落地中的已知缺陷、边界极限及未来的 4.0 演进解决方案。

---

## 🌶️ 五大已知工程缺陷 (Known Limitations)

### 1. 🌵 多人 Git 协作冲突 (Git Merge Conflicts on Kanban)
- **现象**：`.ai/status.md` 与 `.ai/handover.md` 以纯文本形式存储于 Git 仓库中。多人同时在不同分支开发并打卡归档时，容易触发 Git 文件冲突。
- **根因**：当前看板机制优先服务于“单人 / 独立分支 Vibecoding 极速开发”，未引入分布式的状态解耦。
- **4.0 演进方案**：引入轻量级 CLI 或将状态机提升至独立的分支/环境变量隔离。

### 2. 🌵 缺乏强制自动化测试门禁 (Absence of Automated Test Gatekeeper)
- **现象**：任务验收 (SOP 04) 依赖用户手动输入 `4` 和白话 Checklist，缺乏强制的单元测试（`npm test` / `pytest`）或静态代码分析（Linter）门禁。
- **根因**：目前偏重于快速原型开发与白话目标终验。
- **4.0 演进方案**：在 SOP 04 验收前，强制插入自动化脚本命令执行逻辑，必须测试无报错方可标记 `DONE` 🟢。

### 3. 🌵 记忆黑匣子 Token 膨胀 (Context Memory Overload)
- **现象**：`handover.md` 采用追加模式记录。项目开发时间较长（如积攒 100+ Task）时，文件体积过大，在输入 `6` 恢复记忆时会消耗大量 Token 且容易导致大模型长上下文注意力迷失。
- **根因**：缺乏定期自动压缩与冷热记忆分级。
- **4.0 演进方案**：增加 `board.py compress` 功能，自动将超过 30 天或已完成的旧交接日志压缩为 10 行 Summary。

### 4. 🌵 Python 跨平台环境依赖风险 (Cross-Platform Python Dependency)
- **现象**：工作流强依赖系统安装有 `python3` / `python` 环境以运行 `board.py` 与 `arch.py`。若环境缺少 Python 或环境变量未配置，会导致数字指令报错。
- **根因**：确切控制脚本依赖宿主机 Python 解释器。
- **4.0 演进方案**：增加脚本的环境自适应嗅探机制，并提供纯文本 Markdown 降级 (Fallback) 手动处理方案。

### 5. 🌵 黑盒老项目祖传代码隐蔽盲区 (Legacy Code Shadow Blindspots)
- **现象**：面对隐藏在反射机制、动态加载或硬编码 SQL 中的祖传代码，仅凭前 3 层目录树与关键词搜索可能无法 100% 探查出深层连带影响。
- **根因**：静态分析对动态反射/多态运行时的局限。
- **4.0 演进方案**：引入运行时日志抓取与探针注入（Reverse Testing），以运行时数据流印证静态结构。

---

## 📊 演进路线对照表

| 已知缺陷 | 影响级别 | 暂行替代方案 | 4.0 终极演进路线 |
| :--- | :--- | :--- | :--- |
| **1. 多人 Git 冲突** | 中 | 建议在独立 Feature 分支开发，主干合并前统一整理 status | 状态机 SQLite / CLI 脱离文件版本号 |
| **2. 缺乏自动单测** | 高 | 用户在打卡 [4] 验收前手动在终端运行测试命令 | SOP 04 强插入 `npm test`/`pytest` 阻断门禁 |
| **3. 记忆库 Token 膨胀** | 中 | 定期手动清理 `.ai/handover.md` 早期旧日志 | 自动化脚本归档压缩器 (`board.py compress`) |
| **4. Python 环境依赖** | 低 | 确保本地安装 Python 3.8+ 并配置 PATH | 环境变量自适应嗅探与纯 Markdown 降级 |
| **5. 老代码隐蔽盲区** | 高 | 结合【路由逆向追踪法】与真实接口联调日志排查 | 运行时日志抓取与动态切片校验 |
