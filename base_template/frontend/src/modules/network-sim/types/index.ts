export interface AclRuleDTO {
  id: string
  priority: number
  action: 'PERMIT' | 'DENY'
  srcIp?: string
  destIp?: string
  destPort?: number
  protocol?: string
  description?: string
}

export interface TopoNodeDTO {
  id: string
  name: string
  type: string
  zone?: string
  ip: string
  subnet?: string
  acls?: AclRuleDTO[]
}

export interface TopoEdgeDTO {
  id: string
  sourceNodeId: string
  targetNodeId: string
  bandwidth?: string
  latencyMs?: number
}

export interface SimResultDTO {
  reachable: boolean
  blocked: boolean
  blockedNodeId?: string
  blockedRuleId?: string
  nodePath: string[]
  hops: number
  totalLatencyMs: number
  logs: string[]
}
