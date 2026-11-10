package com.apex.req;

import com.apex.req.domain.entity.ReqBaseline;
import com.apex.req.domain.entity.ReqConfirmItem;
import com.apex.req.domain.entity.ReqPrototypeVersion;
import com.apex.req.domain.vo.BaselineDiffVO;
import com.apex.req.mapper.ReqBaselineMapper;
import com.apex.req.mapper.ReqConfirmItemMapper;
import com.apex.req.mapper.ReqPrototypeVersionMapper;
import com.apex.req.service.DiffService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class DiffServiceTest {

    private ReqBaselineMapper baselineMapper;
    private ReqPrototypeVersionMapper prototypeVersionMapper;
    private ReqConfirmItemMapper confirmItemMapper;
    private DiffService diffService;

    @BeforeEach
    public void setup() {
        baselineMapper = Mockito.mock(ReqBaselineMapper.class);
        prototypeVersionMapper = Mockito.mock(ReqPrototypeVersionMapper.class);
        confirmItemMapper = Mockito.mock(ReqConfirmItemMapper.class);

        diffService = new DiffService(baselineMapper, prototypeVersionMapper, confirmItemMapper);
    }

    @Test
    public void testCalculateDiff() {
        Long srcId = 1L;
        Long tgtId = 2L;

        ReqBaseline src = ReqBaseline.builder()
                .id(srcId)
                .requirementId(10L)
                .version("BL-v1.0")
                .totalCostDays(new BigDecimal("3.50"))
                .prototypeVersionId(101L)
                .build();

        ReqBaseline tgt = ReqBaseline.builder()
                .id(tgtId)
                .requirementId(10L)
                .version("BL-v1.1")
                .totalCostDays(new BigDecimal("4.00"))
                .prototypeVersionId(102L)
                .build();

        ReqPrototypeVersion proto1 = ReqPrototypeVersion.builder()
                .id(101L)
                .versionNo("v2")
                .fileHash("hash-v2-abc")
                .build();

        ReqPrototypeVersion proto2 = ReqPrototypeVersion.builder()
                .id(102L)
                .versionNo("v3")
                .fileHash("hash-v3-xyz")
                .build();

        when(baselineMapper.selectById(srcId)).thenReturn(src);
        when(baselineMapper.selectById(tgtId)).thenReturn(tgt);
        when(prototypeVersionMapper.selectById(101L)).thenReturn(proto1);
        when(prototypeVersionMapper.selectById(102L)).thenReturn(proto2);
        when(confirmItemMapper.selectList(any())).thenReturn(Collections.emptyList());

        BaselineDiffVO diff = diffService.calculateDiff(srcId, tgtId);

        Assertions.assertNotNull(diff);
        Assertions.assertEquals("BL-v1.0", diff.getSourceVersion());
        Assertions.assertEquals("BL-v1.1", diff.getTargetVersion());
        Assertions.assertEquals(new BigDecimal("0.50"), diff.getNetCostGrowth(), "净人天代价增量应为 0.50 人天");
        Assertions.assertTrue(diff.getPrototypeDiff().getHasChanged(), "原型哈希不同必须标记变更");
        Assertions.assertEquals("v2", diff.getPrototypeDiff().getOldVersion());
        Assertions.assertEquals("v3", diff.getPrototypeDiff().getNewVersion());
    }
}
