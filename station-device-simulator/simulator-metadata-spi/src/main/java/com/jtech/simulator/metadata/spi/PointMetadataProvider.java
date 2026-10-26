package com.jtech.simulator.metadata.spi;

import com.jtech.simulator.metadata.model.PointMetadata;
import java.util.List;

/**
 * 测点元数据拉取 SPI 扩展接口
 * 允许从 MySQL、本地缓存或 Mock 源抽取电站资产测点定义
 */
public interface PointMetadataProvider {

    /**
     * 获取所有需要仿真的测点元数据列表
     *
     * @return 测点列表
     */
    List<PointMetadata> loadAllPoints();

    /**
     * 根据点位编码查询单点元数据
     *
     * @param pointCode 点位编码 (kid)
     * @return 单点元数据
     */
    PointMetadata getPointByCode(String pointCode);

    /**
     * 根据父节点ID按需加载直接子节点列表 (对齐 etl_station 树按需下钻)
     *
     * @param parentId 父节点ID (null 或 0 表示根节点)
     * @return 节点列表
     */
    default List<com.jtech.simulator.metadata.model.AssetNodeVO> loadTreeNodes(Long parentId) {
        return java.util.Collections.emptyList();
    }

    /**
     * 关键字模糊搜索测点/节点及祖先链路
     *
     * @param keyword 搜索关键字 (名称或Code)
     * @return 命中的节点列表
     */
    default List<com.jtech.simulator.metadata.model.AssetNodeVO> searchTreeNodes(String keyword) {
        return java.util.Collections.emptyList();
    }
}

