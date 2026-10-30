package com.apex.req.controller;

import com.apex.common.result.Result;
import com.apex.req.domain.dto.BatchConfirmItemsDTO;
import com.apex.req.domain.dto.RequirementCreateDTO;
import com.apex.req.domain.entity.ReqRequirement;
import com.apex.req.domain.vo.CostCalculationVO;
import com.apex.req.domain.vo.RequirementDetailVO;
import com.apex.req.domain.vo.RequirementListItemVO;
import com.apex.req.service.RequirementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 需求与四层清单管理 Controller (绑定 DLD §3.1)
 */
@RestController
@RequestMapping("/api/v1/req/requirements")
public class RequirementController {

    private final RequirementService requirementService;

    public RequirementController(RequirementService requirementService) {
        this.requirementService = requirementService;
    }

    /**
     * 快速录入需求 (存客户原话、责任人)
     */
    @PostMapping
    public Result<ReqRequirement> createRequirement(
            @Valid @RequestBody RequirementCreateDTO dto,
            @RequestHeader(value = "X-Operator", defaultValue = "admin") String operator) {
        ReqRequirement req = requirementService.createRequirement(dto, operator);
        return Result.success(req, "需求录入成功");
    }

    /**
     * 需求列表查询 (对齐工作台全景表与基线折算)
     */
    @GetMapping
    public Result<List<RequirementListItemVO>> listRequirements(
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "status", required = false) String status) {
        List<RequirementListItemVO> list = requirementService.listRequirementVOs(projectId, status);
        return Result.success(list);
    }

    /**
     * 获取需求详情 (含四层清单与实时代价)
     */
    @GetMapping("/{id}")
    public Result<RequirementDetailVO> getDetail(@PathVariable("id") Long id) {
        RequirementDetailVO detail = requirementService.getRequirementDetail(id);
        return Result.success(detail);
    }

    /**
     * 批量保存四层清单决策与代价人天
     */
    @PutMapping("/{id}/confirm-items")
    public Result<CostCalculationVO> saveConfirmItems(
            @PathVariable("id") Long id,
            @Valid @RequestBody BatchConfirmItemsDTO dto,
            @RequestHeader(value = "X-Operator", defaultValue = "admin") String operator) {
        CostCalculationVO costVO = requirementService.saveConfirmItems(id, dto.getItems(), operator);
        return Result.success(costVO, "四层清单决策保存成功");
    }

    /**
     * 获取实时代价折算统计
     */
    @GetMapping("/{id}/cost")
    public Result<CostCalculationVO> getCost(@PathVariable("id") Long id) {
        CostCalculationVO costVO = requirementService.calculateCost(id);
        return Result.success(costVO);
    }

    /**
     * 门禁预核验 (检查是否符合基线冻结条件)
     */
    @GetMapping("/{id}/gate-check")
    public Result<Void> checkGate(@PathVariable("id") Long id) {
        requirementService.validateGateCheck(id);
        return Result.success(null, "基线冻结门禁核验通过");
    }

    /**
     * 特权解冻需求 (解除 BASELINED 状态限制)
     */
    @PostMapping("/{id}/unfreeze")
    public Result<ReqRequirement> unfreeze(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-Operator", defaultValue = "admin") String operator) {
        ReqRequirement req = requirementService.unfreezeRequirement(id, operator);
        return Result.success(req, "需求已成功解除冻结，恢复为可编辑状态");
    }
}
