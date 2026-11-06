<template>
  <section class="panel teacher-card" :aria-labelledby="headingId">
    <h2 :id="headingId" class="card-title">{{ t('verification.yourApplication') }}</h2>
    <ol class="row-list applicant-steps">
      <li v-for="(step, index) in steps" :key="step.title">
        <span class="teacher-tile" :class="{ 'is-filled': step.status === 'done' }" aria-hidden="true">
          <IconGlyph :name="step.icon" :size="16" />
        </span>
        <span class="row-list-copy">
          <strong class="row-list-title">{{ index + 1 }}. {{ step.title }}</strong>
          <span class="row-list-meta">
            <span v-if="step.status === 'current'" class="sr-only">{{ t('verification.stepCurrent') }}: </span>
            {{ step.detail }}
          </span>
        </span>
      </li>
    </ol>
  </section>
</template>

<script setup lang="ts">
import { computed, useId } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { teacherVerificationMessages } from '@/plugins/teacherVerificationMessages'
import type { TeacherProfileResponse } from '@/types/ops'
import { formatDateTime, formatShortDate } from '@/utils/format'
import { hasRequiredDocuments, latestUpload } from '@/utils/teacherApplication'

/**
 * A teacher application as three steps (account, documents, admin review): a finished step has a filled tile with a
 * check, the others a sunken tile with what the step is waiting on.
 */
const props = defineProps<{ profile: TeacherProfileResponse }>()

const { t } = useLocalMessages({ verification: teacherVerificationMessages })
const headingId = useId()

type StepStatus = 'done' | 'current' | 'upcoming'

const steps = computed<{ title: string; detail: string; status: StepStatus; icon: string }[]>(() => {
  const profile = props.profile
  const state = profile.verificationState
  const documents = profile.documents
  const uploaded = latestUpload(documents)
  const submitted = state !== 'NOT_SUBMITTED'
  const documentsDone = submitted || hasRequiredDocuments(documents)
  const reviewDetail =
    state === 'IN_REVIEW'
      ? t('verification.stepReviewInReview', { date: formatShortDate(profile.submittedAt) })
      : state === 'CHANGES_REQUESTED'
        ? t('verification.stepReviewChanges', { date: formatDateTime(profile.latestDecision?.decidedAt) })
        : state === 'APPROVED'
          ? t('verification.stepReviewApproved', { date: formatShortDate(profile.approvedAt) })
          : t('verification.stepReviewWaiting')
  return [
    { title: t('verification.stepAccount'), detail: t('verification.stepDone'), status: 'done', icon: 'check' },
    {
      title: t('verification.stepDocuments'),
      detail: uploaded
        ? t(
            'verification.stepDocumentsDone',
            { count: documents.length, date: formatShortDate(uploaded) },
            documents.length,
          )
        : t('verification.stepDocumentsTodo'),
      status: documentsDone ? 'done' : 'current',
      icon: documentsDone ? 'check' : 'upload',
    },
    {
      title: t('verification.stepReview'),
      detail: reviewDetail,
      status: state === 'APPROVED' ? 'done' : submitted ? 'current' : 'upcoming',
      icon: state === 'APPROVED' ? 'check' : state === 'CHANGES_REQUESTED' ? 'alert' : 'history',
    },
  ]
})
</script>
