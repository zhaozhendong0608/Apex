package com.apex.req.service;

import com.apex.common.exception.BusinessException;
import com.apex.req.domain.dto.BaselineCreateDTO;
import com.apex.req.domain.entity.*;
import com.apex.req.domain.vo.BaselineDetailVO;
import com.apex.req.domain.vo.CostCalculationVO;
import com.apex.req.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 需求基线冻结、看门狗门禁与 Git Tag 自动化服务 (绑定 DLD §2.1 & §4.5)
 */
@Slf4j
@Service
public class BaselineService extends ServiceImpl<ReqBaselineMapper, ReqBaseline> {

    private final ReqRequirementMapper requirementMapper;
    private final ReqProjectMapper projectMapper;
    private final ReqPrototypeVersionMapper prototypeVersionMapper;
    private final ReqConfirmItemMapper confirmItemMapper;
    private final RequirementService requirementService;
    private final ApprovalService approvalService;

    @Value("${storage.baseline.path:./storage/baselines}")
    private String baselineStorageRoot;

    public BaselineService(ReqRequirementMapper requirementMapper,
                           ReqProjectMapper projectMapper,
                           ReqPrototypeVersionMapper prototypeVersionMapper,
                           ReqConfirmItemMapper confirmItemMapper,
                           RequirementService requirementService,
                           ApprovalService approvalService) {
        this.requirementMapper = requirementMapper;
        this.projectMapper = projectMapper;
        this.prototypeVersionMapper = prototypeVersionMapper;
        this.confirmItemMapper = confirmItemMapper;
        this.requirementService = requirementService;
        this.approvalService = approvalService;
    }

    /**
     * 申请创建需求基线并触发门禁看门狗核验
     */
    @Transactional(rollbackFor = Exception.class)
    public BaselineDetailVO createBaseline(BaselineCreateDTO dto, String operator) {
        Long reqId = dto.getRequirementId();
        ReqRequirement req = requirementMapper.selectById(reqId);
        if (req == null) {
            throw new BusinessException(404, "关联需求不存在 (ID: " + reqId + ")");
        }
        ReqProject project = projectMapper.selectById(dto.getProjectId());
        if (project == null) {
            throw new BusinessException(404, "关联项目不存在 (ID: " + dto.getProjectId() + ")");
        }

        // 1. 严格执行蓝皮书 §4.1 4项硬门禁核验
        requirementService.validateGateCheck(reqId);

        // 2. 锁定原型版本
        ReqPrototypeVersion protoVersion;
        if (dto.getPrototypeVersionId() != null) {
            protoVersion = prototypeVersionMapper.selectById(dto.getPrototypeVersionId());
        } else {
            protoVersion = prototypeVersionMapper.selectOne(
                    new LambdaQueryWrapper<ReqPrototypeVersion>()
                            .eq(ReqPrototypeVersion::getRequirementId, reqId)
                            .orderByDesc(ReqPrototypeVersion::getId)
                            .last("LIMIT 1")
            );
        }
        if (protoVersion == null) {
            throw new BusinessException(400, "需求未绑定任何有效原型快照");
        }

        // 3. 计算基线版本号 (BL-v1.0, BL-v1.1...)
        String baselineVer = getNextBaselineVersion(reqId);

        // 4. 计算当前锁定人天代价
        CostCalculationVO costVO = requirementService.calculateCost(reqId);
        BigDecimal totalCostDays = costVO.getTotalCostDays();

        // 5. 组装并落盘 manifest.json
        String projectKey = project.getProjectKey();
        String reqCode = req.getReqCode();
        String gitTag = String.format("bl/%s/%s/%s", projectKey, reqCode, baselineVer.replace("BL-", ""));

        Path dirPath = Paths.get(baselineStorageRoot, projectKey, reqCode, baselineVer).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dirPath);

            String manifestJson = buildManifestJson(
                    baselineVer, gitTag, project, req, protoVersion, totalCostDays, operator
            );

            Path manifestFile = dirPath.resolve("manifest.json");
            Files.write(manifestFile, manifestJson.getBytes(StandardCharsets.UTF_8));

            String manifestHash = calculateSha256(manifestJson.getBytes(StandardCharsets.UTF_8));

            // 6. 归档打包 Zip (存储 manifest 与元数据)
            Path zipFile = dirPath.resolve(reqCode + "-" + baselineVer + ".zip");
            createArchiveZip(zipFile, manifestJson, manifestHash);

            // 7. 自动化生成本地代码库 Git Tag
            executeGitTag(gitTag, "Frozen baseline " + baselineVer + " for " + reqCode);

            // 8. 持久化基线记录
            ReqBaseline baseline = ReqBaseline.builder()
                    .projectId(project.getId())
                    .requirementId(req.getId())
                    .version(baselineVer)
                    .gitTag(gitTag)
                    .prototypeVersionId(protoVersion.getId())
                    .manifestPath(manifestFile.toString())
                    .manifestHash(manifestHash)
                    .archiveZipPath(zipFile.toString())
                    .status("PENDING")
                    .totalCostDays(totalCostDays)
                    .createdAt(LocalDateTime.now())
                    .build();
            save(baseline);

            // 9. 为新基线生成三方法定免登签名凭证
            List<ReqApproval> approvals = approvalService.createApprovalTokens(
                    baseline.getId(), dto.getBizOwnerName(), dto.getAcceptorName(), dto.getUserRepName()
            );

