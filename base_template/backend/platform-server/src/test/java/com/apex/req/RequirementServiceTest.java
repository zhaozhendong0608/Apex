package com.apex.req;

import com.apex.common.exception.BusinessException;
import com.apex.req.domain.entity.ReqConfirmItem;
import com.apex.req.domain.entity.ReqRequirement;
import com.apex.req.domain.vo.CostCalculationVO;
import com.apex.req.mapper.ReqBaselineMapper;
import com.apex.req.mapper.ReqConfirmItemMapper;
import com.apex.req.mapper.ReqProjectMapper;
import com.apex.req.mapper.ReqPrototypeVersionMapper;
import com.apex.req.mapper.ReqRequirementMapper;
import com.apex.req.service.RequirementService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class RequirementServiceTest {

    private ReqConfirmItemMapper confirmItemMapper;
    private ReqPrototypeVersionMapper prototypeVersionMapper;
    private ReqProjectMapper projectMapper;
    private ReqRequirementMapper requirementMapper;
    private ReqBaselineMapper baselineMapper;
    private RequirementService requirementService;

    @BeforeEach
    public void setup() {
        confirmItemMapper = Mockito.mock(ReqConfirmItemMapper.class);
        prototypeVersionMapper = Mockito.mock(ReqPrototypeVersionMapper.class);
        projectMapper = Mockito.mock(ReqProjectMapper.class);
        requirementMapper = Mockito.mock(ReqRequirementMapper.class);
        baselineMapper = Mockito.mock(ReqBaselineMapper.class);

        requirementService = new RequirementService(confirmItemMapper, prototypeVersionMapper, projectMapper, baselineMapper);
    }

    @Test
    public void testCalculateCost() {
        Long reqId = 100L;
        List<ReqConfirmItem> items = Arrays.asList(
                ReqConfirmItem.builder().requirementId(reqId).category("SCOPE").itemCode("R-01").decision("CURRENT").costDays(new BigDecimal("1.50")).build(),
                ReqConfirmItem.builder().requirementId(reqId).category("RULE").itemCode("B-01").decision("CURRENT").costDays(new BigDecimal("2.00")).build(),
                ReqConfirmItem.builder().requirementId(reqId).category("EXCEPTION").itemCode("E-01").decision("NEXT").costDays(new BigDecimal("3.00")).build(),
                ReqConfirmItem.builder().requirementId(reqId).category("SKIP").itemCode("N-01").decision("SKIP").costDays(new BigDecimal("1.00")).build(),
                ReqConfirmItem.builder().requirementId(reqId).category("ACCEPTANCE").itemCode("A-01").decision("CURRENT").costDays(new BigDecimal("0.50")).build()
        );

        when(confirmItemMapper.selectList(any())).thenReturn(items);

        CostCalculationVO vo = requirementService.calculateCost(reqId);

        Assertions.assertNotNull(vo);
        Assertions.assertEquals(new BigDecimal("4.00"), vo.getTotalCostDays(), "本期做项代价求和必须为 4.00 人天");
        Assertions.assertEquals(3, vo.getCurrentCount(), "本期做条目数应为 3");
        Assertions.assertEquals(1, vo.getNextCount(), "下期做条目数应为 1");
        Assertions.assertEquals(1, vo.getSkipCount(), "本期不做条目数应为 1");
        Assertions.assertEquals(0, vo.getUndecidedCount(), "未决条目数应为 0");
        Assertions.assertEquals(new BigDecimal("1.50"), vo.getCostByCategory().get("SCOPE"));
        Assertions.assertEquals(new BigDecimal("2.00"), vo.getCostByCategory().get("RULE"));
        Assertions.assertEquals(new BigDecimal("0.50"), vo.getCostByCategory().get("ACCEPTANCE"));
    }
}
