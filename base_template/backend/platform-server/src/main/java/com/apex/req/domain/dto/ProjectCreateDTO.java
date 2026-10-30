package com.apex.req.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建项目空间 DTO
 */
@Data
public class ProjectCreateDTO {
    @NotBlank(message = "项目代号不能为空(如NET-SIM)")
    private String projectKey;

    @NotBlank(message = "项目名称不能为空")
    private String name;

    @NotBlank(message = "客户单位名称不能为空")
    private String clientName;

    private String description;
}
