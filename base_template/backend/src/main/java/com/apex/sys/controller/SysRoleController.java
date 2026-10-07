package com.apex.sys.controller;

import com.apex.sys.common.Result;
import com.apex.sys.domain.entity.SysMenu;
import com.apex.sys.domain.entity.SysRole;
import com.apex.sys.service.SysMenuService;
import com.apex.sys.service.SysRoleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色与菜单管理 Controller (全量连接真实 MySQL/H2 数据库)
 */
@RestController
@RequestMapping("/api/v1/sys")
public class SysRoleController {

    private final SysRoleService sysRoleService;
    private final SysMenuService sysMenuService;

    public SysRoleController(SysRoleService sysRoleService, SysMenuService sysMenuService) {
        this.sysRoleService = sysRoleService;
        this.sysMenuService = sysMenuService;
    }

    /**
     * 从数据库查询真实角色列表
     */
    @GetMapping("/roles")
    public Result<List<SysRole>> getRoles() {
        List<SysRole> roles = sysRoleService.getActiveRoles();
        return Result.success(roles);
    }

    /**
     * 从数据库构建真实用户的动态路由树
     */
    @GetMapping("/menus/routers")
    public Result<List<SysMenu>> getRouters() {
        List<SysMenu> menuTree = sysMenuService.getMenuTree();
        return Result.success(menuTree);
    }
}
