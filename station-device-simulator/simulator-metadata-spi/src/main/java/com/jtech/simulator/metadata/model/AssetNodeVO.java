package com.jtech.simulator.metadata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 资产树节点 VO (对齐 etl_station 规范，支持按需懒加载与滑窗分页)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetNodeVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 节点唯一主键ID */
    private Long id;

    /** 父节点ID (0 为根电站) */
    private Long parentId;

    /** 节点名称 */
    private String name;

    /** 节点标识符 (点位测点Code) */
    private String code;

    /** 节点类型: station (电站), unit (机组), device (设备), component (部件), point (测点) */
    private String type;

    /** 排序号 */
    private Integer orderNum;

    /** 祖先ID路径链 (逗号分隔，如 "0,1,2") */
    private String ancestors;

    /** 是否为叶子节点 (point 为叶子) */
    private Boolean leaf;

    /** 子节点总数 (供前端判断是否展示滑窗分页) */
    private Integer childCount;

    /** 测点属性 (仅 point 类型有效) */
    private String metricType;
    private String strategyType;
    private Double baseValue;
    private Double minValue;
    private Double maxValue;
    private String unit;

    /** 子节点列表 (可选) */
    private List<AssetNodeVO> children;
}
