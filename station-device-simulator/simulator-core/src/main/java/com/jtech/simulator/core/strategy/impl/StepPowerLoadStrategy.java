package com.jtech.simulator.core.strategy.impl;

import com.jtech.simulator.core.strategy.SignalStrategy;
import com.jtech.simulator.metadata.model.PointMetadata;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 电气负荷阶梯工况跃迁模型
 */
public class StepPowerLoadStrategy implements SignalStrategy {

    @Override
    public String getStrategyType() {
        return "POWER";
    }

    @Override
    public Object calculate(PointMetadata meta, Object lastValue, long tickCount, String regime) {
        if ("SHUTDOWN".equals(regime) || "TRIP_REJECTION".equals(regime)) {
            return 0.0; // 停机或跳闸瞬间有功断崖归零
        }
        if ("STARTUP".equals(regime)) {
            return 10.0; // 空载并网初始负荷
        }

        double basePower = "FULL_LOAD".equals(regime) ? 400.0 : 300.0;
        double noise = (ThreadLocalRandom.current().nextDouble() - 0.5) * 1.5;
        double val = basePower + noise;

        return Math.round(val * 10.0) / 10.0;
    }
}
