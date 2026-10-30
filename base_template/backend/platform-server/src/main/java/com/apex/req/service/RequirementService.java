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
import com.apex.req.domain.vo.RequirementListItemVO;
import com.apex.req.mapper.ReqBaselineMapper;
import com.apex.req.mapper.ReqConfirmItemMapper;
import com.apex.req.mapper.ReqProjectMapper;
import com.apex.req.mapper.ReqPrototypeVersionMapper;
import com.apex.req.mapper.ReqRequirementMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
        return req;
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
