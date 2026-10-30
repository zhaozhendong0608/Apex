package com.apex.req.service;

import com.apex.common.exception.BusinessException;
import com.apex.req.domain.dto.ProjectCreateDTO;
import com.apex.req.domain.entity.ReqApproval;
import com.apex.req.domain.entity.ReqBaseline;
import com.apex.req.domain.entity.ReqConfirmItem;
import com.apex.req.domain.entity.ReqProject;
import com.apex.req.domain.entity.ReqPrototypeVersion;
import com.apex.req.domain.entity.ReqRequirement;
import com.apex.req.domain.vo.ProjectStatsVO;
import com.apex.req.mapper.ReqApprovalMapper;
import com.apex.req.mapper.ReqBaselineMapper;
import com.apex.req.mapper.ReqConfirmItemMapper;
import com.apex.req.mapper.ReqProjectMapper;
import com.apex.req.mapper.ReqPrototypeVersionMapper;
import com.apex.req.mapper.ReqRequirementMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 项目空间与工作台统计服务
 */
@Slf4j
@Service
public class ProjectService extends ServiceImpl<ReqProjectMapper, ReqProject> {

    private final ReqRequirementMapper requirementMapper;
    private final ReqBaselineMapper baselineMapper;
    private final ReqPrototypeVersionMapper prototypeVersionMapper;
    private final ReqConfirmItemMapper confirmItemMapper;
    private final ReqApprovalMapper approvalMapper;

    public ProjectService(ReqRequirementMapper requirementMapper,
                          ReqBaselineMapper baselineMapper,
                          ReqPrototypeVersionMapper prototypeVersionMapper,
                          ReqConfirmItemMapper confirmItemMapper,
                          ReqApprovalMapper approvalMapper) {
        this.requirementMapper = requirementMapper;
        this.baselineMapper = baselineMapper;
        this.prototypeVersionMapper = prototypeVersionMapper;
        this.confirmItemMapper = confirmItemMapper;
        this.approvalMapper = approvalMapper;
    }

    /**
     * 获取所有活跃项目列表
     */
    public List<ReqProject> listActiveProjects() {
        return lambdaQuery()
                .eq(ReqProject::getStatus, "ACTIVE")
                .orderByDesc(ReqProject::getId)
                .list();
    }

    /**
     * 根据 projectKey 获取项目
     */
    public ReqProject getByKey(String projectKey) {
        return lambdaQuery()
                .eq(ReqProject::getProjectKey, projectKey)
                .one();
    }

    /**
     * 创建新项目空间
     */
    @Transactional(rollbackFor = Exception.class)
    public ReqProject createProject(ProjectCreateDTO dto, String operator) {
        Long exists = lambdaQuery()
                .eq(ReqProject::getProjectKey, dto.getProjectKey().toUpperCase())
                .count();
        if (exists > 0) {
            throw new BusinessException(400, "项目代号 [" + dto.getProjectKey() + "] 已存在");
        }

        ReqProject project = ReqProject.builder()
                .projectKey(dto.getProjectKey().toUpperCase())
                .name(dto.getName())
                .clientName(dto.getClientName())
                .description(dto.getDescription())
                .status("ACTIVE")
                .createdBy(operator)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        save(project);
        log.info("创建新项目空间成功: ID={}, Key={}, Name={}", project.getId(), project.getProjectKey(), project.getName());
        return project;
    }

    /**
     * 实时聚合计算项目工作台统计指标
     */
    public ProjectStatsVO getProjectStats(Long projectId) {
        ReqProject project = getById(projectId);
        if (project == null) {
            throw new BusinessException(404, "项目不存在 (ID: " + projectId + ")");
        }

        List<ReqRequirement> reqList = requirementMapper.selectList(
                new LambdaQueryWrapper<ReqRequirement>().eq(ReqRequirement::getProjectId, projectId)
        );

        int totalReq = reqList.size();
        int baselinedCount = 0;
        int pendingCount = 0;
        int clarifyingCount = 0;
        List<Long> reqIds = reqList.stream().map(ReqRequirement::getId).collect(Collectors.toList());

        for (ReqRequirement r : reqList) {
            if ("BASELINED".equalsIgnoreCase(r.getStatus())) {
                baselinedCount++;
            } else if ("PENDING".equalsIgnoreCase(r.getStatus())) {
                pendingCount++;
            } else {
                clarifyingCount++;
            }
        }

        // 统计关联的原型快照总数
        int protoCount = 0;
        if (!reqIds.isEmpty()) {
            Long c = prototypeVersionMapper.selectCount(
                    new LambdaQueryWrapper<ReqPrototypeVersion>().in(ReqPrototypeVersion::getRequirementId, reqIds)
            );
            protoCount = c != null ? c.intValue() : 0;
        }

        // 统计已冻结基线总数与最新基线
        List<ReqBaseline> baselines = baselineMapper.selectList(
                new LambdaQueryWrapper<ReqBaseline>()
                        .eq(ReqBaseline::getProjectId, projectId)
                        .orderByDesc(ReqBaseline::getId)
        );

        BigDecimal totalCostDays = BigDecimal.ZERO;
        String latestBaselineCode = "—";
        if (!baselines.isEmpty()) {
            latestBaselineCode = baselines.get(0).getVersion();
            for (ReqBaseline b : baselines) {
                if (b.getTotalCostDays() != null) {
                    totalCostDays = totalCostDays.add(b.getTotalCostDays());
                }
            }
        }

        return ProjectStatsVO.builder()
                .projectId(project.getId())
                .projectKey(project.getProjectKey())
                .projectName(project.getName())
                .clientName(project.getClientName())
                .description(project.getDescription())
                .totalRequirements(totalReq)
                .baselinedCount(baselinedCount)
                .pendingCount(pendingCount)
                .clarifyingCount(clarifyingCount)
                .prototypeCount(protoCount)
                .totalCostDays(totalCostDays)
                .latestBaselineCode(latestBaselineCode)
                .build();
    }

