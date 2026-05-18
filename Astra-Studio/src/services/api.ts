const API_BASE_URL = 'http://localhost:8089/api'

export interface ChatStreamCallbacks {
  onThinking?: (content: string) => void
  onMessage: (content: string) => void
  onComplete: () => void
  onError: (error: Error) => void
}

export interface SendChatMessageOptions {
  memoryId: string
  text: string
  files?: string[]
  deepThink?: boolean
  webSearch?: boolean
  model?: string
}

async function parseSSELine(line: string, callbacks?: Partial<ChatStreamCallbacks>): boolean {
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
  formData.append('model', options.model || 'auto')

  try {
    const response = await fetch(`${API_BASE_URL}/ai/chat`, {
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
        const completed = parseSSELine(line, callbacks)
        if (completed && line.includes('[DONE]')) return
      }
    }

    callbacks?.onComplete?.()
  } catch (error) {
    if ((error as Error).name === 'AbortError') return
    callbacks?.onError?.(error as Error)
  }
}
