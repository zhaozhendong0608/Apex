package com.apex.req.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户免登签署完成电子防伪回执 VO (绑定 E03)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignReceiptVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String receiptCode; // 如 7F3A-91C2-88B1
    private String baselineVersion; // 如 BL-v1.1
    private Boolean isFullyFrozen; // 三方是否全部签署并触发物理冻结
    private String manifestHash; // 固化指纹
    private LocalDateTime signedAt;
}
