// 知识库服务类型定义
export interface KnowledgeDocument {
  id: number
  filename: string
  fileType: string
  fileUrl: string
  fileSize: number
  chunkCount: number
  status: 'PROCESSING' | 'READY' | 'FAILED'
  errorMessage?: string
  createdAt: string
  updatedAt: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}
