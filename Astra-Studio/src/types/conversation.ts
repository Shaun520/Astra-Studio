export interface ConversationItem {
  id: number
  memoryId: string
  title: string
  lastMessagePreview: string
  modelName: string
  messageCount: number
  status: number
  updatedAt: string
}

export interface MessageItem {
  id: number
  role: 'user' | 'assistant' | 'system'
  content: string
  thinkingContent?: string | null
  attachments?: string[] | null
  sequenceNum: number
  timestamp: string
}

export interface PageResult<T> {
  content: T[]
  totalElements: number
  totalPages: number
  currentPage: number
  size: number
}

export interface CreateConversationRequest {
  memoryId?: string
  modelName?: string
}

export interface UpdateTitleRequest {
  title: string
}
