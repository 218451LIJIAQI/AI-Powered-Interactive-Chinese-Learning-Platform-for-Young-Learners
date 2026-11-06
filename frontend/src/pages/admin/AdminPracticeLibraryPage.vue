<template>
  <div class="page-stack">
    <PageHeaderBlock :title="t('adminLearning.libraryPageTitle')" hide-title />
    <AdminContentTabs>
      <button class="btn btn-primary" type="button" data-testid="new-activity" @click="openEditor(null)">
        <IconGlyph name="plus" :size="16" />
        <span>{{ t('adminLearning.newActivity') }}</span>
      </button>
    </AdminContentTabs>
    <DataNotice :error="actionError" />

    <section class="panel admin-table-card" aria-labelledby="library-title">
      <h2 id="library-title" class="sr-only">{{ t('adminLearning.libraryHeading') }}</h2>
      <div class="admin-filters">
        <div class="field is-search">
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
            <option v-for="option in LIBRARY_TYPES" :key="option" :value="option">
              {{ t(practiceTypeKey(option)) }}
            </option>
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

      <StatePanel
        v-if="library.loading.value"
        tone="loading"
        :title="t('adminLearning.libraryLoading')"
        :message="t('adminLearning.libraryLoadingHint')"
      />
      <StatePanel
        v-else-if="library.error.value && !rows.length"
        tone="error"
        icon="alert"
        :title="t('adminLearning.libraryError')"
        :message="library.error.value"
      >
        <template #actions>
          <button class="btn btn-primary" type="button" @click="library.reload">
            {{ t('adminLearning.retry') }}
          </button>
        </template>
      </StatePanel>
      <StatePanel
        v-else-if="!rows.length"
        tone="empty"
        icon="search"
        spacious
        :title="t('adminLearning.noActivities')"
        :message="t('adminLearning.noActivitiesHint')"
      >
        <template #actions>
          <button class="btn btn-primary" type="button" @click="clearFilters">
            {{ t('adminLearning.clearFilters') }}
          </button>
        </template>
      </StatePanel>
      <template v-else>
        <div
          class="data-table-wrap admin-table-wrap"
          role="region"
          :aria-label="t('adminLearning.libraryHeading')"
          tabindex="0"
        >
          <table class="data-table admin-table admin-library-table">
            <thead>
              <tr>
                <th scope="col" class="admin-library-col-title">{{ t('adminLearning.title') }}</th>
                <th scope="col" class="admin-library-col-type">{{ t('adminLearning.type') }}</th>
                <th scope="col" class="admin-library-col-level">{{ t('adminLearning.level') }}</th>
                <th scope="col" class="admin-library-col-author">{{ t('adminLearning.createdBy') }}</th>
                <th scope="col" class="admin-library-col-status">{{ t('adminLearning.status') }}</th>
                <th scope="col" class="admin-library-col-updated">{{ t('adminLearning.updated') }}</th>
                <th scope="col" class="col-actions admin-col-open">
                  <span class="sr-only">{{ t('adminLearning.actions') }}</span>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in rows"
                :key="rowKey(row)"
                :class="{ 'is-selected': detailsOpen && openRow?.type === row.type && openRow?.id === row.id }"
                data-testid="library-row"
                @click="openDetails(row)"
              >
                <td class="admin-cell-strong">{{ row.title }}</td>
                <td>
                  <span class="tag is-outline">{{ t(practiceTypeKey(row.type)) }}</span>
                </td>
                <td class="admin-cell-muted">
                  {{ row.hskLevel ? t('adminLearning.hskLevel', { level: row.hskLevel }) : '—' }}
                </td>
                <td class="admin-cell-muted">{{ authors[rowKey(row)] ?? '—' }}</td>
                <td><StatusPill :label="t(setStatusKey(row.status))" :tone="practiceStatusTone(row.status)" /></td>
                <td class="admin-cell-date">{{ formatShortDate(row.updatedAt) }}</td>
                <td class="col-actions">
                  <button
                    v-if="restorable && restorable.type === row.type && restorable.id === row.id"
                    class="btn btn-secondary btn-sm"
                    type="button"
                    :aria-label="t('adminLearning.restoreActivity', { title: row.title })"
                    @click.stop="runAction('restore', restorable)"
                  >
                    {{ t('adminLearning.restore') }}
                  </button>
                  <button
                    v-else
                    class="btn btn-secondary btn-sm"
                    type="button"
                    :aria-label="t('adminLearning.openActivity', { title: row.title })"
                    @click.stop="openDetails(row)"
                  >
                    {{ t('adminLearning.open') }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationBar
          :current-page="page + 1"
          :total-pages="totalPages"
          :info="t('adminLearning.showing', range)"
          :label="t('adminLearning.libraryPages')"
          icon-only
          @update:current-page="goToPage($event - 1)"
        />
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
    <AdminDecisionDialog
      :open="confirming !== null"
      :tone="confirming === 'archive' ? 'danger' : 'primary'"
      :title="confirmTitle"
      :confirm-label="
        confirming === 'archive' ? t('adminLearning.archiveActivity') : t('adminLearning.unpublishActivity')
      "
      :confirm-icon="confirming === 'archive' ? 'close' : ''"
      :cancel-label="confirming === 'archive' ? t('adminLearning.keepActivity') : t('adminLearning.keepPublished')"
      :busy="busy"
      test-id="set-confirm"
      @confirm="confirmAction"
      @cancel="confirming = null"
    >
      <p class="overlay-message">
        {{ confirming === 'archive' ? t('adminLearning.archiveMessage') : t('adminLearning.unpublishMessage') }}
      </p>
    </AdminDecisionDialog>
    <AppToast :message="toast" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { adminLearningApi } from '@/api/learn'
import AdminContentTabs from '@/components/admin/AdminContentTabs.vue'
import AdminDecisionDialog from '@/components/admin/AdminDecisionDialog.vue'
import { practiceStatusTone } from '@/components/admin/adminDisplay'
import { useAdminContentCounts } from '@/components/admin/useAdminContentCounts'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import PracticeSetDetails from '@/components/learn/admin/PracticeSetDetails.vue'
import PracticeSetEditor from '@/components/learn/admin/PracticeSetEditor.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useDebouncedSearch } from '@/composables/useDebouncedSearch'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource, useServerPage } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { adminLearningMessages } from '@/plugins/adminLearningMessages'
import type { ContentStatus } from '@/types/api'
import type { AdminPracticeSetDetail, AdminPracticeSetRow, PracticeType } from '@/types/learn'
import { resolveApiError } from '@/utils/apiError'
import { formatShortDate } from '@/utils/format'
import { PRACTICE_TYPES, practiceTypeKey, setStatusKey } from '@/utils/learnLabels'
import '@/styles/ops-admin.css'
import '@/styles/learn-admin.css'

const PAGE_SIZE = 10
/** Practice has no Story; a story set from an older database still lists and opens, it just can't be filtered for. */
const LIBRARY_TYPES = PRACTICE_TYPES.filter((type) => type !== 'STORY')
const STATUS_FILTERS: readonly { value: ContentStatus | null; labelKey: string }[] = [
  { value: null, labelKey: 'adminLearning.statusAll' },
  { value: 'PUBLISHED', labelKey: 'adminLearning.published' },
  { value: 'DRAFT', labelKey: 'adminLearning.draft' },
  { value: 'ARCHIVED', labelKey: 'adminLearning.archived' },
]
type SetAction = 'publish' | 'unpublish' | 'archive' | 'restore'

const { t } = useLocalMessages({ adminLearning: adminLearningMessages })
const { actionError, clearFeedback, showError } = useActionFeedback()
const { toast, showToast } = useToast()
const { reloadCounts } = useAdminContentCounts()

// ----- list -----

const { searchTerm, keyword, clear: clearSearch } = useDebouncedSearch()
const typeFilter = ref<PracticeType | null>(null)
const statusFilter = ref<ContentStatus | null>(null)
const page = ref(0)

const library = useResource(() =>
  adminLearningApi.practiceSets({
    q: keyword.value,
    type: typeFilter.value,
    status: statusFilter.value,
    stage: null,
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
  applyFilters()
}

function rowKey(row: { type: PracticeType; id: number }): string {
  return `${row.type}-${row.id}`
}

// The list rows don't say who made a set, only its details do; each set's author is read once.
const authors = ref<Record<string, string>>({})

watch(rows, (list) => {
  for (const row of list) {
    const key = rowKey(row)
    if (authors.value[key]) continue
    adminLearningApi
      .practiceSet(row.type, row.id)
      .then((set) => {
        authors.value[key] = set.createdBy ?? t('adminLearning.platformTeam')
      })
      .catch(() => {
        // The column then shows a dash; opening the row reports the problem.
      })
  }
})

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
  clearFeedback()
  showToast(editing.value ? t('adminLearning.changesSaved', { title: set.title }) : t('adminLearning.activitySaved'))
  void reloadCounts()
  void library.reload()
}

// ----- status changes -----

const confirming = ref<'archive' | 'unpublish' | null>(null)
const busy = ref(false)
/** The set just archived: its row offers Restore instead of Open until the next action. */
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
  clearFeedback()
  try {
    const updated = await adminLearningApi.changePracticeSet(target.type, target.id, action)
    if (details.value?.id === updated.id && details.value.type === updated.type) details.value = updated
    restorable.value = action === 'archive' ? { type: updated.type, id: updated.id } : null
    showToast(outcome(action, updated.title))
    detailsOpen.value = false
    void library.reload()
  } catch (reason) {
    restorable.value = null
    showError(resolveApiError(reason, t('adminLearning.saveFailed')))
  } finally {
    busy.value = false
    confirming.value = null
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
</script>
