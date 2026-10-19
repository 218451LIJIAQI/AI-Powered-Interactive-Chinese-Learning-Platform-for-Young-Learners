<template>
  <div class="page-stack teacher-activities-page">
    <PageHeaderBlock
      :title="t('teacherActivities.title')"
      :description="t('teacherActivities.description')"
      icon="fun"
    />

    <DataNotice :using-fallback="usingFallback" :error="noticeError" :success="actionSuccess" />

    <TeacherApprovalLockPanel
      v-if="teacherApprovalLocked"
      :title="teacherApprovalTitle"
      :message="teacherApprovalMessage"
    />

    <section v-else class="teacher-activities-layout">
      <article class="panel teacher-activities-list-panel" aria-labelledby="teacher-activities-list-title">
        <div class="card-header">
          <div>
            <h2 id="teacher-activities-list-title" class="card-title">{{ t('teacherActivities.myActivities') }}</h2>
            <p class="card-subtitle">
              {{ t('teacherActivities.totalItems', { count: formatNumber(feed.totalItems) }) }}
            </p>
          </div>
          <button
            class="btn btn-secondary btn-sm"
            type="button"
            data-testid="teacher-activity-new"
            :disabled="mutating"
            @click="startNewDraft()"
          >
            <IconGlyph name="plus" :size="14" />
            {{ t('teacherActivities.newActivity') }}
          </button>
        </div>

        <div class="form-grid two teacher-activities-filters">
          <div class="field">
            <label class="field-label" for="teacher-activities-filter-type">
              {{ t('teacherActivities.filterType') }}
            </label>
            <select id="teacher-activities-filter-type" v-model="filters.type" class="select" @change="applyFilters">
              <option value="">{{ t('teacherActivities.allTypes') }}</option>
              <option v-for="item in activityTypeOptions" :key="item.value" :value="item.value">
                {{ item.label }}
              </option>
            </select>
          </div>
          <div class="field">
            <label class="field-label" for="teacher-activities-filter-status">
              {{ t('teacherActivities.filterStatus') }}
            </label>
            <select
              id="teacher-activities-filter-status"
              v-model="filters.status"
              class="select"
              @change="applyFilters"
            >
              <option value="">{{ t('teacherActivities.allStatuses') }}</option>
              <option v-for="status in statusOptions" :key="status" :value="status">
                {{ t(`governance.${contentStatusMessageKey(status)}`) }}
              </option>
            </select>
          </div>
        </div>

        <StatePanel
          v-if="feedResource.loading.value && !feed.items.length"
          tone="loading"
          :title="t('teacherActivities.loadingActivities')"
          :message="t('teacherActivities.loadingActivitiesHint')"
          compact
        />

        <ul v-else-if="feed.items.length" class="teacher-activities-list">
          <li v-for="item in feed.items" :key="`${item.type}-${item.id}`">
            <button
              type="button"
              class="teacher-activities-row"
              :class="{ 'is-active': item.id === selectedId && item.type === selectedType }"
              :aria-pressed="item.id === selectedId && item.type === selectedType"
              :disabled="mutating"
              @click="selectActivity(item)"
            >
              <span class="teacher-activities-row-head">
                <strong>{{ item.title || untitled(item.type) }}</strong>
                <ContentStatusBadge :status="item.status" :show-reason="false" />
              </span>
              <span class="teacher-activities-row-meta">
                <span>{{ activityTypeLabel(item.type) }}</span>
                <LearningStageTag :stage="item.learningStage" show-unset />
              </span>
              <small>{{ t('teacherActivities.updatedOn', { date: formatDateTime(item.updatedAt) }) }}</small>
            </button>
          </li>
        </ul>

        <StatePanel
          v-else-if="feedResource.error.value"
          tone="error"
          icon="alert"
          :title="t('teacherActivities.listErrorTitle')"
          :message="feedResource.error.value"
          compact
        >
          <template #actions>
            <button class="btn btn-secondary btn-sm" type="button" @click="feedResource.reload()">
              {{ t('teacherActivities.retry') }}
            </button>
          </template>
        </StatePanel>

        <StatePanel
          v-else
          icon="practice"
          :title="t('teacherActivities.noActivities')"
          :message="t('teacherActivities.noActivitiesHint')"
          compact
        />

        <PaginationBar
          v-if="feed.totalPages > 1"
          :label="t('teacherActivities.myActivities')"
          :info="t('teacherActivities.pageOf', { page: currentPage, total: feed.totalPages })"
          :previous-label="t('teacherActivities.previous')"
          :next-label="t('teacherActivities.next')"
          :previous-disabled="currentPage <= 1"
          :next-disabled="currentPage >= feed.totalPages"
          @previous="goToPage(currentPage - 1)"
          @next="goToPage(currentPage + 1)"
        />
      </article>

      <article
        class="panel teacher-activities-editor-panel"
        aria-labelledby="teacher-activities-editor-title"
        :aria-busy="loadingDetail"
      >
        <div class="card-header">
          <div>
            <h2 id="teacher-activities-editor-title" class="card-title">{{ editorTitle }}</h2>
            <p v-if="selectedSummary" class="card-subtitle">
              {{ t('teacherActivities.updatedOn', { date: formatDateTime(selectedSummary.updatedAt) }) }}
            </p>
          </div>
          <ContentStatusBadge v-if="selectedSummary" :status="selectedSummary.status" :show-reason="false" />
        </div>

        <DataNotice v-if="previewReadOnly" :info="t('teacherActivities.previewReadOnly')" />

        <ContentReviewNotice :status="selectedStatus ?? 'DRAFT'" :rejection-reason="selectedSummary?.rejectionReason" />

        <ActivityEditorFields
          :form="form"
          :type-locked="hasSelectedRecord || busy"
          :disabled="editLocked || busy"
          @type-change="changeDraftType"
        >
          <template #story-tools>
            <TeacherAiDraftPanel
              mode="story"
              :disabled="editLocked || busy"
              @story-draft="applyStoryDraftToForm"
              @forbidden="markApprovalLocked"
            />
          </template>
        </ActivityEditorFields>

        <div class="card-footer teacher-activities-actions">
          <button
            v-if="selectedStatus === 'DRAFT'"
            class="btn btn-danger-soft teacher-activities-delete"
            type="button"
            :disabled="busy"
            :aria-busy="deleting"
            @click="deleteDraft"
          >
            <IconGlyph v-if="!deleting" name="trash" :size="16" />
            {{ deleting ? t('teacherActivities.deleting') : t('teacherActivities.deleteDraft') }}
          </button>
          <button class="btn btn-ghost" type="button" :disabled="busy" @click="startNewDraft(form.type)">
            <IconGlyph name="plus" :size="16" />
            {{ t('teacherActivities.newActivity') }}
          </button>
          <button
            v-if="canSubmit"
            class="btn btn-secondary"
            type="button"
            :disabled="busy"
            :aria-busy="submitting"
            data-testid="teacher-activity-submit"
            @click="submitActivity"
          >
            {{ submitting ? t('governance.submitting') : submitLabel }}
          </button>
          <button
            class="btn btn-primary"
            type="button"
            :disabled="busy || editLocked"
            :aria-busy="saving"
            data-testid="teacher-activity-save"
            @click="saveActivity"
          >
            {{ saveLabel }}
          </button>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { teacherActivityGateway, teacherApi } from '@/api/services'
