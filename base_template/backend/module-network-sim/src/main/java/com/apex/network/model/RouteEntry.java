package com.apex.network.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 路由器/三层交换机静态路由表项实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 路由规则唯一 ID (route_xxx)
     */
    private String id;

    /**
     * 目标网段 (Destination Network, 如 172.16.1.0 或 0.0.0.0 代表默认路由)
     */
    private String destSubnet;

    /**
     * 子网掩码 (如 255.255.255.0 或 0.0.0.0)
     */
    private String mask;

    /**
     * 下一跳 IP 地址 (Next Hop IP, 如 10.0.0.1 或 172.16.1.1)
     */
    private String nextHopIp;

    /**
     * 路由规则备注描述 (如 "OT生产控制网段静态路由")
     */
    private String description;
}
