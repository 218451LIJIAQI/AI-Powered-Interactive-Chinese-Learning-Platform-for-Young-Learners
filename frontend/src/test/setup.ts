import '@testing-library/jest-dom/vitest'

function createStorageMock(): Storage {
  const values = new Map<string, string>()

  return {
    get length() {
      return values.size
    },
    clear(): void {
      values.clear()
    },
    key(index: number): string | null {
      return Array.from(values.keys())[index] ?? null
    },
    removeItem(key: string): void {
      values.delete(String(key))
    },
    setItem(key: string, value: string): void {
      values.set(String(key), String(value))
    },
    getItem(key: string): string | null {
      return values.get(String(key)) ?? null
    },
  }
}

function ensureStorage(name: 'localStorage' | 'sessionStorage'): void {
  if (typeof window === 'undefined') {
    return
  }

  Object.defineProperty(window, name, {
    configurable: true,
    value: createStorageMock(),
  })
}

ensureStorage('localStorage')
ensureStorage('sessionStorage')
