package com.apex.req.controller;

import com.apex.common.result.Result;
import com.apex.req.domain.dto.SignConfirmDTO;
import com.apex.req.domain.dto.SignRejectDTO;
import com.apex.req.domain.vo.SignContextVO;
import com.apex.req.domain.vo.SignReceiptVO;
import com.apex.req.service.ApprovalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 客户免登法定签名与电子回执公开 Controller (绑定 DLD §2.1, §3.1 & §3.2)
 */
@RestController
@RequestMapping("/api/v1/public/sign")
public class PublicSignController {

    private final ApprovalService approvalService;

    public PublicSignController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /**
     * 客户凭 Token 和 4 位访问码拉取只读确认上下文 (E01/E02)
     */
    @GetMapping("/{token}")
    public Result<SignContextVO> getSignContext(
            @PathVariable("token") String token,
            @RequestParam("code") String code) {
        SignContextVO context = approvalService.getSignContext(token, code);
        return Result.success(context);
    }

    /**
     * 客户提交一句话复述与电子签署 (E03 电子防伪回执生成)
     */
    @PostMapping("/{token}/confirm")
    public Result<SignReceiptVO> confirmSignature(
            @PathVariable("token") String token,
            @Valid @RequestBody SignConfirmDTO dto,
            HttpServletRequest request) {
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        SignReceiptVO receipt = approvalService.confirmSignature(token, dto, clientIp, userAgent);
        return Result.success(receipt, "签署成功");
    }

    /**
     * 客户提出异议分歧并驳回至澄清态
     */
    @PostMapping("/{token}/reject")
    public Result<Void> rejectSignature(
            @PathVariable("token") String token,
            @Valid @RequestBody SignRejectDTO dto,
            HttpServletRequest request) {
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        approvalService.rejectSignature(token, dto, clientIp, userAgent);
        return Result.success(null, "已成功记录异议分歧并退回澄清流程");
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "127.0.0.1";
    }
}
