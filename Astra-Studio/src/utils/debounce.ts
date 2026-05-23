/**
 * 防抖函数 - 在指定延迟时间内只执行最后一次调用
 * @param fn 要执行的函数
 * @param delay 延迟时间（毫秒）
 * @returns 防抖后的函数
 */
export function debounce<T extends (...args: any[]) => any>(
  fn: T,
  delay: number = 300
): (...args: Parameters<T>) => void {
  let timeoutId: ReturnType<typeof setTimeout> | null = null

  return function (this: any, ...args: Parameters<T>) {
    if (timeoutId) {
      clearTimeout(timeoutId)
    }

    timeoutId = setTimeout(() => {
      fn.apply(this, args)
      timeoutId = null
    }, delay)
  }
}

/**
 * 节流函数 - 在指定时间间隔内只执行一次
 * @param fn 要执行的函数
 * @param interval 时间间隔（毫秒）
 * @returns 节流后的函数
 */
export function throttle<T extends (...args: any[]) => any>(
  fn: T,
  interval: number = 300
): (...args: Parameters<T>) => void {
  let lastExecutionTime = 0
  let timeoutId: ReturnType<typeof setTimeout> | null = null

  return function (this: any, ...args: Parameters<T>) {
    const now = Date.now()
    const remainingTime = interval - (now - lastExecutionTime)

    if (remainingTime <= 0) {
      if (timeoutId) {
        clearTimeout(timeoutId)
        timeoutId = null
      }

      fn.apply(this, args)
      lastExecutionTime = now
    } else if (!timeoutId) {
      timeoutId = setTimeout(() => {
        fn.apply(this, args)
        lastExecutionTime = Date.now()
        timeoutId = null
      }, remainingTime)
    }
  }
}

/**
 * 异步防抖 - 用于异步操作（如 API 调用）
 * @param fn 异步函数
 * @param delay 延迟时间（毫秒）
 * @returns 包装后的异步函数，会取消之前的未完成调用
 */
export function asyncDebounce<T extends (...args: any[]) => Promise<any>>(
  fn: T,
  delay: number = 300
): (...args: Parameters<T>) => Promise<ReturnType<T>> {
  let timeoutId: ReturnType<typeof setTimeout> | null = null

  return function (this: any, ...args: Parameters<T>): Promise<ReturnType<T>> {
    return new Promise((resolve,reject) => {
      if (timeoutId) {
        clearTimeout(timeoutId)
      }

      timeoutId = setTimeout(async () => {
        try {
          const result = await fn.apply(this, args)
          resolve(result)
        } catch (error) {
          reject(error)
        }
        timeoutId = null
      }, delay)
    })
  }
}
