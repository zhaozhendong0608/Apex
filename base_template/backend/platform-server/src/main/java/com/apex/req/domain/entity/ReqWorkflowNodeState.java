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
 * 需求管理-工作流节点工位真实执行状态实体 t_req_workflow_node_state
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_req_workflow_node_state")
public class ReqWorkflowNodeState implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 需求编号 (如 REQ-001) */
    private String reqCode;

    /** 所属轨道 (STANDARD/FASTTRACK) */
    private String trackMode;

    /** 工位算子ID (如 SOP-00, SOP-01, SOP-B2) */
    private String nodeId;

    /** 工位算子名称快照 */
    private String nodeName;

    /** 工位状态 (PENDING:未开始, IN_PROGRESS:进行中, COMPLETED:已完成, SKIPPED:跳过) */
    private String status;

    /** 最后操作/责任人 */
    private String operator;

    /** 完工时间 */
    private LocalDateTime completedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}

