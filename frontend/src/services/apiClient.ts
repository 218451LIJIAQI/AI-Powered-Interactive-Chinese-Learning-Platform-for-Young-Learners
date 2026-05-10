import axios from 'axios'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'

const TOKEN_KEY = 'ai-chinese-learning-token'

const PUBLIC_PROTECTED_PATH_PREFIXES = [
  '/student',
  '/teacher',
  '/admin',
  '/notifications',
  '/support-tickets',
  '/payment',
]

const AUTH_ENDPOINT_FRAGMENTS = ['/auth/login', '/auth/register', '/auth/forgot-password', '/auth/reset-password']

export const apiClient = axios.create({
  baseURL: apiBaseUrl,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)

  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  return config
})

function isProtectedClientPath(pathname: string) {
  return PUBLIC_PROTECTED_PATH_PREFIXES.some((prefix) => pathname.startsWith(prefix))
}

function isAuthRequestUrl(requestUrl: string) {
  return AUTH_ENDPOINT_FRAGMENTS.some((fragment) => requestUrl.includes(fragment))
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error.response?.status
    const requestUrl = typeof error.config?.url === 'string' ? error.config.url : ''

    if (status === 401 && !isAuthRequestUrl(requestUrl)) {
      localStorage.removeItem(TOKEN_KEY)

      if (
        typeof window !== 'undefined' &&
        window.location.pathname !== '/login' &&
        isProtectedClientPath(window.location.pathname)
      ) {
        const redirect = `${window.location.pathname}${window.location.search}`
        window.location.assign(`/login?redirect=${encodeURIComponent(redirect)}`)
      }
    }

    if (status === 403 && typeof window !== 'undefined') {
      window.dispatchEvent(
        new CustomEvent('app:forbidden', {
          detail: 'You do not have permission to access that page.',
        }),
      )
    }

    return Promise.reject(error)
  },
)
