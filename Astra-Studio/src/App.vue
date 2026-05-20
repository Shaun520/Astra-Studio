<script setup lang="ts">
import { ref, nextTick, provide } from 'vue'
import { debug, error } from './utils/logger'
import AppSidebar from '@/components/AppSidebar.vue'
import MainHeader from '@/components/MainHeader.vue'
import ChatMessage from '@/components/ChatMessage.vue'
import Composer from '@/components/Composer.vue'
import StudioPanel from '@/components/StudioPanel.vue'
import TweaksPanel from '@/components/TweaksPanel.vue'
import ImageGenerator from '@/components/ImageGenerator.vue'
import VideoGenerator from '@/components/VideoGenerator.vue'
import ImagePreview from '@/components/ImagePreview.vue'
import { sendChatMessage } from '@/services/api'
import { uploadFiles } from '@/services/oss'
import type { Message } from './types'
import { formatFileSize, getFileTypeLabel } from './utils/file'
import { useToast } from './composables/useToast'
import ToastContainer from '@/components/ToastContainer.vue'

const toast = useToast()

const showTweaks = ref(false)
const activeView = ref('对话')
const convWrapRef = ref<HTMLElement | null>(null)
const isLoading = ref(false)
let abortController: AbortController | null = null

// 模型选择状态
const selectedModel = ref('auto')

provide('selectedModel', selectedModel)

// 会话管理
const currentSessionId = ref(generateSessionId())

function generateSessionId(): string {
  const timestamp = Date.now().toString(36)
  const random = Math.random().toString(36).substring(2, 8)
  return `session_${timestamp}_${random}`
}

function startNewSession() {
  debug('[Session] 创建新会话')
  debug('[Session] 旧会话 ID:', currentSessionId.value)
  
  // 清空消息历史
  messages.value = []
  
  // 生成新的会话 ID
  currentSessionId.value = generateSessionId()
  
  debug('[Session] 新会话 ID:', currentSessionId.value)
  
  // 停止正在进行的请求
  if (abortController && isLoading.value) {
    abortController.abort()
    isLoading.value = false
    abortController = null
  }
}

provide('startNewSession', startNewSession)

const previewVisible = ref(false)
const previewImages = ref<{ src: string; alt?: string }[]>([])
const previewIndex = ref(0)

function openImagePreview(images: { src: string; alt?: string }[], index = 0) {
  previewImages.value = images
  previewIndex.value = index
  previewVisible.value = true
}

provide('openImagePreview', openImagePreview)

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

async function handleSend(text: string, attachments?: { id: number; file: File; preview: string | null; type: 'image' | 'file' }[], deepThink: boolean = false, webSearch: boolean = false, isKnowledgeBase: boolean = false, model?: string) {
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
    content: text ? `<p>${text}</p>` : '',
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
      { memoryId: currentSessionId.value, text, files: uploadedUrls, deepThink, webSearch, knowledgeBase: isKnowledgeBase, model: finalModel },
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

const messages = ref<Message[]>([])
</script>

<template>
  <div class="app grid">
    <AppSidebar @navigate="handleNavigate" />

    <main class="main flex flex-col min-w-0 bg-bg border-r border-border overflow-hidden">
      <MainHeader shrink-0 />

      <!-- 对话视图 -->
      <template v-if="activeView === '对话'">
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
