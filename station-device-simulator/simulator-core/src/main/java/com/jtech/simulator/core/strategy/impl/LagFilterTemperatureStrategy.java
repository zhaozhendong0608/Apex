package com.jtech.simulator.core.strategy.impl;

import com.jtech.simulator.core.strategy.SignalStrategy;
import com.jtech.simulator.metadata.model.PointMetadata;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 热工大惯性温升滤波模型 (Lag Filter)
 * val_t = val_{t-1} + alpha * (target - val_{t-1}) + GaussianNoise
 */
public class LagFilterTemperatureStrategy implements SignalStrategy {

    private static final double ALPHA = 0.03; // 热惯性衰减系数

    @Override
    public String getStrategyType() {
        return "TEMPERATURE";
    }

    @Override
    public Object calculate(PointMetadata meta, Object lastValue, long tickCount, String regime) {
        double currentVal = (lastValue instanceof Number) ? ((Number) lastValue).doubleValue() : meta.getBaseValue();
        double targetVal = meta.getBaseValue();

        // 根据工况牵引目标温度
        if ("SHUTDOWN".equals(regime)) {
            targetVal = 25.0; // 室温停机
        } else if ("STEADY_300".equals(regime)) {
            targetVal = meta.getBaseValue(); // 额定稳态 (如 58.0℃)
        } else if ("FULL_LOAD".equals(regime)) {
            targetVal = meta.getBaseValue() + 9.5; // 满负荷发热 (如 67.5℃)
        } else if ("TRIP_REJECTION".equals(regime)) {
            targetVal = 35.0; // 甩负荷降温
        }

        // 一阶惯性滞后平滑逼近
        double noise = ThreadLocalRandom.current().nextGaussian() * 0.05;
        double nextVal = currentVal + ALPHA * (targetVal - currentVal) + noise;

        // 边界防护
        if (meta.getMinValue() != null && nextVal < meta.getMinValue()) nextVal = meta.getMinValue();
        if (meta.getMaxValue() != null && nextVal > meta.getMaxValue()) nextVal = meta.getMaxValue();

        return Math.round(nextVal * 100.0) / 100.0;
    }
}
