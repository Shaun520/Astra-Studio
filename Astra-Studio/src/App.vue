<script setup lang="ts">
import { ref, reactive, nextTick, provide, onMounted, watch } from 'vue'
import { debug, error } from './utils/logger'
import AppSidebar from '@/components/layout/AppSidebar.vue'
import MainHeader from '@/components/layout/MainHeader.vue'
import ChatMessage from '@/components/chat/ChatMessage.vue'
import Composer from '@/components/chat/Composer.vue'
import StudioPanel from '@/components/panels/StudioPanel.vue'
import TweaksPanel from '@/components/panels/TweaksPanel.vue'
import ImageGenerator from '@/components/ai/ImageGenerator.vue'
import VideoGenerator from '@/components/ai/VideoGenerator.vue'
import ImagePreview from '@/components/ai/ImagePreview.vue'
import MessageHistoryView from '@/components/common/MessageHistoryView.vue'
import { sendChatMessage, createConversation, getConversations, getConversationMessages } from '@/services'
import { uploadFiles } from '@/services/oss'
import type { Message, ConversationItem, MessageItem } from './types'
import { formatFileSize, getFileTypeLabel } from './utils/file'
import { useToast } from './composables/useToast'
import ToastContainer from '@/components/common/ToastContainer.vue'

const toast = useToast()

const showTweaks = ref(false)
const activeView = ref('对话')
const viewMode = ref<'chat' | 'history'>('chat')
const convWrapRef = ref<HTMLElement | null>(null)
const isLoading = ref(false)
let abortController: AbortController | null = null

// 模型选择状态
const selectedModel = ref('auto')

provide('selectedModel', selectedModel)

const DEFAULT_CHAT_PARAMS = { temperature: 0.72, maxOutput: 4096, topP: 0.95, systemPrompt: '' }
function loadChatParams() {
  try {
    const raw = localStorage.getItem('chat-params')
    if (raw) { const parsed = JSON.parse(raw); if (parsed && typeof parsed === 'object') return { ...DEFAULT_CHAT_PARAMS, ...parsed } }
  } catch { }
  return { ...DEFAULT_CHAT_PARAMS }
}
const chatParams = reactive(loadChatParams())
provide('chatParams', chatParams)
watch(chatParams, () => { try { localStorage.setItem('chat-params', JSON.stringify(chatParams)) } catch { } }, { deep: true })

// ==================== 会话管理状态 ====================
const currentSessionId = ref(generateSessionId())
const conversationList = ref<ConversationItem[]>([])
const historyMemoryId = ref<string | null>(null)

provide('currentSessionId', currentSessionId)
provide('conversationList', conversationList)

// 当前会话标题（用于 MainHeader 显示）
const currentTitle = ref<string>('新对话')
provide('currentTitle', currentTitle)

function updateCurrentTitle(title: string) {
  currentTitle.value = title
}
provide('updateCurrentTitle', updateCurrentTitle)

function generateSessionId(): string {
  const timestamp = Date.now().toString(36)
  const random = Math.random().toString(36).substring(2, 8)
  return `session_${timestamp}_${random}`
}

async function refreshConversations() {
  try {
    const result = await getConversations(0, 50)
    conversationList.value = result.content
    debug('[Conversation] Refreshed list:', result.totalElements, 'conversations')
  } catch (e) {
    error('[Conversation] Failed to refresh list:', e)
  }
}
provide('refreshConversations', refreshConversations)

