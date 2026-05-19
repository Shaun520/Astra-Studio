<script setup lang="ts">
import { ref, nextTick, provide } from 'vue'
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
  console.log('[Session] 创建新会话')
  console.log('[Session] 旧会话 ID:', currentSessionId.value)
  
  // 清空消息历史
  messages.value = []
  
  // 生成新的会话 ID
  currentSessionId.value = generateSessionId()
  
  console.log('[Session] 新会话 ID:', currentSessionId.value)
  
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

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function getFileTypeLabel(file: File): string {
  const name = file.name.toLowerCase()
  if (name.endsWith('.pdf')) return 'PDF'
  if (name.endsWith('.doc') || name.endsWith('.docx')) return 'Word'
  if (name.endsWith('.xls') || name.endsWith('.xlsx')) return 'Excel'
  if (name.endsWith('.ppt') || name.endsWith('.pptx')) return 'PPT'
  if (name.endsWith('.txt')) return 'TXT'
  if (name.endsWith('.md')) return 'Markdown'
  return file.type.split('/')[1]?.toUpperCase() || 'FILE'
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
        onError: (error) => {
          console.error('Chat error:', error)
          const msg = messages.value[assistantMsgIndex]
          if (msg) {
            msg.content = '<p class="text-danger">抱歉，发生了错误，请稍后重试。</p>'
            msg.isLoading = false
          }
          isLoading.value = false
          abortController = null
        },
      },
      abortController.signal
    )
  } catch (error) {
    console.error('上传或发送失败:', error)
    const msg = messages.value[assistantMsgIndex]
    if (msg) {
      msg.content = '<p class="text-danger">文件上传失败，请稍后重试。</p>'
      msg.isLoading = false
    }
    isLoading.value = false
    abortController = null
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

// const messages = ref([
//   {
//     role: 'user' as const,
//     author: '林深',
//     time: '15:42',
//     content: '<p>我在为一档讲长篇深度报道的播客做视觉方向，调性偏严肃但不沉闷。能给一些方向建议，并按你最看好的那个方向生成一张封面概念图吗？比例 1:1。</p>',
//   },
//   {
//     role: 'assistant' as const,
//     author: 'Astra',
//     time: '15:42',
//     thinkTime: '4 秒',
//     content: `<p>三个值得考虑的方向，从最稳到最有锋芒：</p>
//       <p><span class="em-mark">一</span><strong>档案室</strong>。暖中性色 + 经典衬线 + 旧纸纹理。传递严肃和历史厚度，调性接近《纽约客》《人物》。优点是最贴合"长篇"二字，缺点是容易显得保守。</p>
//       <p><span class="em-mark">二</span><strong>战地记者</strong>。土色调 + 手绘地图 + 颗粒摄影。适合走访、外采型选题，但要小心不要变成旅行播客的视觉。</p>
//       <p><span class="em-mark">三</span><strong>当代随笔</strong>。高对比米白与近黑 + 巨大字号 + 大量留白。最克制也最现代，适合作者气质强的播客。</p>
//       <p>我倾向 <em>第三个</em> —— 既严肃又能从市面上同类视觉里跳出来。生成了一张草图：</p>`,
//     attachments: [
//       {
//         type: 'image' as const,
//         tag: '封面概念 · 1 : 1',
//         title: '深度<br />对话',
//         subtitle: 'a podcast about long-form journalism',
//         meta: 'Astra Vision · 1024×1024 · 用时 2.4s',
//         src: 'https://images.unsplash.com/photo-1478737270239-2f02b77fc618?w=800&h=800&fit=crop&q=80',
//       },
//     ],
//   },
//   {
//     role: 'user' as const,
//     author: '林深',
//     time: '15:44',
//     content: '<p>这个方向很好，但我想在字体选择上再深入聊聊。你推荐用哪些衬线体？中文和英文分别怎么搭配？</p>',
//   },
//   {
//     role: 'assistant' as const,
//     author: 'Astra',
//     time: '15:44',
//     thinkTime: '3 秒',
//     content: `<p>好问题，字体是这个方向的核心武器：</p>
//       <p><strong>英文</strong>：首推 <em>Instrument Serif</em>（我们正在用的这款），它有编辑感但不老派，x-height 偏大所以小字号也清晰。备选是 <em>Canela</em> 或 <em>Freight Display</em>——更暖、更像纸媒。</p>
//       <p><strong>中文</strong>：思源宋体（Source Han Serif）是最安全的选择，各字重齐全。如果想更有个性，可以试 <em>霞鹜文楷</em> 或 <em>方正清刻本悦宋</strong>——后者有雕版印刷的质感，跟"深度报道"的主题非常搭。</p>
//       <p><strong>搭配原则</strong>：中文字号比英文大 2-3pt，行高控制在 1.6-1.8 之间。标题用粗体正文用常规，形成层级但不抢戏。</p>`,
//   },
//   {
//     role: 'user' as const,
//     author: '林深',
//     time: '15:46',
//     content: '<p>能帮我生成一套完整的品牌色板吗？包括主色、辅助色、中性色，以及在不同背景上的使用规范。</p>',
//   },
//   {
//     role: 'assistant' as const,
//     author: 'Astra',
//     time: '15:47',
//     thinkTime: '5 秒',
//     content: `<p>基于"当代随笔"方向，整理了一套完整色板：</p>
//       <p><span class="em-mark">主色</span> —— 近黑 <code>#1a1816</code> 作为文字与框架色，米白 <code>#f4f0ea</code> 作为底色。这对组合本身就是视觉语言的一部分，不需要额外强调色。</p>
//       <p><span class="em-mark">强调色</span> —— 琥珀金 <code>#e3a857</code> 用于链接、高亮、交互反馈。克制使用，每屏不超过 3 处。浅版本 <code>#e3a85718</code> 用于背景区块划分。</p>
//       <p><span class="em-mark">语义色</span> —— 成功用 <code>#4a9e7a</code>，警告用 <code>#c48a3a</code>，错误用 <code>#b85c50</code>。全部做了无障碍对比度校验，WCAG AA 级别通过。</p>
//       <p><span class="em-mark">中性阶梯</span> —— 从 <code>#f4f0ea</code> 到 <code>#1a1816</code> 共 12 级灰阶，用于边框、分割线、次级文字等场景。已输出到右侧面板，可以直接导出。</p>`,
//     attachments: [
//       {
//         type: 'code' as const,
//         tag: '色彩系统',
//         meta: 'Astra Code · 生成于 15:47',
//       },
//     ],
//   },
// ])
interface Message {
  role: 'user' | 'assistant'
  author: string
  time: string
  thinkTime?: string
  content: string
  isLoading?: boolean
  thinkingContent?: string
  sources?: any[]
  attachments?: Array<{
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
  }>
}

const messages = ref<Message[]>([])
// // {
//   role: 'assistant',
//   author: 'Astra',
//   time: new Date().toLocaleTimeString().substring(0, 5),
//   content: '<p>你好，可以随时向我提问</p>',
// }
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
