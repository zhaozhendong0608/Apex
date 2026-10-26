package com.jtech.simulator.metadata.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jtech.simulator.metadata.model.AssetNodeVO;
import com.jtech.simulator.metadata.mysql.entity.FusionAssetEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 资产树只读 Mapper (对齐 etl_station 架构规范)
 */
@Mapper
public interface FusionAssetMapper extends BaseMapper<FusionAssetEntity> {

    /**
     * 单次 LEFT JOIN 快速获取子节点及点位量测信息 (对齐 etl_station: selectAssetList)
     */
    @Select("SELECT " +
            "  a.id AS id, " +
            "  a.parent_id AS parentId, " +
            "  a.name AS name, " +
            "  a.code AS code, " +
            "  a.type AS type, " +
            "  a.order_num AS orderNum, " +
            "  a.ancestors AS ancestors, " +
            "  (CASE WHEN a.type = 'point' THEN true ELSE false END) AS leaf, " +
            "  0 AS childCount, " +
            "  p.metric_type AS metricType " +
            "FROM fusion_asset a " +
            "LEFT JOIN fusion_asset_point p ON a.id = p.asset_id " +
            "WHERE a.parent_id = #{parentId} AND a.del_flag = '0' " +
            "ORDER BY a.order_num ASC, a.id ASC")
    List<AssetNodeVO> selectJoinedAssetNodes(@Param("parentId") Long parentId);
}

