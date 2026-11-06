<template>
  <div class="page-stack">
    <PageHeaderBlock :title="t('adminAi.reportsTitle')" hide-title />
    <AdminAiTabs />
    <DataNotice :error="decisionError" />

    <StatePanel
      v-if="reportsResource.loading.value"
      tone="loading"
      spacious
      :title="t('adminAi.reportsLoading')"
      :message="t('adminAi.reportsLoadingHint')"
    />
    <StatePanel
      v-else-if="reportsResource.error.value && !items.length"
      tone="error"
      icon="alert"
      spacious
      :title="t('adminAi.reportsError')"
      :message="reportsResource.error.value"
    >
      <template #actions>
        <button class="btn btn-primary" type="button" @click="reportsResource.reload">
          {{ t('adminAi.retry') }}
        </button>
      </template>
    </StatePanel>
    <div v-else-if="!items.length" class="panel">
      <StatePanel
        tone="empty"
        icon="check"
        spacious
        :title="t('adminAi.noReports')"
        :message="t('adminAi.noReportsHint')"
      />
    </div>
    <div v-else class="list-detail admin-list-detail">
      <section class="panel admin-list" :aria-labelledby="`${baseId}-list`">
        <header class="admin-list-head">
          <h2 :id="`${baseId}-list`" class="card-title">{{ t('adminAi.reports') }}</h2>
          <p class="admin-meta">{{ t('adminAi.openFirst') }}</p>
        </header>
        <ul class="admin-select-list">
          <li v-for="report in items" :key="report.id">
            <button
              class="select-card"
              type="button"
              :aria-current="report.id === selected?.id ? 'true' : undefined"
              :data-testid="`admin-ai-report-${report.id}`"
              @click="select(report)"
            >
              <span class="admin-select-row">
                <strong class="admin-select-title">{{ t(reportReasonKey(report.reasonCategory)) }}</strong>
                <StatusPill :label="t(statusMeta(report).labelKey)" :tone="adminReportTone(report.status)" />
              </span>
              <span class="admin-meta">{{ featureLabel(report.featureKey) }} · {{ report.studentName }}</span>
              <span class="admin-meta">{{ formatDateTime(report.createdAt) }}</span>
            </button>
          </li>
        </ul>
        <PaginationBar
          v-if="totalPages > 1"
          icon-only
          :current-page="page + 1"
          :total-pages="totalPages"
          :label="t('adminAi.reports')"
          @update:current-page="goToPage($event - 1)"
        />
      </section>

      <div class="admin-detail-slot">
        <section
          v-if="selected"
          class="panel admin-detail"
          :aria-labelledby="`${baseId}-detail`"
          data-testid="admin-ai-report-detail"
        >
          <header class="admin-detail-head">
            <div class="admin-detail-heading">
              <h2 :id="`${baseId}-detail`" class="admin-detail-title">
                {{ t(reportReasonKey(selected.reasonCategory)) }}
              </h2>
              <p class="admin-meta">{{ t('adminAi.reportedAt', { time: formatDateTime(selected.createdAt) }) }}</p>
            </div>
            <StatusPill :label="t(statusMeta(selected).labelKey)" :tone="adminReportTone(selected.status)" />
          </header>

          <dl class="kv-list admin-ai-facts">
            <dt>{{ t('adminAi.student') }}</dt>
            <dd>{{ [selected.studentName, selected.studentEmail].filter(Boolean).join(' · ') }}</dd>
            <dt>{{ t('adminAi.feature') }}</dt>
            <dd>{{ featureLabel(selected.featureKey) }}</dd>
          </dl>

          <section class="admin-ai-exchange" :aria-label="t('adminAi.exchangeLabel')">
            <div class="admin-ai-said admin-ruled">
              <h3 class="admin-meta">{{ t(snapshotLabels(selected.featureKey).prompt) }}</h3>
              <p class="is-strong">{{ selected.promptSnapshot || t('adminAi.notAttached') }}</p>
            </div>
            <div class="admin-ai-said admin-ruled">
              <h3 class="admin-meta">{{ t(snapshotLabels(selected.featureKey).answer) }}</h3>
              <p>{{ selected.aiResponseSnapshot || t('adminAi.notAttached') }}</p>
            </div>
            <div v-if="selected.description" class="admin-ai-said admin-ruled">
              <h3 class="admin-meta">{{ t('adminAi.studentNote') }}</h3>
              <p>{{ selected.description }}</p>
            </div>
          </section>

          <form v-if="selected.status === 'PENDING_REVIEW'" class="admin-section admin-ruled" @submit.prevent>
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
              <p :id="`${baseId}-note-hint`" class="field-hint">
                {{ t('adminAi.noteHint', { name: selected.studentName ?? '' }) }}
              </p>
              <p v-if="noteMissing" class="field-error" role="alert">{{ t('adminAi.noteRequired') }}</p>
            </div>
            <div class="admin-ai-decision">
              <p class="admin-meta">{{ t('adminAi.decisionHint') }}</p>
              <div class="btn-group">
                <button
                  class="btn btn-secondary"
                  type="button"
                  :disabled="Boolean(deciding)"
                  :aria-busy="deciding === 'DISMISSED'"
                  @click="decide('DISMISSED')"
                >
                  <IconGlyph name="close" :size="16" />
                  <span>{{ t('adminAi.dismiss') }}</span>
                </button>
                <button
                  class="btn btn-primary"
                  type="button"
                  :disabled="Boolean(deciding)"
                  :aria-busy="deciding === 'RESOLVED'"
                  @click="decide('RESOLVED')"
                >
                  <IconGlyph name="check" :size="16" />
                  <span>{{ t('adminAi.resolve') }}</span>
                </button>
              </div>
            </div>
          </form>
          <section v-else class="admin-ai-said admin-ruled" :aria-labelledby="`${baseId}-decided`">
            <h3 :id="`${baseId}-decided`" class="admin-meta">{{ t('adminAi.noteToStudent') }}</h3>
            <p>{{ selected.reviewerNote }}</p>
            <p class="admin-meta">
              {{
                t(selected.status === 'RESOLVED' ? 'adminAi.resolvedBy' : 'adminAi.dismissedBy', {
                  name: selected.reviewerName ?? t('adminAi.anAdmin'),
                  time: formatDateTime(selected.reviewedAt),
                })
              }}
            </p>
          </section>
        </section>
      </div>
    </div>
    <AppToast :message="toast" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import { useRoute } from 'vue-router'

