package com.apex.req.domain.vo;

import com.apex.req.domain.entity.ReqConfirmItem;
import com.apex.req.domain.entity.ReqPrototypeVersion;
import com.apex.req.domain.entity.ReqRequirement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 需求详情完整视图 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequirementDetailVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private ReqRequirement requirement;
    private List<ReqConfirmItem> confirmItems;
    private Map<String, List<ReqConfirmItem>> groupedItems;
    private ReqPrototypeVersion latestPrototype;
    private BigDecimal currentTotalCostDays;
    private Integer totalItemCount;
    private Integer decidedItemCount;
    private Integer skipItemCount;
    private Boolean gateCheckPassed;
}
