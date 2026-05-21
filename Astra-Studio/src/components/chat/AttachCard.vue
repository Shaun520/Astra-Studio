<script setup lang="ts">
import { computed, inject } from 'vue'
import { FileText, Download, Eye } from 'lucide-vue-next'

const openImagePreview = inject<(images: { src: string; alt?: string }[], index?: number) => void>('openImagePreview')

interface Props {
  type: 'image' | 'video' | 'audio' | 'code' | 'file'
  tag?: string
  title?: string
  subtitle?: string
  meta?: string
  codeContent?: string
  src?: string
  fileName?: string
  fileSize?: string
  fileType?: string
}

const props = defineProps<Props>()

const fileEmoji = computed(() => {
  if (!props.fileName) return '📎'
  const name = props.fileName.toLowerCase()
  if (name.endsWith('.pdf')) return '📄'
  if (name.endsWith('.doc') || name.endsWith('.docx')) return '📝'
  if (name.endsWith('.xls') || name.endsWith('.xlsx')) return '📊'
  if (name.endsWith('.ppt') || name.endsWith('.pptx')) return '📽️'
  if (name.endsWith('.zip') || name.endsWith('.rar')) return '📦'
  if (name.endsWith('.txt')) return '📃'
  if (name.endsWith('.md')) return '📋'
  return '📎'
})

const fileColor = computed(() => {
  if (!props.fileType) return 'bg-accent-soft text-accent'
  const t = props.fileType.toLowerCase()
  if (t === 'pdf') return 'bg-red-soft text-red'
  if (t === 'word') return 'bg-blue-soft text-blue'
  if (t === 'excel') return 'bg-green-soft text-green'
  if (t === 'ppt') return 'bg-orange-soft text-orange'
  return 'bg-accent-soft text-accent'
})

function handleImageClick() {
  if (props.type !== 'image' || !props.src) return
  if (openImagePreview) {
    openImagePreview([{ src: props.src, alt: props.fileName || props.title }])
  }
}
</script>

