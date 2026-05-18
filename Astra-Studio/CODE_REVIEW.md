# Code Review Report

> **项目**: Astra Studio  
> **日期**: 2026-05-10  
> **范围**: 全量源码审查（`src/` 目录）  
> **结论**: ✅ 可提交 — 1 个需修复项，3 个优化建议

---

## 1. 诊断检查

| 文件 | Error | Warning | 状态 |
|------|-------|---------|------|
| `src/App.vue` | 0 | 0 | ✅ |
| `src/components/AppSidebar.vue` | 0 | 0 | ✅ |
| `src/components/Composer.vue` | 0 | 0 | ✅ |
| `src/components/ChatMessage.vue` | 0 | 0 | ✅ |
| `src/components/AttachCard.vue` | 0 | 0 | ✅ |
| `src/components/ImagePreview.vue` | 0 | 0 | ✅ |
| `src/types/index.ts` | 0 | 0 | ✅ |

**总计: 0 Error / 0 Warning**

---

## 2. 需修复项

### 🔴 P0 — 死代码：空函数 `rotate()` 未使用

- **文件**: `src/components/ImagePreview.vue:88`
- **问题**: 定义了空函数但模板中未调用，工具栏的 🔄 按钮绑定的是 `resetTransform()`
- **影响**: 代码冗余，增加维护困惑
- **建议**: 删除 `rotate()` 函数

```ts
// 当前代码
function rotate() {}   // ← 空函数，无调用方

// 修复方案
// 直接删除此行
```

---

## 3. 优化建议

### 🟡 P1 — 非空断言风险 (`inject` 使用 `!`)

- **文件**: `src/components/Composer.vue:5`
- **问题**: 使用 `!` 非空断言，若组件脱离 App 独立使用会运行时报错
- **对比**: `AttachCard.vue` 做了安全检查 `if (openImagePreview) { ... }`
- **风险等级**: 低（当前 Composer 始终在 App 内渲染）
- **建议**: 统一为安全写法

```ts
// 当前 (Composer.vue)
const openImagePreview = inject<...>('openImagePreview')!

// 建议
const openImagePreview = inject<...>('openImagePreview')
// 调用时加判断
function previewImage(att: PendingAttachment) {
  if (!openImagePreview) return
  // ...
}
```

### 🟡 P2 — 工具函数重复定义

以下两个函数在两处完全相同：

| 函数 | `App.vue` 行号 | `Composer.vue` 行号 |
|------|---------------|---------------------|
| `formatFileSize()` | L53–L57 | L109–L113 |
| `getFileTypeLabel()` | L59–L68 | L115–L124 |

**建议**: 后续抽取到 `src/utils/file.ts`，通过 import 复用。当前不影响功能。

### 🟡 P3 — v-html 安全性注意

- **文件**: `src/components/ChatMessage.vue:42`
- **问题**: 使用 `v-html` 渲染 AI 回复内容
- **现状**: 当前为模拟数据，无 XSS 风险
- **建议**: 接入真实 API 后引入 DOMPurify 或类似库做 HTML sanitize

```ts
// 未来方案示例
import DOMPurify from 'dompurify'
const safeHtml = computed(() => DOMPurify.sanitize(props.content))
```

---

## 4. 架构亮点

### provide/inject 预览模式

```
App.vue (provide)
  ├─ Composer.vue (inject → 触发预览)
  └─ AttachCard.vue (inject → 触发预览)
        ↓
  ImagePreview.vue (全局 Teleport 到 body)
```

解耦清晰，子组件无需 prop 逐层传递。

### 响应式附件管理

Composer 中使用 `reactive()` 包裹 attachment 对象，解决 FileReader 异步回调后视图不更新的经典陷阱。

### 拖拽计数器防抖

使用 `dragCounter` 计数器解决子元素 `dragleave` 冒泡导致的误触发问题，而非简单布尔标志。

### 条件渲染避免空白气泡

ChatMessage 通过 `hasContent()` 判断，纯文件消息不渲染内容气泡。

### 键盘可访问性

ImagePreview 支持：
- `Esc` 关闭
- `←` / `→` 切换图片
- 滚轮缩放
- 双击还原

### body overflow 管理

ImagePreview 打开时锁定页面滚动，关闭时恢复，防止背景滚动穿透。

---

## 5. 变更清单

本次涉及的核心变更：

| 变更项 | 涉及文件 | 说明 |
|--------|---------|------|
| 图片预览 Lightbox | 新建 `ImagePreview.vue` | 全屏预览、缩放、拖拽平移、导航 |
| Sidebar 下拉 Tab 重构 | `AppSidebar.vue` | 对话独立 + 创作工具下拉收纳 |
| Composer 图片预览入口 | `Composer.vue` | 缩略图点击触发预览 |
| AttachCard 图片预览入口 | `AttachCard.vue` | 卡片点击触发预览 |
| App 全局预览注册 | `App.vue` | provide + ImagePreview 组件挂载 |

---

## 6. 总结

| 维度 | 评分 | 说明 |
|------|------|------|
| 类型安全 | ⭐⭐⭐⭐⭐ | TypeScript 全面覆盖，零类型错误 |
| 代码质量 | ⭐⭐⭐⭐ | 结构清晰，仅 1 处死代码 |
| 可访问性 | ⭐⭐⭐⭐ | 键盘支持完善，语义化标签 |
| 性能 | ⭐⭐⭐⭐⭐ | Transition 动画流畅，will-change 优化 |
| 安全性 | ⭐⭐⭐⭐ | v-html 需后续加固（模拟阶段可接受） |

**最终判定**: ✅ **通过审查，可以提交**
