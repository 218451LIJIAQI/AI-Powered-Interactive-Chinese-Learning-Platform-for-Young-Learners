import axios from 'axios'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'
const PUBLIC_PROTECTED_PATHS = ['/student', '/teacher', '/admin', '/notifications', '/support-tickets']

export const apiClient = axios.create({
  baseURL: apiBaseUrl,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('ai-chinese-learning-token')

  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error.response?.status
    const requestUrl = typeof error.config?.url === 'string' ? error.config.url : ''
    const isAuthRequest = requestUrl.includes('/auth/login') || requestUrl.includes('/auth/register')

    if (status === 401 && !isAuthRequest) {
      localStorage.removeItem('ai-chinese-learning-token')

      if (
        typeof window !== 'undefined' &&
        window.location.pathname !== '/login' &&
        PUBLIC_PROTECTED_PATHS.some((path) => window.location.pathname.startsWith(path))
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
