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
 * 四层确认清单与实时代价表 t_req_confirm_item
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_req_confirm_item")
public class ReqConfirmItem implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requirementId;
    private String category; // SCOPE, RULE, EXCEPTION, ACCEPTANCE, SKIP
    private String itemCode; // R-01, B-01, N-01...
    private String question;
    private String optionsJson;
    private String decision; // CURRENT, NEXT, SKIP, UNDECIDED
    private BigDecimal costDays;
    private String decidedBy;
    private LocalDateTime decidedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
