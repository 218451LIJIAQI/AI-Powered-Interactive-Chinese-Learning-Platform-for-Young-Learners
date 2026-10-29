<template>
  <div class="page-stack admin-ai-page">
    <div class="admin-head">
      <PageHeaderBlock :title="t('adminAi.reportsTitle')" :description="t('adminAi.reportsLede')" />
      <AdminAiTabs />
    </div>
    <DataNotice :success="success" :error="decisionError" />

    <div class="admin-inbox">
      <section class="admin-sheet admin-queue" :aria-labelledby="`${baseId}-list`">
        <div class="admin-sheet-head">
          <div>
            <h2 :id="`${baseId}-list`" class="admin-sheet-title">{{ t('adminAi.reports') }}</h2>
            <p class="admin-sheet-caption">{{ t('adminAi.openFirst') }}</p>
          </div>
          <SegmentedControl
            :model-value="filter"
            :label="t('adminAi.filterLabel')"
            :options="FILTERS.map((option) => ({ value: option.value, label: t(option.labelKey) }))"
            @update:model-value="setFilter"
          />
        </div>

        <div v-if="reportsResource.loading.value" class="admin-sheet-body">
          <StatePanel tone="loading" :title="t('adminAi.reportsLoading')" :message="t('adminAi.reportsLoadingHint')" />
        </div>
        <div v-else-if="reportsResource.error.value && !items.length" class="admin-sheet-body">
          <StatePanel
            tone="error"
            icon="alert"
            :title="t('adminAi.reportsError')"
            :message="reportsResource.error.value"
          >
            <template #actions>
              <button class="btn btn-primary" type="button" @click="reportsResource.reload">
                {{ t('adminAi.retry') }}
              </button>
            </template>
          </StatePanel>
        </div>
        <ul v-else-if="items.length" class="admin-queue-list">
          <li v-for="report in items" :key="report.id">
            <button
              class="admin-queue-item"
              type="button"
              :aria-current="report.id === selected?.id ? 'true' : undefined"
              :data-testid="`admin-ai-report-${report.id}`"
              @click="select(report)"
            >
              <span class="admin-kind-mark" aria-hidden="true"><IconGlyph name="flag" :size="16" /></span>
              <span class="admin-queue-item-copy">
                <strong>{{ t(reportReasonKey(report.reasonCategory)) }}</strong>
                <span class="admin-queue-item-meta"
                  >{{ featureLabel(report.featureKey) }} · {{ report.studentName }}</span
                >
                <span class="admin-queue-item-tags">
                  <StatusPill :label="t(statusMeta(report).labelKey)" :tone="statusMeta(report).tone" />
                  <span class="admin-queue-item-meta">{{ formatDateTime(report.createdAt) }}</span>
                </span>
              </span>
            </button>
          </li>
        </ul>
        <div v-else class="admin-sheet-body">
          <StatePanel
            tone="empty"
            icon="check"
            :title="filter === 'OPEN' ? t('adminAi.noOpenReports') : t('adminAi.noReports')"
            :message="t('adminAi.noReportsHint')"
          />
        </div>
        <div v-if="totalPages > 1" class="admin-sheet-foot">
          <PaginationBar
            icon-only
            :current-page="page + 1"
            :total-pages="totalPages"
            :label="t('adminAi.reports')"
            @update:current-page="goToPage($event - 1)"
          />
        </div>
      </section>

      <div class="admin-reader-slot">
        <section
          v-if="selected"
          class="panel admin-reader"
          :aria-labelledby="`${baseId}-detail`"
          data-testid="admin-ai-report-detail"
        >
          <header class="admin-reader-head">
            <div class="admin-reader-heading">
              <h2 :id="`${baseId}-detail`">{{ t(reportReasonKey(selected.reasonCategory)) }}</h2>
              <p>{{ t('adminAi.reportedAt', { time: formatDateTime(selected.createdAt) }) }}</p>
              <div class="admin-reader-tags">
                <StatusPill :label="t(statusMeta(selected).labelKey)" :tone="statusMeta(selected).tone" />
              </div>
            </div>
          </header>

          <dl class="admin-facts">
            <div>
              <dt>{{ t('adminAi.student') }}</dt>
              <dd>{{ [selected.studentName, selected.studentEmail].filter(Boolean).join(' · ') }}</dd>
            </div>
            <div>
              <dt>{{ t('adminAi.feature') }}</dt>
              <dd>{{ featureLabel(selected.featureKey) }}</dd>
            </div>
          </dl>

          <section class="admin-ai-exchange" :aria-label="t('adminAi.exchangeLabel')">
            <div class="admin-ai-said">
              <h3 class="admin-reader-label">{{ t(snapshotLabels(selected.featureKey).prompt) }}</h3>
              <p>{{ selected.promptSnapshot || t('adminAi.notAttached') }}</p>
            </div>
            <div class="admin-ai-said is-answer">
              <h3 class="admin-reader-label">{{ t(snapshotLabels(selected.featureKey).answer) }}</h3>
              <p>{{ selected.aiResponseSnapshot || t('adminAi.notAttached') }}</p>
            </div>
            <div v-if="selected.description" class="admin-ai-said is-note">
              <h3 class="admin-reader-label">{{ t('adminAi.studentNote') }}</h3>
              <p>{{ selected.description }}</p>
            </div>
          </section>

          <form v-if="selected.status === 'PENDING_REVIEW'" class="admin-ai-decision" @submit.prevent>
            <div class="field">
              <label class="field-label" :for="`${baseId}-note`">
                {{ t('adminAi.noteToStudent') }}<span class="required-mark" aria-hidden="true">*</span>
              </label>
              <textarea
                :id="`${baseId}-note`"
                v-model="note"
                class="textarea"
                rows="4"
                required
                :maxlength="MAX_NOTE"
                :aria-describedby="`${baseId}-note-hint`"
                :aria-invalid="noteMissing || undefined"
              />
              <div class="field-foot">
                <p :id="`${baseId}-note-hint`" class="field-hint">
                  {{ t('adminAi.noteHint', { name: selected.studentName ?? '' }) }}
                </p>
                <p class="field-hint field-counter">{{ note.length }} / {{ MAX_NOTE }}</p>
              </div>
              <p v-if="noteMissing" class="field-error" role="alert">{{ t('adminAi.noteRequired') }}</p>
            </div>
            <div class="admin-reader-decision admin-ai-decision-actions">
              <p>{{ t('adminAi.decisionHint') }}</p>
              <button
                class="btn btn-secondary"
                type="button"
                :disabled="Boolean(deciding)"
                :aria-busy="deciding === 'DISMISSED'"
                @click="decide('DISMISSED')"
              >
                <IconGlyph name="close" :size="16" />
                {{ t('adminAi.dismiss') }}
              </button>
              <button
                class="btn btn-primary"
                type="button"
                :disabled="Boolean(deciding)"
                :aria-busy="deciding === 'RESOLVED'"
                @click="decide('RESOLVED')"
              >
                <IconGlyph name="check" :size="16" />
                {{ t('adminAi.resolve') }}
              </button>
            </div>
          </form>
          <section v-else class="admin-reader-section" :aria-labelledby="`${baseId}-decided`">
            <h3 :id="`${baseId}-decided`" class="admin-reader-label">
              {{ selected.status === 'RESOLVED' ? t('adminAi.resolvedNote') : t('adminAi.dismissedNote') }}
            </h3>
            <p>{{ selected.reviewerNote }}</p>
            <p class="type-caption">
              {{
                t(selected.status === 'RESOLVED' ? 'adminAi.resolvedBy' : 'adminAi.dismissedBy', {
                  name: selected.reviewerName ?? t('adminAi.anAdmin'),
                  time: formatDateTime(selected.reviewedAt),
                })
              }}
            </p>
          </section>
        </section>
        <StatePanel
          v-else-if="!reportsResource.loading.value && items.length"
          tone="empty"
          icon="flag"
          spacious
          :title="t('adminAi.pickReport')"
          :message="t('adminAi.pickReportHint')"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'

