# 🧷 SOP 能力点冻结基线 (Capability Baseline v1)

> ⚠️ **这份文件是"功能不缩水"的合同，不是文档。**
> 每一次对 SOP 的精简/重构，**只允许删字，不允许删能力点**。
> 任何 ID 从这里消失，都必须先回答：「这个能力被哪条规则替代了？」
>
> **校验方式**：`python3 .ai/scripts/gate.py --sop`
> 它会逐个比对「本清单的 ID」与「各 SOP 文件里 `## 🧷 能力点` 声明的 ID」，缺一个即 `[BLOCKING]`。
>
> **新增能力**：先在对应 SOP 写规则 → 在此表追加一行 → 跑一次 `gate.py --sop` 确认对齐。

---

## 📊 总览

<!-- AUTO-CAP-SUMMARY:START -->
| 归属 SOP | 能力点数 |
| :--- | :---: |
| `00-sop-overview.md` | 6 |
| `B1-sop-legacy.md` | 5 |
| `B2-sop-fasttrack.md` | 4 |
| `01-sop-planning.md` | 8 |
| `02-sop-tech-design.md` | 5 |
| `03-sop-coding.md` | 6 |
| `04-sop-code-review.md` | 8 |
| `05-sop-review.md` | 5 |
| `06-sop-integration.md` | 7 |
| `07-sop-acceptance.md` | 6 |
| `B3-sop-debug.md` | 5 |
| `B5-sop-archive.md` | 5 |
| `B6-sop-resume.md` | 3 |
| `08-sop-deploy.md` | 6 |
| `09-sop-git-release.md` | 6 |
| `B4-sop-troubleshoot.md` | 6 |
| **合计** | **91** |
<!-- AUTO-CAP-SUMMARY:END -->

---

## 📋 能力点清单