// Task 8.2: 新建对话逻辑（调用 API）
async function handleNewConversation() {
  debug('[Session] Creating new conversation via API...')
  
  try {
    // 调用后端 API 创建会话
    const newConv = await createConversation({
      modelName: selectedModel.value !== 'auto' ? selectedModel.value : undefined
    })
    
    // 使用服务端返回的 memoryId
    currentSessionId.value = newConv.memoryId
    
    // 更新标题
    currentTitle.value = newConv.title || '新对话'
    
    // 清空消息历史
    messages.value = []
    
    // 切换回聊天模式
    viewMode.value = 'chat'
    historyMemoryId.value = null
    
    // 立即刷新侧边栏（乐观插入）
    conversationList.value.unshift(newConv)
    
    // 持久化到 localStorage
    localStorage.setItem('lastSessionId', newConv.memoryId)
    
    // 停止正在进行的请求
    if (abortController && isLoading.value) {
      abortController.abort()
      isLoading.value = false
      abortController = null
    }
    
    debug('[Session] New conversation created:', newConv.memoryId)
    toast.success('新建对话成功')
  } catch (e) {
    error('[Session] Failed to create conversation via API, falling back to local UUID:', e)
    // 降级：使用前端本地生成的 UUID
    startNewSessionLocal()
  }
}

function startNewSessionLocal() {
  debug('[Session] Fallback to local session ID generation')
  
  messages.value = []
  currentSessionId.value = generateSessionId()
  currentTitle.value = '新对话'
  viewMode.value = 'chat'
  historyMemoryId.value = null
  
  if (abortController && isLoading.value) {
    abortController.abort()
    isLoading.value = false
    abortController = null
  }
  
  localStorage.setItem('lastSessionId', currentSessionId.value)
}

function startNewSession() {
  handleNewConversation()
}

provide('startNewSession', startNewSession)

// Task 8.1: 恢复会话（继续聊天）
async function handleRestoreConversation(memoryId: string) {
  debug('[Session] Restoring conversation:', memoryId)
  
  try {
    // 获取该会话的消息记录
    const result = await getConversationMessages(memoryId, 0, 100)
    
    // 转换为前端 Message 格式
    const restoredMessages: Message[] = result.content.map(msg => ({
      role: msg.role === 'user' ? 'user' as const : 'assistant' as const,
      author: msg.role === 'user' ? '林深' : 'Astra',
      time: formatTimestamp(msg.timestamp),
      content: msg.content,
      thinkingContent: msg.thinkingContent || undefined,
      attachments: undefined,
    }))
    
    // 设置消息列表和当前会话 ID
    messages.value = restoredMessages
    currentSessionId.value = memoryId
    viewMode.value = 'chat'
    historyMemoryId.value = null
    
    // 更新标题（从会话列表中查找）
    const conv = conversationList.value.find(c => c.memoryId === memoryId)
    currentTitle.value = conv?.title || '新对话'
    
    localStorage.setItem('lastSessionId', memoryId)
    
    scrollToBottom()
    debug('[Session] Restored', restoredMessages.length, 'messages')
  } catch (e) {
    error('[Session] Failed to restore conversation:', e)
    toast.fromError(e, '恢复对话失败')
  }
}

// Task 8.1: 浏览历史消息（只读模式）
function handleViewHistory(memoryId: string) {
  debug('[History] Viewing history for:', memoryId)
  historyMemoryId.value = memoryId
  viewMode.value = 'history'
}

function formatTimestamp(timestamp: string): string {
  try {
    const date = new Date(timestamp)
    return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
  } catch {
    return ''
  }
}

// Task 8.3: 页面刷新恢复逻辑
onMounted(async () => {
  const lastSessionId = localStorage.getItem('lastSessionId')
  if (lastSessionId) {
    debug('[App] Found last session:', lastSessionId)
    await handleRestoreConversation(lastSessionId)
  }
  
  // 刷新会话列表
  await refreshConversations()
})

// ==================== 图片预览 ====================
const previewVisible = ref(false)
const previewImages = ref<{ src: string; alt?: string }[]>([])
const previewIndex = ref(0)

function openImagePreview(images: { src: string; alt?: string }[], index = 0) {
  previewImages.value = images
  previewIndex.value = index
  previewVisible.value = true
}

provide('openImagePreview', openImagePreview)

