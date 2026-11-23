<template>
  <div class="page-stack">
    <PageHeaderBlock :title="t('adminAi.reportsTitle')" hide-title />
    <AdminAiTabs />
    <DataNotice :error="decisionError" />

    <StatePanel
      v-if="reportsResource.loading.value"
      heading-tag="h2"
      tone="loading"
      spacious
      :title="t('adminAi.reportsLoading')"
      :message="t('adminAi.reportsLoadingHint')"
    />
    <StatePanel
      v-else-if="reportsResource.error.value"
      heading-tag="h2"
      tone="error"
      icon="alert"
      spacious
      :title="t('adminAi.reportsError')"
      :message="reportsResource.error.value"
      @retry="reportsResource.reload"
    />
    <div v-else-if="!items.length && selectedId === null" class="panel">
      <StatePanel
        heading-tag="h2"
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
              @click="select(report.id)"
            >
              <span class="admin-select-row">
                <strong class="admin-select-title">{{ t(reportReasonKey(report.reasonCategory)) }}</strong>
                <StatusPill :label="t(REPORT_STATUS[report.status].labelKey)" :tone="adminReportTone(report.status)" />
              </span>
              <span class="admin-meta">{{ aiFeatureLabel(report.featureKey, t) }} · {{ report.studentName }}</span>
              <span class="admin-meta">{{ formatDateTime(report.createdAt) }}</span>
            </button>
          </li>
        </ul>
        <PaginationBar
          icon-only
          :page="displayedPage"
          :total-pages="totalPages"
          :label="t('adminAi.reports')"
          @update:page="goToReportPage"
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
            <StatusPill :label="t(REPORT_STATUS[selected.status].labelKey)" :tone="adminReportTone(selected.status)" />
          </header>

          <dl class="kv-list admin-ai-facts">
            <dt>{{ t('adminAi.student') }}</dt>
            <dd>{{ selected.studentName }} · {{ selected.studentEmail }}</dd>
            <dt>{{ t('adminAi.feature') }}</dt>
            <dd>{{ aiFeatureLabel(selected.featureKey, t) }}</dd>
          </dl>

          <section class="admin-ai-exchange" :aria-label="t('adminAi.exchangeLabel')">
            <div class="admin-ai-said admin-ruled">
              <h3 class="admin-meta">{{ t(snapshotLabels.prompt) }}</h3>
              <p class="is-strong">{{ selected.promptSnapshot || t('adminAi.notAttached') }}</p>
            </div>
            <div class="admin-ai-said admin-ruled">
              <h3 class="admin-meta">{{ t(snapshotLabels.answer) }}</h3>
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
                ref="noteField"
                v-model="note"
                class="textarea"
                rows="4"
                required
                :disabled="isDecidingSelected"
                :maxlength="MAX_NOTE"
                :placeholder="t('adminAi.notePlaceholder')"
                :aria-describedby="noteError ? `${baseId}-note-error ${baseId}-note-hint` : `${baseId}-note-hint`"
                :aria-invalid="noteError ? 'true' : undefined"
                @blur="checkNoteOnBlur"
              />
              <p :id="`${baseId}-note-hint`" class="field-hint">
                {{ t('adminAi.noteHint', { name: selected.studentName }) }}
              </p>
              <p v-if="noteError" :id="`${baseId}-note-error`" class="field-error" role="alert">{{ noteError }}</p>
            </div>
            <div class="admin-ai-decision">
              <p class="admin-meta">{{ t('adminAi.decisionHint') }}</p>
              <!-- A press on a decision leaves focus in the note: its check on blur would move these buttons mid-click. -->
              <div class="btn-group">
                <button
                  class="btn btn-secondary"
                  type="button"
                  :disabled="isDecidingSelected || Boolean(noteError)"
                  :aria-busy="selectedDecision === 'DISMISSED'"
                  @mousedown.prevent
                  @click="decide('DISMISSED')"
                >
                  <IconGlyph name="close" :size="16" />
                  <span>{{ t('adminAi.dismiss') }}</span>
                </button>
                <button
                  class="btn btn-primary"
                  type="button"
                  :disabled="isDecidingSelected || Boolean(noteError)"
                  :aria-busy="selectedDecision === 'RESOLVED'"
                  @mousedown.prevent
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
                  name: selected.reviewerName,
                  time: formatDateTime(selected.reviewedAt),
                })
              }}
            </p>
          </section>
        </section>
        <StatePanel
          v-else-if="offPageId !== null && detail.loading.value"
          class="panel"
          tone="loading"
          :title="t('adminAi.reportsLoading')"
          :message="t('adminAi.reportsLoadingHint')"
        />
        <StatePanel
          v-else-if="offPageId !== null && detail.error.value"
          class="panel"
          tone="error"
          icon="alert"
          :title="t('adminAi.reportsError')"
          :message="detail.error.value"
          @retry="detail.reload"
        />
      </div>
    </div>
    <AppToast :message="toast" />
  </div>
