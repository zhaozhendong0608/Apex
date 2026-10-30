import request from '@/utils/request'
import type { ApiResponse } from '@/types'

// 0. 项目空间与工作台统计
export function getProjectListApi(): Promise<ApiResponse<any[]>> {
  return request({
    url: '/req/projects',
    method: 'GET'
  })
}

export function getProjectDetailApi(id: number): Promise<ApiResponse<any>> {
  return request({
    url: `/req/projects/${id}`,
    method: 'GET'
  })
}

export function getProjectByKeyApi(key: string): Promise<ApiResponse<any>> {
  return request({
    url: `/req/projects/by-key/${key}`,
    method: 'GET'
  })
}

export function getProjectStatsApi(id: number): Promise<ApiResponse<any>> {
  return request({
    url: `/req/projects/${id}/stats`,
    method: 'GET'
  })
}

export function createProjectApi(data: any): Promise<ApiResponse<any>> {
  return request({
    url: '/req/projects',
    method: 'POST',
    data
  })
}

export function deleteProjectApi(id: number): Promise<ApiResponse<void>> {
  return request({
    url: `/req/projects/${id}`,
    method: 'DELETE'
  })
}

// 1. 需求与四层清单管理
export function createRequirementApi(data: any): Promise<ApiResponse<any>> {
  return request({
    url: '/req/requirements',
    method: 'POST',
    data
  })
}

export function getRequirementListApi(params?: { projectId?: number; status?: string }): Promise<ApiResponse<any[]>> {
  return request({
    url: '/req/requirements',
    method: 'GET',
    params
  })
}

export function getRequirementDetailApi(id: number): Promise<ApiResponse<any>> {
  return request({
    url: `/req/requirements/${id}`,
    method: 'GET'
  })
}

export function saveConfirmItemsApi(id: number, items: any[]): Promise<ApiResponse<any>> {
  return request({
    url: `/req/requirements/${id}/confirm-items`,
    method: 'PUT',
    data: { items }
  })
}

export function getCostCalculationApi(id: number): Promise<ApiResponse<any>> {
  return request({
    url: `/req/requirements/${id}/cost`,
    method: 'GET'
  })
}

export function checkGateApi(id: number): Promise<ApiResponse<void>> {
  return request({
    url: `/req/requirements/${id}/gate-check`,
    method: 'GET'
  })
}

export function unfreezeRequirementApi(id: number): Promise<ApiResponse<any>> {
  return request({
    url: `/req/requirements/${id}/unfreeze`,
    method: 'POST'
  })
}

// 2. 原型沙盒托管与版本快照
export function uploadPrototypeApi(formData: FormData): Promise<ApiResponse<any>> {
  return request({
    url: '/req/prototypes/upload',
    method: 'POST',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function getPrototypeListApi(reqId: number): Promise<ApiResponse<any[]>> {
  return request({
    url: `/req/prototypes/${reqId}`,
    method: 'GET'
  })
}

export function getLatestPrototypeApi(reqId: number): Promise<ApiResponse<any>> {
  return request({
    url: `/req/prototypes/${reqId}/latest`,
    method: 'GET'
  })
}

// 3. 需求基线与三合一差分
export function createBaselineApi(data: any): Promise<ApiResponse<any>> {
  return request({
    url: '/req/baselines',
    method: 'POST',
    data
  })
}

export function getBaselineListApi(requirementId: number): Promise<ApiResponse<any[]>> {
  return request({
    url: '/req/baselines',
    method: 'GET',
    params: { requirementId }
  })
}

export function getBaselineDetailApi(id: number): Promise<ApiResponse<any>> {
  return request({
    url: `/req/baselines/${id}`,
    method: 'GET'
  })
}

export function getBaselineDiffApi(id: number, targetId?: number): Promise<ApiResponse<any>> {
  return request({
    url: `/req/baselines/${id}/diff`,
    method: 'GET',
    params: { target_id: targetId }
  })
}

// 4. 客户免登签署与回执 (公开接口)
export function getSignContextApi(token: string, code: string): Promise<ApiResponse<any>> {
  return request({
    url: `/public/sign/${token}`,
    method: 'GET',
    params: { code }
  })
}

export function confirmSignApi(token: string, data: any): Promise<ApiResponse<any>> {
  return request({
    url: `/public/sign/${token}/confirm`,
    method: 'POST',
    data
  })
}

export function rejectSignApi(token: string, data: any): Promise<ApiResponse<any>> {
  return request({
    url: `/public/sign/${token}/reject`,
    method: 'POST',
    data
  })
}
