package com.jtech.simulator.core.strategy.impl;

import com.jtech.simulator.core.strategy.SignalStrategy;
import com.jtech.simulator.metadata.model.PointMetadata;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 水工水力与导叶开度耦合阻尼模型
 */
public class HydraulicDampingStrategy implements SignalStrategy {

    @Override
    public String getStrategyType() {
        return "HYDRAULIC";
    }

    @Override
    public Object calculate(PointMetadata meta, Object lastValue, long tickCount, String regime) {
        if ("SHUTDOWN".equals(regime)) {
            return 0.0;
        }
        if ("TRIP_REJECTION".equals(regime)) {
            return 15.0; // 甩负荷快速收回到空载开度
        }

        double baseOpening = "FULL_LOAD".equals(regime) ? 95.0 : 78.5;
        double noise = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.4;
        double val = baseOpening + noise;

        return Math.round(val * 10.0) / 10.0;
    }
}
