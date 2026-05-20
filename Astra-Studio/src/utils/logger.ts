const isDev = import.meta.env.DEV

export const debug = (...args: any[]) => {
  if (isDev) console.log(...args)
}

export const warn = (...args: any[]) => {
  if (isDev) console.warn(...args)
}

export const error = (...args: any[]) => {
  console.error(...args)
}
