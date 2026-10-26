package com.jtech.simulator.metadata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 测点统一元数据抽象模型 (SSOT)
 * 映射 MySQL fusion_asset 与 fusion_asset_point
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointMetadata implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 测点全局ID */
    private Long id;

    /** 测点编码 (对应极生中的 kid / 测点名，如: U1_THRUST_PAD_TEMP_01) */
    private String code;

    /** 测点中文名称 (如: "1号发电机推力瓦温1点") */
    private String name;

    /** 所属电站名称 */
    private String stationName;

    /** 所属机组名称 (如: "1号水轮发电机组") */
    private String unitName;

    /** 所属设备名称 (如: "1号发电机") */
    private String deviceName;

    /** 所属部件名称 (如: "推力轴承") */
    private String componentName;

    /** 物理类型: analog (模拟量) / discrete (开关量) */
    private String metricType;

    /** 匹配的仿真算法策略: TEMPERATURE, VIBRATION, POWER, SWITCH, HYDRAULIC */
    private String strategyType;

    /** 额定基准值 (如 58.0) */
    private Double baseValue;

    /** 物理量下限 (如 0.0) */
    private Double minValue;

    /** 物理量上限 (如 120.0) */
    private Double maxValue;

    /** 工程物理单位 (如: "℃", "mm", "MW", "状态") */
    private String unit;
}
