<template>
  <div class="page-stack admin-moderation-page">
    <PageHeaderBlock
      :title="t('adminModeration.title')"
      :description="t('adminModeration.description')"
      icon="moderation"
    />

    <DataNotice :using-fallback="moderationResource.usingFallback.value" :error="pageError" :success="actionSuccess" />

    <section class="admin-ops-hero admin-ops-hero--moderation">
      <div>
        <span class="admin-ops-kicker">{{ t('adminModeration.safetyReview') }}</span>
        <h2>{{ t('adminModeration.heroTitle') }}</h2>
        <p>{{ t('adminModeration.heroHint') }}</p>
      </div>
      <div class="admin-ops-stat-grid">
        <article v-for="stat in moderationStats" :key="stat.label">
          <span><IconGlyph :name="stat.icon" :size="20" /></span>
          <strong>{{ stat.value }}</strong>
          <small>{{ stat.label }}</small>
        </article>
      </div>
    </section>

    <section class="two-col admin-moderation-layout">
      <InfoCard :title="t('adminModeration.records')" :subtitle="t('adminModeration.recordsHint')" icon="moderation">
        <div class="list">
          <ListInfoItem
            v-for="item in moderationRecords"
            :key="item.id"
            :title="item.targetTitle"
            :description="item.note || item.actionTaken"
            icon="moderation"
          >
            <template #meta>
              <span class="tag">{{ item.targetType }}</span>
              <span class="tag">{{ formatStatus(item.actionTaken) }}</span>
            </template>
            <template #aside>
              <span class="muted">{{ formatDateTime(item.createdAt) }}</span>
            </template>
          </ListInfoItem>
        </div>
      </InfoCard>

      <ActionFormCard
        :title="t('adminModeration.reports')"
        :subtitle="t('adminModeration.reportsHint')"
        icon="ai"
        :submit-label="t('adminModeration.saveReview')"
        :submitting-label="t('adminModeration.saving')"
        :submitting="submittingReview"
        :disabled="reviewDisabled"
        @submit="reviewAiReport"
      >
        <template #header-end>
          <StatusPill
            v-if="selectedReport"
            :label="formatStatus(selectedReport.status)"
            :tone="reportStatusTone(selectedReport.status)"
          />
        </template>

        <div class="field">
          <label for="admin-moderation-report">{{ t('adminModeration.report') }}</label>
          <select
            id="admin-moderation-report"
            v-model.number="selectedReportId"
            class="select"
            :disabled="!aiReports.length"
          >
            <option v-for="item in aiReports" :key="item.id" :value="item.id">
              {{ formatFeatureKey(item.featureKey) }} - {{ formatReportReason(item.reasonCategory) }}
            </option>
          </select>
        </div>

        <div v-if="selectedReport" class="admin-report-detail">
          <div class="admin-report-summary-grid">
            <div>
              <span>{{ t('adminModeration.student') }}</span>
              <strong>{{ selectedReport.studentName || t('adminModeration.unknownLearner') }}</strong>
              <small>{{ selectedReport.studentEmail || t('adminModeration.noEmail') }}</small>
            </div>
            <div>
              <span>{{ t('adminModeration.feature') }}</span>
              <strong>{{ formatFeatureKey(selectedReport.featureKey) }}</strong>
              <small v-if="selectedReport.aiUsageLogId">{{
                t('adminModeration.usageLog', { id: selectedReport.aiUsageLogId })
              }}</small>
            </div>
            <div>
              <span>{{ t('adminModeration.reason') }}</span>
              <strong>{{ formatReportReason(selectedReport.reasonCategory) }}</strong>
              <small>{{ t('adminModeration.reported', { date: formatDateTime(selectedReport.createdAt) }) }}</small>
            </div>
            <div>
              <span>{{ t('adminModeration.chatEvidence') }}</span>
              <strong>{{ chatEvidenceLabel(selectedReport) }}</strong>
              <small>{{ reviewerLabel(selectedReport) }}</small>
            </div>
          </div>

          <div v-if="selectedReport.description" class="admin-report-copy-card">
            <span>{{ t('adminModeration.studentNote') }}</span>
            <p>{{ selectedReport.description }}</p>
          </div>

          <div class="admin-report-evidence-grid">
            <article class="admin-report-copy-card">
              <span>{{ t('adminModeration.promptEvidence') }}</span>
              <p>{{ selectedReport.promptSnapshot || t('adminModeration.noPrompt') }}</p>
            </article>
            <article class="admin-report-copy-card">
              <span>{{ t('adminModeration.responseEvidence') }}</span>
              <p>{{ selectedReport.aiResponseSnapshot || t('adminModeration.noResponse') }}</p>
            </article>
          </div>
        </div>

        <p v-if="reviewDisabledReason" class="admin-report-notice">
          {{ reviewDisabledReason }}
        </p>

        <div class="field">
          <label for="admin-moderation-status">{{ t('adminModeration.reviewStatus') }}</label>
          <select id="admin-moderation-status" v-model="reviewForm.status" class="select" :disabled="reviewDisabled">
            <option v-for="option in reviewStatusOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </div>
        <div class="field">
          <label for="admin-moderation-reviewer-note">{{ t('adminModeration.reviewerNote') }}</label>
          <textarea
            id="admin-moderation-reviewer-note"
            v-model="reviewForm.reviewerNote"
            class="textarea"
            maxlength="1000"
            :disabled="reviewDisabled"
          />
        </div>
        <div class="list">
          <ListInfoItem
            v-for="item in aiReports"
            :key="item.id"
            :title="formatReportReason(item.reasonCategory)"
            :description="item.description || formatFeatureKey(item.featureKey)"
            icon="ai"
          >
            <template #meta>
              <span class="tag">{{ formatFeatureKey(item.featureKey) }}</span>
              <span class="tag">{{ item.studentName || t('adminModeration.unknownLearner') }}</span>
            </template>
            <template #aside>
              <div class="admin-report-list-aside">
                <StatusPill :label="formatStatus(item.status)" :tone="reportStatusTone(item.status)" />
                <button class="student-link-button" type="button" @click="selectedReportId = item.id">
                  {{ selectedReportId === item.id ? t('adminModeration.selected') : t('adminModeration.review') }}
                </button>
              </div>
            </template>
          </ListInfoItem>
        </div>
      </ActionFormCard>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { adminApi } from '@/api/services'
