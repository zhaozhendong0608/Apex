package com.jtech.simulator.metadata.mysql.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对应 MySQL etl_badger.fusion_asset 表实体 (纯只读)
 * 资产树模型: type='unit'(机组), 'device'(设备), 'component'(部件), 'point'(测点)
 */
@Data
@TableName("fusion_asset")
public class FusionAssetEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 资产主键ID */
    @TableId
    private Long id;

    /** 父节点ID (0 为根) */
    private Long parentId;

    /** 节点名称 (如: "1号发电机", "推力瓦温1点") */
    private String name;

    /** 节点唯一编码 (如: "U1_THRUST_PAD_TEMP_01") */
    private String code;

    /** 节点类型: unit, device, component, point */
    private String type;

    /** 祖级列表 */
    private String ancestors;

    /** 排序号 */
    private Integer orderNum;

    /** 租户ID */
    private String tenantId;

    /** 创建部门 */
    private Long createDept;

    /** 创建者 */
    private Long createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新者 */
    private Long updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 删除标志 (0代表存在 2代表删除) */
    private String delFlag;

    /** 支持的量测类型 (analog, digital_input, digital_output) */
    private String measurementTypes;

    /** 扩展属性JSON (额定容量等) */
    private String properties;
}