// ==================== 聊天功能 ====================
function getCurrentTime() {
  const now = new Date()
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
}

function formatThinkTime(ms: number): string {
  const sec = Math.floor(ms / 1000)
  if (sec < 60) return `${sec}秒`
  const min = Math.floor(sec / 60)
  const remainSec = sec % 60
  return remainSec > 0 ? `${min}分${remainSec}秒` : `${min}分`
}

function scrollToBottom() {
  nextTick(() => {
    const el = convWrapRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

async function handleSend(text: string, attachments?: { id: number; file: File; preview: string | null; type: 'image' | 'file' }[], deepThink: boolean = false, webSearch: boolean = false, isKnowledgeBase: boolean = false, model?: string, selectedToolsList?: string[]) {
  if (isLoading.value) return
  const finalModel = model || selectedModel.value

  const msgAttachments = (attachments || []).map(att => {
    if (att.type === 'image') {
      return {
        type: 'image' as const,
        tag: '用户上传',
        src: att.preview,
        meta: `${att.file.name} · ${formatFileSize(att.file.size)}`,
      }
    }
    return {
      type: 'file' as const,
      fileName: att.file.name,
      fileSize: formatFileSize(att.file.size),
      fileType: getFileTypeLabel(att.file),
      src: att.preview,
      meta: `${getFileTypeLabel(att.file)} · ${formatFileSize(att.file.size)}`,
    }
  })

  messages.value.push({
    role: 'user',
    author: '林深',
    time: getCurrentTime(),
    content: text || '',
    attachments: msgAttachments.length > 0 ? msgAttachments : undefined,
  })

  const assistantMsgIndex = messages.value.length
  messages.value.push({
    role: 'assistant',
    author: 'Astra',
    time: getCurrentTime(),
    content: '',
    isLoading: true,
    thinkingContent: '',
  })

  isLoading.value = true
  scrollToBottom()

  abortController = new AbortController()
  
  let thinkingStartTime: number | null = null
  
  try {
    const fileObjects = attachments?.map(att => att.file).filter(f => f) || []
    let uploadedUrls: string[] = []
    
    if (fileObjects.length > 0) {
      const uploadResults = await uploadFiles(fileObjects)
      uploadedUrls = uploadResults.map(r => r.url)
    }
    
    sendChatMessage(
      { memoryId: currentSessionId.value, text, files: uploadedUrls, deepThink, webSearch, knowledgeBase: isKnowledgeBase, selectedTools: selectedToolsList, model: finalModel, temperature: chatParams.temperature, maxTokens: chatParams.maxOutput, topP: chatParams.topP, systemPrompt: chatParams.systemPrompt || undefined },
      {
        onThinking: (content) => {
          const msg = messages.value[assistantMsgIndex]
          if (msg) {
            if (!thinkingStartTime) thinkingStartTime = Date.now()
            msg.thinkingContent = (msg.thinkingContent || '') + content
            msg.thinkTime = formatThinkTime(Date.now() - thinkingStartTime!)
          }
        },
        onMessage: (content) => {
          const msg = messages.value[assistantMsgIndex]
          if (msg) {
            if (thinkingStartTime && !msg.thinkTime) {
              msg.thinkTime = formatThinkTime(Date.now() - thinkingStartTime)
            }
            msg.content = (msg.content || '') + content
            scrollToBottom()
          }
        },
        onSources: (sources) => {
          const msg = messages.value[assistantMsgIndex]
          if (msg) {
            msg.sources = sources
          }
        },
        onComplete: () => {
          const msg = messages.value[assistantMsgIndex]
          if (msg) {
            if (thinkingStartTime && !msg.thinkTime) {
              msg.thinkTime = formatThinkTime(Date.now() - thinkingStartTime)
            }
            msg.isLoading = false
          }
          isLoading.value = false
          abortController = null
          
          // Task 8.4: 标题生成完成后刷新侧边栏
          refreshConversations()
        },
        onError: (err) => {
          error('Chat error:', err)
          const msg = messages.value[assistantMsgIndex]
          if (msg) {
            msg.content = ''
            msg.isLoading = false
          }
          isLoading.value = false
          abortController = null
          toast.fromError(err, '对话请求失败')
        },
      },
      abortController.signal
    )
  } catch (e) {
    error('上传或发送失败:', e)
    const msg = messages.value[assistantMsgIndex]
    if (msg) {
      msg.content = ''
      msg.isLoading = false
    }
    isLoading.value = false
    abortController = null
    toast.fromError(e, '发送失败')
  }
}

function stopChat() {
  if (abortController && isLoading.value) {
    abortController.abort()
    
    const lastAssistantMsg = [...messages.value].reverse().find(msg => msg.role === 'assistant')
    if (lastAssistantMsg) {
      lastAssistantMsg.isLoading = false
      if (!lastAssistantMsg.content.trim()) {
        lastAssistantMsg.content = '<p class="text-text-4 italic">已停止生成</p>'
      } else {
        lastAssistantMsg.content += '<p class="text-text-4 italic mt-2">（已停止生成）</p>'
      }
    }
    
    isLoading.value = false
    abortController = null
  }
}

function handleNavigate(label: string) {
  activeView.value = label
}

// 侧边栏事件处理
function handleSidebarRestore(memoryId: string) {
  handleRestoreConversation(memoryId)
}

function handleSidebarNewConversation() {
  handleNewConversation()
}

const messages = ref<Message[]>([])
</script>

<template>
  <div class="app grid">
    <AppSidebar 
      @navigate="handleNavigate" 
      @restore="handleSidebarRestore"
      @new-conversation="handleSidebarNewConversation"
    />

    <main class="main flex flex-col min-w-0 bg-bg border-r border-border overflow-hidden">
      <MainHeader shrink-0 />

      <!-- 对话视图 -->
      <template v-if="activeView === '对话' && viewMode === 'chat'">
        <div ref="convWrapRef" class="conv-wrap flex-1 min-h-0 overflow-y-auto py-8 pb-4">
          <div class="conv max-w-[720px] mx-auto px-8 flex flex-col gap-7">
            <ChatMessage
              v-for="(msg, i) in messages"
              :key="i"
              :role="msg.role"
              :author="msg.author"
              :time="msg.time"
              :think-time="msg.thinkTime"
              :content="msg.content"
              :thinking-content="msg.thinkingContent"
              :sources="msg.sources"
              :attachments="msg.attachments"
              :is-loading="msg.isLoading"
            />
          </div>
        </div>

        <Composer shrink-0 @send="handleSend" @stop="stopChat" :disabled="isLoading" :loading="isLoading" />
      </template>

      <!-- 历史消息浏览视图 -->
      <template v-if="activeView === '对话' && viewMode === 'history' && historyMemoryId">
        <MessageHistoryView 
          :memory-id="historyMemoryId" 
          @back="viewMode = 'chat'"
        />
      </template>

      <!-- 图像生成视图 -->
      <ImageGenerator v-else-if="activeView === '图像生成'" />

      <!-- 视频生成视图 -->
      <VideoGenerator v-else-if="activeView === '视频生成'" />
    </main>

    <StudioPanel />

    <TweaksPanel v-model="showTweaks" />

    <ImagePreview
      v-model:visible="previewVisible"
      :images="previewImages"
      :initial-index="previewIndex"
    />

    <ToastContainer />
  </div>
</template>

<style scoped>
.app {
  position: fixed;
  inset: 0;
  grid-template-columns: var(--sidebar-w) 1fr var(--studio-w);
}
@media (max-width: 1180px) {
  .app { grid-template-columns: var(--sidebar-w) 1fr !important; }
}
@media (max-width: 760px) {
  .app { grid-template-columns: 1fr !important; }
}
</style>
