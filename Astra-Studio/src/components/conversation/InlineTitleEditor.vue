// 会话管理组件 - 行内标题编辑器
<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'

const props = defineProps<{
  modelValue: string
  editing: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'save', value: string): void
  (e: 'cancel'): void
}>()

const inputRef = ref<HTMLInputElement | null>(null)
const localValue = ref(props.modelValue)

watch(() => props.editing, async (val) => {
  if (val) {
    localValue.value = props.modelValue
    await nextTick()
    inputRef.value?.focus()
    inputRef.value?.select()
  }
})

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter') {
    e.preventDefault()
    const trimmed = localValue.value.trim()
    if (trimmed && trimmed !== props.modelValue) {
      emit('update:modelValue', trimmed)
      emit('save', trimmed)
    } else {
      emit('cancel')
    }
  } else if (e.key === 'Escape') {
    emit('cancel')
  }
}

function handleBlur() {
  const trimmed = localValue.value.trim()
  if (trimmed && trimmed !== props.modelValue) {
    emit('update:modelValue', trimmed)
    emit('save', trimmed)
  } else {
    emit('cancel')
  }
}
</script>

<template>
  <span v-if="!editing" class="inline-title" @dblclick="$emit('update:modelValue', '')">
    {{ modelValue }}
  </span>
  <input
    v-else
    ref="inputRef"
    v-model="localValue"
    @keydown="handleKeydown"
    @blur="handleBlur"
    class="inline-title-input px-1 py-0.5 text-[13px] font-medium bg-bg border border-accent rounded outline-none"
  />
</template>

