package com.jtech.simulator.metadata.mysql.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 对应 MySQL etl_badger.fusion_asset_point 表实体 (纯只读)
 * 测点元数据属性: 物理类型(模拟量/开关量), 语义标签
 */
@Data
@TableName("fusion_asset_point")
public class FusionAssetPointEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 资产ID (既是主键，也是外键关联 fusion_asset.id) */
    @TableId
    private Long assetId;

    /** 指标类型: analog=模拟量, digital_input=开关量, digital_output=开出量 */
    private String metricType;

    /** 关联数据源ID */
    private Long datasourceId;

    /** 数据提取SQL */
    private String extractionSql;

    /** 租户ID */
    private String tenantId;

    /** 创建部门 */
    private Long createDept;

    /** 创建者 */
    private Long createBy;

    /** 创建时间 */
    private java.time.LocalDateTime createTime;

    /** 更新者 */
    private Long updateBy;

    /** 更新时间 */
    private java.time.LocalDateTime updateTime;

    /** 删除标志 (0代表存在 2代表删除) */
    private String delFlag;

    /** 标准语义标签 (如: TEMPERATURE, VIBRATION, POWER, SWITCH) */
    private String semanticTag;

    /** 关联系统分类ID */
    private Long systemId;
}