import { adminAiApi } from '@/api/ai'
import SegmentedControl from '@/components/common/SegmentedControl.vue'
import { aiFeatureLabel } from '@/components/admin/adminDisplay'
import AdminAiTabs from '@/components/admin/AdminAiTabs.vue'
import { useAiReportCounts } from '@/components/admin/useAiReportCounts'
import { REPORT_STATUS, reportReasonKey } from '@/components/ai/aiHelper'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource, useServerPage } from '@/composables/useResource'
import { adminAiMessages } from '@/plugins/adminAiMessages'
import { aiFeatureMessages } from '@/plugins/aiFeatureMessages'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import { aiSafetyMessages } from '@/plugins/aiSafetyMessages'
import type { AdminAiResponseReportResponse } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { formatDateTime } from '@/utils/format'
import '@/styles/ops-admin.css'

/**
 * Admin AI reports: open reports first; the detail shows who reported which feature, what was asked and answered and
 * the student's note. The admin resolves (the answer was wrong and they acted) or dismisses (the answer was fine)
 * with a note the student reads in My reports; the student is notified and the decision is audited.
 */
type Filter = 'ALL' | 'OPEN' | 'CLOSED'

const FILTERS: readonly { value: Filter; labelKey: string }[] = [
  { value: 'ALL', labelKey: 'adminAi.filterAll' },
  { value: 'OPEN', labelKey: 'adminAi.filterOpen' },
  { value: 'CLOSED', labelKey: 'adminAi.filterClosed' },
]
const SNAPSHOT_LABELS: Readonly<Record<string, { prompt: string; answer: string }>> = {
  CHAT_TUTOR: { prompt: 'adminAi.snapshotQuestion', answer: 'adminAi.snapshotAnswer' },
  XIAOMEI_CONVERSATION: { prompt: 'adminAi.snapshotConversation', answer: 'adminAi.snapshotReply' },
  QUIZ_GENERATOR: { prompt: 'adminAi.snapshotTopic', answer: 'adminAi.snapshotQuiz' },
  STORY_GENERATOR: { prompt: 'adminAi.snapshotTopic', answer: 'adminAi.snapshotStory' },
  PRACTICE_CHECK: { prompt: 'adminAi.snapshotChecked', answer: 'adminAi.snapshotScore' },
}
const PAGE_SIZE = 10
const MAX_NOTE = 1000

