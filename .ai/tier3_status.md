# 📋 任务看板 (Task Board)

> ⚠️ **本文件是任务状态机的唯一事实源，只允许通过 `python3 .ai/scripts/board.py` 修改。**
> 区块由 HTML 注释锚点守护；AI 与人均严禁手工编辑，否则 `verify` / `complete` 会失效。

---

<!-- SECTION:ACTIVE:START -->

<!-- SECTION:ACTIVE:END -->

---

<!-- SECTION:BLOCKED:START -->

<!-- SECTION:BLOCKED:END -->


---

<!-- SECTION:TODO:START -->

<!-- SECTION:TODO:END -->

---

<!-- SECTION:DONE:START -->

[Task-508] [S2-Muscle] 需求工作台全景数据动态对接与项目详情闭环 (Done: 2026-10-29)
- **Status**: DONE
- **功能目标**: 打通后端Project/Stats真实接口，消除前端假数据与字段映射脱节，实现工作台与需求详情100%动态数据闭环
- **涉及文件**: base_template/backend/platform-server/src/main/java/com/apex/req/controller/ProjectController.java,base_template/backend/platform-server/src/main/java/com/apex/req/service/ProjectService.java,base_template/frontend/src/api/req.ts,base_template/frontend/src/modules/req-management/views/ReqManagementView.vue

[Task-507] [S2-Muscle] 需求治理平台菜单架构重构与SQL权限初始化 (Done: 2026-10-29)
- **Status**: DONE
- **功能目标**: 将内嵌项目空间菜单挂载至需求治理平台并补齐数据库菜单权限
- **涉及文件**: docs/db/service.sql,docs/db/base.sql,base_template/frontend/src/views/layout/Layout.vue,base_template/frontend/src/router/index.ts,base_template/frontend/src/modules/req-management/views/ReqManagementView.vue

[Task-506] [S3-Stub] 基线增量差异计算引擎与联调探针 (Done: 2026-10-29)
- **Status**: DONE
- **功能目标**: 实现 BL-v1.0 与 BL-v1.1 的清单/原型/文档三合一增量比对引擎与自动化测试
- **涉及文件**: base_template/backend/platform-server/src/main/java/com/apex/req/service/DiffService.java

[Task-505] [S2-Muscle] 基线冻结看门狗门禁与 JGit Tag 自动化 (Done: 2026-10-29)
- **Status**: DONE
- **功能目标**: 实现蓝皮书§4.1的4项硬门禁核验、打包归档Zip并在本地代码库自动打标Git Tag
- **涉及文件**: base_template/backend/platform-server/src/main/java/com/apex/req/service/BaselineService.java

[Task-504] [S2-Muscle] 客户免登录签名令牌与防伪电子回执引擎 (Done: 2026-10-29)
- **Status**: DONE
- **功能目标**: 实现 HMAC 一次性令牌发放、4位防窥码校验、一句话复述存证与防伪回执生成
- **涉及文件**: base_template/backend/platform-server/src/main/java/com/apex/req/service/ApprovalService.java

[Task-503] [S2-Muscle] 需求与四层清单核心服务及实时代价计算器 (Done: 2026-10-29)
- **Status**: DONE
- **功能目标**: 实现需求录入、清单项决策切换、人天代价实时代价折算与持久化
- **涉及文件**: base_template/backend/platform-server/src/main/java/com/apex/req/service/RequirementService.java

[Task-502] [S1-Bone] 原型静态沙盒目录托管与 SHA256 引擎 (Done: 2026-10-29)
- **Status**: DONE
- **功能目标**: 实现原型 ZIP 包解压、本地静态托管服务与全目录 SHA256 计算引擎
- **涉及文件**: base_template/backend/platform-server/src/main/java/com/apex/req/service/PrototypeService.java

[Task-501] [S1-Bone] 数据库实体类与 MyBatis-Plus 映射层 (Done: 2026-10-29)
- **Status**: DONE
- **功能目标**: 针对 service.sql 中的 7 张表生成 Java Entity、Mapper 与基础 CRUD 骨架
- **涉及文件**: base_template/backend/platform-server/src/main/java/com/apex/req

[Task-400] [S1-Bone] 工作流 v2 内核加固（脚本/文档/手册） (Done: 2026-10-28)
- **Status**: DONE
- **功能目标**: 让 board.py 具备 verify+--accept 与原子归档、gate.py 补齐 4 类门禁与元门禁、全仓文档口径一致并落地新手手册
- **涉及文件**: .ai/scripts/board.py,.ai/scripts/gate.py,.ai/scripts/sync_tree.py,BEGINNER_HANDBOOK.md,workflow_guide.html

<!-- SECTION:DONE:END -->
