<script setup lang="ts">
import { ref } from 'vue'
import { Sparkles, ImagePlus, Download, RefreshCw, Maximize2, Wand2, Palette, Ratio, SlidersHorizontal, ChevronDown, X, Loader2 } from 'lucide-vue-next'

const prompt = ref('')
const negativePrompt = ref('')
const isGenerating = ref(false)

const aspectRatios = [
  { label: '1 : 1', value: '1:1', icon: '□' },
  { label: '4 : 3', value: '4:3', icon: '▭' },
  { label: '3 : 4', value: '3:4', icon: '▯' },
  { label: '16 : 9', value: '16:9', icon: '▬' },
  { label: '9 : 16', value: '9:16', icon: '▮' },
]

const stylePresets = [
  { name: '摄影写实', desc: '真实照片质感', active: true, samples: [
    { url: 'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=400&h=400&fit=crop&q=80', prompt: '山间晨雾中的湖泊倒影' },
    { url: 'https://images.unsplash.com/photo-1493976040374-85c8e12f0c0e?w=400&h=300&fit=crop&q=80', prompt: '日本京都古寺庭院' },
    { url: 'https://images.unsplash.com/photo-1518837695005-2083093ee35b?w=400&h=500&fit=crop&q=80', prompt: '海洋日落剪影' },
    { url: 'https://images.unsplash.com/photo-1480714378408-67cf0d13bc1b?w=400&h=250&fit=crop&q=80', prompt: '城市天际线航拍' },
  ]},
  { name: '数字插画', desc: '扁平矢量风格', samples: [
    { url: 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=400&h=400&fit=crop&q=80', prompt: '抽象几何色彩构成' },
    { url: 'https://images.unsplash.com/photo-1541701494587-cb58502866ab?w=400&h=300&fit=crop&q=80', prompt: '梦幻森林插画' },
    { url: 'https://images.unsplash.com/photo-1579783902614-a3fb3927b6a5?w=400&h=500&fit=crop&q=80', prompt: '赛博朋克角色设计' },
    { url: 'https://images.unsplash.com/photo-1550859492-d5da9d8e45f3?w=400&h=250&fit=crop&q=80', prompt: '极简线条风景' },
  ]},
  { name: '3D 渲染', desc: 'Blender / C4D 风格', samples: [
    { url: 'https://images.unsplash.com/photo-1633356122544-f134324a6cee?w=400&h=400&fit=crop&q=80', prompt: '3D 抽象几何体' },
    { url: 'https://images.unsplash.com/photo-1614850523060-8da1d56ae167?w=400&h=300&fit=crop&q=80', prompt: 'C4D 柔和材质场景' },
    { url: 'https://images.unsplash.com/photo-1620641788421-7a1c342ea42e?w=400&h=500&fit=crop&q=80', prompt: 'Blender 角色渲染' },
    { url: 'https://images.unsplash.com/photo-1558618666-fcd25c85f82e?w=400&h=250&fit=crop&q=80', prompt: '3D 产品展示' },
  ]},
  { name: '水彩手绘', desc: '艺术绘画质感', samples: [
    { url: 'https://images.unsplash.com/photo-1579783902614-a3fb3927b6a5?w=400&h=400&fit=crop&q=80', prompt: '水彩花卉静物' },
    { url: 'https://images.unsplash.com/photo-1460661419201-fd4cecdf8a8b?w=400&h=300&fit=crop&q=80', prompt: '水彩山水意境' },
    { url: 'https://images.unsplash.com/photo-1536924940846-227afb31e2a5?w=400&h=500&fit=crop&q=80', prompt: '手绘人物肖像' },
    { url: 'https://images.unsplash.com/photo-1580910365203-91ef7c96bdfa?w=400&h=250&fit=crop&q=80', prompt: '水墨画风格' },
  ]},
  { name: '像素艺术', desc: '复古游戏风格', samples: [
    { url: 'https://images.unsplash.com/photo-1550745165-9bc0b252726f?w=400&h=400&fit=crop&q=80', prompt: '像素风游戏场景' },
    { url: 'https://images.unsplash.com/photo-1511512578047-dfb367046420?w=400&h=300&fit=crop&q=80', prompt: '复古像素角色' },
    { url: 'https://images.unsplash.com/photo-1493711662062-fa541adb3fc8?w=400&h=500&fit=crop&q=80', prompt: '8-bit 像素城市' },
    { url: 'https://images.unsplash.com/photo-1606144042614-b2417e99c4e3?w=400&h=250&fit=crop&q=80', prompt: '像素艺术图标集' },
  ]},
  { name: '赛博朋克', desc: '霓虹科幻风格', samples: [
    { url: 'https://images.unsplash.com/photo-1555680202-c86f0e12f086?w=400&h=400&fit=crop&q=80', prompt: '霓虹灯城市夜景' },
    { url: 'https://images.unsplash.com/photo-1563089145-599997674d42?w=400&h=300&fit=crop&q=80', prompt: '赛博朋克街道' },
    { url: 'https://images.unsplash.com/photo-1535295972055-1c762f4483e5?w=400&h=500&fit=crop&q=80', prompt: '未来主义建筑' },
    { url: 'https://images.unsplash.com/photo-1550751827-4bd374c3f58b?w=400&h=250&fit=crop&q=80', prompt: '全息投影效果' },
  ]},
  { name: '极简线条', desc: '线稿草图风格', samples: [
    { url: 'https://images.unsplash.com/photo-1581291518857-4e27b48ff24e?w=400&h=400&fit=crop&q=80', prompt: '极简线条建筑' },
    { url: 'https://images.unsplash.com/photo-1596548438137-d51ea5c83ca5?w=400&h=300&fit=crop&q=80', prompt: '手绘线稿植物' },
    { url: 'https://images.unsplash.com/photo-1605721911519-4d0690cc97c0?w=400&h=500&fit=crop&q=80', prompt: '极简人物轮廓' },
    { url: 'https://images.unsplash.com/photo-1618005198919-d3d4b5a92ead?w=400&h=250&fit=crop&q=80', prompt: '抽象线条构成' },
  ]},
  { name: '油画质感', desc: '古典画布风格', samples: [
    { url: 'https://images.unsplash.com/photo-1578926078-b8fc87b6ec0b?w=400&h=400&fit=crop&q=80', prompt: '印象派风景油画' },
    { url: 'https://images.unsplash.com/photo-1577083552431-6e5fd01988ec?w=400&h=300&fit=crop&q=80', prompt: '古典肖像油画' },
    { url: 'https://images.unsplash.com/photo-1561214115-f2f134cc4912?w=400&h=500&fit=crop&q=80', prompt: '厚涂肌理质感' },
    { url: 'https://images.unsplash.com/photo-1580136608707-63b6f3963e71?w=400&h=250&fit=crop&q=80', prompt: '文艺复兴风格' },
  ]},
]

const selectedRatio = ref('1:1')
const selectedStyle = ref(0)
const showAdvanced = ref(false)
const steps = ref(30)
const cfg = ref(7.5)
const seed = ref('')

const images = ref<Array<{ id: number; url: string; prompt: string; width: number; height: number }>>([])

function handleGenerate() {
  if (!prompt.value.trim() || isGenerating.value) return
  isGenerating.value = true
  setTimeout(() => {
    const [w, h] = selectedRatio.value.split(':').map(Number)
    images.value.unshift({
      id: Date.now(),
      url: `https://images.unsplash.com/photo-${1500000000000 + Math.floor(Math.random() * 100000000)}?w=512&h=${512 * h / w}&fit=crop&q=80`,
      prompt: prompt.value,
      width: w,
      height: h,
    })
    isGenerating.value = false
  }, 2000)
}

function removeImage(id: number) {
  images.value = images.value.filter(img => img.id !== id)
}

const sampleARs = ['1/1', '4/3', '3/4', '16/9']
function getSampleAR(index: number): string {
  return sampleARs[index % sampleARs.length]
}
</script>

<template>
  <div class="image-gen flex flex-col h-full overflow-hidden">
    <!-- Prompt Area -->
    <div class="gen-prompt px-8 pt-6 pb-4 max-w-[720px] mx-auto w-full">
      <div class="prompt-card border border-border/60 rounded-xl bg-bg-2/60 px-4 py-3.5 transition-all duration-200 focus-within:border-accent-line focus-within:bg-bg-2 focus-within:shadow-[0_0_0_3px_var(--color-accent-soft)]">
        <textarea
          v-model="prompt"
          placeholder="描述你想生成的图像… 例如：一只戴着眼镜的猫，坐在咖啡馆窗边，暖色调午后光线"
          rows="2"
          class="w-full bg-transparent border-0 outline-none resize-none text-text font-sans text-[14px] leading-[1.6] min-h-[22px] max-h-[120px] placeholder:text-text-4"
        ></textarea>
        <div class="flex items-center gap-2 mt-2 pt-2 border-t border-border/50">
          <button class="inline-flex items-center gap-1.5 px-2 py-1 rounded-md text-text-3 text-[11.5px] cursor-pointer hover:text-text hover:bg-bg-hover transition-colors bg-transparent border-0" @click="showAdvanced = !showAdvanced">
            <SlidersHorizontal class="w-3.5 h-3.5" />
            高级设置
            <ChevronDown class="w-3 h-3 transition-transform" :class="{ 'rotate-180': showAdvanced }" />
          </button>
          <button class="inline-flex items-center gap-1.5 px-2 py-1 rounded-md text-text-3 text-[11.5px] cursor-pointer hover:text-text hover:bg-bg-hover transition-colors bg-transparent border-0" title="随机提示词">
            <Wand2 class="w-3.5 h-3.5" />
            随机灵感
          </button>
          <div class="flex-1"></div>
          <span class="text-[10.5px] text-text-4 font-mono">{{ prompt.length }} / 2000</span>
          <button
            class="inline-flex items-center justify-center gap-1.5 px-4 py-1.5 rounded-lg bg-accent text-bg text-[12.5px] font-medium cursor-pointer border-0 transition-all duration-150 hover:bg-accent-2 active:scale-[0.97] disabled:opacity-40"
            :disabled="!prompt.trim() || isGenerating"
            @click="handleGenerate"
          >
            <Loader2 v-if="isGenerating" class="w-3.5 h-3.5 animate-spin" />
            <Sparkles v-else class="w-3.5 h-3.5" />
            {{ isGenerating ? '生成中…' : '生成图像' }}
          </button>
        </div>

        <!-- Advanced Settings -->
        <Transition enter-active-class="transition-all duration-200 ease-out" enter-from-class="max-h-0 opacity-0" enter-to-class="max-h-[200px] opacity-100" leave-active-class="transition-all duration-150 ease-in" leave-from-class="max-h-[200px] opacity-100" leave-to-class="max-h-0 opacity-0">
          <div v-show="showAdvanced" class="overflow-hidden mt-3 pt-3 border-t border-border/50 space-y-3">
            <div>
              <label class="text-[11px] text-text-4 mb-1 block">负面提示词</label>
              <input v-model="negativePrompt" type="text" placeholder="排除的内容，如：模糊、低质量、变形…" class="w-full bg-bg-input border border-border rounded-lg px-3 py-1.5 text-[13px] text-text outline-none focus:border-accent-line transition-colors placeholder:text-text-4" />
            </div>
            <div class="grid grid-cols-3 gap-3">
              <div>
                <label class="text-[11px] text-text-4 mb-1 block">采样步数</label>
                <input type="number" v-model.number="steps" min="1" max="100" class="w-full bg-bg-input border border-border rounded-lg px-3 py-1.5 text-[13px] text-text outline-none focus:border-accent-line transition-colors" />
              </div>
              <div>
                <label class="text-[11px] text-text-4 mb-1 block">CFG 强度</label>
                <input type="number" v-model.number="cfg" step="0.5" min="1" max="20" class="w-full bg-bg-input border border-border rounded-lg px-3 py-1.5 text-[13px] text-text outline-none focus:border-accent-line transition-colors" />
              </div>
              <div>
                <label class="text-[11px] text-text-4 mb-1 block">种子值</label>
                <input v-model="seed" type="text" placeholder="留空随机" class="w-full bg-bg-input border border-border rounded-lg px-3 py-1.5 text-[13px] text-text outline-none focus:border-accent-line transition-colors placeholder:text-text-4" />
              </div>
            </div>
          </div>
        </Transition>
      </div>
    </div>

    <!-- Style & Ratio Bar -->
    <div class="style-bar px-8 pb-4 max-w-[720px] mx-auto w-full">
      <div class="flex items-center gap-3">
        <div class="ratio-group flex items-center gap-1">
          <button
            v-for="r in aspectRatios"
            :key="r.value"
            class="ratio-btn w-9 h-9 grid place-items-center rounded-lg text-[11px] font-mono cursor-pointer transition-all duration-150 border-0"
            :class="selectedRatio === r.value ? 'bg-accent-soft text-accent font-medium' : 'text-text-3 hover:bg-bg-hover hover:text-text bg-transparent'"
            @click="selectedRatio = r.value"
            :title="r.label"
          >{{ r.icon }}</button>
        </div>
        <div class="w-px h-5 bg-border"></div>
        <span class="text-[10.5px] text-text-4">风格</span>
        <div class="style-chips flex items-center gap-1 flex-1 overflow-x-auto no-scrollbar">
          <button
            v-for="(style, i) in stylePresets"
            :key="style.name"
            class="shrink-0 px-2.5 py-1 rounded-md text-[11.5px] cursor-pointer transition-all duration-150 border-0 whitespace-nowrap"
            :class="selectedStyle === i ? 'bg-accent text-bg font-medium' : 'text-text-3 hover:text-text hover:bg-bg-hover bg-transparent'"
            @click="selectedStyle = i"
          >{{ style.name }}</button>
        </div>
      </div>
    </div>

    <!-- Gallery -->
    <div class="gallery flex-1 min-h-0 overflow-y-auto px-8 pb-6">
      <!-- 示例图片区（无生成内容时显示） -->
      <div v-if="images.length === 0 && !isGenerating" class="samples-area h-full flex flex-col py-2">
        <div class="samples-header flex items-center gap-2 mb-4 max-w-[720px] mx-auto w-full px-1">
          <Palette class="w-3.5 h-3.5 text-accent" />
          <span class="text-[12px] font-medium text-text">「{{ stylePresets[selectedStyle].name }}」风格示例</span>
          <span class="text-[11px] text-text-4">{{ stylePresets[selectedStyle].desc }}</span>
        </div>

        <TransitionGroup
          name="sample-fade"
          enter-active-class="transition-all duration-300 ease-out"
          enter-from-class="opacity-0 translate-y-2"
          enter-to-class="opacity-100 translate-y-0"
          leave-active-class="transition-all duration-200 ease-in absolute"
          leave-from-class="opacity-100 scale-100"
          leave-to-class="opacity-0 scale-95"
          tag="div"
          class="gallery-grid grid grid-cols-4 gap-3.5 max-w-[720px] mx-auto w-full relative"
        >
          <div
            v-for="(sample, si) in stylePresets[selectedStyle].samples"
            :key="`${selectedStyle}-${si}`"
            class="sample-card group relative rounded-xl overflow-hidden border border-border/60 bg-bg-2 cursor-pointer transition-all duration-200 hover:border-accent-line hover:shadow-lg hover:shadow-accent/5 hover:-translate-y-0.5"
          >
            <div class="aspect-[var(--ar)]" :style="{ '--ar': getSampleAR(si) }">
              <img :src="sample.url" alt="" class="w-full h-full object-cover transition-transform duration-500 group-hover:scale-[1.05]" loading="lazy" />
            </div>
            <div class="sample-overlay absolute inset-0 bg-gradient-to-t from-black/55 via-black/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-250">
              <div class="absolute bottom-0 left-0 right-0 p-2.5">
                <span class="text-[10.5px] text-white/90 line-clamp-1 leading-snug block">{{ sample.prompt }}</span>
                <span class="text-[9.5px] text-white/50 mt-0.5 block">点击使用此提示词</span>
              </div>
            </div>
            <div class="use-btn absolute top-2 right-2 w-6 h-6 rounded-md bg-accent/90 backdrop-blur-sm grid place-items-center text-bg opacity-0 group-hover:opacity-100 transition-all duration-200 cursor-pointer hover:bg-accent hover:scale-110 shadow-sm" title="使用此提示词" @click="prompt = sample.prompt">
              <RefreshCw class="w-3 h-3" />
            </div>
          </div>
        </TransitionGroup>

        <div class="samples-tip mt-6 text-center max-w-[720px] mx-auto w-full">
          <p class="text-[11.5px] text-text-4">选择一个风格，输入描述即可开始创作 · 点击示例图可快速填充提示词</p>
        </div>
      </div>

      <div v-else class="gallery-grid grid grid-cols-2 gap-4 max-w-[720px] mx-auto">
        <TransitionGroup enter-active-class="transition duration-300 ease-out" enter-from-class="opacity-0 scale-95" enter-to-class="opacity-100 scale-100">
          <div
            v-for="img in images"
            :key="img.id"
            class="img-card group relative rounded-xl overflow-hidden border border-border bg-bg-2"
            :style="{ aspectRatio: `${img.width} / ${img.height}` }"
          >
            <img :src="img.url" alt="" class="w-full h-full object-cover" loading="lazy" />
            <div class="img-overlay absolute inset-0 bg-gradient-to-t from-black/50 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-200">
              <div class="absolute bottom-0 left-0 right-0 p-3 flex items-end justify-between">
                <span class="text-[11px] text-white/80 line-clamp-1">{{ img.prompt }}</span>
                <div class="flex items-center gap-1">
                  <button class="w-7 h-7 rounded-lg bg-white/15 backdrop-blur-sm grid place-items-center text-white hover:bg-white/25 transition-colors cursor-pointer border-0" title="下载">
                    <Download class="w-3.5 h-3.5" />
                  </button>
                  <button class="w-7 h-7 rounded-lg bg-white/15 backdrop-blur-sm grid place-items-center text-white hover:bg-white/25 transition-colors cursor-pointer border-0" title="放大">
                    <Maximize2 class="w-3.5 h-3.5" />
                  </button>
                  <button class="w-7 h-7 rounded-lg bg-white/15 backdrop-blur-sm grid place-items-center text-white hover:bg-danger/70 transition-colors cursor-pointer border-0" title="删除" @click="removeImage(img.id)">
                    <X class="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            </div>
            <div class="img-badge absolute top-2 right-2 px-1.5 py-[2px] rounded-[4px] bg-black/40 backdrop-blur-sm text-[9.5px] text-white/80 font-mono tabular-nums">
              {{ img.width }}×{{ img.height }}
            </div>
          </div>
        </TransitionGroup>

        <!-- Generating Placeholder -->
        <div v-if="isGenerating" class="img-card relative rounded-xl overflow-hidden border border-border border-dashed bg-bg-2 flex flex-col items-center justify-center gap-3" style="aspect-ratio: 1/1;">
          <Loader2 class="w-8 h-8 text-accent animate-spin" />
          <div class="text-center">
            <p class="text-[13px] text-text font-medium">正在生成中</p>
            <p class="text-[11px] text-text-4 mt-1">Astra Vision 正在绘制你的创意…</p>
          </div>
        </div>
      </div>
    </div>
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
.line-clamp-1 {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
