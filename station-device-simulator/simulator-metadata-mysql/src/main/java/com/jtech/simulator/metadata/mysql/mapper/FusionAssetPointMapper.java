package com.jtech.simulator.metadata.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jtech.simulator.metadata.mysql.entity.FusionAssetPointEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 测点元数据只读 Mapper
 */
@Mapper
public interface FusionAssetPointMapper extends BaseMapper<FusionAssetPointEntity> {
}
