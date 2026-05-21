// 知识库服务API - 处理知识库文档管理
import type { KnowledgeDocument, Page } from './types'
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL

export async function getKnowledgeDocuments(page = 0, size = 10): Promise<Page<KnowledgeDocument>> {
  const response = await fetch(`${API_BASE_URL}/knowledge/documents?page=${page}&size=${size}`)
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  return response.json()
}

export async function getKnowledgeDocumentStatus(id: number): Promise<Partial<KnowledgeDocument>> {
  const response = await fetch(`${API_BASE_URL}/knowledge/documents/${id}`)
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  return response.json()
}

export async function deleteKnowledgeDocument(id: number): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/knowledge/documents/${id}`, {
    method: 'DELETE'
  })
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
}

export async function uploadKnowledgeDocument(fileUrl: string, fileName?: string): Promise<{ documentId: number, status: string }> {
  const formData = new FormData()
  formData.append('fileUrl', fileUrl)
  if (fileName) formData.append('fileName', fileName)

  const response = await fetch(`${API_BASE_URL}/knowledge/upload`, {
    method: 'POST',
    body: formData
  })
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  return response.json()
}
