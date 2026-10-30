package com.apex.req.domain.vo;

import com.apex.req.domain.entity.ReqApproval;
import com.apex.req.domain.entity.ReqBaseline;
import com.apex.req.domain.entity.ReqPrototypeVersion;
import com.apex.req.domain.entity.ReqRequirement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 需求基线详情与存证视图 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaselineDetailVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private ReqBaseline baseline;
    private ReqRequirement requirement;
    private ReqPrototypeVersion prototypeVersion;
    private List<ReqApproval> approvals;
    private String manifestContent;
}
