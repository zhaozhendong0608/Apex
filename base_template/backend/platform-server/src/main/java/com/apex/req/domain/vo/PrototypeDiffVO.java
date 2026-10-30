package com.apex.req.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 原型版本与指纹差异对比 VO (绑定 DLD §3.2)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrototypeDiffVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String oldVersion;
    private String oldHash;
    private String newVersion;
    private String newHash;
    private Boolean hasChanged;
}
