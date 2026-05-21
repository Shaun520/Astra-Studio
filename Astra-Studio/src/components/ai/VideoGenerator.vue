<script setup lang="ts">
import { ref, computed } from 'vue'
import { Play, Pause, Square, SkipForward, Volume2, VolumeX, Film, Clock, Layers, Wand2, SlidersHorizontal, ChevronDown, Plus, Trash2, GripVertical, Loader2, Download, Maximize2, Sparkles, MonitorPlay } from 'lucide-vue-next'

const scenes = ref([
  { id: 1, prompt: '镜头从太空俯瞰地球，云层缓慢流动', duration: 4, style: 'cinematic' },
  { id: 2, prompt: '切入城市街道，行人匆匆走过霓虹招牌', duration: 3, style: 'cinematic' },
  { id: 3, prompt: '特写一只手推开一扇老旧木门，光线洒入', duration: 3, style: 'cinematic' },
])

const isPlaying = ref(false)
const currentTime = ref(0)
const totalDuration = computed(() => scenes.value.reduce((s, c) => s + c.duration, 0))
const muted = ref(false)
const showSettings = ref(false)

const durationOptions = [2, 3, 4, 5, 8, 10]
const fpsOptions = [24, 30, 60]

const selectedDuration = ref(4)
const selectedFps = ref(24)
const aspectValue = ref('16:9')

const motionStyles = [
  { key: 'cinematic', name: '电影质感', desc: '慢推摇移，胶片颗粒', icon: '🎬' },
  { key: 'dynamic', name: '动感快切', desc: '快速剪辑，节奏紧凑', icon: '⚡' },
  { key: 'smooth', name: '丝滑过渡', desc: '无缝衔接，流体动画', icon: '🌊' },
  { key: 'documentary', name: '纪录片风', desc: '手持跟拍，自然叙事', icon: '📹' },
  { key: 'anime', name: '动漫风格', desc: '日系分镜，夸张表现', icon: '🎨' },
]

const selectedMotion = ref('cinematic')
const activeSceneId = ref(1)
const isGenerating = ref(false)
const generatedVideos = ref<Array<{ id: number; thumb: string; duration: number; prompt: string }>>([])

function addScene() {
  const newId = Math.max(...scenes.value.map(s => s.id), 0) + 1
  scenes.value.push({
    id: newId,
    prompt: '',
    duration: selectedDuration.value,
    style: selectedMotion.value,
  })
  activeSceneId.value = newId
}

function removeScene(id: number) {
  if (scenes.value.length <= 1) return
  scenes.value = scenes.value.filter(s => s.id !== id)
  if (activeSceneId.value === id) {
    activeSceneId.value = scenes.value[0]?.id || 0
  }
}

function selectScene(id: number) {
  activeSceneId.value = id
}

function updateScenePrompt(id: number, val: string) {
  const scene = scenes.value.find(s => s.id === id)
  if (scene) scene.prompt = val
}

function generateAll() {
  if (!canGenerate.value) return
  isGenerating.value = true
  setTimeout(() => {
    generatedVideos.value.unshift({
      id: Date.now(),
      thumb: `https://images.unsplash.com/photo-1536240478700-b869070f9279?w=480&h=270&fit=crop&q=80`,
      duration: totalDuration.value,
      prompt: scenes.value.map(s => s.prompt).join(' → '),
    })
    isGenerating.value = false
  }, 4000)
}

const activeScene = computed(() => scenes.value.find(s => s.id === activeSceneId.value))
const canGenerate = computed(() => scenes.value?.every(s => s.prompt.trim()))

