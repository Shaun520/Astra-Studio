<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { X, ZoomIn, ZoomOut, RotateCw, ChevronLeft, ChevronRight } from 'lucide-vue-next'

interface ImageItem {
  src: string
  alt?: string
}

const props = withDefaults(defineProps<{
  visible: boolean
  images: ImageItem[]
  initialIndex?: number
}>(), {
  initialIndex: 0,
})

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
}>()

const currentIndex = ref(props.initialIndex)
const scale = ref(1)
const translateX = ref(0)
const translateY = ref(0)
const isDragging = ref(false)
const dragStart = ref({ x: 0, y: 0 })
const dragOffset = ref({ x: 0, y: 0 })
const imgRef = ref<HTMLImageElement | null>(null)
const containerRef = ref<HTMLElement | null>(null)

const currentImage = computed(() => props.images[currentIndex.value] || props.images[0])
const canPrev = computed(() => currentIndex.value > 0)
const canNext = computed(() => currentIndex.value < props.images.length - 1)
const isMulti = computed(() => props.images.length > 1)

function resetTransform() {
  scale.value = 1
  translateX.value = 0
  translateY.value = 0
}

watch(() => props.visible, (val) => {
  if (val) {
    currentIndex.value = props.initialIndex
    resetTransform()
    document.body.style.overflow = 'hidden'
  } else {
    document.body.style.overflow = ''
  }
})

watch(() => props.initialIndex, (idx) => {
  if (props.visible) {
    currentIndex.value = idx
    resetTransform()
  }
})

function close() {
  emit('update:visible', false)
}

function prev() {
  if (!canPrev.value) return
  currentIndex.value--
  resetTransform()
}

function next() {
  if (!canNext.value) return
  currentIndex.value++
  resetTransform()
}

function zoom(delta: number) {
  const newScale = Math.max(0.5, Math.min(5, scale.value + delta))
  if (newScale <= 1) {
    resetTransform()
    return
  }
  const ratio = newScale / scale.value
  scale.value = newScale
  translateX.value *= ratio
  translateY.value *= ratio
}

function rotate() {}

function handleWheel(e: WheelEvent) {
  e.preventDefault()
  const delta = e.deltaY > 0 ? -0.15 : 0.15
  zoom(delta)
}

function handlePointerDown(e: PointerEvent) {
  if (scale.value <= 1) return
  isDragging.value = true
  dragStart.value = { x: e.clientX, y: e.clientY }
  dragOffset.value = { x: translateX.value, y: translateY.value }
  ;(e.target as HTMLElement).setPointerCapture(e.pointerId)
}

function handlePointerMove(e: PointerEvent) {
  if (!isDragging.value) return
  const dx = e.clientX - dragStart.value.x
  const dy = e.clientY - dragStart.value.y
  translateX.value = dragOffset.value.x + dx
  translateY.value = dragOffset.value.y + dy
}

function handlePointerUp() {
  isDragging.value = false
}

function handleKeydown(e: KeyboardEvent) {
  if (!props.visible) return
  if (e.key === 'Escape') close()
  else if (e.key === 'ArrowLeft') prev()
  else if (e.key === 'ArrowRight') next()
}

onMounted(() => window.addEventListener('keydown', handleKeydown))
onUnmounted(() => window.removeEventListener('keydown', handleKeydown))

const imgStyle = computed(() => ({
  transform: `translate(${translateX.value}px, ${translateY.value}px) scale(${scale.value})`,
  transition: isDragging.value ? 'none' : 'transform 0.2s ease-out',
}))
</script>

