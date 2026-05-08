import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { AxiosError } from 'axios'

import { apiClient } from '../services/apiClient'

export type UserRole = 'STUDENT' | 'TEACHER' | 'ADMIN'
export type TeacherStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | null
export type SupportedLocale = 'en' | 'ms' | 'zh'

type StudentProfileSummary = {
  id: number
  displayName: string | null
  currentLevel: string | null
  totalPoints: number
}

type TeacherProfileSummary = {
  id: number
  teacherStatus: Exclude<TeacherStatus, null>
  bio: string | null
  rejectionReason: string | null
}

export type CurrentUser = {
  id: number
  fullName: string
  email: string
  preferredLanguage: string | null
  roles: UserRole[]
  studentProfile: StudentProfileSummary | null
  teacherProfile: TeacherProfileSummary | null
}

type ApiResponse<T> = {
  success: boolean
  message: string
  data: T
  errors: string[]
}

type LoginResponse = {
  token: string
  user: {
    id: number
    fullName: string
    email: string
    roles: UserRole[]
    teacherStatus: TeacherStatus
  }
}

type RegisterPayloadBase = {
  fullName: string
  email: string
  password: string
  confirmPassword: string
}

type StudentRegistrationPayload = RegisterPayloadBase & {
  preferredLanguage?: string
}

type TeacherRegistrationPayload = RegisterPayloadBase & {
  bio?: string
}

const TOKEN_KEY = 'ai-chinese-learning-token'

function extractApiError(error: unknown, fallback: string) {
  if (error instanceof AxiosError) {
    const responseData = error.response?.data as Partial<ApiResponse<unknown>> | undefined
    if (Array.isArray(responseData?.errors) && responseData.errors.length > 0) {
      return responseData.errors[0]
    }

    if (typeof responseData?.message === 'string' && responseData.message.trim()) {
      return responseData.message
    }
  }

  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return fallback
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const currentUser = ref<CurrentUser | null>(null)
  const initialized = ref(false)
  const isFetchingCurrentUser = ref(false)

  const isAuthenticated = computed(() => Boolean(token.value) && Boolean(currentUser.value))
  const roles = computed<UserRole[]>(() => currentUser.value?.roles ?? [])
  const teacherStatus = computed<TeacherStatus>(() => currentUser.value?.teacherProfile?.teacherStatus ?? null)

  function setToken(nextToken: string | null) {
    token.value = nextToken
    if (nextToken) {
      localStorage.setItem(TOKEN_KEY, nextToken)
      return
    }

    localStorage.removeItem(TOKEN_KEY)
  }

  function clearAuthState() {
    setToken(null)
    currentUser.value = null
    initialized.value = true
  }

  function hasRole(role: UserRole) {
    return roles.value.includes(role)
  }

  function isTeacherApproved() {
    return teacherStatus.value === 'APPROVED'
  }

  async function login(payload: { email: string; password: string }) {
    const response = await apiClient.post<ApiResponse<LoginResponse>>('/auth/login', payload)
    setToken(response.data.data.token)
    await fetchCurrentUser()
    return currentUser.value
  }

  async function registerStudent(payload: StudentRegistrationPayload) {
    const response = await apiClient.post<ApiResponse<unknown>>('/auth/register/student', payload)
    return response.data.message
  }

  async function registerTeacher(payload: TeacherRegistrationPayload) {
    const response = await apiClient.post<ApiResponse<unknown>>('/auth/register/teacher', payload)
    return response.data.message
  }

  async function fetchCurrentUser() {
    if (!token.value) {
      currentUser.value = null
      initialized.value = true
      return null
    }

    isFetchingCurrentUser.value = true

    try {
      const response = await apiClient.get<ApiResponse<CurrentUser>>('/auth/me')
      currentUser.value = response.data.data
      initialized.value = true
      return currentUser.value
    } catch (error) {
      clearAuthState()
      throw new Error(extractApiError(error, 'Session expired. Please log in again.'))
    } finally {
      isFetchingCurrentUser.value = false
    }
  }

  async function logout() {
    try {
      if (token.value) {
        await apiClient.post('/auth/logout')
      }
    } catch {
      // Stateless logout can safely ignore backend errors.
    } finally {
      clearAuthState()
    }
  }

  return {
    token,
    currentUser,
    initialized,
    isFetchingCurrentUser,
    isAuthenticated,
    roles,
    teacherStatus,
    setToken,
    clearAuthState,
    hasRole,
    isTeacherApproved,
    login,
    registerStudent,
    registerTeacher,
    fetchCurrentUser,
    logout,
  }
})
