<script setup lang="ts">
/* 布局组件 - 左侧边栏 */
import { ref, computed, inject, onMounted,type Ref  } from 'vue'
import {
  MessageSquare, Image, Video, Code2, Languages,
  Clock, Bookmark, Folder, ChevronsUpDown, Settings,
  Sparkles, ChevronDown, ChevronRight, Plus
} from 'lucide-vue-next'
import type { ConversationItem } from '@/types/conversation'
import ConversationCard from '../conversation/ConversationCard.vue'
import ConversationSearch from '../conversation/ConversationSearch.vue'
import EmptyState from '../conversation/EmptyState.vue'
import ContextMenu from '../conversation/ContextMenu.vue'
import DeleteConfirmDialog from '../conversation/DeleteConfirmDialog.vue'
import SkeletonLoader from '../common/SkeletonLoader.vue'
import { deleteConversation } from '@/services/api'
import { useToast } from '@/composables/useToast'

const toast = useToast()

const activeNav = ref('对话')
const toolsOpen = ref(false)
const searchKeyword = ref('')
const isLoadingList = ref(false)

// 右键菜单状态
const contextMenu = ref({
  visible: false,
  x: 0,
  y: 0,
  targetId: ''
})

// 删除确认对话框状态
const deleteDialog = ref({
  visible: false,
  targetId: ''
})

