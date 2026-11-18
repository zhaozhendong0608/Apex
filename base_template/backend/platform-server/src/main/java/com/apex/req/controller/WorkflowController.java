package com.apex.req.controller;

import com.apex.common.result.Result;
import com.apex.req.domain.entity.ReqProject;
import com.apex.req.domain.entity.ReqRequirement;
import com.apex.req.domain.entity.ReqWorkflowRecord;
import com.apex.req.domain.entity.ReqWorkflowTemplate;
import com.apex.req.mapper.ReqProjectMapper;
import com.apex.req.mapper.ReqRequirementMapper;
import com.apex.req.mapper.ReqWorkflowRecordMapper;
import com.apex.req.mapper.ReqWorkflowTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 🚀 DAG 工作流拓扑与多需求独立状态机 Controller
 * 双驱映射模型：MySQL 数据库作为单一事实源 (SSOT)，.ai/ 文件作为轻量级实时镜像
 */
@RestController
@RequestMapping("/api/v1/req/workflow")
public class WorkflowController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private ReqWorkflowRecordMapper workflowRecordMapper;

    @Autowired(required = false)
    private ReqRequirementMapper requirementMapper;

    @Autowired(required = false)
    private ReqProjectMapper projectMapper;

    @Autowired(required = false)
    private ReqWorkflowTemplateMapper workflowTemplateMapper;

    @Autowired(required = false)
    private com.apex.req.mapper.ReqWorkflowNodeStateMapper workflowNodeStateMapper;

    /**
     * 定位 .ai 存储目录
     */
    private File getAiDirectory() {
        String userDir = System.getProperty("user.dir");
        File aiDir = new File(userDir, ".ai");
        if (!aiDir.exists()) {
            File parentAi = new File(userDir, "../.ai");
            if (parentAi.exists()) {
                aiDir = parentAi;
            }
        }
        return aiDir;
    }

    /**
     * 获取指定需求的独立 DAG 状态文件
     */
    private File getRequirementDagFile(String reqCode) {
        File aiDir = getAiDirectory();
        File dagsDir = new File(aiDir, "dags");
        if (!dagsDir.exists()) {
            dagsDir.mkdirs();
        }
        return new File(dagsDir, "dag_" + reqCode + ".json");
    }

    /**
     * 反向投影更新本地 .ai/tier1_snapshot.md 名片卡 (<30行，支持AI在IDE中0.5秒极速断点续传)
     */
    private void syncTier1SnapshotProjection(String reqCode, String targetNode, String trackMode, String operator) {
        try {
            File aiDir = getAiDirectory();
            File snapshotFile = new File(aiDir, "tier1_snapshot.md");
            
            // 保留原有的 TIER1-ACTIVE 锚点与当前焦点（避免覆盖丢失）
            String activeSection = "<!-- TIER1-ACTIVE:START -->\n" +
                    "- **当前焦点**：无（没有 ACTIVE 任务）\n" +
                    "- **下一待办**：暂无 —— 需回复 `[1]` 规划新需求\n" +
                    "<!-- TIER1-ACTIVE:END -->\n";
            if (snapshotFile.exists()) {
                try {
                    String existing = new String(java.nio.file.Files.readAllBytes(snapshotFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                    int sIdx = existing.indexOf("<!-- TIER1-ACTIVE:START -->");
                    int eIdx = existing.indexOf("<!-- TIER1-ACTIVE:END -->");
                    if (sIdx != -1 && eIdx != -1) {
                        activeSection = existing.substring(sIdx, eIdx + "<!-- TIER1-ACTIVE:END -->".length()) + "\n";
                    }
                } catch (Exception ignored) {}
            }

            String content = "<!-- 【三层记忆金字塔 - 第一层：极简快照名片 (<30行)】 -->\n" +
                    "# ⚡ tier1_snapshot.md · 极简工作区名片\n\n" +
                    "- **项目空间**: NET-SIM (全景工业网络仿真与拓扑排版系统)\n" +
                    "- **当前聚焦需求**: " + reqCode + "\n" +
                    activeSection +
                    "- **当前工位**: [" + targetNode + "] (模式: " + trackMode + ")\n" +
                    "- **责任操作人**: " + operator + "\n" +
                    "- **单一事实源 (SSOT)**: MySQL `t_req_requirement` / `t_req_workflow_record`\n" +
                    "- **最后同步时间**: " + LocalDateTime.now() + "\n\n" +
                    "## 🎯 核心质量与安全护栏\n" +
                    "1. 单次改动 ≤ 3 文件 & 零野公网依赖\n" +
                    "2. 保持真实数据源，必须通过 gate.py 机器门禁\n" +
                    "3. 唯一归档出口: board.py verify ➔ complete --accept\n";
            try (FileWriter writer = new FileWriter(snapshotFile)) {
                writer.write(content);
            }
        } catch (Exception ex) {
            System.err.println("刷新本地 tier1_snapshot.md 镜像失败(非阻塞): " + ex.getMessage());
        }
    }

    /**
     * 获取指定需求在指定轨道下的各工位执行状态 (直接从 MySQL t_req_workflow_node_state 读取)
     */
    @GetMapping("/state")
    public Result<Map<String, Object>> getWorkflowState(
            @RequestParam(value = "reqCode", defaultValue = "REQ-001") String reqCode,
            @RequestParam(value = "trackMode", defaultValue = "STANDARD") String trackMode) {
        Map<String, Object> result = new HashMap<>();
        String currentNode = "SOP-01";

        if (requirementMapper != null) {
            try {
                LambdaQueryWrapper<ReqRequirement> q = new LambdaQueryWrapper<>();
                q.eq(ReqRequirement::getReqCode, reqCode);
                ReqRequirement r = requirementMapper.selectOne(q);
                if (r != null && r.getCurrentNodeId() != null) {
                    currentNode = r.getCurrentNodeId();
                }
            } catch (Exception ignored) {}
        }

        List<com.apex.req.domain.entity.ReqWorkflowNodeState> list = new ArrayList<>();
        if (workflowNodeStateMapper != null) {
            try {
                LambdaQueryWrapper<com.apex.req.domain.entity.ReqWorkflowNodeState> qw = new LambdaQueryWrapper<>();
                qw.eq(com.apex.req.domain.entity.ReqWorkflowNodeState::getReqCode, reqCode)
                  .eq(com.apex.req.domain.entity.ReqWorkflowNodeState::getTrackMode, trackMode);
                list = workflowNodeStateMapper.selectList(qw);

                // 如果未找到记录，按当前系统默认模板 (t_req_workflow_template) 进行初次初始化
                if (list.isEmpty()) {
                    List<Map<String, String>> initNodes = new ArrayList<>();
                    if (workflowTemplateMapper != null) {
                        try {
                            LambdaQueryWrapper<ReqWorkflowTemplate> tplQw = new LambdaQueryWrapper<>();
                            tplQw.eq(ReqWorkflowTemplate::getIsSystemDefault, 1);
                            ReqWorkflowTemplate defaultTpl = workflowTemplateMapper.selectOne(tplQw);
                            if (defaultTpl != null && defaultTpl.getNodesConfig() != null) {
                                List<Map<String, Object>> parsed = objectMapper.readValue(defaultTpl.getNodesConfig(), List.class);
                                for (Map<String, Object> item : parsed) {
                                    String nid = String.valueOf(item.getOrDefault("id", ""));
                                    String nname = String.valueOf(item.getOrDefault("name", nid));
                                    String ntrack = String.valueOf(item.getOrDefault("track", nid.startsWith("SOP-B") ? "FASTTRACK" : "STANDARD"));
                                    if (trackMode.equalsIgnoreCase(ntrack)) {
                                        Map<String, String> pair = new HashMap<>();
                                        pair.put("id", nid);
                                        pair.put("name", nname);
                                        initNodes.add(pair);
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                    if (initNodes.isEmpty()) {
                        List<String> fallbackIds = "STANDARD".equalsIgnoreCase(trackMode)
                                ? Arrays.asList("SOP-00", "SOP-01", "SOP-02", "TC-GEN", "SOP-03", "SOP-04", "SOP-05", "SOP-06", "SOP-07", "SOP-08", "SOP-09")
                                : Arrays.asList("SOP-B1", "SOP-B2", "SOP-B3", "SOP-B4", "SOP-B5", "SOP-B6");
                        for (String nid : fallbackIds) {
                            Map<String, String> pair = new HashMap<>();
                            pair.put("id", nid);
                            pair.put("name", nid);
                            initNodes.add(pair);
                        }
                    }
                    for (int i = 0; i < initNodes.size(); i++) {
                        String nid = initNodes.get(i).get("id");
                        String nname = initNodes.get(i).get("name");
                        String st = "STANDARD".equalsIgnoreCase(trackMode)
                                ? ((i == 0) ? "COMPLETED" : ((i == 1) ? "IN_PROGRESS" : "PENDING"))
                                : ((i == 0) ? "IN_PROGRESS" : "PENDING");
                        com.apex.req.domain.entity.ReqWorkflowNodeState ns = com.apex.req.domain.entity.ReqWorkflowNodeState.builder()
                                .reqCode(reqCode)
                                .trackMode(trackMode)
                                .nodeId(nid)
                                .nodeName(nname)
                                .status(st)
                                .operator(("COMPLETED".equals(st) || "IN_PROGRESS".equals(st)) ? "admin" : null)
                                .completedAt("COMPLETED".equals(st) ? LocalDateTime.now() : null)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                        workflowNodeStateMapper.insert(ns);
                        list.add(ns);
                    }
                }
            } catch (Exception ex) {
                System.err.println("读取工位状态库失败: " + ex.getMessage());
            }
        }

        result.put("reqCode", reqCode);
        result.put("trackMode", trackMode);
        result.put("current_node", currentNode);
        result.put("nodes", list);
        return Result.success(result);
    }

    /**
     * 获取指定轨道的拓扑定义与元数据
     */
    @GetMapping("/topology")
    public Result<Map<String, Object>> getWorkflowTopology(
            @RequestParam(value = "trackMode", defaultValue = "STANDARD") String trackMode,
            @RequestParam(value = "projectId", required = false) Long projectId) {
        Map<String, Object> resp = new HashMap<>();
        String nodesJson = "[]";
        String edgesJson = "[]";
        if (workflowTemplateMapper != null) {
            try {
                LambdaQueryWrapper<ReqWorkflowTemplate> qw = new LambdaQueryWrapper<>();
                if (projectId != null && projectMapper != null) {
                    ReqProject p = projectMapper.selectById(projectId);
                    if (p != null && p.getWorkflowTemplateId() != null) {
                        qw.eq(ReqWorkflowTemplate::getId, p.getWorkflowTemplateId());
                    } else {
                        qw.eq(ReqWorkflowTemplate::getIsSystemDefault, 1);
                    }
                } else {
                    qw.eq(ReqWorkflowTemplate::getIsSystemDefault, 1);
                }
                ReqWorkflowTemplate tpl = workflowTemplateMapper.selectOne(qw);
                if (tpl != null) {
                    if (tpl.getNodesConfig() != null) nodesJson = tpl.getNodesConfig();
                    if (tpl.getEdgesConfig() != null) edgesJson = tpl.getEdgesConfig();
                }
            } catch (Exception ignored) {}
        }
        resp.put("trackMode", trackMode);
        resp.put("nodesConfig", nodesJson);
        resp.put("edgesConfig", edgesJson);
        return Result.success(resp);
    }

    /**
     * 获取指定需求的 DAG 真实工位状态 (优先从 MySQL 数据库 SSOT 读取)
     */
    @GetMapping("/dag-state")
    public Result<Map<String, Object>> getDagState(
            @RequestParam(value = "reqCode", required = false, defaultValue = "REQ-001") String reqCode,
            @RequestParam(value = "trackMode", required = false, defaultValue = "STANDARD") String trackMode) {

        Map<String, Object> response = new HashMap<>();
        String currentNode = "SOP-01";
        String resolvedTrackMode = trackMode;

        // 1. 优先从 MySQL 需求主表获取当前绑定工位
        if (requirementMapper != null) {
            try {
                LambdaQueryWrapper<ReqRequirement> query = new LambdaQueryWrapper<>();
                query.eq(ReqRequirement::getReqCode, reqCode);
                ReqRequirement req = requirementMapper.selectOne(query);
                if (req != null && req.getCurrentNodeId() != null) {
                    currentNode = req.getCurrentNodeId();
                    if (req.getCurrentTrackMode() != null) {
                        resolvedTrackMode = req.getCurrentTrackMode();
                    }
                }
            } catch (Exception e) {
                // 降级使用本地文件
            }
        }

        // 2. 从本地缓存或默认生成节点明细
        File reqDagFile = getRequirementDagFile(reqCode);
        try {
            if (reqDagFile.exists()) {
                response = objectMapper.readValue(reqDagFile, Map.class);
            }
        } catch (Exception ignored) {}

        response.put("reqCode", reqCode);
        response.put("current_node", currentNode);
        response.put("trackMode", resolvedTrackMode);
        return Result.success(response);
    }

    /**
     * 推进/更新指定需求的工位状态 (ADVANCE / SET / OVERRIDE)
     * 核心双驱：写入 MySQL (SSOT) ➔ 自动点亮后继工位(支持并行多分支/指定单分支) ➔ 实时更新 .ai/ 本地轻量镜像
     */
    @PostMapping("/advance")
    public Result<Map<String, Object>> advanceRequirementDag(
            @RequestParam("reqCode") String reqCode,
            @RequestParam("targetNode") String targetNode,
            @RequestParam(value = "nodeName", required = false, defaultValue = "") String nodeName,
            @RequestParam(value = "trackMode", required = false, defaultValue = "STANDARD") String trackMode,
            @RequestParam(value = "skillName", required = false, defaultValue = "") String skillName,
            @RequestParam(value = "actionType", required = false, defaultValue = "ADVANCE") String actionType,
            @RequestParam(value = "overrideReason", required = false) String overrideReason,
            @RequestParam(value = "artifactsJson", required = false) String artifactsJson,
            @RequestParam(value = "status", defaultValue = "COMPLETED") String status,
            @RequestParam(value = "nextNodes", required = false) String nextNodes,
            @RequestParam(value = "operator", defaultValue = "admin") String operator) {

        // 0. 解析需要顺推点亮的后继节点列表 (支持显式指定逗号分隔 nextNodes，或自动从模板 edges_config 推导)
        List<String> activatedNextList = new ArrayList<>();
        Map<String, String> tplNodeNameMap = new HashMap<>();
        if (workflowTemplateMapper != null) {
            try {
                LambdaQueryWrapper<ReqWorkflowTemplate> tplQw = new LambdaQueryWrapper<>();
                tplQw.eq(ReqWorkflowTemplate::getIsSystemDefault, 1);
                ReqWorkflowTemplate defaultTpl = workflowTemplateMapper.selectOne(tplQw);
                if (defaultTpl != null) {
                    if (defaultTpl.getNodesConfig() != null) {
                        List<Map<String, Object>> parsedNodes = objectMapper.readValue(defaultTpl.getNodesConfig(), List.class);
                        for (Map<String, Object> n : parsedNodes) {
                            String nid = String.valueOf(n.getOrDefault("id", ""));
                            String nname = String.valueOf(n.getOrDefault("name", nid));
                            tplNodeNameMap.put(nid, nname);
                        }
                    }
                    if ("COMPLETED".equalsIgnoreCase(status) && "ADVANCE".equalsIgnoreCase(actionType)) {
                        if (nextNodes != null && !nextNodes.isBlank()) {
                            for (String part : nextNodes.split(",")) {
                                String trimmed = part.trim();
                                if (!trimmed.isEmpty() && !activatedNextList.contains(trimmed)) {
                                    activatedNextList.add(trimmed);
                                }
                            }
                        } else if (defaultTpl.getEdgesConfig() != null) {
                            List<Map<String, Object>> parsedEdges = objectMapper.readValue(defaultTpl.getEdgesConfig(), List.class);
                            for (Map<String, Object> edge : parsedEdges) {
                                String src = String.valueOf(edge.getOrDefault("source", ""));
                                String tgt = String.valueOf(edge.getOrDefault("target", ""));
                                if (targetNode.equals(src) && !tgt.isEmpty() && !activatedNextList.contains(tgt)) {
                                    boolean tgtIsFast = tgt.startsWith("SOP-B");
                                    boolean reqIsFast = "FASTTRACK".equalsIgnoreCase(trackMode);
                                    if (tgtIsFast == reqIsFast) {
                                        activatedNextList.add(tgt);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        // 顺推后的新主焦点节点 (有后继则指向首个后继，无后继则停留在当前终点节点)
        String nextFocusNode = activatedNextList.isEmpty() ? targetNode : activatedNextList.get(0);

        // 1. 核心持久化：更新 MySQL 需求主表 t_req_requirement (焦点推进至 nextFocusNode)
        if (requirementMapper != null) {
            try {
                LambdaUpdateWrapper<ReqRequirement> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.eq(ReqRequirement::getReqCode, reqCode)
                        .set(ReqRequirement::getCurrentNodeId, nextFocusNode)
                        .set(ReqRequirement::getCurrentTrackMode, trackMode)
                        .set(ReqRequirement::getUpdatedAt, LocalDateTime.now());
                requirementMapper.update(null, updateWrapper);
            } catch (Exception ex) {
                System.err.println("更新 MySQL 需求主表工位异常: " + ex.getMessage());
            }
        }

        // 2. 插入 MySQL 全生命周期审计子表 t_req_workflow_record
        if (workflowRecordMapper != null) {
            try {
                String resolvedNodeName = !nodeName.isEmpty() ? nodeName : tplNodeNameMap.getOrDefault(targetNode, targetNode);
                ReqWorkflowRecord record = ReqWorkflowRecord.builder()
                        .reqCode(reqCode)
                        .nodeId(targetNode)
                        .nodeName(resolvedNodeName)
                        .trackMode(trackMode)
                        .skillName(skillName)
                        .operator(operator)
                        .actionType(actionType)
                        .overrideReason(overrideReason)
                        .artifactsJson(artifactsJson)
                        .createdAt(LocalDateTime.now())
                        .build();
                workflowRecordMapper.insert(record);
            } catch (Exception ex) {
                System.err.println("持久化流水线履历到MySQL异常: " + ex.getMessage());
            }
        }

        // 2.1 核心持久化：更新/插入 MySQL 独立工位状态表 t_req_workflow_node_state
        if (workflowNodeStateMapper != null) {
            try {
                LambdaQueryWrapper<com.apex.req.domain.entity.ReqWorkflowNodeState> qw = new LambdaQueryWrapper<>();
                qw.eq(com.apex.req.domain.entity.ReqWorkflowNodeState::getReqCode, reqCode)
                  .eq(com.apex.req.domain.entity.ReqWorkflowNodeState::getTrackMode, trackMode)
                  .eq(com.apex.req.domain.entity.ReqWorkflowNodeState::getNodeId, targetNode);
                com.apex.req.domain.entity.ReqWorkflowNodeState exist = workflowNodeStateMapper.selectOne(qw);
                if (exist != null) {
                    exist.setStatus(status);
                    exist.setOperator(operator);
                    if ("COMPLETED".equalsIgnoreCase(status)) {
                        exist.setCompletedAt(LocalDateTime.now());
                    }
                    exist.setUpdatedAt(LocalDateTime.now());
                    workflowNodeStateMapper.updateById(exist);
                } else {
                    com.apex.req.domain.entity.ReqWorkflowNodeState newState = com.apex.req.domain.entity.ReqWorkflowNodeState.builder()
                            .reqCode(reqCode)
                            .trackMode(trackMode)
                            .nodeId(targetNode)
                            .nodeName(!nodeName.isEmpty() ? nodeName : tplNodeNameMap.getOrDefault(targetNode, targetNode))
                            .status(status)
                            .operator(operator)
                            .completedAt("COMPLETED".equalsIgnoreCase(status) ? LocalDateTime.now() : null)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    workflowNodeStateMapper.insert(newState);
                }

                // 2.2 自动顺推：将选中的后继工位从 PENDING 点亮为 IN_PROGRESS (不覆盖已 COMPLETED 的节点)
                for (String nxtId : activatedNextList) {
                    LambdaQueryWrapper<com.apex.req.domain.entity.ReqWorkflowNodeState> nxtQw = new LambdaQueryWrapper<>();
                    nxtQw.eq(com.apex.req.domain.entity.ReqWorkflowNodeState::getReqCode, reqCode)
                         .eq(com.apex.req.domain.entity.ReqWorkflowNodeState::getTrackMode, trackMode)
                         .eq(com.apex.req.domain.entity.ReqWorkflowNodeState::getNodeId, nxtId);
                    com.apex.req.domain.entity.ReqWorkflowNodeState nxtExist = workflowNodeStateMapper.selectOne(nxtQw);
                    if (nxtExist != null) {
                        if (!"COMPLETED".equalsIgnoreCase(nxtExist.getStatus())) {
                            nxtExist.setStatus("IN_PROGRESS");
                            nxtExist.setOperator(operator);
                            nxtExist.setUpdatedAt(LocalDateTime.now());
                            workflowNodeStateMapper.updateById(nxtExist);
                        }
                    } else {
                        com.apex.req.domain.entity.ReqWorkflowNodeState nxtNew = com.apex.req.domain.entity.ReqWorkflowNodeState.builder()
                                .reqCode(reqCode)
                                .trackMode(trackMode)
                                .nodeId(nxtId)
                                .nodeName(tplNodeNameMap.getOrDefault(nxtId, nxtId))
                                .status("IN_PROGRESS")
                                .operator(operator)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                        workflowNodeStateMapper.insert(nxtNew);
                    }
                }
            } catch (Exception ex) {
                System.err.println("更新 MySQL 工位状态表异常: " + ex.getMessage());
            }
        }

        // 3. 反向投影：刷新本地 .ai/ 运行时文件与 tier1_snapshot.md 名片
        File reqDagFile = getRequirementDagFile(reqCode);
        Map<String, Object> state = new HashMap<>();
        try {
            if (reqDagFile.exists()) {
                state = objectMapper.readValue(reqDagFile, Map.class);
            }
            state.put("reqCode", reqCode);
            state.put("current_node", nextFocusNode);
            state.put("completed_node", targetNode);
            state.put("activated_next_nodes", activatedNextList);
            state.put("last_operator", operator);
            state.put("track_mode", trackMode);
            state.put("updated_at", new Date().toString());

            Map<String, Object> nodes = (Map<String, Object>) state.getOrDefault("nodes", new HashMap<String, Object>());
            Map<String, Object> nodeDetail = new HashMap<>();
            nodeDetail.put("status", status);
            nodeDetail.put("operator", operator);
            nodeDetail.put("actionType", actionType);
            nodeDetail.put("overrideReason", overrideReason);
            nodes.put(targetNode, nodeDetail);
            for (String nxtId : activatedNextList) {
                Map<String, Object> nxtDetail = new HashMap<>();
                nxtDetail.put("status", "IN_PROGRESS");
                nxtDetail.put("operator", operator);
                nxtDetail.put("actionType", "AUTO_ACTIVATE");
                nodes.put(nxtId, nxtDetail);
            }
            state.put("nodes", nodes);

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(reqDagFile, state);

            if ("REQ-001".equalsIgnoreCase(reqCode)) {
                File globalDag = new File(getAiDirectory(), "dag_state.json");
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(globalDag, state);
            }

            // 同步刷新本地名片卡投影 (推进至新焦点工位)
            syncTier1SnapshotProjection(reqCode, nextFocusNode, trackMode, operator);

            String msg = activatedNextList.isEmpty()
                    ? "需求 [" + reqCode + "] 工位 [" + targetNode + "] 已完成并落库"
                    : "需求 [" + reqCode + "] 工位 [" + targetNode + "] 已完成，并自动顺推点亮: " + String.join(", ", activatedNextList);
            return Result.success(state, msg);
        } catch (IOException e) {
            return Result.error(500, "持久化工位状态失败: " + e.getMessage());
        }
    }

    /**
     * 查询指定需求的历史流转与人机协同审计履历
     */
    @GetMapping("/records")
    public Result<List<ReqWorkflowRecord>> listWorkflowRecords(
            @RequestParam(value = "reqCode", required = false, defaultValue = "REQ-001") String reqCode) {
        if (workflowRecordMapper == null) {
            return Result.success(Collections.emptyList());
        }
        try {
            LambdaQueryWrapper<ReqWorkflowRecord> query = new LambdaQueryWrapper<>();
            query.eq(ReqWorkflowRecord::getReqCode, reqCode)
                    .orderByDesc(ReqWorkflowRecord::getCreatedAt);
            List<ReqWorkflowRecord> records = workflowRecordMapper.selectList(query);
            return Result.success(records);
        } catch (Exception e) {
            return Result.success(Collections.emptyList());
        }
    }

    // =========================================================================
    // 🗺️ 工作流模板治理 API (组织级唯一默认模板、基于模板克隆复制、定制保存)
    // =========================================================================

    /**
     * 获取所有可用工作流模板列表（含全局默认和项目专属）
     */
    @GetMapping("/templates")
    public Result<List<ReqWorkflowTemplate>> listTemplates(
            @RequestParam(value = "projectId", required = false) Long projectId) {
        if (workflowTemplateMapper == null) {
            return Result.success(Collections.emptyList());
        }
        try {
            LambdaQueryWrapper<ReqWorkflowTemplate> query = new LambdaQueryWrapper<>();
            if (projectId != null) {
                query.and(q -> q.eq(ReqWorkflowTemplate::getScopeType, "GLOBAL")
                        .or().eq(ReqWorkflowTemplate::getProjectId, projectId));
            }
            query.orderByDesc(ReqWorkflowTemplate::getIsSystemDefault)
                 .orderByDesc(ReqWorkflowTemplate::getCreatedAt);
            List<ReqWorkflowTemplate> list = workflowTemplateMapper.selectList(query);
            return Result.success(list);
        } catch (Exception e) {
            return Result.error(500, "获取工作流模板列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定工作流模板被哪些项目挂载使用
     */
    @GetMapping("/templates/{id}/mounted-projects")
    public Result<List<ReqProject>> getMountedProjects(@PathVariable("id") Long templateId) {
        if (projectMapper == null) {
            return Result.success(Collections.emptyList());
        }
        try {
            LambdaQueryWrapper<ReqProject> query = new LambdaQueryWrapper<>();
            query.eq(ReqProject::getWorkflowTemplateId, templateId)
                 .orderByDesc(ReqProject::getCreatedAt);
            List<ReqProject> list = projectMapper.selectList(query);
            return Result.success(list);
        } catch (Exception e) {
            return Result.error(500, "获取模板挂载项目失败: " + e.getMessage());
        }
    }

    /**
     * 将项目批量挂载/解绑到指定工作流模板
     */
    @PostMapping("/templates/{id}/mount")
    public Result<Void> mountProjectsToTemplate(
            @PathVariable("id") Long templateId,
            @RequestBody Map<String, Object> body) {
        if (projectMapper == null) {
            return Result.error(500, "数据库服务未就绪");
        }
        try {
            List<Integer> projectIds = (List<Integer>) body.get("projectIds");
            if (projectIds != null && !projectIds.isEmpty()) {
                for (Integer pId : projectIds) {
                    ReqProject p = projectMapper.selectById(pId);
                    if (p != null) {
                        p.setWorkflowTemplateId(templateId);
                        projectMapper.updateById(p);
                    }
                }
            }
            return Result.success(null, "项目已成功挂载至当前工作流模板！");
        } catch (Exception e) {
            return Result.error(500, "挂载项目失败: " + e.getMessage());
        }
    }

    /**
     * 获取单个工作流模板详情
     */
    @GetMapping("/templates/{id}")
    public Result<ReqWorkflowTemplate> getTemplateDetail(@PathVariable("id") Long id) {
        if (workflowTemplateMapper == null) {
            return Result.error(500, "数据库服务未就绪");
        }
        ReqWorkflowTemplate tpl = workflowTemplateMapper.selectById(id);
        if (tpl == null) {
            return Result.error(404, "工作流模板不存在");
        }
        return Result.success(tpl);
    }

    /**
     * 一键重置/初始化官方默认工作流模板 (TPL-DEFAULT)
     * 若数据库不存在则自动创建，若已存在则更新恢复为出厂标准设置
     */
    @PostMapping("/templates/init-default")
    public Result<ReqWorkflowTemplate> initDefaultTemplate(@RequestParam(value = "operator", defaultValue = "admin") String operator) {
        return createOrUpdateSystemTemplate("TPL-DEFAULT", "全局默认 · AI辅助四层一体化研发工作流", 
                "Apex 标准生产工艺：4-Layer 全景拓扑（SOP指令轨 + AI Skill专家轨 + 控制中枢轨 + 确定性物理产物轨），支持双轨延迟汇合与 B2 旁路微创快轨。", operator);
    }

    /**
     * 一键追加/新建一个新的默认工作流模板
     * 每次点击均生成一个新的具有独立唯一编号的默认工作流模板 (如 TPL-DEF-1234)
     */
    @PostMapping("/templates/create-default")
    public Result<ReqWorkflowTemplate> createNewDefaultTemplate(
            @RequestParam(value = "name", required = false) String customName,
            @RequestParam(value = "operator", defaultValue = "admin") String operator) {
        if (workflowTemplateMapper == null) {
            return Result.error(500, "数据库服务未就绪");
        }
        try {
            long suffix = System.currentTimeMillis() % 10000;
            String newCode = "TPL-DEF-" + suffix;
            String tplName = (customName != null && !customName.trim().isEmpty()) 
                    ? customName.trim() 
                    : "全局默认 · AI辅助研发工作流 (#" + suffix + ")";

            String defaultNodes = "[{\"id\":\"SOP-00\",\"name\":\"体系总览\",\"role\":\"全员\",\"skill\":\"-\",\"sop\":\".ai/sop/00-sop-overview.md\",\"gate\":\"自适应分流\"},{\"id\":\"SOP-01\",\"name\":\"需求规划\",\"role\":\"产品/张工\",\"skill\":\"requirement-discovery\",\"sop\":\".ai/sop/01-sop-planning.md\",\"gate\":\"四层清单齐备\"},{\"id\":\"SOP-02\",\"name\":\"方案设计\",\"role\":\"研发/李工\",\"skill\":\"design-spec-architect\",\"sop\":\".ai/sop/02-sop-tech-design.md\",\"gate\":\"python3 gate.py --sql\"},{\"id\":\"TC-GEN\",\"name\":\"测试用例矩阵\",\"role\":\"测试/王工\",\"skill\":\"quality-verifier\",\"sop\":\".ai/sop/01-sop-planning.md\",\"gate\":\"用例覆盖100%\"},{\"id\":\"SOP-03\",\"name\":\"TDD编码\",\"role\":\"研发/李工\",\"skill\":\"coding-standards\",\"sop\":\".ai/sop/03-sop-coding.md\",\"gate\":\"单元测试全绿\"},{\"id\":\"SOP-04\",\"name\":\"机器门禁\",\"role\":\"看门狗探针\",\"skill\":\"code-quality-reviewer\",\"sop\":\".ai/sop/04-sop-code-review.md\",\"gate\":\"python3 gate.py --staged [LGTM]\"},{\"id\":\"SOP-05\",\"name\":\"人工评审\",\"role\":\"架构师/程工\",\"skill\":\"code-quality-reviewer\",\"sop\":\".ai/sop/05-sop-review.md\",\"gate\":\"架构师签字核准\"},{\"id\":\"SOP-06\",\"name\":\"集成测试\",\"role\":\"测试/王工\",\"skill\":\"quality-verifier\",\"sop\":\".ai/sop/06-sop-integration.md\",\"gate\":\"TC用例100%碰撞通过\"},{\"id\":\"SOP-07\",\"name\":\"双向验收\",\"role\":\"看门狗探针\",\"skill\":\"quality-verifier\",\"sop\":\".ai/sop/07-sop-acceptance.md\",\"gate\":\"board.py verify放行\"},{\"id\":\"SOP-08\",\"name\":\"容器部署\",\"role\":\"运维/李工\",\"skill\":\"-\",\"sop\":\".ai/sop/08-sop-deploy.md\",\"gate\":\"健康检查通过\"},{\"id\":\"SOP-09\",\"name\":\"Git发布\",\"role\":\"交付/张工\",\"skill\":\"-\",\"sop\":\".ai/sop/09-sop-git-release.md\",\"gate\":\"Release资产落盘\"},{\"id\":\"SOP-B2\",\"name\":\"极速修补\",\"role\":\"研发/李工\",\"skill\":\"quality-verifier\",\"sop\":\".ai/sop/B2-sop-fasttrack.md\",\"gate\":\"单文件≤20行\"}]";

            ReqWorkflowTemplate tpl = ReqWorkflowTemplate.builder()
                    .templateCode(newCode)
                    .name(tplName)
                    .description("Apex 标准生产工艺：4-Layer 全景拓扑（SOP指令轨 + AI Skill专家轨 + 控制中枢轨 + 确定性物理产物轨），支持双轨延迟汇合与 B2 旁路微创快轨。")
                    .isSystemDefault(1)
                    .scopeType("GLOBAL")
                    .projectId(null)
                    .projectKey(null)
                    .nodesConfig(defaultNodes)
                    .edgesConfig("[]")
                    .createdBy(operator)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            workflowTemplateMapper.insert(tpl);
            return Result.success(tpl, "已成功追加一个新的默认工作流模板 [" + tplName + "]！");
        } catch (Exception e) {
            return Result.error(500, "追加默认工作流模板失败: " + e.getMessage());
        }
    }

    private Result<ReqWorkflowTemplate> createOrUpdateSystemTemplate(String code, String name, String desc, String operator) {
        try {
            LambdaQueryWrapper<ReqWorkflowTemplate> query = new LambdaQueryWrapper<>();
            query.eq(ReqWorkflowTemplate::getTemplateCode, code);
            ReqWorkflowTemplate exist = workflowTemplateMapper.selectOne(query);

            String defaultNodes = "[{\"id\":\"SOP-00\",\"name\":\"体系总览\",\"role\":\"全员\",\"skill\":\"-\",\"sop\":\".ai/sop/00-sop-overview.md\",\"gate\":\"自适应分流\"},{\"id\":\"SOP-01\",\"name\":\"需求规划\",\"role\":\"产品/张工\",\"skill\":\"requirement-discovery\",\"sop\":\".ai/sop/01-sop-planning.md\",\"gate\":\"四层清单齐备\"},{\"id\":\"SOP-02\",\"name\":\"方案设计\",\"role\":\"研发/李工\",\"skill\":\"design-spec-architect\",\"sop\":\".ai/sop/02-sop-tech-design.md\",\"gate\":\"python3 gate.py --sql\"},{\"id\":\"TC-GEN\",\"name\":\"测试用例矩阵\",\"role\":\"测试/王工\",\"skill\":\"quality-verifier\",\"sop\":\".ai/sop/01-sop-planning.md\",\"gate\":\"用例覆盖100%\"},{\"id\":\"SOP-03\",\"name\":\"TDD编码\",\"role\":\"研发/李工\",\"skill\":\"coding-standards\",\"sop\":\".ai/sop/03-sop-coding.md\",\"gate\":\"单元测试全绿\"},{\"id\":\"SOP-04\",\"name\":\"机器门禁\",\"role\":\"看门狗探针\",\"skill\":\"code-quality-reviewer\",\"sop\":\".ai/sop/04-sop-code-review.md\",\"gate\":\"python3 gate.py --staged [LGTM]\"},{\"id\":\"SOP-05\",\"name\":\"人工评审\",\"role\":\"架构师/程工\",\"skill\":\"code-quality-reviewer\",\"sop\":\".ai/sop/05-sop-review.md\",\"gate\":\"架构师签字核准\"},{\"id\":\"SOP-06\",\"name\":\"集成测试\",\"role\":\"测试/王工\",\"skill\":\"quality-verifier\",\"sop\":\".ai/sop/06-sop-integration.md\",\"gate\":\"TC用例100%碰撞通过\"},{\"id\":\"SOP-07\",\"name\":\"双向验收\",\"role\":\"看门狗探针\",\"skill\":\"quality-verifier\",\"sop\":\".ai/sop/07-sop-acceptance.md\",\"gate\":\"board.py verify放行\"},{\"id\":\"SOP-08\",\"name\":\"容器部署\",\"role\":\"运维/李工\",\"skill\":\"-\",\"sop\":\".ai/sop/08-sop-deploy.md\",\"gate\":\"健康检查通过\"},{\"id\":\"SOP-09\",\"name\":\"Git发布\",\"role\":\"交付/张工\",\"skill\":\"-\",\"sop\":\".ai/sop/09-sop-git-release.md\",\"gate\":\"Release资产落盘\"},{\"id\":\"SOP-B2\",\"name\":\"极速修补\",\"role\":\"研发/李工\",\"skill\":\"quality-verifier\",\"sop\":\".ai/sop/B2-sop-fasttrack.md\",\"gate\":\"单文件≤20行\"}]";

            if (exist != null) {
                exist.setName(name);
                exist.setDescription(desc);
                exist.setIsSystemDefault(1);
                exist.setScopeType("GLOBAL");
                exist.setNodesConfig(defaultNodes);
                exist.setEdgesConfig("[]");
                exist.setUpdatedAt(LocalDateTime.now());
                workflowTemplateMapper.updateById(exist);
                return Result.success(exist, "官方系统默认工作流模板已恢复并更新！后续可为任意项目挂载绑定。");
            } else {
                ReqWorkflowTemplate tpl = ReqWorkflowTemplate.builder()
                        .templateCode(code)
                        .name(name)
                        .description(desc)
                        .isSystemDefault(1)
                        .scopeType("GLOBAL")
                        .projectId(null)
                        .projectKey(null)
                        .nodesConfig(defaultNodes)
                        .edgesConfig("[]")
                        .createdBy(operator)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                workflowTemplateMapper.insert(tpl);
                return Result.success(tpl, "官方系统默认工作流模板已一键初始化创建成功！");
            }
        } catch (Exception e) {
            return Result.error(500, "操作默认工作流模板失败: " + e.getMessage());
        }
    }

    /**
     * 基于已有模板进行复制克隆 (Clone/Fork)
     */
    @PostMapping("/templates/clone")
    public Result<ReqWorkflowTemplate> cloneTemplate(@RequestBody Map<String, Object> body) {
        if (workflowTemplateMapper == null) {
            return Result.error(500, "数据库服务未就绪");
        }
        try {
            Long sourceId = Long.valueOf(body.get("sourceTemplateId").toString());
            String newName = (String) body.getOrDefault("name", "未命名定制模板");
            String newDesc = (String) body.getOrDefault("description", "");
            Long targetProjectId = body.get("targetProjectId") != null ? Long.valueOf(body.get("targetProjectId").toString()) : null;
            String targetProjectKey = (String) body.getOrDefault("targetProjectKey", "");
            String operator = (String) body.getOrDefault("operator", "admin");

            ReqWorkflowTemplate source = workflowTemplateMapper.selectById(sourceId);
            if (source == null) {
                return Result.error(404, "源模板不存在，无法复制");
            }

            String newCode = "TPL-" + (targetProjectKey != null && !targetProjectKey.isEmpty() ? targetProjectKey : "CUSTOM") + "-" + System.currentTimeMillis() % 10000;

            ReqWorkflowTemplate target = ReqWorkflowTemplate.builder()
                    .templateCode(newCode)
                    .name(newName)
                    .description(newDesc != null && !newDesc.isEmpty() ? newDesc : "衍生自 [" + source.getName() + "] 的专属定制流程")
                    .isSystemDefault(0)
                    .scopeType(targetProjectId != null ? "PROJECT" : "GLOBAL")
                    .projectId(targetProjectId)
                    .projectKey(targetProjectKey)
                    .nodesConfig(source.getNodesConfig())
                    .edgesConfig(source.getEdgesConfig())
                    .createdBy(operator)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            workflowTemplateMapper.insert(target);
            return Result.success(target, "工作流模板已成功从 [" + source.getName() + "] 复制克隆！");
        } catch (Exception e) {
            return Result.error(500, "复制工作流模板失败: " + e.getMessage());
        }
    }

    /**
     * 更新保存工作流模板拓扑蓝本配置
     */
    @PutMapping("/templates/{id}")
    public Result<ReqWorkflowTemplate> updateTemplate(
            @PathVariable("id") Long id,
            @RequestBody Map<String, Object> body) {
        if (workflowTemplateMapper == null) {
            return Result.error(500, "数据库服务未就绪");
        }
        try {
            ReqWorkflowTemplate exist = workflowTemplateMapper.selectById(id);
            if (exist == null) {
                return Result.error(404, "工作流模板不存在");
            }

            if (body.containsKey("name")) exist.setName((String) body.get("name"));
            if (body.containsKey("description")) exist.setDescription((String) body.get("description"));
            if (body.containsKey("nodesConfig")) exist.setNodesConfig((String) body.get("nodesConfig"));
            if (body.containsKey("edgesConfig")) exist.setEdgesConfig((String) body.get("edgesConfig"));
            exist.setUpdatedAt(LocalDateTime.now());

            workflowTemplateMapper.updateById(exist);
            return Result.success(exist, "工作流模板配置已成功保存！");
        } catch (Exception e) {
            return Result.error(500, "保存工作流模板失败: " + e.getMessage());
        }
    }

    /**
     * 删除工作流模板 (级联解绑关联的项目并安全清理)
     * 规则保护：唯一出厂默认模板 TPL-DEFAULT 禁止删除，保证系统至少保留一套基准规范
     */
    @DeleteMapping("/templates/{id}")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteTemplate(@PathVariable("id") Long id) {
        if (workflowTemplateMapper == null) {
            return Result.error(500, "数据库服务未就绪");
        }
        try {
            ReqWorkflowTemplate exist = workflowTemplateMapper.selectById(id);
            if (exist == null) {
                return Result.error(404, "工作流模板不存在");
            }
            if ("TPL-DEFAULT".equalsIgnoreCase(exist.getTemplateCode())) {
                return Result.error(400, "系统官方唯一出厂基准模板 [TPL-DEFAULT] 受核心保护，禁止删除！");
            }

            // 1. 查询所有原本挂载在此模板上的关联项目
            Long fallbackTplId = 1L;
            LambdaQueryWrapper<ReqWorkflowTemplate> baseQuery = new LambdaQueryWrapper<>();
            baseQuery.eq(ReqWorkflowTemplate::getTemplateCode, "TPL-DEFAULT");
            ReqWorkflowTemplate baseTpl = workflowTemplateMapper.selectOne(baseQuery);
            if (baseTpl != null) {
                fallbackTplId = baseTpl.getId();
            }

            if (projectMapper != null) {
                LambdaQueryWrapper<ReqProject> pQuery = new LambdaQueryWrapper<>();
                pQuery.eq(ReqProject::getWorkflowTemplateId, id);
                List<ReqProject> boundProjects = projectMapper.selectList(pQuery);

                if (boundProjects != null && !boundProjects.isEmpty()) {
                    // 将挂载此模板的项目回退重置绑定到官方基础默认模板
                    for (ReqProject p : boundProjects) {
                        p.setWorkflowTemplateId(fallbackTplId);
                        projectMapper.updateById(p);
                    }
                }
            }

            // 2. 物理删除工作流模板记录
            workflowTemplateMapper.deleteById(id);
            return Result.success(null, "工作流模板 [" + exist.getName() + "] 及其项目关联已全部安全解除并删除！");
        } catch (Exception e) {
            return Result.error(500, "删除工作流模板失败: " + e.getMessage());
        }
    }

    /**
     * 🤖 获取系统级 AI Skills 专家库清单与 SKILL.md 实体规约内容
     */
    @GetMapping("/skills")
    public Result<List<Map<String, Object>>> listSkills() {
        List<Map<String, Object>> list = new ArrayList<>();
        File rootDir = getWorkspaceRootDir();
        File skillsDir = new File(rootDir, ".agents/skills");
        if (skillsDir.exists() && skillsDir.isDirectory()) {
            File[] files = skillsDir.listFiles();
            if (files != null) {
                Arrays.sort(files, Comparator.comparing(File::getName));
                for (File dir : files) {
                    if (dir.isDirectory()) {
                        File skillMd = new File(dir, "SKILL.md");
                        String content = "";
                        String desc = "";
                        String name = dir.getName();
                        if (skillMd.exists() && skillMd.isFile()) {
                            try {
                                content = new String(java.nio.file.Files.readAllBytes(skillMd.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                                // 提取 description
                                if (content.contains("description:")) {
                                    int start = content.indexOf("description:") + "description:".length();
                                    int end = content.indexOf("\n", start);
                                    if (end > start) {
                                        desc = content.substring(start, end).trim();
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", dir.getName());
                        map.put("name", name);
                        map.put("desc", desc);
                        map.put("path", ".agents/skills/" + dir.getName() + "/SKILL.md");
                        map.put("content", content);
                        list.add(map);
                    }
                }
            }
        }
        return Result.success(list, "AI Skills 资产列表读取成功");
    }

    /**
     * 🛠️ 获取系统级底层自动化治理/看门狗脚本清单与源码内容
     */
    @GetMapping("/scripts")
    public Result<List<Map<String, Object>>> listScripts() {
        List<Map<String, Object>> list = new ArrayList<>();
        File rootDir = getWorkspaceRootDir();
        File scriptsDir = new File(rootDir, ".ai/scripts");
        if (scriptsDir.exists() && scriptsDir.isDirectory()) {
            File[] files = scriptsDir.listFiles((d, name) -> name.endsWith(".py") || name.endsWith(".sh"));
            if (files != null) {
                Arrays.sort(files, Comparator.comparing(File::getName));
                for (File f : files) {
                    String content = "";
                    try {
                        content = new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Exception ignored) {}
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", f.getName());
                    map.put("name", f.getName());
                    map.put("runtime", f.getName().endsWith(".py") ? "Python 3" : "Shell Script");
                    map.put("path", ".ai/scripts/" + f.getName());
                    map.put("content", content);
                    list.add(map);
                }
            }
        }
        return Result.success(list, "自动化脚本资产列表读取成功");
    }

    /*
      📄 通用工作流规约/脚本文件在线透视读取
      支持读取 .ai/sop*//*.md, .ai/scripts/*.py, .agents/skills/**//*SKILL.md 等工程白皮书
    */
    @GetMapping("/file-content")
    public Result<Map<String, Object>> getWorkflowFileContent(@RequestParam("path") String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return Result.error(400, "文件路径不能为空");
        }
        // 安全拦截路径穿越攻击
        if (relativePath.contains("..")) {
            return Result.error(400, "非法文件访问路径");
        }

        File rootDir = getWorkspaceRootDir();
        String cleanPath = relativePath.trim().startsWith("/") ? relativePath.trim().substring(1) : relativePath.trim();
        File targetFile = new File(rootDir, cleanPath);

        if (!targetFile.exists() || !targetFile.isFile()) {
            return Result.error(404, "规约/源文件不存在: " + cleanPath);
        }

        try {
            String content = new String(java.nio.file.Files.readAllBytes(targetFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            Map<String, Object> map = new HashMap<>();
            map.put("path", cleanPath);
            map.put("name", targetFile.getName());
            map.put("extension", targetFile.getName().contains(".") ? targetFile.getName().substring(targetFile.getName().lastIndexOf(".") + 1) : "txt");
            map.put("content", content);
            return Result.success(map, "文件内容读取成功");
        } catch (Exception e) {
            return Result.error(500, "读取文件失败: " + e.getMessage());
        }
    }

    /**
     * 获取工程根目录 (兼容平铺与多模块运行目录)
     */
    private File getWorkspaceRootDir() {
        File dir = new File(System.getProperty("user.dir"));
        if (new File(dir, ".agents").exists()) {
            return dir;
        }
        if (dir.getParentFile() != null && new File(dir.getParentFile(), ".agents").exists()) {
            return dir.getParentFile();
        }
        if (dir.getParentFile() != null && dir.getParentFile().getParentFile() != null && new File(dir.getParentFile().getParentFile(), ".agents").exists()) {
            return dir.getParentFile().getParentFile();
        }
        if (dir.getParentFile() != null && dir.getParentFile().getParentFile() != null && dir.getParentFile().getParentFile().getParentFile() != null && new File(dir.getParentFile().getParentFile().getParentFile(), ".agents").exists()) {
            return dir.getParentFile().getParentFile().getParentFile();
        }
        return dir;
    }
}
