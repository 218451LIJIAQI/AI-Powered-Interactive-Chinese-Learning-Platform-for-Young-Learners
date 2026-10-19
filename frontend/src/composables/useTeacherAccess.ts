import { computed, ref } from 'vue'
import type { ComputedRef } from 'vue'
import { useI18n } from 'vue-i18n'

import { useAuthStore } from '@/stores/auth'
import { isForbiddenApiError } from '@/utils/apiError'

interface TeacherAccessState {
  /** True for pending/rejected teachers (D13), or after the backend answered 403 to a teaching endpoint. */
  readonly teacherApprovalLocked: ComputedRef<boolean>
  readonly teacherApprovalTitle: ComputedRef<string>
  readonly teacherApprovalMessage: ComputedRef<string>
  /** Runs a teacher API request and switches the page into the locked state when the backend returns 403. */
  readonly guardTeacherRequest: <T>(request: () => Promise<T>) => Promise<T>
  /** Switches the page into the locked state, e.g. after a child component received a 403. */
  readonly markApprovalLocked: () => void
}

export function useTeacherAccess(): TeacherAccessState {
  const auth = useAuthStore()
  const { t } = useI18n({ useScope: 'global' })
  const serverLocked = ref(false)

  const teacherStatus = computed(() => auth.currentUser?.teacherProfile?.teacherStatus ?? 'PENDING')
  const statusLocked = computed(() => auth.activeRole === 'TEACHER' && teacherStatus.value !== 'APPROVED')
  const teacherApprovalLocked = computed(() => statusLocked.value || serverLocked.value)
  const teacherApprovalTitle = computed(() => {
    if (!statusLocked.value && serverLocked.value) return t('teacherAccess.forbiddenTitle')
    return t(teacherStatus.value === 'REJECTED' ? 'teacherAccess.rejectedTitle' : 'teacherAccess.pendingTitle')
  })
  const teacherApprovalMessage = computed(() => {
    if (!statusLocked.value && serverLocked.value) return t('teacherAccess.forbiddenMessage')
    return t(teacherStatus.value === 'REJECTED' ? 'teacherAccess.rejectedMessage' : 'teacherAccess.pendingMessage')
  })

  async function guardTeacherRequest<T>(request: () => Promise<T>): Promise<T> {
    try {
      return await request()
    } catch (reason) {
      if (isForbiddenApiError(reason)) {
        serverLocked.value = true
      }
      throw reason
    }
  }

  function markApprovalLocked(): void {
    serverLocked.value = true
  }

  return {
    teacherApprovalLocked,
    teacherApprovalTitle,
    teacherApprovalMessage,
    guardTeacherRequest,
    markApprovalLocked,
  }
}
