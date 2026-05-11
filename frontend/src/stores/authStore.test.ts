import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { STORAGE_KEYS } from '../constants/storageKeys'
import { useAuthStore } from './authStore'

const TOKEN_KEY = STORAGE_KEYS.AUTH_TOKEN

describe('authStore', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  afterEach(() => {
    localStorage.clear()
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
})