import type { AdminAiResponseReportResponse, AiResponseReportReviewStatus } from '@/types/api'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ActionFormCard from '@/components/dashboard/ActionFormCard.vue'
import InfoCard from '@/components/dashboard/InfoCard.vue'
import ListInfoItem from '@/components/dashboard/ListInfoItem.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { adminAiReports, adminModerationRecords } from '@/demo/data'
import { adminModerationMessages } from '@/plugins/adminModerationMessages'
import { resolveApiError } from '@/utils/apiError'
import { formatDateTime } from '@/utils/format'

const { t } = useI18n({
  messages: {
    en: { adminModeration: adminModerationMessages.en },
    ms: { adminModeration: adminModerationMessages.ms },
    zh: { adminModeration: adminModerationMessages.zh },
  },
})

type PillTone = 'info' | 'primary' | 'success' | 'warning'

const defaultReviewerNote = computed(() => t('adminModeration.defaultNote'))

const moderationResource = useResource(() => adminApi.moderationRecords(), adminModerationRecords)
const aiReportsResource = useResource(() => adminApi.aiReports(), adminAiReports)

const moderationRecords = computed(
  () => moderationResource.data.value ?? (moderationResource.usingFallback.value ? adminModerationRecords : []),
)
const aiReports = computed(
  () => aiReportsResource.data.value ?? (aiReportsResource.usingFallback.value ? adminAiReports : []),
)
const selectedReportId = ref(0)
const selectedReport = computed(() => aiReports.value.find((item) => item.id === selectedReportId.value) ?? null)

