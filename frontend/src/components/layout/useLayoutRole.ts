import { computed } from 'vue'

import { useAuthStore } from '@/stores/auth'
import type { UserRole } from '@/types/api'

/**
 * The signed-in role whose workspace chrome (sidebar, header) is shown, and the full message key of its label in the
 * `dashboard` bundle (a teacher not approved yet is an applicant).
 */
export function useLayoutRole() {
  const auth = useAuthStore()
  const role = computed<UserRole>(() => auth.activeRole ?? 'STUDENT')
  const isApplicant = computed(() => role.value === 'TEACHER' && !auth.isApprovedTeacher)
  const roleLabelKey = computed(() => {
    if (role.value === 'ADMIN') return 'dashboard.roleAdmin'
    if (role.value === 'TEACHER') return isApplicant.value ? 'dashboard.roleApplicant' : 'dashboard.roleTeacher'
    return 'dashboard.roleStudent'
  })
  return { role, isApplicant, roleLabelKey }
}
