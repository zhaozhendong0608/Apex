# 💡 [PRD-NET-01] 网络拓扑仿真系统 (Module-Network-Sim) 需求规格说明书

## 1. 业务目标与愿景
构建一个基于“可视化画布 - 后端图算法引擎 - 结果动态回显”的网络拓扑仿真系统。用户可在线直观绘制路由器、交换机、防火墙、Neuron 网关及主机节点，并设定 IP、网段和 ACL 安全策略；后端基于有向图与 BFS 广度优先搜索算法，实时校验数据包可达性与防火墙拦截机制，并通过时序监控记录仿真全流程指标。

---

## 2. 核心功能场景与技术契约

### 场景一：网络拓扑绘制与序列化持久化
1. **设备库支持**：
   - 节点类型：`HOST` (终端主机), `SWITCH` (交换机), `ROUTER` (路由器), `FIREWALL` (防火墙), `NEURON_GATEWAY` (边缘网关)。
   - 节点属性：节点名称、IP 地址、子网掩码、所属网段。
   - 连线属性：源节点、目标节点、链路延迟/带宽。
2. **ACL 规则配置**：
   - 规则元素：规则优先级、Action (`PERMIT` / `DENY`)、源 IP/网段、目标 IP/网段、目标端口、协议 (`TCP` / `UDP` / `ICMP`)。
3. **持久化**：
   - 前端基于 AntV X6 将画布导出为标准 JSON 树结构。
   - 后端保存至 MySQL 数据表 `net_topology`。

### 场景二：拓扑图构建与 BFS 寻路 + ACL 拦截引擎
1. **内存构图**：后端解析拓扑 JSON，生成有向加权图 `Graph<Node, Edge>`。
2. **BFS 连通性测试**：
   - 入参：`sourceNodeId` (源节点), `targetNodeId` (目的节点), `protocol`, `destPort`。
   - 寻路逻辑：使用队列执行 BFS 搜索全部有效通路。
   - 规则拦截：当路径遍历到带 ACL 的节点（如防火墙/路由器）时，按顺序匹配 ACL 规则：
     - 若命中 `DENY`：立即阻断该路径并生成拦截审计日志。
     - 若命中 `PERMIT`：继续向前推进。
     - 若默认无匹配：执行默认策略 (默认阻断或通行)。
3. **出参返回**：
   - `reachable`: true/false (是否连通)
   - `path`: 途经的节点与链路序列 `[NodeA, NodeB, NodeC]`
   - `hops`: 跳数
   - `logs`: 仿真审计日志列表（如“经过防火墙 FW-1：命中规则 ACL-101(PERMIT)”）

### 场景三：TDengine 时序监控与指标流 (预留与逐步集成)
1. 记录每次连通性测试的耗时、跳数、链路拥塞仿真指标以及时间序列事件日志。

---

## 3. 架构落地与 Maven/Vue 分层设计

### 后端模块划分
- `platform-common`: 全局统一异常、统一 `Result<T>` 封装、通用工具。
- `module-network-sim`: 拓扑仿真领域核心（图结构、BFS 算法、ACL 校验引擎、MySQL 拓扑表 DAO）。
- `platform-server`: 聚合启动类，装配 Controller 与数据源。

### 前端模块划分
- `src/modules/network-sim/`:
  - `views/NetworkSimCanvas.vue`: 仿真主画布页面。
  - `components/DevicePalette.vue`: 左侧设备拖拽面板。
  - `components/PropertyPanel.vue`: 右侧设备与 ACL 配置面板。
  - `api/networkSim.ts`: 拓扑保存与连通性测试接口。
