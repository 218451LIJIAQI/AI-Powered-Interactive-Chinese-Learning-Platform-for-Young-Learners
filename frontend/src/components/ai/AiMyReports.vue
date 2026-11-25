<template>
  <section class="panel ai-reports" :aria-labelledby="`${baseId}-title`" data-testid="ai-my-reports">
    <header class="ai-reports-head">
      <h2 :id="`${baseId}-title`" class="card-title">{{ t('aiHelper.myReports') }}</h2>
      <p v-if="totalItems" class="card-subtitle">
        {{ t('aiHelper.myReportsCount', { count: totalItems }, totalItems) }}
      </p>
    </header>
    <StatePanel
      v-if="loading"
      tone="loading"
      :title="t('aiHelper.reportsLoading')"
      :message="t('aiHelper.reportsLoadingHint')"
      :skeleton="false"
    />
    <StatePanel v-else-if="error" tone="error" :title="t('aiHelper.reportsError')" :message="error">
      <template #actions>
        <button class="btn btn-secondary btn-sm" type="button" @click="reload">{{ t('common.retry') }}</button>
      </template>
    </StatePanel>
    <ul v-else-if="reportRows.length" class="row-list ai-report-list" tabindex="0" :aria-labelledby="`${baseId}-title`">
      <li v-for="{ report, quote } in reportRows" :key="report.id" class="ai-report-item">
        <div class="ai-report-top">
          <strong>{{ t(reportReasonKey(report.reasonCategory)) }}</strong>
          <StatusPill :label="t(AI_REPORT_STATUS_KEYS[report.status])" :tone="REPORT_STATUS_TONES[report.status]" />
        </div>
        <p v-if="quote" class="ai-report-meta">“{{ quote }}”</p>
        <p class="ai-report-meta">
          {{
            t('aiHelper.reportSent', {
              feature: t(`opsLabels.aiFeatures.${report.featureKey}`),
              time: formatDateTime(report.createdAt),
            })
          }}
        </p>
        <p v-if="report.status === 'PENDING_REVIEW'" class="ai-report-meta">{{ t('aiHelper.reportWaiting') }}</p>
        <div v-else-if="report.reviewerNote" class="ai-report-note">
          <p class="ai-report-note-head">
            {{
              report.reviewedAt
                ? t('aiHelper.adminNote', { time: formatDateTime(report.reviewedAt) })
                : t('aiHelper.adminNoteNoTime')
            }}
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
    <PaginationBar
      v-if="!loading && !error"
      :page="displayedPage"
      :total-pages="totalPages"
      :label="t('aiHelper.myReports')"
      icon-only
      @update:page="goToPage"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'

import { aiHelperApi } from '@/api/ai'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill, { type PillTone } from '@/components/common/StatusPill.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource, useServerPage } from '@/composables/useResource'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import { AI_REPORT_STATUS_KEYS, reportReasonKey } from '@/plugins/aiSafetyMessages'
import { opsLabelMessages } from '@/plugins/opsLabelMessages'
import type { StudentAiResponseReportResponse } from '@/types/ai'
import type { AiResponseReportStatus } from '@/types/api'
import { formatDateTime } from '@/utils/format'
import { truncateText } from '@/utils/truncateText'

const REPORT_STATUS_TONES: Readonly<Record<AiResponseReportStatus, PillTone>> = {
  PENDING_REVIEW: 'info',
  RESOLVED: 'success',
  DISMISSED: 'neutral',
}

const props = defineProps<{ revision: number }>()
const page = ref(0)
const resource = useResource(() => aiHelperApi.reports(page.value))
const { loading, error, reload } = resource
const { items: reports, totalItems, totalPages, displayedPage, goToPage } = useServerPage(resource, page)
// A report submitted in any AI Helper tab appears at the start of the history.
watch(
  () => props.revision,
  () => goToPage(0),
)

const { t } = useLocalMessages({ aiHelper: aiHelperMessages, opsLabels: opsLabelMessages })
const baseId = useId()

/** The last line of what was asked (a conversation's newest turn), else of the answer: the quote that names a report. */
function quotedLine(report: StudentAiResponseReportResponse): string {
  const source = report.promptSnapshot?.trim() || report.aiResponseSnapshot?.trim() || ''
  const text = source.split('\n').at(-1)?.trim() ?? ''
  return text.length > 90 ? `${truncateText(text, 87)}…` : text
}

const reportRows = computed(() => reports.value.map((report) => ({ report, quote: quotedLine(report) })))
</script>
