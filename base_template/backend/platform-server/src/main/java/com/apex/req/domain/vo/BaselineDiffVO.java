package com.apex.req.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 基线差异三合一智能对比 VO (绑定 DLD §3.2)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaselineDiffVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sourceVersion;
    private String targetVersion;
    private BigDecimal netCostGrowth; // 净代价增量
    private List<ItemDiffVO> itemDiffs;
    private PrototypeDiffVO prototypeDiff;
}