            log.info("成功创建需求基线 [{}], Git Tag: {}, 待签署凭证数: {}", baselineVer, gitTag, approvals.size());

            return BaselineDetailVO.builder()
                    .baseline(baseline)
                    .requirement(req)
                    .prototypeVersion(protoVersion)
                    .approvals(approvals)
                    .manifestContent(manifestJson)
                    .build();

        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("创建需求基线异常: {}", e.getMessage(), e);
            throw new BusinessException(500, "创建需求基线异常: " + e.getMessage());
        }
    }

    /**
     * 获取指定需求的所有基线版本列表
     */
    public List<ReqBaseline> listBaselines(Long reqId) {
        return lambdaQuery()
                .eq(ReqBaseline::getRequirementId, reqId)
                .orderByDesc(ReqBaseline::getId)
                .list();
    }

    /**
     * 获取基线完整详情
     */
    public BaselineDetailVO getBaselineDetail(Long baselineId) {
        ReqBaseline baseline = getById(baselineId);
        if (baseline == null) {
            throw new BusinessException(404, "基线不存在 (ID: " + baselineId + ")");
        }
        ReqRequirement req = requirementMapper.selectById(baseline.getRequirementId());
        ReqPrototypeVersion proto = prototypeVersionMapper.selectById(baseline.getPrototypeVersionId());
        List<ReqApproval> approvals = approvalService.lambdaQuery()
                .eq(ReqApproval::getBaselineId, baselineId)
                .list();

        String manifestContent = "";
        if (baseline.getManifestPath() != null) {
            Path path = Paths.get(baseline.getManifestPath());
            if (Files.exists(path)) {
                try {
                    manifestContent = Files.readString(path, StandardCharsets.UTF_8);
                } catch (IOException ignored) {
                }
            }
        }

        return BaselineDetailVO.builder()
                .baseline(baseline)
                .requirement(req)
                .prototypeVersion(proto)
                .approvals(approvals)
                .manifestContent(manifestContent)
                .build();
    }

    /**
     * 计算下一个基线版本号 (BL-v1.0 -> BL-v1.1...)
     */
    public String getNextBaselineVersion(Long reqId) {
        List<ReqBaseline> baselines = lambdaQuery()
                .eq(ReqBaseline::getRequirementId, reqId)
                .list();
        if (baselines.isEmpty()) {
            return "BL-v1.0";
        }
        int maxMinor = 0;
        for (ReqBaseline b : baselines) {
            String v = b.getVersion();
            if (v != null && v.startsWith("BL-v1.")) {
                try {
                    int minor = Integer.parseInt(v.substring("BL-v1.".length()).trim());
                    if (minor > maxMinor) {
                        maxMinor = minor;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return "BL-v1." + (maxMinor + 1);
    }

    private String buildManifestJson(String baselineVer,
                                     String gitTag,
                                     ReqProject project,
                                     ReqRequirement req,
                                     ReqPrototypeVersion proto,
                                     BigDecimal totalCostDays,
                                     String operator) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return String.format(
                "{\n" +
                        "  \"baseline_version\": \"%s\",\n" +
                        "  \"git_tag\": \"%s\",\n" +
                        "  \"project_id\": %d,\n" +
                        "  \"project_key\": \"%s\",\n" +
                        "  \"project_name\": \"%s\",\n" +
                        "  \"requirement_id\": %d,\n" +
                        "  \"req_code\": \"%s\",\n" +
                        "  \"req_title\": \"%s\",\n" +
                        "  \"prototype_version\": \"%s\",\n" +
                        "  \"prototype_hash\": \"%s\",\n" +
                        "  \"total_cost_days\": %s,\n" +
                        "  \"created_by\": \"%s\",\n" +
                        "  \"created_at\": \"%s\"\n" +
                        "}",
                baselineVer,
                gitTag,
                project.getId(),
                project.getProjectKey(),
                project.getName(),
                req.getId(),
                req.getReqCode(),
                req.getTitle(),
                proto.getVersionNo(),
                proto.getFileHash(),
                totalCostDays.toPlainString(),
                operator != null ? operator : "system",
                now
        );
    }

    private void createArchiveZip(Path zipPath, String manifestJson, String manifestHash) throws IOException {
        try (OutputStream fos = Files.newOutputStream(zipPath);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            ZipEntry manifestEntry = new ZipEntry("manifest.json");
            zos.putNextEntry(manifestEntry);
            zos.write(manifestJson.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            ZipEntry hashEntry = new ZipEntry("manifest.sha256");
            zos.putNextEntry(hashEntry);
            zos.write(manifestHash.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    private void executeGitTag(String tag, String message) {
        try {
            // 在当前工作区根目录执行 git tag -a <tag> -m <message>
            ProcessBuilder pb = new ProcessBuilder("git", "tag", "-a", tag, "-m", message);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("本地代码库成功创建 Git Tag: {}", tag);
            } else {
                log.warn("执行 git tag 退出码非0 ({})，可能为重复标签或无权限", exitCode);
            }
        } catch (Exception e) {
            log.warn("自动化执行 git tag 异常 (环境无 git 或非 git 仓库): {}", e.getMessage());
        }
    }

    public static String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 计算失败", e);
        }
    }
}
