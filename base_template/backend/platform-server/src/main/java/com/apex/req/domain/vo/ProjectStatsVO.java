package com.apex.req.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 项目空间工作台统计 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectStatsVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long projectId;
    private String projectKey;
    private String projectName;
    private String clientName;
    private String description;
    private Integer totalRequirements;
    private Integer baselinedCount;
    private Integer pendingCount;
    private Integer clarifyingCount;
    private Integer prototypeCount;
    private BigDecimal totalCostDays;
    private String latestBaselineCode;
}
