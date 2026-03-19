import { apiClient } from './client'

// ---------------------------------------------------------------------------
// DTOs — mirror coordinator api/public/v1/dto/EffortDtos.kt
// ---------------------------------------------------------------------------

export interface CollaboratorSpec {
  name: string          // e.g. "@alice" or "#review"
  type: 'HUMAN' | 'AGENT'
  agentTypeId?: string  // required when type === 'AGENT'
  isLead?: boolean
}

export interface CreateEffortRequest {
  name: string
  goal: string
  leadName: string           // e.g. "@alice"
  collaborators: CollaboratorSpec[]
}

export interface CreateEffortResponse {
  effortId: string
  name: string
  createdAt: string
}

export interface EffortSummary {
  effortId: string
  name: string
  goal: string
  status: 'created' | 'active' | 'paused' | 'closed'
  leadName: string
  collaboratorCount: number
  createdAt: string
}

export interface EffortListResponse {
  efforts: EffortSummary[]
}

export interface EffortDetail extends EffortSummary {
  collaborators: CollaboratorSpec[]
}

// ---------------------------------------------------------------------------
// API wrappers
// ---------------------------------------------------------------------------

const BASE = '/api/v1/efforts'

/** POST /api/v1/efforts/create — create a new effort */
export async function createEffort(req: CreateEffortRequest): Promise<CreateEffortResponse> {
  const res = await apiClient.post<CreateEffortResponse>(`${BASE}/create`, req)
  return res.data
}

/** GET /api/v1/efforts — list all efforts */
export async function listEfforts(): Promise<EffortListResponse> {
  const res = await apiClient.get<EffortListResponse>(BASE)
  return res.data
}

/** GET /api/v1/efforts/:effortId — get effort detail */
export async function getEffort(effortId: string): Promise<EffortDetail> {
  const res = await apiClient.get<EffortDetail>(`${BASE}/${effortId}`)
  return res.data
}

/** POST /api/v1/efforts/:effortId/start */
export async function startEffort(effortId: string): Promise<void> {
  await apiClient.post(`${BASE}/${effortId}/start`)
}

/** POST /api/v1/efforts/:effortId/pause */
export async function pauseEffort(effortId: string): Promise<void> {
  await apiClient.post(`${BASE}/${effortId}/pause`)
}

/** POST /api/v1/efforts/:effortId/resume */
export async function resumeEffort(effortId: string): Promise<void> {
  await apiClient.post(`${BASE}/${effortId}/resume`)
}

/** POST /api/v1/efforts/:effortId/close */
export async function closeEffort(effortId: string): Promise<void> {
  await apiClient.post(`${BASE}/${effortId}/close`)
}
