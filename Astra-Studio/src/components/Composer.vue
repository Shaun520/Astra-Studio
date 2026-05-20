<script setup lang="ts">
import { ref, computed, nextTick, watch, reactive, inject, type Ref } from 'vue'
import { Paperclip, Image, Mic, Globe, ArrowUp, X, FileText, Loader2, Square, Brain, Library } from 'lucide-vue-next'
import { formatFileSize, getFileTypeLabel } from '../utils/file'

const openImagePreview = inject<(images: { src: string; alt?: string }[], index?: number) => void>('openImagePreview')!
const selectedModel = inject<Ref<string>>('selectedModel')!

const inputText = ref('')
const deepThink = ref(false)
const webSearch = ref(false)
const knowledgeBase = ref(false)
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const isDragging = ref(false)
const dragCounter = ref(0)

interface PendingAttachment {
  id: number
  file: File
  preview: string | null
  type: 'image' | 'file'
}

const pendingAttachments = ref<PendingAttachment[]>([])
let attachmentIdCounter = 0

const emit = defineEmits<{
  (e: 'send', text: string, attachments: PendingAttachment[], isDeepThink: boolean, isWebSearch: boolean, isKnowledgeBase: boolean, model?: string): void
  (e: 'stop'): void
  (e: 'update:deepThink', value: boolean): void
  (e: 'update:webSearch', value: boolean): void
}>()

const props = defineProps<{
  disabled?: boolean
  loading?: boolean
}>()

const tokenCount = computed(() => inputText.value.length)
const MAX_TOKENS = 12000
const canSend = computed(() => (inputText.value.trim() || pendingAttachments.value.length > 0) && !props.disabled)

watch(inputText, () => {
  autoResize()
})

function autoResize() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

function getFileIcon(file: File) {
  const name = file.name.toLowerCase()
  if (name.endsWith('.pdf')) return '📄'
  if (name.endsWith('.doc') || name.endsWith('.docx')) return '📝'
  if (name.endsWith('.xls') || name.endsWith('.xlsx')) return '📊'
  if (name.endsWith('.ppt') || name.endsWith('.pptx')) return '📽️'
  if (name.endsWith('.zip') || name.endsWith('.rar')) return '📦'
  return '📎'
}

function openFilePicker() {
  fileInputRef.value?.click()
}

function addFiles(files: FileList | File[]) {
  Array.from(files).forEach(file => {
    const isImage = file.type.startsWith('image')
    const attachment = reactive<PendingAttachment>({
      id: ++attachmentIdCounter,
      file,
      preview: null,
      type: isImage ? 'image' : 'file',
    })
    if (isImage) {
      const reader = new FileReader()
      reader.onload = () => {
        attachment.preview = reader.result as string
      }
      reader.readAsDataURL(file)
    }
    pendingAttachments.value.push(attachment)
  })
}

function handleFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  if (!input.files?.length) return
  addFiles(input.files)
  input.value = ''
}

function handleDragEnter(e: DragEvent) {
  e.preventDefault()
  e.stopPropagation()
  dragCounter.value++
  isDragging.value = true
}

function handleDragOver(e: DragEvent) {
  e.preventDefault()
  e.stopPropagation()
}

function handleDragLeave(e: DragEvent) {
  e.preventDefault()
  e.stopPropagation()
  dragCounter.value--
  if (dragCounter.value <= 0) {
    isDragging.value = false
    dragCounter.value = 0
  }
}

function handleDrop(e: DragEvent) {
  e.preventDefault()
  e.stopPropagation()
  isDragging.value = false
  dragCounter.value = 0
  if (!e.dataTransfer?.files.length) return
  addFiles(e.dataTransfer.files)
}

function removeAttachment(id: number) {
  pendingAttachments.value = pendingAttachments.value.filter(a => a.id !== id)
}

function previewImage(att: PendingAttachment) {
  if (att.type !== 'image' || !att.preview) return
  const allImages = pendingAttachments.value
    .filter(a => a.type === 'image' && a.preview)
    .map(a => ({ src: a.preview!, alt: a.file.name }))
  const idx = allImages.findIndex(i => i.src === att.preview)
  openImagePreview(allImages, idx >= 0 ? idx : 0)
}

