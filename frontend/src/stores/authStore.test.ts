import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { STORAGE_KEYS } from '../constants/storageKeys'
import { useAuthStore, type CurrentUser } from './authStore'

const { apiClientGet, apiClientPost } = vi.hoisted(() => ({
  apiClientGet: vi.fn(),
  apiClientPost: vi.fn(),
}))

vi.mock('../services/apiClient', () => ({
  apiClient: {
    get: apiClientGet,
    post: apiClientPost,
  },
}))

const TOKEN_KEY = STORAGE_KEYS.AUTH_TOKEN

describe('authStore', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    apiClientGet.mockReset()
    apiClientPost.mockReset()
  })

  afterEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('initialises without a token, without a user, and not authenticated', () => {
    const store = useAuthStore()
    expect(store.token).toBeNull()
    expect(store.currentUser).toBeNull()
    expect(store.isAuthenticated).toBe(false)
    expect(store.roles).toEqual([])
  })

  it('reads an existing token from localStorage on creation', () => {
    localStorage.setItem(TOKEN_KEY, 'restored-token')
    const store = useAuthStore()
    expect(store.token).toBe('restored-token')
    // Authentication still requires a fetched currentUser.
    expect(store.isAuthenticated).toBe(false)
  })

  it('setToken persists the token and clearAuthState removes it', () => {
    const store = useAuthStore()
    store.setToken('new-token')
    expect(localStorage.getItem(TOKEN_KEY)).toBe('new-token')
    expect(store.token).toBe('new-token')

    store.clearAuthState()
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(store.token).toBeNull()
    expect(store.currentUser).toBeNull()
    expect(store.initialized).toBe(true)
  })

  it('hasRole and isTeacherApproved reflect the currentUser state', () => {
    const store = useAuthStore()
    expect(store.hasRole('STUDENT')).toBe(false)
    expect(store.isTeacherApproved()).toBe(false)

    store.currentUser = {
      id: 1,
      fullName: 'Approved Teacher',
      email: 'approved@example.com',
      preferredLanguage: 'en',
      roles: ['TEACHER'],
      studentProfile: null,
      teacherProfile: {
        id: 9,
        teacherStatus: 'APPROVED',
        bio: null,
        rejectionReason: null,
      },
    }

    expect(store.hasRole('TEACHER')).toBe(true)
    expect(store.hasRole('ADMIN')).toBe(false)
    expect(store.isTeacherApproved()).toBe(true)
  })

  it('shares the same in-flight fetchCurrentUser request across concurrent callers', async () => {
    localStorage.setItem(TOKEN_KEY, 'restored-token')

    let resolveRequest: ((value: { data: { data: CurrentUser } }) => void) | undefined
    apiClientGet.mockReturnValue(new Promise((resolve) => {
      resolveRequest = resolve
    }))

    const store = useAuthStore()
    const firstCall = store.fetchCurrentUser()
    const secondCall = store.fetchCurrentUser()

    expect(apiClientGet).toHaveBeenCalledTimes(1)
    expect(store.isFetchingCurrentUser).toBe(true)

    resolveRequest?.({
      data: {
        data: {
          id: 3,
          fullName: 'Concurrent Student',
          email: 'student@example.com',
          preferredLanguage: 'en',
          roles: ['STUDENT'],
          studentProfile: {
            id: 7,
            displayName: 'Concurrent Student',
            currentLevel: 'Beginner',
            totalPoints: 25,
          },
          teacherProfile: null,
        },
      },
    })

    const [firstResult, secondResult] = await Promise.all([firstCall, secondCall])
    expect(firstResult?.email).toBe('student@example.com')
    expect(secondResult?.email).toBe('student@example.com')
    expect(store.currentUser?.email).toBe('student@example.com')
    expect(store.isAuthenticated).toBe(true)
    expect(store.initialized).toBe(true)
    expect(store.isFetchingCurrentUser).toBe(false)
  })
})
