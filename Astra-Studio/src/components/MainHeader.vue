<script setup lang="ts">
import { Share2, History, MoreHorizontal, ChevronDown, Pencil, Check, Link2, Copy, Download, QrCode, Clock, Trash2, RotateCcw, Pin, MoreVertical, Plus, Sparkles } from 'lucide-vue-next'
import { ref, inject, onMounted, onUnmounted, type Ref } from 'vue'

const currentModel = ref('auto')
const isOnline = ref(true)
const showDropdown = ref(false)
const showShare = ref(false)
const showHistory = ref(false)
const showMore = ref(false)

// 注入新会话函数
const startNewSession = inject<() => void>('startNewSession')

// 注入全局模型状态（直接修改，绕过 emit）
const selectedModel = inject<Ref<string>>('selectedModel')

// 模型列表：value=API名称（传给后端），name=显示名称（UI展示）
const models = [
  { value: 'auto', name: 'auto', desc: '智能识别任务类型，自动选择最优模型', tag: '推荐', icon: 'sparkles' },
  { value: 'glm-5', name: 'GLM-5', desc: '通用对话、多轮交互', tag: '默认' },
  { value: 'deepseek-v4-flash', name: 'DeepSeek V4 Flash', desc: '代码生成、逻辑推理', tag: '' },
  { value: 'qwen3.6-flash-2026-04-16', name: 'Qwen 3.6 Flash', desc: '中文理解、文本生成', tag: '' },
]

function selectModel(model: typeof models[0]) {
  currentModel.value = model.name
  if (selectedModel) {
    selectedModel.value = model.value
  }
  closeAll()
}

function toggleDropdown() { toggle('showDropdown') }
function toggleShare() { toggle('showShare') }
function toggleHistory() { toggle('showHistory') }
function toggleMore() { toggle('showMore') }

function toggle(key: string) {
  const val = key === 'showDropdown' ? showDropdown : key === 'showShare' ? showShare : key === 'showHistory' ? showHistory : showMore
  const wasOpen = val.value
  closeAll()
  if (!wasOpen) val.value = true
}

function closeAll() {
  showDropdown.value = false
  showShare.value = false
  showHistory.value = false
  showMore.value = false
}

function closePopovers(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (!target.closest('.header-actions')) closeAll()
}

onMounted(() => document.addEventListener('click', closePopovers))
onUnmounted(() => document.removeEventListener('click', closePopovers))

const shareItems = [
  { icon: Link2, label: '复制链接', action: () => {} },
  { icon: Copy, label: '复制内容', action: () => {} },
  { icon: Download, label: '导出对话', action: () => {} },
  { icon: QrCode, label: '二维码分享', action: () => {} },
]

const historyItems = [
  { icon: Clock, label: '查看历史记录', sub: '共 28 条', action: () => {} },
  { icon: RotateCcw, label: '恢复上一版本', sub: '', action: () => {} },
  { icon: Pin, label: '固定此对话', sub: '', action: () => {} },
  { icon: Trash2, label: '清空并新建会话', sub: '', danger: true, action: () => startNewSession?.() },
]

const moreItems = [
  { icon: Download, label: '导出为 Markdown', action: () => {} },
  { icon: Copy, label: '复制全部内容', action: () => {} },
  { icon: Pin, label: '添加到收藏夹', action: () => {} },
]
</script>

