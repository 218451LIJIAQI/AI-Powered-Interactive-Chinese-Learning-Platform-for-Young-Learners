import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { STORAGE_KEYS } from '../constants/storageKeys'
import { apiClient } from '../services/apiClient'
import type { ApiResponse } from '../types/api'
import { extractApiError } from '../utils/apiError'
import { safeGetItem, safeRemoveItem, safeSetItem } from '../utils/safeStorage'

export type UserRole = 'STUDENT' | 'TEACHER' | 'ADMIN'
export type TeacherStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | null

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

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(safeGetItem(STORAGE_KEYS.AUTH_TOKEN))
  const currentUser = ref<CurrentUser | null>(null)
  const initialized = ref(false)
  const isFetchingCurrentUser = ref(false)
  let fetchCurrentUserPromise: Promise<CurrentUser | null> | null = null
  let authStateVersion = 0

  const isAuthenticated = computed(() => Boolean(token.value) && Boolean(currentUser.value))
  const roles = computed<UserRole[]>(() => currentUser.value?.roles ?? [])
  const teacherStatus = computed<TeacherStatus>(() => currentUser.value?.teacherProfile?.teacherStatus ?? null)

  function setToken(nextToken: string | null) {
    token.value = nextToken
    if (nextToken) {
      safeSetItem(STORAGE_KEYS.AUTH_TOKEN, nextToken)
      return
    }

    safeRemoveItem(STORAGE_KEYS.AUTH_TOKEN)
  }

  function clearAuthState() {
    authStateVersion += 1
    fetchCurrentUserPromise = null
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

    if (fetchCurrentUserPromise) {
      return fetchCurrentUserPromise
    }

    const versionAtStart = authStateVersion
    isFetchingCurrentUser.value = true
    fetchCurrentUserPromise = (async () => {
      try {
        const response = await apiClient.get<ApiResponse<CurrentUser>>('/auth/me')
        if (versionAtStart !== authStateVersion) {
          return currentUser.value
        }

        currentUser.value = response.data.data
        initialized.value = true
        return currentUser.value
      } catch (error) {
        if (versionAtStart === authStateVersion) {
          clearAuthState()
        }
        throw new Error(extractApiError(error, 'Session expired. Please log in again.'))
      } finally {
        isFetchingCurrentUser.value = false
        fetchCurrentUserPromise = null
      }
    })()

    return fetchCurrentUserPromise
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
