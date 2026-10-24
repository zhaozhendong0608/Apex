# 📋 [第三层：微观原子任务层] 实时任务看板 (tier3_status.md) [DEMO 示例 / 样例模板]

> 🏛️ **【三层记忆金字塔 - 第三层：微观原子任务看板 (看现在与未来)】**
> 💡 **使用与读取规约**：本文件由底层 Python 脚本 board.py 确切管控。记载当下唯一正在进行的 ACTIVE 焦点任务以及排队中的 TODO 任务，只维护当下的最新进度状态。

---

<!-- SECTION:ACTIVE:START -->
## 🔴 进行中 (ACTIVE)

---

<!-- SECTION:ACTIVE:END -->

<!-- SECTION:TODO:START -->
## ⚪ 待办中 (TODO)
- [Task-001] [S1-Bone 骨架] 完成需求规划与接口契约设计

---

<!-- SECTION:TODO:END -->

<!-- SECTION:DONE:START -->
## 🟢 已完成 (DONE)

[Task-101] [S1-Bone] 通用平台数据表与认证API骨架 (Done: 2026-10-07)
- **Status**: DONE
- **功能目标**: 实现管理员开户用户表、角色表、菜单表及登录JWT认证API
- **涉及文件**: base_template/docs/modules/sys_platform/prd-sys-platform-v1.0.md

[Task-102] [API实现] 统一认证与管理员开户Controller/Service开发 (Done: 2026-10-07)
- **Status**: DONE
- **功能目标**: 基于DLD契约完成Login与User/Role核心接口编写
- **涉及文件**: base_template/docs/modules/sys_platform/dld-sys-platform-v1.0.md

[Task-103] [前端实现] Vue3管理平台前端搭建与登录开户界面开发 (Done: 2026-10-07)
- **Status**: DONE
- **功能目标**: 基于DLD与mockup.html开发Vue3+Vite+Element Plus前端工程及登录与统一开户页面
- **涉及文件**: base_template/frontend/

[Task-104] [SysPlatform全栈] 补齐SpringBoot启动类、全量消灭Mock假数据与MySQL/Lombok规范重构 (Done: 2026-10-07)
- **Status**: DONE
- **功能目标**: 完成Spring Boot 3启动类与pom.xml配置、消灭全量Controller硬编码假数据、连接MySQL 8.0数据库及官网标准Lombok 1.18.38代码精简
- **涉及文件**: base_template/backend/

[Task-201] [S1-Bone] Maven多模块重构与通用基础设施抽取 (Done: 2026-10-24)
- **Status**: DONE
- **功能目标**: 重构后端为Maven多模块结构(父POM/platform-common/platform-server/module-network-sim骨架)
- **涉及文件**: backend/pom.xml, backend/platform-common/, backend/platform-server/

[Task-202] [S1-Bone] 网络拓扑数据结构设计与纯Java BFS+ACL算法单测 (Done: 2026-10-24)
- **Status**: DONE
- **功能目标**: 在module-network-sim中定义Graph/Node/Edge/AclRule并用JUnit跑通BFS寻路与拦截
- **涉及文件**: backend/module-network-sim/src/test/java/

[Task-203] [S2-Muscle] 前端引入AntV X6与网络拓扑仿真独立模块搭建 (Done: 2026-10-24)
- **Status**: DONE
- **功能目标**: 在Vue3前端安装AntV X6，创建modules/network-sim，实现设备拖拽与画布连线
- **涉及文件**: base_template/frontend/src/modules/network-sim/

[Task-204] [S2-Muscle] 前后端联调：拓扑图MySQL保存加载与连通性动态演示 (Done: 2026-10-24)
- **Status**: DONE
- **功能目标**: 完成net_topology表持久化API，打通前端测试连通性按钮与后端BFS引擎，动态高亮数据包流动轨迹
- **涉及文件**: backend/module-network-sim/controller/, frontend/src/modules/network-sim/views/

[Task-205] [S2-Muscle] 前端拓扑方案管理列表页与路由打通 (Done: 2026-10-24)
- **Status**: DONE
- **功能目标**: 创建TopologyListView支持拓扑方案增删改查分页，点击【设计拓扑】携带ID跳转到全屏画布工作台
- **涉及文件**: frontend/src/modules/network-sim/views/TopologyListView.vue

[Task-206] [S2-Muscle] 画布工作台根据ID加载与实时保存，打通BFS动态连通性仿真 (Done: 2026-10-24)
- **Status**: DONE
- **功能目标**: 画布支持根据路由query中id读取真实JSON，修改后点击保存回写数据库，连通性测试调用后端BFS接口
- **涉及文件**: frontend/src/modules/network-sim/views/NetworkSimView.vue

<!-- SECTION:DONE:END -->