<template>
  <header class="h-header-h flex items-center px-7 border-b border-border gap-5 shrink-0">
    <div class="breadcrumb flex items-center gap-2.5 flex-1 min-w-0">
      <span class="crumb text-[13.5px] text-text-3">创作</span>
      <span class="sep text-text-4">/</span>
      <span class="title text-[15px] font-medium flex items-center gap-2 overflow-hidden text-ellipsis whitespace-nowrap group">
        长篇播客视觉方向
        <Pencil class="edit-ic w-3.5 h-3.5 text-text-4 opacity-0 transition-opacity duration-150 group-hover:opacity-100" />
      </span>
    </div>

    <div class="header-actions flex items-center gap-2">
      <!-- 新会话 -->
      <button
        class="icon-btn w-9 h-9 rounded-lg grid place-items-center text-text-2 cursor-pointer transition-all duration-150 hover:bg-bg-hover hover:text-accent bg-transparent border-0"
        @click.stop="startNewSession?.()"
        title="新会话"
      >
        <Plus class="w-4 h-4" />
      </button>

      <!-- 模型选择器 -->
      <div class="model-selector relative">
        <button
          class="pill-btn inline-flex items-center gap-1.5 px-3 py-[7px] border rounded-lg bg-transparent text-text-2 text-[12.5px] font-sans cursor-pointer transition-all duration-150 hover:text-text border-accent-line hover:bg-bg-2"
          @click.stop="toggleDropdown"
        >
          <span class="dot w-1.5 h-1.5 rounded-full bg-success shadow-[0_0_0_3px_oklch(76%_0.13_155_/_0.18)]"></span>
          <span>{{ currentModel }}</span>
          <ChevronDown class="w-3.5 h-3.5 transition-transform duration-200" :class="{ 'rotate-180': showDropdown }" />
        </button>
        <Transition enter-active-class="transition duration-150 ease-out" enter-from-class="opacity-0 -translate-y-1 scale-95" enter-to-class="opacity-100 translate-y-0 scale-100" leave-active-class="transition duration-100 ease-in" leave-from-class="opacity-100 translate-y-0 scale-100" leave-to-class="opacity-0 -translate-y-1 scale-95">
          <div v-if="showDropdown" class="dropdown absolute top-full right-0 mt-2 w-[260px] bg-bg-2 border border-border rounded-xl shadow-lg shadow-black/10 overflow-hidden z-50">
            <div class="p-2">
              <button v-for="m in models" :key="m.value" class="model-item w-full flex items-start gap-2.5 px-2.5 py-2 rounded-lg cursor-pointer transition-colors duration-100 text-left" :class="[currentModel === m.name ? 'bg-accent-soft' : 'hover:bg-bg-hover', m.value === 'auto' ? 'auto-item' : '']" @click.stop="selectModel(m)">
                <div v-if="m.icon === 'sparkles'" class="w-4 h-4 mt-0.5 shrink-0">
                  <Sparkles class="w-full h-full text-accent" />
                </div>
                <div class="flex-1 min-w-0 pt-0.5">
                  <div class="flex items-center gap-1.5">
                    <span class="text-[13px] font-medium text-text">{{ m.name }}</span>
                    <span v-if="m.tag" class="text-[9.5px] px-1.5 py-[1px] rounded-[3px] font-medium leading-none" :class="m.tag === '推荐' ? 'bg-gradient-to-r from-accent/30 to-purple-500/30 text-accent border border-accent/20' : m.tag === '默认' ? 'bg-accent/20 text-accent' : 'bg-bg-hover text-text-3'">{{ m.tag }}</span>
                  </div>
                  <span class="text-[11px] text-text-4 mt-0.5 block">{{ m.desc }}</span>
                </div>
                <Check v-if="currentModel === m.name" class="w-4 h-4 text-accent shrink-0 mt-0.5" />
              </button>
            </div>
            <div class="border-t border-border px-3 py-2 flex items-center justify-between">
              <span class="text-[10.5px] text-text-4">模型版本 v2.4.1</span>
              <span class="text-[10.5px] text-text-4 cursor-default">共 4 个模型</span>
            </div>
          </div>
        </Transition>
      </div>

      <!-- 分享 -->
      <div class="relative">
        <button class="icon-btn w-9 h-9 rounded-lg grid place-items-center text-text-2 cursor-pointer transition-all duration-150 hover:bg-bg-hover hover:text-text bg-transparent border-0 relative"
          :class="{ 'bg-accent-soft text-accent': showShare }"
          @click.stop="toggleShare" title="分享">
          <Share2 class="w-4 h-4" />
        </button>
        <Transition enter-active-class="transition duration-150 ease-out" enter-from-class="opacity-0 -translate-y-1 scale-95" enter-to-class="opacity-100 translate-y-0 scale-100" leave-active-class="transition duration-100 ease-in" leave-from-class="opacity-100 translate-y-0 scale-100" leave-to-class="opacity-0 -translate-y-1 scale-95">
          <div v-if="showShare" class="absolute top-full right-0 mt-2 w-[180px] bg-bg-2 border border-border rounded-xl shadow-lg shadow-black/10 overflow-hidden z-50 py-1.5">
            <button v-for="(item, i) in shareItems" :key="i" class="w-full flex items-center gap-2.5 px-3 py-2 text-[13px] text-text hover:bg-bg-hover transition-colors cursor-pointer" @click.stop="item.action(); closeAll()">
              <component :is="item.icon" class="w-4 h-4 text-text-3" />
              <span>{{ item.label }}</span>
            </button>
          </div>
        </Transition>
      </div>

      <!-- 历史 -->
      <div class="relative">
        <button class="icon-btn w-9 h-9 rounded-lg grid place-items-center text-text-2 cursor-pointer transition-all duration-150 hover:bg-bg-hover hover:text-text bg-transparent border-0"
          :class="{ 'bg-accent-soft text-accent': showHistory }"
          @click.stop="toggleHistory" title="历史">
          <History class="w-4 h-4" />
        </button>
        <Transition enter-active-class="transition duration-150 ease-out" enter-from-class="opacity-0 -translate-y-1 scale-95" enter-to-class="opacity-100 translate-y-0 scale-100" leave-active-class="transition duration-100 ease-in" leave-from-class="opacity-100 translate-y-0 scale-100" leave-to-class="opacity-0 -translate-y-1 scale-95">
          <div v-if="showHistory" class="absolute top-full right-0 mt-2 w-[210px] bg-bg-2 border border-border rounded-xl shadow-lg shadow-black/10 overflow-hidden z-50 py-1.5">
            <button v-for="(item, i) in historyItems" :key="i" class="w-full flex items-center gap-2.5 px-3 py-2 text-[13px] transition-colors cursor-pointer text-left" :class="item.danger ? 'text-danger hover:bg-danger/8' : 'text-text hover:bg-bg-hover'" @click.stop="item.action(); closeAll()">
              <component :is="item.icon" class="w-4 h-4 shrink-0" :class="item.danger ? '' : 'text-text-3'" />
              <div class="flex-1 min-w-0">
                <span>{{ item.label }}</span>
                <span v-if="item.sub" class="block text-[10.5px] text-text-4 mt-px">{{ item.sub }}</span>
              </div>
            </button>
          </div>
        </Transition>
      </div>

      <!-- 更多 -->
      <div class="relative">
        <button class="icon-btn w-9 h-9 rounded-lg grid place-items-center text-text-2 cursor-pointer transition-all duration-150 hover:bg-bg-hover hover:text-text bg-transparent border-0"
          :class="{ 'bg-accent-soft text-accent': showMore }"
          @click.stop="toggleMore" title="更多">
          <MoreHorizontal class="w-4 h-4" />
        </button>
        <Transition enter-active-class="transition duration-150 ease-out" enter-from-class="opacity-0 -translate-y-1 scale-95" enter-to-class="opacity-100 translate-y-0 scale-100" leave-active-class="transition duration-100 ease-in" leave-from-class="opacity-100 translate-y-0 scale-100" leave-to-class="opacity-0 -translate-y-1 scale-95">
          <div v-if="showMore" class="absolute top-full right-0 mt-2 w-[190px] bg-bg-2 border border-border rounded-xl shadow-lg shadow-black/10 overflow-hidden z-50 py-1.5">
            <button v-for="(item, i) in moreItems" :key="i" class="w-full flex items-center gap-2.5 px-3 py-2 text-[13px] text-text hover:bg-bg-hover transition-colors cursor-pointer" @click.stop="item.action(); closeAll()">
              <component :is="item.icon" class="w-4 h-4 text-text-3" />
              <span>{{ item.label }}</span>
            </button>
            <div class="border-t border-border mx-2 my-1"></div>
            <button class="w-full flex items-center gap-2.5 px-3 py-2 text-[13px] text-danger hover:bg-danger/8 transition-colors cursor-pointer" @click.stop="closeAll()">
              <Trash2 class="w-4 h-4" />
              <span>删除此项目</span>
            </button>
          </div>
        </Transition>
      </div>
    </div>
  </header>
</template>

<style scoped>
.auto-item {
  background: linear-gradient(135deg, oklch(96% 0.02 250 / 0.5) 0%, oklch(96% 0.03 280 / 0.3) 100%);
  border: 1px solid oklch(70% 0.15 250 / 0.2);
  position: relative;
  overflow: hidden;
}

.auto-item::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, #6366f1, #8b5cf6, #a855f7);
  opacity: 0.6;
}

.auto-item:hover {
  background: linear-gradient(135deg, oklch(94% 0.03 250 / 0.6) 0%, oklch(94% 0.04 280 / 0.4) 100%);
  border-color: oklch(65% 0.18 250 / 0.3);
}
</style>
