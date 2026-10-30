package com.apex.req.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户免登三方法定签名凭证表 t_req_approval
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_req_approval")
public class ReqApproval implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long baselineId;
    private String roleType; // BIZ_OWNER, ACCEPTOR, USER_REP
    private String signerName;
    private String status; // PENDING, SIGNED, REJECTED
    private String token;
    private String accessCode;
    private LocalDateTime tokenExpiresAt;
    private LocalDateTime signedAt;
    private String signerIp;
    private String userAgent;
    private String restatementText;
    private String receiptCode;
    private String rejectReason;
    private LocalDateTime createdAt;
}
