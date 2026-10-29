<template>
  <div class="page-stack teacher-locked">
    <PageHeaderBlock :title="t('verification.lockedTitle')" :description="t('verification.lockedDescription')">
      <template #meta>
        <StatusPill :label="t('verification.locked')" tone="neutral" icon="lock" />
      </template>
    </PageHeaderBlock>

    <section class="panel teacher-locked-card" aria-labelledby="teacher-locked-title">
      <div class="teacher-locked-head">
        <span class="seal is-muted teacher-locked-seal" aria-hidden="true"><IconGlyph name="lock" :size="24" /></span>
        <div class="teacher-locked-copy">
          <h2 id="teacher-locked-title" class="section-title">{{ t('verification.lockedCardTitle') }}</h2>
          <p class="type-caption">
            {{ state === 'IN_REVIEW' ? t('verification.lockedCopyInReview') : t('verification.lockedCopyEditable') }}
          </p>
        </div>
      </div>

      <TeacherApplicationPath v-if="profile" :profile="profile" />

      <div class="teacher-locked-status" data-testid="teacher-locked-status">
        <div class="teacher-locked-status-head">
          <strong>{{ t('verification.teacherVerification') }}</strong>
          <StatusPill v-if="profile" :label="t(verificationStateKey(state))" :tone="verificationTone(state)" />
        </div>
        <p v-if="resource.loading.value" class="type-caption">{{ t('verification.loadingHint') }}</p>
        <p v-else-if="!profile" class="type-caption">{{ resource.error.value }}</p>
        <p v-else class="type-caption">{{ statusLine }}</p>
      </div>

      <div class="teacher-locked-actions">
        <RouterLink class="btn btn-primary" :to="{ name: 'teacher-profile' }">
          <IconGlyph name="verified" :size="16" />
          {{ t('verification.viewStatus') }}
        </RouterLink>
        <RouterLink class="btn btn-ghost" :to="{ name: 'teacher-dashboard' }">
          <IconGlyph name="dashboard" :size="16" />
          {{ t('verification.backToDashboard') }}
        </RouterLink>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { teacherVerificationApi } from '@/api/ops'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import TeacherApplicationPath from '@/components/teacher/TeacherApplicationPath.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { opsLabelMessages } from '@/plugins/opsLabelMessages'
import { teacherVerificationMessages } from '@/plugins/teacherVerificationMessages'
import { useAuthStore } from '@/stores/auth'
import type { TeacherVerificationState } from '@/types/api'
import { formatDate } from '@/utils/format'
import { verificationStateKey, verificationTone } from '@/utils/opsLabels'
import '@/styles/ops-account.css'

/** Where a teacher who is not approved yet lands when opening Classes, Live sessions or Students. */
const { t } = useLocalMessages({ verification: teacherVerificationMessages, opsLabels: opsLabelMessages })
const auth = useAuthStore()
const router = useRouter()

const resource = useResource(() => teacherVerificationApi.profile())
const profile = computed(() => resource.data.value)
const state = computed<TeacherVerificationState>(() => profile.value?.verificationState ?? 'NOT_SUBMITTED')

const statusLine = computed(() => {
  switch (state.value) {
    case 'IN_REVIEW':
      return t('verification.lockedInReview', { date: formatDate(profile.value?.submittedAt) })
    case 'CHANGES_REQUESTED': {
      const reason = profile.value?.latestDecision?.reason
      return reason ? t('verification.lockedChanges', { reason }) : t('verification.lockedChangesNoReason')
    }
    default:
      return t('verification.lockedNotSubmitted')
  }
})

// An approved teacher has nothing locked: go back to the dashboard.
function leaveIfApproved(): void {
  if (auth.isApprovedTeacher || state.value === 'APPROVED') void router.replace({ name: 'teacher-dashboard' })
}
onMounted(leaveIfApproved)
watch(state, leaveIfApproved)
</script>
