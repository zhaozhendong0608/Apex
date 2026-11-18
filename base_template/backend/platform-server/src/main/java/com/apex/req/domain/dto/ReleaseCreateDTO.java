package com.apex.req.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 新建发布计划 DTO (M12)
 */
@Data
public class ReleaseCreateDTO {
    @NotNull(message = "所属项目 ID 不能为空")
    private Long projectId;

    @NotNull(message = "必须绑定一个已冻结基线")
    private Long baselineId;

    @NotBlank(message = "发布版本号不能为空，例如 R-1.0.0")
    private String releaseVersion;

    private LocalDate plannedDate;

    private String releaseNotes;
}
