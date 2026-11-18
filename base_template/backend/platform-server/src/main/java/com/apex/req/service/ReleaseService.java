package com.apex.req.service;

import com.apex.common.exception.BusinessException;
import com.apex.req.domain.dto.ReleaseCreateDTO;
import com.apex.req.domain.entity.ReqBaseline;
import com.apex.req.domain.entity.ReqProject;
import com.apex.req.domain.entity.ReqRelease;
import com.apex.req.domain.entity.ReqRequirement;
import com.apex.req.domain.vo.ReleaseVO;
import com.apex.req.mapper.ReqBaselineMapper;
import com.apex.req.mapper.ReqProjectMapper;
import com.apex.req.mapper.ReqReleaseMapper;
import com.apex.req.mapper.ReqRequirementMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 发布版本追踪 Service (P16 / M12)
 */
@Service
public class ReleaseService {

    private final ReqReleaseMapper releaseMapper;
    private final ReqBaselineMapper baselineMapper;
    private final ReqRequirementMapper requirementMapper;
    private final ReqProjectMapper projectMapper;

    public ReleaseService(
            ReqReleaseMapper releaseMapper,
            ReqBaselineMapper baselineMapper,
            ReqRequirementMapper requirementMapper,
            ReqProjectMapper projectMapper) {
        this.releaseMapper = releaseMapper;
        this.baselineMapper = baselineMapper;
        this.requirementMapper = requirementMapper;
        this.projectMapper = projectMapper;
    }

