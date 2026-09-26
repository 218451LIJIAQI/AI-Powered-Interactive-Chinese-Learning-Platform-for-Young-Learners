import { computed } from 'vue'
import type { ComputedRef } from 'vue'
import { useI18n } from 'vue-i18n'

import { useAuthStore } from '@/stores/auth'

interface TeacherAccessState {
  readonly teacherApprovalLocked: ComputedRef<boolean>
  readonly teacherApprovalTitle: ComputedRef<string>
  readonly teacherApprovalMessage: ComputedRef<string>
}

export function useTeacherAccess(): TeacherAccessState {
  const auth = useAuthStore()
  const { t } = useI18n({ useScope: 'global' })

  const teacherStatus = computed(() => auth.currentUser?.teacherProfile?.teacherStatus ?? 'PENDING')
  const teacherApprovalLocked = computed(() => auth.activeRole === 'TEACHER' && teacherStatus.value !== 'APPROVED')
  const teacherApprovalTitle = computed(() =>
    t(teacherStatus.value === 'REJECTED' ? 'teacherAccess.rejectedTitle' : 'teacherAccess.pendingTitle'),
  )
  const teacherApprovalMessage = computed(() =>
    t(teacherStatus.value === 'REJECTED' ? 'teacherAccess.rejectedMessage' : 'teacherAccess.pendingMessage'),
  )

  return {
    teacherApprovalLocked,
    teacherApprovalTitle,
    teacherApprovalMessage,
  }
}
