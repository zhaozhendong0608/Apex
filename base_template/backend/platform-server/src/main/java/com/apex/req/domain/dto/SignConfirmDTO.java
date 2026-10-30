package com.apex.req.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 客户免登签署确认请求 DTO (绑定 DLD §3.2)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignConfirmDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "4位访问防窥验证码不能为空")
    private String accessCode;

    @NotBlank(message = "签署人姓名不能为空")
    private String signerName;

    @NotBlank(message = "一句话复述理解存证不能为空")
    private String restatementText;

    private Boolean agreeDisclaimer;
}
