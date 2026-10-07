package com.apex.sys.controller;

import com.apex.sys.common.Result;
import com.apex.sys.domain.dto.UserCreateDTO;
import com.apex.sys.domain.entity.SysUser;
import com.apex.sys.service.SysUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户管理与管理员统一开户 Controller
 */
@RestController
@RequestMapping("/api/v1/sys/users")
public class SysUserController {

    private final SysUserService sysUserService;

    public SysUserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    /**
     * 管理员统一开户
     */
    @PostMapping
    public Result<SysUser> createUser(@Valid @RequestBody UserCreateDTO dto) {
        SysUser user = sysUserService.createUser(dto);
        return Result.success(user, "用户账号开户成功");
    }

    /**
     * 获取用户列表
     */
    @GetMapping
    public Result<List<SysUser>> getUserList() {
        List<SysUser> list = sysUserService.list();
        return Result.success(list);
    }

    /**
     * 禁用/启用用户状态
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable("id") Long id, @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        SysUser user = sysUserService.getById(id);
        if (user != null && status != null) {
            user.setStatus(status);
            sysUserService.updateById(user);
        }
        return Result.success((Void) null, "状态更新成功");
    }
}
