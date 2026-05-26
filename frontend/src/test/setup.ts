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
    key(index: number) {
      return Array.from(values.keys())[index] ?? null
    },
    removeItem(key: string) {
      values.delete(String(key))
    },
    setItem(key: string, value: string) {
      values.set(String(key), String(value))
    },
    getItem(key: string) {
      return values.has(String(key)) ? values.get(String(key))! : null
    },
  }
}

function ensureStorage(name: 'localStorage' | 'sessionStorage') {
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
