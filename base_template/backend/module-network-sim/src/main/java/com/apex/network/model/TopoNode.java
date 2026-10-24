package com.apex.network.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 网络拓扑设备节点实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopoNode implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 节点唯一 ID (node_xxx)
     */
    private String id;

    /**
     * 设备显示名称 (研发办公PC, 工业核心防火墙, TDengine时序库)
     */
    private String name;

    /**
     * 设备类型 (HOST, SWITCH, ROUTER, FIREWALL, GATEWAY, NEURON, TDENGINE, SENSOR, SERVER)
     */
    private String type;

    /**
     * 所属安全大区 (IT_ZONE, DMZ_ZONE, OT_ZONE, PHY_ZONE)
     */
    private String zone;

    /**
     * IP 地址 (如 192.168.1.10)
     */
    private String ip;

    /**
     * 子网掩码 (如 255.255.255.0)
     */
    private String subnet;

    /**
     * 默认网关 (如 192.168.1.1)
     */
    private String gateway;

    /**
     * 该节点配置的 ACL 安全访问控制规则链
     */
    @Builder.Default
    private List<AclRule> acls = new ArrayList<>();

    /**
     * 该路由器/三层核心设备配置的静态路由表 (Routing Table)
     */
    @Builder.Default
    private List<RouteEntry> routes = new ArrayList<>();

    /**
     * 该防火墙/网关设备配置的 NAT 网络地址转换规则表 (SNAT / DNAT)
     */
    @Builder.Default
    private List<NatRule> natRules = new ArrayList<>();

    /**
     * 节点设备宕机/断电故障状态 (true: 宕机离线, false: 正常运行)
     */
    @Builder.Default
    private Boolean disabled = false;
}