const emit = defineEmits<{
  (e: 'navigate', label: string): void
  (e: 'restore', memoryId: string): void
  (e: 'new-conversation'): void
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

// Injected state from App.vue
const currentSessionId = inject<Ref<string>>('currentSessionId')
const conversationList = inject<Ref<ConversationItem[]>>('conversationList')
const refreshConversations = inject<() => Promise<void>>('refreshConversations')

// Computed filtered list
const filteredConversations = computed(() => {
  if (!searchKeyword.value.trim() || !conversationList?.value) return conversationList?.value || []
  const keyword = searchKeyword.value.toLowerCase().trim()
  return conversationList.value.filter(c => 
    c.title.toLowerCase().includes(keyword) || 
    c.lastMessagePreview.toLowerCase().includes(keyword)
  )
})

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

function handleRestore(memoryId: string) {
  emit('restore', memoryId)
}

function handleNewConversation() {
  emit('new-conversation')
}

async function handleSearch(keyword: string) {
  searchKeyword.value = keyword
}

// 右键菜单处理
function handleContextMenu(e: MouseEvent, memoryId: string) {
  e.preventDefault()
  contextMenu.value = {
    visible: true,
    x: e.clientX,
    y: e.clientY,
    targetId: memoryId
  }
}

// 关闭右键菜单
function closeContextMenu() {
  contextMenu.value.visible = false
}

// 从上下文菜单点击删除
function handleContextDelete() {
  deleteDialog.value = {
    visible: true,
    targetId: contextMenu.value.targetId
  }
  closeContextMenu()
}

// 确认删除
async function confirmDelete() {
  const memoryId = deleteDialog.value.targetId
  const isDeletingCurrent = currentSessionId?.value === memoryId
  
  // 如果删除的是当前会话，预先选择临近的会话
  let fallbackMemoryId: string | null = null
  if (isDeletingCurrent && conversationList?.value) {
    const currentIndex = conversationList.value.findIndex(c => c.memoryId === memoryId)
    
    if (conversationList.value.length > 1) {
      // 优先选择下一个会话，如果没有则选择上一个
      const nextIndex = currentIndex < conversationList.value.length - 1 ? currentIndex + 1 : currentIndex - 1
      fallbackMemoryId = conversationList.value[nextIndex]?.memoryId || null
    }
    // 如果只有一个会话且被删除，fallbackMemoryId 保持为 null，后续会创建新会话
  }
  
  try {
    await deleteConversation(memoryId)
    
    // 处理当前会话被删除的情况
    if (isDeletingCurrent) {
      if (fallbackMemoryId) {
        // 切换到临近的会话
        emit('restore', fallbackMemoryId)
      } else {
        // 没有其他会话了，创建新会话
        emit('new-conversation')
      }
    }
    
    // 刷新列表
    if (refreshConversations) {
      await refreshConversations()
    }
    
    toast?.success('删除成功', '对话已被永久删除')
  } catch (e) {
    console.error('[Sidebar] Delete failed:', e)
    toast?.fromError(e, '删除失败')
  } finally {
    deleteDialog.value.visible = false
  }
}

// 取消删除
function cancelDelete() {
  deleteDialog.value.visible = false
}

const isToolActive = computed(() => {
  return toolItems.some(t => t.label === activeNav.value)
})

onMounted(async () => {
  if (refreshConversations) {
    await refreshConversations()
  }
})
</script>

<template>
  <aside class="sidebar flex flex-col h-full bg-bg border-r border-border py-[18px] px-[14px] gap-[18px] overflow-y-auto">
    <div class="brand flex items-center gap-2.5 px-2">
      <div class="brand-mark w-6 h-6 rounded-md bg-accent grid place-items-center text-bg font-serif italic text-[17px] tracking-tight">A</div>
      <div class="brand-name font-serif text-xl tracking-tight"><em>Astra</em></div>
    </div>

    <!-- New Conversation Button -->
    <button
      @click="handleNewConversation"
      class="new-conv-btn w-full flex items-center justify-center gap-2 px-3 py-2 bg-accent/10 text-accent text-[12.5px] font-medium rounded-lg hover:bg-accent/20 transition-colors cursor-pointer border-0"
    >
      <Plus class="w-4 h-4" />
      新建对话
    </button>

    <div class="workspace flex items-center gap-2 px-2.5 py-2 border border-border rounded-lg cursor-pointer transition-colors duration-200 hover:border-border-2 hover:bg-bg-2">
      <div class="ws-avatar w-[22px] h-[22px] rounded-[5px] bg-[oklch(45%_0.04_35)] text-xs font-semibold grid place-items-center text-text">林</div>
      <div class="ws-name text-[13px] flex-1">个人工作空间</div>
      <ChevronsUpDown class="w-3.5 h-3.5 text-text-3" />
    </div>

    <div class="nav-group flex flex-col gap-px">
      <div class="nav-label text-[10.5px] uppercase tracking-[0.14em] text-text-4 px-2.5 pb-1.5 pt-2 font-medium">创作</div>

      <a
        class="nav-item relative flex items-center gap-2.5 px-2.5 py-[7px] rounded-lg text-text-2 cursor-pointer text-[13.5px] transition-colors duration-150"
        :class="{ 'active': activeNav === '对话' }"
        @click="handleNav('对话')"
      >
        <MessageSquare class="icon w-4 h-4 shrink-0 opacity-85" />
        <span>对话</span>
      </a>

      <div>
        <a
          class="nav-item relative flex items-center gap-2.5 px-2.5 py-[7px] rounded-lg text-text-2 cursor-pointer text-[13.5px] transition-colors duration-150"
          :class="{ 'active': isToolActive || toolsOpen }"
          @click="toggleTools()"
        >
          <Sparkles class="icon w-4 h-4 shrink-0 opacity-85" />
          <span class="flex-1">创作工具</span>
          <ChevronRight v-if="!toolsOpen" class="w-3.5 h-3.5 text-text-4 transition-transform duration-200" />
          <ChevronDown v-else class="w-3.5 h-3.5 text-text-3 transition-transform duration-200" />
        </a>

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

    <!-- Recent Conversations Section -->
    <div class="nav-group flex flex-col gap-px flex-1 min-h-0">
      <div class="nav-label text-[10.5px] uppercase tracking-[0.14em] text-text-4 px-2.5 pb-1.5 pt-2 font-medium">最近对话</div>
      
      <!-- Search Box -->
      <ConversationSearch v-model="searchKeyword" @search="handleSearch" class="mb-3" />

      <!-- Conversation List Container -->
      <div class="conv-list flex-1 min-h-0 overflow-y-auto -mx-2.5 px-2.5">
        <!-- Loading State -->
        <SkeletonLoader v-if="isLoadingList && (!conversationList || conversationList.length === 0)" />

        <!-- Empty State -->
        <EmptyState 
          v-else-if="!isLoadingList && filteredConversations.length === 0" 
          @create="handleNewConversation" 
        />

        <!-- Conversation Cards -->
        <div v-else class="flex flex-col gap-px">
          <ConversationCard
            v-for="conv in filteredConversations"
            :key="conv.memoryId"
            :conversation="conv"
            :is-active="currentSessionId === conv.memoryId"
            @click="handleRestore"
            @contextmenu="handleContextMenu"
          />
        </div>
      </div>
    </div>

    <!-- 右键上下文菜单 -->
    <ContextMenu
      :visible="contextMenu.visible"
      :x="contextMenu.x"
      :y="contextMenu.y"
      @delete="handleContextDelete"
      @close="closeContextMenu"
    />

    <!-- 删除确认对话框 -->
    <DeleteConfirmDialog
      :visible="deleteDialog.visible"
      title="确认删除"
      message="删除后无法恢复，确定要删除这个对话吗？"
      @confirm="confirmDelete"
      @cancel="cancelDelete"
    />

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
.new-conv-btn:hover {
  background: var(--color-accent);
  color: var(--color-bg);
}
.conv-list {
  max-height: calc(100vh - 480px);
}
</style>
