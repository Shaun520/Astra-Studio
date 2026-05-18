<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  MessageSquare, Image, Video, Code2, Languages,
  Clock, Bookmark, Folder, ChevronsUpDown, Settings,
  Sparkles, ChevronDown, ChevronRight
} from 'lucide-vue-next'

const activeNav = ref('对话')
const toolsOpen = ref(false)

const emit = defineEmits<{
  (e: 'navigate', label: string): void
}>()

const toolItems = [
  { icon: Image, label: '图像生成' },
  { icon: Video, label: '视频生成', badge: 'BETA' },
  { icon: Code2, label: '代码助手' },
  { icon: Languages, label: '翻译润色' },
]

const libraryItems = [
  { icon: Clock, label: '最近', count: 28 },
  { icon: Bookmark, label: '收藏', count: 7 },
  { icon: Folder, label: '项目', count: 3 },
]

function handleNav(label: string) {
  activeNav.value = label
  emit('navigate', label)
}

function toggleTools() {
  toolsOpen.value = !toolsOpen.value
}

function selectTool(label: string) {
  activeNav.value = label
  emit('navigate', label)
}

const isToolActive = computed(() => {
  return toolItems.some(t => t.label === activeNav.value)
})
</script>

<template>
  <aside class="sidebar flex flex-col h-full bg-bg border-r border-border py-[18px] px-[14px] gap-[18px] overflow-y-auto">
    <div class="brand flex items-center gap-2.5 px-2">
      <div class="brand-mark w-6 h-6 rounded-md bg-accent grid place-items-center text-bg font-serif italic text-[17px] tracking-tight">A</div>
      <div class="brand-name font-serif text-xl tracking-tight"><em>Astra</em></div>
    </div>

    <div class="workspace flex items-center gap-2 px-2.5 py-2 border border-border rounded-lg cursor-pointer transition-colors duration-200 hover:border-border-2 hover:bg-bg-2">
      <div class="ws-avatar w-[22px] h-[22px] rounded-[5px] bg-[oklch(45%_0.04_35)] text-xs font-semibold grid place-items-center text-text">林</div>
      <div class="ws-name text-[13px] flex-1">个人工作空间</div>
      <ChevronsUpDown class="w-3.5 h-3.5 text-text-3" />
    </div>

    <div class="nav-group flex flex-col gap-px">
      <div class="nav-label text-[10.5px] uppercase tracking-[0.14em] text-text-4 px-2.5 pb-1.5 pt-2 font-medium">创作</div>

      <!-- 对话 - 独立项 -->
      <a
        class="nav-item relative flex items-center gap-2.5 px-2.5 py-[7px] rounded-lg text-text-2 cursor-pointer text-[13.5px] transition-colors duration-150"
        :class="{ 'active': activeNav === '对话' }"
        @click="handleNav('对话')"
      >
        <MessageSquare class="icon w-4 h-4 shrink-0 opacity-85" />
        <span>对话</span>
      </a>

      <!-- 创作工具 - 下拉 Tab -->
      <div>
        <a
          class="nav-item relative flex items-center gap-2.5 px-2.5 py-[7px] rounded-lg text-text-2 cursor-pointer text-[13.5px] transition-colors duration-150"
          :class="{ 'active': isToolActive || toolsOpen }"
          @click="toggleTools()"
        >
          <Sparkles class="icon w-4 h-4 shrink-0 opacity-85" />
          <span class="flex-1">创作工具</span>
          <ChevronRight
            v-if="!toolsOpen"
            class="w-3.5 h-3.5 text-text-4 transition-transform duration-200"
          />
          <ChevronDown
            v-else
            class="w-3.5 h-3.5 text-text-3 transition-transform duration-200"
          />
        </a>

        <!-- 下拉子菜单 -->
        <Transition enter-active-class="transition-all duration-200 ease-out overflow-hidden" enter-from-class="max-h-0 opacity-0" enter-to-class="max-h-[240px] opacity-100"
          leave-active-class="transition-all duration-150 ease-in overflow-hidden" leave-from-class="max-h-[240px] opacity-100" leave-to-class="max-h-0 opacity-0">
          <div v-if="toolsOpen" class="tool-submenu mt-px flex flex-col gap-px">
            <a
              v-for="item in toolItems"
              :key="item.label"
              class="nav-item relative flex items-center gap-2.5 px-2.5 py-[7px] rounded-lg text-text-2 cursor-pointer text-[13.5px] transition-colors duration-150"
              :class="{ 'active': activeNav === item.label }"
              @click.stop="selectTool(item.label)"
            >
              <component :is="item.icon" class="icon w-4 h-4 shrink-0 opacity-85" />
              <span>{{ item.label }}</span>
              <span v-if="item.badge" class="badge ml-auto text-[9.5px] px-1.5 py-[2px] rounded bg-accent-soft text-accent font-medium tracking-[0.06em]">{{ item.badge }}</span>
            </a>
          </div>
        </Transition>
      </div>
    </div>

    <div class="nav-group flex flex-col gap-px">
      <div class="nav-label text-[10.5px] uppercase tracking-[0.14em] text-text-4 px-2.5 pb-1.5 pt-2 font-medium">资料库</div>
      <a
        v-for="item in libraryItems"
        :key="item.label"
        class="nav-item flex items-center gap-2.5 px-2.5 py-[7px] rounded-lg text-text-2 cursor-pointer text-[13.5px] transition-colors duration-150 hover:bg-bg-hover hover:text-text"
      >
        <component :is="item.icon" class="icon w-4 h-4 shrink-0 opacity-85" />
        <span>{{ item.label }}</span>
        <span class="count ml-auto text-[11px] text-text-4 tabular-nums font-mono">{{ item.count }}</span>
      </a>
    </div>

    <div class="sidebar-footer mt-auto">
      <div class="user-card flex items-center gap-2.5 p-2.5 border border-border rounded-[10px]">
        <div class="user-avatar w-7 h-7 rounded-full bg-[oklch(70%_0.05_200)] text-bg grid place-items-center text-xs font-semibold">L</div>
        <div class="user-info flex-1 min-w-0">
          <div class="user-name text-[13px] font-medium">林深</div>
          <div class="user-credits text-[11px] text-text-3 font-mono tabular-nums">余 <b class="text-accent font-medium">2,840</b> 算力</div>
        </div>
        <button class="icon-btn w-8 h-8 rounded-lg grid place-items-center text-text-2 cursor-pointer transition-colors duration-150 hover:bg-bg-hover hover:text-transparent bg-transparent border-0" title="设置">
          <Settings class="w-4 h-4" />
        </button>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.nav-item.active {
  background: var(--color-bg-2);
  color: var(--color-text);
}
.nav-item.active::before {
  content: '';
  position: absolute;
  left: -14px;
  top: 50%;
  transform: translateY(-50%);
  width: 2px;
  height: 16px;
  background: var(--color-accent);
  border-radius: 0 2px 2px 0;
}
</style>
