package com.apex.network.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 网络拓扑方案实体类 (对应数据表: net_topology)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("net_topology")
public class NetTopology implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 拓扑方案主键自增 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 拓扑方案名称
     */
    private String name;

    /**
     * 拓扑详细描述
     */
    private String description;

    /**
     * 前端 AntV X6 序列化画布图元 JSON 数据
     */
    @TableField("canvas_data")
    private String canvasData;

    /**
     * 包含设备节点总数
     */
    @TableField("node_count")
    private Integer nodeCount;

    /**
     * 包含链路总数
     */
    @TableField("edge_count")
    private Integer edgeCount;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