<template>
  <Teleport to="body">
    <Transition enter-active-class="transition-all duration-200 ease-out" enter-from-class="opacity-0" enter-to-class="opacity-100"
      leave-active-class="transition-all duration-150 ease-in" leave-from-class="opacity-100" leave-to-class="opacity-0">
      <div v-if="visible" class="image-preview-overlay fixed inset-0 z-[9999] bg-black/85 backdrop-blur-md flex items-center justify-center" @click.self="close" @wheel.prevent="handleWheel">
        <!-- 关闭按钮 -->
        <button class="absolute top-4 right-4 w-10 h-10 rounded-full bg-white/10 hover:bg-white/20 grid place-items-center text-white cursor-pointer border-0 transition-colors z-10"
          @click="close"
        >
          <X class="w-5 h-5" />
        </button>

        <!-- 图片计数 -->
        <div v-if="isMulti" class="absolute top-4 left-1/2 -translate-x-1/2 px-3 py-1 rounded-full bg-white/10 text-white/80 text-[12px] font-mono z-10">
          {{ currentIndex + 1 }} / {{ images.length }}
        </div>

        <!-- 左箭头 -->
        <button v-if="isMulti && canPrev" class="nav-btn absolute left-4 top-1/2 -translate-y-1/2 w-11 h-11 rounded-full bg-white/10 hover:bg-white/20 grid place-items-center text-white cursor-pointer border-0 transition-colors z-10"
          @click.stop="prev"
        >
          <ChevronLeft class="w-6 h-6" />
        </button>

        <!-- 右箭头 -->
        <button v-if="isMulti && canNext" class="nav-btn absolute right-4 top-1/2 -translate-y-1/2 w-11 h-11 rounded-full bg-white/10 hover:bg-white/20 grid place-items-center text-white cursor-pointer border-0 transition-colors z-10"
          @click.stop="next"
        >
          <ChevronRight class="w-6 h-6" />
        </button>

        <!-- 工具栏 -->
        <div class="toolbar absolute bottom-5 left-1/2 -translate-x-1/2 flex items-center gap-1 px-2 py-1.5 rounded-xl bg-white/10 backdrop-blur-sm z-10">
          <button class="tool-btn w-9 h-9 rounded-lg grid place-items-center text-white/70 hover:text-white hover:bg-white/10 cursor-pointer border-0 transition-colors"
            title="缩小 (滚轮)" @click.stop="zoom(-0.3)"
          >
            <ZoomOut class="w-[18px] h-[18px]" />
          </button>
          <span class="text-white/60 text-[11px] font-mono min-w-[42px] text-center">{{ Math.round(scale * 100) }}%</span>
          <button class="tool-btn w-9 h-9 rounded-lg grid place-items-center text-white/70 hover:text-white hover:bg-white/10 cursor-pointer border-0 transition-colors"
            title="放大 (滚轮)" @click.stop="zoom(0.3)"
          >
            <ZoomIn class="w-[18px] h-[18px]" />
          </button>
          <div class="w-px h-5 bg-white/15 mx-1"></div>
          <button class="tool-btn w-9 h-9 rounded-lg grid place-items-center text-white/70 hover:text-white hover:bg-white/10 cursor-pointer border-0 transition-colors"
            title="重置" @click.stop="resetTransform()"
          >
            <RotateCw class="w-[18px] h-[18px]" />
          </button>
        </div>

        <!-- 图片容器 -->
        <div ref="containerRef" class="img-container relative max-w-[90vw] max-h-[85vh] overflow-hidden select-none cursor-grab active:cursor-grabbing">
          <img
            ref="imgRef"
            :src="currentImage.src"
            :alt="currentImage.alt || ''"
            :style="imgStyle"
            class="max-w-[90vw] max-h-[85vw] object-contain will-change-transform"
            draggable="false"
            @pointerdown="handlePointerDown"
            @pointermove="handlePointerMove"
            @pointerup="handlePointerUp"
            @pointercancel="handlePointerUp"
            @dblclick="zoom(scale >= 2 ? -(scale - 1) : 1)"
          />
        </div>

        <!-- 底部文件名提示 -->
        <div v-if="currentImage.alt" class="absolute bottom-16 left-1/2 -translate-x-1/2 px-3 py-1.5 rounded-lg bg-black/50 text-white/70 text-[12px] truncate max-w-[400px] z-10">
          {{ currentImage.alt }}
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
