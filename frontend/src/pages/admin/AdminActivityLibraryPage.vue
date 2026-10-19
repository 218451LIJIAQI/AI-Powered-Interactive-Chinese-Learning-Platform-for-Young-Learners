<template>
  <div class="page-stack admin-activity-page">
    <PageHeaderBlock
      :title="t('adminActivityLibrary.title')"
      :description="t('adminActivityLibrary.description')"
      icon="practice"
    >
      <template #actions>
        <button
          class="btn btn-secondary"
          type="button"
          :disabled="pageBusy || previewReadOnly"
          :aria-busy="pageBusy"
          @click="reloadFeed('keep')"
        >
          <IconGlyph name="refresh" :size="16" />
          <span>{{ refreshFeedLabel }}</span>
        </button>
      </template>
    </PageHeaderBlock>

    <DataNotice :error="pageError" :success="actionSuccess" />

    <form class="panel admin-filter-bar" @submit.prevent="applyFilters">
      <div class="field">
        <label for="admin-activity-filter-type">{{ t('adminActivityLibrary.activityType') }}</label>
        <select id="admin-activity-filter-type" v-model="filters.type" class="select">
          <option value="">{{ t('adminActivityLibrary.allTypes') }}</option>
          <option v-for="item in activityTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>
      </div>

      <div class="field">
        <label for="admin-activity-filter-status">{{ t('adminActivityLibrary.status') }}</label>
        <select id="admin-activity-filter-status" v-model="filters.status" class="select">
          <option value="">{{ t('adminActivityLibrary.allStatuses') }}</option>
          <option v-for="status in statusFilterOptions" :key="status" :value="status">
            {{ activityStatusLabel(status) }}
          </option>
        </select>
      </div>

      <div class="field">
        <label for="admin-activity-filter-difficulty">{{ t('adminActivityLibrary.difficulty') }}</label>
        <select id="admin-activity-filter-difficulty" v-model="filters.difficulty" class="select">
          <option value="">{{ t('adminActivityLibrary.allLevels') }}</option>
          <option v-for="item in difficultyOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>
      </div>

      <div class="field">
        <label for="admin-activity-filter-keyword">{{ t('adminActivityLibrary.keyword') }}</label>
        <div class="input-with-icon">
          <IconGlyph class="input-icon" name="search" :size="16" />
          <input
            id="admin-activity-filter-keyword"
            v-model="filters.keyword"
            class="input"
            type="search"
            maxlength="200"
            :placeholder="t('adminActivityLibrary.keywordPlaceholder')"
          />
        </div>
      </div>

      <div class="field">
        <label for="admin-activity-filter-topic">{{ t('adminActivityLibrary.topic') }}</label>
        <input
          id="admin-activity-filter-topic"
          v-model="filters.topic"
          class="input"
          maxlength="200"
          :placeholder="t('adminActivityLibrary.topicPlaceholder')"
        />
      </div>

      <div class="admin-filter-actions">
        <button class="btn btn-ghost" type="button" :disabled="pageBusy" @click="resetFilters">
          {{ t('adminActivityLibrary.reset') }}
        </button>
        <button class="btn btn-primary" type="submit" :disabled="pageBusy" :aria-busy="pageBusy">
          {{ pageBusy ? t('adminActivityLibrary.loading') : t('adminActivityLibrary.applyFilters') }}
        </button>
      </div>
    </form>

    <section class="admin-kpi-grid admin-kpi-grid--5">
      <AdminKpiTile
        v-for="item in statCards"
        :key="item.label"
        :label="item.label"
        :value="item.value"
        :hint="item.copy"
        :icon="item.icon"
        :tone="item.tone"
      />
    </section>

    <section class="admin-split admin-split--even">
      <article class="panel admin-panel">
        <div class="card-header">
          <div>
            <h2 class="card-title">{{ t('adminActivityLibrary.unifiedFeed') }}</h2>
            <p class="card-subtitle">
              {{ t('adminActivityLibrary.totalItems', { count: formatNumber(feed.totalItems) }) }}
            </p>
          </div>
          <button class="btn btn-secondary btn-sm" type="button" :disabled="previewReadOnly" @click="startNewDraft()">
            <IconGlyph name="plus" :size="14" />
            <span>{{ t('adminActivityLibrary.newDraft') }}</span>
          </button>
        </div>

        <StatePanel
          v-if="previewReadOnly"
          compact
          icon="practice"
          :title="t('adminActivityLibrary.liveAdminRequired')"
          :message="t('adminActivityLibrary.liveAdminMessage')"
        />

        <template v-else>
          <ul v-if="feed.items.length" class="admin-queue">
            <li v-for="item in feed.items" :key="`${item.type}-${item.id}`">
              <button
                type="button"
                class="admin-queue-item"
                :class="{ 'is-selected': item.id === selectedId && item.type === selectedType }"
                :aria-pressed="item.id === selectedId && item.type === selectedType"
                @click="selectActivity(item.type, item.id)"
              >
                <span class="admin-queue-copy">
                  <span class="admin-queue-title">
                    <strong>{{ item.title || fallbackActivityTitle(item.type) }}</strong>
                    <ContentStatusBadge :status="item.status" :show-reason="false" />
                  </span>
                  <span class="admin-meta-row">
                    <span class="tag">{{ activityTypeLabel(item.type) }}</span>
                    <LearningStageTag :stage="item.learningStage" />
                  </span>
                  <span class="admin-feed-meta">{{ activitySummaryMeta(item) }}</span>
                </span>
              </button>
            </li>
          </ul>

          <StatePanel
            v-else-if="feedResource.loading.value"
            compact
            tone="loading"
            icon="progress"
            :title="t('adminActivityLibrary.loading')"
            :message="t('adminActivityLibrary.unifiedFeed')"
          />
          <StatePanel
            v-else-if="feedResource.error.value"
            compact
            tone="error"
            icon="alert"
            :title="t('adminActivityLibrary.loadErrorTitle')"
            :message="feedResource.error.value"
          >
            <template #actions>
              <button class="btn btn-primary" type="button" @click="reloadFeed('keep')">
                {{ t('adminActivityLibrary.retry') }}
              </button>
            </template>
          </StatePanel>
          <StatePanel
            v-else
            compact
            icon="practice"
            :title="t('adminActivityLibrary.noMatches')"
            :message="t('adminActivityLibrary.noMatchesMessage')"
          >
            <template #actions>
              <button class="btn btn-secondary" type="button" @click="resetFilters">
                {{ t('adminActivityLibrary.reset') }}
              </button>
            </template>
          </StatePanel>

          <PaginationBar
            v-if="feed.totalPages > 1"
            :info="t('adminActivityLibrary.pageOf', { page: currentPage, total: feed.totalPages })"
            :previous-label="t('adminActivityLibrary.previous')"
            :next-label="t('adminActivityLibrary.next')"
            :previous-disabled="currentPage <= 1 || pageBusy"
            :next-disabled="currentPage >= feed.totalPages || pageBusy"
            @previous="goToPage(currentPage - 1)"
            @next="goToPage(currentPage + 1)"
          />
        </template>
      </article>

      <article class="panel admin-panel" :aria-busy="detailLoading">
        <div class="card-header">
          <div>
            <h2 class="card-title">{{ editorTitle }}</h2>
            <p class="card-subtitle">{{ editorSubtitle }}</p>
          </div>
          <div class="admin-activity-head-actions">
            <ContentStatusBadge
              v-if="hasSelectedRecord && selectedSummary"
              :status="selectedSummary.status"
              :show-reason="false"
            />
            <button
              class="btn btn-ghost btn-sm"
              type="button"
              :disabled="previewReadOnly"
              @click="startNewDraft(form.type)"
            >
              {{ t('adminActivityLibrary.newType', { type: activityTypeLabel(form.type) }) }}
            </button>
          </div>
        </div>

        <StatePanel
          v-if="previewReadOnly"
          compact
          icon="verification"
          :title="t('adminActivityLibrary.readOnlyPreview')"
          :message="t('adminActivityLibrary.readOnlyPreviewMessage')"
        />

        <template v-else>
          <div
            v-if="hasSelectedRecord && selectedSummary && selectedSummary.status === 'SUBMITTED'"
            class="alert alert-warning admin-activity-review-hint"
            role="note"
          >
            <IconGlyph name="clip" :size="16" />
            <span>{{ t('adminActivityLibrary.reviewQueueHint') }}</span>
            <RouterLink class="btn btn-link" :to="reviewQueueLink">
              {{ t('adminActivityLibrary.openReviewQueue') }}
            </RouterLink>
          </div>

          <ContentStatusBadge
            v-if="hasSelectedRecord && selectedSummary && selectedSummary.status === 'REJECTED'"
            :status="selectedSummary.status"
            :rejection-reason="selectedSummary.rejectionReason"
          />

          <ActivityEditorFields
            :form="form"
            :type-locked="hasSelectedRecord || formBusy"
            @type-change="changeDraftType"
          />

          <ul v-if="selectedSummary" class="admin-activity-editor-meta">
            <li v-if="hasSelectedRecord">
              {{ t('adminActivityLibrary.createdAt', { date: formatDateTime(selectedSummary.createdAt) }) }}
            </li>
            <li v-if="hasSelectedRecord">
              {{ t('adminActivityLibrary.updatedAt', { date: formatDateTime(selectedSummary.updatedAt) }) }}
            </li>
            <li v-if="selectedSummary.createdByDisplayName">
              {{ t('adminActivityLibrary.createdBy', { name: selectedSummary.createdByDisplayName }) }}
            </li>
          </ul>

          <div class="card-footer">
            <button
              class="btn btn-danger-soft admin-activity-delete"
              type="button"
              :disabled="!hasSelectedRecord || formBusy"
              :aria-busy="deleting"
              @click="deleteActivity"
            >
              {{ deleteButtonLabel }}
            </button>
            <button class="btn btn-ghost" type="button" :disabled="formBusy" @click="startNewDraft(form.type)">
              {{ t('adminActivityLibrary.clearDraft') }}
            </button>
            <button
              class="btn btn-secondary"
              type="button"
              :disabled="!hasSelectedRecord || formBusy"
              @click="updateStatus('DRAFT')"
            >
              {{ t('adminActivityLibrary.saveAsDraft') }}
            </button>
            <button
              class="btn btn-secondary"
              type="button"
              :disabled="!hasSelectedRecord || formBusy"
              @click="updateStatus('ARCHIVED')"
            >
              <IconGlyph name="archive" :size="16" />
              {{ t('adminActivityLibrary.archive') }}
            </button>
            <button
              class="btn btn-secondary"
              type="button"
              :disabled="!hasSelectedRecord || formBusy"
              @click="updateStatus('PUBLISHED')"
            >
              {{ t('adminActivityLibrary.publish') }}
            </button>
            <button
              class="btn btn-primary"
              type="button"
              :disabled="formBusy"
              :aria-busy="saving"
              @click="saveActivity"
            >
              {{ saveButtonLabel }}
            </button>
          </div>
        </template>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { adminActivityGateway, adminApi } from '@/api/services'
