/**
 * Shared axios instance used by every API service module.
 *
 * <h2>Token attachment</h2>
 * <p>Reads the JWT from {@code sessionStorage} (per the post-auth-
 * hardening pass) and attaches it as {@code Authorization: Bearer}.
 * Quotes are stripped because older builds JSON-encoded the token
 * when persisting; the guard keeps existing browsers from sending
 * malformed headers after the upgrade.</p>
 *
 * <h2>401 handling</h2>
 * <p>When the backend rejects with 401 the interceptor:</p>
 * <ol>
 *   <li>Clears the stored token from both session + local storage
 *       (defence in depth against the legacy {@code localStorage}
 *       token).</li>
 *   <li>Emits a {@code mandarin-spark:auth-invalidated} CustomEvent
 *       so {@code main.ts} can clear the Pinia store and route the
 *       user to {@code /auth} (only when on a protected route).</li>
 *   <li>Rejects the promise so the calling component still sees the
 *       error and can show a friendly message.</li>
 * </ol>
 *
 * <h2>Timeout</h2>
 * <p>20 seconds is an upper bound chosen for the AI endpoints; faster
 * calls return well before this and slow timeouts are surfaced as
 * a learner-friendly "took too long" message by {@code apiError.ts}.</p>
 */
import axios from 'axios'

import { appEnv } from '@/config/env'
import { safeGetItem, safeRemoveItem, storageKeys } from '@/utils/storage'

export const http = axios.create({
  baseURL: appEnv.apiBaseUrl,
  timeout: 20000,
})

http.interceptors.request.use((config) => {
  const token = safeGetItem(storageKeys.token, window.sessionStorage)

  if (token) {
    config.headers.Authorization = `Bearer ${token.replaceAll('"', '')}`
  }

  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      safeRemoveItem(storageKeys.token)
      safeRemoveItem(storageKeys.token, window.sessionStorage)
      window.dispatchEvent(new CustomEvent('mandarin-spark:auth-invalidated'))
    }

    return Promise.reject(error)
  },
)
