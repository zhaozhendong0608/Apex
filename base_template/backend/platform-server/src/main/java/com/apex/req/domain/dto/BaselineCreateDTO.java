package com.apex.req.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 申请创建新需求基线 DTO (绑定 DLD §3.1)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaselineCreateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull(message = "项目 ID 不能为空")
    private Long projectId;

    @NotNull(message = "需求 ID 不能为空")
    private Long requirementId;

    private Long prototypeVersionId; // 可选，默认使用该需求最新原型版本

    private String bizOwnerName; // 业务拍板人实名

    private String acceptorName; // 项目验收人实名

    private String userRepName; // 使用者代表实名
}
