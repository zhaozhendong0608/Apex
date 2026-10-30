# 🎬 实战 Demo 剧本（自动注入 workflow_guide.html）

> 本文件是 HTML 里两个 Demo 面板的**唯一事实源**。
> 改完运行 `python3 .ai/scripts/sync_tree.py` 即可同步，**不要直接改 HTML**。

<!-- DEMO | panel-demo-new | 💡 Demo 1：从 0 到 1 走完主链 [1]→[7] | 主链全流程 | 一个新功能从一句话需求到标绿归档，一共 8 步。旁路（如 [B3] 排错）只在出问题时插入，不算主链步骤。 -->
<!-- STEP | Step 1 | [1] 规划：一句话需求 → 选择题对撞 → 拆 Task -->
- YOU: `1 我想给系统加个用户角色权限管理`
- AI: 明白！请做个单选决策：👉 **A（推荐）** 标准 RBAC（用户→角色→菜单权限）；👉 **B** 数据级细粒度（按部门/区域隔离）。回 A 或 B，我收敛需求基线。
- OUT: 需求基线 `docs/01-requirements.md`
- OUT: `board.py add` 写入 `[Task-001] [S1-Bone] 权限骨架`
<!-- /STEP -->
<!-- STEP | Step 2 | [1+] 大模块：PRD + 交互原型 -->
- YOU: `选 A`
- AI: 已生成模块 PRD 与可点击原型。**请你先扫一眼业务规则对不对**，写错现在改最便宜。
- OUT: `docs/modules/auth/prd-auth-v1.md`
- OUT: `mockup.html` 原型（含空/加载/异常三态）
<!-- /STEP -->
<!-- STEP | Step 3 | [2] 技术方案：契约冻结（写码前最后一关） -->
- YOU: `2`
- AI: 接口字段、表结构、时序流已写进施工图并冻结。**之后改字段必须先把文档改了再改代码。**
- OUT: `docs/design/auth/detail.md`
- OUT: `V1.0.1__auth.sql` + `rollback_V1.0.1__auth.sql` 配对
<!-- /STEP -->
<!-- STEP | Step 4 | [3] 编码：TDD 红 → 绿 → 重构 -->
- YOU: `3`
- AI: 先写了一条必失败的测试（红灯），再用最小实现让它变绿，最后重构仍绿。改动 3 个文件，无新增依赖。
- OUT: 源码 + 单测
- OUT: TDD 三态勾选卡片
<!-- /STEP -->
<!-- STEP | Step 5 | [4] CR 门禁：机器说了算 -->
- YOU: `4`
- AI: 跑 `gate.py --staged`：6 项看门狗 + SQL 配对 + linter → **[LGTM]**。若是 [BLOCKING] 必须修完重跑，不许合入。
- OUT: 门禁报告（LGTM / BLOCKING + 退出码）
<!-- /STEP -->
<!-- STEP | Step 6 | [5] 人工评审：机器判不了的部分 -->
- YOU: `5`
- AI: 分层落点对、字段与文档逐字一致、命名能看懂 → **APPROVED**。本步**不改看板状态**。
- OUT: 评审报告（APPROVED / CHANGES-REQUESTED）
<!-- /STEP -->
<!-- STEP | Step 7 | [6] 集成穿透：真实环境下跑一遍 -->
- YOU: `6`
- AI: 脱离 Mock，在近真实拓扑跑 TC 矩阵 + E2E，前后端构建 0 Error。
- OUT: IT/E2E 验证报告
- OUT: 构建凭据
<!-- /STEP -->
<!-- STEP | Step 8 | [7] 验收归档：唯一标绿出口 -->
- YOU: `7`
- AI: 先 `board.py verify` 拿机器凭证，再给你 4 维清单确认，最后 `complete --accept` 原子归档。
- OUT: 任务标绿 + 黑匣子记录
- OUT: `tier1` 名片刷新 + 自动接下一个 Task
<!-- /STEP -->
- NOTE: ↩ 旁路什么时候用（不占主链步骤）：编码时 [B3] 报错排错、小改动 [B2] 快修、接手老项目 [B1]——它们随时可进，但**不改变 ACTIVE 状态**；想收工打 [B5]，第二天 [B6] 复活。
<!-- /DEMO -->

<!-- DEMO | panel-demo-legacy | 🏛️ Demo 2：修改 / 维护老模块 | 旁路 B1 → 回主链 | 接手老代码先用 [B1] 建地图（只看前 3 层，防上下文爆炸），再回到主链动刀。**先锁行为，再改行为。** -->
<!-- STEP | Step 1 | [B1] 逆向考古：顺着路由建地图（旁路） -->
- YOU: `B1 接手一个老项目，先看看订单模块`
- AI: 只扫前 3 层目录与依赖，定位到 `/order/create` → `OrderService` → `t_order`，自动绘出关系图，并生成一个**行为锁死探针**把老逻辑钉住。
- OUT: `.ai/tier2_legacy_arch.md` 切片地图
- OUT: `scripts/tests/test_legacy_order.py` 锁死探针
<!-- /STEP -->
<!-- STEP | Step 2 | [1] 只拆本次改动（不重写整个模块） -->
- YOU: `1 给订单创建加一个库存校验`
- AI: 只拆 2 个原子 Task，不动无关链路。老模块改动**范围越小越安全**。
- OUT: 2 个 Task 入看板
- OUT: 变更点测试用例
<!-- /STEP -->
<!-- STEP | Step 3 | [3] 单链路防腐修改 -->
- YOU: `3`
- AI: 只在 `OrderService` 创建链路上加校验，旧分支一律不动；锁死探针仍全绿 → 说明没改崩旧行为。
- OUT: 最小改动源码
- OUT: 锁死探针回归结果
<!-- /STEP -->
<!-- STEP | Step 4 | [4] 门禁 + [7] 验收 -->
- YOU: `4` → `7`
- AI: 门禁 [LGTM] 后走 `verify` + `complete --accept`，任务标绿。
- OUT: 门禁报告
- OUT: 看板标绿 + 黑匣子
<!-- /STEP -->
- NOTE: ⚡ 更小的改动走 [B2] 快修：改文案、换颜色、加判空 → 直接改，**不进状态机、不跑门禁**；但碰到接口字段或表结构，必须顺手同步设计文档（漂移检查）。
<!-- /DEMO -->
