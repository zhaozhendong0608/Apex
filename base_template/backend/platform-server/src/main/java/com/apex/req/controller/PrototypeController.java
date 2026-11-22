package com.apex.req.controller;

import com.apex.common.result.Result;
import com.apex.req.domain.entity.ReqPrototypeVersion;
import com.apex.req.service.PrototypeService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 原型沙盒托管与版本快照 Controller (绑定 DLD §3.1)
 */
@RestController
@RequestMapping("/api/v1/req/prototypes")
public class PrototypeController {

    private final PrototypeService prototypeService;

    public PrototypeController(PrototypeService prototypeService) {
        this.prototypeService = prototypeService;
    }

    /**
     * 上传原型文件 (优先支持以 projectId 为项目物理维度，同时兼容 reqId)
     */
    @PostMapping("/upload")
    public Result<ReqPrototypeVersion> uploadPrototype(
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "reqId", required = false) Long reqId,
            @RequestParam(value = "changeSummary", required = false) String changeSummary,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Operator", defaultValue = "admin") String operator) {
        ReqPrototypeVersion version = prototypeService.uploadPrototype(projectId, reqId, changeSummary, file, operator);
        return Result.success(version, "原型快照托管成功");
    }

    /**
     * 按项目查询原型版本快照清单 (以项目为唯一物理和业务维度)
     */
    @GetMapping("/project/{projectId}")
    public Result<List<ReqPrototypeVersion>> listProjectVersions(@PathVariable("projectId") Long projectId) {
        List<ReqPrototypeVersion> list = prototypeService.listByProjectId(projectId);
        return Result.success(list);
    }

    /**
     * 获取指定项目的最新原型版本
     */
    @GetMapping("/project/{projectId}/latest")
    public Result<ReqPrototypeVersion> getLatestProjectVersion(@PathVariable("projectId") Long projectId) {
        ReqPrototypeVersion latest = prototypeService.getLatestByProjectId(projectId);
        return Result.success(latest);
    }

    /**
     * 获取指定需求的所有原型版本列表 (兼容历史接口)
     */
    @GetMapping("/{reqId}")
    public Result<List<ReqPrototypeVersion>> listVersions(@PathVariable("reqId") Long reqId) {
        List<ReqPrototypeVersion> list = prototypeService.listByReqId(reqId);
        return Result.success(list);
    }

    /**
     * 获取指定需求的最新原型版本
     */
    @GetMapping("/{reqId}/latest")
    public Result<ReqPrototypeVersion> getLatestVersion(@PathVariable("reqId") Long reqId) {
        ReqPrototypeVersion latest = prototypeService.getLatestByReqId(reqId);
        return Result.success(latest);
    }
}
