package com.apex.req.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 需求管理-发布版本追踪表 t_req_release
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_req_release")
public class ReqRelease implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long baselineId;
    private String releaseVersion;
    private String gitTag;
    private LocalDate plannedDate;
    private LocalDateTime releasedAt;
    private String status; // PLANNED, RELEASED, ROLLBACK
    private String releaseNotes;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
