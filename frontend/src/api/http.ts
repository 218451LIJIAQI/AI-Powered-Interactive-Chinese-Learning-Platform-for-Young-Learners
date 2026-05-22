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
