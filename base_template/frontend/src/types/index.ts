// 100% 绑定 DLD-SYS-PLATFORM-V1.0 API 契约与 TypeScript 类型

// 通用 HTTP 响应封装
export interface ApiResponse<T = any> {
  code: number
  msg: string
  data: T
}

// 登录请求与响应数据结构
export interface LoginDTO {
  username: string
  password?: string
}

export interface UserVO {
  userId: number
  username: string
  nickname: string
  phone?: string
  status?: number
  roles: string[]
}

export interface LoginVO {
  token: string
  tokenType: string
  expiresIn: number
  user: UserVO
}

// 用户开户创建 DTO
export interface CreateUserDTO {
  username: string
  nickname: string
  phone: string
  password?: string
  roleIds: number[]
}

// 菜单与路由节点 VO
export interface MenuRouterVO {
  menuId: number
  parentId?: number
  title: string
  path: string
  component: string
  icon?: string
  permission?: string
  children?: MenuRouterVO[]
}

// 角色 VO
export interface RoleVO {
  roleId: number
  roleName: string
  roleCode: string
  status: number
  menuIds?: number[]
}
