import { ref, readonly, type App } from 'vue'

export type ToastType = 'success' | 'error' | 'warning' | 'info' | 'loading'

export interface Toast {
  id: number
  type: ToastType
  title: string
  message?: string
  duration?: number
  dismissible?: boolean
}

const toasts = ref<Toast[]>([])
let nextId = 0

function classifyError(err: unknown): { type: ToastType; title: string; message: string } {
  if (err instanceof DOMException && err.name === 'AbortError') {
    return { type: 'info', title: '请求已取消', message: '' }
  }
  if (err instanceof Error) {
    const msg = err.message.toLowerCase()
    if (msg.includes('timeout') || msg.includes('timed out') || msg.includes('超时')) {
      return { type: 'warning', title: '请求超时', message: '网络响应较慢，请稍后重试' }
    }
    if (msg.includes('network') || msg.includes('fetch') || msg.includes('failed to fetch') || msg.includes('net::ERR')) {
      return { type: 'error', title: '网络连接失败', message: '请检查网络后重试' }
    }
    if (msg.includes('401') || msg.includes('unauthorized') || msg.includes('认证')) {
      return { type: 'error', title: '认证失败', message: '请重新登录' }
    }
    if (msg.includes('403') || msg.includes('forbidden')) {
      return { type: 'error', title: '权限不足', message: '您没有执行此操作的权限' }
    }
    if (msg.includes('429') || msg.includes('too many requests') || msg.includes('rate limit')) {
      return { type: 'warning', title: '请求过于频繁', message: '请稍后再试' }
    }
    if (msg.includes('500') || msg.includes('502') || msg.includes('503') || msg.includes('504')) {
      return { type: 'error', title: '服务暂时不可用', message: '服务端异常，请稍后重试' }
    }
    if (msg.includes('上传失败') || msg.includes('upload')) {
      return { type: 'error', title: '文件上传失败', message: '请检查文件大小或格式后重试' }
    }
    return { type: 'error', title: '操作失败', message: err.message }
  }
  return { type: 'error', title: '未知错误', message: String(err) }
}

export function useToast() {
  function add(toast: Omit<Toast, 'id'>): number {
    const id = ++nextId
    const t = { ...toast, id }
    toasts.value.push(t)

    if (t.type !== 'loading' && t.duration !== Infinity) {
      setTimeout(() => remove(id), t.duration ?? (t.type === 'error' ? 5000 : 3000))
    }

    return id
  }

  function remove(id: number) {
    const idx = toasts.value.findIndex(t => t.id === id)
    if (idx !== -1) toasts.value.splice(idx, 1)
  }

  function success(title: string, message?: string) {
    return add({ type: 'success', title, message })
  }

  function warning(title: string, message?: string) {
    return add({ type: 'warning', title, message })
  }

  function info(title: string, message?: string) {
    return add({ type: 'info', title, message })
  }

  function errorToast(title: string, message?: string) {
    return add({ type: 'error', title, message })
  }

  function loading(title: string, message?: string): number {
    return add({ type: 'loading', title, message, duration: Infinity })
  }

  function fromError(err: unknown, fallbackTitle?: string): number {
    const classified = classifyError(err)
    return add({
      type: classified.type,
      title: fallbackTitle ?? classified.title,
      message: classified.message,
    })
  }

  function clear() {
    toasts.value.splice(0)
  }

  return {
    toasts: readonly(toasts),
    add,
    remove,
    success,
    warning,
    info,
    error: errorToast,
    loading,
    fromError,
    clear,
  }
}

const globalToast = useToast()

export function installToast(app: App) {
  app.provide('toast', globalToast)
}

export default globalToast
