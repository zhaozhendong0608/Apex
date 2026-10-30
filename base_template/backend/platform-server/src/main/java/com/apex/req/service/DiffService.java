package com.apex.req.service;

import com.apex.common.exception.BusinessException;
import com.apex.req.domain.entity.ReqBaseline;
import com.apex.req.domain.entity.ReqConfirmItem;
import com.apex.req.domain.entity.ReqPrototypeVersion;
import com.apex.req.domain.vo.BaselineDiffVO;
import com.apex.req.domain.vo.ItemDiffVO;
import com.apex.req.domain.vo.PrototypeDiffVO;
import com.apex.req.mapper.ReqBaselineMapper;
import com.apex.req.mapper.ReqConfirmItemMapper;
import com.apex.req.mapper.ReqPrototypeVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基线增量差异计算引擎 (清单/原型/代价三合一增量比对) (绑定 DLD §3.2)
 */
@Slf4j
@Service
public class DiffService {

    private final ReqBaselineMapper baselineMapper;
    private final ReqPrototypeVersionMapper prototypeVersionMapper;
    private final ReqConfirmItemMapper confirmItemMapper;

    public DiffService(ReqBaselineMapper baselineMapper,
                       ReqPrototypeVersionMapper prototypeVersionMapper,
                       ReqConfirmItemMapper confirmItemMapper) {
        this.baselineMapper = baselineMapper;
        this.prototypeVersionMapper = prototypeVersionMapper;
        this.confirmItemMapper = confirmItemMapper;
    }

    /**
     * 计算两个基线之间的三合一差异 (源版本 vs 目标版本)
     */
    public BaselineDiffVO calculateDiff(Long sourceBaselineId, Long targetBaselineId) {
        if (sourceBaselineId == null) {
            throw new BusinessException(400, "源基线 ID 不能为空");
        }

        ReqBaseline source = baselineMapper.selectById(sourceBaselineId);
        if (source == null) {
            throw new BusinessException(404, "源基线不存在 (ID: " + sourceBaselineId + ")");
        }

        ReqBaseline target;
        if (targetBaselineId != null) {
            target = baselineMapper.selectById(targetBaselineId);
            if (target == null) {
                throw new BusinessException(404, "目标基线不存在 (ID: " + targetBaselineId + ")");
            }
        } else {
            // 未传 target_id 时默认选取后续最新一个基线
            target = baselineMapper.selectOne(
                    new LambdaQueryWrapper<ReqBaseline>()
                            .eq(ReqBaseline::getRequirementId, source.getRequirementId())
                            .gt(ReqBaseline::getId, source.getId())
                            .orderByAsc(ReqBaseline::getId)
                            .last("LIMIT 1")
            );
            if (target == null) {
                // 如果没有后续版本，则以当前基线自比
                target = source;
            }
        }

        // 1. 代价净增量 (net_cost_growth)
        BigDecimal sourceCost = source.getTotalCostDays() != null ? source.getTotalCostDays() : BigDecimal.ZERO;
        BigDecimal targetCost = target.getTotalCostDays() != null ? target.getTotalCostDays() : BigDecimal.ZERO;
        BigDecimal netCostGrowth = targetCost.subtract(sourceCost);

        // 2. 原型版本与指纹差异
        ReqPrototypeVersion sourceProto = source.getPrototypeVersionId() != null
                ? prototypeVersionMapper.selectById(source.getPrototypeVersionId()) : null;
        ReqPrototypeVersion targetProto = target.getPrototypeVersionId() != null
                ? prototypeVersionMapper.selectById(target.getPrototypeVersionId()) : null;

        String oldVer = sourceProto != null ? sourceProto.getVersionNo() : "none";
        String oldHash = sourceProto != null ? sourceProto.getFileHash() : "";
        String newVer = targetProto != null ? targetProto.getVersionNo() : "none";
        String newHash = targetProto != null ? targetProto.getFileHash() : "";
        boolean protoChanged = !oldHash.equals(newHash);

        PrototypeDiffVO prototypeDiff = PrototypeDiffVO.builder()
                .oldVersion(oldVer)
                .oldHash(oldHash)
                .newVersion(newVer)
                .newHash(newHash)
                .hasChanged(protoChanged)
                .build();

        // 3. 清单项对比
        List<ReqConfirmItem> items = confirmItemMapper.selectList(
                new LambdaQueryWrapper<ReqConfirmItem>()
                        .eq(ReqConfirmItem::getRequirementId, target.getRequirementId())
                        .orderByAsc(ReqConfirmItem::getItemCode)
        );

        List<ItemDiffVO> itemDiffs = new ArrayList<>();
        for (ReqConfirmItem item : items) {
            String decision = item.getDecision() != null ? item.getDecision() : "UNDECIDED";
            BigDecimal cost = item.getCostDays() != null ? item.getCostDays() : BigDecimal.ZERO;

            if ("CURRENT".equalsIgnoreCase(decision)) {
                itemDiffs.add(ItemDiffVO.builder()
                        .type("ADDED")
                        .itemCode(item.getItemCode())
                        .category(item.getCategory())
                        .question(item.getQuestion())
                        .oldDecision("NONE")
                        .newDecision(decision)
                        .costDelta(cost)
                        .build());
            } else if ("NEXT".equalsIgnoreCase(decision) || "SKIP".equalsIgnoreCase(decision)) {
                itemDiffs.add(ItemDiffVO.builder()
                        .type("MODIFIED")
                        .itemCode(item.getItemCode())
                        .category(item.getCategory())
                        .question(item.getQuestion())
                        .oldDecision("UNDECIDED")
                        .newDecision(decision)
                        .costDelta(BigDecimal.ZERO)
                        .build());
            }
        }

        return BaselineDiffVO.builder()
                .sourceVersion(source.getVersion())
                .targetVersion(target.getVersion())
                .netCostGrowth(netCostGrowth)
                .itemDiffs(itemDiffs)
                .prototypeDiff(prototypeDiff)
                .build();
    }
}
