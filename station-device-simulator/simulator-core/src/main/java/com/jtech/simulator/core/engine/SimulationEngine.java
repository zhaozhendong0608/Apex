package com.jtech.simulator.core.engine;

import com.jtech.simulator.core.strategy.SignalStrategy;
import com.jtech.simulator.core.strategy.impl.*;
import com.jtech.simulator.metadata.model.PointMetadata;
import com.jtech.simulator.metadata.spi.PointMetadataProvider;
import com.jtech.simulator.sink.model.TimeSeriesRecord;
import com.jtech.simulator.sink.spi.TimeSeriesSink;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 物理机理波形仿真与因果调度中心 (Simulation Engine Core)
 */
@Slf4j
public class SimulationEngine {

    private final PointMetadataProvider metadataProvider;
    private final TimeSeriesSink timeSeriesSink;

    /** 策略字典 */
    private final Map<String, SignalStrategy> strategyMap = new ConcurrentHashMap<>();

    /** 测点无锁状态暂存器 (pointCode -> 上一时刻值) */
    private final Map<String, Object> stateStore = new ConcurrentHashMap<>();

    /** 当前已加载的点位元数据缓存 */
    private final List<PointMetadata> registeredPoints = new CopyOnWriteArrayList<>();

    /** 全局宏观工况 (SHUTDOWN, STARTUP, STEADY_300, FULL_LOAD, TRIP_REJECTION) */
    @Getter
    @Setter
    private volatile String currentRegime = "STEADY_300";

    /** 动态故障注入容器 (pointCode -> 锁定数值) */
    private final Map<String, Double> faultOverrideMap = new ConcurrentHashMap<>();

    /** 调度时钟 */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "simulation-engine-tick");
        t.setDaemon(true);
        return t;
    });

    private final AtomicLong tickCounter = new AtomicLong(0);

    @Getter
    private volatile boolean running = false;

    public SimulationEngine(PointMetadataProvider metadataProvider, TimeSeriesSink timeSeriesSink) {
        this.metadataProvider = metadataProvider;
        this.timeSeriesSink = timeSeriesSink;
        registerDefaultStrategies();
    }

    private void registerDefaultStrategies() {
        registerStrategy(new LagFilterTemperatureStrategy());
        registerStrategy(new SineGaussianVibrationStrategy());
        registerStrategy(new StepPowerLoadStrategy());
        registerStrategy(new MarkovSwitchStrategy());
        registerStrategy(new HydraulicDampingStrategy());
    }

    public void registerStrategy(SignalStrategy strategy) {
        strategyMap.put(strategy.getStrategyType().toUpperCase(), strategy);
    }

    /**
     * 引擎启动：加载元数据并启动每秒调度 Tick
     */
    public synchronized void start() {
        if (running) return;

        // 1. 初始化点位元数据
        List<PointMetadata> points = metadataProvider.loadAllPoints();
        registeredPoints.clear();
        registeredPoints.addAll(points);

        // 2. 预热初始数值
        for (PointMetadata point : points) {
            stateStore.put(point.getCode(), point.getBaseValue() != null ? point.getBaseValue() : 0.0);
        }

        running = true;

        // 3. 启动每秒 Tick 定时下刷
        scheduler.scheduleAtFixedRate(this::tick, 0, 1000, TimeUnit.MILLISECONDS);
        log.info("🚀 电站物理数字孪生模拟引擎已启动！挂载测点数: {}, 初始工况: {}", points.size(), currentRegime);
    }

    /**
     * 核心每秒物理演变主循环
     */
    public void tick() {
        if (!running) return;
        long tickCount = tickCounter.incrementAndGet();
        long sec = System.currentTimeMillis() / 1000;
        long ms = System.currentTimeMillis();

        List<TimeSeriesRecord> batch = new ArrayList<>(registeredPoints.size());

        for (PointMetadata point : registeredPoints) {
            Object lastVal = stateStore.get(point.getCode());
            String strategyType = point.getStrategyType() != null ? point.getStrategyType().toUpperCase() : "TEMPERATURE";
            SignalStrategy strategy = strategyMap.getOrDefault(strategyType, strategyMap.get("TEMPERATURE"));

            Object currentVal;
            int quality = 0;

            // 检查是否存在人工/剧本故障注入覆盖
            Double faultOverride = faultOverrideMap.get(point.getCode());
            if (faultOverride != null) {
                currentVal = faultOverride;
                quality = 1; // 异常质量码
            } else {
                currentVal = strategy.calculate(point, lastVal, tickCount, currentRegime);
            }

            // 更新状态机
            stateStore.put(point.getCode(), currentVal);

            // 构造时序数据帧
            TimeSeriesRecord record = TimeSeriesRecord.builder()
                    .kid(point.getCode())
                    .timestampSec(sec)
                    .timestampMs(ms)
                    .value(currentVal)
                    .quality(quality)
                    .regimeTag(currentRegime)
                    .build();

            batch.add(record);
        }

        // 推送到时序持久化 Sink (JSDB)
        if (!batch.isEmpty()) {
            timeSeriesSink.writeBatch(batch);
        }
    }

    /**
     * 动态注入数值突变 (超温/越限)
     */
    public void injectFault(String pointCode, double targetValue) {
        faultOverrideMap.put(pointCode, targetValue);
        log.warn("⚠️ 动态注入故障：锁定点位 {} 目标值 {}", pointCode, targetValue);
    }

    /**
     * 清除故障，恢复正常物理演化
     */
    public void clearFault(String pointCode) {
        faultOverrideMap.remove(pointCode);
        log.info("✅ 恢复正常：解除点位 {} 故障锁定", pointCode);
    }

    public synchronized void stop() {
        running = false;
        scheduler.shutdown();
        log.info("🛑 模拟引擎已停止调度。");
    }

    public List<PointMetadata> getRegisteredPoints() {
        return Collections.unmodifiableList(registeredPoints);
    }

    public Object getCurrentValue(String pointCode) {
        return stateStore.get(pointCode);
    }
}
