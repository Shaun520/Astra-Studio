const API_BASE_URL = import.meta.env.VITE_API_BASE_URL

export interface ChatStreamCallbacks {
  onThinking?: (content: string) => void
  onMessage: (content: string) => void
  onComplete: () => void
  onError: (error: Error) => void
  onSources?: (sources: any[]) => void
}

export interface SendChatMessageOptions {
  memoryId: string
  text: string
  files?: string[]
  deepThink?: boolean
  webSearch?: boolean
  knowledgeBase?: boolean
  model?: string
}

async function parseSSELine(line: string, callbacks?: Partial<ChatStreamCallbacks>): Promise<boolean> {
  const trimmed = line.trim()
  if (!trimmed || trimmed.startsWith(':')) return false

  if (trimmed.startsWith('data:')) {
    const data = trimmed.slice(5).trim()
    if (!data || data === '[DONE]') return data === '[DONE]'

    try {
      const parsed = JSON.parse(data)
      if (parsed.type === 'thinking' && parsed.content !== undefined) {
        if (parsed.content) {
          callbacks?.onThinking?.(parsed.content)
        }
        return true
      }
      if (parsed.type === 'text' && parsed.content) {
        callbacks?.onMessage?.(parsed.content)
        return true
      }
      if (parsed.status === 'done') {
        callbacks?.onComplete?.()
        return true
      }
      if (parsed.error) {
        callbacks?.onError?.(new Error(parsed.error))
        return true
      }
      if (parsed.type === 'sources' && parsed.sources) {
        callbacks?.onSources?.(parsed.sources)
        return true
      }
    } catch {
      if (data) callbacks?.onMessage?.(data)
    }
  }
  return false
}

export async function sendChatMessage(
  options: SendChatMessageOptions,
  callbacks?: Partial<ChatStreamCallbacks>,
  signal?: AbortSignal
): Promise<void> {
  const { memoryId, text, files } = options

  const formData = new FormData()
  formData.append('memoryId', memoryId)
  if (text?.trim()) formData.append('text', text)
  files?.forEach(url => { if (url?.trim()) formData.append('files', url.trim()) })
  if (options.deepThink) {
    formData.append('deepThink', 'true')
  }
  if (options.webSearch) {
    formData.append('webSearch', 'true')
  }
  if (options.knowledgeBase) {
    formData.append('knowledgeBase', 'true')
  }
  formData.append('model', options.model || 'auto')

  try {
    const response = await fetch(`${API_BASE_URL}/chat`, {
      method: 'POST',
      body: formData,
      signal,
    })

    if (!response.ok) throw new Error(`HTTP ${response.status}`)

    const reader = response.body?.getReader()
    if (!reader) throw new Error('No response body')

    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        const completed =await parseSSELine(line, callbacks)
        if (completed && line.includes('[DONE]')) return
      }
    }

    callbacks?.onComplete?.()
  } catch (error) {
    if ((error as Error).name === 'AbortError') return
    callbacks?.onError?.(error as Error)
  }
}

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
