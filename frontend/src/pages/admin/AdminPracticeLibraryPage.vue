<template>
  <div class="page-stack">
    <div class="admin-head">
      <PageHeaderBlock :title="t('adminLearning.libraryPageTitle')" :description="t('adminLearning.libraryNote')">
        <template #actions>
          <button class="btn btn-primary" type="button" data-testid="new-activity" @click="openEditor(null)">
            <IconGlyph name="plus" :size="16" />{{ t('adminLearning.newActivity') }}
          </button>
        </template>
      </PageHeaderBlock>
      <AdminContentTabs />
    </div>
    <DataNotice :success="actionSuccess" :error="actionError">
      <template v-if="restorable" #actions>
        <button class="btn btn-secondary btn-sm" type="button" @click="runAction('restore', restorable)">
          {{ t('adminLearning.restore') }}
        </button>
      </template>
    </DataNotice>

    <section class="admin-sheet" aria-labelledby="library-title">
      <h2 id="library-title" class="sr-only">{{ t('adminLearning.libraryHeading') }}</h2>
      <div class="admin-toolbar">
        <div class="field admin-toolbar-search">
          <label class="field-label" for="library-search">{{ t('adminLearning.search') }}</label>
          <div class="input-with-icon">
            <IconGlyph class="input-icon" name="search" :size="16" />
            <input
              id="library-search"
              v-model="searchTerm"
              class="input"
              type="search"
              :placeholder="t('adminLearning.searchPlaceholder')"
              data-testid="library-search"
            />
          </div>
        </div>
        <div class="field">
          <label class="field-label" for="library-type">{{ t('adminLearning.type') }}</label>
          <select id="library-type" v-model="typeFilter" class="select" @change="applyFilters">
            <option :value="null">{{ t('adminLearning.allTypes') }}</option>
            <option v-for="option in PRACTICE_TYPES" :key="option" :value="option">
              {{ t(practiceTypeKey(option)) }}
            </option>
          </select>
        </div>
        <div class="field">
          <label class="field-label" for="library-stage">{{ t('adminLearning.stage') }}</label>
          <select id="library-stage" v-model="stageFilter" class="select" @change="applyFilters">
            <option :value="null">{{ t('adminLearning.allStages') }}</option>
            <option v-for="stage in STAGES" :key="stage" :value="stage">{{ t(stageKey(stage)) }}</option>
          </select>
        </div>
        <div class="field">
          <label class="field-label" for="library-status">{{ t('adminLearning.status') }}</label>
          <select id="library-status" v-model="statusFilter" class="select" @change="applyFilters">
            <option v-for="option in STATUS_FILTERS" :key="option.labelKey" :value="option.value">
              {{ t(option.labelKey) }}
            </option>
          </select>
        </div>
      </div>

      <div v-if="library.loading.value" class="admin-sheet-body">
        <StatePanel
          tone="loading"
          :title="t('adminLearning.libraryLoading')"
          :message="t('adminLearning.libraryLoadingHint')"
        />
      </div>
      <div v-else-if="library.error.value && !rows.length" class="admin-sheet-body">
        <StatePanel tone="error" icon="alert" :title="t('adminLearning.libraryError')" :message="library.error.value">
          <template #actions>
            <button class="btn btn-primary" type="button" @click="library.reload">
              {{ t('adminLearning.retry') }}
            </button>
          </template>
        </StatePanel>
      </div>
      <div v-else-if="!rows.length" class="admin-sheet-body">
        <StatePanel
          tone="empty"
          icon="search"
          :title="t('adminLearning.noActivities')"
          :message="t('adminLearning.noActivitiesHint')"
        >
          <template #actions>
            <button class="btn btn-secondary" type="button" @click="clearFilters">
              {{ t('adminLearning.clearFilters') }}
            </button>
          </template>
        </StatePanel>
      </div>
      <template v-else>
        <div class="data-table-wrap" role="region" :aria-label="t('adminLearning.libraryHeading')" tabindex="0">
          <table class="data-table admin-table">
            <thead>
              <tr>
                <th scope="col">{{ t('adminLearning.title') }}</th>
                <th scope="col">{{ t('adminLearning.type') }}</th>
                <th scope="col">{{ t('adminLearning.stageLevel') }}</th>
                <th scope="col">{{ t('adminLearning.status') }}</th>
                <th scope="col" class="col-numeric">{{ t('adminLearning.updated') }}</th>
                <th scope="col" class="col-actions">
                  <span class="sr-only">{{ t('adminLearning.actions') }}</span>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in rows"
                :key="`${row.type}-${row.id}`"
                :class="{ 'is-selected': detailsOpen && openRow?.type === row.type && openRow?.id === row.id }"
                data-testid="library-row"
                @click="openDetails(row)"
              >
                <td class="admin-learning-title-cell">{{ row.title }}</td>
                <td>
                  <span class="tag">{{ t(practiceTypeKey(row.type)) }}</span>
                </td>
                <td class="admin-learning-stage-cell">
                  {{ t(stageKey(row.stage)) }}<template v-if="row.hskLevel"> · HSK {{ row.hskLevel }}</template>
                </td>
                <td><StatusPill :label="t(setStatusKey(row.status))" :tone="setStatusTone(row.status)" /></td>
                <td class="col-numeric admin-cell-date">
                  {{ formatShortDate(row.updatedAt) }}
                </td>
                <td class="col-actions">
                  <button
                    class="btn btn-ghost btn-icon btn-sm"
                    type="button"
                    :aria-label="t('adminLearning.openActivity', { title: row.title })"
                    @click.stop="openDetails(row)"
                  >
                    <IconGlyph name="chevronRight" :size="16" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="admin-sheet-foot">
          <PaginationBar
            :current-page="page + 1"
            :total-pages="totalPages"
            :info="t('adminLearning.showing', range)"
            :label="t('adminLearning.libraryPages')"
            icon-only
            @update:current-page="goToPage($event - 1)"
          />
        </div>
      </template>
    </section>

    <PracticeSetDetails
      :open="detailsOpen"
      :set="details"
      :loading="detailsLoading"
      :error="detailsError"
      @close="detailsOpen = false"
      @retry="loadDetails"
      @edit="openEditor(details)"
      @action="requestAction"
    />
    <PracticeSetEditor :open="editorOpen" :existing="editing" @close="editorOpen = false" @saved="onSaved" />
    <ConfirmDialog
      :open="confirming !== null"
      :tone="confirming === 'archive' ? 'danger' : 'primary'"
      :title="confirmTitle"
      :message="confirming === 'archive' ? t('adminLearning.archiveMessage') : t('adminLearning.unpublishMessage')"
      :confirm-label="
        confirming === 'archive' ? t('adminLearning.archiveActivity') : t('adminLearning.unpublishActivity')
      "
      :cancel-label="confirming === 'archive' ? t('adminLearning.keepActivity') : t('adminLearning.keepPublished')"
      :busy="busy"
      @confirm="confirmAction"
      @cancel="confirming = null"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { adminLearningApi } from '@/api/learn'