import ActivityEditorFields from '@/components/activities/ActivityEditorFields.vue'
import TeacherAiDraftPanel from '@/components/ai/TeacherAiDraftPanel.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import ContentReviewNotice from '@/components/content/ContentReviewNotice.vue'
import ContentStatusBadge from '@/components/content/ContentStatusBadge.vue'
import LearningStageTag from '@/components/content/LearningStageTag.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import TeacherApprovalLockPanel from '@/components/teacher/TeacherApprovalLockPanel.vue'
import { useResource } from '@/composables/useResource'
import { useTeacherAccess } from '@/composables/useTeacherAccess'
import { teacherActivitySummaries } from '@/demo/data'
import { adminActivityLibraryMessages } from '@/plugins/adminActivityLibraryMessages'
import { contentGovernanceMessages } from '@/plugins/contentGovernanceMessages'
import { teacherActivitiesMessages } from '@/plugins/teacherActivitiesMessages'
import { teacherAiDraftMessages } from '@/plugins/teacherAiDraftMessages'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import type {
  AdminActivitySummaryResponse,
  AdminActivityType,
  AiStoryGeneratorResponse,
  ContentStatus,
  PageResponse,
} from '@/types/api'
import {
  ACTIVITY_TYPES,
  activityFormFromDetail,
  activityTypeLabelKey,
  buildActivityPayload,
  createEmptyActivityForm,
  validateActivityForm,
  type ActivityEditorForm,
} from '@/utils/activityForm'
import { applyStoryDraft } from '@/utils/aiDraft'
import { resolveApiError } from '@/utils/apiError'
import {
  canSubmitForReview,
  contentStatusMessageKey,
  editReturnsToReview,
  isTeacherEditLocked,
} from '@/utils/contentStatus'
import { formatDateTime, formatNumber } from '@/utils/format'
import { normalizeLearningStage } from '@/utils/learningStage'

