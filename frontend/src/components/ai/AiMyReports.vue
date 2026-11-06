<template>
  <section class="panel ai-reports" :aria-labelledby="`${baseId}-title`" data-testid="ai-my-reports">
    <header class="ai-reports-head">
      <h2 :id="`${baseId}-title`" class="card-title">{{ t('aiHelper.myReports') }}</h2>
      <p v-if="reports.length" class="card-subtitle">
        {{ t('aiHelper.myReportsCount', { count: reports.length }, reports.length) }}
      </p>
    </header>
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
    <ul v-else-if="reports.length" class="row-list ai-report-list" tabindex="0" :aria-labelledby="`${baseId}-title`">
      <li v-for="report in reports" :key="report.id" class="ai-report-item">
        <div class="ai-report-top">
          <strong>{{ t(reportReasonKey(report.reasonCategory)) }}</strong>
          <StatusPill :label="t(status(report).labelKey)" :tone="status(report).tone" />
        </div>
        <p v-if="firstLine(report)" class="ai-report-meta">“{{ firstLine(report) }}”</p>
        <p class="ai-report-meta">
          {{
            t('aiHelper.reportSent', {
              feature: t(reportFeatureKey(report.featureKey)),
              time: formatDateTime(report.createdAt),
            })
          }}
        </p>
        <p v-if="report.status === 'PENDING_REVIEW'" class="ai-report-meta">{{ t('aiHelper.reportWaiting') }}</p>
        <div v-else-if="report.reviewerNote" class="ai-report-note">
          <p class="ai-report-note-head">
            {{ t('aiHelper.adminNote', { time: formatDateTime(report.reviewedAt) }) }}
          </p>
          <p>{{ report.reviewerNote }}</p>
        </div>
      </li>
    </ul>
    <StatePanel
      v-else
      tone="empty"
      icon="flag"
      :title="t('aiHelper.noReportsTitle')"
      :message="t('aiHelper.noReports')"
    />
  </section>
</template>

<script setup lang="ts">
import { useId } from 'vue'

import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import { aiSafetyMessages } from '@/plugins/aiSafetyMessages'
import type { StudentAiResponseReportResponse } from '@/types/api'
import { formatDateTime } from '@/utils/format'

import { REPORT_STATUS, reportFeatureKey, reportReasonKey } from './aiHelper'

/** "My reports", the rail of Ask SparkBot: every AI report of the student, with the admin's note once answered. */
defineProps<{
  reports: readonly StudentAiResponseReportResponse[]
  loading: boolean
  error: string | null
}>()
const emit = defineEmits<{ retry: [] }>()

const { t } = useLocalMessages({ aiHelper: aiHelperMessages, aiSafety: aiSafetyMessages })
const baseId = useId()

function status(report: StudentAiResponseReportResponse) {
  return REPORT_STATUS[report.status] ?? REPORT_STATUS.PENDING_REVIEW
}

/** The last line of what was asked (a conversation's newest turn), else of the answer: the quote that names a report. */
function firstLine(report: StudentAiResponseReportResponse): string {
  const text = (report.promptSnapshot || report.aiResponseSnapshot || '').split('\n').at(-1)?.trim() ?? ''
  return text.length > 90 ? `${text.slice(0, 87)}…` : text
}
</script>
