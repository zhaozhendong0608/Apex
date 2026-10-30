package com.apex.req.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 需求基线表 t_req_baseline (不可变存证)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_req_baseline")
public class ReqBaseline implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long requirementId;
    private String version; // BL-v1.0, BL-v1.1...
    private String gitTag; // bl/NET-SIM/REQ-001/v1.0
    private Long prototypeVersionId;
    private String manifestPath;
    private String manifestHash;
    private String archiveZipPath;
    private String status; // DRAFT, PENDING, BASELINED, SUPERSEDED
    private BigDecimal totalCostDays;
    private LocalDateTime frozenAt;
    private String supersededBy;
    private LocalDateTime createdAt;
}