| ID | 能力点（可测） | 归属文件 | 原出处（压缩前） |
| :--- | :--- | :--- | :--- |
| `C-0-01` | 六域划分与指令映射 | 00-sop-overview.md | 核心目标 / 快捷指令映射表 |
| `C-0-02` | 双轨并行与延迟汇合 | 00-sop-overview.md | 双轨并行说明 |
| `C-0-03` | S/M/L 分级与裁剪规则 | 00-sop-overview.md | 任务体量分级 |
| `C-0-04` | 复合批处理不削落盘资产 | 00-sop-overview.md | 流水线合并批处理 |
| `C-0-05` | 模块化文档落盘路径 | 00-sop-overview.md | 模块化文档管理机制 |
| `C-0-06` | 单一事实源与归档权唯一 | 00-sop-overview.md | 归档权唯一性说明 |
| `C-B1-01` | 前 3 层骨架鸟瞰防溢出 | B1-sop-legacy.md | 3 大防溢出护栏 |
| `C-B1-02` | 黑盒线索解密（路由/表名/菜单） | B1-sop-legacy.md | 模式 B / C |
| `C-B1-03` | 四维元数据注册 | B1-sop-legacy.md | 模式 B 第 4 步 |
| `C-B1-04` | Mermaid 拓扑与跨模块共享表推断 | B1-sop-legacy.md | 模式 B / arch.py analyze |
| `C-B1-05` | 行为锁死探针 | B1-sop-legacy.md | 模式 B 第 3 步 |
| `C-B2-01` | ≤20 行单文件微创 | B2-sop-fasttrack.md | 适用场景 / 护栏 |
| `C-B2-02` | 免状态机免门禁 | B2-sop-fasttrack.md | 快轨看门狗护栏 |
| `C-B2-03` | Schema 漂移检查与 DLD 反哺 | B2-sop-fasttrack.md | 执行流第 3 步 |
| `C-B2-04` | 极简回复格式 | B2-sop-fasttrack.md | 极简回复格式 |
| `C-1-01` | 8 维隐性扫描与白话提问 | 01-sop-planning.md | 1. 深度需求发现 |
| `C-1-02` | 四态管理（已确认/推断/暂定/待确认） | 01-sop-planning.md | 1. 动态状态管理 |
| `C-1-03` | PA 三层拆解（Epic/Story/UseCase） | 01-sop-planning.md | 2. PA |
| `C-1-04` | PRD + 原型与纯后端豁免 | 01-sop-planning.md | 2. PD / Backend-Only |
| `C-1-05` | SRS 研发轨（Grill-Me 对撞） | 01-sop-planning.md | 3. 研发轨 |
| `C-1-06` | TC 三类用例矩阵 | 01-sop-planning.md | 3. 测试轨 |
| `C-1-07` | Pizza 三层切片 S1/S2/S3 | 01-sop-planning.md | 4. Pizza Slicing |
| `C-1-08` | 看板登记与定级 | 01-sop-planning.md | 5. 写入看板 |
| `C-2-01` | 全局架构与模块施工图双档 | 02-sop-tech-design.md | 技术设计资产架构 |
| `C-2-02` | 模式 A/B 自适应深度 | 02-sop-tech-design.md | 自适应设计模式 |
| `C-2-03` | 契约冻结（字段/URL/错误码） | 02-sop-tech-design.md | 架构看门狗硬门禁 |
| `C-2-04` | 表结构索引与 DDL/rollback 配对 | 02-sop-tech-design.md | 骨架第 4 节 |
| `C-2-05` | 时序图与端到端流 | 02-sop-tech-design.md | 骨架第 1.2 节 |
| `C-3-01` | 看门狗 8 底线 | 03-sop-coding.md | 阶段一 8 条底线 |
| `C-3-02` | TDD 红绿重构微循环 | 03-sop-coding.md | 阶段二 Red-Green-Refactor |
| `C-3-03` | 公共资产影响自判 | 03-sop-coding.md | 底线 8 + 阶段三 |
| `C-3-04` | 禁假 Mock 与真联调日志 | 03-sop-coding.md | 底线 5 / 6 |
| `C-3-05` | 最小改动与代码落盘 | 03-sop-coding.md | 底线 1~4 |
| `C-3-06` | 白话验证指引卡片 | 03-sop-coding.md | 阶段四 Checklist |
| `C-4-01` | 双层审查分工（机器+人工） | 04-sop-code-review.md | 双层审查机制 |
| `C-4-02` | gate.py 能力边界（8 项） | 04-sop-code-review.md | gate.py v2 能力边界 |
| `C-4-03` | 5 维人工检查清单 | 04-sop-code-review.md | 代码审查 5 大维度 |
| `C-4-04` | DLD 契约一致性核验 | 04-sop-code-review.md | 维度 2 契约一致性 |
| `C-4-05` | 健壮性与并发安全 | 04-sop-code-review.md | 维度 3 |
| `C-4-06` | 洁净度与资源释放 | 04-sop-code-review.md | 维度 4 |
| `C-4-07` | 单测真红真绿与反向破坏 | 04-sop-code-review.md | 维度 5 |
| `C-4-08` | BLOCKING / LGTM 判定与合入红线 | 04-sop-code-review.md | 评审判定 / 纪律红线 |
| `C-5-01` | 三门禁权责划分 | 05-sop-review.md | 三个门禁的分工 |
| `C-5-02` | 架构分层评审 | 05-sop-review.md | 第二步 |
| `C-5-03` | 契约逐字对齐 | 05-sop-review.md | 第三步 |
| `C-5-04` | 可维护性评审 | 05-sop-review.md | 第四步 |
| `C-5-05` | 不改状态不归档红线 | 05-sop-review.md | 三条红线 |
| `C-6-01` | TC 矩阵汇合执行 | 06-sop-integration.md | 阶段流转与双轨汇合 |
| `C-6-02` | 跨服务契约与序列化校验 | 06-sop-integration.md | L2 契约 |
| `C-6-03` | 持久化与事务真穿透 | 06-sop-integration.md | L2 持久化 |
| `C-6-04` | 队列吞吐与背压 | 06-sop-integration.md | L2 中间件 |
| `C-6-05` | E2E 全链路与 UI 保真 | 06-sop-integration.md | L3 E2E |
| `C-6-06` | 5 铁律（禁假数据/单源/降级/离线/自愈） | 06-sop-integration.md | IT/E2E 5 大铁律 |
| `C-6-07` | 0 Error 构建门禁 | 06-sop-integration.md | 构建验证命令 |
| `C-7-01` | 4 维终验清单 | 07-sop-acceptance.md | 双向核对 Checklist |
| `C-7-02` | verify 四项机器核验 | 07-sop-acceptance.md | 自动化归档 第一步 |
| `C-7-03` | 人工放行 `--accept` | 07-sop-acceptance.md | 自动化归档 第二步 |
| `C-7-04` | 原子归档四动作 | 07-sop-acceptance.md | 自动化归档 第三步 |
| `C-7-05` | SQL 回滚审计 | 07-sop-acceptance.md | 维度 3 |
| `C-7-06` | 文档资产同步审计 | 07-sop-acceptance.md | 维度 2 |
| `C-B3-01` | Debug 三不原则 | B3-sop-debug.md | 阶段一 |
| `C-B3-02` | 堆栈定位到精确行 | B3-sop-debug.md | 第一步 |
| `C-B3-03` | 小黄鸭根因四问 | B3-sop-debug.md | 第一步第 3 点 |
| `C-B3-04` | 最小切口修复 | B3-sop-debug.md | 第二步 |
| `C-B3-05` | 禁假数据掩盖与日志克制 | B3-sop-debug.md | 三不原则 4 |
| `C-B5-01` | 全盘状态扫描 | B5-sop-archive.md | 第一步 |
| `C-B5-02` | 垃圾清理与核心文件提炼 | B5-sop-archive.md | 第二步 |
| `C-B5-03` | KI 踩坑卡片反哺 | B5-sop-archive.md | 第二.五步 |
| `C-B5-04` | Handover 文档落盘 | B5-sop-archive.md | 第三步 |
| `C-B5-05` | 与 `[7]` 防重复归档 | B5-sop-archive.md | 与 [7] 的分工 |
| `C-B6-01` | 四件套按序读取 | B6-sop-resume.md | 第一步 |
| `C-B6-02` | 三场景断点定位 | B6-sop-resume.md | 第二步 |
| `C-B6-03` | 复活卡片与自动切入 | B6-sop-resume.md | 第三步 |
| `C-8-01` | 零公网离线镜像 | 08-sop-deploy.md | 铁律 1 |
| `C-8-02` | 非 root 最小权限 | 08-sop-deploy.md | 铁律 2 |
| `C-8-03` | 127.0.0.1 端口收敛 | 08-sop-deploy.md | 铁律 3 |
| `C-8-04` | 幂等与卸载二次确认 | 08-sop-deploy.md | 铁律 4 |
| `C-8-05` | 健康探针与启动时序 | 08-sop-deploy.md | 铁律 5 |
| `C-8-06` | 脚本规范与升级备份 | 08-sop-deploy.md | 离线运维脚本规范 |
| `C-9-01` | 原子提交 | 09-sop-git-release.md | Git 铁律 1 |
| `C-9-02` | 敏感信息过滤 | 09-sop-git-release.md | Git 铁律 2 |
| `C-9-03` | DDL 与代码版本绑定 | 09-sop-git-release.md | Git 铁律 3 |
| `C-9-04` | Conventional Commits | 09-sop-git-release.md | 提交规范 |
| `C-9-05` | Tag 与 CHANGELOG | 09-sop-git-release.md | 发布流程 |
| `C-9-06` | 三级回滚 | 09-sop-git-release.md | 生产应急回滚 |
| `C-B4-01` | P0-P2 定级与影响面 | B4-sop-troubleshoot.md | Step 1 |
| `C-B4-02` | 日志与链路穿透 | B4-sop-troubleshoot.md | Step 2 |
| `C-B4-03` | 现场快照保证据 | B4-sop-troubleshoot.md | 铁律 2 |
| `C-B4-04` | 线程/队列/网络深探 | B4-sop-troubleshoot.md | Step 3 |
| `C-B4-05` | 微创修复 + 防御单测 | B4-sop-troubleshoot.md | Step 4 / 铁律 3 |
| `C-B4-06` | 回滚衔接 | B4-sop-troubleshoot.md | Step 4 末 |
