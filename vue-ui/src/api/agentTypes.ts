import { apiClient } from './client'

// ---------------------------------------------------------------------------
// DTOs — mirror coordinator AgentTypeDtos.kt
// ---------------------------------------------------------------------------

export interface AgentTypeSummary {
  agentTypeId: string
  name: string
  description: string
  dockerImage: string
  version: number
  createdAt: string
}

export interface AgentTypeListResponse {
  agentTypes: AgentTypeSummary[]
}

export interface CreateAgentTypeRequest {
  name: string
  description: string
  dockerImage: string
}

export interface CreateAgentTypeResponse {
  agentTypeId: string
  name: string
  dockerImage: string
  createdAt: string
}

export interface UpdateAgentTypeRequest {
  description?: string
  dockerImage?: string
}

export interface UpdateAgentTypeResponse {
  agentTypeId: string
  name: string
  dockerImage: string
  version: number
  updatedAt: string
}

// ---------------------------------------------------------------------------
// API wrappers
// ---------------------------------------------------------------------------

const BASE = '/api/v1/agent-types'

/** GET /api/v1/agent-types */
export async function listAgentTypes(): Promise<AgentTypeListResponse> {
  const res = await apiClient.get<AgentTypeListResponse>(BASE)
  return res.data
}

/** GET /api/v1/agent-types/:agentTypeId */
export async function getAgentType(agentTypeId: string): Promise<AgentTypeSummary> {
  const res = await apiClient.get<AgentTypeSummary>(`${BASE}/${agentTypeId}`)
  return res.data
}

/** POST /api/v1/agent-types */
export async function createAgentType(
  req: CreateAgentTypeRequest,
): Promise<CreateAgentTypeResponse> {
  const res = await apiClient.post<CreateAgentTypeResponse>(BASE, req)
  return res.data
}

/** PUT /api/v1/agent-types/:agentTypeId */
export async function updateAgentType(
  agentTypeId: string,
  req: UpdateAgentTypeRequest,
): Promise<UpdateAgentTypeResponse> {
  const res = await apiClient.put<UpdateAgentTypeResponse>(`${BASE}/${agentTypeId}`, req)
  return res.data
}
