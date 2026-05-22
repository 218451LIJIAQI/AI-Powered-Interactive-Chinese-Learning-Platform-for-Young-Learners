import { computed } from 'vue'

import { useAuthStore } from '@/stores/auth'

export function useTeacherAccess() {
  const auth = useAuthStore()

  const teacherStatus = computed(() => auth.currentUser?.teacherProfile?.teacherStatus ?? 'PENDING')
  const teacherApprovalLocked = computed(
    () => auth.activeRole === 'TEACHER' && teacherStatus.value !== 'APPROVED',
  )
  const teacherApprovalTitle = computed(() =>
    teacherStatus.value === 'REJECTED' ? 'Verification needs attention' : 'Verification pending',
  )
  const teacherApprovalMessage = computed(() =>
    teacherStatus.value === 'REJECTED'
      ? 'Open your profile to review the verification feedback, then resubmit your documents to unlock teaching tools.'
      : 'Complete verification and wait for admin approval to unlock live classes, content publishing, and learner feedback tools.',
  )

  return {
    teacherApprovalLocked,
    teacherApprovalTitle,
    teacherApprovalMessage,
  }
}
