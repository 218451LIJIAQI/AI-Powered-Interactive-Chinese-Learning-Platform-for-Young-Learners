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
