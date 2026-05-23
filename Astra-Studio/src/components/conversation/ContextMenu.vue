<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { Trash2 } from 'lucide-vue-next'

const props = defineProps<{
  visible: boolean
  x: number
  y: number
}>()

const emit = defineEmits<{
  (e: 'delete'): void
  (e: 'close'): void
}>()

const menuRef = ref<HTMLElement | null>(null)

function handleDelete() {
  emit('delete')
  emit('close')
}

function handleClickOutside(e: MouseEvent) {
  if (menuRef.value && !menuRef.value.contains(e.target as Node)) {
    emit('close')
  }
}

function handleEscape(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    emit('close')
  }
}

onMounted(() => {
  document.addEventListener('mousedown', handleClickOutside)
  document.addEventListener('keydown', handleEscape)
})

onUnmounted(() => {
  document.removeEventListener('mousedown', handleClickOutside)
  document.removeEventListener('keydown', handleEscape)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="context-menu">
      <div
        v-if="visible"
        ref="menuRef"
        class="context-menu fixed z-50 min-w-[140px] bg-bg border border-border rounded-lg shadow-xl py-1 overflow-hidden"
        :style="{ left: `${x}px`, top: `${y}px` }"
      >
        <button
          @click="handleDelete"
          class="w-full flex items-center gap-2 px-3 py-2 text-left text-[12.5px] text-text-2 hover:bg-bg-hover hover:text-danger transition-colors cursor-pointer border-0"
        >
          <Trash2 class="w-3.5 h-3.5" />
          删除对话
        </button>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.context-menu-enter-active { transition: all 0.15s ease-out; }
.context-menu-leave-active { transition: all 0.1s ease-in; }
.context-menu-enter-from { opacity: 0; transform: scale(0.95); }
.context-menu-leave-to { opacity: 0; transform: scale(0.95); }
</style>
