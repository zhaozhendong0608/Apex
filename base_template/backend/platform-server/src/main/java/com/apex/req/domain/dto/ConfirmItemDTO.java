package com.apex.req.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 四层确认清单项 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmItemDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank(message = "清单分类不能为空 (SCOPE, RULE, EXCEPTION, ACCEPTANCE, SKIP)")
    private String category;

    @NotBlank(message = "条目代号不能为空 (如 R-01, B-01)")
    private String itemCode;

    @NotBlank(message = "业务问法/约定项标题不能为空")
    private String question;

    private String optionsJson;

    private String decision; // CURRENT, NEXT, SKIP, UNDECIDED

    private BigDecimal costDays; // 代价人天

    private String decidedBy;
}
