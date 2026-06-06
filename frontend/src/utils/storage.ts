import type { AppLanguage } from '@/types/api'

export const storageKeys = {
  token: 'mandarin-spark.token',
  recoveryEmail: 'mandarin-spark.recovery-email',
  language: 'mandarin-spark.language',
  previewRole: 'mandarin-spark.preview-role',
} as const

function browserStorage(name: 'localStorage' | 'sessionStorage'): Storage | null {
  if (typeof window === 'undefined') {
    return null
  }

  try {
    return window[name]
  } catch {
    return null
  }
}

function getLocalStorage(): Storage | null {
  return browserStorage('localStorage')
}

export function getSessionStorage(): Storage | null {
  return browserStorage('sessionStorage')
}

export function safeGetItem(key: string, storage: Storage | null = getLocalStorage()): string | null {
  if (!storage) {
    return null
  }

  try {
    return storage.getItem(key)
  } catch {
    return null
  }
}

function writeStorage(storage: Storage | null, action: (storage: Storage) => void): void {
  if (!storage) return

  try {
    action(storage)
  } catch {}
}

export function safeSetItem(key: string, value: string, storage: Storage | null = getLocalStorage()): void {
  writeStorage(storage, (availableStorage) => availableStorage.setItem(key, value))
}

export function safeRemoveItem(key: string, storage: Storage | null = getLocalStorage()): void {
  writeStorage(storage, (availableStorage) => availableStorage.removeItem(key))
}

const validLocales: ReadonlySet<string> = new Set<AppLanguage>(['en', 'ms', 'zh'])

export function readPersistedLocale(): AppLanguage {
  const raw = safeGetItem(storageKeys.language)
  return raw !== null && validLocales.has(raw) ? (raw as AppLanguage) : 'en'
}
