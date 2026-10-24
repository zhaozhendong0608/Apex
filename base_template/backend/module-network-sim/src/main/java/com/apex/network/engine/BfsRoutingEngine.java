package com.apex.network.engine;

import com.apex.network.model.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * BFS 广度优先搜索寻路与拓扑仿真核心引擎
 */
@Component
public class BfsRoutingEngine {

    private final AclRuleMatcher aclMatcher;

    public BfsRoutingEngine(AclRuleMatcher aclMatcher) {
        this.aclMatcher = aclMatcher;
    }

    /**
     * 执行端到端 BFS 寻路与安全拦截仿真
     *
     * @param graph        拓扑图对象
     * @param sourceNodeId 源设备 ID
     * @param targetNodeId 目标设备 ID
     * @param destPort     目的端口
     * @param protocol     协议类型 (TCP, UDP, ICMP)
     * @return 仿真结果
     */
    public SimResultVO simulate(TopoGraph graph, String sourceNodeId, String targetNodeId, Integer destPort, String protocol) {
        List<String> logs = new ArrayList<>();
        Map<String, TopoNode> nodeMap = new HashMap<>();
        for (TopoNode n : graph.getNodes()) {
            nodeMap.put(n.getId(), n);
        }

        TopoNode srcNode = nodeMap.get(sourceNodeId);
        TopoNode destNode = nodeMap.get(targetNodeId);

        if (srcNode == null || destNode == null) {
            logs.add("[ERROR] 源设备节点或目标设备节点不存在！");
            return SimResultVO.builder()
                    .reachable(false)
                    .blocked(false)
                    .logs(logs)
                    .build();
        }

        logs.add(String.format("[INFO] 发起连通性仿真寻路: [%s](%s) ➔ [%s](%s) | 端口: %s, 协议: %s",
                srcNode.getName(), srcNode.getIp(), destNode.getName(), destNode.getIp(),
                destPort != null ? destPort : "任意", protocol != null ? protocol : "TCP"));

        // 检查源节点与目标节点自身的健康状态
        if (Boolean.TRUE.equals(srcNode.getDisabled())) {
            logs.add(String.format("[ERROR] 源设备节点 [%s] 处于离线/宕机状态，无法发起数据传输！", srcNode.getName()));
            return SimResultVO.builder()
                    .reachable(false)
                    .blocked(false)
                    .logs(logs)
                    .build();
        }

        if (Boolean.TRUE.equals(destNode.getDisabled())) {
            logs.add(String.format("[ERROR] 目标设备节点 [%s] 处于离线/宕机状态，网络不可达！", destNode.getName()));
            return SimResultVO.builder()
                    .reachable(false)
                    .blocked(false)
                    .logs(logs)
                    .build();
        }

        // 网络协议层核心校验：子网 (Subnet) 与路由网关 (Gateway) 判定
        String srcSubnet = srcNode.getSubnet() != null ? srcNode.getSubnet() : "255.255.255.0";
        String destSubnet = destNode.getSubnet() != null ? destNode.getSubnet() : "255.255.255.0";
        boolean sameSubnet = isSameSubnet(srcNode.getIp(), destNode.getIp(), srcSubnet);

        if (sameSubnet) {
            logs.add(String.format("[PROTOCOL] 二层局域网检测：[%s] 与 [%s] 属于相同子网 (掩码: %s)，数据包执行 ARP 二层直连/交换机内部转发。",
                    srcNode.getName(), destNode.getName(), srcSubnet));
        } else {
            logs.add(String.format("[PROTOCOL] 三层跨网段检测：[%s](%s) 与 [%s](%s) 处于不同子网，必须经由默认网关或三层路由器中继寻径！",
                    srcNode.getName(), srcNode.getIp(), destNode.getName(), destNode.getIp()));
            if (srcNode.getGateway() == null || srcNode.getGateway().trim().isEmpty()) {
                logs.add(String.format("[WARN] 路由配置提示：源设备 [%s] 未显式配置默认网关 (Default Gateway)，系统将尝试根据拓扑自主探测三层出口路由。",
                        srcNode.getName()));
            } else {
                logs.add(String.format("[ROUTING] 源设备 [%s] 已绑定默认网关: %s，三层数据包首跳指向该网关。",
                        srcNode.getName(), srcNode.getGateway()));
            }
        }

        // 构建邻接表 (双向物理链路，过滤故障链路)
        Map<String, List<TopoEdge>> adjacencyMap = new HashMap<>();
        for (TopoEdge edge : graph.getEdges()) {
            if (Boolean.TRUE.equals(edge.getDisabled())) {
                logs.add(String.format("[WARN] 物理链路 [%s ➔ %s] 处于故障切断/挖断状态，BFS 寻路跳过此通道。",
                        edge.getSourceNodeId(), edge.getTargetNodeId()));
                continue;
            }
            adjacencyMap.computeIfAbsent(edge.getSourceNodeId(), k -> new ArrayList<>()).add(edge);

            // 构造反向链路以支持常规设备间的双向物理连通
            TopoEdge reverseEdge = TopoEdge.builder()
                    .id(edge.getId() + "_rev")
                    .sourceNodeId(edge.getTargetNodeId())
                    .targetNodeId(edge.getSourceNodeId())
                    .bandwidth(edge.getBandwidth())
                    .latencyMs(edge.getLatencyMs())
                    .disabled(edge.getDisabled())
                    .build();
            adjacencyMap.computeIfAbsent(edge.getTargetNodeId(), k -> new ArrayList<>()).add(reverseEdge);
        }

        // 内部路径状态：使每一条路径分支持有独立的流水线日志，杜绝跨分支相互串染
        record PathState(List<String> path, List<String> pathLogs, boolean rerouted) {}

        Queue<PathState> queue = new LinkedList<>();
        queue.add(new PathState(new ArrayList<>(List.of(sourceNodeId)), new ArrayList<>(logs), false));

        Set<String> visited = new HashSet<>();
        visited.add(sourceNodeId);

        int maxHopsLimit = 50; // 防死循环跳数护栏
        SimResultVO blockedResult = null;

        while (!queue.isEmpty()) {
            PathState state = queue.poll();
            List<String> currentPath = state.path();
            List<String> currentLogs = new ArrayList<>(state.pathLogs());
            boolean currentRerouted = state.rerouted();

            String currentNodeId = currentPath.get(currentPath.size() - 1);
            TopoNode currentNode = nodeMap.get(currentNodeId);

            if (currentNode == null) {
                continue;
            }

            // 非源节点：在真正处理当前节点时，记录前移 TRACE 日志
            if (currentPath.size() > 1) {
                currentLogs.add(String.format("[TRACE] 数据包前移至下一跳: [%s] (类型: %s, IP: %s)",
                        currentNode.getName(), currentNode.getType(), currentNode.getIp()));
            }

            // 成功抵达终点
            if (currentNodeId.equals(targetNodeId)) {
                int hops = currentPath.size() - 1;
                int totalLatency = hops * 2; // 默认每跳 2ms 模拟
                if (currentRerouted) {
                    currentLogs.add(String.format("[REDUNDANCY] 工业网络故障应急自愈成功！避开故障节点/链路，成功通过冗余备用路径抵达目的节点 [%s]！(跳数: %d, 预估延迟: %dms)",
                            destNode.getName(), hops, totalLatency));
                } else {
                    currentLogs.add(String.format("[SUCCESS] 数据包顺利抵达目的节点 [%s]，网络连通！(跳数: %d, 预估延迟: %dms)",
                            destNode.getName(), hops, totalLatency));
                }

                return SimResultVO.builder()
                        .reachable(true)
                        .blocked(false)
                        .nodePath(currentPath)
                        .hops(hops)
                        .totalLatencyMs(totalLatency)
                        .logs(currentLogs)
                        .build();
            }

            // 安全护栏：防止复杂拓扑产生死循环
            if (currentPath.size() > maxHopsLimit) {
                currentLogs.add("[ERROR] 遍历跳数超过安全上限 50 跳，疑似存在拓扑环路异常，终止寻路！");
                break;
            }

            // 特性 1：电力二次系统物理单向安全隔离装置 (GAP) 硬件单向约束
            // 工业规约：仅允许数据从“生产控制大区 (OT_ZONE / 现场层)”单向摆渡至“管理信息大区 (IT_ZONE)”，严禁反向穿透！
            if ("GAP".equalsIgnoreCase(currentNode.getType())) {
                String destZone = destNode.getZone() != null ? destNode.getZone() : "";
                boolean isReverseTraffic = "OT_ZONE".equalsIgnoreCase(destZone) || "PHY_ZONE".equalsIgnoreCase(destZone);

                if (isReverseTraffic) {
                    currentLogs.add(String.format("[SECURITY ALERT] 触发电力二次系统物理单向隔离网闸 [%s] 硬件反向阻断！", currentNode.getName()));
                    currentLogs.add("[ERROR] 国家能源局 36 号令安全防护规约：严禁由管理信息大区 (IT) 向生产控制大区 (OT) 进行反向穿透控制！物理链路强行切断。");

                    blockedResult = SimResultVO.builder()
                            .reachable(false)
                            .blocked(true)
                            .blockedNodeId(currentNodeId)
                            .blockedRuleId("GAP_HARDWARE_REVERSE_ISOLATION")
                            .nodePath(currentPath)
                            .hops(currentPath.size() - 1)
                            .logs(currentLogs)
                            .build();
                    // 网闸反向物理切断，不再从网闸向下扩散
                    continue;
                } else {
                    currentLogs.add(String.format("[INFO] 物理单向网闸 [%s] 校验通过：合法单向数据采集摆渡 (OT ➔ IT)，允许单向透传数据包。", currentNode.getName()));
                }
            }

            // 特性 2：如果当前中继节点是防火墙或具备 ACL 规则，执行安全拦截策略校验
            if ("FIREWALL".equalsIgnoreCase(currentNode.getType()) || (currentNode.getAcls() != null && !currentNode.getAcls().isEmpty())) {
                AclRuleMatcher.MatchResult matchRes = aclMatcher.evaluate(currentNode, srcNode.getIp(), destNode.getIp(), destPort, protocol);
                currentLogs.add("[TRACE] " + matchRes.reason());

                if (matchRes.matched() && matchRes.matchedRule().getAction() == AclAction.DENY) {
                    currentLogs.add(String.format("[ERROR] 数据包被节点 [%s] 的 ACL 规则 [%s] 强制拦截阻断！网络不可达。",
                            currentNode.getName(), matchRes.matchedRule().getId()));

                    blockedResult = SimResultVO.builder()
                            .reachable(false)
                            .blocked(true)
                            .blockedNodeId(currentNodeId)
                            .blockedRuleId(matchRes.matchedRule().getId())
                            .nodePath(currentPath)
                            .hops(currentPath.size() - 1)
                            .logs(currentLogs)
                            .build();
                    // 被防火墙 ACL 策略阻断，此分支停止前移
                    continue;
                }
            }

            // 特性 A：路由器 / 三层交换机静态路由表 (Routing Table) 转发决策
            // 若当前节点配置了路由表，根据目标 IP 匹配最佳静态路由项 (最长前缀匹配 / LPM)
            RouteEntry matchedRoute = null;
            if (currentNode.getRoutes() != null && !currentNode.getRoutes().isEmpty()) {
                matchedRoute = findBestMatchingRoute(currentNode.getRoutes(), destNode.getIp());
                if (matchedRoute != null) {
                    currentLogs.add(String.format("[ROUTE MATCH] 节点 [%s] 命中静态路由表项: 目标网段 [%s/%s] ➔ 下一跳网关 [%s] (描述: %s)",
                            currentNode.getName(), matchedRoute.getDestSubnet(), matchedRoute.getMask(),
                            matchedRoute.getNextHopIp(), matchedRoute.getDescription() != null ? matchedRoute.getDescription() : "静态路由"));
                }
            }

            List<TopoEdge> nextEdges = adjacencyMap.getOrDefault(currentNodeId, Collections.emptyList());

            // 若命中了明确的静态路由表项且下一跳非空：
            // 工业路由核心原则：三层转发沿指定下一跳 (Next Hop) 单播递送，严格避免向同广播域其他主机节点无效广播扩散！
            if (matchedRoute != null && matchedRoute.getNextHopIp() != null) {
                String nextHopIp = matchedRoute.getNextHopIp();
                List<TopoEdge> routedEdges = new ArrayList<>();
                for (TopoEdge edge : nextEdges) {
                    TopoNode tn = nodeMap.get(edge.getTargetNodeId());
                    if (tn != null && nextHopIp.equalsIgnoreCase(tn.getIp()) && !Boolean.TRUE.equals(tn.getDisabled())) {
                        routedEdges.add(edge);
                    }
                }
                // 若匹配到指定下一跳且设备存活，严格仅通过该路由出接口递送
                if (!routedEdges.isEmpty()) {
                    nextEdges = routedEdges;
                }
            }

            for (TopoEdge edge : nextEdges) {
                String nextNodeId = edge.getTargetNodeId();
                if (visited.contains(nextNodeId)) {
                    continue;
                }

                TopoNode nextNode = nodeMap.get(nextNodeId);
                if (nextNode == null) {
                    continue;
                }

                boolean nextRerouted = currentRerouted;
                // 特性 0：如果下一跳中间节点处于宕机/断电故障状态，跳过并尝试冗余寻路
                if (Boolean.TRUE.equals(nextNode.getDisabled())) {
                    nextRerouted = true;
                    currentLogs.add(String.format("[WARN] 中继设备节点 [%s] 处于硬件宕机/断电离线状态，跳过此节点并搜寻冗余备用通道！", nextNode.getName()));
                    continue;
                }

                visited.add(nextNodeId);
                List<String> newPath = new ArrayList<>(currentPath);
                newPath.add(nextNodeId);
                queue.add(new PathState(newPath, currentLogs, nextRerouted));
            }
        }

        // 若队列搜索完毕仍未连通，如果此前记录过明确的安全拦截/网闸阻断结果，则返回该阻断信息
        if (blockedResult != null) {
            return blockedResult;
        }

        logs.add(String.format("[WARN] BFS 遍历完成，未找到从 [%s] 至 [%s] 的有效物理连通路径。",
                srcNode.getName(), destNode.getName()));

        return SimResultVO.builder()
                .reachable(false)
                .blocked(false)
                .logs(logs)
                .build();
    }

