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
     * 上传原型 ZIP 包并计算整体 SHA256 目录指纹
     */
    @PostMapping("/upload")
    public Result<ReqPrototypeVersion> uploadPrototype(
            @RequestParam("reqId") Long reqId,
            @RequestParam(value = "changeSummary", required = false) String changeSummary,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Operator", defaultValue = "admin") String operator) {
        ReqPrototypeVersion version = prototypeService.uploadPrototype(reqId, changeSummary, file, operator);
        return Result.success(version, "原型快照托管成功");
    }

    /**
     * 获取指定需求的所有原型版本列表
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
