import { computed } from 'vue'
import { useRoute } from 'vue-router'

/**
 * Returns the role-scoped base path for the current route, e.g.
 *   /student/support-tickets/3  ->  '/student'
 *   /teacher/notifications      ->  '/teacher'
 *   /admin/support-tickets      ->  '/admin'
 *   /support-tickets            ->  '' (top-level fallback)
 *
 * Used by shared pages (notifications, support tickets) so navigation
 * stays inside the same role's layout instead of jumping out.
 */
export function useRoleBasePath() {
  const route = useRoute()
  return computed(() => {
    const path = route.path
    if (path.startsWith('/student/')) return '/student'
    if (path.startsWith('/teacher/')) return '/teacher'
    if (path.startsWith('/admin/')) return '/admin'
    return ''
  })
}
