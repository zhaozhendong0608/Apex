package com.jtech.simulator.controller;

import com.jtech.simulator.core.engine.SimulationEngine;
import com.jtech.simulator.sink.jsdb.writer.JsdbBatchWriter;
import com.jtech.simulator.sink.model.TimeSeriesRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟器状态监控与 REST 控制端点 (映射 DLD 契约)
 */
@RestController
@RequestMapping("/api/simulator")
@RequiredArgsConstructor
public class SimulatorApiController {

    private final SimulationEngine simulationEngine;
    private final JsdbBatchWriter jsdbBatchWriter;

    /**
     * 查询当前模拟服务运行大盘状态
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> res = new HashMap<>();
        res.put("code", 200);
        res.put("msg", "success");

        Map<String, Object> data = new HashMap<>();
        data.put("running", simulationEngine.isRunning());
        data.put("currentRegime", simulationEngine.getCurrentRegime());
        data.put("totalPoints", simulationEngine.getRegisteredPoints().size());
        data.put("jsdbStatus", jsdbBatchWriter.getStatus());

        res.put("data", data);
        return res;
    }

    /**
     * 切换机组宏观工况 (SHUTDOWN, STARTUP, STEADY_300, FULL_LOAD, TRIP_REJECTION)
     */
    @PostMapping("/regime")
    public Map<String, Object> setRegime(@RequestBody Map<String, String> body) {
        String regime = body.getOrDefault("regime", "STEADY_300");
        simulationEngine.setCurrentRegime(regime);

        Map<String, Object> res = new HashMap<>();
        res.put("code", 200);
        res.put("msg", "工况已切换为: " + regime);
        return res;
    }

    /**
     * 动态故障注入 (超温/越限突变)
     */
    @PostMapping("/fault/inject")
    public Map<String, Object> injectFault(@RequestBody Map<String, Object> body) {
        String pointCode = (String) body.get("pointCode");
        double targetVal = Double.parseDouble(body.get("targetValue").toString());

        simulationEngine.injectFault(pointCode, targetVal);

        Map<String, Object> res = new HashMap<>();
        res.put("code", 200);
        res.put("msg", "故障已注入，已锁定点位 " + pointCode + " 目标值 " + targetVal);
        return res;
    }

    /**
     * 恢复正常物理演变
     */
    @PostMapping("/fault/reset")
    public Map<String, Object> resetFault(@RequestBody Map<String, String> body) {
        String pointCode = body.get("pointCode");
        simulationEngine.clearFault(pointCode);

        Map<String, Object> res = new HashMap<>();
        res.put("code", 200);
        res.put("msg", "点位 " + pointCode + " 已恢复正常工况");
        return res;
    }

    private final com.jtech.simulator.metadata.spi.PointMetadataProvider metadataProvider;

    /**
     * 获取从 MySQL 加载的测点列表或按需层级树 (对齐 etl_station 树按需加载)
     * - 不带参数：返回当前模拟引擎已纳管的仿真测点摘要列表 (默认 limit 100，避免 14 万点位挤爆网络)
     * - 带 parentId：按需查询直接子节点 (station -> unit -> device -> component -> point)
     * - 带 keyword：按关键词精准过滤搜索
     */
    @GetMapping("/points")
    public Map<String, Object> getPoints(
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "100") int limit) {
        Map<String, Object> res = new HashMap<>();
        res.put("code", 200);

        if (keyword != null && !keyword.trim().isEmpty()) {
            // 搜索模式
            res.put("data", metadataProvider.searchTreeNodes(keyword.trim()));
            return res;
        }

        if (parentId != null) {
            // 树节点按需懒加载
            res.put("data", metadataProvider.loadTreeNodes(parentId));
            return res;
        }

        // 默认返回引擎运行中的仿真测点切片 (防巨量传输崩溃 502)
        List<com.jtech.simulator.metadata.model.PointMetadata> all = simulationEngine.getRegisteredPoints();
        if (all.size() > limit) {
            res.put("data", all.subList(0, limit));
        } else {
            res.put("data", all);
        }
        res.put("total", all.size());
        return res;
    }

    /**
     * 获取资产树节点 (专门树节点接口)
     */
    @GetMapping("/tree")
    public Map<String, Object> getTreeNodes(@RequestParam(defaultValue = "0") Long parentId) {
        Map<String, Object> res = new HashMap<>();
        res.put("code", 200);
        res.put("data", metadataProvider.loadTreeNodes(parentId));
        return res;
    }

    /**
     * 搜索资产树节点
     */
    @GetMapping("/tree/search")
    public Map<String, Object> searchTree(@RequestParam String keyword) {
        Map<String, Object> res = new HashMap<>();
        res.put("code", 200);
        res.put("data", metadataProvider.searchTreeNodes(keyword));
        return res;
    }

    /**
     * 获取最近底层真实时序帧流水 (供控制台同屏比对)
     */
    @GetMapping("/recent-frames")
    public Map<String, Object> getRecentFrames(@RequestParam(defaultValue = "15") int limit) {
        List<TimeSeriesRecord> records = jsdbBatchWriter.getRecentFrames(limit);

        Map<String, Object> res = new HashMap<>();
        res.put("code", 200);
        res.put("data", records);
        return res;
    }
}
