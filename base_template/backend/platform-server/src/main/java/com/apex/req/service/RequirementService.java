package com.apex.req.service;

import com.apex.common.exception.BusinessException;
import com.apex.req.domain.dto.ConfirmItemDTO;
import com.apex.req.domain.dto.RequirementCreateDTO;
import com.apex.req.domain.entity.ReqConfirmItem;
import com.apex.req.domain.entity.ReqProject;
import com.apex.req.domain.entity.ReqPrototypeVersion;
import com.apex.req.domain.entity.ReqRequirement;
import com.apex.req.domain.vo.CostCalculationVO;
import com.apex.req.domain.vo.RequirementDetailVO;
import com.apex.req.domain.entity.ReqBaseline;
import com.apex.req.domain.entity.ReqWorkflowTemplate;
import com.apex.req.domain.entity.ReqWorkflowNodeState;
import com.apex.req.domain.vo.RequirementListItemVO;
import com.apex.req.mapper.ReqBaselineMapper;
import com.apex.req.mapper.ReqConfirmItemMapper;
import com.apex.req.mapper.ReqProjectMapper;
import com.apex.req.mapper.ReqPrototypeVersionMapper;
import com.apex.req.mapper.ReqRequirementMapper;
import com.apex.req.mapper.ReqWorkflowTemplateMapper;
import com.apex.req.mapper.ReqWorkflowNodeStateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 需求核心服务与四层清单实时代价计算器 (绑定 DLD §3.1 & §4.2-4.3)
 */
@Slf4j
@Service
public class RequirementService extends ServiceImpl<ReqRequirementMapper, ReqRequirement> {

    private final ReqConfirmItemMapper confirmItemMapper;
    private final ReqPrototypeVersionMapper prototypeVersionMapper;
    private final ReqProjectMapper projectMapper;
    private final ReqBaselineMapper baselineMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private ReqWorkflowTemplateMapper workflowTemplateMapper;

    @Autowired(required = false)
    private ReqWorkflowNodeStateMapper workflowNodeStateMapper;

    public RequirementService(ReqConfirmItemMapper confirmItemMapper,
                              ReqPrototypeVersionMapper prototypeVersionMapper,
                              ReqProjectMapper projectMapper,
                              ReqBaselineMapper baselineMapper) {
        this.confirmItemMapper = confirmItemMapper;
        this.prototypeVersionMapper = prototypeVersionMapper;
        this.projectMapper = projectMapper;
        this.baselineMapper = baselineMapper;
    }

    /**
     * 针对工作台查询需求列表视图 (带基线版本与实时代价折算)
     */
    public List<RequirementListItemVO> listRequirementVOs(Long projectId, String status) {
        List<ReqRequirement> list = lambdaQuery()
                .eq(projectId != null, ReqRequirement::getProjectId, projectId)
                .eq(StringUtils.hasText(status), ReqRequirement::getStatus, status)
                .orderByDesc(ReqRequirement::getId)
                .list();

        if (list.isEmpty()) {
            return Collections.emptyList();
        }

        List<RequirementListItemVO> voList = new ArrayList<>(list.size());
        for (ReqRequirement req : list) {
            String baselineCode = "—";
            BigDecimal totalCost = BigDecimal.ZERO;

            ReqBaseline baseline = baselineMapper.selectOne(
                    new LambdaQueryWrapper<ReqBaseline>()
                            .eq(ReqBaseline::getRequirementId, req.getId())
                            .orderByDesc(ReqBaseline::getId)
                            .last("LIMIT 1")
            );
            if (baseline != null) {
                baselineCode = baseline.getVersion();
                totalCost = baseline.getTotalCostDays() != null ? baseline.getTotalCostDays() : BigDecimal.ZERO;
            } else {
                CostCalculationVO cost = calculateCost(req.getId());
                totalCost = cost.getTotalCostDays();
            }

            RequirementListItemVO vo = RequirementListItemVO.builder()
                    .id(req.getId())
                    .projectId(req.getProjectId())
                    .reqCode(req.getReqCode())
                    .title(req.getTitle())
                    .rawQuote(req.getRawQuote())
                    .triggerEvent(req.getTriggerEvent())
                    .status(req.getStatus())
                    .priority(req.getPriority())
                    .ownerName(req.getOwnerName())
                    .baselineCode(baselineCode)
                    .totalCostDays(totalCost)
                    .createdAt(req.getCreatedAt())
                    .updatedAt(req.getUpdatedAt())
                    .build();
            voList.add(vo);
        }
        return voList;
    }

