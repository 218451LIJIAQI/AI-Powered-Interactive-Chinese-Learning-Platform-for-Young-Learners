<template>
  <div class="page-stack admin-moderation-page">
    <PageHeaderBlock
      :title="t('adminModeration.title')"
      :description="t('adminModeration.description')"
      icon="moderation"
    />

    <DataNotice
      :using-fallback="moderationResource.usingFallback.value"
      :error="actionError"
      :success="actionSuccess"
    />

    <ResourceSection class="page-stack" :loading="pageLoading" :error="loadError" @retry="reloadAll">
      <section class="admin-stack" aria-labelledby="admin-moderation-summary-title">
        <div class="admin-section-head">
          <h2 id="admin-moderation-summary-title" class="section-title">{{ t('adminModeration.safetyReview') }}</h2>
          <p class="section-description">{{ t('adminModeration.heroHint') }}</p>
        </div>
        <div class="admin-kpi-grid admin-kpi-grid--3">
          <AdminKpiTile
            v-for="stat in moderationStats"
            :key="stat.label"
            :label="stat.label"
            :value="stat.value"
            :icon="stat.icon"
            :tone="stat.tone"
          />
        </div>
      </section>

      <section class="admin-split admin-split--even">
        <article class="panel admin-panel">
          <div class="card-header">
            <div>
              <h2 class="card-title">{{ t('adminModeration.reports') }}</h2>
              <p class="card-subtitle">{{ t('adminModeration.reportsHint') }}</p>
            </div>
          </div>

          <ul v-if="aiReports.length" class="admin-queue">
            <li v-for="item in aiReports" :key="item.id">
              <button
                type="button"
                class="admin-queue-item"
                :class="{ 'is-selected': selectedReportId === item.id }"
                :aria-pressed="selectedReportId === item.id"
                @click="selectedReportId = item.id"
              >
                <span class="admin-icon-badge is-sm admin-tone-primary" aria-hidden="true">
                  <IconGlyph name="ai" :size="16" />
                </span>
                <span class="admin-queue-copy">
                  <span class="admin-queue-title">
                    <strong>{{ formatReportReason(item.reasonCategory) }}</strong>
                    <StatusPill :label="formatStatus(item.status)" :tone="aiReportStatusTone(item.status)" />
                  </span>
                  <span class="admin-feed-text">{{ item.description || formatFeatureKey(item.featureKey) }}</span>
                  <span class="admin-meta-row">
                    <span class="tag">{{ formatFeatureKey(item.featureKey) }}</span>
                    <span class="tag">{{ item.studentName || t('adminModeration.unknownLearner') }}</span>
                  </span>
                </span>
              </button>
            </li>
          </ul>
          <StatePanel
            v-else
            compact
            icon="ai"
            :title="t('adminModeration.noReportsTitle')"
            :message="t('adminModeration.noReport')"
          />
          <PaginationBar
            v-if="reportTotalPages > 1"
            :info="t('adminModeration.pageOf', { page: reportPage, total: reportTotalPages })"
            :previous-label="t('adminModeration.previous')"
            :next-label="t('adminModeration.next')"
            :previous-disabled="reportPage <= 1"
            :next-disabled="reportPage >= reportTotalPages"
            @previous="reportPage -= 1"
            @next="reportPage += 1"
          />
        </article>

        <form class="panel admin-panel" @submit.prevent="reviewAiReport">
          <div class="card-header">
            <div>
              <h2 class="card-title">{{ t('adminModeration.reviewTitle') }}</h2>
              <p v-if="selectedReport" class="card-subtitle">
                {{ formatFeatureKey(selectedReport.featureKey) }} ·
                {{ formatReportReason(selectedReport.reasonCategory) }}
              </p>
            </div>
            <StatusPill
              v-if="selectedReport"
              :label="formatStatus(selectedReport.status)"
              :tone="aiReportStatusTone(selectedReport.status)"
            />
          </div>

          <template v-if="selectedReport">
            <dl class="admin-moderation-summary">
              <div class="admin-note-block">
                <dt>{{ t('adminModeration.student') }}</dt>
                <dd>
                  <strong>{{ selectedReport.studentName || t('adminModeration.unknownLearner') }}</strong>
                </dd>
                <dd>{{ selectedReport.studentEmail || t('adminModeration.noEmail') }}</dd>
              </div>
              <div class="admin-note-block">
                <dt>{{ t('adminModeration.feature') }}</dt>
                <dd>
                  <strong>{{ formatFeatureKey(selectedReport.featureKey) }}</strong>
                </dd>
                <dd v-if="selectedReport.aiUsageLogId">
                  {{ t('adminModeration.usageLog', { id: selectedReport.aiUsageLogId }) }}
                </dd>
              </div>
              <div class="admin-note-block">
                <dt>{{ t('adminModeration.reason') }}</dt>
                <dd>
                  <strong>{{ formatReportReason(selectedReport.reasonCategory) }}</strong>
                </dd>
                <dd>
                  {{ t('adminModeration.reported', { date: formatDateTime(selectedReport.createdAt) }) }}
                </dd>
              </div>
              <div class="admin-note-block">
                <dt>{{ t('adminModeration.chatEvidence') }}</dt>
                <dd>
                  <strong>{{ chatEvidenceLabel(selectedReport) }}</strong>
                </dd>
                <dd>{{ reviewerLabel(selectedReport) }}</dd>
              </div>
            </dl>

            <div v-if="selectedReport.description" class="admin-note-block">
              <span>{{ t('adminModeration.studentNote') }}</span>
              <p>{{ selectedReport.description }}</p>
            </div>

            <div class="admin-moderation-evidence">
              <div class="admin-note-block">
                <span>{{ t('adminModeration.promptEvidence') }}</span>
                <p>{{ selectedReport.promptSnapshot || t('adminModeration.noPrompt') }}</p>
              </div>
              <div class="admin-note-block">
                <span>{{ t('adminModeration.responseEvidence') }}</span>
                <p>{{ selectedReport.aiResponseSnapshot || t('adminModeration.noResponse') }}</p>
              </div>
            </div>
          </template>

          <p v-if="reviewDisabledReason" id="admin-moderation-disabled-reason" class="field-hint">
            {{ reviewDisabledReason }}
          </p>

          <div class="field">
            <label for="admin-moderation-status">{{ t('adminModeration.reviewStatus') }}</label>
            <select
              id="admin-moderation-status"
              v-model="reviewForm.status"
              class="select"
              :disabled="reviewDisabled"
              :aria-describedby="reviewDisabledReason ? 'admin-moderation-disabled-reason' : undefined"
            >
              <option v-for="option in reviewStatusOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </div>
          <div class="field">
            <label for="admin-moderation-reviewer-note">
              {{ t('adminModeration.reviewerNote') }}<span class="required-mark" aria-hidden="true">*</span>
            </label>
            <textarea
              id="admin-moderation-reviewer-note"
              v-model="reviewForm.reviewerNote"
              class="textarea"
              maxlength="1000"
              required
              :disabled="reviewDisabled"
              :aria-describedby="reviewDisabledReason ? 'admin-moderation-disabled-reason' : undefined"
            />
          </div>

          <div class="form-actions">
            <button
              class="btn btn-primary"
              type="submit"
              :disabled="reviewDisabled || submittingReview"
              :aria-busy="submittingReview"
            >
              {{ submittingReview ? t('adminModeration.saving') : t('adminModeration.saveReview') }}
            </button>
          </div>
        </form>
      </section>

      <article class="panel admin-panel">
        <div class="card-header">
          <div>
            <h2 class="card-title">{{ t('adminModeration.records') }}</h2>
            <p class="card-subtitle">{{ t('adminModeration.recordsHint') }}</p>
          </div>
        </div>

        <div
          v-if="moderationRecords.length"
          class="data-table-wrap admin-table-wrap"
          role="region"
          :aria-label="t('adminModeration.records')"
          tabindex="0"
        >
          <table class="data-table admin-table admin-table-static">
            <thead>
              <tr>
                <th scope="col">{{ t('adminModeration.recordContent') }}</th>
                <th scope="col">{{ t('adminModeration.recordType') }}</th>
                <th scope="col">{{ t('adminModeration.recordAction') }}</th>
                <th scope="col" class="col-numeric">{{ t('adminModeration.recordDate') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in moderationRecords" :key="item.id">
                <td>
                  <div class="admin-table-cell-copy">
                    <strong>{{ item.targetTitle ?? `${formatTargetType(item.targetType)} #${item.targetId}` }}</strong>
                    <span>{{ item.note || formatStatus(item.actionTaken) }}</span>
                  </div>
                </td>
                <td :data-label="t('adminModeration.recordType')">
                  <span class="tag">{{ formatTargetType(item.targetType) }}</span>
                </td>
                <td :data-label="t('adminModeration.recordAction')">
                  <span class="tag">{{ formatStatus(item.actionTaken) }}</span>
                </td>
                <td class="col-numeric admin-table-nowrap" :data-label="t('adminModeration.recordDate')">
                  {{ formatDateTime(item.createdAt) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <StatePanel
          v-else
          compact
          icon="moderation"
          :title="t('adminModeration.noRecordsTitle')"
          :message="t('adminModeration.noRecordsHint')"
        />
      </article>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { adminApi } from '@/api/services'
import type {
  AdminAiResponseReportResponse,
  AiFeatureKey,
  AiResponseReportReason,
  AiResponseReportReviewStatus,
} from '@/types/api'
import AdminKpiTile from '@/components/admin/AdminKpiTile.vue'
import { aiReportStatusTone } from '@/components/admin/adminDisplay'
import type { AdminTone } from '@/components/admin/adminTone'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { adminAiReports, adminModerationRecords } from '@/demo/data'
import { adminModerationMessages } from '@/plugins/adminModerationMessages'
import { humanizeEnumValue } from '@/plugins/statusLabelMessages'
import { resolveApiError } from '@/utils/apiError'
import { formatDateTime } from '@/utils/format'
import { pagePreviewItems } from '@/utils/previewPaging'

const { t } = useI18n({
  messages: {
    en: { adminModeration: adminModerationMessages.en },
    ms: { adminModeration: adminModerationMessages.ms },
    'zh-CN': { adminModeration: adminModerationMessages['zh-CN'] },
  },
})

const PAGE_SIZE = 20
/** 1-based page of the AI report queue; every report stays reachable (FR-REP-03), not only the newest page. */
const reportPage = ref(1)

const moderationResource = useResource(
  () => adminApi.moderationRecordPage({ size: PAGE_SIZE }),
  () => pagePreviewItems(adminModerationRecords, 0, PAGE_SIZE),
)
const aiReportsResource = useResource(
  () => adminApi.aiReportPage({ page: reportPage.value - 1, size: PAGE_SIZE }),
  () => pagePreviewItems(adminAiReports, reportPage.value - 1, PAGE_SIZE),
)
/** KPI totals come from the server, so they count every report, not only the rows on screen. */
const reportCountsResource = useResource(
  async () => {
    const [pending, inReview] = await Promise.all([
      adminApi.aiReportPage({ status: 'PENDING_REVIEW', size: 1 }),
      adminApi.aiReportPage({ status: 'IN_REVIEW', size: 1 }),
    ])
    return { pending: pending.totalItems, inReview: inReview.totalItems }
  },
  () => ({
    pending: adminAiReports.filter((item) => item.status === 'PENDING_REVIEW').length,
    inReview: adminAiReports.filter((item) => item.status === 'IN_REVIEW').length,
  }),
)

const moderationRecords = computed(() => moderationResource.data.value?.items ?? [])
const aiReports = computed(() => aiReportsResource.data.value?.items ?? [])
const reportTotalPages = computed(() => aiReportsResource.data.value?.totalPages ?? 1)
const selectedReportId = ref(0)
const selectedReport = computed(() => aiReports.value.find((item) => item.id === selectedReportId.value) ?? null)

const reviewForm = reactive<{
  status: AiResponseReportReviewStatus
  reviewerNote: string
}>({
  status: 'IN_REVIEW',
  reviewerNote: '',
})

const reviewStatusOptions = computed<Array<{ value: AiResponseReportReviewStatus; label: string }>>(() => [
  { value: 'IN_REVIEW', label: t('adminModeration.inReview') },
  { value: 'RESOLVED', label: t('adminModeration.resolved') },
  { value: 'DISMISSED', label: t('adminModeration.dismissed') },
])

const submittingReview = ref(false)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
const pageLoading = computed(() => moderationResource.loading.value || aiReportsResource.loading.value)
const loadError = computed(
  () => moderationResource.error.value || aiReportsResource.error.value || reportCountsResource.error.value,
)

const pendingReviewCount = computed(() => reportCountsResource.data.value?.pending ?? 0)
const openReportCount = computed(() => pendingReviewCount.value + (reportCountsResource.data.value?.inReview ?? 0))
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

const moderationStats = computed<Array<{ icon: string; label: string; value: string; tone: AdminTone }>>(() => [
  {
    icon: 'shield',
    label: t('adminModeration.records'),
    value: String(moderationResource.data.value?.totalItems ?? 0),
    tone: 'primary',
  },
  { icon: 'ai', label: t('adminModeration.openReports'), value: String(openReportCount.value), tone: 'info' },
  {
    icon: 'history',
    label: t('adminModeration.pendingReview'),
    value: String(pendingReviewCount.value),
    tone: 'warning',
  },
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
      reviewForm.reviewerNote = ''
      return
    }

    reviewForm.status = report.status === 'IN_REVIEW' ? 'RESOLVED' : 'IN_REVIEW'
    reviewForm.reviewerNote = report.reviewerNote || ''
  },
  { immediate: true },
)

watch(reportPage, () => {
  void aiReportsResource.reload()
})

function reloadAll(): void {
  void moderationResource.reload()
  void aiReportsResource.reload()
  void reportCountsResource.reload()
}

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
    await Promise.all([aiReportsResource.reload(), reportCountsResource.reload()])
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
    // D14: hide / unpublish / archive all leave the content ARCHIVED; request revision leaves it REJECTED.
    HIDE: 'archived',
    HIDDEN: 'archived',
    UNPUBLISH: 'archived',
    UNPUBLISHED: 'archived',
    ARCHIVE: 'archived',
    ARCHIVED: 'archived',
    RESTORE: 'restored',
    RESTORED: 'restored',
    REQUEST_REVISION: 'revisionRequested',
    REJECTED: 'revisionRequested',
  }
  const key = keys[status.toUpperCase()]
  if (key) return t(`adminModeration.${key}`)
  return humanizeEnumValue(status)
}

function formatTargetType(targetType: string): string {
  const keys: Record<string, string> = {
    PACKAGE: 'typeCourse',
    MODULE: 'typeModule',
    LESSON: 'typeLesson',
  }
  const key = keys[targetType.toUpperCase()]
  if (key) return t(`adminModeration.${key}`)
  return formatStatus(targetType)
}

function formatFeatureKey(featureKey: string): string {
  const keys: Record<AiFeatureKey, string> = {
    CHAT_TUTOR: 'chatTutor',
    VOCABULARY_HELPER: 'vocabularyHelper',
    SENTENCE_PRACTICE: 'sentencePractice',
    QUIZ_GENERATOR: 'quizGenerator',
    STORY_GENERATOR: 'storyGenerator',
    PRONUNCIATION_FEEDBACK: 'pronunciationFeedback',
    WRITING_FEEDBACK: 'writingFeedback',
  }
  const key = (keys as Record<string, string | undefined>)[featureKey.toUpperCase()]
  if (key) return t(`adminModeration.${key}`)
  return formatStatus(featureKey)
}

function formatReportReason(reason: string): string {
  const keys: Record<AiResponseReportReason, string> = {
    INCORRECT_CONTENT: 'incorrect',
    HALLUCINATION: 'hallucination',
    UNSAFE_CONTENT: 'unsafe',
    INAPPROPRIATE_CONTENT: 'inappropriate',
    OTHER: 'other',
  }
  const key = (keys as Record<string, string | undefined>)[reason.toUpperCase()]
  if (key) return t(`adminModeration.${key}`)
  return formatStatus(reason)
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
.admin-moderation-summary,
.admin-moderation-evidence {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
  margin: 0;
}

@media (max-width: 640px) {
  .admin-moderation-summary,
  .admin-moderation-evidence {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
