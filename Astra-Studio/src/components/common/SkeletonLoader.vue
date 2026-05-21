// 通用组件 - 骨架屏加载器
<template>
  <div class="skeleton-loader flex flex-col gap-3">
    <div v-for="i in 3" :key="i" class="skeleton-item flex items-center gap-2.5 px-2.5 py-2.5">
      <div class="skeleton-icon w-4 h-4 rounded bg-bg-2 shimmer" />
      <div class="flex-1 min-w-0 space-y-1.5">
        <div class="skeleton-line h-3 rounded bg-bg-2 shimmer" :style="{ width: i === 1 ? '60%' : i === 2 ? '75%' : '50%' }" />
        <div class="skeleton-line h-2.5 w-4/5 rounded bg-bg-2/60 shimmer" />
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}
.shimmer {
  background: linear-gradient(90deg, var(--color-bg-2) 25%, var(--color-bg-hover) 50%, var(--color-bg-2) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s ease-in-out infinite;
}
</style>

