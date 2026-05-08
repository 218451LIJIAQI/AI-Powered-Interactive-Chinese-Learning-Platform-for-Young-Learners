import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

export type UserRole = 'STUDENT' | 'TEACHER' | 'ADMIN' | null

const TOKEN_KEY = 'ai-chinese-learning-token'
const ROLE_KEY = 'ai-chinese-learning-role'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const userRole = ref<UserRole>((localStorage.getItem(ROLE_KEY) as UserRole) || null)

  const isAuthenticated = computed(() => Boolean(token.value))

  function setSession(nextToken: string, nextRole: Exclude<UserRole, null>) {
    token.value = nextToken
    userRole.value = nextRole
    localStorage.setItem(TOKEN_KEY, nextToken)
    localStorage.setItem(ROLE_KEY, nextRole)
  }

  function clearSession() {
    token.value = null
    userRole.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(ROLE_KEY)
  }

  return {
    token,
    userRole,
    isAuthenticated,
    setSession,
    clearSession,
  }
})
