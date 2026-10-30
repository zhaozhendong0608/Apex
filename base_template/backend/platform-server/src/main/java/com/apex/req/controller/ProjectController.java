package com.apex.req.controller;

import com.apex.common.result.Result;
import com.apex.req.domain.dto.ProjectCreateDTO;
import com.apex.req.domain.entity.ReqProject;
import com.apex.req.domain.vo.ProjectStatsVO;
import com.apex.req.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目空间与工作台 Controller (绑定 DLD §3.1)
 */
@RestController
@RequestMapping("/api/v1/req/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * 获取所有活跃项目列表
     */
    @GetMapping
    public Result<List<ReqProject>> listProjects() {
        return Result.success(projectService.listActiveProjects());
    }

    /**
     * 获取项目详情
     */
    @GetMapping("/{id}")
    public Result<ReqProject> getProjectById(@PathVariable("id") Long id) {
        return Result.success(projectService.getById(id));
    }

    /**
     * 根据 projectKey 获取项目
     */
    @GetMapping("/by-key/{key}")
    public Result<ReqProject> getProjectByKey(@PathVariable("key") String key) {
        return Result.success(projectService.getByKey(key));
    }

    /**
     * 获取项目工作台实时聚合统计数据
     */
    @GetMapping("/{id}/stats")
    public Result<ProjectStatsVO> getProjectStats(@PathVariable("id") Long id) {
        return Result.success(projectService.getProjectStats(id));
    }

    /**
     * 创建新项目空间
     */
    @PostMapping
    public Result<ReqProject> createProject(
            @Valid @RequestBody ProjectCreateDTO dto,
            @RequestHeader(value = "X-Operator", defaultValue = "admin") String operator) {
        ReqProject project = projectService.createProject(dto, operator);
        return Result.success(project, "项目空间创建成功");
    }

    /**
     * 删除项目空间 (仅限管理员操作)
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteProject(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-Operator", defaultValue = "admin") String operator) {
        projectService.deleteProject(id, operator);
        return Result.success(null, "项目空间已成功删除");
    }
}