const PAGE_SIZE = 12

const { t } = useI18n({
  messages: {
    en: {
      teacherActivities: teacherActivitiesMessages.en,
      adminActivityLibrary: adminActivityLibraryMessages.en,
      governance: contentGovernanceMessages.en,
      teacherAiDraft: teacherAiDraftMessages.en,
    },
    ms: {
      teacherActivities: teacherActivitiesMessages.ms,
      adminActivityLibrary: adminActivityLibraryMessages.ms,
      governance: contentGovernanceMessages.ms,
      teacherAiDraft: teacherAiDraftMessages.ms,
    },
    'zh-CN': {
      teacherActivities: teacherActivitiesMessages['zh-CN'],
      adminActivityLibrary: adminActivityLibraryMessages['zh-CN'],
      governance: contentGovernanceMessages['zh-CN'],
      teacherAiDraft: teacherAiDraftMessages['zh-CN'],
    },
  },
})
const auth = useAuthStore()
const ui = useUiStore()
const { teacherApprovalLocked, teacherApprovalTitle, teacherApprovalMessage, guardTeacherRequest, markApprovalLocked } =
  useTeacherAccess()

const emptyFeed: PageResponse<AdminActivitySummaryResponse> = {
  items: [],
  page: 0,
  size: PAGE_SIZE,
  totalItems: 0,
  totalPages: 0,
}
const statusOptions: ContentStatus[] = ['DRAFT', 'SUBMITTED', 'PUBLISHED', 'REJECTED', 'ARCHIVED']

const filters = reactive({ type: '' as AdminActivityType | '', status: '' as ContentStatus | '' })
const currentPage = ref(1)
const selectedType = ref<AdminActivityType>('FLASHCARD')
const selectedId = ref<number | null>(null)
const form = reactive<ActivityEditorForm>(createEmptyActivityForm('FLASHCARD'))
const savedSnapshot = ref(JSON.stringify(form))
const loadingDetail = ref(false)
const saving = ref(false)
const submitting = ref(false)
const deleting = ref(false)
// NFR-PERF-06: no switching records while a save / submit / delete runs (it would apply to the wrong record).
const mutating = computed(() => saving.value || submitting.value || deleting.value)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
/** Status of a record saved in this session (the list may lag behind until it reloads). */
const localStatus = ref<{ id: number; type: AdminActivityType; status: ContentStatus } | null>(null)
/** Status of the list row the teacher opened (used while that row is not on the current page). */
const selectedListStatus = ref<ContentStatus | null>(null)
let detailRequest = 0

const feedResource = useResource(
  () =>
    teacherApprovalLocked.value
      ? Promise.resolve(emptyFeed)
      : guardTeacherRequest(() =>
          teacherApi.activities({
            type: filters.type || undefined,
            status: filters.status || undefined,
            page: currentPage.value - 1,
            size: PAGE_SIZE,
          }),
        ),
  teacherActivitySummaries,
)

const feed = computed(() => feedResource.data.value ?? emptyFeed)
const usingFallback = computed(() => feedResource.usingFallback.value)
const previewReadOnly = computed(() => usingFallback.value || (auth.isPreviewSession && !auth.isAuthenticated))
// A feed load error with no rows is shown (with a retry) in the list panel; keep it out of the notice then.
const noticeError = computed(() => actionError.value || (feed.value.items.length ? feedResource.error.value : null))
const hasSelectedRecord = computed(() => selectedId.value !== null)
const selectedSummary = computed<AdminActivitySummaryResponse | null>(() => {
  if (selectedId.value === null) return null
  const summary = feed.value.items.find((item) => item.id === selectedId.value && item.type === selectedType.value)
  if (!summary) return null
  const override =
    localStatus.value && localStatus.value.id === summary.id && localStatus.value.type === summary.type
      ? localStatus.value.status
      : null
  return override ? { ...summary, status: override } : summary
})
/**
 * Review state of the open record. Falls back to the last saved / selected status when the record is not in the
 * current list page (e.g. a filter hides it after saving), so a SUBMITTED item never looks editable.
 */
