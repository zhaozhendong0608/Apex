package com.jtech.simulator.core;

import com.jtech.simulator.core.engine.SimulationEngine;
import com.jtech.simulator.core.strategy.impl.LagFilterTemperatureStrategy;
import com.jtech.simulator.core.strategy.impl.StepPowerLoadStrategy;
import com.jtech.simulator.metadata.model.PointMetadata;
import com.jtech.simulator.metadata.spi.PointMetadataProvider;
import com.jtech.simulator.sink.model.TimeSeriesRecord;
import com.jtech.simulator.sink.spi.TimeSeriesSink;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 核心仿真波形与因果联动单元测试桩
 */
public class SimulationEngineUnitTest {

    @Test
    public void testLagFilterTemperatureStrategy() {
        LagFilterTemperatureStrategy strategy = new LagFilterTemperatureStrategy();
        PointMetadata meta = PointMetadata.builder()
                .code("U1_TEMP_TEST")
                .baseValue(58.0)
                .minValue(0.0)
                .maxValue(120.0)
                .build();

        // 初始温升从 25.0 开始逼近 58.0
        Object v1 = strategy.calculate(meta, 25.0, 1L, "STEADY_300");
        Assertions.assertTrue(((Number) v1).doubleValue() > 25.0, "一阶惯性滞后应该单调温升逼近额定值");
        Assertions.assertTrue(((Number) v1).doubleValue() < 58.5, "温升不应瞬间突变");
    }

    @Test
    public void testTripRejectionCausality() {
        StepPowerLoadStrategy powerStrategy = new StepPowerLoadStrategy();
        PointMetadata meta = PointMetadata.builder()
                .code("U1_POWER_TEST")
                .baseValue(300.0)
                .build();

        // 稳态下负荷约为 300MW
        Object normalPower = powerStrategy.calculate(meta, 300.0, 1L, "STEADY_300");
        Assertions.assertTrue(((Number) normalPower).doubleValue() > 290.0);

        // 甩负荷跳闸下，因果连锁强制瞬间归零
        Object tripPower = powerStrategy.calculate(meta, normalPower, 2L, "TRIP_REJECTION");
        Assertions.assertEquals(0.0, ((Number) tripPower).doubleValue(), "跳闸工况下有功负荷必须断崖瞬间归零！");
    }

    @Test
    public void testEngineTickAndBatchWrite() {
        List<TimeSeriesRecord> collected = new ArrayList<>();

        PointMetadataProvider mockProvider = new PointMetadataProvider() {
            @Override
            public List<PointMetadata> loadAllPoints() {
                return Collections.singletonList(
                        PointMetadata.builder()
                                .id(1L)
                                .code("TEST_PAD_1")
                                .name("测试瓦温")
                                .baseValue(58.0)
                                .strategyType("TEMPERATURE")
                                .build()
                );
            }

            @Override
            public PointMetadata getPointByCode(String pointCode) {
                return null;
            }
        };

        TimeSeriesSink mockSink = new TimeSeriesSink() {
            @Override
            public void write(TimeSeriesRecord record) {
                collected.add(record);
            }

            @Override
            public void writeBatch(List<TimeSeriesRecord> records) {
                collected.addAll(records);
            }

            @Override
            public void flush() {}

            @Override
            public String getStatus() {
                return "MOCK_CONNECTED";
            }
        };

        SimulationEngine engine = new SimulationEngine(mockProvider, mockSink);
        engine.start();

        // 执行一次手动 Tick
        engine.tick();

        Assertions.assertFalse(collected.isEmpty(), "Engine 调度 Tick 必须产生下刷数据帧");
        Assertions.assertEquals("TEST_PAD_1", collected.get(0).getKid());
        Assertions.assertEquals(0, collected.get(0).getQuality(), "额定工况下数据质量码应为 0");

        // 注入故障
        engine.injectFault("TEST_PAD_1", 99.9);
        engine.tick();

        TimeSeriesRecord faultRecord = collected.get(collected.size() - 1);
        Assertions.assertEquals(99.9, ((Number) faultRecord.getValue()).doubleValue(), "故障注入后数值应被锁定为 99.9");
        Assertions.assertEquals(1, faultRecord.getQuality(), "故障注入异常数据质量码应为 1");

        engine.stop();
    }
}