import ActivityEditorFields from '@/components/activities/ActivityEditorFields.vue'
import AdminKpiTile from '@/components/admin/AdminKpiTile.vue'
import type { AdminTone } from '@/components/admin/adminTone'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import ContentStatusBadge from '@/components/content/ContentStatusBadge.vue'
import LearningStageTag from '@/components/content/LearningStageTag.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { adminActivityLibraryMessages } from '@/plugins/adminActivityLibraryMessages'
import { contentGovernanceMessages } from '@/plugins/contentGovernanceMessages'
import { useAuthStore } from '@/stores/auth'
import type {
  ActivityDifficulty,
  AdminActivitySummaryResponse,
  AdminActivityType,
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
import { resolveApiError } from '@/utils/apiError'
import { contentStatusMessageKey } from '@/utils/contentStatus'
import { formatDateTime, formatNumber } from '@/utils/format'

type StatusMutation = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

const auth = useAuthStore()
const { t } = useI18n({
  messages: {
    en: { adminActivityLibrary: adminActivityLibraryMessages.en, governance: contentGovernanceMessages.en },
    ms: { adminActivityLibrary: adminActivityLibraryMessages.ms, governance: contentGovernanceMessages.ms },
    'zh-CN': {
      adminActivityLibrary: adminActivityLibraryMessages['zh-CN'],
      governance: contentGovernanceMessages['zh-CN'],
    },
  },
})

const emptyFeed: PageResponse<AdminActivitySummaryResponse> = {
  items: [],
  page: 0,
  size: 12,
  totalItems: 0,
  totalPages: 0,
}

const statusFilterOptions: ContentStatus[] = ['DRAFT', 'SUBMITTED', 'PUBLISHED', 'REJECTED', 'ARCHIVED']

const activityTypeOptions = computed(() =>
  ACTIVITY_TYPES.map((value) => ({ value, label: t(`adminActivityLibrary.${activityTypeLabelKey(value)}`) })),
)

const difficultyOptions = computed<Array<{ value: ActivityDifficulty; label: string }>>(() => [
  { value: 'EASY', label: t('adminActivityLibrary.easy') },
  { value: 'MEDIUM', label: t('adminActivityLibrary.medium') },
  { value: 'HARD', label: t('adminActivityLibrary.hard') },
])

const filters = reactive({
  type: '' as AdminActivityType | '',
  status: '' as ContentStatus | '',
  difficulty: '' as ActivityDifficulty | '',
  keyword: '',
  topic: '',
})

const currentPage = ref(1)
const selectedType = ref<AdminActivityType>('FLASHCARD')
const selectedId = ref<number | null>(null)
const detailLoading = ref(false)
const saving = ref(false)
const deleting = ref(false)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)

