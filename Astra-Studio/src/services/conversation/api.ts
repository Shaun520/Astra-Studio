// 会话服务API - 处理会话的增删改查
import type { ConversationItem, MessageItem, PageResult, CreateConversationRequest, UpdateTitleRequest } from '@/types/conversation'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL

export async function createConversation(request?: CreateConversationRequest): Promise<ConversationItem> {
  const response = await fetch(`${API_BASE_URL}/conversation`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: request ? JSON.stringify(request) : undefined,
  })
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  return response.json()
}

export async function getConversations(page = 0, size = 20, keyword = ''): Promise<PageResult<ConversationItem>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (keyword.trim()) params.set('keyword', keyword.trim())
  const response = await fetch(`${API_BASE_URL}/conversation?${params.toString()}`)
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  return response.json()
}

export async function getConversation(memoryId: string): Promise<ConversationItem> {
  const response = await fetch(`${API_BASE_URL}/conversation/${memoryId}`)
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  return response.json()
}

export async function getConversationMessages(
  memoryId: string,
  page = 0,
  size = 50,
  role?: string
): Promise<PageResult<MessageItem>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (role?.trim()) params.set('role', role.trim())
  const response = await fetch(`${API_BASE_URL}/conversation/${memoryId}/messages?${params.toString()}`)
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  return response.json()
}

export async function updateConversationTitle(memoryId: string, title: string): Promise<void> {
  const body: UpdateTitleRequest = { title }
  const response = await fetch(`${API_BASE_URL}/conversation/${memoryId}/title`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
}

export async function deleteConversation(memoryId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/conversation/${memoryId}`, {
    method: 'DELETE',
  })
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
}
