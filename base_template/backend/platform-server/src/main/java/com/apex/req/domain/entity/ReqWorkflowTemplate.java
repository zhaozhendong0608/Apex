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
 * 工作流模板实体 t_req_workflow_template
 * 记录组织级与项目级的四层拓扑蓝本、AI Skill 专家矩阵与门禁规约
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_req_workflow_template")
public class ReqWorkflowTemplate implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String templateCode;
    private String name;
    private String description;
    private Integer isSystemDefault;
    private String scopeType;
    private Long projectId;
    private String projectKey;
    private String nodesConfig;
    private String edgesConfig;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