    /**
     * 删除项目空间 (物理级联粉碎删除)
     * 1. 强门禁：核验是否已存在冻结法定基线 (BASELINED)；若已冻结，作为防扯皮单一事实源禁止物理粉碎！
     * 2. 物理级联粉碎：自底向上删除关联表所有数据：
     *    - t_req_approval (签名凭证)
     *    - t_req_baseline (基线记录)
     *    - t_req_confirm_item (四层清单细项)
     *    - t_req_prototype_version (原型版本快照记录)
     *    - t_req_requirement (需求全景主表)
     *    - t_req_project (项目主表)
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteProject(Long projectId, String operator) {
        ReqProject project = getById(projectId);
        if (project == null) {
            throw new BusinessException(404, "项目不存在或已被粉碎删除");
        }

        // 1. 查找项目下所有的需求 ID 列表
        List<ReqRequirement> reqList = requirementMapper.selectList(
                new LambdaQueryWrapper<ReqRequirement>().eq(ReqRequirement::getProjectId, projectId)
        );
        List<Long> reqIds = reqList.stream().map(ReqRequirement::getId).collect(Collectors.toList());

        // 2. 查找项目下所有基线记录
        List<ReqBaseline> baselineList = baselineMapper.selectList(
                new LambdaQueryWrapper<ReqBaseline>().eq(ReqBaseline::getProjectId, projectId)
        );
        List<Long> baselineIds = baselineList.stream().map(ReqBaseline::getId).collect(Collectors.toList());

        // 3. 强门禁核验：若项目下存在状态为 BASELINED 的生效法定基线，阻断物理抹除
        boolean hasActiveBaselined = baselineList.stream()
                .anyMatch(b -> "BASELINED".equalsIgnoreCase(b.getStatus()));
        if (hasActiveBaselined) {
            throw new BusinessException(400, "删除拒绝：该项目已产生已冻结法定需求基线，作为防扯皮单一事实源禁止物理粉碎！");
        }

        // 4. 自底向上物理粉碎删除
        int deletedApprovals = 0;
        if (!baselineIds.isEmpty()) {
            // 4.1 物理删除基线关联的审批/签名记录
            deletedApprovals = approvalMapper.delete(
                    new LambdaQueryWrapper<ReqApproval>().in(ReqApproval::getBaselineId, baselineIds)
            );
            // 4.2 物理删除基线记录本身
            baselineMapper.delete(
                    new LambdaQueryWrapper<ReqBaseline>().in(ReqBaseline::getId, baselineIds)
            );
        }

        int deletedItems = 0;
        int deletedPrototypes = 0;
        if (!reqIds.isEmpty()) {
            // 4.3 物理删除四层确认清单表 t_req_confirm_item
            deletedItems = confirmItemMapper.delete(
                    new LambdaQueryWrapper<ReqConfirmItem>().in(ReqConfirmItem::getRequirementId, reqIds)
            );

            // 4.4 物理删除原型版本记录表 t_req_prototype_version
            deletedPrototypes = prototypeVersionMapper.delete(
                    new LambdaQueryWrapper<ReqPrototypeVersion>().in(ReqPrototypeVersion::getRequirementId, reqIds)
            );

            // 4.5 物理删除需求全景主表 t_req_requirement
            requirementMapper.delete(
                    new LambdaQueryWrapper<ReqRequirement>().in(ReqRequirement::getId, reqIds)
            );
        }

        // 4.6 物理删除项目主表 t_req_project
        removeById(projectId);

        log.info("管理员 [{}] 执行项目级联物理粉碎删除成功: 项目ID={}, Key={}, 清除需求数={}, 清除清单项数={}, 清除原型版本数={}, 清除基线数={}, 清除签名凭证数={}",
                operator, project.getId(), project.getProjectKey(), reqIds.size(), deletedItems, deletedPrototypes, baselineIds.size(), deletedApprovals);
    }
}
