import type { UserRole } from '@/types/api'

export function defaultRouteForRole(role: UserRole | null) {
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
