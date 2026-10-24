package com.apex.network.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 端到端仿真连通性测试请求 DTO
 */
@Data
public class SimRequestDTO {

    /**
     * 拓扑方案主键 ID
     */
    @NotNull(message = "拓扑方案 ID 不能为空")
    private Long topologyId;

    /**
     * 源设备节点 ID (如 dev_xxx)
     */
    @NotBlank(message = "源设备节点不能为空")
    private String sourceNodeId;

    /**
     * 目标设备节点 ID
     */
    @NotBlank(message = "目标设备节点不能为空")
    private String targetNodeId;

    /**
     * 测试目的端口 (如 6030, 80, 22)
     */
    private Integer destPort;

    /**
     * 测试协议 (TCP, UDP, ICMP)
     */
    private String protocol = "TCP";
}
