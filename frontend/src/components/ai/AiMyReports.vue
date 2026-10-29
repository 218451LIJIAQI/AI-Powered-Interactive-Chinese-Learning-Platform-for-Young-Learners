<template>
  <div class="ai-reports" data-testid="ai-my-reports">
    <StatePanel
      v-if="loading"
      tone="loading"
      :title="t('aiHelper.reportsLoading')"
      :message="t('aiHelper.reportsLoadingHint')"
      :skeleton="false"
    />
    <StatePanel
      v-else-if="error && !reports.length"
      tone="error"
      icon="alert"
      :title="t('aiHelper.reportsError')"
      :message="error"
    >
      <template #actions>
        <button class="btn btn-secondary btn-sm" type="button" @click="emit('retry')">{{ t('aiHelper.retry') }}</button>
      </template>
    </StatePanel>
    <template v-else-if="reports.length">
      <p class="ai-reports-count">{{ t('aiHelper.myReportsCount', { count: reports.length }, reports.length) }}</p>
      <ul class="ai-report-list">
        <li v-for="report in reports" :key="report.id" class="ai-report-item">
          <div class="ai-report-top">
            <strong>{{ t(reportReasonKey(report.reasonCategory)) }}</strong>
            <StatusPill :label="t(status(report).labelKey)" :tone="status(report).tone" :icon="status(report).icon" />
          </div>
          <p v-if="firstLine(report)" class="ai-report-quote cjk-text">“{{ firstLine(report) }}”</p>
          <p class="ai-report-meta">
            {{
              t('aiHelper.reportSent', {
                feature: t(reportFeatureKey(report.featureKey)),
                time: formatDateTime(report.createdAt),
              })
            }}
          </p>
          <p v-if="report.status === 'PENDING_REVIEW'" class="ai-report-waiting">
            <IconGlyph name="clock" :size="14" />{{ t('aiHelper.reportWaiting') }}
          </p>
          <div v-else-if="report.reviewerNote" class="ai-report-note">
            <p class="ai-report-note-head">
              {{ t('aiHelper.adminNote', { time: formatDateTime(report.reviewedAt) }) }}
            </p>
            <p>{{ report.reviewerNote }}</p>
          </div>
        </li>
      </ul>
    </template>
    <StatePanel
      v-else
      tone="empty"
      icon="flag"
      :title="t('aiHelper.noReportsTitle')"
      :message="t('aiHelper.noReports')"
    />
  </div>
</template>

<script setup lang="ts">
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import { aiSafetyMessages } from '@/plugins/aiSafetyMessages'
import type { StudentAiResponseReportResponse } from '@/types/api'
import { formatDateTime } from '@/utils/format'

import { REPORT_STATUS, reportFeatureKey, reportReasonKey } from './aiHelper'

/** "My reports" (in a drawer of the AI Helper): every AI report of the student, with the admin's note once answered. */
defineProps<{
  reports: readonly StudentAiResponseReportResponse[]
  loading: boolean
  error: string | null
}>()
const emit = defineEmits<{ retry: [] }>()

const { t } = useLocalMessages({ aiHelper: aiHelperMessages, aiSafety: aiSafetyMessages })

function status(report: StudentAiResponseReportResponse) {
  return REPORT_STATUS[report.status] ?? REPORT_STATUS.PENDING_REVIEW
}

/** The last line of what was asked (a conversation's newest turn), else of the answer: the quote that names a report. */
function firstLine(report: StudentAiResponseReportResponse): string {
  const text = (report.promptSnapshot || report.aiResponseSnapshot || '').split('\n').at(-1)?.trim() ?? ''
  return text.length > 90 ? `${text.slice(0, 87)}…` : text
}
</script>
