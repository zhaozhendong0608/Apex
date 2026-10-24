package com.apex.network.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * NAT 网络地址转换规则实体 (SNAT / DNAT)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NatRule implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 规则唯一 ID
     */
    private String id;

    /**
     * 转换类型: SNAT (源地址伪装出境) 或 DNAT (目的地址端口映射入站)
     */
    private String type;

    /**
     * 原源地址 / 内部真实源网段 (仅 SNAT 使用，如 192.168.1.0/24 或 192.168.1.10)
     */
    private String origSrc;

    /**
     * 转换后源地址 / 出境公网或边界伪装 IP (仅 SNAT 使用，如 10.0.0.1)
     */
    private String transSrc;

    /**
     * 外部目的 IP / 边界对外暴露虚拟 IP (仅 DNAT 使用，如 10.0.0.1)
     */
    private String extIp;

    /**
     * 外部目的端口 (仅 DNAT 使用，如 8080)
     */
    private Integer extPort;

    /**
     * 内部真实服务目的 IP (仅 DNAT 使用，如 172.16.1.100)
     */
    private String intIp;

    /**
     * 内部真实服务目的端口 (仅 DNAT 使用，如 6030)
     */
    private Integer intPort;

    /**
     * 协议类型 (TCP, UDP, ALL)
     */
    @Builder.Default
    private String protocol = "TCP";

    /**
     * 规则描述
     */
    private String description;
}
