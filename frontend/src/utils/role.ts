import type { UserRole } from '@/types/api'

/**
 * Maps a user role to the home route the auth flow lands on after
 * sign-in or whenever a deep-link is missing.
 *
 * Returning `'/'` for null routes a not-yet-logged-in user back to
 * the public landing page rather than 404-ing them into an
 * authenticated surface.
 */
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