</template>

<script setup lang="ts">
import { computed, inject, onBeforeUnmount, reactive, ref, shallowRef, useId, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { adminAiApi } from '@/api/ai'
import { adminReportTone, aiFeatureLabel } from '@/components/admin/adminDisplay'
import AdminAiTabs from '@/components/admin/AdminAiTabs.vue'
import { REPORT_STATUS, reportReasonKey } from '@/components/ai/aiHelper'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { navigationCountsKey } from '@/composables/navigationCounts'
import { pauseDuringNavigation } from '@/composables/usePageNavigation'
import { useFieldCheck } from '@/composables/useFieldCheck'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { notifyProgressChanged } from '@/composables/useProgressRefresh'
import { useRecordById, useResource, useServerPage } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { adminAiMessages } from '@/plugins/adminAiMessages'
import { opsLabelMessages } from '@/plugins/opsLabelMessages'
import type { AdminAiResponseReportResponse, AiFeatureKey } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { formatDateTime } from '@/utils/format'
import { parseRecordId } from '@/utils/recordId'
import '@/styles/admin-shared.css'
import '@/styles/ops-admin.css'

/**
 * Admin AI reports: open reports first; the detail shows who reported which feature, what was asked and answered and
 * the student's note. The admin resolves (the answer was wrong and they acted) or dismisses (no change needed: the
 * answer was fine or is already fixed) with a note the student reads in My reports; the student is notified and the
 * decision is audited.
 */
const SNAPSHOT_LABELS: Readonly<Partial<Record<AiFeatureKey, { prompt: string; answer: string }>>> = {
  CHAT_TUTOR: { prompt: 'adminAi.snapshotQuestion', answer: 'adminAi.snapshotAnswer' },
  XIAOMEI_CONVERSATION: { prompt: 'adminAi.snapshotConversation', answer: 'adminAi.snapshotReply' },
  QUIZ_GENERATOR: { prompt: 'adminAi.snapshotTopic', answer: 'adminAi.snapshotQuiz' },
  STORY_GENERATOR: { prompt: 'adminAi.snapshotTopic', answer: 'adminAi.snapshotStory' },
  PRACTICE_CHECK: { prompt: 'adminAi.snapshotChecked', answer: 'adminAi.snapshotScore' },
}
const DEFAULT_SNAPSHOT_LABELS = { prompt: 'adminAi.snapshotRequest', answer: 'adminAi.snapshotAnswer' }
const PAGE_SIZE = 10
const MAX_NOTE = 1000

type Decision = 'RESOLVED' | 'DISMISSED'

const { t } = useLocalMessages({
  adminAi: adminAiMessages,
  opsLabels: opsLabelMessages,
})
const baseId = useId()
const route = useRoute()
const router = useRouter()
const navCounts = inject(navigationCountsKey, null)
const { toast, showToast } = useToast()

const page = ref(0)
const reportsResource = useResource(() => adminAiApi.reports({ page: page.value, size: PAGE_SIZE }))
const { items, displayedPage, totalPages, goToPage } = useServerPage(reportsResource, page)
const linkedId = computed(() => parseRecordId(route.query.report))
const selectedId = ref<number | null>(null)
const cachedReviewed = shallowRef<AdminAiResponseReportResponse | null>(null)
let pendingPageSwitch = false
const selectedRow = computed(() => items.value.find((report) => report.id === selectedId.value))
// Queue rows already have the full detail. Only a link outside the current page needs another request.
const offPageId = computed(() => {
  if (!reportsResource.data.value || selectedId.value !== linkedId.value || selectedRow.value) return null
  if (cachedReviewed.value?.id === selectedId.value) return null
  return linkedId.value
})
const detail = useRecordById(offPageId, adminAiApi.report)
const selected = computed(() => {
  if (cachedReviewed.value?.id === selectedId.value) return cachedReviewed.value
  if (selectedRow.value) return selectedRow.value
  const linked = detail.data.value
  return linked?.id === offPageId.value ? linked : null
})
const snapshotLabels = computed(
  () => (selected.value && SNAPSHOT_LABELS[selected.value.featureKey]) ?? DEFAULT_SNAPSHOT_LABELS,
)
const note = ref('')
const noteField = ref<HTMLTextAreaElement | null>(null)
/** Both decisions need the note, so its missing-note message names both, whichever was pressed. */
const {
  error: noteError,
  validate: validateNote,
  validateOnBlur: checkNoteOnBlur,
  reset: resetNoteError,
} = useFieldCheck(note, (text) => (text.trim() ? null : t('adminAi.noteRequired')))
const deciding = reactive(new Map<number, Decision>())
const selectedDecision = computed<Decision | null>(() =>
  selected.value === null ? null : (deciding.get(selected.value.id) ?? null),
)
const isDecidingSelected = computed(() => selectedDecision.value !== null)
const decisionError = ref<string | null>(null)
let selectionVersion = 0
let expectedQueryId: number | null | undefined
let disposed = false
onBeforeUnmount(() => {
  disposed = true
  selectionVersion += 1
})

watch(
  linkedId,
  (id) => {
    if (expectedQueryId !== undefined && id !== expectedQueryId) return
    expectedQueryId = undefined
    pendingPageSwitch = false
    select(id ?? items.value[0]?.id ?? null, { syncQuery: false })
  },
  { immediate: true },
)
watch(reportsResource.error, (error) => {
  if (error !== null) pendingPageSwitch = false
})
const selectFromPage = watch(items, (list) => {
  if (pendingPageSwitch) {
    pendingPageSwitch = false
    select(list[0]?.id ?? null, { syncQuery: true })
    return
  }
  if (selectedId.value !== null && (selectedId.value === linkedId.value || selectedRow.value)) return
  // Keep any active decision error if a failed decision's reload shifted the closed report off this page.
  select(list[0]?.id ?? null, { syncQuery: false, keepError: decisionError.value !== null })
})
pauseDuringNavigation(selectFromPage)

/** A note belongs to the report it was written for, so another report starts with an empty one. */
function select(id: number | null, { syncQuery = true, keepError = false } = {}): void {
  if (id === selectedId.value) return
  selectionVersion += 1
  selectedId.value = id
  cachedReviewed.value = null
  note.value = ''
  resetNoteError()
  if (!keepError) decisionError.value = null
  if (syncQuery && route.query.report !== undefined && (id !== linkedId.value || expectedQueryId !== undefined)) {
    expectedQueryId = id
    void router.replace({ query: { ...route.query, report: id === null ? undefined : String(id) } }).finally(() => {
      if (expectedQueryId === id) expectedQueryId = undefined
    })
  }
}

function goToReportPage(next: number): void {
  // Keep the current detail visible until the next page loads; watch(items) selects the first row on arrival.
  pendingPageSwitch = true
  goToPage(next)
}

async function decide(decision: Decision): Promise<void> {
  const report = selected.value
  if (!report || deciding.has(report.id)) return
  const version = selectionVersion
  decisionError.value = null
  if (!validateNote()) {
    noteField.value?.focus()
    return
  }
  deciding.set(report.id, decision)
  try {
    const reviewed = await adminAiApi.reviewReport(report.id, decision, note.value.trim())
    // The sidebar badge counts outlive this page: they catch up even when the admin has already moved on.
    notifyProgressChanged()
    if (disposed) return
    showToast(
      t(decision === 'RESOLVED' ? 'adminAi.resolvedNotice' : 'adminAi.dismissedNotice', { name: report.studentName }),
    )
    if (version === selectionVersion) note.value = ''
    if (selectedId.value === report.id) cachedReviewed.value = reviewed
    await reportsResource.reload()
  } catch (reason) {
    if (disposed) return
    if (selectedId.value === report.id) decisionError.value = resolveApiError(reason, t('adminAi.decisionError'))
    // Another admin may have closed the report meanwhile ("already closed"): show it as the server has it now.
    void navCounts?.reload()
    const wasOffPage = offPageId.value === report.id
    await reportsResource.reload()
    if (!disposed && wasOffPage && offPageId.value === report.id) await detail.reload()
  } finally {
    deciding.delete(report.id)
  }
}
</script>