import { adminAiApi } from '@/api/ai'
import { adminReportTone, aiFeatureLabel } from '@/components/admin/adminDisplay'
import AdminAiTabs from '@/components/admin/AdminAiTabs.vue'
import { useAiReportCounts } from '@/components/admin/useAiReportCounts'
import { REPORT_STATUS, reportReasonKey } from '@/components/ai/aiHelper'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource, useServerPage } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
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
const route = useRoute()
const { afterDecision } = useAiReportCounts()
const { toast, showToast } = useToast()

const page = ref(0)
const reportsResource = useResource(() => adminAiApi.reports({ page: page.value, size: PAGE_SIZE }))
const { items, totalPages, goToPage } = useServerPage(reportsResource, page)
const selectedId = ref<number | null>(null)
const selected = computed(() => items.value.find((report) => report.id === selectedId.value) ?? null)
const note = ref('')
const noteMissing = ref(false)
const deciding = ref<'RESOLVED' | 'DISMISSED' | null>(null)
const decisionError = ref<string | null>(null)

// The report the address names (the dashboard links to it), else the first one (an open one, when there are any).
watch(items, (list) => {
  if (list.some((report) => report.id === selectedId.value)) return
  const named = list.find((report) => report.id === Number(route.query.report))
  selectedId.value = (named ?? list[0])?.id ?? null
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

async function decide(decision: 'RESOLVED' | 'DISMISSED'): Promise<void> {
  const report = selected.value
  if (!report || deciding.value) return
  decisionError.value = null
  noteMissing.value = !note.value.trim()
  if (noteMissing.value) return
  deciding.value = decision
  try {
    await adminAiApi.reviewReport(report.id, decision, note.value.trim())
    showToast(
      t(decision === 'RESOLVED' ? 'adminAi.resolvedNotice' : 'adminAi.dismissedNotice', {
        name: report.studentName ?? '',
      }),
    )
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
