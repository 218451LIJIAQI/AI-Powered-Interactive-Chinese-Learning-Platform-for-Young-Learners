<template>
  <section class="applicant-path" :aria-labelledby="headingId">
    <h3 :id="headingId" class="applicant-path-title">{{ t('verification.yourApplication') }}</h3>
    <ol class="applicant-step-list">
      <li v-for="(step, index) in steps" :key="step.title" class="applicant-step" :class="`is-${step.status}`">
        <span
          class="seal applicant-step-seal"
          :class="{ 'is-outline': step.status === 'current', 'is-muted': step.status === 'upcoming' }"
          lang="zh-CN"
          aria-hidden="true"
          >{{ step.status === 'done' ? '完' : hanziNumeral(index + 1) }}</span
        >
        <span class="applicant-step-copy">
          <strong>{{ index + 1 }}. {{ step.title }}</strong>
          <span class="type-caption">
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

import { hanziNumeral } from '@/components/learn/sealGlyphs'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { teacherVerificationMessages } from '@/plugins/teacherVerificationMessages'
import type { TeacherProfileResponse } from '@/types/ops'
import { formatDate, formatDateTime } from '@/utils/format'
import { hasRequiredDocuments, latestUpload } from '@/utils/teacherApplication'

/**
 * A teacher application as a path of three steps (account, documents, admin review): finished steps are stamped
 * with a seal, the current one is an outline seal, the next one a muted seal. Shown on the applicant's dashboard and
 * on the Locked page, so the applicant always sees what unlocks the teaching tools.
 */
const props = defineProps<{ profile: TeacherProfileResponse }>()

const { t } = useLocalMessages({ verification: teacherVerificationMessages })
const headingId = useId()

type StepStatus = 'done' | 'current' | 'upcoming'

const steps = computed<{ title: string; detail: string; status: StepStatus }[]>(() => {
  const profile = props.profile
  const state = profile.verificationState
  const documents = profile.documents
  const uploaded = latestUpload(documents)
  const submitted = state !== 'NOT_SUBMITTED'
  const documentsDone = submitted || hasRequiredDocuments(documents)
  const reviewDetail =
    state === 'IN_REVIEW'
      ? t('verification.stepReviewInReview', { date: formatDate(profile.submittedAt) })
      : state === 'CHANGES_REQUESTED'
        ? t('verification.stepReviewChanges', { date: formatDateTime(profile.latestDecision?.decidedAt) })
        : state === 'APPROVED'
          ? t('verification.stepReviewApproved', { date: formatDate(profile.approvedAt) })
          : t('verification.stepReviewWaiting')
  return [
    { title: t('verification.stepAccount'), detail: t('verification.stepDone'), status: 'done' },
    {
      title: t('verification.stepDocuments'),
      detail: uploaded
        ? t('verification.stepDocumentsDone', { count: documents.length, date: formatDate(uploaded) }, documents.length)
        : t('verification.stepDocumentsTodo'),
      status: documentsDone ? 'done' : 'current',
    },
    {
      title: t('verification.stepReview'),
      detail: reviewDetail,
      status: state === 'APPROVED' ? 'done' : submitted ? 'current' : 'upcoming',
    },
  ]
})
</script>
