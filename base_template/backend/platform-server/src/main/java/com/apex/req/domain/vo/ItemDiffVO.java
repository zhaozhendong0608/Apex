package com.apex.req.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 清单项差异明细 VO (绑定 DLD §3.2)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemDiffVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String type; // ADDED, REMOVED, MODIFIED, UNCHANGED
    private String itemCode;
    private String category;
    private String question;
    private String oldDecision;
    private String newDecision;
    private BigDecimal costDelta; // 该条目引起的代价增量
}
