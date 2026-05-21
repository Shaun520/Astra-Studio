// 聊天服务类型定义
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
