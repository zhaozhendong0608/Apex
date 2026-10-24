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

export interface RouteEntryDTO {
  id: string
  destSubnet: string
  mask: string
  nextHopIp: string
  description?: string
}

export interface NatRuleDTO {
  id: string
  type: 'SNAT' | 'DNAT'
  origSrc?: string
  transSrc?: string
  extIp?: string
  extPort?: number
  intIp?: string
  intPort?: number
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
  gateway?: string
  acls?: AclRuleDTO[]
  routes?: RouteEntryDTO[]
  natRules?: NatRuleDTO[]
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
