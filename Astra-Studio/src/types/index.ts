export interface NavItem {
  icon: string
  label: string
  badge?: string
  active?: boolean
}

export interface LibraryItem {
  icon: string
  label: string
  count: number
}

export interface Message {
  role: 'user' | 'assistant'
  author: string
  time: string
  thinkTime?: string
  content: string
  isLoading?: boolean
  thinkingContent?: string
  sources?: KnowledgeSource[]
  attachments?: Attachment[]
}

export interface Attachment {
  type: 'image' | 'video' | 'audio' | 'code' | 'file'
  tag?: string
  title?: string
  subtitle?: string
  meta?: string
  codeContent?: string
  src?: string | null
  fileName?: string
  fileSize?: string
  fileType?: string
}

export interface ModelInfo {
  name: string
  tags: string[]
}

export interface ParamConfig {
  name: string
  value: number
  min: number
  max: number
  step: number
}

export interface RecentAsset {
  type: 'image' | 'video' | 'audio' | 'code'
  label: string
  meta1: string
  meta2: string
}

export interface ToolChip {
  icon: string
  label?: string
  active?: boolean
}

export interface ThemeConfig {
  mode: 'dark' | 'light'
  accent: 'amber' | 'sage' | 'rose'
  density: 'loose' | 'default' | 'tight'
}

export interface KnowledgeSource {
  chunk_id: number | null
  content_snippet: string
  document_name: string
  page_number: number | null
  score: number
}
