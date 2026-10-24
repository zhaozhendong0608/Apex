# 📦 网络拓扑仿真模块 (Module-Network-Sim) 阶段交接文档

- **交接日期**：2026-10-24 18:23:00
- **模块标识**：`network_sim`
- **对应任务**：`Task-207` (静态路由表 LPM 转发与探索分支日志隔离)

---

## 一、本次核心演进与成果

1. **静态路由表与最长前缀匹配 (LPM) 引擎实现**：
   - 在后端 [RouteEntry.java](file:///Users/up_dong/Documents/java_workspace/Apex/base_template/backend/module-network-sim/src/main/java/com/apex/network/model/RouteEntry.java) 中建模路由条目（`destSubnet`, `mask`, `nextHopIp`, `description`）；
   - 在 [BfsRoutingEngine.java](file:///Users/up_dong/Documents/java_workspace/Apex/base_template/backend/module-network-sim/src/main/java/com/apex/network/engine/BfsRoutingEngine.java) 中实现最长前缀匹配（Longest Prefix Match）选路算法，支持 `0.0.0.0/0` 缺省兜底；
   - 拓扑中三层设备（ROUTER/SWITCH/FIREWALL）在转发跨网段数据包时，若命中下一跳网关，严格只向指定下一跳出接口单播递送，彻底解决无效广播漫游问题。

2. **独立分支日志隔离架构 (PathState)**：
   - 消除原先全局共享 `logs` 导致的“主成功路径混入弃用分支拦截警告”缺陷；
   - 采用内部记录对象 `PathState(path, pathLogs, rerouted)`，使失败或被网闸阻断的死胡同分支日志自动销毁，最终连通日志 100% 纯净精准。

3. **前端可视化路由配置与双向持久化**：
   - [PropertyInspector.vue](file:///Users/up_dong/Documents/java_workspace/Apex/base_template/frontend/src/modules/network-sim/components/PropertyInspector.vue) 增加 `🌐 静态路由表 (Routing Table)` 科技蓝配置卡片，支持动态增删查改路由项；
   - [NetworkSimView.vue](file:///Users/up_dong/Documents/java_workspace/Apex/base_template/frontend/src/modules/network-sim/views/NetworkSimView.vue) 为 `IT核心路由器` 默认注入前往 OT 核心网段（`172.16.1.0/24 ➔ 10.0.0.1`）的静态路由并持久化至 MySQL `net_topology`。

4. **自动化测试 100% 覆盖**：
   - [BfsRoutingEngineTest.java](file:///Users/up_dong/Documents/java_workspace/Apex/base_template/backend/module-network-sim/src/test/java/com/apex/network/engine/BfsRoutingEngineTest.java) 新增 `TC-NET-10` 静态路由精确转发与抵御漫游用例，全套 8 组单测 100% 通过。

---

## 二、关键技术决议与踩坑看门狗

1. **工业仿真聚焦原则**：
   - 坚决砍掉假 CPU/内存时序大屏与深层 DPI 应用层过滤，全面聚焦在“二三层寻路、静态路由、ACL 防火墙、单向网闸与网络自愈”核心网络行为上。
2. **多分支搜索日志污染隐患**：
   - BFS 寻路中严禁在主循环外部使用全局共享的 `List<String> logs`，必须由分支对象（如 `PathState`）携带自身日志，并在终点命中时提取返回。

---

## 三、后续接力待办（TODO 优先级排序）

1. **`[Task-208]` 画布动态流水线光效与阻断视觉强化 (P0)**：寻路成功后点亮动态流光粒子链路；阻断时在拦截节点打上红色脉冲警告圈 🚨。
2. **`[Task-209]` NAT 网络地址转换 (SNAT/DNAT) 仿真 (P1)**：防火墙源地址伪装与端口映射。
3. **`[Task-210]` 工业网络等保与合规审计报告一键生成 (P1)**：一键评估导出全网资产、大区隔离与网闸合规性。
