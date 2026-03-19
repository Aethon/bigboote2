import { apiClient } from './client'

// ---------------------------------------------------------------------------
// DTOs — mirror coordinator DocumentDtos.kt
// ---------------------------------------------------------------------------

export interface DocumentSummary {
  docId: string
  name: string
  mimeType: string
  s3Key: string
  createdAt: string
  createdBy: string
}

export interface DocumentListResponse {
  documents: DocumentSummary[]
}

export interface DocumentDetail extends DocumentSummary {
  effortId: string
  content: string
}

export interface CreateDocumentRequest {
  name: string
  mimeType: string
  content: string
}

export interface CreateDocumentResponse {
  docId: string
  name: string
  s3Key: string
  createdAt: string
  createdBy: string
}

export interface UpdateDocumentRequest {
  content: string
}

export interface DeleteDocumentResponse {
  docId: string
  deleted: boolean
}

// ---------------------------------------------------------------------------
// API wrappers
// ---------------------------------------------------------------------------

const base = (effortId: string) => `/api/v1/efforts/${effortId}/documents`

/** GET /api/v1/efforts/:effortId/documents */
export async function listDocuments(effortId: string): Promise<DocumentListResponse> {
  const res = await apiClient.get<DocumentListResponse>(base(effortId))
  return res.data
}

/** GET /api/v1/efforts/:effortId/documents/:docId */
export async function getDocument(effortId: string, docId: string): Promise<DocumentDetail> {
  const res = await apiClient.get<DocumentDetail>(`${base(effortId)}/${docId}`)
  return res.data
}

/** POST /api/v1/efforts/:effortId/documents/create */
export async function createDocument(
  effortId: string,
  req: CreateDocumentRequest,
): Promise<CreateDocumentResponse> {
  const res = await apiClient.post<CreateDocumentResponse>(`${base(effortId)}/create`, req)
  return res.data
}

/** POST /api/v1/efforts/:effortId/documents/:docId/update */
export async function updateDocument(
  effortId: string,
  docId: string,
  req: UpdateDocumentRequest,
): Promise<DocumentSummary> {
  const res = await apiClient.post<DocumentSummary>(`${base(effortId)}/${docId}/update`, req)
  return res.data
}

/** POST /api/v1/efforts/:effortId/documents/:docId/delete */
export async function deleteDocument(
  effortId: string,
  docId: string,
): Promise<DeleteDocumentResponse> {
  const res = await apiClient.post<DeleteDocumentResponse>(`${base(effortId)}/${docId}/delete`)
  return res.data
}