    /**
     * 查询指定项目的全部发布版本追踪列表（聚合基线与需求元数据，防 N+1 查询）
     */
    public List<ReleaseVO> listByProject(Long projectId) {
        LambdaQueryWrapper<ReqRelease> qw = new LambdaQueryWrapper<>();
        if (projectId != null) {
            qw.eq(ReqRelease::getProjectId, projectId);
        }
        qw.orderByDesc(ReqRelease::getCreatedAt);
        List<ReqRelease> releases = releaseMapper.selectList(qw);
        if (releases.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> baselineIds = releases.stream().map(ReqRelease::getBaselineId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ReqBaseline> baselineMap = baselineIds.isEmpty()
                ? Collections.emptyMap()
                : baselineMapper.selectBatchIds(baselineIds).stream().collect(Collectors.toMap(ReqBaseline::getId, Function.identity()));

        Set<Long> reqIds = baselineMap.values().stream().map(ReqBaseline::getRequirementId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ReqRequirement> reqMap = reqIds.isEmpty()
                ? Collections.emptyMap()
                : requirementMapper.selectBatchIds(reqIds).stream().collect(Collectors.toMap(ReqRequirement::getId, Function.identity()));

        Set<Long> projIds = releases.stream().map(ReqRelease::getProjectId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ReqProject> projMap = projIds.isEmpty()
                ? Collections.emptyMap()
                : projectMapper.selectBatchIds(projIds).stream().collect(Collectors.toMap(ReqProject::getId, Function.identity()));

        List<ReleaseVO> result = new ArrayList<>(releases.size());
        for (ReqRelease rel : releases) {
            ReqBaseline bl = baselineMap.get(rel.getBaselineId());
            ReqRequirement req = bl != null ? reqMap.get(bl.getRequirementId()) : null;
            ReqProject proj = projMap.get(rel.getProjectId());
            result.add(toVO(rel, bl, req, proj));
        }
        return result;
    }

    /**
     * 查询指定项目下可供发布绑定的「已冻结基线」列表 (status = BASELINED)
     */
    public List<Map<String, Object>> listFrozenBaselines(Long projectId) {
        LambdaQueryWrapper<ReqBaseline> qw = new LambdaQueryWrapper<>();
        if (projectId != null) {
            qw.eq(ReqBaseline::getProjectId, projectId);
        }
        qw.eq(ReqBaseline::getStatus, "BASELINED").orderByDesc(ReqBaseline::getFrozenAt);
        List<ReqBaseline> baselines = baselineMapper.selectList(qw);
        if (baselines.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> reqIds = baselines.stream().map(ReqBaseline::getRequirementId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ReqRequirement> reqMap = reqIds.isEmpty()
                ? Collections.emptyMap()
                : requirementMapper.selectBatchIds(reqIds).stream().collect(Collectors.toMap(ReqRequirement::getId, Function.identity()));

        List<Map<String, Object>> list = new ArrayList<>();
        for (ReqBaseline bl : baselines) {
            ReqRequirement req = reqMap.get(bl.getRequirementId());
            Map<String, Object> item = new HashMap<>();
            item.put("id", bl.getId());
            item.put("version", bl.getVersion());
            item.put("requirementId", bl.getRequirementId());
            item.put("reqCode", req != null ? req.getReqCode() : "—");
            item.put("reqTitle", req != null ? req.getTitle() : "—");
            item.put("totalCostDays", bl.getTotalCostDays());
            item.put("manifestHash", bl.getManifestHash());
            item.put("frozenAt", bl.getFrozenAt());
            list.add(item);
        }
        return list;
    }

    /**
     * 创建发布计划 (M12)：强校验必须绑定已冻结基线且同项目版本号唯一
     */
    @Transactional(rollbackFor = Exception.class)
    public ReleaseVO createRelease(ReleaseCreateDTO dto, String operator) {
        ReqBaseline baseline = baselineMapper.selectById(dto.getBaselineId());
        if (baseline == null) {
            throw new BusinessException("绑定的基线不存在");
        }
        if (!"BASELINED".equalsIgnoreCase(baseline.getStatus())) {
            throw new BusinessException("仅允许绑定已冻结基线 (BASELINED)，当前基线状态为: " + baseline.getStatus());
        }

        LambdaQueryWrapper<ReqRelease> dupQw = new LambdaQueryWrapper<>();
        dupQw.eq(ReqRelease::getProjectId, dto.getProjectId())
                .eq(ReqRelease::getReleaseVersion, dto.getReleaseVersion().trim());
        if (releaseMapper.selectCount(dupQw) > 0) {
            throw new BusinessException("当前项目已存在相同发布版本号: " + dto.getReleaseVersion());
        }

        ReqRelease entity = ReqRelease.builder()
                .projectId(dto.getProjectId())
                .baselineId(dto.getBaselineId())
                .releaseVersion(dto.getReleaseVersion().trim())
                .plannedDate(dto.getPlannedDate())
                .status("PLANNED")
                .releaseNotes(dto.getReleaseNotes())
                .createdBy(operator != null && !operator.isBlank() ? operator : "admin")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        releaseMapper.insert(entity);

        ReqRequirement req = baseline.getRequirementId() != null ? requirementMapper.selectById(baseline.getRequirementId()) : null;
        ReqProject proj = projectMapper.selectById(dto.getProjectId());
        return toVO(entity, baseline, req, proj);
    }

    /**
     * 正式发布：生成语义化 Git Tag，置发布状态为 RELEASED，并联动对应需求状态为 RELEASED
     */
    @Transactional(rollbackFor = Exception.class)
    public ReleaseVO publishRelease(Long releaseId, String operator) {
        ReqRelease rel = releaseMapper.selectById(releaseId);
        if (rel == null) {
            throw new BusinessException("发布记录不存在");
        }
        if ("RELEASED".equalsIgnoreCase(rel.getStatus())) {
            throw new BusinessException("该版本已正式发布，请勿重复操作");
        }

        ReqProject proj = projectMapper.selectById(rel.getProjectId());
        String projKey = proj != null && proj.getProjectKey() != null ? proj.getProjectKey() : "PROJ-" + rel.getProjectId();
        String gitTag = "release/" + projKey + "/" + rel.getReleaseVersion();
        LocalDateTime now = LocalDateTime.now();

        rel.setStatus("RELEASED");
        rel.setGitTag(gitTag);
        rel.setReleasedAt(now);
        rel.setUpdatedAt(now);
        releaseMapper.updateById(rel);

        // 联动推进对应需求状态为 RELEASED (闭环 PRD 状态机 BASELINED -> RELEASED)
        ReqBaseline bl = baselineMapper.selectById(rel.getBaselineId());
        ReqRequirement req = null;
        if (bl != null && bl.getRequirementId() != null) {
            req = requirementMapper.selectById(bl.getRequirementId());
            if (req != null) {
                req.setStatus("RELEASED");
                req.setUpdatedAt(now);
                requirementMapper.updateById(req);
            }
        }

        return toVO(rel, bl, req, proj);
    }

    private ReleaseVO toVO(ReqRelease rel, ReqBaseline bl, ReqRequirement req, ReqProject proj) {
        String statusText = "RELEASED".equalsIgnoreCase(rel.getStatus()) ? "已发布"
                : ("ROLLBACK".equalsIgnoreCase(rel.getStatus()) ? "已回滚" : "计划中");
        return ReleaseVO.builder()
                .id(rel.getId())
                .projectId(rel.getProjectId())
                .projectKey(proj != null ? proj.getProjectKey() : null)
                .baselineId(rel.getBaselineId())
                .baselineVersion(bl != null ? bl.getVersion() : "—")
                .manifestHash(bl != null ? bl.getManifestHash() : null)
                .totalCostDays(bl != null ? bl.getTotalCostDays() : null)
                .requirementId(bl != null ? bl.getRequirementId() : null)
                .reqCode(req != null ? req.getReqCode() : "—")
                .reqTitle(req != null ? req.getTitle() : "—")
                .releaseVersion(rel.getReleaseVersion())
                .gitTag(rel.getGitTag())
                .plannedDate(rel.getPlannedDate())
                .releasedAt(rel.getReleasedAt())
                .status(rel.getStatus())
                .statusText(statusText)
                .releaseNotes(rel.getReleaseNotes())
                .createdBy(rel.getCreatedBy())
                .createdAt(rel.getCreatedAt())
                .build();
    }
}
