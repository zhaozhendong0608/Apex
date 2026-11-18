package com.apex.req.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 发布版本追踪视图对象 (P16 列表与发布清单详情)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseVO {
    private Long id;
    private Long projectId;
    private String projectKey;
    private Long baselineId;
    private String baselineVersion;
    private String manifestHash;
    private BigDecimal totalCostDays;
    private Long requirementId;
    private String reqCode;
    private String reqTitle;
    private String releaseVersion;
    private String gitTag;
    private LocalDate plannedDate;
    private LocalDateTime releasedAt;
    private String status;
    private String statusText;
    private String releaseNotes;
    private String createdBy;
    private LocalDateTime createdAt;
}