const reviewForm = reactive<{
  status: AiResponseReportReviewStatus
  reviewerNote: string
}>({
  status: 'IN_REVIEW',
  reviewerNote: defaultReviewerNote.value,
})

const reviewStatusOptions = computed<Array<{ value: AiResponseReportReviewStatus; label: string }>>(() => [
  { value: 'IN_REVIEW', label: t('adminModeration.inReview') },
  { value: 'RESOLVED', label: t('adminModeration.resolved') },
  { value: 'DISMISSED', label: t('adminModeration.dismissed') },
])

const submittingReview = ref(false)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
const pageError = computed(() => actionError.value || moderationResource.error.value || aiReportsResource.error.value)

const openReportCount = computed(
  () => aiReports.value.filter((item) => item.status === 'PENDING_REVIEW' || item.status === 'IN_REVIEW').length,
)
const pendingReviewCount = computed(() => aiReports.value.filter((item) => item.status === 'PENDING_REVIEW').length)
const reviewReadOnly = computed(() => aiReportsResource.usingFallback.value)
const reviewClosed = computed(() => isClosedStatus(selectedReport.value?.status))
const reviewDisabled = computed(() => reviewReadOnly.value || !selectedReport.value || reviewClosed.value)
const reviewDisabledReason = computed(() => {
  if (reviewReadOnly.value) {
    return t('adminModeration.previewDisabled')
  }
  if (!selectedReport.value) {
    return t('adminModeration.noReport')
  }
  if (reviewClosed.value) {
    return t('adminModeration.closedSelect')
  }
  return null
})

const moderationStats = computed(() => [
  { icon: 'shield', label: t('adminModeration.records'), value: String(moderationRecords.value.length) },
  { icon: 'ai', label: t('adminModeration.openReports'), value: String(openReportCount.value) },
  { icon: 'history', label: t('adminModeration.pendingReview'), value: String(pendingReviewCount.value) },
])

watch(
  aiReports,
  (items) => {
    if (!items.some((item) => item.id === selectedReportId.value)) {
      selectedReportId.value = items[0]?.id ?? 0
    }
  },
  { immediate: true, deep: true },
)

watch(
  selectedReport,
  (report) => {
    if (!report) {
      reviewForm.status = 'IN_REVIEW'
      reviewForm.reviewerNote = defaultReviewerNote.value
      return
    }

    reviewForm.status = report.status === 'IN_REVIEW' ? 'RESOLVED' : 'IN_REVIEW'
    reviewForm.reviewerNote = report.reviewerNote || defaultReviewerNote.value
  },
  { immediate: true },
)

async function reviewAiReport(): Promise<void> {
  if (submittingReview.value) return
  if (reviewReadOnly.value) {
    actionError.value = t('adminModeration.previewDisabled')
    actionSuccess.value = null
    return
  }

  if (!selectedReport.value) {
    actionError.value = t('adminModeration.noReport')
    actionSuccess.value = null
    return
  }

  if (reviewClosed.value) {
    actionError.value = t('adminModeration.closed')
    actionSuccess.value = null
    return
  }

  if (!reviewForm.reviewerNote.trim()) {
    actionError.value = t('adminModeration.noteRequired')
    actionSuccess.value = null
    return
  }

  actionError.value = null
  actionSuccess.value = null
  submittingReview.value = true
  try {
    const submittedStatus = reviewForm.status
    await adminApi.reviewAiReport(selectedReport.value.id, {
      status: submittedStatus,
      reviewerNote: reviewForm.reviewerNote.trim(),
    })
    await aiReportsResource.reload()
    actionSuccess.value = t('adminModeration.updated', { status: formatStatus(submittedStatus) })
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('adminModeration.updateError'))
  } finally {
    submittingReview.value = false
  }
}