const form = reactive<ActivityEditorForm>(createEmptyActivityForm('FLASHCARD'))

const previewReadOnly = computed(() => auth.isPreviewSession && !auth.isAuthenticated)

const feedResource = useResource(
  () =>
    adminApi.activityFeed({
      type: filters.type || undefined,
      status: filters.status || undefined,
      keyword: filters.keyword.trim() || undefined,
      topic: filters.topic.trim() || undefined,
      difficulty: filters.difficulty || undefined,
      page: currentPage.value - 1,
      size: 12,
    }),
  undefined,
  false,
)

const feed = computed(() => feedResource.data.value ?? emptyFeed)
const selectedSummary = computed(
  () => feed.value.items.find((item) => item.id === selectedId.value && item.type === selectedType.value) ?? null,
)
const hasSelectedRecord = computed(() => selectedId.value !== null)
const pageBusy = computed(() => feedResource.loading.value || detailLoading.value)
const formBusy = computed(() => saving.value || deleting.value || detailLoading.value)
// A feed load error with no rows is shown (with a retry) in the feed panel; keep it out of the notice then.
const pageError = computed(() => actionError.value || (feed.value.items.length ? feedResource.error.value : null))
const reviewQueueLink = computed(() => ({
  name: 'admin-content-review',
  query: { type: selectedType.value, id: String(selectedId.value ?? '') },
}))
const statCards = computed<Array<{ label: string; value: string; copy: string; icon: string; tone: AdminTone }>>(() => {
  const pageItems = feed.value.items
  const statusCounts = pageItems.reduce(
    (counts, item) => {
      if (item.status === 'PUBLISHED') counts.published += 1
      else if (item.status === 'DRAFT') counts.drafts += 1
      else if (item.status === 'SUBMITTED') counts.submitted += 1
      else if (item.status === 'ARCHIVED') counts.archived += 1
      return counts
    },
    { published: 0, drafts: 0, submitted: 0, archived: 0 },
  )

  return [
    {
      label: t('adminActivityLibrary.filteredTotal'),
      value: formatNumber(feed.value.totalItems),
      copy: t('adminActivityLibrary.filteredTotalCopy'),
      icon: 'practice',
      tone: 'primary',
    },
    {
      label: t('adminActivityLibrary.published'),
      value: formatNumber(statusCounts.published),
      copy: t('adminActivityLibrary.publishedCopy'),
      icon: 'status',
      tone: 'success',
    },
    {
      label: t('adminActivityLibrary.inReviewCount'),
      value: formatNumber(statusCounts.submitted),
      copy: t('adminActivityLibrary.inReviewCountCopy'),
      icon: 'clip',
      tone: 'warning',
    },
    {
      label: t('adminActivityLibrary.drafts'),
      value: formatNumber(statusCounts.drafts),
      copy: t('adminActivityLibrary.draftsCopy'),
      icon: 'content',
      tone: 'info',
    },
    {
      label: t('adminActivityLibrary.archived'),
      value: formatNumber(statusCounts.archived),
      copy: t('adminActivityLibrary.archivedCopy'),
      icon: 'history',
      tone: 'neutral',
    },
  ]
})

