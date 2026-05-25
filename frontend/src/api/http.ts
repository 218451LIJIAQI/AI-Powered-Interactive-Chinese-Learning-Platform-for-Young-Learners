/**
 * Shared axios instance used by every API service module.
 *
 * <h2>Token attachment</h2>
 * <p>The access token is kept in memory only. Login / refresh responses
 * update this module-level value; nothing writes the bearer token to
 * localStorage or sessionStorage.</p>
 *
 * <h2>401 handling</h2>
 * <p>The first 401 attempts a refresh-cookie rotation, then retries the
 * original request with the new access token. If refresh fails, the
 * store is notified so the UI can clear the in-memory session.</p>
 *
 * <h2>Timeout</h2>
 * <p>20 seconds is an upper bound chosen for the AI endpoints; faster
 * calls return well before this and slow timeouts are surfaced as
 * a learner-friendly "took too long" message by {@code apiError.ts}.</p>
 */
import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'

import { appEnv } from '@/config/env'
import { safeRemoveItem, storageKeys } from '@/utils/storage'
import type { ApiResponse, AuthResponse } from '@/types/api'

export const http = axios.create({
  baseURL: appEnv.apiBaseUrl,
  timeout: 20000,
  withCredentials: true,
})

let accessToken: string | null = null
let refreshPromise: Promise<string | null> | null = null

interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  _refreshRetried?: boolean
}

export function setAccessToken(token: string | null) {
  accessToken = token
  safeRemoveItem(storageKeys.token)
  safeRemoveItem(storageKeys.token, window.sessionStorage)
}

export function getAccessToken() {
  return accessToken
}

export async function refreshAccessToken(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = http
      .post<ApiResponse<AuthResponse>>('/auth/refresh', undefined, { _refreshRetried: true } as RetriableRequestConfig)
      .then((response) => {
        const token = response.data.data?.token ?? null
        setAccessToken(token)
        return token
      })
      .catch(() => {
        setAccessToken(null)
        return null
      })
      .finally(() => {
        refreshPromise = null
      })
  }

  return refreshPromise
}

http.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }

  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const originalRequest = error.config as RetriableRequestConfig | undefined
      const isAuthSessionEndpoint =
        originalRequest?.url?.includes('/auth/login') || originalRequest?.url?.includes('/auth/refresh')
      const shouldAttemptRefresh = originalRequest && !originalRequest._refreshRetried && !isAuthSessionEndpoint

      if (shouldAttemptRefresh) {
        const refreshedToken = await refreshAccessToken()
        if (refreshedToken) {
          originalRequest._refreshRetried = true
          originalRequest.headers.Authorization = `Bearer ${refreshedToken}`
          return http(originalRequest)
        }
      }

      setAccessToken(null)
      if (!isAuthSessionEndpoint) {
        window.dispatchEvent(new CustomEvent('mandarin-spark:auth-invalidated'))
      }
    }

    return Promise.reject(error)
  },
)
