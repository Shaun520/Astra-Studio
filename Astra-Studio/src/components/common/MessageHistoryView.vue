// 通用组件 - 消息历史视图
<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ChevronUp, User, Bot, Loader2 } from 'lucide-vue-next'
import type { MessageItem, PageResult } from '@/types/conversation'
import { getConversationMessages } from '@/services/conversation/api'
import SkeletonLoader from './SkeletonLoader.vue'

const props = defineProps<{
  memoryId: string
}>()

const emit = defineEmits<{
  (e: 'back'): void
}>()

const messages = ref<MessageItem[]>([])
const currentPage = ref(0)
const totalPages = ref(1)
const isLoading = ref(false)
const isLoadingMore = ref(false)
const containerRef = ref<HTMLElement | null>(null)
const activeRoleFilter = ref<string>('')
const roleOptions = [
  { label: '鍏ㄩ儴', value: '' },
  { label: '鐢ㄦ埛', value: 'user' },
  { label: 'AI', value: 'assistant' },
]

async function loadMessages(page = 0, append = false) {
  if (append) {
    isLoadingMore.value = true
  } else {
    isLoading.value = true
  }
  
  try {
    const result: PageResult<MessageItem> = await getConversationMessages(
      props.memoryId,
      page,
      50,
      activeRoleFilter.value || undefined
    )
    
    if (append) {
      messages.value = [...result.content.reverse(), ...messages.value]
    } else {
      messages.value = result.content.reverse()
    }
    
    currentPage.value = result.currentPage
    totalPages.value = result.totalPages
  } catch (error) {
    console.error('Failed to load messages:', error)
  } finally {
    isLoading.value = false
    isLoadingMore.value = false
  }
}

async function loadMore() {
  if (currentPage.value >= totalPages.value - 1 || isLoadingMore.value) return
  await loadMessages(currentPage.value + 1, true)
}

function handleScroll() {
  const el = containerRef.value
  if (!el) return
  if (el.scrollTop < 50 && !isLoadingMore.value) {
    loadMore()
  }
}

function filterByRole(role: string) {
  activeRoleFilter.value = role
  loadMessages(0)
}

function formatTime(timestamp: string): string {
  const date = new Date(timestamp)
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

onMounted(() => {
  loadMessages()
})

watch(() => props.memoryId, () => {
  currentPage.value = 0
  messages.value = []
  loadMessages()
})
</script>

<template>
  <div class="message-history flex flex-col h-full">
    <div class="history-header shrink-0 flex items-center justify-between px-4 py-3 border-b border-border">
      <button @click="emit('back')" class="flex items-center gap-1.5 text-[12.5px] text-text-2 hover:text-text transition-colors cursor-pointer border-0 bg-transparent">
        鈫?杩斿洖瀵硅瘽
      </button>
      <div class="role-filters flex items-center gap-1">
        <button
          v-for="opt in roleOptions"
          :key="opt.value"
          @click="filterByRole(opt.value)"
          class="px-2.5 py-1 text-[11px] rounded-md transition-colors border-0 cursor-pointer"
          :class="activeRoleFilter === opt.value ? 'bg-accent text-bg' : 'text-text-3 hover:bg-bg-hover'"
        >
          {{ opt.label }}
        </button>
      </div>
    </div>

    <div 
      ref="containerRef" 
      class="history-content flex-1 overflow-y-auto"
      @scroll="handleScroll"
    >
      <!-- Load More Indicator -->
      <div v-if="isLoadingMore" class="flex justify-center py-3">
        <Loader2 class="w-4 h-4 text-text-3 animate-spin" />
      </div>

      <!-- Loading State -->
      <SkeletonLoader v-if="isLoading && messages.length === 0" />

      <!-- Empty State -->
      <div v-else-if="!isLoading && messages.length === 0" class="flex flex-col items-center justify-center py-16 text-center">
        <Bot class="w-10 h-10 text-text-3 mb-3" />
        <p class="text-[13px] text-text-3">该会话暂无消息</p>
      </div>

      <!-- Messages List -->
      <div v-else class="max-w-[720px] mx-auto px-6 py-6 space-y-5">
        <div
          v-for="(msg, i) in messages"
          :key="msg.id || i"
          class="message-item flex items-start gap-3"
          :style="{ flexDirection: msg.role === 'user' ? 'row-reverse' : 'row' }"
        >
          <component
            :is="msg.role === 'user' ? User : Bot"
            class="avatar w-7 h-7 rounded-lg grid place-items-center shrink-0 mt-0.5"
            :class="msg.role === 'user' ? 'bg-blue-500/10 text-blue-400' : 'bg-bg-2 text-text-2'"
          />
          <div class="flex-1 min-w-0" :style="{ textAlign: msg.role === 'user' ? 'right' : 'left' }">
            <div class="flex items-center gap-2 mb-1" :style="{ justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start' }">
              <span class="text-[12px] font-medium">{{ msg.role === 'user' ? '鐢ㄦ埛' : 'Astra' }}</span>
              <span class="text-[10.5px] text-text-4">{{ formatTime(msg.timestamp) }}</span>
            </div>
            <!-- Thinking Content (collapsible for assistant) -->
            <div v-if="msg.thinkingContent && msg.role === 'assistant'" class="thinking-block mb-2 p-2.5 bg-bg-2/60 rounded-lg">
              <details class="group">
                <summary class="cursor-pointer text-[11.5px] text-text-3 hover:text-text-2 list-none flex items-center gap-1">
                  馃挱 鎬濈淮杩囩▼
                </summary>
                <p class="mt-2 text-[12px] text-text-3 leading-relaxed whitespace-pre-wrap">{{ msg.thinkingContent }}</p>
              </details>
            </div>
            <!-- Message Content -->
            <div
              class="content text-[13px] leading-relaxed whitespace-pre-wrap break-words px-4 py-2.5 rounded-2xl inline-block"
              :class="msg.role === 'user'
                ? 'bg-accent/12 border border-accent/20'
                : 'bg-bg-2 border-border'"
              v-html="msg.content"
            ></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
</style>

