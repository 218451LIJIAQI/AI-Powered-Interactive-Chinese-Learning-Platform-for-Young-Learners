/**
 * Defensive `localStorage` access.
 *
 * Browsers can throw on `localStorage.getItem` / `setItem` / `removeItem` in:
 *   - Safari "block all cookies" / strict private browsing
 *   - iOS Lockdown mode
 *   - Firefox `dom.storage.enabled = false`
 *   - Quota-exceeded scenarios on `setItem`
 *
 * We never want a thrown SecurityError or QuotaExceededError to crash a code
 * path. These helpers swallow such errors and return safe defaults so the
 * caller can continue with in-memory state.
 */

export function safeGetItem(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

export function safeSetItem(key: string, value: string): void {
  try {
    localStorage.setItem(key, value)
  } catch {
    // Quota exceeded or storage disabled; silently degrade to in-memory only.
  }
}

export function safeRemoveItem(key: string): void {
  try {
    localStorage.removeItem(key)
  } catch {
    // Storage disabled; nothing to remove.
  }
}
