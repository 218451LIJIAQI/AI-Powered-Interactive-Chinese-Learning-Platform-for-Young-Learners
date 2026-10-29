<template>
  <div class="page-stack support-page">
    <PageHeaderBlock :title="t('support.pageTitle')" :description="t('support.replyTime')">
      <template #actions>
        <button class="btn btn-primary" type="button" data-testid="support-new" @click="openDialog">
          <IconGlyph name="plus" :size="16" />
          {{ t('support.newRequest') }}
        </button>
      </template>
    </PageHeaderBlock>

    <DataNotice :success="sentNotice" :success-title="t('support.sentTitle')" />

    <div class="support-layout">
      <section class="support-requests" aria-labelledby="support-requests-title">
        <div class="support-section-head">
          <h2 id="support-requests-title" class="support-section-title">{{ t('support.yourRequests') }}</h2>
          <span v-if="requests.length" class="type-caption">
            {{ t('support.requestCount', { count: requests.length }, requests.length) }}
          </span>
        </div>

        <StatePanel
          v-if="resource.loading.value"
          tone="loading"
          :title="t('support.loadingTitle')"
          :message="t('support.loadingHint')"
        />
        <StatePanel
          v-else-if="resource.error.value"
          tone="error"
          icon="alert"
          :title="t('support.loadErrorTitle')"
          :message="resource.error.value"
        >
          <template #actions>
            <button class="btn btn-primary" type="button" @click="resource.reload">{{ t('support.retry') }}</button>
          </template>
        </StatePanel>
        <ul v-else-if="requests.length" class="support-request-list" data-testid="support-requests">
          <li v-for="request in requests" :key="request.id">
            <RouterLink
              class="support-request-row"
              :class="{ 'is-solved': request.status === 'SOLVED' }"
              :to="{ name: ticketRouteName, params: { ticketId: request.id } }"
              :aria-label="t('support.openRequest', { number: request.number, subject: request.subject })"
            >
              <span class="support-request-copy">
                <strong class="support-request-subject">
                  <span class="support-request-number">#{{ request.number }}</span> · {{ request.subject }}
                </strong>
                <span class="type-caption">{{ requestMeta(request) }}</span>
              </span>
              <StatusPill :label="t(requestStatusKey(request.status))" :tone="supportStatusTone(request.status)" />
              <IconGlyph name="chevronRight" :size="18" class="support-request-chevron" />
            </RouterLink>
          </li>
        </ul>
        <StatePanel
          v-else
          tone="empty"
          icon="support"
          :title="t('support.emptyTitle')"
          :message="t('support.emptyHint')"
        >
          <template #actions>
            <button class="btn btn-secondary" type="button" @click="openDialog">{{ t('support.newRequest') }}</button>
          </template>
        </StatePanel>

        <p class="support-footnote">{{ t('support.footnote') }}</p>
      </section>

      <!-- Quick answers: questions first, an answer opens under its question. -->
      <aside class="support-rail" :aria-label="t('support.quickAnswers')">
        <h2 class="support-section-title">{{ t('support.quickAnswers') }}</h2>
        <div class="support-answer-list">
          <details v-for="answer in quickAnswers" :key="answer.question" class="support-answer">
            <summary>
              {{ t(answer.question) }}
              <IconGlyph name="chevronDown" :size="16" class="support-answer-chevron" />
            </summary>
            <p>{{ t(answer.answer) }}</p>
          </details>
        </div>
        <RouterLink class="support-rail-link" to="/safety">
          {{ t('support.safetyLink') }} <IconGlyph name="chevronRight" :size="14" />
        </RouterLink>
        <RouterLink class="support-rail-link" to="/responsible-ai">
          {{ t('support.aiLink') }} <IconGlyph name="chevronRight" :size="14" />
        </RouterLink>
      </aside>
    </div>

    <AppOverlay
      :open="dialogOpen"
      :title="t('support.dialogTitle')"
      :subtitle="t('support.dialogSubtitle')"
      test-id="support-dialog"
      @close="closeDialog"
    >
      <form id="support-new-form" class="form-grid" novalidate @submit.prevent="send">
        <div class="field">
          <label for="support-topic">{{ t('support.topic') }}</label>
          <select id="support-topic" v-model="form.topic" class="select" aria-describedby="support-topic-hint">
            <option v-for="topic in SUPPORT_TOPICS" :key="topic" :value="topic">{{ t(supportTopicKey(topic)) }}</option>
          </select>
          <p id="support-topic-hint" class="field-hint">{{ t('support.topicHint') }}</p>
        </div>

        <div class="field">
          <label for="support-subject">
            {{ t('support.subject') }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <input
            id="support-subject"
            v-model="form.subject"
            class="input"
            maxlength="150"
            aria-required="true"
            :placeholder="t('support.subjectPlaceholder')"
            :aria-invalid="Boolean(errors.subject)"
            :aria-describedby="errors.subject ? 'support-subject-error' : undefined"
          />
          <p v-if="errors.subject" id="support-subject-error" class="field-error">{{ errors.subject }}</p>
        </div>

        <div class="field">
          <label for="support-message">
            {{ t('support.message') }}<span class="required-mark" aria-hidden="true">*</span>
          </label>
          <textarea
            id="support-message"
            v-model="form.message"
            class="textarea"
            rows="4"
            maxlength="4000"
            aria-required="true"
            :placeholder="t('support.messagePlaceholder')"
            :aria-invalid="Boolean(errors.message)"
            :aria-describedby="errors.message ? 'support-message-error' : undefined"
          />
          <p v-if="errors.message" id="support-message-error" class="field-error">{{ errors.message }}</p>
        </div>

        <div class="field">
          <label for="support-screenshot">{{ t('support.screenshot') }}</label>
          <div class="support-file-row">
            <input
              id="support-screenshot"
              ref="fileInput"
              class="input"
              type="file"
              accept="image/png,image/jpeg"
              :aria-invalid="Boolean(errors.screenshot)"
              :aria-describedby="errors.screenshot ? 'support-screenshot-error' : 'support-screenshot-hint'"
              @change="chooseScreenshot"
            />
            <button
              v-if="form.screenshot"
              class="btn btn-ghost btn-icon btn-sm"
              type="button"
              :aria-label="t('support.removeScreenshot')"
              @click="clearScreenshot"
            >
              <IconGlyph name="close" :size="16" />
            </button>
          </div>
          <p id="support-screenshot-hint" class="field-hint">{{ t('support.screenshotHint') }}</p>
          <p v-if="errors.screenshot" id="support-screenshot-error" class="field-error">{{ errors.screenshot }}</p>
        </div>

        <p v-if="sendError" class="alert alert-danger" role="alert">
          <IconGlyph name="alert" :size="18" />
          <span>{{ sendError }}</span>
        </p>
      </form>

      <template #footer>
        <p class="support-privacy-note">{{ t('support.privacyNote') }}</p>
        <button class="btn btn-ghost" type="button" @click="closeDialog">{{ t('support.cancel') }}</button>
        <button
          class="btn btn-primary"
          type="submit"
          form="support-new-form"
          :disabled="sending"
          :aria-busy="sending"
          data-testid="support-send"
        >
          {{ sending ? t('support.sending') : t('support.send') }}
        </button>
      </template>
    </AppOverlay>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { supportApi } from '@/api/ops'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import AppOverlay from '@/components/common/AppOverlay.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { opsLabelMessages } from '@/plugins/opsLabelMessages'
import { supportMessages } from '@/plugins/supportMessages'
import { useAuthStore } from '@/stores/auth'
import type { SupportRequestSummary, SupportTopic } from '@/types/ops'
import { resolveApiError } from '@/utils/apiError'
import { formatShortDate } from '@/utils/format'
import { requestStatusKey, SUPPORT_TOPICS, supportStatusTone, supportTopicKey } from '@/utils/opsLabels'
import { screenshotIssue } from '@/utils/uploadChecks'
import '@/styles/ops-account.css'

const { t } = useLocalMessages({ support: supportMessages, opsLabels: opsLabelMessages })
const auth = useAuthStore()
const route = useRoute()

const isTeacher = computed(() => auth.activeRole === 'TEACHER')
const isApplicant = computed(() => isTeacher.value && !auth.isApprovedTeacher)
const ticketRouteName = computed(() => (isTeacher.value ? 'teacher-support-ticket' : 'student-support-ticket'))

const STUDENT_ANSWERS = [
  { question: 'support.studentQ1', answer: 'support.studentA1' },
  { question: 'support.studentQ2', answer: 'support.studentA2' },
  { question: 'support.studentQ3', answer: 'support.studentA3' },
  { question: 'support.studentQ4', answer: 'support.studentA4' },
] as const
const TEACHER_ANSWERS = [
  { question: 'support.teacherQ1', answer: 'support.teacherA1' },
  { question: 'support.teacherQ2', answer: 'support.teacherA2' },
  { question: 'support.teacherQ3', answer: 'support.teacherA3' },
  { question: 'support.teacherQ4', answer: 'support.teacherA4' },
] as const
const APPLICANT_ANSWERS = [
  { question: 'support.applicantQ1', answer: 'support.applicantA1' },
  { question: 'support.applicantQ2', answer: 'support.applicantA2' },
  { question: 'support.applicantQ3', answer: 'support.applicantA3' },
] as const

const quickAnswers = computed(() =>
  isApplicant.value ? APPLICANT_ANSWERS : isTeacher.value ? TEACHER_ANSWERS : STUDENT_ANSWERS,
)

const resource = useResource(() => supportApi.list())
const requests = computed<SupportRequestSummary[]>(() => resource.data.value ?? [])

function requestMeta(request: SupportRequestSummary): string {
  const parts = [t(supportTopicKey(request.topic)), t('support.sentOn', { date: formatShortDate(request.createdAt) })]
  if (request.status === 'SOLVED' && request.solvedAt) {
    parts.push(t('support.solvedOn', { date: formatShortDate(request.solvedAt) }))
  } else if (request.lastSupportReplyAt) {
    parts.push(t('support.repliedOn', { date: formatShortDate(request.lastSupportReplyAt) }))
  }
  return parts.join(' · ')
}

const dialogOpen = ref(false)
const sending = ref(false)
const sendError = ref<string | null>(null)
const sentNotice = ref<string | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const form = reactive({
  topic: 'ACCOUNT' as SupportTopic,
  subject: '',
  message: '',
  screenshot: null as File | null,
})
const errors = reactive<{ subject: string | null; message: string | null; screenshot: string | null }>({
  subject: null,
  message: null,
  screenshot: null,
})

function openDialog(): void {
  form.topic = isApplicant.value ? 'VERIFICATION' : route.query.topic === 'SAFETY' ? 'SAFETY' : 'ACCOUNT'
  form.subject = ''
  form.message = ''
  clearScreenshot()
  errors.subject = null
  errors.message = null
  sendError.value = null
  dialogOpen.value = true
}

function closeDialog(): void {
  if (!sending.value) dialogOpen.value = false
}

function chooseScreenshot(event: Event): void {
  const file = (event.target as HTMLInputElement).files?.[0] ?? null
  const issue = file ? screenshotIssue(file) : null
  errors.screenshot =
    issue === 'type' ? t('support.screenshotType') : issue === 'size' ? t('support.screenshotSize') : null
  form.screenshot = issue ? null : file
  if (issue && fileInput.value) fileInput.value.value = ''
}

function clearScreenshot(): void {
  form.screenshot = null
  errors.screenshot = null
  if (fileInput.value) fileInput.value.value = ''
}

async function send(): Promise<void> {
  if (sending.value) return
  errors.subject = form.subject.trim() ? null : t('support.subjectRequired')
  errors.message = form.message.trim() ? null : t('support.messageRequired')
  if (errors.subject || errors.message || errors.screenshot) return

  sending.value = true
  sendError.value = null
  try {
    const created = await supportApi.create({
      topic: form.topic,
      subject: form.subject.trim(),
      message: form.message.trim(),
      screenshot: form.screenshot,
    })
    dialogOpen.value = false
    sentNotice.value = t('support.sent', { number: created.request.number })
    await resource.reload()
  } catch (reason) {
    sendError.value = resolveApiError(reason, t('support.sendError'))
  } finally {
    sending.value = false
  }
}
</script>