<template>
  <div class="attach-card my-[18px] border border-border rounded-xl overflow-hidden max-w-[320px] bg-bg-2">
    <!-- 图片类型 -->
    <div v-if="type === 'image' && src" class="attach-img aspect-square relative overflow-hidden cursor-pointer group/img" @click="handleImageClick">
      <img :src="src" alt="" class="w-full h-full object-cover group-hover/img:scale-[1.03] transition-transform duration-200" loading="lazy" />
      <span v-if="tag" class="attach-img-tag absolute top-3 left-3 text-[10.5px] text-white/70 tracking-[0.14em] uppercase px-1.5 py-[2px] bg-black/32 rounded-[3px] backdrop-blur-[4px] z-10 font-medium">{{ tag }}</span>
      <div v-if="title" class="attach-img-overlay absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent flex flex-col p-[18px] justify-end">
        <div class="attach-img-title font-serif text-[44px] leading-none text-white mt-auto tracking-[-0.015em]" v-html="title"></div>
        <div v-if="subtitle" class="attach-img-sub font-serif italic text-sm text-white/80 mt-2">{{ subtitle }}</div>
      </div>
    </div>

    <div v-else-if="type === 'image'" class="attach-img aspect-square bg-gradient-to-br from-[radial-gradient(120%_80%_at_30%_20%,oklch(82%_0.13_65_/_0.45),transparent_55%)] via-[radial-gradient(80%_70%_at_80%_90%,oklch(60%_0.10_30_/_0.35),transparent_60%)] to-[linear-gradient(180deg,oklch(28%_0.04_55),oklch(19%_0.02_60))] relative flex flex-col p-[18px]">
      <span v-if="tag" class="attach-img-tag text-[10.5px] text-[oklch(95%_0.01_80_/_0.7)] tracking-[0.14em] uppercase">{{ tag }}</span>
      <div v-if="title" class="attach-img-title font-serif text-[44px] leading-none text-[oklch(96%_0.01_80)] mt-auto tracking-[-0.015em]" v-html="title"></div>
      <div v-if="subtitle" class="attach-img-sub font-serif italic text-sm text-[oklch(96%_0.01_80_/_0.8)] mt-2">{{ subtitle }}</div>
    </div>

    <!-- 文件类型（PDF、Word等） -->
    <div v-else-if="type === 'file'" class="file-card p-4">
      <div class="file-header flex items-center gap-3 mb-3">
        <div class="file-icon w-11 h-11 rounded-xl grid place-items-center text-2xl shrink-0" :class="fileColor">
          {{ fileEmoji }}
        </div>
        <div class="file-info min-w-0 flex-1">
          <p class="file-name text-[13px] font-semibold text-text truncate" :title="fileName">{{ fileName || '未命名文件' }}</p>
          <p class="file-detail text-[11px] text-text-4 font-mono">{{ fileType || '文件' }}{{ fileSize ? ` · ${fileSize}` : '' }}</p>
        </div>
      </div>
      <div class="file-preview bg-bg-hover rounded-lg overflow-hidden min-h-[48px] max-h-[120px] flex items-center justify-center">
        <template v-if="src">
          <img v-if="fileName?.toLowerCase().match(/\.(png|jpe?g|gif|webp|svg)$/)" :src="src" alt="" class="max-h-[120px] object-contain" />
          <div v-else class="flex items-center gap-2 px-4 py-3 text-text-4 text-xs">
            <FileText class="w-5 h-5 opacity-40" />
            <span>文件预览不可用</span>
          </div>
        </template>
        <div v-else class="flex items-center gap-2 px-4 py-3 text-text-4 text-xs">
          <FileText class="w-5 h-5 opacity-40" />
          <span>已上传 · 点击查看</span>
        </div>
      </div>
    </div>

    <!-- 视频类型 -->
    <div v-if="type === 'video'" class="rc-video aspect-square bg-gradient-to-br from-[oklch(28%_0.04_220)] to-[oklch(18%_0.02_230)] grid place-items-center">
      <div class="play w-8 h-8 rounded-full bg-[oklch(96%_0.01_80_/_0.95)] text-[oklch(20%_0.01_60)] grid place-items-center">
        <svg class="w-3.5 h-3.5 ml-0.5" fill="currentColor" viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>
      </div>
    </div>

    <!-- 音频类型 -->
    <div v-if="type === 'audio'" class="rc-audio bg-bg-2 flex items-center px-3.5">
      <div class="wave flex items-center gap-[2px] w-full h-9">
        <i v-for="n in 30" :key="n" class="flex-1 bg-accent rounded-sm block" :style="{ height: (Math.random() * 28 + 4) + 'px', opacity: 0.45 + Math.random() * 0.5 }"></i>
      </div>
    </div>

    <!-- 代码类型 -->
    <div v-if="type === 'code'" class="rc-code bg-bg-2 p-[22px_14px_14px] font-mono text-[9.5px] leading-relaxed text-text-3 overflow-hidden">
      <pre><code><span class="text-accent k">const</span> tokens = {<br/>  &nbsp;bg: <span class="text-success s">"#1a1816"</span>,<br/>  &nbsp;ink: <span class="text-success s">"#f4f0ea"</span>,<br/>  &nbsp;accent: <span class="text-success s">"#e3a857"</span>,<br/>};</code></pre>
    </div>

    <!-- 底部元信息栏 -->
    <div v-if="type !== 'file'" class="attach-meta flex items-center px-3.5 py-2.5 text-[11.5px] text-text-3 border-t border-border gap-1.5 font-mono">
      <span>{{ meta || 'Astra Vision · 1024×1024 · 用时 2.4s' }}</span>
      <div class="actions ml-auto flex gap-px">
        <button class="icon-btn w-6 h-6 rounded-[5px] grid place-items-center text-text-3 cursor-pointer transition-colors duration-150 hover:text-text bg-transparent border-0" title="重新生成">
          <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M23 4v6h-6M1 20v-6h6"/><path d="M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"/></svg>
        </button>
        <button class="icon-btn w-6 h-6 rounded-[5px] grid place-items-center text-text-3 cursor-pointer transition-colors duration-150 hover:text-text bg-transparent border-0" title="放大">
          <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"/></svg>
        </button>
        <button class="icon-btn w-6 h-6 rounded-[5px] grid place-items-center text-text-3 cursor-pointer transition-colors duration-150 hover:text-text bg-transparent border-0" title="下载">
          <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M7 10l5 5 5-5M12 15V3"/></svg>
        </button>
      </div>
    </div>

    <!-- 文件类型的底部操作栏 -->
    <div v-if="type === 'file'" class="attach-meta flex items-center px-4 py-2.5 text-[11.5px] text-text-3 border-t border-border gap-1.5 font-mono">
      <span>{{ meta || `${fileType || '文件'} · ${fileSize || '--'}` }}</span>
      <div class="actions ml-auto flex gap-px">
        <button class="icon-btn w-6 h-6 rounded-[5px] grid place-items-center text-text-3 cursor-pointer transition-colors duration-150 hover:text-text bg-transparent border-0" title="预览">
          <Eye class="w-3.5 h-3.5" />
        </button>
        <button class="icon-btn w-6 h-6 rounded-[5px] grid place-items-center text-text-3 cursor-pointer transition-colors duration-150 hover:text-text bg-transparent border-0" title="下载">
          <Download class="w-3.5 h-3.5" />
        </button>
      </div>
    </div>
  </div>
</template>
