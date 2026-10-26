package com.jtech.simulator.sink.jsdb.client;

import com.jsdb.XPhy.TDBAPI;
import com.jtech.simulator.sink.jsdb.config.JsdbProperties;
import com.pydk.util.DBMS;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * 极生底层驱动客户端包装器 (封装 DBMS 与 TDBAPI 句柄)
 */
@Slf4j
@Component
public class JsdbClientWrapper {

    private final JsdbProperties properties;

    @Getter
    private DBMS dbms;

    @Getter
    private volatile boolean connected = false;

    public JsdbClientWrapper(JsdbProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public synchronized void init() {
        log.info("🔌 开始连接极生时序数据库 (JSDB) -> {}:{}, db={}, table={}", 
                properties.getHost(), properties.getPort(), properties.getDatabase(), properties.getTableName());
        try {
            String address = String.format("TDB,%s,%s,%d,%s,0,0",
                    properties.getDatabase(),
                    properties.getHost(),
                    properties.getPort(),
                    properties.getTableName());

            // 1. 遵循 etl_station 官方模式: 若表未初始化或 schema 为空，使用 flag=1 初始化建表
            try {
                DBMS createDbms = new DBMS();
                createDbms.load(new String[]{address}, 1);
                com.pydk.proc.TDB.Schm schema = new com.pydk.proc.TDB.Schm();
                schema.dbname = properties.getTableName();
                schema.alias = "";
                TDBAPI.addColumn(schema, "time", "秒戳", com.pydk.proc.TDB.VarType.TDB_INTEGER, 0, 0, 1);
                TDBAPI.addColumn(schema, "timestamp", "毫秒戳", com.pydk.proc.TDB.VarType.TDB_LONG, 0, 0, 1);
                TDBAPI.addColumn(schema, "value", "测量值", com.pydk.proc.TDB.VarType.TDB_DOUBLE, 7, 0, 1);
                TDBAPI.addColumn(schema, "flag", "异常标记", com.pydk.proc.TDB.VarType.TDB_CHAR, 0, 0, 1);
                try {
                    TDBAPI.create(createDbms, properties.getDatabase(), schema);
                    log.info("✅ 极生时序超表 {} 创建/注册成功", properties.getTableName());
                } catch (Exception ex) {
                    log.debug("极生表已存在或跳过建表: {}", ex.getMessage());
                } finally {
                    createDbms.close();
                }
            } catch (Throwable ct) {
                log.warn("检查/创建极生表提示: {}", ct.getMessage());
            }

            // 2. 正常以读写模式 (flag=0) 加载表结构
            this.dbms = new DBMS();
            this.dbms.load(new String[]{address}, 0);
            this.connected = true;
            log.info("✅ 成功连通极生时序数据库 DBMS 引擎！address: {}", address);
        } catch (Throwable t) {
            log.warn("⚠️ 极生数据库连接未能就绪 ({}:{})，启动软降级模式 (模拟推流仍将保活，不阻塞服务启动): {}",
                    properties.getHost(), properties.getPort(), t.getMessage());
            this.connected = false;
        }
    }

    /**
     * 周期性安全提交与合并底层时序文件句柄
     */
    public synchronized void commitAndMerge() {
        if (!connected || dbms == null) return;
        try {
            TDBAPI.commit(dbms, properties.getDatabase(), properties.getTableName());
            TDBAPI.merge(dbms, properties.getDatabase(), properties.getTableName());
            log.debug("💾 已执行 TDBAPI.commit() 与 TDBAPI.merge() 释放底层文件句柄");
        } catch (Throwable t) {
            log.error("❌ 极生 commit/merge 异常: {}", t.getMessage());
        }
    }

    @PreDestroy
    public synchronized void close() {
        if (dbms != null) {
            try {
                commitAndMerge();
                dbms.close();
                connected = false;
                log.info("🛑 极生 DBMS 连接已安全释放。");
            } catch (Throwable t) {
                log.warn("释放极生连接发生异常: {}", t.getMessage());
            }
        }
    }
}
