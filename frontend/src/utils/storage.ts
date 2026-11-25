export const storageKeys = {
  /** Session storage, read once: the address typed on the sign-in page, handed to Forgot password. */
  recoveryEmail: 'mandarin-spark.recovery-email',
  language: 'mandarin-spark.language',
  /** Written when a tab starts an access-token refresh, so the other tabs learn the refresh cookie may rotate. */
  refreshMarker: 'mandarin-spark.refresh-started',
  /** Written on an explicit sign-in (the account id) or sign-out (null), so the other tabs follow (stores/auth.ts). */
  sessionOwner: 'mandarin-spark.session-owner',
} as const

/** The browser's storage, or null where reading it throws (e.g. blocked site data). */
function browserStorage(name: 'localStorage' | 'sessionStorage'): Storage | null {
  try {
    return window[name]
  } catch {
    return null
  }
}

export function getSessionStorage(): Storage | null {
  return browserStorage('sessionStorage')
}

/* The helpers below use localStorage unless given another storage, and never throw (blocked or full storage). */

export function safeGetItem(key: string, storage = browserStorage('localStorage')): string | null {
  try {
    return storage?.getItem(key) ?? null
  } catch {
    return null
  }
}

export function safeSetItem(key: string, value: string, storage = browserStorage('localStorage')): void {
  try {
    storage?.setItem(key, value)
  } catch {
    // Storage may be blocked or full; callers treat persistence as best-effort.
  }
}

export function safeRemoveItem(key: string, storage = browserStorage('localStorage')): void {
  try {
    storage?.removeItem(key)
  } catch {
    // Storage may be blocked; ignore cleanup errors.
  }
}

/** An account id means signed in, null means explicitly signed out, and undefined means no readable announcement. */
export function sessionOwner(announcement = safeGetItem(storageKeys.sessionOwner)): number | null | undefined {
  if (!announcement) return undefined
  try {
    const userId: unknown = JSON.parse(announcement)?.userId
    return (typeof userId === 'number' && Number.isSafeInteger(userId) && userId > 0) || userId === null
      ? userId
      : undefined
  } catch {
    return undefined
  }
}

/** Tells the browser's other tabs that this tab signed an account in (its id) or signed out (null). */
export function announceSessionOwner(userId: number | null): void {
  // The time makes every announcement a change, so the other tabs hear a second sign-in of the same account too.
  safeSetItem(storageKeys.sessionOwner, JSON.stringify({ userId, at: Date.now() }))
}
