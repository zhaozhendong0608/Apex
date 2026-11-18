package com.apex.req.controller;

import com.apex.common.result.Result;
import com.apex.req.domain.dto.ReleaseCreateDTO;
import com.apex.req.domain.vo.ReleaseVO;
import com.apex.req.service.ReleaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 发布版本追踪 Controller (P16 / M12)
 */
@RestController
@RequestMapping("/api/v1/req/releases")
public class ReleaseController {

    private final ReleaseService releaseService;

    public ReleaseController(ReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    /**
     * 查询项目下的所有发布版本列表
     */
    @GetMapping
    public Result<List<ReleaseVO>> listReleases(@RequestParam(value = "projectId", required = false) Long projectId) {
        return Result.success(releaseService.listByProject(projectId));
    }

    /**
     * 查询当前项目可用于发布绑定的已冻结基线 (status = BASELINED)
     */
    @GetMapping("/frozen-baselines")
    public Result<List<Map<String, Object>>> listFrozenBaselines(@RequestParam(value = "projectId", required = false) Long projectId) {
        return Result.success(releaseService.listFrozenBaselines(projectId));
    }

    /**
     * 新建发布计划 (M12)
     */
    @PostMapping
    public Result<ReleaseVO> createRelease(
            @Valid @RequestBody ReleaseCreateDTO dto,
            @RequestHeader(value = "X-Operator", defaultValue = "admin") String operator) {
        ReleaseVO vo = releaseService.createRelease(dto, operator);
        return Result.success(vo, "发布计划创建成功");
    }

    /**
     * 执行正式发布，自动打 Git Tag 并推进需求状态至 RELEASED
     */
    @PostMapping("/{id}/publish")
    public Result<ReleaseVO> publishRelease(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-Operator", defaultValue = "admin") String operator) {
        ReleaseVO vo = releaseService.publishRelease(id, operator);
        return Result.success(vo, "正式发布成功，已自动打标 Git Tag: " + vo.getGitTag());
    }
}
