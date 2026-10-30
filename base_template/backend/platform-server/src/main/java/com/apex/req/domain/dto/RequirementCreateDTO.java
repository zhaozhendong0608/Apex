package com.apex.req.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 需求快速录入 DTO (含客户原话留痕)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequirementCreateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull(message = "项目空间 ID 不能为空")
    private Long projectId;

    @NotBlank(message = "需求编号不能为空 (如 REQ-001)")
    private String reqCode;

    @NotBlank(message = "需求标题不能为空")
    private String title;

    @NotBlank(message = "客户原话长文本不能为空 (不可篡改留痕)")
    private String rawQuote;

    private String triggerEvent;

    private String priority; // P1, P2, P3

    @NotBlank(message = "责任人不能为空")
    private String ownerName;

    private List<ConfirmItemDTO> confirmItems;
}
