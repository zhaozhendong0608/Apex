package com.apex.network.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * ACL 安全访问控制策略规则
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AclRule implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 规则唯一 ID (如 acl_1)
     */
    private String id;

    /**
     * 规则优先级 (1~1000，数值越小优先级越高，按顺序匹配短路)
     */
    private Integer priority;

    /**
     * 执行动作 (PERMIT 放行, DENY 拦截)
     */
    private AclAction action;

    /**
     * 源 IP 或 CIDR 网段 (如 192.168.1.10 或 192.168.1.0/24，空表示任意)
     */
    private String srcIp;

    /**
     * 目的 IP 或 CIDR 网段 (如 10.1.1.5 或 10.0.0.0/24，空表示任意)
     */
    private String destIp;

    /**
     * 目标服务端口 (如 80, 3306, 6030，null 表示全端口)
     */
    private Integer destPort;

    /**
     * 协议类型 (TCP, UDP, ICMP, ANY)
     */
    private String protocol;

    /**
     * 规则白话描述
     */
    private String description;
}
