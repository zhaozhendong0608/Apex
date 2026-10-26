package com.jtech.simulator.core.strategy.impl;

import com.jtech.simulator.core.strategy.SignalStrategy;
import com.jtech.simulator.metadata.model.PointMetadata;

/**
 * 双稳态遥信马尔可夫链模型 (0/1 跳变)
 */
public class MarkovSwitchStrategy implements SignalStrategy {

    @Override
    public String getStrategyType() {
        return "SWITCH";
    }

    @Override
    public Object calculate(PointMetadata meta, Object lastValue, long tickCount, String regime) {
        // 停机或跳闸工况下强制断开 (0=分闸)
        if ("SHUTDOWN".equals(regime) || "TRIP_REJECTION".equals(regime)) {
            return 0;
        }

        // 正常并网工况下保持闭合 (1=合闸)
        return 1;
    }
}
