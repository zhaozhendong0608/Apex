import request from '@/utils/request'
import type { ApiResponse, CreateUserDTO, UserVO } from '@/types'

// 1. 管理员开户创建新用户
export function createUserApi(data: CreateUserDTO): Promise<ApiResponse<UserVO>> {
  return request({
    url: '/sys/users',
    method: 'POST',
    data
  })
}

// 2. 获取用户列表
export function getUserListApi(params?: { page?: number; size?: number; keyword?: string }): Promise<ApiResponse<UserVO[]>> {
  return request({
    url: '/sys/users',
    method: 'GET',
    params
  })
}

// 3. 启用/禁用用户状态
export function updateUserStatusApi(userId: number, status: number): Promise<ApiResponse<void>> {
  return request({
    url: `/sys/users/${userId}/status`,
    method: 'PUT',
    data: { status }
  })
}