const { t } = useLocalMessages({
  adminAi: adminAiMessages,
  aiFeatures: aiFeatureMessages,
  aiHelper: aiHelperMessages,
  aiSafety: aiSafetyMessages,
})
const baseId = useId()
const { afterDecision } = useAiReportCounts()

const filter = ref<Filter>('ALL')
const page = ref(0)
const reportsResource = useResource(() =>
  adminAiApi.reports({
    status: filter.value === 'ALL' ? undefined : filter.value,
    page: page.value,
    size: PAGE_SIZE,
  }),
)
const { items, totalPages, goToPage } = useServerPage(reportsResource, page)
const selectedId = ref<number | null>(null)
const selected = computed(() => items.value.find((report) => report.id === selectedId.value) ?? null)
const note = ref('')
const noteMissing = ref(false)
const deciding = ref<'RESOLVED' | 'DISMISSED' | null>(null)
const success = ref<string | null>(null)
const decisionError = ref<string | null>(null)

// The first report (an open one, when there are any) is shown at once.
watch(items, (list) => {
  if (!list.some((report) => report.id === selectedId.value)) selectedId.value = list[0]?.id ?? null
})

function featureLabel(featureKey: string): string {
  return aiFeatureLabel(featureKey, t)
}

function snapshotLabels(featureKey: string): { prompt: string; answer: string } {
  return SNAPSHOT_LABELS[featureKey] ?? { prompt: 'adminAi.snapshotRequest', answer: 'adminAi.snapshotAnswer' }
}

function statusMeta(report: AdminAiResponseReportResponse) {
  return REPORT_STATUS[report.status] ?? REPORT_STATUS.PENDING_REVIEW
}

function select(report: AdminAiResponseReportResponse): void {
  selectedId.value = report.id
  note.value = ''
  noteMissing.value = false
  decisionError.value = null
}

function setFilter(next: Filter): void {
  if (filter.value === next) return
  filter.value = next
  success.value = null
  goToPage(0)
}

async function decide(decision: 'RESOLVED' | 'DISMISSED'): Promise<void> {
  const report = selected.value
  if (!report || deciding.value) return
  success.value = null
  decisionError.value = null
  noteMissing.value = !note.value.trim()
  if (noteMissing.value) return
  deciding.value = decision
  try {
    await adminAiApi.reviewReport(report.id, decision, note.value.trim())
    success.value = t(decision === 'RESOLVED' ? 'adminAi.resolvedNotice' : 'adminAi.dismissedNotice', {
      name: report.studentName ?? '',
    })
    note.value = ''
    await reportsResource.reload()
    void afterDecision()
  } catch (reason) {
    decisionError.value = resolveApiError(reason, t('adminAi.decisionError'))
  } finally {
    deciding.value = null
  }
}
</script>
