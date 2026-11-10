package com.apex.req;

import com.apex.common.exception.BusinessException;
import com.apex.req.service.PrototypeService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class PrototypeServiceTest {

    @Test
    public void testCalculateDirectoryHashDeterministic(@TempDir Path tempDir) throws IOException, NoSuchAlgorithmException {
        // 创建测试文件目录结构
        Path subDir = tempDir.resolve("assets");
        Files.createDirectories(subDir);
        Files.write(tempDir.resolve("mockup.html"), "<html><body>Demo</body></html>".getBytes(StandardCharsets.UTF_8));
        Files.write(subDir.resolve("style.css"), "body { color: red; }".getBytes(StandardCharsets.UTF_8));

        String hash1 = PrototypeService.calculateDirectoryHash(tempDir);
        String hash2 = PrototypeService.calculateDirectoryHash(tempDir);

        Assertions.assertNotNull(hash1);
        Assertions.assertEquals(64, hash1.length());
        Assertions.assertEquals(hash1, hash2, "相同内容目录计算的 SHA256 必须严格确定性一致");

        // 修改内容后哈希必须改变
        Files.write(subDir.resolve("style.css"), "body { color: blue; }".getBytes(StandardCharsets.UTF_8));
        String hash3 = PrototypeService.calculateDirectoryHash(tempDir);
        Assertions.assertNotEquals(hash1, hash3, "修改文件内容后 SHA256 必须发生变更");
    }

    public static byte[] createSampleZip(boolean includeMockupHtml) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            if (includeMockupHtml) {
                ZipEntry entry1 = new ZipEntry("mockup.html");
                zos.putNextEntry(entry1);
                zos.write("<html><body><h1>Prototype Test</h1></body></html>".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            ZipEntry entry2 = new ZipEntry("js/app.js");
            zos.putNextEntry(entry2);
            zos.write("console.log('loaded');".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
