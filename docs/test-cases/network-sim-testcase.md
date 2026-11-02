# 🧪 [TEST-NET-01] 网络拓扑仿真核心接口与算法测试说明书 (Test Case Matrix)

> 依据【钧天科技 V1.0 质量标准】与 `DLD-NET-01` 接口与表结构契约，设计覆盖正向连通、ACL 规则拦截、环路拓扑检测及非法入参的完整测试用例矩阵。

---

## 1. 测试用例矩阵表

| 用例编号 | 测试模块 | 测试场景与测试目的 | 输入数据 (Input) | 预期结果 (Expected Output) | 测试级别 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-NET-01** | `BFS Engine` | **正向连通寻路**<br>主机 A 经交换机 SW-1 到 主机 B，无任何防火墙阻断 | `sourceNodeId`: "node_host_a"<br>`targetNodeId`: "node_host_b"<br>`destPort`: 80 | `reachable == true`<br>`hops == 2`<br>`nodePath == ["node_host_a", "node_sw_1", "node_host_b"]` | P0 (核心) |
| **TC-NET-02** | `ACL Matcher` | **防火墙精确端口拦截 (DENY)**<br>主机 A 经防火墙访问服务器 B 的 3306 端口，命中 DENY 规则 | `sourceNodeId`: "node_host_a"<br>`targetNodeId`: "node_server_b"<br>`destPort`: 3306<br>`protocol`: "TCP" | `reachable == false`<br>`blocked == true`<br>`blockedRuleId == "acl_1"`<br>包含拦截审计日志 | P0 (核心) |
| **TC-NET-03** | `ACL Matcher` | **防火墙放行规则 (PERMIT)**<br>主机 A 经防火墙访问服务器 B 的 80 端口，命中 PERMIT 放行规则 | `sourceNodeId`: "node_host_a"<br>`targetNodeId`: "node_server_b"<br>`destPort`: 80<br>`protocol`: "TCP" | `reachable == true`<br>`blocked == false`<br>`hops == 3` | P0 (核心) |
| **TC-NET-04** | `ACL Matcher` | **默认放行策略 (Default Permit)**<br>未命中任何 ACL 规则的未知端口流量默认允许通行 | `sourceNodeId`: "node_host_a"<br>`targetNodeId`: "node_server_b"<br>`destPort`: 8088<br>`protocol`: "TCP" | `reachable == true`<br>日志包含“匹配默认策略(PERMIT)允许放行” | P1 (边界) |
| **TC-NET-05** | `Graph Builder` | **环路拓扑防死循环保护**<br>交换机之间存在环路 (Ring Topology)，BFS 寻路不会卡死 | 3 台交换机互联形成三角环，并在两端接主机 | 在 100ms 内正常返回最短跳数路径，无死循环 | P1 (防御) |
| **TC-NET-06** | `Topology API` | **拓扑持久化保存与重拉**<br>保存前端 AntV X6 序列化 JSON 到 MySQL，再重新查询回显 | 提交包含 5 个节点、4 条连线的 JSON | 返回保存成功 `id > 0`，重查字段一致 | P0 (核心) |
| **TC-NET-07** | `Validation` | **非法入参报错防御**<br>传入不存在的源节点 ID 或空图 | `sourceNodeId`: "node_not_exist" | 返回 HTTP 400 或业务错误码 4001，友好提示“源节点不存在” | P2 (异常) |
