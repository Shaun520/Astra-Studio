<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { debug, error } from '../../utils/logger'
import { useToast } from '../../composables/useToast'
import { 
  BookOpen, 
  Upload, 
  Trash2, 
  Loader2, 
  AlertCircle, 
  CheckCircle2, 
  ExternalLink,
  ChevronLeft,
  ChevronRight
} from 'lucide-vue-next'
import { 
  getKnowledgeDocuments, 
  deleteKnowledgeDocument, 
  uploadKnowledgeDocument,
  getKnowledgeDocumentStatus,
  type KnowledgeDocument 
} from '@/services/api'
import { uploadFiles } from '@/services/oss'

const documents = ref<KnowledgeDocument[]>([])
const loading = ref(false)
const uploading = ref(false)
const toast = useToast()
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const pageSize = 10

let pollInterval: number | null = null

async function fetchDocuments() {
  loading.value = true
  try {
    const data = await getKnowledgeDocuments(page.value, pageSize)
    documents.value = data.content
    totalPages.value = data.totalPages
    totalElements.value = data.totalElements
    
    // 如果有正在处理的文档，启动轮询
    const hasProcessing = documents.value.some(doc => doc.status === 'PROCESSING')
    if (hasProcessing && !pollInterval) {
      startPolling()
    } else if (!hasProcessing && pollInterval) {
      stopPolling()
    }
  } catch (e) {
    error('Failed to fetch documents:', e)
  } finally {
    loading.value = false
  }
}

async function handleDelete(id: number) {
  if (!confirm('确定要删除该文档吗？此操作不可恢复。')) return
  
  try {
    await deleteKnowledgeDocument(id)
    await fetchDocuments()
  } catch (e) {
    error('Delete failed:', e)
    toast.fromError(e, '删除失败')
  }
}

async function handleFileUpload(event: Event) {
  const target = event.target as HTMLInputElement
  if (!target.files?.length) return
  
  const file = target.files[0]
  uploading.value = true
  
  try {
    // 1. 上传到 OSS
    const results = await uploadFiles([file])
    if (results.length > 0) {
      // 2. 导入到知识库
      await uploadKnowledgeDocument(results[0].url, file.name)
      // 3. 刷新列表
      await fetchDocuments()
    }
  } catch (e) {
    error('Upload failed:', e)
    toast.fromError(e, '上传失败')
  } finally {
    uploading.value = false
    target.value = '' // 重置 input
  }
}

function startPolling() {
  pollInterval = window.setInterval(async () => {
    const hasProcessing = documents.value.some(doc => doc.status === 'PROCESSING')
    if (!hasProcessing) {
      stopPolling()
      return
    }
    
    // 简单起见，轮询整个列表
    try {
      const data = await getKnowledgeDocuments(page.value, pageSize)
      documents.value = data.content
    } catch (e) {
      error('Polling failed:', e)
    }
  }, 3000)
}

function stopPolling() {
  if (pollInterval) {
    clearInterval(pollInterval)
    pollInterval = null
  }
}

function formatDate(dateStr: string) {
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function formatSize(bytes: number) {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
}

onMounted(() => {
  fetchDocuments()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <div class="knowledge-panel flex flex-col h-full">
    <div class="panel-header flex items-center justify-between mb-4">
      <div class="flex items-center gap-2 text-text-2">
        <BookOpen class="w-4 h-4" />
        <span class="text-[13px] font-medium uppercase tracking-wider">知识库文档</span>
      </div>
      <label class="upload-btn flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-accent text-bg text-[12px] font-medium cursor-pointer transition-opacity hover:opacity-90" :class="{ 'opacity-50 pointer-events-none': uploading }">
        <Upload v-if="!uploading" class="w-3.5 h-3.5" />
        <Loader2 v-else class="w-3.5 h-3.5 animate-spin" />
        <span>{{ uploading ? '上传中...' : '上传文档' }}</span>
        <input type="file" class="hidden" @change="handleFileUpload" accept=".pdf,.doc,.docx,.txt,.md" :disabled="uploading" />
      </label>
    </div>

    <div class="doc-list flex-1 overflow-y-auto min-h-0 space-y-2 pr-1 custom-scrollbar">
      <div v-if="loading && documents.length === 0" class="flex flex-col items-center justify-center py-12 text-text-3">
        <Loader2 class="w-6 h-6 animate-spin mb-2 opacity-20" />
        <span class="text-[12px]">加载中...</span>
      </div>
      
      <div v-else-if="documents.length === 0" class="flex flex-col items-center justify-center py-12 border border-dashed border-border rounded-xl text-text-3">
        <BookOpen class="w-8 h-8 mb-3 opacity-10" />
        <span class="text-[12px]">暂无文档，请上传</span>
      </div>

      <div v-for="doc in documents" :key="doc.id" class="doc-item group p-3 rounded-xl border border-border bg-bg-2/50 hover:border-border-2 transition-all">
        <div class="flex items-start justify-between gap-3">
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2 mb-1">
              <span class="text-[13px] font-medium text-text truncate" :title="doc.filename">{{ doc.filename }}</span>
              <div class="status-badge flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-bold uppercase" :class="{
                'bg-warning/10 text-warning': doc.status === 'PROCESSING',
                'bg-success/10 text-success': doc.status === 'READY',
                'bg-danger/10 text-danger': doc.status === 'FAILED'
              }">
                <Loader2 v-if="doc.status === 'PROCESSING'" class="w-2.5 h-2.5 animate-spin" />
                <CheckCircle2 v-else-if="doc.status === 'READY'" class="w-2.5 h-2.5" />
                <AlertCircle v-else class="w-2.5 h-2.5" />
                {{ doc.status }}
              </div>
            </div>
            <div class="flex items-center gap-3 text-[11px] text-text-3 font-mono">
              <span>{{ formatDate(doc.createdAt) }}</span>
              <span v-if="doc.chunkCount">{{ doc.chunkCount }} 块</span>
              <span v-if="doc.fileSize">{{ formatSize(doc.fileSize) }}</span>
            </div>
            <div v-if="doc.errorMessage" class="mt-2 p-2 rounded bg-danger/5 text-danger text-[11px] leading-relaxed border border-danger/10">
              {{ doc.errorMessage }}
            </div>
          </div>
          <button @click="handleDelete(doc.id)" class="p-1.5 rounded-lg text-text-3 hover:bg-danger/10 hover:text-danger transition-colors opacity-0 group-hover:opacity-100">
            <Trash2 class="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>

    <div v-if="totalPages > 1" class="pagination flex items-center justify-center gap-4 mt-4 pt-4 border-t border-border">
      <button @click="page--; fetchDocuments()" :disabled="page === 0" class="p-1 rounded hover:bg-bg-2 disabled:opacity-20">
        <ChevronLeft class="w-4 h-4" />
      </button>
      <span class="text-[11px] font-mono text-text-3">{{ page + 1 }} / {{ totalPages }}</span>
      <button @click="page++; fetchDocuments()" :disabled="page >= totalPages - 1" class="p-1 rounded hover:bg-bg-2 disabled:opacity-20">
        <ChevronRight class="w-4 h-4" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.custom-scrollbar::-webkit-scrollbar {
  width: 4px;
}
.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: var(--color-border);
  border-radius: 10px;
}
.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  background: var(--color-border-2);
}
</style>
