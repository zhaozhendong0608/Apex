package com.apex.network.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 网络拓扑图聚合实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopoGraph implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 拓扑 ID
     */
    private Long id;

    /**
     * 拓扑名称
     */
    private String name;

    /**
     * 设备节点列表
     */
    @Builder.Default
    private List<TopoNode> nodes = new ArrayList<>();

    /**
     * 物理与逻辑链路列表
     */
    @Builder.Default
    private List<TopoEdge> edges = new ArrayList<>();
}