function formatStatus(status: string): string {
  const keys: Record<string, string> = {
    PENDING_REVIEW: 'pendingReview',
    IN_REVIEW: 'inReview',
    RESOLVED: 'resolved',
    DISMISSED: 'dismissed',
    HIDE: 'hidden',
    HIDDEN: 'hidden',
    UNPUBLISH: 'unpublished',
    UNPUBLISHED: 'unpublished',
    ARCHIVE: 'archived',
    ARCHIVED: 'archived',
    RESTORE: 'restored',
    RESTORED: 'restored',
    REQUEST_REVISION: 'revisionRequested',
  }
  const key = keys[status.toUpperCase()]
  if (key) return t(`adminModeration.${key}`)
  return status
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/(^|\s)\w/g, (match) => match.toUpperCase())
}

function formatFeatureKey(featureKey: string): string {
  const keys: Record<string, string> = {
    AI_CHAT: 'aiChat',
    CHAT_TUTOR: 'chatTutor',
    WRITING_FEEDBACK: 'writingFeedback',
    PRONUNCIATION: 'pronunciation',
  }
  const key = keys[featureKey.toUpperCase()]
  if (key) return t(`adminModeration.${key}`)
  return formatStatus(featureKey)
}

function formatReportReason(reason: string): string {
  const keys: Record<string, string> = {
    INAPPROPRIATE_CONTENT: 'inappropriate',
    UNSAFE_RESPONSE: 'unsafe',
    INACCURATE_RESPONSE: 'inaccurate',
    INCORRECT_CONTENT: 'incorrect',
    OTHER: 'other',
  }
  const key = keys[reason.toUpperCase()]
  if (key) return t(`adminModeration.${key}`)
  return formatStatus(reason)
}

function reportStatusTone(status: string): PillTone {
  switch (status) {
    case 'PENDING_REVIEW':
      return 'warning' as const
    case 'IN_REVIEW':
      return 'info' as const
    case 'RESOLVED':
      return 'success' as const
    case 'DISMISSED':
      return 'primary' as const
    default:
      return 'primary' as const
  }
}

function isClosedStatus(status?: string | null): boolean {
  return status === 'RESOLVED' || status === 'DISMISSED'
}

function chatEvidenceLabel(report: AdminAiResponseReportResponse): string {
  if (report.chatSessionId && report.chatMessageId) {
    return t('adminModeration.chatAndMessage', { chat: report.chatSessionId, message: report.chatMessageId })
  }
  if (report.chatSessionId) {
    return t('adminModeration.chat', { chat: report.chatSessionId })
  }
  return t('adminModeration.noChat')
}

function reviewerLabel(report: AdminAiResponseReportResponse): string {
  if (report.reviewerName && report.reviewedAt) {
    return t('adminModeration.reviewedOn', { name: report.reviewerName, date: formatDateTime(report.reviewedAt) })
  }
  if (report.reviewerName) {
    return t('adminModeration.reviewedBy', { name: report.reviewerName })
  }
  return t('adminModeration.awaiting')
}
</script>

<style scoped>
.admin-moderation-layout {
  align-items: start;
}

.admin-report-detail,
.admin-report-evidence-grid,
.admin-report-summary-grid {
  display: grid;
  gap: 12px;
}

.admin-report-summary-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.admin-report-summary-grid > div,
.admin-report-copy-card {
  padding: 14px 16px;
  border: 1px solid rgba(229, 233, 245, 0.96);
  border-radius: 18px;
  background: rgba(248, 250, 255, 0.92);
}

.admin-report-summary-grid span,
.admin-report-copy-card span,
.admin-report-summary-grid small,
.admin-report-notice {
  color: #72809d;
}

.admin-report-summary-grid strong,
.admin-report-copy-card p {
  color: #344260;
}

.admin-report-summary-grid strong,
.admin-report-summary-grid small,
.admin-report-copy-card span {
  display: block;
}

.admin-report-copy-card p {
  margin: 8px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.55;
}

.admin-report-evidence-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.admin-report-notice {
  margin: 0;
  font-weight: 600;
}

.admin-report-list-aside {
  display: grid;
  justify-items: end;
  gap: 8px;
}

@media (max-width: 960px) {
  .admin-report-summary-grid,
  .admin-report-evidence-grid {
    grid-template-columns: 1fr;
  }
}
</style>
