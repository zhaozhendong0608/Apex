package com.apex.req.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 客户免登异议分歧驳回请求 DTO (绑定 DLD §2.2)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignRejectDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "4位访问防窥验证码不能为空")
    private String accessCode;

    private String signerName;

    @NotBlank(message = "异议分歧驳回说明不能为空")
    private String rejectReason;
}
