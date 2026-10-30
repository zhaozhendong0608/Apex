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
 * 原型版本托管与快照指纹表 t_req_prototype_version
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_req_prototype_version")
public class ReqPrototypeVersion implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requirementId;
    private String versionNo; // v1, v2, v3...
    private String dirPath;
    private String fileHash; // 整个原型目录的SHA256哈希指纹
    private String previewUrl;
    private String changeSummary;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
}
