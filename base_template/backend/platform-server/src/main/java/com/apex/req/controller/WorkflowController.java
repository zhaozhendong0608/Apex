package com.apex.req.controller;

import com.apex.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 🚀 DAG 工作流拓扑与多需求独立状态机 Controller
 * 支持针对每个需求持久化独立的有向无环图工位进度，彻底打通 .ai/ 运行时
 */
@RestController
@RequestMapping("/api/v1/req/workflow")
public class WorkflowController {

    private final ObjectMapper objectMapper = new ObjectMapper();

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
     * 获取指定需求的 DAG 真实工位状态
     * 针对每个需求支持独立状态文件；若尚无独立文件，则拉取全局 .ai/dag_state.json 或自适应初始化
     */
    @GetMapping("/dag-state")
    public Result<Map<String, Object>> getDagState(
            @RequestParam(value = "reqCode", required = false, defaultValue = "REQ-001") String reqCode,
            @RequestParam(value = "trackMode", required = false, defaultValue = "STANDARD") String trackMode) {
        
        Map<String, Object> response = new HashMap<>();
        File reqDagFile = getRequirementDagFile(reqCode);
        File globalDagFile = new File(getAiDirectory(), "dag_state.json");

        try {
            if (reqDagFile.exists()) {
                response = objectMapper.readValue(reqDagFile, Map.class);
            } else if (globalDagFile.exists() && "REQ-001".equalsIgnoreCase(reqCode)) {
                response = objectMapper.readValue(globalDagFile, Map.class);
            } else {
                // 为新需求动态生成默认初始有向图状态
                response.put("reqCode", reqCode);
                response.put("current_node", "FASTTRACK".equalsIgnoreCase(trackMode) ? "B2-01" : "LM-01");
                response.put("active_module", "req-management");
                response.put("mode", "FROZEN");
                response.put("nodes", new HashMap<String, Object>());
            }
        } catch (Exception e) {
            response.put("current_node", "LM-01");
            response.put("reqCode", reqCode);
        }

        response.put("reqCode", reqCode);
        response.put("trackMode", trackMode);
        return Result.success(response);
    }

    /**
     * 推进/更新指定需求的工位状态 (ADVANCE / SET)
     */
    @PostMapping("/advance")
    public Result<Map<String, Object>> advanceRequirementDag(
            @RequestParam("reqCode") String reqCode,
            @RequestParam("targetNode") String targetNode,
            @RequestParam(value = "status", defaultValue = "COMPLETED") String status,
            @RequestParam(value = "operator", defaultValue = "admin") String operator) {
        
        File reqDagFile = getRequirementDagFile(reqCode);
        Map<String, Object> state = new HashMap<>();
        try {
            if (reqDagFile.exists()) {
                state = objectMapper.readValue(reqDagFile, Map.class);
            }
            state.put("reqCode", reqCode);
            state.put("current_node", targetNode);
            state.put("last_operator", operator);
            state.put("updated_at", new Date().toString());
            
            Map<String, Object> nodes = (Map<String, Object>) state.getOrDefault("nodes", new HashMap<String, Object>());
            Map<String, Object> nodeDetail = new HashMap<>();
            nodeDetail.put("status", status);
            nodeDetail.put("operator", operator);
            nodes.put(targetNode, nodeDetail);
            state.put("nodes", nodes);

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(reqDagFile, state);

            // 若当前推进的是主需求 REQ-001，同步更新全局 dag_state.json 保持统一
            if ("REQ-001".equalsIgnoreCase(reqCode)) {
                File globalDag = new File(getAiDirectory(), "dag_state.json");
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(globalDag, state);
            }

            return Result.success(state, "需求 [" + reqCode + "] 工位已推进至: " + targetNode);
        } catch (IOException e) {
            return Result.error(500, "持久化工位状态失败: " + e.getMessage());
        }
    }
}
