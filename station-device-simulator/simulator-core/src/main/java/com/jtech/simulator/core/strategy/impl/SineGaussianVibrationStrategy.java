package com.jtech.simulator.core.strategy.impl;

import com.jtech.simulator.core.strategy.SignalStrategy;
import com.jtech.simulator.metadata.model.PointMetadata;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 旋转机械高频正弦叠加高斯白噪声与二倍频偏心模型
 * y(t) = A1 * sin(omega * t) + A2 * sin(2 * omega * t) + N(0, sigma^2)
 */
public class SineGaussianVibrationStrategy implements SignalStrategy {

    @Override
    public String getStrategyType() {
        return "VIBRATION";
    }

    @Override
    public Object calculate(PointMetadata meta, Object lastValue, long tickCount, String regime) {
        if ("SHUTDOWN".equals(regime)) {
            return 0.002; // 停机时仅剩微弱环境地振
        }

        double baseAmp = (meta.getBaseValue() != null) ? meta.getBaseValue() : 0.05; // 0.05mm
        if ("FULL_LOAD".equals(regime)) {
            baseAmp *= 1.25;
        }

        // 工频基波 (omega * t) 与二倍频 (2 * omega * t)
        double omega = 0.5;
        double a1 = 0.02 * Math.sin(omega * tickCount);
        double a2 = 0.008 * Math.sin(2 * omega * tickCount);
        double noise = ThreadLocalRandom.current().nextGaussian() * 0.005;

        double val = baseAmp + a1 + a2 + noise;
        if (val < 0.0) val = 0.001;

        return Math.round(val * 1000.0) / 1000.0;
    }
}