const selectedStatus = computed<ContentStatus | null>(() => {
  if (selectedId.value === null) return null
  if (selectedSummary.value) return selectedSummary.value.status
  const local = localStatus.value
  if (local && local.id === selectedId.value && local.type === selectedType.value) return local.status
  return selectedListStatus.value
})
const busy = computed(() => loadingDetail.value || saving.value || submitting.value || deleting.value)
const editLocked = computed(() => isTeacherEditLocked(selectedStatus.value))
const canSubmit = computed(() => hasSelectedRecord.value && canSubmitForReview(selectedStatus.value))
const submitLabel = computed(() =>
  selectedStatus.value === 'REJECTED' ? t('governance.resubmitForReview') : t('governance.submitForReview'),
)
const saveLabel = computed(() => {
  if (saving.value) return t('teacherActivities.saving')
  return hasSelectedRecord.value ? t('teacherActivities.saveChanges') : t('teacherActivities.saveDraft')
})
const editorTitle = computed(() =>
  hasSelectedRecord.value
    ? t('teacherActivities.editorEdit', { type: activityTypeLabel(form.type) })
    : t('teacherActivities.editorNew', { type: activityTypeLabel(form.type) }),
)
const activityTypeOptions = computed(() => ACTIVITY_TYPES.map((value) => ({ value, label: activityTypeLabel(value) })))

function activityTypeLabel(type: AdminActivityType): string {
  return t(`adminActivityLibrary.${activityTypeLabelKey(type)}`)
}

function untitled(type: AdminActivityType): string {
  return t('teacherActivities.untitled', { type: activityTypeLabel(type) })
}

function displayTitle(): string {
  return form.title.trim() || selectedSummary.value?.title || untitled(form.type)
}

function clearFeedback(): void {
  actionError.value = null
  actionSuccess.value = null
}

function applyForm(next: ActivityEditorForm): void {
  Object.assign(form, next)
  savedSnapshot.value = JSON.stringify(form)
}

function startNewDraft(type: AdminActivityType = 'FLASHCARD'): void {
  // Drops a detail response that is still in flight for the previous selection.
  detailRequest++
  loadingDetail.value = false
  selectedId.value = null
  selectedType.value = type
  selectedListStatus.value = null
  localStatus.value = null
  applyForm(createEmptyActivityForm(type))
  clearFeedback()
}

function changeDraftType(type: AdminActivityType): void {
  if (!hasSelectedRecord.value) startNewDraft(type)
}

function applyFilters(): void {
  currentPage.value = 1
  void feedResource.reload()
}

function goToPage(page: number): void {
  currentPage.value = page
  void feedResource.reload()
}

async function selectActivity(item: AdminActivitySummaryResponse): Promise<void> {
  if (mutating.value) return
  clearFeedback()
  selectedType.value = item.type
  selectedId.value = item.id
  selectedListStatus.value = item.status
  localStatus.value = null

  if (previewReadOnly.value) {
    applyForm({
      ...createEmptyActivityForm(item.type),
      title: item.title,
      learningStage: normalizeLearningStage(item.learningStage) ?? '',
      hskLevel: item.hskLevel ?? null,
      difficulty: item.difficulty ?? '',
      topic: item.topic ?? '',
      tagsText: item.tags.join(', '),
      xpReward: item.xpReward ?? null,
      estimatedSeconds: item.estimatedSeconds ?? null,
    })
    return
  }

  // Only the latest selection may fill the editor (a slower earlier response must not overwrite it).
  const request = ++detailRequest
  loadingDetail.value = true
  try {
    const detail = await guardTeacherRequest(() => teacherActivityGateway.detail(item.type, item.id))
    if (request === detailRequest) applyForm(activityFormFromDetail(item.type, detail))
  } catch (reason) {
    if (request === detailRequest) actionError.value = resolveApiError(reason, t('teacherActivities.loadError'))
  } finally {
    if (request === detailRequest) loadingDetail.value = false
  }
}

function applyStoryDraftToForm(draft: AiStoryGeneratorResponse): void {
  Object.assign(
    form,
    applyStoryDraft(form, draft, { language: ui.locale, answerLabel: t('teacherAiDraft.answerLabel') }),
  )
}

