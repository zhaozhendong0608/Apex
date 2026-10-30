package com.apex.req.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 实时代价折算与统计 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostCalculationVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long requirementId;
    private BigDecimal totalCostDays; // 本期确认项总人天
    private Integer currentCount; // 本期做条目数
    private Integer nextCount; // 下期做条目数
    private Integer skipCount; // 本期不做条目数
    private Integer undecidedCount; // 未决条目数
    private Map<String, BigDecimal> costByCategory; // 按四层分类折算代价
}
