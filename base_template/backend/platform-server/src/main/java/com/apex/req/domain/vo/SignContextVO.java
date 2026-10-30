package com.apex.req.domain.vo;

import com.apex.req.domain.entity.ReqConfirmItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户免登签署上下文视图 VO (绑定 E02)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignContextVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String roleType; // BIZ_OWNER, ACCEPTOR, USER_REP
    private String roleTitle; // 业务拍板人, 验收人, 使用者代表
    private String signerName;
    private String baselineVersion;
    private String projectKey;
    private String projectName;
    private String clientName;
    private String reqCode;
    private String reqTitle;
    private String rawQuote; // 客户原话留痕
    private String previewUrl; // 原型 iframe 预览地址
    private String prototypeHash;
    private BigDecimal totalCostDays;
    private List<ReqConfirmItem> confirmItems;
    private String status; // PENDING, SIGNED, REJECTED
    private String receiptCode;
    private LocalDateTime signedAt;
    private LocalDateTime tokenExpiresAt;
}
