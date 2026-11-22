package com.apex.req.service;

import com.apex.common.exception.BusinessException;
import com.apex.req.domain.entity.ReqProject;
import com.apex.req.domain.entity.ReqPrototypeVersion;
import com.apex.req.domain.entity.ReqRequirement;
import com.apex.req.mapper.ReqProjectMapper;
import com.apex.req.mapper.ReqPrototypeVersionMapper;
import com.apex.req.mapper.ReqRequirementMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 原型沙盒托管与 SHA256 指纹快照服务 (绑定 DLD §3.1 & service.sql)
 */
@Slf4j
@Service
public class PrototypeService extends ServiceImpl<ReqPrototypeVersionMapper, ReqPrototypeVersion> {

    private final ReqRequirementMapper reqRequirementMapper;
    private final ReqProjectMapper reqProjectMapper;

    @Value("${storage.prototype.path:./storage/prototypes}")
    private String prototypeStorageRoot;

    public PrototypeService(ReqRequirementMapper reqRequirementMapper, ReqProjectMapper reqProjectMapper) {
        this.reqRequirementMapper = reqRequirementMapper;
        this.reqProjectMapper = reqProjectMapper;
    }

    /**
     * 上传原型文件（以项目为维度存储，同时兼容需求粒度），计算整体 SHA256 并生成 v(N+1) 版本快照
     *
     * @param projectId     关联项目 ID (可传，若未传则从 reqId 推导)
     * @param reqId         关联需求 ID (可选，若未传则绑定该项目首个需求或保留0)
     * @param changeSummary 本次版本主要变更说明
     * @param file          上传的 ZIP 原型包或 HTML5 文件
     * @param operator      上传人
     * @return 原型版本实体
     */
    @Transactional(rollbackFor = Exception.class)
    public ReqPrototypeVersion uploadPrototype(Long projectId, Long reqId, String changeSummary, MultipartFile file, String operator) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "上传的原型文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new BusinessException(400, "文件名不能为空");
        }
        String lowerName = originalFilename.toLowerCase();
        boolean isZip = lowerName.endsWith(".zip");
        boolean isHtml = lowerName.endsWith(".html") || lowerName.endsWith(".htm");

        if (!isZip && !isHtml) {
            throw new BusinessException(400, "仅支持上传 ZIP 压缩包或单文件 HTML5 原型 (.html, .htm)");
        }

        // 推导项目与需求实体
        ReqProject project = null;
        ReqRequirement req = null;

        if (projectId != null) {
            project = reqProjectMapper.selectById(projectId);
        }

        if (reqId != null && reqId > 0) {
            req = reqRequirementMapper.selectById(reqId);
            if (req != null && project == null) {
                project = reqProjectMapper.selectById(req.getProjectId());
            }
        }

        // 如果传了 projectId 但没传有效 reqId，尝试取该项目下的需求兜底关联
        if (project != null && req == null) {
            List<ReqRequirement> reqList = reqRequirementMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReqRequirement>()
                            .eq(ReqRequirement::getProjectId, project.getId())
                            .orderByAsc(ReqRequirement::getId)
            );
            if (!reqList.isEmpty()) {
                req = reqList.get(0);
            }
        }

        if (project == null && req == null) {
            throw new BusinessException(400, "上传原型必须指定所属项目 ID (projectId) 或需求 ID (reqId)");
        }

        String projectKey = project != null && StringUtils.hasText(project.getProjectKey())
                ? project.getProjectKey().trim().toUpperCase()
                : (req != null ? "PROJ-" + req.getProjectId() : "DEFAULT");

        Long effectiveReqId = req != null ? req.getId() : (project != null ? project.getId() : 0L);

        // 计算下一个版本号 v(N+1)：以项目维度版本计数
        String nextVersion = getNextVersionByProject(project != null ? project.getId() : null, effectiveReqId);

        // 目标存储物理目录：以项目为维度 storage/prototypes/{PROJECT_KEY}/{nextVersion}
        String relDirPath = projectKey + "/" + nextVersion;
        Path targetDir = Paths.get(prototypeStorageRoot, relDirPath).toAbsolutePath().normalize();
        try {
            if (Files.exists(targetDir)) {
                deleteRecursively(targetDir);
            }
            Files.createDirectories(targetDir);

            if (isZip) {
                // 解压 ZIP 文件 (防御 Zip-Slip 目录穿透攻击)
                extractZip(file.getInputStream(), targetDir);

                // 检查入口文件 (必须存在 entry_file: mockup.html)
                Path entryFilePath = targetDir.resolve("mockup.html");
                if (!Files.exists(entryFilePath)) {
                    // 如果解压后只有一层根目录，检查子目录下是否有 mockup.html
                    Path found = findMockupHtml(targetDir);
                    if (found != null && !found.equals(entryFilePath)) {
                        // 提升文件到目标根目录
                        flattenDirectory(found.getParent(), targetDir);
                    }
                }

                if (!Files.exists(targetDir.resolve("mockup.html"))) {
                    deleteRecursively(targetDir);
                    throw new BusinessException(400202, "原型包静态相对路径异常：根目录缺失 entry_file (mockup.html)");
                }
            } else {
                // 单个 HTML5 文件：直接保存为目标目录的 mockup.html 作为标准入口
                Path targetHtmlFile = targetDir.resolve("mockup.html");
                file.transferTo(targetHtmlFile.toFile());
            }

            // 计算全目录整体 SHA256 指纹
            String fileHash = calculateDirectoryHash(targetDir);

            // 构建可直接预览的相对路径
            String previewUrl = "/static/prototypes/" + relDirPath + "/mockup.html";

            ReqPrototypeVersion prototypeVersion = ReqPrototypeVersion.builder()
                    .requirementId(effectiveReqId)
                    .versionNo(nextVersion)
                    .dirPath(relDirPath)
                    .fileHash(fileHash)
                    .previewUrl(previewUrl)
                    .changeSummary(changeSummary)
                    .uploadedBy(StringUtils.hasText(operator) ? operator : "system")
                    .uploadedAt(LocalDateTime.now())
                    .build();

            save(prototypeVersion);
            log.info("成功创建项目 [{}] 原型版本 [{}]，SHA256: {}，路径: {}", projectKey, nextVersion, fileHash, relDirPath);
            return prototypeVersion;

        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("原型解压与托管处理失败: {}", e.getMessage(), e);
            throw new BusinessException(500, "原型文件托管或处理异常: " + e.getMessage());
        }
    }

    /**
     * 重载兼容历史接口：按 reqId 上传
     */
    @Transactional(rollbackFor = Exception.class)
    public ReqPrototypeVersion uploadPrototype(Long reqId, String changeSummary, MultipartFile file, String operator) {
        return uploadPrototype(null, reqId, changeSummary, file, operator);
    }

    /**
     * 查询指定项目的所有原型版本（汇聚该项目下的所有需求原型或以项目路径开头的版本）
     */
    public List<ReqPrototypeVersion> listByProjectId(Long projectId) {
        if (projectId == null) {
            return Collections.emptyList();
        }
        ReqProject project = reqProjectMapper.selectById(projectId);
        String projectKey = project != null ? project.getProjectKey().trim().toUpperCase() : null;

        List<ReqRequirement> reqs = reqRequirementMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReqRequirement>()
                        .eq(ReqRequirement::getProjectId, projectId)
        );
        List<Long> reqIds = reqs.stream().map(ReqRequirement::getId).collect(Collectors.toList());

        return lambdaQuery()
                .and(w -> {
                    if (!reqIds.isEmpty()) {
                        w.in(ReqPrototypeVersion::getRequirementId, reqIds);
                    }
                    if (StringUtils.hasText(projectKey)) {
                        if (!reqIds.isEmpty()) {
                            w.or();
                        }
                        w.likeRight(ReqPrototypeVersion::getDirPath, projectKey + "/");
                    }
                })
                .orderByDesc(ReqPrototypeVersion::getId)
                .list();
    }

    /**
     * 获取指定项目的最新原型版本
     */
    public ReqPrototypeVersion getLatestByProjectId(Long projectId) {
        List<ReqPrototypeVersion> list = listByProjectId(projectId);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 计算项目维度的下一个版本号 (如 v1 -> v2, 默认从 v1 开始)
     */
    public String getNextVersionByProject(Long projectId, Long reqId) {
        List<ReqPrototypeVersion> versions = new ArrayList<>();
        if (projectId != null) {
            versions = listByProjectId(projectId);
        } else if (reqId != null) {
            versions = listByReqId(reqId);
        }

        if (versions.isEmpty()) {
            return "v1";
        }
        int maxVer = 0;
        Pattern pattern = Pattern.compile("^v(\\d+)$", Pattern.CASE_INSENSITIVE);
        for (ReqPrototypeVersion v : versions) {
            if (v.getVersionNo() != null) {
                Matcher matcher = pattern.matcher(v.getVersionNo().trim());
                if (matcher.matches()) {
                    try {
                        int num = Integer.parseInt(matcher.group(1));
                        if (num > maxVer) {
                            maxVer = num;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return "v" + (maxVer + 1);
    }

    /**
     * 查询指定需求的所有原型版本
     */
    public List<ReqPrototypeVersion> listByReqId(Long reqId) {
        return lambdaQuery()
                .eq(ReqPrototypeVersion::getRequirementId, reqId)
                .orderByDesc(ReqPrototypeVersion::getId)
                .list();
    }

    /**
     * 获取指定需求的最新原型版本
     */
    public ReqPrototypeVersion getLatestByReqId(Long reqId) {
        return lambdaQuery()
                .eq(ReqPrototypeVersion::getRequirementId, reqId)
                .orderByDesc(ReqPrototypeVersion::getId)
                .last("LIMIT 1")
                .one();
    }

    /**
     * 计算下一个版本号 (如 v1 -> v2, 默认从 v1 开始)
     */
    public String getNextVersion(Long reqId) {
        List<ReqPrototypeVersion> versions = lambdaQuery()
                .eq(ReqPrototypeVersion::getRequirementId, reqId)
                .list();
        if (versions.isEmpty()) {
            return "v1";
        }
        int maxVer = 0;
        Pattern pattern = Pattern.compile("^v(\\d+)$", Pattern.CASE_INSENSITIVE);
        for (ReqPrototypeVersion v : versions) {
            if (v.getVersionNo() != null) {
                Matcher matcher = pattern.matcher(v.getVersionNo().trim());
                if (matcher.matches()) {
                    try {
                        int num = Integer.parseInt(matcher.group(1));
                        if (num > maxVer) {
                            maxVer = num;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return "v" + (maxVer + 1);
    }

    /**
     * 计算目录内所有文件的确定性组合 SHA-256 哈希值
     * 规则：将相对路径字典序排序，依次把 "相对路径:文件字节" 写入 SHA-256 摘要
     */
    public static String calculateDirectoryHash(Path directory) throws IOException, NoSuchAlgorithmException {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            throw new IllegalArgumentException("指定路径不存在或非目录: " + directory);
        }

        List<Path> fileList;
        try (Stream<Path> stream = Files.walk(directory)) {
            fileList = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .filter(p -> !p.toString().contains("__MACOSX"))
                    .sorted(Comparator.comparing(p -> directory.relativize(p).toString().replace('\\', '/')))
                    .collect(Collectors.toList());
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];

        for (Path file : fileList) {
            String relPath = directory.relativize(file).toString().replace('\\', '/');
            digest.update(relPath.getBytes("UTF-8"));
            digest.update((byte) 0x00); // 分隔符

            try (InputStream is = Files.newInputStream(file)) {
                int read;
                while ((read = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
        }

        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 安全解压 ZIP 文件（杜绝 Zip-Slip 目录穿透漏洞）
     */
    private void extractZip(InputStream inputStream, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().startsWith("__MACOSX") || entry.getName().endsWith(".DS_Store")) {
                    zis.closeEntry();
                    continue;
                }
                Path resolvedPath = targetDir.resolve(entry.getName()).normalize();
                if (!resolvedPath.startsWith(targetDir)) {
                    throw new SecurityException("潜在的目录穿越漏洞攻击 (Zip-Slip): " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(resolvedPath);
                } else {
                    Path parent = resolvedPath.getParent();
                    if (parent != null && !Files.exists(parent)) {
                        Files.createDirectories(parent);
                    }
                    try (OutputStream os = Files.newOutputStream(resolvedPath)) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            os.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private Path findMockupHtml(Path targetDir) throws IOException {
        try (Stream<Path> stream = Files.walk(targetDir, 3)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> "mockup.html".equalsIgnoreCase(p.getFileName().toString()))
                    .findFirst()
                    .orElse(null);
        }
    }

    private void flattenDirectory(Path sourceDir, Path targetDir) throws IOException {
        if (sourceDir.equals(targetDir)) return;
        try (Stream<Path> stream = Files.list(sourceDir)) {
            for (Path p : stream.collect(Collectors.toList())) {
                Path dest = targetDir.resolve(p.getFileName());
                Files.move(p, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }
}
