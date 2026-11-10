package com.apex.req;

import com.apex.req.service.BaselineService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

public class BaselineServiceTest {

    @Test
    public void testCalculateSha256() {
        String testData = "{\"baseline\":\"BL-v1.0\"}";
        String hash1 = BaselineService.calculateSha256(testData.getBytes(StandardCharsets.UTF_8));
        String hash2 = BaselineService.calculateSha256(testData.getBytes(StandardCharsets.UTF_8));

        Assertions.assertNotNull(hash1);
        Assertions.assertEquals(64, hash1.length());
        Assertions.assertEquals(hash1, hash2, "相同 Manifest 数据的哈希必须一致");
    }
}
