<script setup lang="ts">
/* 工作空间面板组件 */
import { ref, inject } from 'vue'
import { SlidersHorizontal, Sparkles, Layers, BookOpen } from 'lucide-vue-next'
import KnowledgeBasePanel from './KnowledgeBasePanel.vue'

const activeTab = ref<'params' | 'knowledge'>('params')
const chatParams = inject<{ temperature: number; maxOutput: number; topP: number; systemPrompt: string }>('chatParams')!

const recentAssets = [
  { type: 'image' as const, label: '图像', meta1: '1024²', meta2: '2s' },
  { type: 'video' as const, label: '视频', meta1: '5s · 720p', meta2: '14s' },
  { type: 'audio' as const, label: '语音', meta1: '00:42', meta2: '转写' },
  { type: 'code' as const, label: '代码', meta1: '', meta2: '' },
]
</script>

<template>
  <aside class="studio bg-bg flex flex-col overflow-y-auto">
    <!-- Tabs Header -->
    <div class="tabs-header flex border-b border-border">
      <button 
        @click="activeTab = 'params'"
        class="flex-1 py-3 text-[11px] font-medium uppercase tracking-wider flex items-center justify-center gap-1.5 transition-colors"
        :class="activeTab === 'params' ? 'text-accent border-b-2 border-accent bg-accent/5' : 'text-text-3 hover:text-text-2'"
      >
        <SlidersHorizontal class="w-3.5 h-3.5" /> 参数配置
      </button>
      <button 
        @click="activeTab = 'knowledge'"
        class="flex-1 py-3 text-[11px] font-medium uppercase tracking-wider flex items-center justify-center gap-1.5 transition-colors"
        :class="activeTab === 'knowledge' ? 'text-accent border-b-2 border-accent bg-accent/5' : 'text-text-3 hover:text-text-2'"
      >
        <BookOpen class="w-3.5 h-3.5" /> 知识库管理
      </button>
    </div>

    <div v-if="activeTab === 'params'" class="params-content">
      <div class="studio-section py-[18px] px-5 border-b border-border last:border-b-0">
      <div class="studio-h text-[11px] text-text-3 uppercase tracking-[0.14em] mb-3 flex items-center gap-1.5 font-medium">
        <SlidersHorizontal class="w-3 h-3" />参数
      </div>
      <div class="param mb-3.5 last:mb-0">
        <div class="param-head flex justify-between items-baseline mb-2">
          <span class="param-name text-[12.5px] text-text-2">温度</span>
          <span class="param-val font-mono text-[11.5px] text-text tabular-nums">{{ (chatParams?.temperature ?? 0.72).toFixed(2) }}</span>
        </div>
        <input type="range" v-model.number="chatParams.temperature" min="0" max="2" step="0.01"
          class="slider w-full h-1 bg-bg-2 rounded-full appearance-none cursor-pointer [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:w-3 [&::-webkit-slider-thumb]:h-3 [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-text [&::-webkit-slider-thumb]:border-2 [&::-webkit-slider-thumb]:border-bg [&::-webkit-slider-thumb]:shadow-[0_0_0_1px_var(--color-border-2)]"
          :style="{ background: `linear-gradient(to right, var(--color-accent) ${(chatParams?.temperature ?? 0.72) / 2 * 100}%, var(--color-bg-2) ${(chatParams?.temperature ?? 0.72) / 2 * 100}%)` }"
        />
      </div>
      <div class="param mb-3.5 last:mb-0">
        <div class="param-head flex justify-between items-baseline mb-2">
          <span class="param-name text-[12.5px] text-text-2">最大输出</span>
          <span class="param-val font-mono text-[11.5px] text-text tabular-nums">{{ (chatParams?.maxOutput ?? 4096).toLocaleString() }}</span>
        </div>
        <input type="range" v-model.number="chatParams.maxOutput" min="256" max="8192" step="128"
          class="slider w-full h-1 bg-bg-2 rounded-full appearance-none cursor-pointer [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:w-3 [&::-webkit-slider-thumb]:h-3 [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-text [&::-webkit-slider-thumb]:border-2 [&::-webkit-slider-thumb]:border-bg [&::-webkit-slider-thumb]:shadow-[0_0_0_1px_var(--color-border-2)]"
          :style="{ background: `linear-gradient(to right, var(--color-accent) ${((chatParams?.maxOutput ?? 4096) - 256) / (8192 - 256) * 100}%, var(--color-bg-2) ${((chatParams?.maxOutput ?? 4096) - 256) / (8192 - 256) * 100}%)` }"
        />
      </div>
      <div class="param mb-3.5 last:mb-0">
        <div class="param-head flex justify-between items-baseline mb-2">
          <span class="param-name text-[12.5px] text-text-2">Top-p</span>
          <span class="param-val font-mono text-[11.5px] text-text tabular-nums">{{ (chatParams?.topP ?? 0.95).toFixed(2) }}</span>
        </div>
        <input type="range" v-model.number="chatParams.topP" min="0" max="1" step="0.01"
          class="slider w-full h-1 bg-bg-2 rounded-full appearance-none cursor-pointer [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:w-3 [&::-webkit-slider-thumb]:h-3 [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-text [&::-webkit-slider-thumb]:border-2 [&::-webkit-slider-thumb]:border-bg [&::-webkit-slider-thumb]:shadow-[0_0_0_1px_var(--color-border-2)]"
          :style="{ background: `linear-gradient(to right, var(--color-accent) ${(chatParams?.topP ?? 0.95) * 100}%, var(--color-bg-2) ${(chatParams?.topP ?? 0.95) * 100}%)` }"
        />
      </div>
    </div>

    <div class="studio-section py-[18px] px-5 border-b border-border last:border-b-0">
      <div class="studio-h text-[11px] text-text-3 uppercase tracking-[0.14em] mb-3 flex items-center gap-1.5 font-medium">
        <Sparkles class="w-3 h-3" />系统提示
      </div>
      <textarea
        v-model="chatParams.systemPrompt"
        class="system-prompt border border-border rounded-lg px-3 py-2.5 text-[12.5px] text-text-2 leading-[1.55] bg-bg-input font-sans resize-none w-full h-24 focus:outline-none focus:border-accent-line"
        placeholder="定义 AI 的角色、行为风格或回答约束，例如：&#10;你是一位专业的技术顾问，回答简洁准确，优先使用代码示例说明。"
      ></textarea>
    </div>

    <div class="studio-section py-[18px] px-5 border-b border-border last:border-b-0">
      <div class="studio-h text-[11px] text-text-3 uppercase tracking-[0.14em] mb-3 flex items-center justify-between font-medium">
        <span class="flex items-center gap-1.5"><Layers class="w-3 h-3" />本次会话产物</span>
        <span class="normal-case tracking-normal text-[11px] text-text-3 font-mono">04</span>
      </div>
      <div class="recent-grid grid grid-cols-2 gap-2">
        <div v-for="(asset, i) in recentAssets" :key="i"
          class="recent-card aspect-square border border-border rounded-lg overflow-hidden cursor-pointer relative transition-all duration-150 hover:border-border-2 hover:-translate-y-px"
          :class="[`rc-${asset.type}`]"
        >
          <span class="label absolute top-2 left-2 text-[9.5px] tracking-[0.12em] uppercase text-[oklch(96%_0.01_80_/_0.8)] px-1.5 py-[2px] bg-[oklch(0%_0_0_/_0.32)] rounded-[3px] backdrop-blur-[4px] z-10 font-medium">{{ asset.label }}</span>
          <div v-if="asset.type === 'video'" class="play w-8 h-8 rounded-full bg-[oklch(96%_0.01_80_/_0.95)] text-[oklch(20%_0.01_60)] grid place-items-center absolute inset-0 m-auto">
            <svg class="w-3.5 h-3.5 ml-0.5" fill="currentColor" viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>
          </div>
          <div v-if="asset.type === 'audio'" class="wave flex items-center gap-[2px] w-full h-full px-3.5">
            <i v-for="n in 30" :key="n" class="flex-1 bg-accent rounded-sm block self-end" :style="{ height: (Math.random() * 28 + 4) + 'px', opacity: 0.45 + Math.random() * 0.5 }"></i>
          </div>
          <div v-if="asset.type === 'code'" class="bg-bg-2 p-[22px_14px_14px] font-mono text-[9.5px] leading-relaxed text-text-3 h-full">
            <pre><code><span class="text-accent">const</span> tokens = {<br/>  &nbsp;bg: <span class="text-success">"#1a1816"</span>,<br/>  &nbsp;ink: <span class="text-success">"#f4f0ea"</span>,<br/>  &nbsp;accent: <span class="text-success">"#e3a857"</span>,<br/>};</code></pre>
          </div>
          <span v-if="asset.meta1 || asset.meta2" class="meta-bottom absolute bottom-2 left-2 right-2 text-[10px] font-mono text-[oklch(96%_0.01_80_/_0.85)] flex justify-between z-10">
            <span>{{ asset.meta1 }}</span><span>{{ asset.meta2 }}</span>
          </span>
        </div>
      </div>
    </div>

    </div>

    <div v-else-if="activeTab === 'knowledge'" class="knowledge-content flex-1 p-5 overflow-hidden">
      <KnowledgeBasePanel />
    </div>
  </aside>
</template>

<style scoped>
.system-prompt::placeholder {
  color: var(--color-text-4);
  opacity: 1;
}
.recent-card.rc-image {
  background:
    radial-gradient(120% 80% at 25% 25%, oklch(82% 0.13 65 / 0.45), transparent 55%),
    linear-gradient(135deg, oklch(35% 0.06 50), oklch(20% 0.02 60));
}
.recent-card.rc-video {
  background: linear-gradient(135deg, oklch(28% 0.04 220), oklch(18% 0.02 230));
}
</style>
