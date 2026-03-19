import { apiClient } from './client'

// ---------------------------------------------------------------------------
// DTOs — mirror agent Control API response shapes
// ---------------------------------------------------------------------------

export interface AgentStatus {
  instanceId: string
  effortId: string
  agentTypeId: string
  collaboratorName: string
  loopState: 'IDLE' | 'RUNNING' | 'PAUSED' | 'STOPPED' | 'ERROR'
  currentTurn: number
  lastActivityAt: string | null
  streamPosition: number
}

export interface AgentAckResponse {
  success: boolean
  instanceId: string
}

// ---------------------------------------------------------------------------
// API wrappers
// ---------------------------------------------------------------------------

const base = (effortId: string, agentId: string) =>
  `/api/v1/efforts/${effortId}/agents/${agentId}`

/** GET /api/v1/efforts/:effortId/agents/:agentId/status */
export async function getAgentStatus(effortId: string, agentId: string): Promise<AgentStatus> {
  const res = await apiClient.get<AgentStatus>(`${base(effortId, agentId)}/status`)
  return res.data
}

/** POST /api/v1/efforts/:effortId/agents/:agentId/pause */
export async function pauseAgent(effortId: string, agentId: string): Promise<AgentAckResponse> {
  const res = await apiClient.post<AgentAckResponse>(`${base(effortId, agentId)}/pause`)
  return res.data
}

/** POST /api/v1/efforts/:effortId/agents/:agentId/resume */
export async function resumeAgent(effortId: string, agentId: string): Promise<AgentAckResponse> {
  const res = await apiClient.post<AgentAckResponse>(`${base(effortId, agentId)}/resume`)
  return res.data
}

/** POST /api/v1/efforts/:effortId/agents/:agentId/stop */
export async function stopAgent(effortId: string, agentId: string): Promise<AgentAckResponse> {
  const res = await apiClient.post<AgentAckResponse>(`${base(effortId, agentId)}/stop`)
  return res.data
}
