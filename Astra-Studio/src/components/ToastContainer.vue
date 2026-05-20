<script setup lang="ts">
import { useToast, type Toast } from '../composables/useToast'
import { CheckCircle, XCircle, AlertTriangle, Info, Loader2, X } from 'lucide-vue-next'

const { toasts, remove } = useToast()

const icons: Record<Toast['type'], typeof CheckCircle> = {
  success: CheckCircle,
  error: XCircle,
  warning: AlertTriangle,
  info: Info,
  loading: Loader2,
}

const colors: Record<Toast['type'], string> = {
  success: 'border-emerald-500/30 bg-emerald-500/10 text-emerald-400',
  error: 'border-red-500/30 bg-red-500/10 text-red-400',
  warning: 'border-amber-500/30 bg-amber-500/10 text-amber-400',
  info: 'border-sky-500/30 bg-sky-500/10 text-sky-400',
  loading: 'border-blue-500/30 bg-blue-500/10 text-blue-400',
}

const iconColors: Record<Toast['type'], string> = {
  success: 'text-emerald-400',
  error: 'text-red-400',
  warning: 'text-amber-400',
  info: 'text-sky-400',
  loading: 'text-blue-400 animate-spin',
}
</script>

<template>
  <Teleport to="body">
    <div class="fixed top-4 right-4 z-[9999] flex flex-col gap-2.5 max-w-[380px] w-full pointer-events-none">
      <TransitionGroup
        name="toast"
        tag="div"
        class="flex flex-col gap-2.5 w-full"
      >
        <div
          v-for="toast in toasts"
          :key="toast.id"
          class="pointer-events-auto group relative flex items-start gap-3 rounded-xl border px-4 py-3.5 shadow-lg shadow-black/20 backdrop-blur-sm transition-all duration-200"
          :class="[colors[toast.type]]"
          @mouseenter="toast.dismissible !== false && remove(toast.id)"
        >
          <component :is="icons[toast.type]" :class="['mt-0.5 shrink-0', iconColors[toast.type]]" :size="18" />
          <div class="min-w-0 flex-1">
            <p class="text-[13px] font-semibold leading-snug">{{ toast.title }}</p>
            <p v-if="toast.message" class="mt-1 text-[12px] leading-relaxed opacity-80">{{ toast.message }}</p>
          </div>
          <button
            v-if="toast.type !== 'loading'"
            class="shrink-0 p-0.5 rounded-md opacity-40 hover:opacity-100 transition-opacity cursor-pointer border-0 bg-transparent"
            @click.stop="remove(toast.id)"
          >
            <X :size="14" />
          </button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.toast-enter-active { transition: all 0.25s ease-out }
.toast-leave-active { transition: all 0.2s ease-in }
.toast-enter-from {
  opacity: 0;
  transform: translateX(100%);
}
.toast-leave-to {
  opacity: 0;
  transform: translateX(100%) scale(0.95);
}
.toast-move { transition: transform 0.25s ease-out }
</style>
