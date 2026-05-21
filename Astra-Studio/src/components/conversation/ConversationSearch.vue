<script setup lang="ts">
/* 会话管理组件 - 会话搜索框 */
import { ref, watch } from 'vue'
import { Search, X } from 'lucide-vue-next'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'search', keyword: string): void
}>()

let debounceTimer: ReturnType<typeof setTimeout> | null = null

function handleInput(e: Event) {
  const value = (e.target as HTMLInputElement).value
  emit('update:modelValue', value)
  
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    emit('search', value)
  }, 300)
}

function clearSearch() {
  emit('update:modelValue', '')
  emit('search', '')
}
</script>

<template>
  <div class="conversation-search relative">
    <Search class="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-text-4" />
    <input
      type="text"
      :value="modelValue"
      @input="handleInput"
      placeholder="鎼滅储瀵硅瘽..."
      class="w-full pl-8 pr-8 py-1.5 text-[12px] bg-bg border border-border rounded-lg text-text placeholder:text-text-4 focus:outline-none focus:border-accent transition-colors"
    />
    <button
      v-if="modelValue"
      @click="clearSearch"
      class="absolute right-2 top-1/2 -translate-y-1/2 w-4 h-4 rounded grid place-items-center text-text-3 hover:text-text hover:bg-bg-hover transition-colors"
    >
      <X class="w-3 h-3" />
    </button>
  </div>
</template>