    /**
     * 快速录入需求 (客户原话不可篡改留痕)
     */
    @Transactional(rollbackFor = Exception.class)
    public ReqRequirement createRequirement(RequirementCreateDTO dto, String operator) {
        ReqProject project = projectMapper.selectById(dto.getProjectId());
        if (project == null) {
            throw new BusinessException(404, "所属项目空间不存在 (ID: " + dto.getProjectId() + ")");
        }

        // 校验业务编码唯一性 (projectId + reqCode)
        Long count = lambdaQuery()
                .eq(ReqRequirement::getProjectId, dto.getProjectId())
                .eq(ReqRequirement::getReqCode, dto.getReqCode())
                .count();
        if (count > 0) {
            throw new BusinessException(400, "当前项目下需求编码已存在: " + dto.getReqCode());
        }

        ReqRequirement req = ReqRequirement.builder()
                .projectId(dto.getProjectId())
                .reqCode(dto.getReqCode())
                .title(dto.getTitle())
                .rawQuote(dto.getRawQuote())
                .triggerEvent(dto.getTriggerEvent())
                .status("CLARIFYING")
                .priority(StringUtils.hasText(dto.getPriority()) ? dto.getPriority() : "P1")
                .ownerName(dto.getOwnerName())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        save(req);

        // 如果携带了初始四层清单，批量插入
        if (dto.getConfirmItems() != null && !dto.getConfirmItems().isEmpty()) {
            for (ConfirmItemDTO itemDto : dto.getConfirmItems()) {
                ReqConfirmItem item = ReqConfirmItem.builder()
                        .requirementId(req.getId())
                        .category(itemDto.getCategory())
                        .itemCode(itemDto.getItemCode())
                        .question(itemDto.getQuestion())
                        .optionsJson(StringUtils.hasText(itemDto.getOptionsJson()) ? itemDto.getOptionsJson() : "[]")
                        .decision(StringUtils.hasText(itemDto.getDecision()) ? itemDto.getDecision() : "UNDECIDED")
                        .costDays(itemDto.getCostDays() != null ? itemDto.getCostDays() : BigDecimal.ZERO)
                        .decidedBy(operator)
                        .decidedAt(LocalDateTime.now())
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                confirmItemMapper.insert(item);
            }
        }

        log.info("成功创建需求 [ID:{}, Code:{}], 责任人: {}", req.getId(), req.getReqCode(), req.getOwnerName());

        // 🚀 【工作流实例化】将当前所属项目绑定的全量工作流模板克隆到该任务下，完成 SSOT 状态机持久化
        initWorkflowInstanceState(req, project, operator);

        return req;
    }

