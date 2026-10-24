package com.apex.network.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.apex.common.exception.BusinessException;
import com.apex.network.dto.SimRequestDTO;
import com.apex.network.dto.TopologySaveDTO;
import com.apex.network.engine.BfsRoutingEngine;
import com.apex.network.entity.NetTopology;
import com.apex.network.mapper.NetTopologyMapper;
import com.apex.network.model.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 网络拓扑方案领域服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NetTopologyService {

    private final NetTopologyMapper topologyMapper;
    private final BfsRoutingEngine bfsRoutingEngine;

    /**
     * 分页查询拓扑方案列表
     */
    public Page<NetTopology> getTopologyPage(int pageNo, int pageSize, String keyword) {
        Page<NetTopology> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<NetTopology> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(NetTopology::getName, keyword)
                   .or()
                   .like(NetTopology::getDescription, keyword);
        }
        wrapper.orderByDesc(NetTopology::getUpdatedAt);
        return topologyMapper.selectPage(page, wrapper);
    }

    /**
     * 根据主键查询拓扑方案详情 (包含完整画布 JSON)
     */
    public NetTopology getTopologyById(Long id) {
        NetTopology topology = topologyMapper.selectById(id);
        if (topology == null) {
            throw new BusinessException("拓扑方案不存在 (ID: " + id + ")");
        }
        return topology;
    }

    /**
     * 保存或更新拓扑方案
     */
    @Transactional(rollbackFor = Exception.class)
    public NetTopology saveTopology(TopologySaveDTO dto) {
        NetTopology entity;
        LocalDateTime now = LocalDateTime.now();

        if (dto.getId() != null) {
            entity = topologyMapper.selectById(dto.getId());
            if (entity == null) {
                throw new BusinessException("待更新的拓扑方案不存在");
            }
            entity.setName(dto.getName());
            entity.setDescription(dto.getDescription());
            if (dto.getCanvasData() != null) {
                entity.setCanvasData(dto.getCanvasData());
            }
            if (dto.getNodeCount() != null) {
                entity.setNodeCount(dto.getNodeCount());
            }
            if (dto.getEdgeCount() != null) {
                entity.setEdgeCount(dto.getEdgeCount());
            }
            entity.setUpdatedAt(now);
            topologyMapper.updateById(entity);
            log.info("成功更新拓扑方案 [ID: {}, Name: {}]", entity.getId(), entity.getName());
        } else {
            entity = NetTopology.builder()
                    .name(dto.getName())
                    .description(dto.getDescription())
                    .canvasData(StringUtils.hasText(dto.getCanvasData()) ? dto.getCanvasData() : "{\"cells\":[]}")
                    .nodeCount(dto.getNodeCount() != null ? dto.getNodeCount() : 0)
                    .edgeCount(dto.getEdgeCount() != null ? dto.getEdgeCount() : 0)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            topologyMapper.insert(entity);
            log.info("成功新建拓扑方案 [ID: {}, Name: {}]", entity.getId(), entity.getName());
        }

        return entity;
    }

    /**
     * 删除拓扑方案
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTopology(Long id) {
        NetTopology topology = topologyMapper.selectById(id);
        if (topology == null) {
            throw new BusinessException("拓扑方案不存在");
        }
        topologyMapper.deleteById(id);
        log.info("成功删除拓扑方案 [ID: {}]", id);
    }

    /**
     * 执行真实端到端连通性仿真测试 (调用 BFS + ACL 算法引擎)
     */
    public SimResultVO runSimulation(SimRequestDTO req) {
        NetTopology topology = getTopologyById(req.getTopologyId());
        String canvasData = topology.getCanvasData();
        if (!StringUtils.hasText(canvasData)) {
            throw new BusinessException("该拓扑方案画布数据为空，无法执行仿真测试");
        }

        // 解析 X6 画布 JSON 为 TopoGraph 内存图模型
        TopoGraph graph = parseCanvasToGraph(topology.getId(), topology.getName(), canvasData);

        // 调用 BFS 寻路与安全过滤引擎
        return bfsRoutingEngine.simulate(graph, req.getSourceNodeId(), req.getTargetNodeId(), req.getDestPort(), req.getProtocol());
    }

    /**
     * 将 AntV X6 导出的 cells JSON 转换为纯 Java 算法图模型
     */
    private TopoGraph parseCanvasToGraph(Long topoId, String name, String canvasData) {
        JSONObject root = JSON.parseObject(canvasData);
        JSONArray cells = root.getJSONArray("cells");
        if (cells == null) {
            cells = new JSONArray();
        }

        List<TopoNode> nodes = new ArrayList<>();
        List<TopoEdge> edges = new ArrayList<>();

        for (int i = 0; i < cells.size(); i++) {
            JSONObject cell = cells.getJSONObject(i);
            String shape = cell.getString("shape");
            String id = cell.getString("id");

            // 过滤大区底板，只提取设备节点与连线
            if (id != null && id.startsWith("zone_")) {
                continue;
            }

            if ("edge".equalsIgnoreCase(shape) || cell.containsKey("source")) {
                // 链路 Edge
                JSONObject srcObj = cell.getJSONObject("source");
                JSONObject tgtObj = cell.getJSONObject("target");
                JSONObject data = cell.getJSONObject("data");
                boolean edgeDisabled = data != null && Boolean.TRUE.equals(data.getBoolean("disabled"));

                if (srcObj != null && tgtObj != null) {
                    String srcId = srcObj.getString("cell");
                    String tgtId = tgtObj.getString("cell");
                    if (srcId != null && tgtId != null) {
                        edges.add(TopoEdge.builder()
                                .id(id)
                                .sourceNodeId(srcId)
                                .targetNodeId(tgtId)
                                .bandwidth("1000 Mbps")
                                .latencyMs(2)
                                .disabled(edgeDisabled)
                                .build());
                    }
                }
            } else {
                // 设备节点 Node
                JSONObject data = cell.getJSONObject("data");
                JSONObject attrs = cell.getJSONObject("attrs");

                String nodeName = "未命名设备";
                String nodeType = "HOST";
                String ip = "192.168.1.1";
                String zone = "IT_ZONE";
                boolean nodeDisabled = false;
                List<AclRule> acls = new ArrayList<>();

                if (data != null) {
                    nodeName = data.getString("name");
                    nodeType = data.getString("type");
                    ip = data.getString("ip");
                    zone = data.getString("zone");
                    nodeDisabled = Boolean.TRUE.equals(data.getBoolean("disabled"));
                    JSONArray aclArr = data.getJSONArray("acls");
                    if (aclArr != null) {
                        acls = aclArr.toJavaList(AclRule.class);
                    }
                } else if (attrs != null && attrs.getJSONObject("title") != null) {
                    nodeName = attrs.getJSONObject("title").getString("text");
                }

                nodes.add(TopoNode.builder()
                        .id(id)
                        .name(nodeName)
                        .type(nodeType != null ? nodeType : "HOST")
                        .zone(zone)
                        .ip(ip != null ? ip : "192.168.1.1")
                        .acls(acls)
                        .disabled(nodeDisabled)
                        .build());
            }
        }

        return TopoGraph.builder()
                .id(topoId)
                .name(name)
                .nodes(nodes)
                .edges(edges)
                .build();
    }
}
