package com.jtech.simulator.sink.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 时序数据物理帧 (映射极生超表存储模型)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 测点标识 (对应极生 kid / 测点编码，如: U1_THRUST_PAD_TEMP_01) */
    private String kid;

    /** 10 位秒级时间戳 (如: 1716384000) */
    private long timestampSec;

    /** 13 位毫秒级时间戳 (如: 1716384000123) */
    private long timestampMs;

    /** 物理数值 (Double 或 Integer) */
    private Object value;

    /** 数据质量码 (0=正常稳态, 1=演练/故障注入异常, 2=坏质量/开路) */
    private int quality;

    /** 当前工况标签 (如: "STEADY_300", "COOLING_FAIL") */
    private String regimeTag;
}
