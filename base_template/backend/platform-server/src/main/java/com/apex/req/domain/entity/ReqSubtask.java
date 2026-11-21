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
 * 需求管理-原子子任务拆解与进度协同实体 t_req_subtask
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_req_subtask")
public class ReqSubtask implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联主需求编号 (如 REQ-001) */
    private String reqCode;

    /** 子任务唯一编号 (如 Task-ORDER-up_dong-01) */
    private String taskCode;

    /** 所属业务切片模块 (如 ORDER, SMS) */
    private String moduleName;

    /** 子任务标题 */
    private String title;

    /** 白话验收目标 */
    private String goal;

    /** 涉及文件列表 */
    private String files;

    /** 责任研发人 (Git用户名) */
    private String ownerName;

    /** 状态: TODO待办, IN_PROGRESS进行中, DONE已完成, BLOCKED已阻塞 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 完工时间 */
    private LocalDateTime completedAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
