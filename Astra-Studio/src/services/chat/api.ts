// 聊天服务API - 处理SSE流式聊天
import type { ChatStreamCallbacks, SendChatMessageOptions } from './types'
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL

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
  if (options.selectedTools?.length) {
    options.selectedTools.forEach(tool => formData.append('selectedTools', tool))
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

    const MAX_BUFFER_SIZE = 10 * 1024 * 1024
    const SSE_TIMEOUT_MS = 300000
    const startTime = Date.now()

    while (true) {
      if (Date.now() - startTime > SSE_TIMEOUT_MS) {
        callbacks?.onError?.(new Error('SSE connection timeout (5 minutes)'))
        reader.cancel()
        return
      }

      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      if (buffer.length > MAX_BUFFER_SIZE) {
        callbacks?.onError?.(new Error('Response size exceeds limit (10MB)'))
        reader.cancel()
        return
      }

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
