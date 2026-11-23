import { computed } from 'vue'
import { useRoute } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import type { UserRole } from '@/types/api'

/** The open dashboard names its own role. An account can have access to both dashboards. */
export function useWorkspaceRole() {
  const route = useRoute()
  const auth = useAuthStore()
  return computed<UserRole>(() => route.meta.roles?.[0] ?? auth.activeRole ?? 'STUDENT')
}
