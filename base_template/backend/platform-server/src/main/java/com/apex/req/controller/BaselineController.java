package com.apex.req.controller;

import com.apex.common.result.Result;
import com.apex.req.domain.dto.BaselineCreateDTO;
import com.apex.req.domain.entity.ReqBaseline;
import com.apex.req.domain.vo.BaselineDetailVO;
import com.apex.req.service.BaselineService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 需求基线管理与冻结 Controller (绑定 DLD §3.1)
 */
@RestController
@RequestMapping("/api/v1/req/baselines")
public class BaselineController {

    private final BaselineService baselineService;
    private final com.apex.req.service.DiffService diffService;

    public BaselineController(BaselineService baselineService, com.apex.req.service.DiffService diffService) {
        this.baselineService = baselineService;
        this.diffService = diffService;
    }

    /**
     * 申请创建新基线并生成三方客户签名专用链接
     */
    @PostMapping
    public Result<BaselineDetailVO> createBaseline(
            @Valid @RequestBody BaselineCreateDTO dto,
            @RequestHeader(value = "X-Operator", defaultValue = "admin") String operator) {
        BaselineDetailVO vo = baselineService.createBaseline(dto, operator);
        return Result.success(vo, "基线创建并申请签署成功");
    }

    /**
     * 查询指定需求的所有基线版本列表
     */
    @GetMapping
    public Result<List<ReqBaseline>> listBaselines(@RequestParam("requirementId") Long requirementId) {
        List<ReqBaseline> list = baselineService.listBaselines(requirementId);
        return Result.success(list);
    }

    /**
     * 查询指定基线的详细存证与三方签署状态
     */
    @GetMapping("/{id}")
    public Result<BaselineDetailVO> getDetail(@PathVariable("id") Long id) {
        BaselineDetailVO vo = baselineService.getBaselineDetail(id);
        return Result.success(vo);
    }

    /**
     * 智能计算基线增量差异对比 (绑定 DLD §3.2)
     */
    @GetMapping("/{id}/diff")
    public Result<com.apex.req.domain.vo.BaselineDiffVO> getDiff(
            @PathVariable("id") Long id,
            @RequestParam(value = "target_id", required = false) Long targetId) {
        com.apex.req.domain.vo.BaselineDiffVO diff = diffService.calculateDiff(id, targetId);
        return Result.success(diff);
    }
}