const editorTitle = computed(() =>
  hasSelectedRecord.value
    ? t('adminActivityLibrary.details', { type: activityTypeLabel(selectedType.value) })
    : t('adminActivityLibrary.newActivityDraft'),
)
const editorSubtitle = computed(() => {
  if (hasSelectedRecord.value && selectedSummary.value) {
    return `${activityStatusLabel(selectedSummary.value.status)} · ${activitySummaryMeta(selectedSummary.value)}`
  }
  return t('adminActivityLibrary.editorNewHint', { type: activityTypeLabel(form.type) })
})
const saveButtonLabel = computed(() =>
  saving.value
    ? t('adminActivityLibrary.saving')
    : hasSelectedRecord.value
      ? t('adminActivityLibrary.saveChanges')
      : t('adminActivityLibrary.createActivity'),
)
const deleteButtonLabel = computed(() =>
  deleting.value ? t('adminActivityLibrary.deleting') : t('adminActivityLibrary.delete'),
)
const refreshFeedLabel = computed(() => {
  if (previewReadOnly.value) return t('adminActivityLibrary.previewFeed')
  return pageBusy.value ? t('adminActivityLibrary.refreshing') : t('adminActivityLibrary.refreshFeed')
})

onMounted(() => {
  if (previewReadOnly.value) {
    return
  }

  void reloadFeed('first')
})

