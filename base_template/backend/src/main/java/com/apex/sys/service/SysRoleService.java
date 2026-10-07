package com.apex.sys.service;

import com.apex.sys.domain.entity.SysRole;
import com.apex.sys.mapper.SysRoleMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SysRoleService extends ServiceImpl<SysRoleMapper, SysRole> {

    public List<SysRole> getActiveRoles() {
        return lambdaQuery().eq(SysRole::getStatus, 1).orderByAsc(SysRole::getSort).list();
    }
}
