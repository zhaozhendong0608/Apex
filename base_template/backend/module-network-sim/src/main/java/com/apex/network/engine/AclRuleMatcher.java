package com.apex.network.engine;

import com.apex.network.model.AclAction;
import com.apex.network.model.AclRule;
import com.apex.network.model.TopoNode;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * ACL 安全规则过滤匹配引擎
 */
@Component
public class AclRuleMatcher {

    /**
     * 规则匹配检查结果
     */
    public record MatchResult(boolean matched, AclRule matchedRule, String reason) {}

    /**
     * 针对目标节点执行 ACL 规则集检查
     *
     * @param node        当前评估节点 (如防火墙/网关)
     * @param srcIp       数据包源 IP
     * @param destIp      数据包目的 IP
     * @param destPort    目的端口
     * @param protocol    协议类型 (TCP, UDP, ICMP)
     * @return 规则匹配结果
     */
    public MatchResult evaluate(TopoNode node, String srcIp, String destIp, Integer destPort, String protocol) {
        if (node == null || node.getAcls() == null || node.getAcls().isEmpty()) {
            // 无任何配置规则：默认放行 (Default Permit)
            return new MatchResult(false, null, "节点无 ACL 规则，默认放行");
        }

        // 按 priority 升序排序 (数字越小优先级越高，短路匹配)
        List<AclRule> sortedRules = node.getAcls().stream()
                .sorted(Comparator.comparing(r -> r.getPriority() != null ? r.getPriority() : 999))
                .toList();

        for (AclRule rule : sortedRules) {
            if (isRuleHit(rule, srcIp, destIp, destPort, protocol)) {
                String actionText = rule.getAction() == AclAction.PERMIT ? "放行 (PERMIT)" : "阻断拦截 (DENY)";
                String reason = String.format("命中规则 [%s]: %s (端口: %s, 描述: %s)",
                        rule.getId(), actionText, rule.getDestPort() == null ? "全部" : rule.getDestPort(), rule.getDescription());
                return new MatchResult(true, rule, reason);
            }
        }

        // 全部规则未匹配：执行默认放行 (Default Permit)
        return new MatchResult(false, null, "未命中任何自定义 ACL 规则，执行默认放行 (Default Permit)");
    }

    /**
     * 判定单条规则是否精准命中
     */
    private boolean isRuleHit(AclRule rule, String srcIp, String destIp, Integer destPort, String protocol) {
        // 1. 协议比对
        if (rule.getProtocol() != null && !rule.getProtocol().equalsIgnoreCase("ANY")) {
            if (protocol != null && !rule.getProtocol().equalsIgnoreCase(protocol)) {
                return false;
            }
        }

        // 2. 目的端口比对
        if (rule.getDestPort() != null && destPort != null) {
            if (!rule.getDestPort().equals(destPort)) {
                return false;
            }
        }

        // 3. 源 IP 网段简易比对 (支持直接相等、CIDR 前缀匹配或空放行)
        if (rule.getSrcIp() != null && !rule.getSrcIp().trim().isEmpty() && srcIp != null) {
            if (!matchesIp(rule.getSrcIp(), srcIp)) {
                return false;
            }
        }

        // 4. 目的 IP 网段简易比对
        if (rule.getDestIp() != null && !rule.getDestIp().trim().isEmpty() && destIp != null) {
            if (!matchesIp(rule.getDestIp(), destIp)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 简易 IP / 子网段前缀匹配
     */
    private boolean matchesIp(String ruleIp, String targetIp) {
        if (ruleIp.equalsIgnoreCase("ANY") || ruleIp.equals("0.0.0.0/0")) {
            return true;
        }
        if (ruleIp.contains("/")) {
            // CIDR 网段匹配：提取前缀比对
            String prefix = ruleIp.substring(0, ruleIp.lastIndexOf('.'));
            return targetIp.startsWith(prefix);
        }
        return ruleIp.equalsIgnoreCase(targetIp);
    }
}