function applyFormState(next: ActivityEditorForm): void {
  Object.assign(form, next)
}

function activityTypeLabel(type: AdminActivityType): string {
  return t(`adminActivityLibrary.${activityTypeLabelKey(type)}`)
}

function fallbackActivityTitle(type: AdminActivityType): string {
  return t('adminActivityLibrary.draftFallback', { type: activityTypeLabel(type) })
}

function activityStatusLabel(status: ContentStatus | StatusMutation): string {
  return t(`governance.${contentStatusMessageKey(status)}`)
}

function difficultyLabel(difficulty: ActivityDifficulty): string {
  return difficultyOptions.value.find((item) => item.value === difficulty)?.label ?? difficulty
}

function activitySummaryMeta(item: AdminActivitySummaryResponse): string {
  const parts = [item.topic?.trim() || t('adminActivityLibrary.noTopic')]
  if (item.hskLevel) parts.push(`HSK ${item.hskLevel}`)
  if (item.difficulty) parts.push(difficultyLabel(item.difficulty))
  if (item.tags.length) parts.push(item.tags.slice(0, 2).join(', '))
  return parts.join(' · ')
}

function resetFilters(): void {
  filters.type = ''
  filters.status = ''
  filters.difficulty = ''
  filters.keyword = ''
  filters.topic = ''
  currentPage.value = 1
  actionError.value = null
  actionSuccess.value = null
  if (!previewReadOnly.value) {
    void reloadFeed('first')
  }
}

