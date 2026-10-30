package com.apex.req.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 需求列表项视图 VO (对齐工作台全景表与 DLD 规范)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequirementListItemVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long projectId;
    private String reqCode;
    private String title;
    private String rawQuote;
    private String triggerEvent;
    private String status;
    private String priority;
    private String ownerName;
    private String baselineCode;
    private BigDecimal totalCostDays;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
