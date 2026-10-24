# 📦 项目交接与架构变更卡片 (handover-20261024-network_sim.md)

## 📌 1. 本次会话核心成果与关键决策
- **Maven 多模块重构与通用平台拆分 (Task-201)**：
  - 根目录 `pom.xml` 改为父工程，提取 `platform-common`（公共通用响应体、异常捕获、常量），新建 `module-network-sim` 子模块，由 `platform-server` 统一聚合装配启动。
  - 统一升级关键依赖：Spring Boot 3.2.4、MyBatis-Plus 3.5.7、Lombok 1.18.38，本地 MySQL 8.0 数据库初始化 `net_topology` 拓扑方案表。
- **纯 Java 领域模型与 BFS + ACL 寻路引擎 (Task-202)**：
  - 在 `module-network-sim` 构建领域模型（`TopoGraph`, `TopoNode`, `TopoEdge`, `AclRule`）。
  - 实现纯 Java 17 无外部依赖的图 BFS 寻路与防火墙规则匹配算法 `BfsRoutingEngine`，并通过 JUnit 5 单元测试（4/4 绿灯通过）。
- **拓扑方案后端 RESTful CRUD 接口 (Task-204)**：
  - 编写 `NetTopologyController`、`NetTopologyService` 与 Mapper，接口基准路径规范为 `/api/v1/network-sim/topology`。
  - 包含分页查询、详情读取、画布序列化 JSON 保存及在线 BFS 连通性推演与日志审计接口。
- **前端 Vue 3 + AntV X6 拓扑仿真工作台系统 (Task-203, Task-205, Task-206)**：
  - 新增 `TopologyListView.vue` 方案列表管理页，支持方案增删改查及带参 `?id=xxx` 跳转至全屏设计画布。
  - 重构图元渲染引擎，采用原生 SVG 标准图元（`rect` + `text` + `orth` 折线路由），彻底根除 HTML 图元渲染异常与 Manhattan 路由告警。
  - **双向响应式属性检查器 (PropertyInspector.vue)**：支持点选设备节点修改名称、IP、所属大区及动态编辑防火墙 ACL 规则集。
  - **通用区域底板 (DevicePalette.vue & NetworkSimView.vue)**：将写死的分区重构为单一通用“区域底板”原子图元，支持拖拽上图后在检查器自由修改名称、主题色、透明度、实线/虚线边框及字号。
  - **右键上下文菜单与快捷删除**：针对设备、连线、区域底板全覆盖右键菜单，支持右键直接删除图元并联动清除选中态与后端同步。

---

## 🛠️ 2. 本次改动核心文件清单
1. **后端多模块与引擎**：
   - `base_template/backend/pom.xml` (多模块聚合工程)
   - `base_template/backend/platform-common/pom.xml`
   - `base_template/backend/platform-common/src/main/java/com/apex/common/result/Result.java`
   - `base_template/backend/platform-server/pom.xml`
   - `base_template/backend/platform-server/src/main/java/com/apex/PlatformApplication.java`
   - `base_template/backend/module-network-sim/pom.xml`
   - `base_template/backend/module-network-sim/src/main/java/com/apex/network/domain/` (领域模型)
   - `base_template/backend/module-network-sim/src/main/java/com/apex/network/engine/BfsRoutingEngine.java` (算法引擎)
   - `base_template/backend/module-network-sim/src/main/java/com/apex/network/controller/NetTopologyController.java` (CRUD与仿真Controller)
   - `base_template/backend/module-network-sim/src/main/java/com/apex/network/service/NetTopologyService.java`
   - `base_template/backend/module-network-sim/src/test/java/com/apex/network/engine/BfsRoutingEngineTest.java` (JUnit单测)
   - `base_template/docs/db/service.sql` (net_topology表结构与种子数据)
2. **前端拓扑仿真模块**：
   - `base_template/frontend/src/router/index.ts` (路由配置)
   - `base_template/frontend/src/modules/network-sim/api/topology.ts` (API 请求定义)
   - `base_template/frontend/src/modules/network-sim/views/TopologyListView.vue` (方案管理列表)
   - `base_template/frontend/src/modules/network-sim/views/NetworkSimView.vue` (拓扑工作台与X6画布)
   - `base_template/frontend/src/modules/network-sim/components/DevicePalette.vue` (设备与通用底板面板)
   - `base_template/frontend/src/modules/network-sim/components/PropertyInspector.vue` (双向属性检查器)
   - `base_template/frontend/src/modules/network-sim/components/SimulationConsole.vue` (实时仿真控制台)

---

## 💡 3. 关键架构决策与踩坑预防 (KI)
1. **API 路径统一契约**：前端 Axios 实例已注入 `baseURL: '/api/v1'`，子模块前端 API 定义不得重复拼接 `/api/`，Controller 统一映射为 `@RequestMapping("/api/v1/network-sim")`。
2. **X6 图元原生化**：严禁在 Vue 3 打包环境中滥用 HTML 图元（易因 vue-shape 运行时导致空白或样式穿透失败），统一采用 X6 声明式 SVG Markup 与 `attrs` 属性驱动。
3. **图元通用化原则**：图元面板仅暴露原子原型，一切业务特化属性（名称、颜色、透明度、实虚线）交由右侧属性检查器双向动态驱动。

---

## 🚀 4. 下一步工作建议
- 网络拓扑仿真模块的基础骨架、算法引擎、数据库持久化及前端全交互（增删改查、属性联动、通用底板、右键删除、BFS 实时推演）已全部高质量落地并验证通过。
- 下一会话可直接发送 `6` 或 `继续` 启动下一阶段业务演进（如：链路丢包抖动仿真、多路径动态路由选择或导出拓扑图片）。
