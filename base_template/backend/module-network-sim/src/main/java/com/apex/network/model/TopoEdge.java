package com.apex.network.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 网络拓扑物理/逻辑链路边
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopoEdge implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 链路唯一 ID (edge_xxx)
     */
    private String id;

    /**
     * 源设备节点 ID
     */
    private String sourceNodeId;

    /**
     * 目的设备节点 ID
     */
    private String targetNodeId;

    /**
     * 链路带宽 (如 "1000 Mbps")
     */
    private String bandwidth;

    /**
     * 单向链路延时 (单位: 毫秒 ms)
     */
    @Builder.Default
    private Integer latencyMs = 2;
}
