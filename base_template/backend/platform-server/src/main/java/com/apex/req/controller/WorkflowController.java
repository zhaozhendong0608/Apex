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
            String content = "<!-- 【三层记忆金字塔 - 第一层：极简快照名片 (<30行)】 -->\n" +
                    "# ⚡ tier1_snapshot.md · 极简工作区名片\n\n" +
                    "- **项目空间**: NET-SIM (全景工业网络仿真与拓扑排版系统)\n" +
                    "- **当前聚焦需求**: " + reqCode + "\n" +
                    "- **当前工位**: [" + targetNode + "] (模式: " + trackMode + ")\n" +
                    "- **责任操作人**: " + operator + "\n" +
                    "- **单一事实源 (SSOT)**: MySQL `t_req_requirement` / `t_req_workflow_record`\n" +
                    "- **最后同步时间**: " + LocalDateTime.now() + "\n\n" +
                    "## 🎯 核心质量与安全护栏\n" +
                    "1. 单次改动 ≤ 3 文件 & 零野公网依赖\n" +
                    "2. 严禁假 Mock，必须通过 gate.py 机器门禁\n" +
                    "3. 唯一归档出口: board.py verify ➔ complete --accept\n";
            try (FileWriter writer = new FileWriter(snapshotFile)) {
                writer.write(content);
            }
        } catch (Exception ex) {
            System.err.println("刷新本地 tier1_snapshot.md 镜像失败(非阻塞): " + ex.getMessage());
        }
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
     * 核心双驱：写入 MySQL (SSOT) ➔ 实时更新 .ai/ 本地轻量镜像
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
            @RequestParam(value = "operator", defaultValue = "admin") String operator) {

        // 1. 核心持久化：更新 MySQL 需求主表 t_req_requirement
        if (requirementMapper != null) {
            try {
                LambdaUpdateWrapper<ReqRequirement> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.eq(ReqRequirement::getReqCode, reqCode)
                        .set(ReqRequirement::getCurrentNodeId, targetNode)
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
                ReqWorkflowRecord record = ReqWorkflowRecord.builder()
                        .reqCode(reqCode)
                        .nodeId(targetNode)
                        .nodeName(nodeName.isEmpty() ? targetNode : nodeName)
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

        // 3. 反向投影：刷新本地 .ai/ 运行时文件与 tier1_snapshot.md 名片
        File reqDagFile = getRequirementDagFile(reqCode);
        Map<String, Object> state = new HashMap<>();
        try {
            if (reqDagFile.exists()) {
                state = objectMapper.readValue(reqDagFile, Map.class);
            }
            state.put("reqCode", reqCode);
            state.put("current_node", targetNode);
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
            state.put("nodes", nodes);

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(reqDagFile, state);

            if ("REQ-001".equalsIgnoreCase(reqCode)) {
                File globalDag = new File(getAiDirectory(), "dag_state.json");
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(globalDag, state);
            }

            // 同步刷新本地名片卡投影
            syncTier1SnapshotProjection(reqCode, targetNode, trackMode, operator);

            return Result.success(state, "需求 [" + reqCode + "] 工位已持久化更新至: " + targetNode);
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
     * 更新保存定制模板 (禁止篡改系统唯一默认模板)
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
            if (Integer.valueOf(1).equals(exist.getIsSystemDefault())) {
                return Result.error(400, "系统默认工作流模板受法律级保护，禁止直接篡改！请先点击「复制克隆」创建衍生定制模板。");
            }

            if (body.containsKey("name")) exist.setName((String) body.get("name"));
            if (body.containsKey("description")) exist.setDescription((String) body.get("description"));
            if (body.containsKey("nodesConfig")) exist.setNodesConfig((String) body.get("nodesConfig"));
            if (body.containsKey("edgesConfig")) exist.setEdgesConfig((String) body.get("edgesConfig"));
            exist.setUpdatedAt(LocalDateTime.now());

            workflowTemplateMapper.updateById(exist);
            return Result.success(exist, "定制工作流模板配置已更新保存！");
        } catch (Exception e) {
            return Result.error(500, "保存工作流模板失败: " + e.getMessage());
        }
    }

    /**
     * 删除定制模板 (系统默认模板禁止删除)
     */
    @DeleteMapping("/templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable("id") Long id) {
        if (workflowTemplateMapper == null) {
            return Result.error(500, "数据库服务未就绪");
        }
        ReqWorkflowTemplate exist = workflowTemplateMapper.selectById(id);
        if (exist == null) {
            return Result.error(404, "模板不存在");
        }
        if (Integer.valueOf(1).equals(exist.getIsSystemDefault())) {
            return Result.error(400, "系统默认模板为全局蓝本，禁止删除！");
        }
        workflowTemplateMapper.deleteById(id);
        return Result.success(null, "工作流模板已成功删除！");
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
