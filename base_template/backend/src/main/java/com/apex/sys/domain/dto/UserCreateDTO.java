package com.apex.sys.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 管理员开户创建用户 DTO (纯粹 Lombok 注解驱动)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "登录账号不能为空")
    private String username;

    @NotBlank(message = "真实姓名不能为空")
    private String nickname;

    private String phone;

    @NotBlank(message = "初始密码不能为空")
    private String password;

    private List<Long> roleIds;
}