import AdminContentTabs from '@/components/admin/AdminContentTabs.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import PracticeSetDetails from '@/components/learn/admin/PracticeSetDetails.vue'
import PracticeSetEditor from '@/components/learn/admin/PracticeSetEditor.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useDebouncedSearch } from '@/composables/useDebouncedSearch'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource, useServerPage } from '@/composables/useResource'
import { adminLearningMessages } from '@/plugins/adminLearningMessages'
import { classesMessages } from '@/plugins/classesMessages'
import type { ContentStatus, LearningStage } from '@/types/api'
import type { AdminPracticeSetDetail, AdminPracticeSetRow, PracticeType } from '@/types/learn'
import { resolveApiError } from '@/utils/apiError'
import { formatShortDate } from '@/utils/format'
import { PRACTICE_TYPES, practiceTypeKey, setStatusKey, setStatusTone, STAGES, stageKey } from '@/utils/learnLabels'
import '@/styles/learn-admin.css'

const PAGE_SIZE = 10
const STATUS_FILTERS: readonly { value: ContentStatus | null; labelKey: string }[] = [
  { value: null, labelKey: 'adminLearning.statusAll' },
  { value: 'PUBLISHED', labelKey: 'adminLearning.published' },
  { value: 'DRAFT', labelKey: 'adminLearning.draft' },
  { value: 'ARCHIVED', labelKey: 'adminLearning.archived' },
]
type SetAction = 'publish' | 'unpublish' | 'archive' | 'restore'

