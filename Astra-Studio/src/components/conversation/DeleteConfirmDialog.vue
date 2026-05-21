// 会话管理组件 - 删除确认对话框
<script setup lang="ts">
import { AlertTriangle } from 'lucide-vue-next'

const props = defineProps<{
  visible: boolean
  title?: string
  message?: string
}>()

const emit = defineEmits<{
  (e: 'confirm'): void
  (e: 'cancel'): void
}>()

function handleConfirm() {
  emit('confirm')
}

function handleCancel() {
  emit('cancel')
}
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="visible" class="dialog-overlay fixed inset-0 z-50 bg-black/40 grid place-items-center" @click.self="handleCancel">
        <div class="dialog-content w-[340px] bg-bg border border-border rounded-xl p-5 shadow-2xl">
          <div class="flex items-start gap-3 mb-4">
            <div class="icon-wrap w-9 h-9 rounded-lg bg-red-500/10 grid place-items-center shrink-0">
              <AlertTriangle class="w-4.5 h-4.5 text-red-400" />
            </div>
            <div>
              <h3 class="text-[14px] font-semibold text-text">{{ title || '纭鍒犻櫎' }}</h3>
              <p class="text-[12.5px] text-text-3 mt-1 leading-relaxed">{{ message || '鍒犻櫎鍚庢棤娉曟仮澶嶏紝纭畾瑕佸垹闄よ繖涓璇濆悧锛? }}</p>
            </div>
          </div>
          <div class="flex justify-end gap-2.5">
            <button
              @click="handleCancel"
              class="px-3.5 py-1.5 text-[12.5px] text-text-2 bg-bg-2 hover:bg-bg-hover rounded-lg transition-colors border-0 cursor-pointer"
            >
              鍙栨秷
            </button>
            <button
              @click="handleConfirm"
              class="px-3.5 py-1.5 text-[12.5px] text-white bg-red-500 hover:bg-red-600 rounded-lg transition-colors border-0 cursor-pointer"
            >
              纭鍒犻櫎
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.fade-enter-active, .fade-leave-active { transition: opacity 0.15s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>

