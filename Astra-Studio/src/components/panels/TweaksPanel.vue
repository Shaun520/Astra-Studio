<script setup lang="ts">
import { ref } from 'vue'
import { Settings2, X } from 'lucide-vue-next'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const isOpen = ref(props.modelValue)

function toggle() {
  isOpen.value = !isOpen.value
  emit('update:modelValue', isOpen.value)
}

function close() {
  isOpen.value = false
  emit('update:modelValue', false)
}

const themeMode = ref<'dark' | 'light'>('light')
const accentColor = ref<'amber' | 'sage' | 'rose'>('sage')
const density = ref<'loose' | 'default' | 'tight'>('default')

function setTheme(mode: 'dark' | 'light') {
  themeMode.value = mode
  document.documentElement.classList.toggle('light', mode === 'light')
}

function setAccent(color: 'amber' | 'sage' | 'rose') {
  accentColor.value = color
  const el = document.documentElement
  el.classList.remove('accent-sage', 'accent-rose')
  if (color !== 'amber') el.classList.add(`accent-${color}`)
}

function setDensity(den: 'loose' | 'default' | 'tight') {
  density.value = den
  const sizes = { loose: 15, default: 14, tight: 13 }
  document.body.style.fontSize = `${sizes[den]}px`
}
</script>

<template>
  <div>
    <button
      class="tweaks-toggle fixed bottom-4 right-4 z-[49] w-[38px] h-[38px] rounded-full bg-bg-2 border border-border-2 text-text-2 cursor-pointer grid place-items-center transition-all duration-200 shadow-[0_4px_12px_oklch(0%_0_0_/_0.3)] hover:text-text hover:bg-bg-3"
      :class="{ hidden: isOpen }"
      @click="toggle"
    >
      <Settings2 class="w-4 h-4" />
    </button>

    <Transition name="tweaks-slide">
      <div v-if="isOpen" class="tweaks fixed bottom-4 right-4 z-50 bg-bg-2 border border-border-2 rounded-xl w-[240px] overflow-hidden shadow-[0_10px_36px_oklch(0%_0_0_/_0.4)]">
        <div class="tweaks-h px-3.5 py-3 font-serif text-[17px] border-b border-border flex justify-between items-center">
          <span><em>Tweaks</em></span>
          <button class="x text-text-3 cursor-pointer w-[18px] h-[18px] grid place-items-center hover:text-text" @click="close">
            <X class="w-3.5 h-3.5" />
          </button>
        </div>
        <div class="tweaks-body p-3.5 flex flex-col gap-3.5">
          <div class="tweak-row flex flex-col gap-1.75">
            <label class="text-[10.5px] text-text-3 uppercase tracking-[0.12em] font-medium">主题</label>
            <div class="seg grid auto-flow-col grid-cols-[1fr_1fr] gap-px p-0.5 bg-bg border border-border rounded-lg">
              <button
                class="bg-transparent border-0 py-1 text-[11.5px] text-text-2 cursor-pointer rounded-md font-sans transition-colors duration-150"
                :class="{ 'on': themeMode === 'dark', 'bg-accent-soft !text-accent': themeMode === 'dark' }"
                @click="setTheme('dark')"
              >深色</button>
              <button
                class="bg-transparent border-0 py-1 text-[11.5px] text-text-2 cursor-pointer rounded-md font-sans transition-colors duration-150 hover:!text-text"
                :class="{ 'on': themeMode === 'light', 'bg-accent-soft !text-accent': themeMode === 'light' }"
                @click="setTheme('light')"
              >浅色</button>
            </div>
          </div>

          <div class="tweak-row flex flex-col gap-1.75">
            <label class="text-[10.5px] text-text-3 uppercase tracking-[0.12em] font-medium">强调色</label>
            <div class="seg grid auto-flow-col grid-cols-3 gap-px p-0.5 bg-bg border border-border rounded-lg">
              <button
                class="bg-transparent border-0 py-1 text-[11.5px] text-text-2 cursor-pointer rounded-md font-sans transition-colors duration-150"
                :class="{ 'on': accentColor === 'amber', 'bg-accent-soft !text-accent': accentColor === 'amber' }"
                @click="setAccent('amber')"
              >琥珀</button>
              <button
                class="bg-transparent border-0 py-1 text-[11.5px] text-text-2 cursor-pointer rounded-md font-sans transition-colors duration-150 hover:!text-text"
                :class="{ 'on': accentColor === 'sage', 'bg-accent-soft !text-accent': accentColor === 'sage' }"
                @click="setAccent('sage')"
              >青苔</button>
              <button
                class="bg-transparent border-0 py-1 text-[11.5px] text-text-2 cursor-pointer rounded-md font-sans transition-colors duration-150 hover:!text-text"
                :class="{ 'on': accentColor === 'rose', 'bg-accent-soft !text-accent': accentColor === 'rose' }"
                @click="setAccent('rose')"
              >陶土</button>
            </div>
          </div>

          <div class="tweak-row flex flex-col gap-1.75">
            <label class="text-[10.5px] text-text-3 uppercase tracking-[0.12em] font-medium">文字密度</label>
            <div class="seg grid auto-flow-col grid-cols-3 gap-px p-0.5 bg-bg border border-border rounded-lg">
              <button
                class="bg-transparent border-0 py-1 text-[11.5px] text-text-2 cursor-pointer rounded-md font-sans transition-colors duration-150 hover:!text-text"
                :class="{ 'on': density === 'loose', 'bg-accent-soft !text-accent': density === 'loose' }"
                @click="setDensity('loose')"
              >宽松</button>
              <button
                class="bg-transparent border-0 py-1 text-[11.5px] text-text-2 cursor-pointer rounded-md font-sans transition-colors duration-150"
                :class="{ 'on': density === 'default', 'bg-accent-soft !text-accent': density === 'default' }"
                @click="setDensity('default')"
              >默认</button>
              <button
                class="bg-transparent border-0 py-1 text-[11.5px] text-text-2 cursor-pointer rounded-md font-sans transition-colors duration-150 hover:!text-text"
                :class="{ 'on': density === 'tight', 'bg-accent-soft !text-accent': density === 'tight' }"
                @click="setDensity('tight')"
              >紧凑</button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.tweaks-slide-enter-active,
.tweaks-slide-leave-active {
  transition: transform 0.25s ease-out, opacity 0.25s ease-out;
}
.tweaks-slide-enter-from,
.tweaks-slide-leave-to {
  transform: translateY(calc(100% + 24px));
  opacity: 0;
  pointer-events: none;
}
.seg button.on {
  background: var(--color-accent-soft);
  color: var(--color-accent);
}
</style>