const { t } = useLocalMessages({ adminLearning: adminLearningMessages, classes: classesMessages })
const feedback = useActionFeedback()
const { actionError, actionSuccess } = feedback

// ----- list -----

const { searchTerm, keyword, clear: clearSearch } = useDebouncedSearch()
const typeFilter = ref<PracticeType | null>(null)
const statusFilter = ref<ContentStatus | null>(null)
const stageFilter = ref<LearningStage | null>(null)
const page = ref(0)

const library = useResource(() =>
  adminLearningApi.practiceSets({
    q: keyword.value,
    type: typeFilter.value,
    status: statusFilter.value,
    stage: stageFilter.value,
    page: page.value,
    size: PAGE_SIZE,
  }),
)
const { items: rows, totalPages, range, goToPage } = useServerPage(library, page)

function applyFilters(): void {
  page.value = 0
  void library.reload()
}

watch(keyword, applyFilters)

function clearFilters(): void {
  clearSearch()
  typeFilter.value = null
  statusFilter.value = null
  stageFilter.value = null
  applyFilters()
}

// ----- details -----

const detailsOpen = ref(false)
const openRow = ref<{ type: PracticeType; id: number } | null>(null)
const details = ref<AdminPracticeSetDetail | null>(null)
const detailsLoading = ref(false)
const detailsError = ref<string | null>(null)

function openDetails(row: AdminPracticeSetRow): void {
  openRow.value = { type: row.type, id: row.id }
  details.value = null
  detailsOpen.value = true
  void loadDetails()
}

async function loadDetails(): Promise<void> {
  if (!openRow.value) return
  detailsLoading.value = true
  detailsError.value = null
  try {
    details.value = await adminLearningApi.practiceSet(openRow.value.type, openRow.value.id)
  } catch (reason) {
    detailsError.value = resolveApiError(reason, t('adminLearning.activityLoadFailed'))
  } finally {
    detailsLoading.value = false
  }
}

// ----- editor -----

const editorOpen = ref(false)
const editing = ref<AdminPracticeSetDetail | null>(null)

function openEditor(set: AdminPracticeSetDetail | null): void {
  editing.value = set
  detailsOpen.value = false
  editorOpen.value = true
}

function onSaved(set: AdminPracticeSetDetail): void {
  editorOpen.value = false
  restorable.value = null
  feedback.showSuccess(
    editing.value ? t('adminLearning.changesSaved', { title: set.title }) : t('adminLearning.activitySaved'),
  )
  void library.reload()
}

// ----- status changes -----

const confirming = ref<'archive' | 'unpublish' | null>(null)
const busy = ref(false)
const restorable = ref<{ type: PracticeType; id: number } | null>(null)

const confirmTitle = computed(() => {
  const title = details.value?.title ?? ''
  return confirming.value === 'archive'
    ? t('adminLearning.archiveTitle', { title })
    : t('adminLearning.unpublishTitle', { title })
})

function requestAction(action: SetAction): void {
  if (action === 'archive' || action === 'unpublish') {
    confirming.value = action
    return
  }
  if (details.value) void runAction(action, { type: details.value.type, id: details.value.id })
}

function confirmAction(): void {
  const action = confirming.value
  if (action && details.value) void runAction(action, { type: details.value.type, id: details.value.id })
}

async function runAction(action: SetAction, target: { type: PracticeType; id: number }): Promise<void> {
  busy.value = true
  const succeeded = await feedback.runAction(t('adminLearning.saveFailed'), async () => {
    const updated = await adminLearningApi.changePracticeSet(target.type, target.id, action)
    if (details.value?.id === updated.id && details.value.type === updated.type) details.value = updated
    restorable.value = action === 'archive' ? { type: updated.type, id: updated.id } : null
    return outcome(action, updated.title)
  })
  busy.value = false
  confirming.value = null
  if (succeeded) {
    detailsOpen.value = false
    void library.reload()
  }
}

function outcome(action: SetAction, title: string): string {
  switch (action) {
    case 'publish':
      return t('adminLearning.publishedNotice', { title })
    case 'unpublish':
      return t('adminLearning.unpublishedNotice', { title })
    case 'archive':
      return t('adminLearning.archivedNotice', { title })
    default:
      return t('adminLearning.restoredNotice', { title })
  }
}

watch(actionError, (error) => {
  if (error) restorable.value = null
})
</script>
