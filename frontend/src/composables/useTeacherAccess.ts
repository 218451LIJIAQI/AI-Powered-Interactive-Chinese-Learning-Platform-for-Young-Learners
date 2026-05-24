import { computed } from 'vue'

import { useAuthStore } from '@/stores/auth'

/**
 * Composable that exposes "is this teacher gated by their verification
 * status?" plus copy-paste-ready banner title / message.
 *
 * <p>Used by every teacher dashboard surface to show the same banner
 * for PENDING / REJECTED accounts. Centralising the copy here means
 * a wording change ripples through the app without per-page edits,
 * and keeps the banner consistent between content authoring,
 * earnings, classes, and feedback pages.</p>
 */
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