function formatTime(sec: number): string {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

function seekTo(e: MouseEvent) {
  const bar = (e.currentTarget as HTMLElement)
  const rect = bar.getBoundingClientRect()
  const pct = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
  currentTime.value = Math.round(pct * totalDuration.value)
}

function togglePlay() {
  isPlaying.value = !isPlaying.value
}
</script>

<template>
  <div class="video-gen flex flex-1 min-h-0 overflow-hidden bg-bg">
    <!-- 左侧：场景时间线 -->
    <aside class="timeline-panel w-[300px] shrink-0 border-r border-border flex flex-col bg-bg/80">
      <div class="panel-header px-5 py-4 border-b border-border flex items-center justify-between">
        <div class="flex items-center gap-2">
          <Film class="w-4 h-4 text-accent" />
          <span class="text-[13px] font-medium text-text">场景时间线</span>
        </div>
        <button class="w-7 h-7 rounded-lg grid place-items-center text-text-3 cursor-pointer hover:text-accent hover:bg-accent-soft transition-colors border-0 bg-transparent" @click="addScene" title="添加场景">
          <Plus class="w-3.5 h-3.5" />
        </button>
      </div>

      <div class="scenes-list flex-1 min-h-0 overflow-y-auto p-3 space-y-2">
        <TransitionGroup enter-active-class="transition-all duration-200 ease-out" enter-from-class="opacity-0 -translate-x-2" enter-to-class="opacity-100 translate-x-0" leave-active-class="transition-all duration-150 ease-in" leave-from-class="opacity-100 scale-100" leave-to-class="opacity-0 scale-95">
          <div
            v-for="(scene, i) in scenes"
            :key="scene.id"
            class="scene-card group relative rounded-xl border p-3 cursor-pointer transition-all duration-150"
            :class="activeSceneId === scene.id ? 'border-accent-line bg-accent-soft shadow-sm' : 'border-border hover:border-border-2 bg-bg-2'"
            @click="selectScene(scene.id)"
          >
            <div class="flex items-center gap-2 mb-2">
              <GripVertical class="w-3.5 h-3.5 text-text-4 shrink-0 cursor-grab" />
              <span class="scene-num w-5 h-5 rounded-md grid place-items-center text-[10px] font-mono font-medium shrink-0"
                :class="activeSceneId === scene.id ? 'bg-accent text-bg' : 'bg-bg-hover text-text-3'"
              >{{ i + 1 }}</span>
              <span class="text-[11px] font-medium text-text flex-1 truncate">场景 {{ i + 1 }}</span>
              <span class="text-[10px] font-mono text-text-4 tabular-nums">{{ scene.duration }}s</span>
              <button class="w-5 h-5 rounded grid place-items-center text-text-4 opacity-0 group-hover:opacity-100 hover:text-danger transition-all cursor-pointer border-0 bg-transparent" @click.stop="removeScene(scene.id)" title="删除场景">
                <Trash2 class="w-3 h-3" />
              </button>
            </div>

            <textarea
              v-if="activeSceneId === scene.id"
              v-model="scene.prompt"
              rows="2"
              placeholder="描述这个场景的画面内容…"
              class="w-full bg-transparent border-0 outline-none resize-none text-[12.5px] text-text leading-[1.6] placeholder:text-text-4"
              @input="updateScenePrompt(scene.id, scene.prompt)"
              @click.stop
            ></textarea>
            <p v-else class="text-[11.5px] text-text-3 line-clamp-2 leading-snug">{{ scene.prompt || '点击编辑场景描述…' }}</p>

            <div class="scene-bar mt-2.5 h-1 rounded-full bg-bg-hover overflow-hidden">
              <div class="h-full rounded-full transition-all duration-300"
                :class="activeSceneId === scene.id ? 'bg-accent' : 'bg-text-4/30'"
                :style="{ width: `${(scene.duration / 10) * 100}%` }"
              ></div>
            </div>
          </div>
        </TransitionGroup>
      </div>

      <div class="panel-footer px-4 py-3 border-t border-border">
        <div class="flex items-center justify-between mb-2.5">
          <span class="text-[11px] text-text-4">总时长</span>
          <span class="text-[13px] font-mono font-medium text-text tabular-nums">{{ formatTime(totalDuration) }}</span>
        </div>
        <button
          class="generate-btn w-full inline-flex items-center justify-center gap-2 py-2.5 rounded-xl text-bg text-[13px] font-medium cursor-pointer border-0 transition-all duration-150 hover:brightness-110 active:scale-[0.98] disabled:opacity-40"
          :class="isGenerating ? 'bg-text-3' : 'bg-accent'"
          :disabled="isGenerating || !canGenerate"
          @click="generateAll"
        >
          <Loader2 v-if="isGenerating" class="w-4 h-4 animate-spin" />
          <Sparkles v-else class="w-4 h-4" />
          {{ isGenerating ? `生成中 ${Math.round((totalDuration * 0.8))}s…` : '生成全部视频' }}
        </button>
      </div>
    </aside>

    <!-- 右侧主区域 -->
    <main class="flex-1 min-w-0 flex flex-col overflow-hidden">
      <!-- 预览区 -->
      <div class="preview-area flex-1 min-h-0 relative">
        <div class="preview-player absolute inset-0 flex items-center justify-center bg-bg-2/40">
          <div class="preview-frame relative max-w-[720px] w-full mx-auto aspect-video rounded-2xl overflow-hidden border border-border bg-black/90 shadow-2xl shadow-black/20">
            <img src="https://images.unsplash.com/photo-1536240478700-b869070f9279?w=1280&h=720&fit=crop&q=80" alt="" class="w-full h-full object-cover opacity-90" />

            <div class="preview-overlay absolute inset-0 flex items-center justify-center">
              <button
                class="play-btn w-16 h-16 rounded-full bg-white/15 backdrop-blur-md grid place-items-center text-white cursor-pointer transition-all duration-200 hover:bg-white/25 hover:scale-105 border-0 shadow-lg"
                @click="togglePlay"
              >
                <Pause v-if="isPlaying" class="w-7 h-7 ml-0.5" />
                <Play v-else class="w-7 h-7 ml-1" />
              </button>
            </div>

            <div class="preview-info absolute bottom-0 left-0 right-0 p-4 bg-gradient-to-t from-black/70 via-black/20 to-transparent">
              <div class="flex items-end justify-between">
                <div>
                  <p class="text-white/90 text-[13px] font-medium line-clamp-1">{{ activeScene?.prompt || '选择一个场景开始预览' }}</p>
                  <p class="text-white/50 text-[10.5px] mt-0.5 font-mono">{{ formatTime(currentTime) }} / {{ formatTime(totalDuration) }}</p>
                </div>
                <div class="flex items-center gap-2">
                  <button class="w-8 h-8 rounded-lg bg-white/10 backdrop-blur-sm grid place-items-center text-white/70 hover:text-white hover:bg-white/20 transition-colors cursor-pointer border-0" @click="muted = !muted">
                    <VolumeX v-if="muted" class="w-3.5 h-3.5" />
                    <Volume2 v-else class="w-3.5 h-3.5" />
                  </button>
                  <button class="w-8 h-8 rounded-lg bg-white/10 backdrop-blur-sm grid place-items-center text-white/70 hover:text-white hover:bg-white/20 transition-colors cursor-pointer border-0">
                    <Maximize2 class="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>

              <div class="progress-bar mt-3 h-1 rounded-full bg-white/15 cursor-pointer" @click="seekTo($event)">
                <div class="h-full rounded-full bg-accent transition-all duration-100" :style="{ width: `${(currentTime / totalDuration) * 100}%` }"></div>
              </div>
            </div>

            <div class="preview-badge absolute top-3 left-3 px-2 py-[3px] rounded-md bg-black/40 backdrop-blur-sm flex items-center gap-1.5">
              <MonitorPlay class="w-3 h-3 text-accent" />
              <span class="text-[10px] text-white/70 font-mono">{{ aspectValue }} · {{ selectedFps }}fps</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部控制栏 -->
      <div class="controls-bar border-t border-border bg-bg/90 backdrop-blur-sm px-6 py-3.5">
        <div class="max-w-[900px] mx-auto flex items-center gap-6">
          <!-- 时长 -->
          <div class="control-group flex items-center gap-2">
            <Clock class="w-3.5 h-3.5 text-text-4" />
            <span class="text-[10.5px] text-text-4">单场景</span>
            <div class="duration-chips flex items-center gap-1">
              <button
                v-for="d in durationOptions"
                :key="d"
                class="px-2 py-[3px] rounded-md text-[11px] font-mono cursor-pointer transition-all duration-100 border-0"
                :class="selectedDuration === d ? 'bg-accent text-bg font-medium' : 'text-text-3 hover:text-text hover:bg-bg-hover bg-transparent'"
                @click="selectedDuration = d"
              >{{ d }}s</button>
            </div>
          </div>

          <div class="w-px h-5 bg-border"></div>

          <!-- 帧率 -->
          <div class="control-group flex items-center gap-2">
            <Layers class="w-3.5 h-3.5 text-text-4" />
            <span class="text-[10.5px] text-text-4">帧率</span>
            <div class="fps-chips flex items-center gap-1">
              <button
                v-for="f in fpsOptions"
                :key="f"
                class="px-2 py-[3px] rounded-md text-[11px] font-mono cursor-pointer transition-all duration-100 border-0"
                :class="selectedFps === f ? 'bg-accent text-bg font-medium' : 'text-text-3 hover:text-text hover:bg-bg-hover bg-transparent'"
                @click="selectedFps = f"
              >{{ f }}</button>
            </div>
          </div>

          <div class="w-px h-5 bg-border"></div>

          <!-- 运动风格 -->
          <div class="control-group flex items-center gap-2 flex-1 min-w-0">
            <Wand2 class="w-3.5 h-3.5 text-text-4 shrink-0" />
            <span class="text-[10.5px] text-text-4 shrink-0">运动风格</span>
            <div class="motion-chips flex items-center gap-1 overflow-x-auto no-scrollbar flex-1">
              <button
                v-for="ms in motionStyles"
                :key="ms.key"
                class="shrink-0 inline-flex items-center gap-1 px-2 py-[3px] rounded-md text-[11px] cursor-pointer transition-all duration-100 border-0 whitespace-nowrap"
                :class="selectedMotion === ms.key ? 'bg-accent text-bg font-medium' : 'text-text-3 hover:text-text hover:bg-bg-hover bg-transparent'"
                @click="selectedMotion = ms.key"
              >
                <span>{{ ms.icon }}</span>
                <span>{{ ms.name }}</span>
              </button>
            </div>
          </div>

          <div class="w-px h-5 bg-border"></div>

          <!-- 高级设置 -->
          <button class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-text-3 text-[11.5px] cursor-pointer hover:text-text hover:bg-bg-hover transition-colors border-0 bg-transparent" @click="showSettings = !showSettings">
            <SlidersHorizontal class="w-3.5 h-3.5" />
            设置
          </button>
        </div>

        <Transition enter-active-class="transition-all duration-200 ease-out" enter-from-class="max-h-0 opacity-0" enter-to-class="max-h-[120px] opacity-100" leave-active-class="transition-all duration-150 ease-in" leave-from-class="max-h-[120px] opacity-100" leave-to-class="max-h-0 opacity-0">
          <div v-show="showSettings" class="overflow-hidden mt-3 pt-3 border-t border-border/50">
            <div class="max-w-[900px] mx-auto grid grid-cols-4 gap-4">
              <div>
                <label class="text-[10.5px] text-text-4 block mb-1">运动强度</label>
                <select class="w-full bg-bg-input border border-border rounded-lg px-2.5 py-1.5 text-[12px] text-text outline-none focus:border-accent-line"><option>标准</option><option>强烈</option><option>柔和</option></select>
              </div>
              <div>
                <label class="text-[10.5px] text-text-4 block mb-1">镜头语言</label>
                <select class="w-full bg-bg-input border border-border rounded-lg px-2.5 py-1.5 text-[12px] text-text outline-none focus:border-accent-line"><option>自动</option><option>固定机位</option><option>手持</option><option>轨道</option></select>
              </div>
              <div>
                <label class="text-[10.5px] text-text-4 block mb-1">转场效果</label>
                <select class="w-full bg-bg-input border border-border rounded-lg px-2.5 py-1.5 text-[12px] text-text outline-none focus:border-accent-line"><option>智能渐变</option><option>硬切</option><option>溶解</option><option>滑动</option></select>
              </div>
              <div>
                <label class="text-[10.5px] text-text-4 block mb-1">画质预设</label>
                <select class="w-full bg-bg-input border border-border rounded-lg px-2.5 py-1.5 text-[12px] text-text outline-none focus:border-accent-line"><option>高清 1080p</option><option>超清 4K</option><option>标清 720p</option></select>
              </div>
            </div>
          </div>
        </Transition>
      </div>
    </main>
  </div>
</template>

<style scoped>
.no-scrollbar::-webkit-scrollbar {
  display: none;
}
.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
.line-clamp-1, .line-clamp-2 {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

</style>
