package com.apex.req.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设计文档与归档产物表 t_req_artifact
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_req_artifact")
public class ReqArtifact implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long requirementId;
    private Long baselineId;
    private String type; // TECH_DESIGN, ARCH_DESIGN, DETAIL_DESIGN, PROTO_ZIP
    private String docName;
    private String versionTag;
    private String storagePath;
    private String sha256Hash;
    private Long fileSize;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
}
