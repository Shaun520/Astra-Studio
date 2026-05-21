// 聊天核心组件 - 右键菜单
<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

export interface MenuItem {
  icon?: string
  label: string
  disabled?: boolean
  divider?: boolean
  danger?: boolean
}

const props = defineProps<{
  visible: boolean
  x: number
  y: number
  items: MenuItem[]
}>()

const emit = defineEmits<{
  (e: 'select', item: MenuItem): void
  (e: 'close'): void
}>()

function handleClick(item: MenuItem) {
  if (item.disabled || item.divider) return
  emit('select', item)
  emit('close')
}

function close() {
  emit('close')
}

onMounted(() => {
  document.addEventListener('click', close)
})

onUnmounted(() => {
  document.removeEventListener('click', close)
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="context-menu fixed z-50 min-w-[160px] py-1 bg-bg border border-border rounded-lg shadow-lg"
      :style="{ left: `${x}px`, top: `${y}px` }"
      @click.stop
    >
      <template v-for="(item, index) in items" :key="index">
        <div v-if="item.divider" class="my-1 border-t border-border" />
        <button
          v-else
          @click="handleClick(item)"
          :disabled="item.disabled"
          class="menu-item w-full flex items-center gap-2 px-3 py-1.5 text-[12.5px] transition-colors cursor-pointer text-left border-0 bg-transparent"
          :class="{
            'text-text-4 cursor-not-allowed': item.disabled,
            'text-text hover:bg-bg-hover': !item.disabled && !item.danger,
            'text-red-400 hover:bg-red-500/10': item.danger,
          }"
        >
          <span v-if="item.icon" class="w-4 text-center">{{ item.icon }}</span>
          <span>{{ item.label }}</span>
        </button>
      </template>
    </div>
  </Teleport>
</template>

