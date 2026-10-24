package com.apex.sys.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 登录成功响应 VO (纯粹 Lombok 注解驱动)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String token;

    @Builder.Default
    private String tokenType = "Bearer";

    @Builder.Default
    private Long expiresIn = 7200L;

    private UserVO user;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserVO implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long userId;
        private String username;
        private String nickname;
        private List<String> roles;
    }
}
