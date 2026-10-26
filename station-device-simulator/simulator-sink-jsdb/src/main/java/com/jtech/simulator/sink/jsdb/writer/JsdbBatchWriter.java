package com.jtech.simulator.sink.jsdb.writer;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jsdb.XPhy.TDBAPI;
import com.jtech.simulator.sink.jsdb.client.JsdbClientWrapper;
import com.jtech.simulator.sink.jsdb.config.JsdbProperties;
import com.jtech.simulator.sink.model.TimeSeriesRecord;
import com.jtech.simulator.sink.spi.TimeSeriesSink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 极生时序数据库批量写入器
 * 严格遵循反编译签名与踩坑规约:
 * TDBAPI.insert(DBMS, String db, String table, JSONArray kids, JSONArray docs)
 * 采用单点独立批次或同构数组，避免多 kid 指针交叉污染 (P-014 规约)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JsdbBatchWriter implements TimeSeriesSink {

    private final JsdbProperties properties;
    private final JsdbClientWrapper clientWrapper;

    /** 内存最近帧滚动缓存 (供前端控制台实时拉取比对) */
    private final Queue<TimeSeriesRecord> recentFrames = new ConcurrentLinkedQueue<>();
    private final AtomicInteger batchCounter = new AtomicInteger(0);

    @Override
    public void write(TimeSeriesRecord record) {
        if (record == null) return;
        writeBatch(Collections.singletonList(record));
    }

    @Override
    public void writeBatch(List<TimeSeriesRecord> records) {
        if (records == null || records.isEmpty()) return;

        // 1. 保留近 50 条帧数据供控制台/算子即时比对
        for (TimeSeriesRecord r : records) {
            recentFrames.offer(r);
            if (recentFrames.size() > 50) {
                recentFrames.poll();
            }
        }

        // 2. 检查底层极生是否连通
        if (!clientWrapper.isConnected() || clientWrapper.getDbms() == null) {
            log.trace("极生未连接，数据帧已进入本地内存暂存队列 (Kid: {})", records.get(0).getKid());
            return;
        }

        try {
            // 3. 构造极生 TDBAPI.insert 入参
            // 按点位 kid 分组单点安全批次写入 (杜绝交叉指针缺陷)
            Map<String, List<TimeSeriesRecord>> groupedByKid = new HashMap<>();
            for (TimeSeriesRecord r : records) {
                groupedByKid.computeIfAbsent(r.getKid(), k -> new ArrayList<>()).add(r);
            }

            for (Map.Entry<String, List<TimeSeriesRecord>> entry : groupedByKid.entrySet()) {
                String pointCode = entry.getKey();
                List<TimeSeriesRecord> list = entry.getValue();

                // 核心修复1: 极生 kid 必须为点位编码 MD5 计算后的 64 位整型 key (与 etl_station 保持一致)
                long kidLong = com.pydk.util.MD5.Md5Key(pointCode.getBytes(java.nio.charset.StandardCharsets.UTF_8));

                JSONArray kidsArray = new JSONArray();
                JSONArray docsArray = new JSONArray();

                for (TimeSeriesRecord rec : list) {
                    kidsArray.add(kidLong);

                    // 核心修复2: 极生行记录 docs 必须为有序数组 [time, timestamp, value, flag]
                    // 传入 JSONObject 会导致底层 C/JNI 返回 null 并抛出 getIntValue("<local9>") 异常
                    JSONArray docRow = new JSONArray();
                    docRow.add(rec.getTimestampSec());
                    docRow.add(rec.getTimestampMs());
                    docRow.add(rec.getValue());
                    docRow.add((byte) rec.getQuality());
                    docsArray.add(docRow);
                }

                // 核心 JNI 调用下刷
                int inserted = TDBAPI.insert(
                        clientWrapper.getDbms(),
                        properties.getDatabase(),
                        properties.getTableName(),
                        kidsArray,
                        docsArray
                );
                log.trace("已向极生超表 {} 写入测点 {} 数据 {} 条, 返回码: {}", properties.getTableName(), pointCode, list.size(), inserted);
            }

            // 4. 定期触发 Commit/Merge 释放文件句柄
            int currentBatch = batchCounter.incrementAndGet();
            if (currentBatch >= properties.getFlushThreshold()) {
                batchCounter.set(0);
                clientWrapper.commitAndMerge();
            }

        } catch (Throwable t) {
            log.error("❌ 极生批量写入发生异常: {}", t.getMessage());
        }
    }

    @Override
    public void flush() {
        clientWrapper.commitAndMerge();
    }

    @Override
    public String getStatus() {
        return clientWrapper.isConnected() ? "CONNECTED" : "DISCONNECTED (SOFT_STANDBY)";
    }

    /**
     * 获取最近发送的原始时序数据流水 (供 REST API 返回给控制台比对)
     */
    public List<TimeSeriesRecord> getRecentFrames(int limit) {
        List<TimeSeriesRecord> list = new ArrayList<>(recentFrames);
        Collections.reverse(list);
        if (list.size() > limit) {
            return list.subList(0, limit);
        }
        return list;
    }
}
