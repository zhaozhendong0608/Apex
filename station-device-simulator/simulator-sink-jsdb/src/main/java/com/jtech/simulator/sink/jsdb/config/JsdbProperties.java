package com.jtech.simulator.sink.jsdb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 极生时序数据库 (JSDB) 连接与防缺陷参数配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "jsdb")
public class JsdbProperties {

    /** 极生服务端地址 (默认: 127.0.0.1:9000) */
    private String host = "127.0.0.1";
    private int port = 9000;

    /** 超表名与库名 (默认: tdb / position) */
    private String database = "tdb";
    private String tableName = "position";

    /** 单批次最大写入记录数 (默认: 500) */
    private int batchSize = 500;

    /** 自动触发 commit / merge 批次数 (默认: 50批) */
    private int flushThreshold = 50;

    /** 是否启用单点安全批次规约 (防 P-014 / 2026-09-14 决议) */
    private boolean safeSinglePointMode = true;
}
