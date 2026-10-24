# 📋 [第三层：微观原子任务层] 实时任务看板 (tier3_status.md) [DEMO 示例 / 样例模板]

> 🏛️ **【三层记忆金字塔 - 第三层：微观原子任务看板 (看现在与未来)】**
> 💡 **使用与读取规约**：本文件由底层 Python 脚本 board.py 确切管控。记载当下唯一正在进行的 ACTIVE 焦点任务以及排队中的 TODO 任务，只维护当下的最新进度状态。

---

<!-- SECTION:ACTIVE:START -->
## 🔴 进行中 (ACTIVE)

<!-- SECTION:ACTIVE:END -->

<!-- SECTION:TODO:START -->
## ⚪ 待办中 (TODO)

### 🌟 第一优先级 (优先做 - 核心体验与工业合规亮点)
- 

### ⏳ 第二优先级 (以后做 - 二期进阶扩展)
- [Task-301] 多方案拓扑场景对比与版本快照 (整改前 vs 整改后连通性矩阵对比)
- [Task-302] 预置工业攻防演练剧本库 (震网病毒渗透、勒索软件横向移动拦截、APT跨区渗透演示)
- [Task-303] 真实工业交换机/路由器网络配置（Cisco/Huawei 格式配置或 CSV）资产逆向导入

### ❌ 明确放弃项 (不必做 - 避免过度设计)
- ❌ 接入复杂的底层服务器/设备瞬时时序指标采集图表（如假 CPU/内存曲线）
- ❌ 完整实现 OSPF/BGP 等底层复杂动态路由洪泛协议状态机
- ❌ 浏览器端应用层报文深度内容抓包过滤（DPI/HTTP Payload）

---

<!-- SECTION:TODO:END -->

<!-- SECTION:DONE:START -->
## 🟢 已完成 (DONE)

[Task-207] [S2-Muscle] 路由器与核心三层设备：静态路由表 (LPM) 与分支隔离日志 (Done: 2026-10-24)
- **Status**: DONE
- **功能目标**: 实现基于最长前缀匹配 (LPM) 的静态路由转发决策，前端支持可视增删查改路由条目；实现 PathState 分支日志完全隔离机制，杜绝弃用分支日志污染。
- **涉及文件**: `backend/module-network-sim/engine/BfsRoutingEngine.java`, `frontend/src/modules/network-sim/components/PropertyInspector.vue`

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

[Task-208] [S3-Skin] 画布动态流水线光效与阻断视觉强化 (Done: 2026-10-24)
- **Status**: DONE
- **功能目标**: 寻路成功后点亮链路流动粒子光效（流光顺畅流动）；阻断时在拦截节点打上红色脉冲警告圈 🚨 并悬浮展示放行/拦截原因气泡。
- **涉及文件**: `frontend/src/modules/network-sim/views/NetworkSimView.vue`, `SimulationConsole.vue`

[Task-209] [S2-Muscle] NAT 网络地址转换 (SNAT/DNAT) 仿真 (Done: 2026-10-24)
  - **功能目标**: 边界防火墙/网关支持配置 SNAT 私网源地址伪装与 DNAT 端口映射，数据包过境时执行五元组转换并输出 `[NAT TRANSLATION]` 仿真日志。
  - **涉及文件**: `backend/module-network-sim/engine/`, `frontend/src/modules/network-sim/components/PropertyInspector.vue`
- [Task-210] [S2-Muscle] 工业网络等保与合规审计报告一键生成
  - **功能目标**: 顶部工作台增加“合规评估报告”弹窗与一键导出（PDF/Markdown），自动化评估全网资产、大区隔离合规性、网闸物理隔离有效性及高危端口（22/3389）暴露审计。
  - **涉及文件**: `frontend/src/modules/network-sim/views/NetworkSimView.vue`

<!-- SECTION:DONE:END -->