function handleSend() {
  const text = inputText.value.trim()
  if (!text && pendingAttachments.value.length === 0) return
  emit('send', text, [...pendingAttachments.value], deepThink.value, webSearch.value, knowledgeBase.value, selectedModel?.value || 'auto')
  inputText.value = ''
  pendingAttachments.value = []
  nextTick(() => {
    if (textareaRef.value) textareaRef.value.style.height = 'auto'
  })
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

function handlePaste(e: ClipboardEvent) {
  if (props.disabled) return
  
  const items = e.clipboardData?.items
  if (!items) return

  const imageFiles: File[] = []
  
  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    if (item.type.startsWith('image/')) {
      const file = item.getAsFile()
      if (file) {
        imageFiles.push(file)
      }
    }
  }

  if (imageFiles.length > 0) {
    e.preventDefault()
    addFiles(imageFiles)
  }
}
</script>

<template>
  <div
    class="composer-wrap px-8 pb-5 pt-2 max-w-[720px] mx-auto w-full"
    @dragenter="handleDragEnter"
    @dragover="handleDragOver"
    @dragleave="handleDragLeave"
    @drop="handleDrop"
  >
    <!-- 拖拽提示遮罩 -->
    <Transition enter-active-class="transition-all duration-150 ease-out" enter-from-class="opacity-0" enter-to-class="opacity-100"
      leave-active-class="transition-all duration-100 ease-in" leave-from-class="opacity-100" leave-to-class="opacity-0">
      <div v-if="isDragging" class="drop-overlay absolute inset-0 z-50 rounded-xl border-2 border-dashed border-accent bg-accent/5 flex items-center justify-center pointer-events-none">
        <div class="flex flex-col items-center gap-2">
          <div class="w-12 h-12 rounded-xl bg-accent/10 grid place-items-center">
            <Image class="w-6 h-6 text-accent" />
          </div>
          <p class="text-[13px] font-medium text-accent">释放以上传文件</p>
          <p class="text-[11px] text-text-4">支持图片、PDF、Word、Excel 等</p>
        </div>
      </div>
    </Transition>

    <!-- 附件预览区 -->
    <Transition enter-active-class="transition-all duration-200 ease-out" enter-from-class="opacity-0 -translate-y-2" enter-to-class="opacity-100 translate-y-0"
      leave-active-class="transition-all duration-150 ease-in" leave-from-class="opacity-100 scale-100" leave-to-class="opacity-0 scale-95">
      <div v-if="pendingAttachments.length > 0" class="attachment-preview flex flex-wrap gap-2 mb-3 p-3 rounded-xl border border-border/60 bg-bg-2/60">
        <div v-for="att in pendingAttachments" :key="att.id"
          class="preview-item group relative rounded-lg overflow-hidden border transition-all"
          :class="att.type === 'image' ? 'w-[88px] h-[88px] border-border' : 'flex items-center gap-2.5 px-3 py-2 pr-8 w-[200px] border-border hover:border-accent-line'"
        >
          <!-- 图片预览 -->
          <template v-if="att.type === 'image'">
            <img v-if="att.preview" :src="att.preview" alt="" class="w-full h-full object-cover cursor-pointer hover:opacity-90 transition-opacity" @click="previewImage(att)" />
            <div v-else class="w-full h-full bg-bg-hover grid place-items-center">
              <Loader2 class="w-5 h-5 text-text-4 animate-spin" />
            </div>
          </template>
          <!-- 文件预览 -->
          <template v-else>
            <div class="file-icon-wrap w-9 h-9 rounded-lg bg-accent-soft grid place-items-center shrink-0 text-lg">{{ getFileIcon(att.file) }}</div>
            <div class="min-w-0">
              <p class="text-[12px] font-medium text-text truncate">{{ att.file.name }}</p>
              <p class="text-[10.5px] text-text-4">{{ getFileTypeLabel(att.file) }} · {{ formatFileSize(att.file.size) }}</p>
            </div>
          </template>
          <!-- 删除按钮 -->
          <button class="absolute top-1.5 right-1.5 w-5 h-5 rounded-full bg-black/50 backdrop-blur-sm grid place-items-center text-white cursor-pointer opacity-0 group-hover:opacity-100 transition-all hover:bg-danger border-0"
            @click="removeAttachment(att.id)"
          >
            <X class="w-3 h-3" />
          </button>
        </div>
      </div>
    </Transition>

    <div class="composer relative border border-border/60 rounded-xl bg-bg-2/80 backdrop-blur-sm px-4 py-3 transition-all duration-200 focus-within:border-accent-line focus-within:bg-bg-2 focus-within:shadow-[0_0_0_3px_var(--color-accent-soft)]"
      :class="{ 'border-accent border-dashed': isDragging }"
    >
      <textarea
        ref="textareaRef"
        v-model="inputText"
        placeholder="继续对话，或输入 / 调用图像、视频、语音工具…"
        rows="1"
        :disabled="props.disabled"
        class="w-full bg-transparent border-0 outline-none resize-none text-text font-sans text-[14px] leading-[1.6] min-h-[22px] max-h-[160px] py-0.5 placeholder:text-text-4 disabled:opacity-50 disabled:cursor-not-allowed"
        @keydown="handleKeydown"
        @paste="handlePaste"
      ></textarea>
      <div class="composer-tools flex items-center gap-0.5 mt-2">
        <button class="tool-chip inline-flex items-center gap-1.5 px-2 py-1 rounded-md text-text-3 text-[12px] cursor-pointer transition-colors duration-150 hover:bg-bg-hover hover:text-text bg-transparent border-0 font-sans relative disabled:opacity-50 disabled:cursor-not-allowed"
          :class="{ 'text-accent': pendingAttachments.length > 0 }"
          :disabled="props.disabled"
          title="上传附件（图片、PDF、Word等）"
          @click="openFilePicker"
        >
          <Paperclip class="w-[15px] h-[15px]" />
          <span v-if="pendingAttachments.length > 0" class="absolute -top-1 -right-1 w-4 h-4 rounded-full bg-accent text-bg text-[9.5px] font-bold grid place-items-center">{{ pendingAttachments.length }}</span>
        </button>
        <input ref="fileInputRef" type="file" accept="image/*,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.md" multiple class="hidden" :disabled="props.disabled" @change="handleFileSelect" />
        <button
          class="tool-chip inline-flex items-center gap-1.5 px-2 py-1 rounded-md text-text-3 text-[12px] cursor-pointer transition-colors duration-150 hover:bg-bg-hover hover:text-text bg-transparent border-0 font-sans disabled:opacity-50 disabled:cursor-not-allowed"
          :class="{ 'active': deepThink }"
          :disabled="props.disabled"
          @click="deepThink = !deepThink; emit('update:deepThink', deepThink)"
          title="深度思考"
        >
          <Brain class="w-[15px] h-[15px]" />
          <span>深度思考</span>
        </button>
        <button
          class="tool-chip inline-flex items-center gap-1.5 px-2 py-1 rounded-md text-text-3 text-[12px] cursor-pointer transition-colors duration-150 hover:bg-bg-hover hover:text-text bg-transparent border-0 font-sans disabled:opacity-50 disabled:cursor-not-allowed"
          :class="{ active: webSearch }"
          :disabled="props.disabled"
          @click="webSearch = !webSearch; emit('update:webSearch', webSearch)"
          title="联网搜索"
        >
          <Globe class="w-[15px] h-[15px]" />
          <span>联网</span>
        </button>
        <button
          class="tool-chip inline-flex items-center gap-1.5 px-2 py-1 rounded-md text-text-3 text-[12px] cursor-pointer transition-colors duration-150 hover:bg-bg-hover hover:text-text bg-transparent border-0 font-sans disabled:opacity-50 disabled:cursor-not-allowed"
          :class="{ active: knowledgeBase }"
          :disabled="props.disabled"
          @click="knowledgeBase = !knowledgeBase"
          title="知识库检索"
        >
          <Library class="w-[15px] h-[15px]" />
          <span>知识库</span>
        </button>
        <button class="tool-chip inline-flex items-center gap-1.5 px-2 py-1 rounded-md text-text-3 text-[12px] cursor-pointer transition-colors duration-150 hover:bg-bg-hover hover:text-text bg-transparent border-0 font-sans disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="props.disabled"
          title="语音输入"
        >
          <Mic class="w-[15px] h-[15px]" />
          <span>语音输入</span>
        </button>
        <div class="flex-1"></div>
        <span class="token-count text-[11px] text-text-4 font-mono mr-1.5 tabular-nums">{{ tokenCount }} / {{ MAX_TOKENS.toLocaleString() }}</span>
        <button
          v-if="!props.loading"
          class="send-btn inline-flex items-center justify-center w-8 h-8 rounded-lg bg-accent text-bg cursor-pointer border-0 transition-all duration-150 hover:bg-accent-2 active:scale-95 disabled:opacity-40"
          :disabled="!canSend"
          @click="handleSend"
        >
          <ArrowUp class="w-4 h-4" />
        </button>
        <button
          v-else
          class="send-btn inline-flex items-center justify-center w-8 h-8 rounded-lg bg-danger text-white cursor-pointer border-0 transition-all duration-150 hover:bg-danger/80 active:scale-95"
          @click="emit('stop')"
        >
          <Square class="w-3.5 h-3.5" fill="currentColor" />
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.composer-wrap { position: relative; }
.tool-chip.active {
  background: var(--color-accent-soft);
  color: var(--color-accent);
}
.drop-overlay {
  margin: -8px;
  width: calc(100% + 16px);
}
</style>
