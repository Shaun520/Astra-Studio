<script setup lang="ts">
import { ref, computed } from 'vue'
import { Download, Loader2, Check, FileText } from 'lucide-vue-next'
import { downloadFileFromUrl } from '../../utils/file'

interface Props {
  fileName: string
  downloadUrl: string
  fileSize?: string
  compact?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  compact: false
})

const isDownloading = ref(false)
const status = ref<'idle' | 'downloading' | 'success' | 'error'>('idle')

const displayName = computed(() => {
  const name = props.fileName
  if (name.length <= 25) return name
  const ext = name.includes('.') ? '.' + name.split('.').pop() : ''
  const base = ext ? name.slice(0, -ext.length) : name
  return base.slice(0, 12) + '...' + base.slice(-8) + ext
})

async function handleDownload(e: Event) {
  e.preventDefault()
  e.stopPropagation()
  
  if (isDownloading.value) return
  
  isDownloading.value = true
  status.value = 'downloading'
  
  try {
    await downloadFileFromUrl(props.downloadUrl, props.fileName)
    status.value = 'success'
    
    setTimeout(() => {
      status.value = 'idle'
      isDownloading.value = false
    }, 2000)
  } catch (error) {
    console.error('[PdfButton] Download failed:', error)
    status.value = 'error'
    isDownloading.value = false
    
    setTimeout(() => {
      status.value = 'idle'
    }, 2000)
  }
}
</script>

<template>
  <component
    :is="compact ? 'span' : 'div'"
    class="pdf-download-btn"
    :class="[status, { compact }]"
    @click="handleDownload"
  >
    <template v-if="status === 'downloading'">
      <Loader2 class="icon spinning" />
      <span v-if="!compact" class="label">下载中</span>
    </template>
    <template v-else-if="status === 'success'">
      <Check class="icon success" />
      <span v-if="!compact" class="label">已下载</span>
    </template>
    <template v-else-if="status === 'error'">
      <Download class="icon error" />
      <span v-if="!compact" class="label">重试</span>
    </template>
    <template v-else>
      <FileText v-if="!compact" class="icon file-icon" />
      <Download class="icon download-icon" />
      <span v-if="!compact" class="label">
        {{ fileSize ? `${displayName} (${fileSize})` : displayName }}
      </span>
      <span v-else class="sr-only">下载 {{ fileName }}</span>
    </template>
  </component>
</template>

<style scoped>
.pdf-download-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 2px 8px rgba(239, 68, 68, 0.25);
  user-select: none;
  position: relative;
  overflow: hidden;
}

.pdf-download-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(255, 255, 255, 0.2),
    transparent
  );
  transition: left 0.5s ease;
}

.pdf-download-btn:hover::before {
  left: 100%;
}

.pdf-download-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(239, 68, 68, 0.35);
}

.pdf-download-btn:active:not(:disabled) {
  transform: translateY(0);
  box-shadow: 0 2px 8px rgba(239, 68, 68, 0.25);
}

.pdf-download-btn.compact {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border-radius: 8px;
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  box-shadow: 0 2px 6px rgba(239, 68, 68, 0.3);
  vertical-align: middle;
  margin: 0 4px;
}

.pdf-download-btn.compact:hover {
  transform: scale(1.1);
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.4);
}

.pdf-download-btn.downloading {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
  box-shadow: 0 2px 8px rgba(245, 158, 11, 0.25);
}

.pdf-download-btn.success {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.25);
}

.pdf-download-btn.error {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  animation: shake 0.4s ease-in-out;
}

.icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  transition: transform 0.2s ease;
}

.compact .icon {
  width: 16px;
  height: 16px;
}

.icon.file-icon {
  color: rgba(255, 255, 255, 0.9);
}

.icon.download-icon {
  animation: bounce-down 2s infinite;
}

@keyframes bounce-down {
  0%, 20%, 50%, 80%, 100% {
    transform: translateY(0);
  }
  40% {
    transform: translateY(2px);
  }
  60% {
    transform: translateY(1px);
  }
}

.icon.success {
  color: #fff;
  animation: scale-in 0.3s ease-out;
}

.icon.error {
  color: #fff;
}

.icon.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

@keyframes scale-in {
  0% {
    transform: scale(0);
  }
  50% {
    transform: scale(1.2);
  }
  100% {
    transform: scale(1);
  }
}

@keyframes shake {
  0%, 100% {
    transform: translateX(0);
  }
  20%, 60% {
    transform: translateX(-4px);
  }
  40%, 80% {
    transform: translateX(4px);
  }
}

.label {
  font-weight: 600;
  letter-spacing: 0.01em;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
</style>
