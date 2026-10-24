package com.apex.network.mapper;

import com.apex.network.entity.NetTopology;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 网络拓扑持久层 Mapper 接口
 */
@Mapper
public interface NetTopologyMapper extends BaseMapper<NetTopology> {
}
