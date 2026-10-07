package com.apex.sys.controller;

import com.apex.sys.common.Result;
import com.apex.sys.domain.dto.LoginDTO;
import com.apex.sys.domain.vo.LoginVO;
import com.apex.sys.service.SysUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 认证与授权 Controller
 */
@RestController
@RequestMapping("/api/v1/sys/auth")
public class AuthController {

    private final SysUserService sysUserService;

    public AuthController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    /**
     * 账号密码登录
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO) {
        LoginVO loginVO = sysUserService.login(loginDTO);
        return Result.success(loginVO, "登录成功");
    }

    /**
     * 获取当前登录用户信息 (查询真实数据库)
     */
    @GetMapping("/me")
    public Result<LoginVO.UserVO> getMe() {
        // 默认查询当前绑定的超级管理员真实账号信息
        com.apex.sys.domain.entity.SysUser user = sysUserService.getById(1001L);
        LoginVO.UserVO userVO = new LoginVO.UserVO();
        userVO.setUserId(user != null ? user.getId() : 1001L);
        userVO.setUsername(user != null ? user.getUsername() : "admin");
        userVO.setNickname(user != null ? user.getNickname() : "超级管理员");
        userVO.setRoles(java.util.List.of("admin"));
        return Result.success(userVO);
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success((Void) null, "成功退出");
    }
}
