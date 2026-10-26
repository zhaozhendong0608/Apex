package com.jtech.simulator.core.strategy;

import com.jtech.simulator.metadata.model.PointMetadata;

/**
 * 物理波形生成策略接口
 */
public interface SignalStrategy {

    /**
     * 策略支持的类型: TEMPERATURE, VIBRATION, POWER, SWITCH, HYDRAULIC
     */
    String getStrategyType();

    /**
     * 计算当前时钟周期的物理数值
     *
     * @param meta 测点元数据定义
     * @param lastValue 上一时刻的值
     * @param tickCount 全局调度时钟序号
     * @param regime 全局宏观工况 (SHUTDOWN, STARTUP, STEADY_300, FULL_LOAD, TRIP_REJECTION)
     * @return 当前仿真值
     */
    Object calculate(PointMetadata meta, Object lastValue, long tickCount, String regime);
}
