import { request } from './http'

export interface DocumentArtifact {
  id: string
  traceId: string
  taskId?: string
  documentId?: string
  parentId?: string
  artifactType: string
  stageCode: string
  fileName?: string
  fileExt?: string
  mimeType?: string
  fileSize?: number
  checksum?: string
  pageNo?: number
  pageRange?: string
  sortNo?: number
  status?: string
  metadataJson?: string
  previewUrl?: string
  downloadUrl?: string
  createdAt?: string
  updatedAt?: string
}

export function listTaskArtifacts(taskId: string) {
  return request<DocumentArtifact[]>(`/api/artifacts/tasks/${taskId}`)
}
