# 📦 项目交接文档 (Handover Artifact)

- **交接日期**: 2026-10-23
- **模块名称**: `station-device-simulator` 电站数字孪生控制台与海量点位加载性能调优
- **当前状态**: ✅ 生产就绪 (Production Ready)

---

## 🎯 本次完成的核心任务 (Done Tasks)

1. **[Task-007] 对齐 `etl_station` 架构规范的海量点位懒加载与滑窗分页系统**：
   - 全面重写前端层级树逻辑，通过 [TreeNodeItem.vue](file:///Users/dongzi/Documents/工作/workplace/Apex/station-device-simulator/simulator-ui/src/components/TreeNodeItem.vue) 递归组件支持任意深度的电站资产拓扑展开（`电站 ➔ 机组 ➔ 设备 ➔ 部件 ➔ 测点`）。
   - 实现滑动窗口分页 (`PAGE_SIZE = 50`)，通过 `⬆️ 上一页` 与 `⬇️ 下一页` 导航保持 DOM 节点数恒定在 52 个以内，彻底避免 140,000+ 点位全量渲染引发的前端卡死与 502 网关超时。

2. **[Task-008] 彻底清除硬编码 Mock 兜底数据与动态真实数据绑定**：
   - 彻底移除了 `pointList` 中的假数据数组（`U1_THRUST_PAD_TEMP_01` 等）。
   - 动态将选中的真实叶子测点与右侧自定义故障注入器、波形监视器动态绑定。
   - 修复前端模板缺失 `formatVal` 辅助方法引起的运行时 `TypeError: _ctx.formatVal is not a function` 异常。

3. **[Task-009] 解决 JavaScript 64 位雪花算法 Long ID 精度截断丢失问题**：
   - **痛点**：数据库中 Long ID（如丰宁电站 `2059524942126518272`）超过 JS `Number.MAX_SAFE_INTEGER`，前端自动被四舍五入为 `2059524942126518300`，导致向后端请求子节点永远命中空结果 `[]`。
   - **方案**：在后端配置全局 [JacksonConfig.java](file:///Users/dongzi/Documents/工作/workplace/Apex/station-device-simulator/simulator-starter/src/main/java/com/jtech/simulator/config/JacksonConfig.java)，将 `Long`、`BigInteger` 全局转换为 JSON String 输出（如 `"2059524942126518272"`），对齐 `etl_station` 规约，彻底修复子层级点击无法展开问题。

4. **[Task-010] 突破海量点位查询性能瓶颈（从 66.94 秒优化至 3.16 秒，提速 21 倍）**：
   - **痛点**：查询部件下的 3,254 个点位耗时 1 分多钟，严重影响使用体验。
   - **优化**：在 [FusionAssetMapper.java](file:///Users/dongzi/Documents/工作/workplace/Apex/station-device-simulator/simulator-metadata-mysql/src/main/java/com/jtech/simulator/metadata/mysql/mapper/FusionAssetMapper.java) 与 [MysqlPointMetadataProvider.java](file:///Users/dongzi/Documents/工作/workplace/Apex/station-device-simulator/simulator-metadata-mysql/src/main/java/com/jtech/simulator/metadata/mysql/provider/MysqlPointMetadataProvider.java) 中对齐 `etl_station` 的 `selectAssetList` 模式：
     - 采用单次高效 `LEFT JOIN` 联合索引驱动查询（命中 `idx_parent_id`）。
     - 彻底消除逐点孙代统计的空查循环，直接在内存完成属性参数装配。

---

## 🛠️ 本次核心改动文件

1. **后端 (Java/Spring Boot/MyBatis-Plus)**:
   - [JacksonConfig.java](file:///Users/dongzi/Documents/工作/workplace/Apex/station-device-simulator/simulator-starter/src/main/java/com/jtech/simulator/config/JacksonConfig.java): 全局 Long 转 String 序列化配置。
   - [application.yml](file:///Users/dongzi/Documents/工作/workplace/Apex/station-device-simulator/simulator-starter/src/main/resources/application.yml): Spring Boot Jackson 配置对齐。
   - [FusionAssetMapper.java](file:///Users/dongzi/Documents/工作/workplace/Apex/station-device-simulator/simulator-metadata-mysql/src/main/java/com/jtech/simulator/metadata/mysql/mapper/FusionAssetMapper.java): 单次高效 LEFT JOIN 索引覆盖查询。
   - [MysqlPointMetadataProvider.java](file:///Users/dongzi/Documents/工作/workplace/Apex/station-device-simulator/simulator-metadata-mysql/src/main/java/com/jtech/simulator/metadata/mysql/provider/MysqlPointMetadataProvider.java): 懒加载逻辑重构与 21 倍提速。

2. **前端 (Vue 3 / Vite)**:
   - [App.vue](file:///Users/dongzi/Documents/工作/workplace/Apex/station-device-simulator/simulator-ui/src/App.vue): 移除所有旧兜底假数据、挂载 `TreeNodeItem` 递归树组件、补齐 `formatVal` 辅助函数、增加加载状态与重试按钮。
   - [TreeNodeItem.vue](file:///Users/dongzi/Documents/工作/workplace/Apex/station-device-simulator/simulator-ui/src/components/TreeNodeItem.vue): 递归树节点组件，集成窗口分页与 String ID 响应式。

---

## 💡 关键工程与架构决策 (Decisions)

1. **坚持只读原则与数据安全**:
   - 模拟器工程 `station-device-simulator` 对 MySQL 主库表 `fusion_asset` 和 `fusion_asset_point` 仅保持纯只读访问，不修改业务数据。
2. **前后端接口防溢出契约**:
   - 只要涉及数据库主键雪花 ID，后端必须序列化为字符串，杜绝前端 JavaScript 双精度浮点数丢失低位。
3. **海量点位加载的性能铁律**:
   - 严禁任何形式的无参全表拉取。所有点位按 `parentId` 懒加载，在遇到上千点位时单次 SQL 获取，前端仅渲染滑窗切片（50条/页）。
