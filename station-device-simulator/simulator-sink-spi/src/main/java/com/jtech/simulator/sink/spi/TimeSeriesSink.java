package com.jtech.simulator.sink.spi;

import com.jtech.simulator.sink.model.TimeSeriesRecord;
import java.util.List;

/**
 * 时序数据接收下刷 SPI
 */
public interface TimeSeriesSink {

    /**
     * 单条记录写入 (内部缓冲聚合)
     *
     * @param record 单帧数据
     */
    void write(TimeSeriesRecord record);

    /**
     * 批量记录写入
     *
     * @param records 数据帧批次
     */
    void writeBatch(List<TimeSeriesRecord> records);

    /**
     * 手动触发缓冲区下刷 (释放底层驱动句柄 Commit/Merge)
     */
    void flush();

    /**
     * 获取当前 Sink 健康连接状态
     *
     * @return 状态描述 (如 CONNECTED, DISCONNECTED)
     */
    String getStatus();
}