function applyFilters(): void {
  currentPage.value = 1
  actionError.value = null
  actionSuccess.value = null
  if (!previewReadOnly.value) {
    void reloadFeed('first')
  }
}

function goToPage(page: number): void {
  currentPage.value = page
  void reloadFeed('first')
}

function changeDraftType(): void {
  if (hasSelectedRecord.value) {
    return
  }
  startNewDraft(form.type)
}

function startNewDraft(type: AdminActivityType = 'FLASHCARD'): void {
  // A detail load still in flight must not fill (or clear) the new draft.
  detailRequestSequence++
  detailLoading.value = false
  selectedId.value = null
  selectedType.value = type
  applyFormState(createEmptyActivityForm(type))
  actionError.value = null
  actionSuccess.value = null
}

async function reloadFeed(
  selection: 'first' | 'keep' | { id: number; type: AdminActivityType } = 'keep',
): Promise<void> {
  if (previewReadOnly.value) {
    actionError.value = null
    actionSuccess.value = t('adminActivityLibrary.previewRefresh')
    return
  }

  await feedResource.reload()

  const items = feed.value.items
  if (!items.length) {
    if (selection !== 'keep') {
      startNewDraft(form.type)
    }
    return
  }

  if (selection === 'first') {
    await selectActivity(items[0].type, items[0].id)
    return
  }

  if (selection === 'keep') {
    if (hasSelectedRecord.value) {
      const match = items.find((item) => item.id === selectedId.value && item.type === selectedType.value)
      if (!match) {
        await selectActivity(items[0].type, items[0].id)
      }
    }
    return
  }

  const match = items.find((item) => item.id === selection.id && item.type === selection.type)
  await selectActivity(match?.type ?? items[0].type, match?.id ?? items[0].id)
}

/** Only the latest detail request may fill the editor (rapid clicks on different feed rows). */
let detailRequestSequence = 0

async function selectActivity(type: AdminActivityType, id: number): Promise<void> {
  const sequence = ++detailRequestSequence
  detailLoading.value = true
  actionError.value = null
  actionSuccess.value = null

  try {
    selectedType.value = type
    selectedId.value = id
    const detail = await adminActivityGateway.detail(type, id)
    if (sequence !== detailRequestSequence) return
    applyFormState(activityFormFromDetail(type, detail))
  } catch (reason) {
    if (sequence !== detailRequestSequence) return
    // Never leave the previous record's fields bound to this id: "Save changes" would overwrite it with them.
    selectedId.value = null
    applyFormState(createEmptyActivityForm(type))
    actionError.value = resolveApiError(reason, t('adminActivityLibrary.loadDetailError'))
  } finally {
    if (sequence === detailRequestSequence) detailLoading.value = false
  }
}

async function saveActivity(): Promise<void> {
  if (saving.value || deleting.value) return
  if (previewReadOnly.value) {
    actionError.value = t('adminActivityLibrary.previewChanges')
    actionSuccess.value = null
    return
  }

  const validationKey = validateActivityForm(form)
  if (validationKey) {
    actionError.value = t(`adminActivityLibrary.${validationKey}`)
    actionSuccess.value = null
    return
  }

  saving.value = true
  actionError.value = null
  actionSuccess.value = null

  try {
    const type = form.type
    const payload = buildActivityPayload(form)
    const response =
      hasSelectedRecord.value && selectedId.value != null
        ? await adminActivityGateway.update(type, selectedId.value, payload)
        : await adminActivityGateway.create(type, payload)

    if (response?.id == null) {
      throw new Error(t('adminActivityLibrary.recordIdError'))
    }

    actionSuccess.value = hasSelectedRecord.value
      ? t('adminActivityLibrary.updatedSuccess', { type: activityTypeLabel(type) })
      : t('adminActivityLibrary.createdSuccess', { type: activityTypeLabel(type) })

    await reloadFeed({ id: response.id, type })
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('adminActivityLibrary.saveError'))
  } finally {
    saving.value = false
  }
}

