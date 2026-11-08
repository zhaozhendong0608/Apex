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
 * 工程流水线-工位流转与人机协同审计履历表 t_req_workflow_record
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_req_workflow_record")
public class ReqWorkflowRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String reqCode;
    private String nodeId;
    private String nodeName;
    private String trackMode;
    private String skillName;
    private String operator;
    private String actionType; // ADVANCE, OVERRIDE, REJECT, CONSULT
    private String overrideReason;
    private String artifactsJson;
    private LocalDateTime createdAt;
}

