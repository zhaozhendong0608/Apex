package com.apex.network.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建/更新拓扑方案 DTO
 */
@Data
public class TopologySaveDTO {

    /**
     * 拓扑主键 ID (新建为空，修改传值)
     */
    private Long id;

    /**
     * 拓扑方案名称
     */
    @NotBlank(message = "拓扑方案名称不能为空")
    private String name;

    /**
     * 拓扑详细描述
     */
    private String description;

    /**
     * AntV X6 前端序列化画布 JSON 数据
     */
    private String canvasData;

    /**
     * 设备节点数
     */
    private Integer nodeCount;

    /**
     * 链路数
     */
    private Integer edgeCount;
}
