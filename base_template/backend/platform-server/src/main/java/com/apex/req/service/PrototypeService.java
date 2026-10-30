package com.apex.req.service;

import com.apex.common.exception.BusinessException;
import com.apex.req.domain.entity.ReqPrototypeVersion;
import com.apex.req.domain.entity.ReqRequirement;
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

    @Value("${storage.prototype.path:./storage/prototypes}")
    private String prototypeStorageRoot;

    public PrototypeService(ReqRequirementMapper reqRequirementMapper) {
        this.reqRequirementMapper = reqRequirementMapper;
    }

    /**
     * 上传原型 ZIP 包，计算整体 SHA256 并生成 v(N+1) 版本快照
     *
     * @param reqId         关联需求 ID (requirement_id)
     * @param changeSummary 本次版本主要变更说明
     * @param file          上传的 ZIP 原型文件
     * @param operator      上传人
     * @return 原型版本实体
     */
    @Transactional(rollbackFor = Exception.class)
    public ReqPrototypeVersion uploadPrototype(Long reqId, String changeSummary, MultipartFile file, String operator) {
        if (reqId == null) {
            throw new BusinessException(400, "需求 ID 不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "上传的原型文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".zip")) {
            throw new BusinessException(400, "仅支持上传 ZIP 压缩格式的原型包");
        }

        ReqRequirement req = reqRequirementMapper.selectById(reqId);
        if (req == null) {
            throw new BusinessException(404, "关联需求不存在 (ID: " + reqId + ")");
        }

        // 计算下一个版本号 v(N+1)
        String nextVersion = getNextVersion(reqId);

        // 目标存储目录：storage/prototypes/REQ-{reqId}/{nextVersion}
        String relDirPath = "REQ-" + reqId + "/" + nextVersion;
        Path targetDir = Paths.get(prototypeStorageRoot, relDirPath).toAbsolutePath().normalize();
        try {
            if (Files.exists(targetDir)) {
                deleteRecursively(targetDir);
            }
            Files.createDirectories(targetDir);

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

            // 计算全目录整体 SHA256 指纹
            String fileHash = calculateDirectoryHash(targetDir);

            // 构建可直接预览的相对路径
            String previewUrl = "/static/prototypes/" + relDirPath + "/mockup.html";

            ReqPrototypeVersion prototypeVersion = ReqPrototypeVersion.builder()
                    .requirementId(reqId)
                    .versionNo(nextVersion)
                    .dirPath(relDirPath)
                    .fileHash(fileHash)
                    .previewUrl(previewUrl)
                    .changeSummary(changeSummary)
                    .uploadedBy(StringUtils.hasText(operator) ? operator : "system")
                    .uploadedAt(LocalDateTime.now())
                    .build();

            save(prototypeVersion);
            log.info("成功创建需求 [REQ-{}] 原型版本 [{}]，SHA256: {}", reqId, nextVersion, fileHash);
            return prototypeVersion;

        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("原型解压与托管处理失败: {}", e.getMessage(), e);
            throw new BusinessException(500, "原型包解压或处理异常: " + e.getMessage());
        }
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
