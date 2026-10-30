package com.apex.req.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 批量更新四层清单与决策 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchConfirmItemsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull(message = "确认清单项列表不能为null")
    @Valid
    @Builder.Default
    private List<ConfirmItemDTO> items = new java.util.ArrayList<>();
}
