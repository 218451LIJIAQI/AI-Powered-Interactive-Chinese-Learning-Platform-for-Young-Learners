export const storageKeys = {
  token: 'mandarin-spark.token',
  language: 'mandarin-spark.language',
  previewRole: 'mandarin-spark.preview-role',
} as const

export function safeGetItem(key: string, storage: Storage = window.localStorage): string | null {
  try {
    return storage.getItem(key)
  } catch {
    return null
  }
}

export function safeSetItem(key: string, value: string, storage: Storage = window.localStorage) {
  try {
    storage.setItem(key, value)
  } catch {
  }
}

export function safeRemoveItem(key: string, storage: Storage = window.localStorage) {
  try {
    storage.removeItem(key)
  } catch {
  }
}
