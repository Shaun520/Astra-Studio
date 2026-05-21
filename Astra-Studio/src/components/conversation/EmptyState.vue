// 会话管理组件 - 空状态占位符
<script setup lang="ts">
import { MessageSquare } from 'lucide-vue-next'

const emit = defineEmits<{
  (e: 'create'): void
}>()
</script>

<template>
  <div class="empty-state flex flex-col items-center justify-center py-12 px-4 text-center">
    <div class="icon-wrapper w-16 h-16 rounded-2xl bg-bg-2 grid place-items-center mb-4 animate-pulse-slow">
      <MessageSquare class="w-7 h-7 text-text-3" />
    </div>
    <p class="text-[13px] text-text-3 font-medium mb-1">还没有对话记录</p>
    <p class="text-[11.5px] text-text-4 mb-5">开始你的第一个 AI 对话</p>
    <button
      @click="emit('create')"
      class="px-4 py-2 bg-accent text-bg text-[12.5px] font-medium rounded-lg hover:bg-accent/90 transition-colors"
    >
      开始第一个对话
    </button>
  </div>
</template>

<style scoped>
@keyframes pulse-slow {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 1; }
}
.animate-pulse-slow {
  animation: pulse-slow 3s ease-in-out infinite;
}
</style>