    /**
     * 判断两个 IP 在指定掩码下是否属于同一子网
     */
    private boolean isSameSubnet(String ip1, String ip2, String mask) {
        if (ip1 == null || ip2 == null || mask == null) {
            return false;
        }
        try {
            long lIp1 = ipToLong(ip1);
            long lIp2 = ipToLong(ip2);
            long lMask = ipToLong(mask);
            return (lIp1 & lMask) == (lIp2 & lMask);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 点分十进制 IPv4 转换为 long 整数
     */
    private long ipToLong(String ip) {
        String[] parts = ip.trim().split("\\.");
        if (parts.length != 4) {
            return 0;
        }
        long result = 0;
        for (int i = 0; i < 4; i++) {
            result = (result << 8) | (Integer.parseInt(parts[i]) & 0xFF);
        }
        return result;
    }

    /**
     * 根据目标 IP 匹配最佳静态路由项 (最长前缀匹配 / Longest Prefix Match, LPM)
     */
    private RouteEntry findBestMatchingRoute(List<RouteEntry> routes, String targetIp) {
        if (routes == null || routes.isEmpty() || targetIp == null) {
            return null;
        }

        RouteEntry bestMatch = null;
        int maxPrefixLength = -1;

        for (RouteEntry entry : routes) {
            String destSubnet = entry.getDestSubnet();
            String mask = entry.getMask() != null ? entry.getMask() : "255.255.255.0";

            // 0.0.0.0/0 默认路由 (Default Route)
            if ("0.0.0.0".equals(destSubnet) || "default".equalsIgnoreCase(destSubnet)) {
                if (maxPrefixLength < 0) {
                    maxPrefixLength = 0;
                    bestMatch = entry;
                }
                continue;
            }

            // 检查 targetIp 是否匹配该路由网段
            if (isSameSubnet(targetIp, destSubnet, mask)) {
                int prefixLen = Long.bitCount(ipToLong(mask));
                if (prefixLen > maxPrefixLength) {
                    maxPrefixLength = prefixLen;
                    bestMatch = entry;
                }
            }
        }

        return bestMatch;
    }
}
