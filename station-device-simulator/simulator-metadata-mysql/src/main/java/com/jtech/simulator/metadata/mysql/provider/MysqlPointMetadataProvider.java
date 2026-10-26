package com.jtech.simulator.metadata.mysql.provider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jtech.simulator.metadata.model.PointMetadata;
import com.jtech.simulator.metadata.mysql.entity.FusionAssetEntity;
import com.jtech.simulator.metadata.mysql.entity.FusionAssetPointEntity;
import com.jtech.simulator.metadata.mysql.mapper.FusionAssetMapper;
import com.jtech.simulator.metadata.mysql.mapper.FusionAssetPointMapper;
import com.jtech.simulator.metadata.spi.PointMetadataProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于 MySQL 资产树的测点元数据拉取实现 (只读 SSOT)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MysqlPointMetadataProvider implements PointMetadataProvider {

    private final FusionAssetMapper assetMapper;
    private final FusionAssetPointMapper assetPointMapper;

    @Override
    public List<PointMetadata> loadAllPoints() {
        log.info("📡 开始只读同步 MySQL 资产树 (fusion_asset)...");

        // 1. 全量只读查询未删除状态的资产树节点 (del_flag = '0')
        List<FusionAssetEntity> allAssets = assetMapper.selectList(
                new LambdaQueryWrapper<FusionAssetEntity>().eq(FusionAssetEntity::getDelFlag, "0")
        );

        if (allAssets == null || allAssets.isEmpty()) {
            log.warn("⚠️ MySQL fusion_asset 表中未查询到有效资产记录！");
            return Collections.emptyList();
        }

        // 建立 ID 索引字典方便向上回溯父级
        Map<Long, FusionAssetEntity> assetMap = allAssets.stream()
                .collect(Collectors.toMap(FusionAssetEntity::getId, a -> a, (k1, k2) -> k1));

        // 2. 查询所有测点属性表
        List<FusionAssetPointEntity> pointProps = assetPointMapper.selectList(null);
        Map<Long, FusionAssetPointEntity> propMap = (pointProps != null) ? pointProps.stream()
                .collect(Collectors.toMap(FusionAssetPointEntity::getAssetId, p -> p, (k1, k2) -> k1))
                : Collections.emptyMap();

        List<PointMetadata> result = new ArrayList<>();

        // 3. 过滤出所有 type='point' 的测点节点，并向上回溯设备与部件
        for (FusionAssetEntity asset : allAssets) {
            if (!"point".equalsIgnoreCase(asset.getType())) {
                continue;
            }

            FusionAssetPointEntity prop = propMap.get(asset.getId());

            // 向上回溯 4 层树: point -> component -> device -> unit
            String componentName = "通用部件";
            String deviceName = "主设备";
            String unitName = "1号机组";

            FusionAssetEntity parent1 = assetMap.get(asset.getParentId()); // 部件 component
            if (parent1 != null) {
                componentName = parent1.getName();
                FusionAssetEntity parent2 = assetMap.get(parent1.getParentId()); // 设备 device
                if (parent2 != null) {
                    deviceName = parent2.getName();
                    FusionAssetEntity parent3 = assetMap.get(parent2.getParentId()); // 机组 unit
                    if (parent3 != null) {
                        unitName = parent3.getName();
                    }
                }
            }

            // 自动推断策略类型 (基于名称关键字和 semanticTag)
            String strategyType = resolveStrategyType(asset.getName(), prop);
            String metricType = (prop != null && prop.getMetricType() != null)
                    ? prop.getMetricType()
                    : ("SWITCH".equals(strategyType) ? "discrete" : "analog");

            Double baseVal = getDefaultBaseValue(strategyType);
            Double minVal = getDefaultMinValue(strategyType);
            Double maxVal = getDefaultMaxValue(strategyType);
            String unit = getDefaultUnit(strategyType);

            PointMetadata meta = PointMetadata.builder()
                    .id(asset.getId())
                    .code(asset.getCode())
                    .name(asset.getName())
                    .stationName("天生桥一级水电站")
                    .unitName(unitName)
                    .deviceName(deviceName)
                    .componentName(componentName)
                    .metricType(metricType)
                    .strategyType(strategyType)
                    .baseValue(baseVal)
                    .minValue(minVal)
                    .maxValue(maxVal)
                    .unit(unit)
                    .build();

            result.add(meta);
        }

        log.info("✅ 成功加载解析测点元数据 {} 个！", result.size());
        return result;
    }

    @Override
    public PointMetadata getPointByCode(String pointCode) {
        if (pointCode == null) return null;
        return loadAllPoints().stream()
                .filter(p -> pointCode.equalsIgnoreCase(p.getCode()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<com.jtech.simulator.metadata.model.AssetNodeVO> loadTreeNodes(Long parentId) {
        Long targetParentId = (parentId == null) ? 0L : parentId;
        log.info("🌲 按需加载资产层级子节点 (单次索引驱动): parentId = {}", targetParentId);

        // 1. 对齐 etl_station 做法: 单次高效查询 idx_parent_id 索引 + LEFT JOIN 附表
        List<com.jtech.simulator.metadata.model.AssetNodeVO> nodes = assetMapper.selectJoinedAssetNodes(targetParentId);
        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 仅对非 point 节点 (机组/设备/部件) 进行轻量级统计，避免 3000+ 点位时进行巨量子节点扫描
        List<Long> nonPointIds = nodes.stream()
                .filter(n -> !"point".equalsIgnoreCase(n.getType()))
                .map(com.jtech.simulator.metadata.model.AssetNodeVO::getId)
                .collect(Collectors.toList());

        Map<Long, Long> childCountMap = Collections.emptyMap();
        if (!nonPointIds.isEmpty()) {
            List<FusionAssetEntity> grandchildren = assetMapper.selectList(
                    new LambdaQueryWrapper<FusionAssetEntity>()
                            .in(FusionAssetEntity::getParentId, nonPointIds)
                            .eq(FusionAssetEntity::getDelFlag, "0")
                            .select(FusionAssetEntity::getId, FusionAssetEntity::getParentId)
            );
            if (grandchildren != null) {
                childCountMap = grandchildren.stream()
                        .collect(Collectors.groupingBy(FusionAssetEntity::getParentId, Collectors.counting()));
            }
        }

        // 3. 内存直接补齐策略与数值默认值
        for (com.jtech.simulator.metadata.model.AssetNodeVO vo : nodes) {
            boolean isPoint = "point".equalsIgnoreCase(vo.getType()) || Boolean.TRUE.equals(vo.getLeaf());
            vo.setLeaf(isPoint);

            if (isPoint) {
                String strategyType = resolveStrategyType(vo.getName(), null);
                vo.setStrategyType(strategyType);
                if (vo.getMetricType() == null) {
                    vo.setMetricType("SWITCH".equals(strategyType) ? "discrete" : "analog");
                }
                vo.setBaseValue(getDefaultBaseValue(strategyType));
                vo.setMinValue(getDefaultMinValue(strategyType));
                vo.setMaxValue(getDefaultMaxValue(strategyType));
                vo.setUnit(getDefaultUnit(strategyType));
                vo.setChildCount(0);
            } else {
                vo.setChildCount(childCountMap.getOrDefault(vo.getId(), 0L).intValue());
            }
        }

        return nodes;
    }

    @Override
    public List<com.jtech.simulator.metadata.model.AssetNodeVO> searchTreeNodes(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String kw = keyword.trim();
        log.info("🔍 搜索资产树节点: keyword = {}", kw);

        // 1. 模糊匹配名称或编码
        List<FusionAssetEntity> matchedAssets = assetMapper.selectList(
                new LambdaQueryWrapper<FusionAssetEntity>()
                        .eq(FusionAssetEntity::getDelFlag, "0")
                        .and(w -> w.like(FusionAssetEntity::getName, kw).or().like(FusionAssetEntity::getCode, kw))
                        .last("LIMIT 100")
        );

        if (matchedAssets == null || matchedAssets.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 收集需要回溯的所有祖先 ID
        Set<Long> allNeededIds = new HashSet<>();
        matchedAssets.forEach(a -> {
            allNeededIds.add(a.getId());
            if (a.getAncestors() != null) {
                for (String s : a.getAncestors().split(",")) {
                    try {
                        long aid = Long.parseLong(s.trim());
                        if (aid > 0) allNeededIds.add(aid);
                    } catch (NumberFormatException ignored) {}
                }
            }
        });

        // 3. 批量查询补齐祖先节点
        List<FusionAssetEntity> allAssets = assetMapper.selectList(
                new LambdaQueryWrapper<FusionAssetEntity>()
                        .in(FusionAssetEntity::getId, allNeededIds)
                        .eq(FusionAssetEntity::getDelFlag, "0")
        );

        // 批量查询附表
        List<Long> pointIds = allAssets.stream()
                .filter(a -> "point".equalsIgnoreCase(a.getType()))
                .map(FusionAssetEntity::getId)
                .collect(Collectors.toList());

        Map<Long, FusionAssetPointEntity> pointPropMap = Collections.emptyMap();
        if (!pointIds.isEmpty()) {
            List<FusionAssetPointEntity> props = assetPointMapper.selectList(
                    new LambdaQueryWrapper<FusionAssetPointEntity>().in(FusionAssetPointEntity::getAssetId, pointIds)
            );
            if (props != null) {
                pointPropMap = props.stream().collect(Collectors.toMap(FusionAssetPointEntity::getAssetId, p -> p, (k1, k2) -> k1));
            }
        }

        List<com.jtech.simulator.metadata.model.AssetNodeVO> result = new ArrayList<>(allAssets.size());
        for (FusionAssetEntity asset : allAssets) {
            boolean isPoint = "point".equalsIgnoreCase(asset.getType());
            FusionAssetPointEntity prop = pointPropMap.get(asset.getId());

            String strategyType = isPoint ? resolveStrategyType(asset.getName(), prop) : null;
            String metricType = isPoint ? ((prop != null && prop.getMetricType() != null) ? prop.getMetricType() : ("SWITCH".equals(strategyType) ? "discrete" : "analog")) : null;
            Double baseVal = isPoint ? getDefaultBaseValue(strategyType) : null;
            Double minVal = isPoint ? getDefaultMinValue(strategyType) : null;
            Double maxVal = isPoint ? getDefaultMaxValue(strategyType) : null;
            String unit = isPoint ? getDefaultUnit(strategyType) : null;

            com.jtech.simulator.metadata.model.AssetNodeVO vo = com.jtech.simulator.metadata.model.AssetNodeVO.builder()
                    .id(asset.getId())
                    .parentId(asset.getParentId())
                    .name(asset.getName())
                    .code(asset.getCode())
                    .type(asset.getType())
                    .orderNum(asset.getOrderNum())
                    .ancestors(asset.getAncestors())
                    .leaf(isPoint)
                    .childCount(0)
                    .metricType(metricType)
                    .strategyType(strategyType)
                    .baseValue(baseVal)
                    .minValue(minVal)
                    .maxValue(maxVal)
                    .unit(unit)
                    .build();

            result.add(vo);
        }

        return result;
    }

    /**
     * 依据点位名称和语义标签智能派发物理策略 (Signal Strategy Router)
     */
    private String resolveStrategyType(String pointName, FusionAssetPointEntity prop) {
        if (prop != null && prop.getSemanticTag() != null) {
            return prop.getSemanticTag().toUpperCase();
        }
        if (pointName == null) return "TEMPERATURE";

        String name = pointName.toLowerCase();
        if (name.contains("温") || name.contains("temp") || name.contains("热")) {
            return "TEMPERATURE";
        } else if (name.contains("振") || name.contains("摆") || name.contains("vib") || name.contains("偏心")) {
            return "VIBRATION";
        } else if (name.contains("功率") || name.contains("负荷") || name.contains("power") || name.contains("电流")) {
            return "POWER";
        } else if (name.contains("断路器") || name.contains("开关") || name.contains("刀闸") || name.contains("state")) {
            return "SWITCH";
        } else if (name.contains("水头") || name.contains("导叶") || name.contains("开度") || name.contains("流量")) {
            return "HYDRAULIC";
        }
        return "TEMPERATURE";
    }

    private Double getDefaultBaseValue(String strategyType) {
        switch (strategyType) {
            case "TEMPERATURE": return 58.0;
            case "VIBRATION": return 0.05;
            case "POWER": return 300.0;
            case "SWITCH": return 1.0;
            case "HYDRAULIC": return 78.5;
            default: return 50.0;
        }
    }

    private Double getDefaultMinValue(String strategyType) {
        switch (strategyType) {
            case "TEMPERATURE": return 15.0;
            case "VIBRATION": return 0.0;
            case "POWER": return 0.0;
            case "SWITCH": return 0.0;
            case "HYDRAULIC": return 0.0;
            default: return 0.0;
        }
    }

    private Double getDefaultMaxValue(String strategyType) {
        switch (strategyType) {
            case "TEMPERATURE": return 120.0;
            case "VIBRATION": return 0.5;
            case "POWER": return 360.0;
            case "SWITCH": return 1.0;
            case "HYDRAULIC": return 100.0;
            default: return 100.0;
        }
    }

    private String getDefaultUnit(String strategyType) {
        switch (strategyType) {
            case "TEMPERATURE": return "℃";
            case "VIBRATION": return "mm";
            case "POWER": return "MW";
            case "SWITCH": return "状态";
            case "HYDRAULIC": return "%";
            default: return "";
        }
    }
}
