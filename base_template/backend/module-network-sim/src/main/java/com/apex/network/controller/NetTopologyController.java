package com.apex.network.controller;

import com.apex.common.result.Result;
import com.apex.network.dto.SimRequestDTO;
import com.apex.network.dto.TopologySaveDTO;
import com.apex.network.entity.NetTopology;
import com.apex.network.model.SimResultVO;
import com.apex.network.service.NetTopologyService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 网络拓扑方案与仿真测试 API 控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/network-sim")
@RequiredArgsConstructor
public class NetTopologyController {

    private final NetTopologyService topologyService;

    /**
     * 分页查询拓扑方案列表
     */
    @GetMapping("/topology/page")
    public Result<Page<NetTopology>> getTopologyPage(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        Page<NetTopology> page = topologyService.getTopologyPage(pageNo, pageSize, keyword);
        return Result.success(page);
    }

    /**
     * 根据主键查询拓扑方案详情 (读取画布 JSON)
     */
    @GetMapping("/topology/{id}")
    public Result<NetTopology> getTopologyById(@PathVariable Long id) {
        NetTopology topology = topologyService.getTopologyById(id);
        return Result.success(topology);
    }

    /**
     * 新建或更新拓扑方案 (支持保存画布 JSON)
     */
    @PostMapping("/topology")
    public Result<NetTopology> saveTopology(@Valid @RequestBody TopologySaveDTO dto) {
        NetTopology entity = topologyService.saveTopology(dto);
        return Result.success(entity, "拓扑方案保存成功");
    }

    /**
     * 删除拓扑方案
     */
    @DeleteMapping("/topology/{id}")
    public Result<Void> deleteTopology(@PathVariable Long id) {
        topologyService.deleteTopology(id);
        return Result.success(null, "拓扑方案删除成功");
    }

    /**
     * 触发端到端连通性仿真测试 (基于 BFS 寻路与 ACL 规则拦截)
     */
    @PostMapping("/simulate")
    public Result<SimResultVO> runSimulation(@Valid @RequestBody SimRequestDTO req) {
        log.info("收到拓扑连通性测试请求: 拓扑ID={}, 源={}, 目标={}, 端口={}, 协议={}",
                req.getTopologyId(), req.getSourceNodeId(), req.getTargetNodeId(), req.getDestPort(), req.getProtocol());
        SimResultVO simResult = topologyService.runSimulation(req);
        return Result.success(simResult, "仿真测试计算完毕");
    }
}
