package com.apex.req.service;

import com.apex.common.exception.BusinessException;
import com.apex.req.domain.dto.SignConfirmDTO;
import com.apex.req.domain.dto.SignRejectDTO;
import com.apex.req.domain.entity.*;
import com.apex.req.domain.vo.SignContextVO;
import com.apex.req.domain.vo.SignReceiptVO;
import com.apex.req.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 客户免登三方法定签名凭证与防伪电子回执服务 (绑定 DLD §2.1 & §3.2)
 */
@Slf4j
@Service
public class ApprovalService extends ServiceImpl<ReqApprovalMapper, ReqApproval> {

    private final ReqBaselineMapper baselineMapper;
    private final ReqRequirementMapper requirementMapper;
    private final ReqProjectMapper projectMapper;
    private final ReqPrototypeVersionMapper prototypeVersionMapper;
    private final ReqConfirmItemMapper confirmItemMapper;

    public ApprovalService(ReqBaselineMapper baselineMapper,
                           ReqRequirementMapper requirementMapper,
                           ReqProjectMapper projectMapper,
                           ReqPrototypeVersionMapper prototypeVersionMapper,
                           ReqConfirmItemMapper confirmItemMapper) {
        this.baselineMapper = baselineMapper;
        this.requirementMapper = requirementMapper;
        this.projectMapper = projectMapper;
        this.prototypeVersionMapper = prototypeVersionMapper;
        this.confirmItemMapper = confirmItemMapper;
    }

    /**
     * 为新基线生成三方法定签名凭证 (业务拍板人, 验收人, 使用者代表)
     */
    @Transactional(rollbackFor = Exception.class)
    public List<ReqApproval> createApprovalTokens(Long baselineId, String bizOwnerName, String acceptorName, String userRepName) {
        ReqBaseline baseline = baselineMapper.selectById(baselineId);
        if (baseline == null) {
            throw new BusinessException(404, "基线不存在 (ID: " + baselineId + ")");
        }

        // 删除已有未签署的存量记录 (避免重复发令)
        lambdaUpdate().eq(ReqApproval::getBaselineId, baselineId).eq(ReqApproval::getStatus, "PENDING").remove();

        Random random = new Random();
        List<ReqApproval> approvals = new ArrayList<>();

        String[][] roles = new String[][]{
                {"BIZ_OWNER", StringUtils.hasText(bizOwnerName) ? bizOwnerName : "业务拍板人"},
                {"ACCEPTOR", StringUtils.hasText(acceptorName) ? acceptorName : "项目验收人"},
                {"USER_REP", StringUtils.hasText(userRepName) ? userRepName : "使用者代表"}
        };

        for (String[] r : roles) {
            String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            String accessCode = String.format("%04d", random.nextInt(10000));

            ReqApproval approval = ReqApproval.builder()
                    .baselineId(baselineId)
                    .roleType(r[0])
                    .signerName(r[1])
                    .status("PENDING")
                    .token(token)
                    .accessCode(accessCode)
                    .tokenExpiresAt(LocalDateTime.now().plusDays(7))
                    .createdAt(LocalDateTime.now())
                    .build();

            save(approval);
            approvals.add(approval);
        }

        log.info("成功为基线 [ID:{}] 生成 3 份客户法定签名凭证", baselineId);
        return approvals;
    }

    /**
     * 免登录拉取只读确认上下文 (E01/E02)
     */
    public SignContextVO getSignContext(String token, String accessCode) {
        ReqApproval approval = validateTokenAndCode(token, accessCode);

        ReqBaseline baseline = baselineMapper.selectById(approval.getBaselineId());
        if (baseline == null) {
            throw new BusinessException(404, "关联基线不存在");
        }

        ReqRequirement req = requirementMapper.selectById(baseline.getRequirementId());
        ReqProject project = req != null ? projectMapper.selectById(req.getProjectId()) : null;

        ReqPrototypeVersion proto = prototypeVersionMapper.selectById(baseline.getPrototypeVersionId());

        List<ReqConfirmItem> items = confirmItemMapper.selectList(
                new LambdaQueryWrapper<ReqConfirmItem>()
                        .eq(ReqConfirmItem::getRequirementId, baseline.getRequirementId())
                        .orderByAsc(ReqConfirmItem::getCategory)
                        .orderByAsc(ReqConfirmItem::getItemCode)
        );

        String roleTitle = "外部签署人";
        if ("BIZ_OWNER".equalsIgnoreCase(approval.getRoleType())) roleTitle = "业务拍板人";
        else if ("ACCEPTOR".equalsIgnoreCase(approval.getRoleType())) roleTitle = "项目验收人";
        else if ("USER_REP".equalsIgnoreCase(approval.getRoleType())) roleTitle = "使用者代表";

        return SignContextVO.builder()
                .roleType(approval.getRoleType())
                .roleTitle(roleTitle)
                .signerName(approval.getSignerName())
                .baselineVersion(baseline.getVersion())
                .projectKey(project != null ? project.getProjectKey() : "")
                .projectName(project != null ? project.getName() : "")
                .clientName(project != null ? project.getClientName() : "")
                .reqCode(req != null ? req.getReqCode() : "")
                .reqTitle(req != null ? req.getTitle() : "")
                .rawQuote(req != null ? req.getRawQuote() : "")
                .previewUrl(proto != null ? proto.getPreviewUrl() : "")
                .prototypeHash(proto != null ? proto.getFileHash() : "")
                .totalCostDays(baseline.getTotalCostDays())
                .confirmItems(items)
                .status(approval.getStatus())
                .receiptCode(approval.getReceiptCode())
                .signedAt(approval.getSignedAt())
                .tokenExpiresAt(approval.getTokenExpiresAt())
                .build();
    }

