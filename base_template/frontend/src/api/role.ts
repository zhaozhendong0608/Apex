import request from '@/utils/request'
import type { ApiResponse, MenuRouterVO, RoleVO } from '@/types'

// 1. 获取角色列表
export function getRoleListApi(): Promise<ApiResponse<RoleVO[]>> {
  return request({
    url: '/sys/roles',
    method: 'GET'
  })
}

// 2. 获取用户菜单路由树
export function getUserRoutersApi(): Promise<ApiResponse<MenuRouterVO[]>> {
  return request({
    url: '/sys/menus/routers',
    method: 'GET'
  })
}