const STATUS_UPDATERS = {
  FLASHCARD: adminApi.updateFlashcardStatus,
  MATCHING_GAME: adminApi.updateMatchingGameStatus,
  LISTENING_EXERCISE: adminApi.updateListeningExerciseStatus,
  TRACING_EXERCISE: adminApi.updateTracingExerciseStatus,
  DRAG_DROP_ACTIVITY: adminApi.updateDragDropActivityStatus,
  STORY_ACTIVITY: adminApi.updateStoryActivityStatus,
} satisfies Record<AdminActivityType, (id: number, status: StatusMutation) => Promise<unknown>>

async function updateStatus(status: StatusMutation): Promise<void> {
  if (saving.value || deleting.value) return
  if (!hasSelectedRecord.value || selectedId.value == null) {
    return
  }

  if (previewReadOnly.value) {
    actionError.value = t('adminActivityLibrary.previewStatus')
    actionSuccess.value = null
    return
  }

  if (
    status !== 'DRAFT' &&
    !confirmActivityMutation(
      t('adminActivityLibrary.confirmStatus', {
        type: activityTypeLabel(selectedType.value),
        status: activityStatusLabel(status),
      }),
    )
  ) {
    return
  }

  saving.value = true
  actionError.value = null
  actionSuccess.value = null

  try {
    await STATUS_UPDATERS[selectedType.value](selectedId.value, status)

    actionSuccess.value = t('adminActivityLibrary.statusUpdated', {
      type: activityTypeLabel(selectedType.value),
      status: activityStatusLabel(status),
    })
    await reloadFeed({ id: selectedId.value, type: selectedType.value })
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('adminActivityLibrary.statusError'))
  } finally {
    saving.value = false
  }
}

async function deleteActivity(): Promise<void> {
  if (deleting.value || saving.value) return
  if (!hasSelectedRecord.value || selectedId.value == null) {
    return
  }

  if (previewReadOnly.value) {
    actionError.value = t('adminActivityLibrary.previewDelete')
    actionSuccess.value = null
    return
  }

  if (
    !confirmActivityMutation(t('adminActivityLibrary.confirmDelete', { type: activityTypeLabel(selectedType.value) }))
  ) {
    return
  }

  deleting.value = true
  actionError.value = null
  actionSuccess.value = null

  try {
    await adminActivityGateway.remove(selectedType.value, selectedId.value)

    actionSuccess.value = t('adminActivityLibrary.deletedSuccess', {
      type: activityTypeLabel(selectedType.value),
    })
    startNewDraft(selectedType.value)
    await reloadFeed('first')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('adminActivityLibrary.deleteError'))
  } finally {
    deleting.value = false
  }
}

function confirmActivityMutation(message: string): boolean {
  const confirmed = window.confirm(`${message}\n\n${t('adminActivityLibrary.liveChangeWarning')}`)
  if (!confirmed) {
    actionSuccess.value = null
    actionError.value = null
  }

  return confirmed
}
</script>

<style scoped>
.admin-activity-head-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.admin-activity-review-hint {
  flex-wrap: wrap;
  align-items: center;
  margin: 0;
}

.admin-activity-review-hint > span {
  flex: 1 1 200px;
}

.admin-activity-editor-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1) var(--space-4);
  margin: 0;
  padding: 0;
  list-style: none;
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
}

.admin-activity-delete {
  margin-right: auto;
}

@media (max-width: 640px) {
  .admin-activity-delete {
    margin-right: 0;
  }
}
</style>
