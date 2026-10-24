package com.apex.network.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 仿真测试执行结果视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否可达
     */
    private Boolean reachable;

    /**
     * 是否被 ACL 防火墙拦截阻断
     */
    private Boolean blocked;

    /**
     * 阻断发生的节点 ID
     */
    private String blockedNodeId;

    /**
     * 命中的阻断规则 ID
     */
    private String blockedRuleId;

    /**
     * 途径的节点 ID 序列
     */
    @Builder.Default
    private List<String> nodePath = new ArrayList<>();

    /**
     * 累计跳数 (Hops)
     */
    private Integer hops;

    /**
     * 预估链路端到端总延时 (毫秒)
     */
    private Integer totalLatencyMs;

    /**
     * 仿真审计与追踪日志列表
     */
    @Builder.Default
    private List<String> logs = new ArrayList<>();
}
