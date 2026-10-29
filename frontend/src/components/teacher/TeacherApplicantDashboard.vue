<template>
  <div class="page-stack applicant-dashboard">
    <PageHeaderBlock
      :eyebrow="t('verification.applicationEyebrow')"
      :title="t('verification.dashboardTitle')"
      :description="profile ? t('verification.applicantLine', { name: profile.fullName }) : ''"
    />

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
      <section class="panel applicant-sheet" aria-labelledby="applicant-status-title">
        <div class="applicant-sheet-head">
          <h2 id="applicant-status-title" class="section-title">
            {{ t('verification.verificationTitle', { state: t(verificationStateKey(state)) }) }}
          </h2>
          <StatusPill :label="t(verificationStateKey(state))" :tone="verificationTone(state)" />
        </div>

        <TeacherApplicationPath :profile="profile" />

        <div class="applicant-state">
          <template v-if="state === 'NOT_SUBMITTED'">
            <p class="applicant-state-text">{{ t('verification.notSubmittedCopy') }}</p>
            <div class="applicant-actions">
              <RouterLink class="btn btn-primary" :to="{ name: 'teacher-profile' }" data-testid="applicant-upload">
                <IconGlyph name="upload" :size="16" />
                {{ t('verification.uploadDocuments') }}
              </RouterLink>
              <span class="type-caption">{{ t('verification.fileRule') }}</span>
            </div>
          </template>

          <template v-else-if="state === 'IN_REVIEW'">
            <!-- The date is already in the application steps beside this. -->
            <p class="applicant-state-text">{{ t('verification.inReviewCopy') }}</p>
            <h3 class="applicant-subtitle">{{ t('verification.whatHappensNext') }}</h3>
            <ol class="applicant-next">
              <li>{{ t('verification.nextStep1') }}</li>
              <li>{{ t('verification.nextStep2') }}</li>
              <li>{{ t('verification.nextStep3') }}</li>
            </ol>
            <div class="applicant-actions">
              <RouterLink class="btn btn-secondary" :to="{ name: 'teacher-profile' }">
                <IconGlyph name="verified" :size="16" />
                {{ t('verification.viewStatus') }}
              </RouterLink>
            </div>
          </template>

          <template v-else-if="state === 'CHANGES_REQUESTED'">
            <blockquote class="applicant-reason" data-testid="applicant-reason">
              <p class="applicant-reason-from">
                {{
                  decision?.reviewerName
                    ? t('verification.reasonFrom', { name: decision.reviewerName })
                    : t('verification.reasonFromAdmin')
                }}
              </p>
              <p>{{ decision?.reason }}</p>
              <p v-if="decision" class="type-caption">{{ formatDateTime(decision.decidedAt) }}</p>
            </blockquote>
            <div class="applicant-fix">
              <div>
                <strong>{{ t('verification.fixTitle') }}</strong>
                <p class="type-caption">{{ t('verification.fixCopy') }}</p>
              </div>
              <RouterLink class="btn btn-primary" :to="{ name: 'teacher-profile' }">
                {{ t('verification.fixButton') }}
              </RouterLink>
            </div>
          </template>
        </div>
      </section>

      <section class="applicant-tools" aria-labelledby="applicant-tools-title">
        <div>
          <h2 id="applicant-tools-title" class="section-title">{{ t('verification.opensAfterApproval') }}</h2>
          <p class="section-description">{{ t('verification.opensAfterApprovalHint') }}</p>
        </div>
        <ul class="applicant-tool-list">
          <li v-for="tool in TOOLS" :key="tool.title">
            <RouterLink class="applicant-tool" :to="{ name: 'teacher-locked' }">
              <span class="sr-only">{{ t('verification.locked') }}</span>
              <span class="seal is-muted applicant-tool-seal" aria-hidden="true">
                <IconGlyph :name="tool.icon" :size="18" />
              </span>
              <span class="applicant-tool-copy">
                <strong>{{ t(tool.title) }}</strong>
                <span class="type-caption">{{ t(tool.hint) }}</span>
              </span>
              <IconGlyph name="lock" :size="16" class="applicant-tool-lock" />
            </RouterLink>
          </li>
        </ul>
      </section>
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
import { formatDateTime } from '@/utils/format'
import { verificationStateKey, verificationTone } from '@/utils/opsLabels'
import '@/styles/ops-account.css'

import TeacherApplicationPath from './TeacherApplicationPath.vue'

/** Where a teacher who is not approved yet stands: the application as a path, what to do now, the tools that open. */
const { t } = useLocalMessages({ verification: teacherVerificationMessages, opsLabels: opsLabelMessages })

const TOOLS = [
  { icon: 'classes', title: 'verification.toolClasses', hint: 'verification.toolClassesHint' },
  { icon: 'live', title: 'verification.toolLiveSessions', hint: 'verification.toolLiveSessionsHint' },
  { icon: 'students', title: 'verification.toolStudents', hint: 'verification.toolStudentsHint' },
] as const

const resource = useResource(() => teacherVerificationApi.profile())
const profile = computed(() => resource.data.value)
const state = computed<TeacherVerificationState>(() => profile.value?.verificationState ?? 'NOT_SUBMITTED')
const decision = computed(() => profile.value?.latestDecision ?? null)
</script>
