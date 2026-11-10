package com.apex.req;

import com.apex.common.exception.BusinessException;
import com.apex.req.domain.dto.SignConfirmDTO;
import com.apex.req.domain.entity.ReqApproval;
import com.apex.req.domain.entity.ReqBaseline;
import com.apex.req.domain.vo.SignReceiptVO;
import com.apex.req.mapper.*;
import com.apex.req.service.ApprovalService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class ApprovalServiceTest {

    private ReqBaselineMapper baselineMapper;
    private ReqRequirementMapper requirementMapper;
    private ReqProjectMapper projectMapper;
    private ReqPrototypeVersionMapper prototypeVersionMapper;
    private ReqConfirmItemMapper confirmItemMapper;
    private ApprovalService approvalService;

    @BeforeEach
    public void setup() {
        baselineMapper = Mockito.mock(ReqBaselineMapper.class);
        requirementMapper = Mockito.mock(ReqRequirementMapper.class);
        projectMapper = Mockito.mock(ReqProjectMapper.class);
        prototypeVersionMapper = Mockito.mock(ReqPrototypeVersionMapper.class);
        confirmItemMapper = Mockito.mock(ReqConfirmItemMapper.class);

        approvalService = new ApprovalService(
                baselineMapper, requirementMapper, projectMapper, prototypeVersionMapper, confirmItemMapper
        );
    }

    @Test
    public void testGenerateReceiptCodeFormat() {
        String code = ApprovalService.generateReceiptCode();
        Assertions.assertNotNull(code);
        Assertions.assertTrue(Pattern.matches("^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$", code),
                "回执码必须满足 XXXX-XXXX-XXXX 大写防伪格式");
    }
}