/** Saves the editor (create or update) and returns the saved status, or null when nothing was saved. */
async function persistForm(): Promise<{ id: number; status: ContentStatus } | null> {
  const validationKey = validateActivityForm(form)
  if (validationKey) {
    actionError.value = t(`adminActivityLibrary.${validationKey}`)
    return null
  }

  const type = form.type
  const payload = buildActivityPayload(form)
  const response =
    selectedId.value !== null
      ? await guardTeacherRequest(() => teacherActivityGateway.update(type, selectedId.value as number, payload))
      : await guardTeacherRequest(() => teacherActivityGateway.create(type, payload))

  selectedType.value = type
  selectedId.value = response.id
  localStatus.value = { id: response.id, type, status: response.status }
  applyForm(activityFormFromDetail(type, response))
  return { id: response.id, status: response.status }
}

async function saveActivity(): Promise<void> {
  if (busy.value || editLocked.value) return
  clearFeedback()
  if (previewReadOnly.value) {
    actionError.value = t('teacherActivities.previewReadOnly')
    return
  }

  const wasPublished = editReturnsToReview(selectedStatus.value)
  if (wasPublished && !window.confirm(t('governance.confirmEditPublished', { title: displayTitle() }))) return

  const isNew = !hasSelectedRecord.value
  saving.value = true
  try {
    const saved = await persistForm()
    if (!saved) return
    actionSuccess.value = isNew
      ? t('teacherActivities.created', { type: activityTypeLabel(form.type) })
      : wasPublished
        ? t('teacherActivities.updatedResubmitted')
        : t('teacherActivities.updated')
    await feedResource.reload()
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherActivities.saveError'))
  } finally {
    saving.value = false
  }
}

async function submitActivity(): Promise<void> {
  if (busy.value || selectedId.value === null || !canSubmit.value) return
  clearFeedback()
  if (previewReadOnly.value) {
    actionError.value = t('teacherActivities.previewReadOnly')
    return
  }
  const title = displayTitle()
  if (!window.confirm(t('governance.confirmSubmit', { title }))) return

  submitting.value = true
  try {
    if (JSON.stringify(form) !== savedSnapshot.value) {
      const saved = await persistForm()
      if (!saved) return
    }
    const type = selectedType.value
    const id = selectedId.value
    const response = await guardTeacherRequest(() => teacherActivityGateway.submit(type, id))
    localStatus.value = { id, type, status: response.status }
    actionSuccess.value = t('governance.submittedSuccess', { title })
    await feedResource.reload()
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('governance.submitError'))
  } finally {
    submitting.value = false
  }
}

async function deleteDraft(): Promise<void> {
  if (busy.value || selectedId.value === null) return
  clearFeedback()
  if (previewReadOnly.value) {
    actionError.value = t('teacherActivities.previewReadOnly')
    return
  }
  if (!window.confirm(t('teacherActivities.confirmDelete', { title: displayTitle() }))) return

  deleting.value = true
  try {
    const type = selectedType.value
    const id = selectedId.value
    await guardTeacherRequest(() => teacherActivityGateway.remove(type, id))
    startNewDraft(type)
    actionSuccess.value = t('teacherActivities.deleted')
    await feedResource.reload()
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherActivities.deleteError'))
  } finally {
    deleting.value = false
  }
}
</script>

<style scoped>
/* List + editor: the editor gets the room; below 1200px the list sits above the editor. */
.teacher-activities-layout {
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
  gap: var(--grid-gap);
  align-items: start;
}

.teacher-activities-list-panel,
.teacher-activities-editor-panel {
  display: grid;
  gap: var(--section-gap);
  min-width: 0;
}

.teacher-activities-list {
  display: grid;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.teacher-activities-row {
  display: grid;
  gap: var(--space-1-5);
  width: 100%;
  min-width: 0;
  padding: var(--space-3) var(--space-3-5);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
  color: var(--text-primary);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background var(--duration-fast) var(--ease-standard);
}

.teacher-activities-row:hover {
  border-color: var(--border-interactive);
  background: var(--surface-hover);
}

.teacher-activities-row.is-active {
  border-color: var(--color-primary-300);
  background: var(--surface-selected);
}

.teacher-activities-row-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-2);
  min-width: 0;
}

.teacher-activities-row-head strong {
  min-width: 0;
  font-size: var(--font-size-md);
  line-height: var(--line-height-snug);
  overflow-wrap: anywhere;
}

.teacher-activities-row-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-1) var(--space-2);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.teacher-activities-row small {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
}

.teacher-activities-delete {
  margin-right: auto;
}

@media (max-width: 1200px) {
  .teacher-activities-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  /* The list is short: show it as a grid of cards above the editor. */
  .teacher-activities-list {
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 260px), 1fr));
  }
}

@media (max-width: 640px) {
  .teacher-activities-delete {
    margin-right: 0;
  }
}
</style>