    /**
     * 客户免登签署确认 (E03 电子防伪回执生成)
     */
    @Transactional(rollbackFor = Exception.class)
    public SignReceiptVO confirmSignature(String token, SignConfirmDTO dto, String clientIp, String userAgent) {
        ReqApproval approval = validateTokenAndCode(token, dto.getAccessCode());

        ReqBaseline baseline = baselineMapper.selectById(approval.getBaselineId());
        if (baseline == null) {
            throw new BusinessException(404, "关联基线不存在");
        }

        if ("SIGNED".equalsIgnoreCase(approval.getStatus())) {
            // 已签署过，幂等返回回执
            return SignReceiptVO.builder()
                    .receiptCode(approval.getReceiptCode())
                    .baselineVersion(baseline.getVersion())
                    .isFullyFrozen("BASELINED".equalsIgnoreCase(baseline.getStatus()))
                    .manifestHash(baseline.getManifestHash())
                    .signedAt(approval.getSignedAt())
                    .build();
        }

        String receiptCode = generateReceiptCode();
        LocalDateTime now = LocalDateTime.now();

        approval.setStatus("SIGNED");
        approval.setSignerName(dto.getSignerName());
        approval.setRestatementText(dto.getRestatementText());
        approval.setSignedAt(now);
        approval.setSignerIp(clientIp);
        approval.setUserAgent(userAgent);
        approval.setReceiptCode(receiptCode);
        updateById(approval);

        log.info("角色 [{}] 签署人 [{}] 签署成功, 回执码: {}", approval.getRoleType(), dto.getSignerName(), receiptCode);

        // 检查三方是否全部签署齐备 (3/3)
        Long signedCount = lambdaQuery()
                .eq(ReqApproval::getBaselineId, baseline.getId())
                .eq(ReqApproval::getStatus, "SIGNED")
                .count();

        boolean fullyFrozen = false;
        if (signedCount >= 3) {
            // 触发不可逆物理冻结
            baseline.setStatus("BASELINED");
            baseline.setFrozenAt(now);
            baselineMapper.updateById(baseline);

            ReqRequirement req = requirementMapper.selectById(baseline.getRequirementId());
            if (req != null) {
                req.setStatus("BASELINED");
                req.setUpdatedAt(now);
                requirementMapper.updateById(req);
            }
            fullyFrozen = true;
            log.info("基线 [ID:{}, Version:{}] 三方法定签名全部齐备，已完成物理冻结！", baseline.getId(), baseline.getVersion());
        }

        return SignReceiptVO.builder()
                .receiptCode(receiptCode)
                .baselineVersion(baseline.getVersion())
                .isFullyFrozen(fullyFrozen)
                .manifestHash(baseline.getManifestHash())
                .signedAt(now)
                .build();
    }

    /**
     * 客户逆向驳回分歧流转 (返回澄清中)
     */
    @Transactional(rollbackFor = Exception.class)
    public void rejectSignature(String token, SignRejectDTO dto, String clientIp, String userAgent) {
        ReqApproval approval = validateTokenAndCode(token, dto.getAccessCode());

        approval.setStatus("REJECTED");
        if (StringUtils.hasText(dto.getSignerName())) {
            approval.setSignerName(dto.getSignerName());
        }
        approval.setRejectReason(dto.getRejectReason());
        approval.setSignerIp(clientIp);
        approval.setUserAgent(userAgent);
        updateById(approval);

        ReqBaseline baseline = baselineMapper.selectById(approval.getBaselineId());
        if (baseline != null) {
            baseline.setStatus("DRAFT");
            baselineMapper.updateById(baseline);

            ReqRequirement req = requirementMapper.selectById(baseline.getRequirementId());
            if (req != null) {
                req.setStatus("CLARIFYING");
                req.setUpdatedAt(LocalDateTime.now());
                requirementMapper.updateById(req);
            }
        }

        log.warn("客户驳回基线 [ID:{}]，原因: {}", approval.getBaselineId(), dto.getRejectReason());
    }

    /**
     * 校验 Token 与 4位 AccessCode
     */
    private ReqApproval validateTokenAndCode(String token, String accessCode) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(400101, "签名链接无效");
        }
        ReqApproval approval = lambdaQuery().eq(ReqApproval::getToken, token).one();
        if (approval == null || (approval.getTokenExpiresAt() != null && approval.getTokenExpiresAt().isBefore(LocalDateTime.now()))) {
            throw new BusinessException(400101, "签名链接已失效或过期");
        }

        if (!StringUtils.hasText(accessCode) || !accessCode.trim().equals(approval.getAccessCode())) {
            throw new BusinessException(400102, "访问验证码错误");
        }
        return approval;
    }

    /**
     * 生成防伪回执码 (格式如 7F3A-91C2-88B1)
     */
    public static String generateReceiptCode() {
        String raw = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-" + raw.substring(8, 12);
    }
}
