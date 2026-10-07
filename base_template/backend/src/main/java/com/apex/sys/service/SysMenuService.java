package com.apex.sys.service;

import com.apex.sys.domain.entity.SysMenu;
import com.apex.sys.mapper.SysMenuMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SysMenuService extends ServiceImpl<SysMenuMapper, SysMenu> {

    /**
     * 从数据库构建动态菜单路由树
     */
    public List<SysMenu> getMenuTree() {
        List<SysMenu> allMenus = lambdaQuery().orderByAsc(SysMenu::getSort).list();
        List<SysMenu> rootMenus = new ArrayList<>();
        for (SysMenu menu : allMenus) {
            if (menu.getParentId() == null || menu.getParentId() == 0L) {
                menu.setChildren(findChildren(menu.getId(), allMenus));
                rootMenus.add(menu);
            }
        }
        return rootMenus;
    }

    private List<SysMenu> findChildren(Long parentId, List<SysMenu> allMenus) {
        List<SysMenu> children = new ArrayList<>();
        for (SysMenu menu : allMenus) {
            if (parentId.equals(menu.getParentId())) {
                menu.setChildren(findChildren(menu.getId(), allMenus));
                children.add(menu);
            }
        }
        return children;
    }
}
