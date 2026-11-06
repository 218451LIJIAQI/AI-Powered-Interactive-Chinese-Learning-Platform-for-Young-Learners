<template>
  <div class="page-stack applicant-dashboard">
    <PageHeaderBlock :title="t('verification.dashboardTitle')" hide-title />

    <StatePanel
      v-if="resource.loading.value"
      tone="loading"
      spacious
      :title="t('verification.loadingTitle')"
      :message="t('verification.loadingHint')"
    />
    <StatePanel
      v-else-if="resource.error.value || !profile"
      tone="error"
      icon="alert"
      spacious
      :title="t('verification.loadErrorTitle')"
      :message="resource.error.value ?? ''"
    >
      <template #actions>
        <button class="btn btn-primary" type="button" @click="resource.reload">{{ t('verification.retry') }}</button>
      </template>
    </StatePanel>

    <div v-else class="applicant-layout">
      <section class="panel teacher-card" aria-labelledby="applicant-status-title">
        <div class="applicant-status-head">
          <h2
            id="applicant-status-title"
            class="section-title"
            :aria-label="t('verification.verificationTitle', { state: t(verificationStateKey(state)) })"
          >
            {{ t('verification.verification') }}
          </h2>
          <!-- The heading's accessible name already says the state. -->
          <StatusPill :label="t(verificationStateKey(state))" :tone="applicationTone(state)" aria-hidden="true" />
        </div>

        <template v-if="state === 'NOT_SUBMITTED'">
          <p class="type-caption">{{ t('verification.notSubmittedCopy') }}</p>
          <div class="applicant-action">
            <RouterLink class="btn btn-primary" :to="{ name: 'teacher-profile' }" data-testid="applicant-upload">
              <IconGlyph name="upload" :size="16" />{{ t('verification.uploadDocuments') }}
            </RouterLink>
            <span class="type-caption">{{ t('verification.fileRule') }}</span>
          </div>
        </template>

        <template v-else-if="state === 'IN_REVIEW'">
          <p class="type-caption">
            {{ t('verification.inReviewCopy', { date: formatShortDate(profile.submittedAt) }) }}
          </p>
          <div class="applicant-action">
            <RouterLink class="btn btn-secondary" :to="{ name: 'teacher-profile' }">
              <IconGlyph name="verified" :size="16" />{{ t('verification.viewStatus') }}
            </RouterLink>
          </div>
        </template>

        <template v-else-if="state === 'CHANGES_REQUESTED'">
          <div class="callout" data-testid="applicant-reason">
            <IconGlyph name="alert" :size="18" />
            <div class="teacher-callout-copy">
              <strong>
                {{
                  decision?.reviewerName
                    ? t('verification.reasonFrom', { name: decision.reviewerName })
                    : t('verification.reasonFromAdmin')
                }}
              </strong>
              <p>{{ decision?.reason }}</p>
              <span v-if="decision" class="type-caption">{{ formatDateTime(decision.decidedAt) }}</span>
            </div>
          </div>
          <div class="applicant-action">
            <RouterLink class="btn btn-primary" :to="{ name: 'teacher-profile' }">
              <IconGlyph name="upload" :size="16" />{{ t('verification.fixButton') }}
            </RouterLink>
            <span class="type-caption">{{ t('verification.fixCopy') }}</span>
          </div>
        </template>
      </section>

      <TeacherApplicationPath :profile="profile" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import { teacherVerificationApi } from '@/api/ops'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { opsLabelMessages } from '@/plugins/opsLabelMessages'
import { teacherVerificationMessages } from '@/plugins/teacherVerificationMessages'
import type { TeacherVerificationState } from '@/types/api'
import { formatDateTime, formatShortDate } from '@/utils/format'
import { verificationStateKey } from '@/utils/opsLabels'
import { applicationTone } from '@/utils/teacherApplication'
import '@/styles/classes-teacher.css'

import TeacherApplicationPath from './TeacherApplicationPath.vue'

/** Where a teacher who is not approved yet stands: what to do now, and the application's three steps beside it. */
const { t } = useLocalMessages({ verification: teacherVerificationMessages, opsLabels: opsLabelMessages })

const resource = useResource(() => teacherVerificationApi.profile())
const profile = computed(() => resource.data.value)
const state = computed<TeacherVerificationState>(() => profile.value?.verificationState ?? 'NOT_SUBMITTED')
const decision = computed(() => profile.value?.latestDecision ?? null)
</script>
