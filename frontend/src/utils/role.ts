import type { RouteLocationRaw } from 'vue-router'

import type { UserRole } from '@/types/api'

type DefaultRoleRoute = '/' | '/student/home' | '/teacher/dashboard' | '/admin/dashboard'

export function defaultRouteForRole(role: UserRole | null): DefaultRoleRoute {
  switch (role) {
    case 'STUDENT':
      return '/student/home'
    case 'TEACHER':
      return '/teacher/dashboard'
    case 'ADMIN':
      return '/admin/dashboard'
    default:
      return '/'
  }
}

const AI_HELPER_PATH = '/student/ai'

/**
 * The public pages' "ask the AI helper" action: a student opens the helper; anyone else signs in first and is sent
 * back to it (the auth route would send an already signed-in user to their own home page instead).
 */
export function aiHelperEntryRoute(isStudent: boolean): RouteLocationRaw {
  return isStudent ? AI_HELPER_PATH : { name: 'auth', query: { redirect: AI_HELPER_PATH } }
}
