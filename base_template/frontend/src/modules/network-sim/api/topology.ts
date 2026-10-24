import request from '@/utils/request'
import type { SimResultDTO } from '../types'

export interface NetTopologyItem {
  id: number
  name: string
  description?: string
  canvasData?: string
  nodeCount: number
  edgeCount: number
  createdAt: string
  updatedAt: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

export interface TopologySaveForm {
  id?: number
  name: string
  description?: string
  canvasData?: string
  nodeCount?: number
  edgeCount?: number
}

export interface SimRequestParams {
  topologyId: number
  sourceNodeId: string
  targetNodeId: string
  destPort?: number
  protocol?: string
}

/**
 * 分页查询拓扑方案列表
 */
export const getTopologyPageApi = (params: { pageNo: number; pageSize: number; keyword?: string }) => {
  return request.get<any, { code: number; data: PageResult<NetTopologyItem> }>('/network-sim/topology/page', { params })
}

/**
 * 获取拓扑详情 (读取画布 JSON)
 */
export const getTopologyDetailApi = (id: number) => {
  return request.get<any, { code: number; data: NetTopologyItem }>(`/network-sim/topology/${id}`)
}

/**
 * 保存或修改拓扑方案
 */
export const saveTopologyApi = (data: TopologySaveForm) => {
  return request.post<any, { code: number; data: NetTopologyItem; message: string }>('/network-sim/topology', data)
}

/**
 * 删除拓扑方案
 */
export const deleteTopologyApi = (id: number) => {
  return request.delete<any, { code: number; message: string }>(`/network-sim/topology/${id}`)
}

/**
 * 执行端到端仿真寻路与连通性测试
 */
export const runSimulationApi = (data: SimRequestParams) => {
  return request.post<any, { code: number; data: SimResultDTO; message: string }>('/network-sim/simulate', data)
}
