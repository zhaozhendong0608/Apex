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

        // 构建邻接表 (双向物理链路，过滤故障链路)
        Map<String, List<TopoEdge>> adjacencyMap = new HashMap<>();
        for (TopoEdge edge : graph.getEdges()) {
            if (Boolean.TRUE.equals(edge.getDisabled())) {
                logs.add(String.format("[WARN] 物理链路 [%s ➔ %s] 处于故障切断/挖断状态，BFS 寻路跳过此通道。",
                        edge.getSourceNodeId(), edge.getTargetNodeId()));
                continue;
            }
            adjacencyMap.computeIfAbsent(edge.getSourceNodeId(), k -> new ArrayList<>()).add(edge);
            // 构造反向链路以支持无向物理连通
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

        // BFS 搜索队列：保存当前遍历到的路径序列
        Queue<List<String>> queue = new LinkedList<>();
        queue.add(new ArrayList<>(List.of(sourceNodeId)));

        Set<String> visited = new HashSet<>();
        visited.add(sourceNodeId);

        int maxHopsLimit = 50; // 防死循环跳数护栏
        boolean hasRerouted = false; // 是否发生冗余路径自愈

        while (!queue.isEmpty()) {
            List<String> currentPath = queue.poll();
            String currentNodeId = currentPath.get(currentPath.size() - 1);

            // 成功抵达终点
            if (currentNodeId.equals(targetNodeId)) {
                int hops = currentPath.size() - 1;
                int totalLatency = hops * 2; // 默认每跳 2ms 模拟
                if (hasRerouted) {
                    logs.add(String.format("[REDUNDANCY] 工业网络故障应急自愈成功！避开故障节点/链路，成功通过冗余备用路径抵达目的节点 [%s]！(跳数: %d, 预估延迟: %dms)",
                            destNode.getName(), hops, totalLatency));
                } else {
                    logs.add(String.format("[SUCCESS] 数据包顺利抵达目的节点 [%s]，网络连通！(跳数: %d, 预估延迟: %dms)",
                            destNode.getName(), hops, totalLatency));
                }

                return SimResultVO.builder()
                        .reachable(true)
                        .blocked(false)
                        .nodePath(currentPath)
                        .hops(hops)
                        .totalLatencyMs(totalLatency)
                        .logs(logs)
                        .build();
            }

            if (currentPath.size() > maxHopsLimit) {
                logs.add("[ERROR] 遍历跳数超过安全上限 50 跳，疑似存在拓扑环路异常，终止寻路！");
                break;
            }

            List<TopoEdge> nextEdges = adjacencyMap.getOrDefault(currentNodeId, Collections.emptyList());
            for (TopoEdge edge : nextEdges) {
                String nextNodeId = edge.getTargetNodeId();
                if (visited.contains(nextNodeId)) {
                    continue;
                }

                TopoNode nextNode = nodeMap.get(nextNodeId);
                if (nextNode == null) {
                    continue;
                }

                // 特性 0：如果下一跳中间节点处于宕机/断电故障状态，跳过并尝试冗余寻路
                if (Boolean.TRUE.equals(nextNode.getDisabled())) {
                    hasRerouted = true;
                    logs.add(String.format("[WARN] 中继设备节点 [%s] 处于硬件宕机/断电离线状态，跳过此节点并搜寻冗余备用通道！", nextNode.getName()));
                    continue;
                }

                logs.add(String.format("[TRACE] 数据包前移至下一跳: [%s] (类型: %s, IP: %s)",
                        nextNode.getName(), nextNode.getType(), nextNode.getIp()));

                // 特性 1：电力二次系统物理单向安全隔离装置 (GAP) 硬件单向约束
                // 工业规约：仅允许数据从“生产控制大区 (OT_ZONE / 现场层)”单向摆渡至“管理信息大区 (IT_ZONE)”，严禁反向穿透！
                if ("GAP".equalsIgnoreCase(nextNode.getType())) {
                    String srcZone = srcNode.getZone() != null ? srcNode.getZone() : "";
                    String destZone = destNode.getZone() != null ? destNode.getZone() : "";

                    // 判断是否为从 IT_ZONE (或非 OT 区) 试图反向穿透发往 OT_ZONE / PHY_ZONE
                    boolean isReverseTraffic = "IT_ZONE".equalsIgnoreCase(srcZone) 
                            || "OT_ZONE".equalsIgnoreCase(destZone) 
                            || "PHY_ZONE".equalsIgnoreCase(destZone);

                    if (isReverseTraffic) {
                        List<String> blockedPath = new ArrayList<>(currentPath);
                        blockedPath.add(nextNodeId);
                        logs.add(String.format("[SECURITY ALERT] 触发电力二次系统物理单向隔离网闸 [%s] 硬件反向阻断！", nextNode.getName()));
                        logs.add("[ERROR] 国家能源局 36 号令安全防护规约：严禁由管理信息大区 (IT) 向生产控制大区 (OT) 进行反向穿透控制！物理链路强行切断。");

                        return SimResultVO.builder()
                                .reachable(false)
                                .blocked(true)
                                .blockedNodeId(nextNodeId)
                                .blockedRuleId("GAP_HARDWARE_REVERSE_ISOLATION")
                                .nodePath(blockedPath)
                                .hops(blockedPath.size() - 1)
                                .logs(logs)
                                .build();
                    } else {
                        logs.add(String.format("[INFO] 物理单向网闸 [%s] 校验通过：合法单向数据采集摆渡 (OT ➔ IT)，允许单向透传数据包。", nextNode.getName()));
                    }
                }

                // 特性 2：如果下一跳是防火墙/具备 ACL 的节点，执行安全拦截策略校验
                if ("FIREWALL".equalsIgnoreCase(nextNode.getType()) || (nextNode.getAcls() != null && !nextNode.getAcls().isEmpty())) {
                    AclRuleMatcher.MatchResult matchRes = aclMatcher.evaluate(nextNode, srcNode.getIp(), destNode.getIp(), destPort, protocol);
                    logs.add("[TRACE] " + matchRes.reason());

                    if (matchRes.matched() && matchRes.matchedRule().getAction() == AclAction.DENY) {
                        // 命中阻断拦截
                        List<String> blockedPath = new ArrayList<>(currentPath);
                        blockedPath.add(nextNodeId);
                        logs.add(String.format("[ERROR] 数据包被节点 [%s] 的 ACL 规则 [%s] 强制拦截阻断！网络不可达。",
                                nextNode.getName(), matchRes.matchedRule().getId()));

                        return SimResultVO.builder()
                                .reachable(false)
                                .blocked(true)
                                .blockedNodeId(nextNodeId)
                                .blockedRuleId(matchRes.matchedRule().getId())
                                .nodePath(blockedPath)
                                .hops(blockedPath.size() - 1)
                                .logs(logs)
                                .build();
                    }
                }

                visited.add(nextNodeId);
                List<String> newPath = new ArrayList<>(currentPath);
                newPath.add(nextNodeId);
                queue.add(newPath);
            }
        }

        logs.add(String.format("[WARN] BFS 遍历完成，未找到从 [%s] 至 [%s] 的有效物理连通路径。",
                srcNode.getName(), destNode.getName()));

        return SimResultVO.builder()
                .reachable(false)
                .blocked(false)
                .logs(logs)
                .build();
    }
}
