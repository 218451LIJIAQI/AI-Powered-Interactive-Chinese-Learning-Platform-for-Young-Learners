import { computed } from 'vue'
import type { ComputedRef } from 'vue'

import { useAuthStore } from '@/stores/auth'

interface TeacherAccessState {
  readonly teacherApprovalLocked: ComputedRef<boolean>
  readonly teacherApprovalTitle: ComputedRef<string>
  readonly teacherApprovalMessage: ComputedRef<string>
}

const TEACHER_APPROVAL_COPY = {
  REJECTED: {
    title: 'Verification needs attention',
    message:
      'Open your profile to review the verification feedback, then resubmit your documents to unlock teaching tools.',
  },
  PENDING: {
    title: 'Verification pending',
    message:
      'Complete verification and wait for admin approval to unlock live classes, content publishing, and learner feedback tools.',
  },
} as const

export function useTeacherAccess(): TeacherAccessState {
  const auth = useAuthStore()

  const teacherStatus = computed(() => auth.currentUser?.teacherProfile?.teacherStatus ?? 'PENDING')
  const teacherApprovalLocked = computed(() => auth.activeRole === 'TEACHER' && teacherStatus.value !== 'APPROVED')
  const approvalCopy = computed(() =>
    teacherStatus.value === 'REJECTED' ? TEACHER_APPROVAL_COPY.REJECTED : TEACHER_APPROVAL_COPY.PENDING,
  )
  const teacherApprovalTitle = computed(() => approvalCopy.value.title)
  const teacherApprovalMessage = computed(() => approvalCopy.value.message)

  return {
    teacherApprovalLocked,
    teacherApprovalTitle,
    teacherApprovalMessage,
  }
}
