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
 * 需求实体 t_req_requirement (含客户原话不可篡改留痕)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_req_requirement")
public class ReqRequirement implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String reqCode;
    private String title;
    private String rawQuote;
    private String triggerEvent;
    private String status;
    private String priority;
    private String ownerName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