    /**
     * 为新需求原子初始化专属的工作流实例状态 (克隆项目模板到 t_req_workflow_node_state 并生成 .ai/dags 镜像)
     */
    private void initWorkflowInstanceState(ReqRequirement req, ReqProject project, String operator) {
        if (workflowNodeStateMapper == null) {
            return;
        }
        try {
            // 1. 查询项目挂载的模板；若未绑定则取系统全局默认模板
            ReqWorkflowTemplate template = null;
            if (workflowTemplateMapper != null) {
                if (project.getWorkflowTemplateId() != null) {
                    template = workflowTemplateMapper.selectById(project.getWorkflowTemplateId());
                }
                if (template == null) {
                    LambdaQueryWrapper<ReqWorkflowTemplate> tplQw = new LambdaQueryWrapper<>();
                    tplQw.eq(ReqWorkflowTemplate::getIsSystemDefault, 1);
                    template = workflowTemplateMapper.selectOne(tplQw);
                }
            }

            // 2. 解析模板节点并分类到 STANDARD 和 FASTTRACK 两个轨道
            List<Map<String, String>> standardNodes = new ArrayList<>();
            List<Map<String, String>> fasttrackNodes = new ArrayList<>();

            if (template != null && StringUtils.hasText(template.getNodesConfig())) {
                try {
                    List<Map<String, Object>> parsed = objectMapper.readValue(template.getNodesConfig(), List.class);
                    for (Map<String, Object> item : parsed) {
                        String nid = String.valueOf(item.getOrDefault("id", ""));
                        String nname = String.valueOf(item.getOrDefault("name", nid));
                        String ntrack = String.valueOf(item.getOrDefault("track", nid.startsWith("SOP-B") ? "FASTTRACK" : "STANDARD"));
                        Map<String, String> pair = new HashMap<>();
                        pair.put("id", nid);
                        pair.put("name", nname);
                        if ("FASTTRACK".equalsIgnoreCase(ntrack)) {
                            fasttrackNodes.add(pair);
                        } else {
                            standardNodes.add(pair);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("解析工作流模板节点 JSON 失败，回退默认节点: {}", ex.getMessage());
                }
            }

            // 保底节点定义
            if (standardNodes.isEmpty()) {
                String[][] defaults = {
                    {"SOP-00", "体系总览"}, {"SOP-01", "需求规划"}, {"SOP-02", "方案设计"},
                    {"TC-GEN", "测试用例矩阵"}, {"SOP-03", "TDD编码"}, {"SOP-04", "机器门禁"},
                    {"SOP-05", "人工评审"}, {"SOP-06", "集成测试"}, {"SOP-07", "双向验收"},
                    {"SOP-08", "容器部署"}, {"SOP-09", "Git发布"}
                };
                for (String[] pair : defaults) {
                    Map<String, String> m = new HashMap<>();
                    m.put("id", pair[0]);
                    m.put("name", pair[1]);
                    standardNodes.add(m);
                }
            }
            if (fasttrackNodes.isEmpty()) {
                String[][] defaults = {
                    {"SOP-B1", "老代码考古"}, {"SOP-B2", "极速微创"}, {"SOP-B3", "小黄鸭排错"},
                    {"SOP-B4", "故障急救"}, {"SOP-B5", "记忆归档"}, {"SOP-B6", "断点续传"}
                };
                for (String[] pair : defaults) {
                    Map<String, String> m = new HashMap<>();
                    m.put("id", pair[0]);
                    m.put("name", pair[1]);
                    fasttrackNodes.add(m);
                }
            }

            // 3. 批量持久化到 MySQL t_req_workflow_node_state
            // STANDARD 主轨: SOP-00 COMPLETED, SOP-01 IN_PROGRESS, 其余 PENDING
            for (int i = 0; i < standardNodes.size(); i++) {
                String nid = standardNodes.get(i).get("id");
                String nname = standardNodes.get(i).get("name");
                String st = (i == 0) ? "COMPLETED" : ((i == 1) ? "IN_PROGRESS" : "PENDING");
                ReqWorkflowNodeState ns = ReqWorkflowNodeState.builder()
                        .reqCode(req.getReqCode())
                        .trackMode("STANDARD")
                        .nodeId(nid)
                        .nodeName(nname)
                        .status(st)
                        .operator(("COMPLETED".equals(st) || "IN_PROGRESS".equals(st)) ? operator : null)
                        .completedAt("COMPLETED".equals(st) ? LocalDateTime.now() : null)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                workflowNodeStateMapper.insert(ns);
            }

            // FASTTRACK 旁路轨: 初始均为 PENDING
            for (Map<String, String> fn : fasttrackNodes) {
                ReqWorkflowNodeState ns = ReqWorkflowNodeState.builder()
                        .reqCode(req.getReqCode())
                        .trackMode("FASTTRACK")
                        .nodeId(fn.get("id"))
                        .nodeName(fn.get("name"))
                        .status("PENDING")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                workflowNodeStateMapper.insert(ns);
            }

            // 4. 同步生成本地物理镜像 .ai/dags/dag_{reqCode}.json 方便 CLI 与 RAG 读取
            syncLocalDagJson(req.getReqCode(), template, standardNodes);

            log.info("已成功为新需求 [{}] 克隆生成独立全量工作流实例", req.getReqCode());
        } catch (Exception e) {
            log.error("为需求 [{}] 初始化工作流状态失败(非阻断): {}", req.getReqCode(), e.getMessage());
        }
    }

    private void syncLocalDagJson(String reqCode, ReqWorkflowTemplate template, List<Map<String, String>> standardNodes) {
        try {
            String userDir = System.getProperty("user.dir");
            File aiDir = new File(userDir, ".ai");
            if (!aiDir.exists()) {
                File pAi = new File(userDir, "../.ai");
                if (pAi.exists()) aiDir = pAi;
            }
            File dagsDir = new File(aiDir, "dags");
            if (!dagsDir.exists()) {
                dagsDir.mkdirs();
            }
            File dagFile = new File(dagsDir, "dag_" + reqCode + ".json");
            Map<String, Object> dagContent = new HashMap<>();
            dagContent.put("reqCode", reqCode);
            dagContent.put("templateCode", template != null ? template.getTemplateCode() : "TPL-DEFAULT");
            dagContent.put("currentNode", "SOP-01");
            dagContent.put("trackMode", "STANDARD");
            dagContent.put("updatedAt", LocalDateTime.now().toString());
            dagContent.put("nodes", standardNodes);
            try (FileWriter writer = new FileWriter(dagFile)) {
                writer.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(dagContent));
            }
        } catch (Exception ex) {
            log.warn("写入本地 .ai/dags 镜像失败(非阻断): {}", ex.getMessage());
        }
    }

    /**
     * 批量更新四层清单决策并重算实时代价
     */
    @Transactional(rollbackFor = Exception.class)
    public CostCalculationVO saveConfirmItems(Long reqId, List<ConfirmItemDTO> items, String operator) {
        ReqRequirement req = getById(reqId);
        if (req == null) {
            throw new BusinessException(404, "需求不存在 (ID: " + reqId + ")");
        }
        // 400301: 已冻结基线禁止物理修改
        if ("BASELINED".equalsIgnoreCase(req.getStatus())) {
            throw new BusinessException(400301, "已冻结基线禁止物理修改");
        }

        // 1. 同步清理已在前端删除的持久化条目
        List<ReqConfirmItem> existingDbItems = confirmItemMapper.selectList(
                new LambdaQueryWrapper<ReqConfirmItem>().eq(ReqConfirmItem::getRequirementId, reqId)
        );
        Set<Long> incomingIds = (items == null) ? Collections.emptySet() : items.stream()
                .map(ConfirmItemDTO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (ReqConfirmItem dbItem : existingDbItems) {
            if (!incomingIds.contains(dbItem.getId())) {
                confirmItemMapper.deleteById(dbItem.getId());
            }
        }

        // 2. 逐项更新已有或插入新增条目
        if (items != null) {
            for (ConfirmItemDTO itemDto : items) {
                if (itemDto.getId() != null) {
                    ReqConfirmItem existing = confirmItemMapper.selectById(itemDto.getId());
                    if (existing != null && existing.getRequirementId().equals(reqId)) {
                        existing.setCategory(itemDto.getCategory());
                        existing.setItemCode(itemDto.getItemCode());
                        existing.setQuestion(itemDto.getQuestion());
                        if (StringUtils.hasText(itemDto.getOptionsJson())) {
                            existing.setOptionsJson(itemDto.getOptionsJson());
                        }
                        if (StringUtils.hasText(itemDto.getDecision())) {
                            existing.setDecision(itemDto.getDecision());
                        }
                        if (itemDto.getCostDays() != null) {
                            existing.setCostDays(itemDto.getCostDays());
                        }
                        existing.setDecidedBy(operator);
                        existing.setDecidedAt(LocalDateTime.now());
                        existing.setUpdatedAt(LocalDateTime.now());
                        confirmItemMapper.updateById(existing);
                        continue;
                    }
                }

                // 新增项
                ReqConfirmItem newItem = ReqConfirmItem.builder()
                        .requirementId(reqId)
                        .category(itemDto.getCategory())
                        .itemCode(itemDto.getItemCode())
                        .question(itemDto.getQuestion())
                        .optionsJson(StringUtils.hasText(itemDto.getOptionsJson()) ? itemDto.getOptionsJson() : "[]")
                        .decision(StringUtils.hasText(itemDto.getDecision()) ? itemDto.getDecision() : "UNDECIDED")
                        .costDays(itemDto.getCostDays() != null ? itemDto.getCostDays() : BigDecimal.ZERO)
                        .decidedBy(operator)
                        .decidedAt(LocalDateTime.now())
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                confirmItemMapper.insert(newItem);
            }
        }

        // 重新计算实时代价并返回
        return calculateCost(reqId);
    }

    /**
     * 计算并折算四层清单实时代价
     */
    public CostCalculationVO calculateCost(Long reqId) {
        List<ReqConfirmItem> allItems = confirmItemMapper.selectList(
                new LambdaQueryWrapper<ReqConfirmItem>().eq(ReqConfirmItem::getRequirementId, reqId)
        );

        BigDecimal totalCost = BigDecimal.ZERO;
        int currentCount = 0;
        int nextCount = 0;
        int skipCount = 0;
        int undecidedCount = 0;
        Map<String, BigDecimal> costByCategory = new HashMap<>();

        for (ReqConfirmItem item : allItems) {
            String decision = item.getDecision() != null ? item.getDecision().toUpperCase() : "UNDECIDED";
            BigDecimal cost = item.getCostDays() != null ? item.getCostDays() : BigDecimal.ZERO;
            String category = item.getCategory() != null ? item.getCategory().toUpperCase() : "OTHER";

            switch (decision) {
                case "CURRENT":
                    currentCount++;
                    totalCost = totalCost.add(cost);
                    costByCategory.put(category, costByCategory.getOrDefault(category, BigDecimal.ZERO).add(cost));
                    break;
                case "NEXT":
                    nextCount++;
                    break;
                case "SKIP":
                    skipCount++;
                    break;
                default:
                    undecidedCount++;
                    break;
            }
        }

        return CostCalculationVO.builder()
                .requirementId(reqId)
                .totalCostDays(totalCost)
                .currentCount(currentCount)
                .nextCount(nextCount)
                .skipCount(skipCount)
                .undecidedCount(undecidedCount)
                .costByCategory(costByCategory)
                .build();
    }

    /**
     * 获取需求详情，包含四层清单与最新原型
     */
    public RequirementDetailVO getRequirementDetail(Long reqId) {
        ReqRequirement req = getById(reqId);
        if (req == null) {
            throw new BusinessException(404, "需求不存在 (ID: " + reqId + ")");
        }

        List<ReqConfirmItem> items = confirmItemMapper.selectList(
                new LambdaQueryWrapper<ReqConfirmItem>()
                        .eq(ReqConfirmItem::getRequirementId, reqId)
                        .orderByAsc(ReqConfirmItem::getCategory)
                        .orderByAsc(ReqConfirmItem::getItemCode)
        );

        Map<String, List<ReqConfirmItem>> grouped = items.stream()
                .collect(Collectors.groupingBy(item -> item.getCategory() != null ? item.getCategory().toUpperCase() : "OTHER"));

        ReqPrototypeVersion latestProto = prototypeVersionMapper.selectOne(
                new LambdaQueryWrapper<ReqPrototypeVersion>()
                        .eq(ReqPrototypeVersion::getRequirementId, reqId)
                        .orderByDesc(ReqPrototypeVersion::getId)
                        .last("LIMIT 1")
        );

        CostCalculationVO costVO = calculateCost(reqId);

        int skipOrNext = costVO.getSkipCount() + costVO.getNextCount();
        boolean pass = costVO.getUndecidedCount() == 0
                && skipOrNext >= 3
                && grouped.containsKey("SCOPE")
                && grouped.containsKey("RULE")
                && grouped.containsKey("EXCEPTION")
                && grouped.containsKey("ACCEPTANCE")
                && latestProto != null
                && StringUtils.hasText(latestProto.getFileHash());

        return RequirementDetailVO.builder()
                .requirement(req)
                .confirmItems(items)
                .groupedItems(grouped)
                .latestPrototype(latestProto)
                .currentTotalCostDays(costVO.getTotalCostDays())
                .totalItemCount(items.size())
                .decidedItemCount(costVO.getCurrentCount() + costVO.getNextCount() + costVO.getSkipCount())
                .skipItemCount(skipOrNext)
                .gateCheckPassed(pass)
                .build();
    }

    /**
     * 校验基线冻结看门狗门禁 (蓝皮书§4.1 4项硬门禁核验)
     * 400201: 基线冻结门禁未放行
     */
    public void validateGateCheck(Long reqId) {
        RequirementDetailVO detail = getRequirementDetail(reqId);
        List<String> violations = new ArrayList<>();

        // 门禁 1: 四层清单完整度 (SCOPE, RULE, EXCEPTION, ACCEPTANCE)
        Map<String, List<ReqConfirmItem>> grouped = detail.getGroupedItems();
        if (!grouped.containsKey("SCOPE") || grouped.get("SCOPE").isEmpty()) {
            violations.add("缺少【范围层 (SCOPE)】清单项");
        }
        if (!grouped.containsKey("RULE") || grouped.get("RULE").isEmpty()) {
            violations.add("缺少【规则层 (RULE)】清单项");
        }
        if (!grouped.containsKey("EXCEPTION") || grouped.get("EXCEPTION").isEmpty()) {
            violations.add("缺少【异常层 (EXCEPTION)】清单项");
        }
        if (!grouped.containsKey("ACCEPTANCE") || grouped.get("ACCEPTANCE").isEmpty()) {
            violations.add("缺少【验收层 (ACCEPTANCE)】清单项");
        }

        // 门禁 2: “本期不做/下期做”条目必须 ≥ 3 条
        if (detail.getSkipItemCount() < 3) {
            violations.add("【本期不做/下期做】条目不足 3 条 (当前仅 " + detail.getSkipItemCount() + " 条)");
        }

        // 门禁 3: 无未决状态清单项 (UNDECIDED)
        CostCalculationVO costVO = calculateCost(reqId);
        if (costVO.getUndecidedCount() > 0) {
            violations.add("存在 " + costVO.getUndecidedCount() + " 条未决策项 (UNDECIDED)");
        }

        // 门禁 4: 必须已绑定有效原型且具备 SHA256 哈希
        if (detail.getLatestPrototype() == null || !StringUtils.hasText(detail.getLatestPrototype().getFileHash())) {
            violations.add("必须上传有效原型包并完成 SHA256 目录哈希固化");
        }

        if (!violations.isEmpty()) {
            throw new BusinessException(400201, "基线冻结门禁未放行: " + String.join("; ", violations));
        }
    }

    /**
     * 管理员特权解冻需求 (解除 BASELINED 物理锁死保护)
     * 解冻后状态恢复为 CLARIFYING (澄清中)，允许重新修改四层清单或执行级联物理删除
     */
    @Transactional(rollbackFor = Exception.class)
    public ReqRequirement unfreezeRequirement(Long reqId, String operator) {
        ReqRequirement req = getById(reqId);
        if (req == null) {
            throw new BusinessException(404, "需求不存在 (ID: " + reqId + ")");
        }

        // 1. 将需求状态解冻回 CLARIFYING
        req.setStatus("CLARIFYING");
        req.setUpdatedAt(LocalDateTime.now());
        updateById(req);

        // 2. 将关联已冻结的基线状态解冻/降级为 DRAFT
        List<ReqBaseline> baselines = baselineMapper.selectList(
                new LambdaQueryWrapper<ReqBaseline>().eq(ReqBaseline::getRequirementId, reqId)
        );
        for (ReqBaseline b : baselines) {
            if ("BASELINED".equalsIgnoreCase(b.getStatus())) {
                b.setStatus("DRAFT");
                baselineMapper.updateById(b);
            }
        }

        log.info("管理员 [{}] 成功执行特权解冻: 需求ID={}, 需求Code={}, 已解除物理锁死限制", operator, req.getId(), req.getReqCode());
        return req;
    }
}
