import '@testing-library/jest-dom/vitest'

function createStorageMock(): Storage {
  const values = new Map<string, string>()

  return {
    get length() {
      return values.size
    },
    clear() {
      values.clear()
    },
    getItem(key: string) {
      return values.has(key) ? values.get(key)! : null
    },
    key(index: number) {
      return Array.from(values.keys())[index] ?? null
    },
    removeItem(key: string) {
      values.delete(key)
    },
    setItem(key: string, value: string) {
      values.set(key, value)
    },
  }
}

function ensureStorage(name: 'localStorage' | 'sessionStorage') {
  if (typeof window === 'undefined' || window[name]) {
    return
  }

  Object.defineProperty(window, name, {
    configurable: true,
    value: createStorageMock(),
  })
}

ensureStorage('localStorage')
ensureStorage('sessionStorage')
