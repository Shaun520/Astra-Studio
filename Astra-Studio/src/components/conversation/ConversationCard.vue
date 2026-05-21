// 会话管理组件 - 会话卡片
<script setup lang="ts">
import { computed } from 'vue'
import { MessageSquare, Bot, Brain, Sparkles } from 'lucide-vue-next'
import type { ConversationItem } from '@/types/conversation'

const props = defineProps<{
  conversation: ConversationItem
  isActive: boolean
}>()

const emit = defineEmits<{
  (e: 'click', memoryId: string): void
  (e: 'contextmenu', event: MouseEvent, memoryId: string): void
}>()

const modelIcon = computed(() => {
  const name = props.conversation.modelName.toLowerCase()
  if (name.includes('deepseek')) return MessageSquare
  if (name.includes('qwen')) return Brain
  if (name.includes('glm') || name.includes('zhipu')) return Bot
  return Sparkles
})

const timeAgo = computed(() => {
  const date = new Date(props.conversation.updatedAt)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  if (diffMins < 1) return '刚刚'
  if (diffMins < 60) return `${diffMins}分钟前`
  const diffHours = Math.floor(diffMins / 60)
  if (diffHours < 24) return `${diffHours}小时前`
  const diffDays = Math.floor(diffHours / 24)
  return `${diffDays}天前`
})
</script>

<template>
  <div
    class="conversation-card flex items-start gap-2.5 px-2.5 py-2 rounded-lg cursor-pointer transition-colors duration-150"
    :class="{ 'active': isActive }"
    @click="emit('click', conversation.memoryId)"
    @contextmenu.prevent="emit('contextmenu', $event, conversation.memoryId)"
  >
    <component :is="modelIcon" class="icon w-4 h-4 shrink-0 mt-0.5 opacity-70" />
    <div class="flex-1 min-w-0">
      <div class="title text-[13px] font-medium truncate">{{ conversation.title }}</div>
      <div v-if="conversation.lastMessagePreview" class="preview text-[11px] text-text-3 truncate mt-0.5">
        {{ conversation.lastMessagePreview }}
      </div>
      <div class="meta flex items-center gap-2 mt-1">
        <span class="count text-[10px] text-text-4 tabular-nums">{{ conversation.messageCount }} 条</span>
        <span class="time text-[10px] text-text-4">{{ timeAgo }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.conversation-card:hover {
  background: var(--color-bg-hover);
}
.conversation-card.active {
  background: var(--color-bg-2);
  color: var(--color-text);
}
.conversation-card.active::before {
  content: '';
  position: absolute;
  left: -14px;
  top: 50%;
  transform: translateY(-50%);
  width: 2px;
  height: 16px;
  background: var(--color-accent);
  border-radius: 0 2px 2px 0;
}
.conversation-card {
  position: relative;
}
</style>

