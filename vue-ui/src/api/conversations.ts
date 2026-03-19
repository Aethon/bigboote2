import { apiClient } from './client'

// ---------------------------------------------------------------------------
// DTOs — mirror coordinator ConversationDtos.kt
// ---------------------------------------------------------------------------

export interface ConversationSummary {
  convId: string
  convName: string
  members: string[]
  createdAt: string
}

export interface ConversationListResponse {
  conversations: ConversationSummary[]
}

export interface MessageResponse {
  messageId: string
  from: string
  body: string
  postedAt: string
}

export interface MessageListResponse {
  convId: string
  messages: MessageResponse[]
  from: number
  limit: number
}

export interface CreateChannelRequest {
  name: string
  members: string[]
}

export interface CreateChannelResponse {
  convId: string
  convName: string
  members: string[]
  createdAt: string
}

export interface AddMemberRequest {
  member: string
}

export interface AddMemberResponse {
  convId: string
  member: string
}

// ---------------------------------------------------------------------------
// API wrappers
// ---------------------------------------------------------------------------

const base = (effortId: string) => `/api/v1/efforts/${effortId}/conversations`

/** GET /api/v1/efforts/:effortId/conversations */
export async function listConversations(effortId: string): Promise<ConversationListResponse> {
  const res = await apiClient.get<ConversationListResponse>(base(effortId))
  return res.data
}

/** GET /api/v1/efforts/:effortId/conversations/:convId/messages */
export async function getMessages(
  effortId: string,
  convId: string,
  from = 0,
  limit = 50,
): Promise<MessageListResponse> {
  const res = await apiClient.get<MessageListResponse>(
    `${base(effortId)}/${encodeURIComponent(convId)}/messages`,
    { params: { from, limit } },
  )
  return res.data
}

/** POST /api/v1/efforts/:effortId/conversations/create-channel */
export async function createChannel(
  effortId: string,
  req: CreateChannelRequest,
): Promise<CreateChannelResponse> {
  const res = await apiClient.post<CreateChannelResponse>(`${base(effortId)}/create-channel`, req)
  return res.data
}

/** POST /api/v1/efforts/:effortId/conversations/:convId/members */
export async function addMember(
  effortId: string,
  convId: string,
  req: AddMemberRequest,
): Promise<AddMemberResponse> {
  const res = await apiClient.post<AddMemberResponse>(
    `${base(effortId)}/${encodeURIComponent(convId)}/members`,
    req,
  )
  return res.data
}
