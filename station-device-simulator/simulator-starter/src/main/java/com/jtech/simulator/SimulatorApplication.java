package com.jtech.simulator;

import com.jtech.simulator.core.engine.SimulationEngine;
import com.jtech.simulator.metadata.spi.PointMetadataProvider;
import com.jtech.simulator.sink.spi.TimeSeriesSink;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * 电站设备模拟服务 (Station Device Simulator) 启动入口
 */
@SpringBootApplication(scanBasePackages = "com.jtech.simulator")
@MapperScan("com.jtech.simulator.metadata.mysql.mapper")
public class SimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimulatorApplication.class, args);
    }

    @Bean
    public SimulationEngine simulationEngine(PointMetadataProvider metadataProvider, TimeSeriesSink timeSeriesSink) {
        return new SimulationEngine(metadataProvider, timeSeriesSink);
    }

    @Bean
    public CommandLineRunner runner(SimulationEngine simulationEngine) {
        return args -> {
            // 启动时自动运行仿真调度引擎
            simulationEngine.start();
        };
    }
}
