import request from '@/utils/request'
import type { ApiResponse, LoginDTO, LoginVO, UserVO } from '@/types'

// 1. 账号密码登录
export function loginApi(data: LoginDTO): Promise<ApiResponse<LoginVO>> {
  return request({
    url: '/sys/auth/login',
    method: 'POST',
    data
  })
}

// 2. 获取当前登录用户信息
export function getMeApi(): Promise<ApiResponse<UserVO>> {
  return request({
    url: '/sys/auth/me',
    method: 'GET'
  })
}

// 3. 退出登录
export function logoutApi(): Promise<ApiResponse<void>> {
  return request({
    url: '/sys/auth/logout',
    method: 'POST'
  })
}
