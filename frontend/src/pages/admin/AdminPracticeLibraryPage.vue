<template>
  <div class="page-stack">
    <h1 class="sr-only">{{ t('nav.practiceLibrary') }}</h1>
    <AdminContentTabs :skip-practice="!hasFilters">
      <button class="btn btn-primary" type="button" data-testid="new-activity" @click="openEditor(null)">
        <IconGlyph name="plus" :size="16" />
        <span>{{ t('adminLearning.newActivity') }}</span>
      </button>
    </AdminContentTabs>
    <DataNotice :error="actionError" />

    <section class="panel admin-table-card">
      <h2 id="library-title" class="sr-only">{{ t('nav.practiceLibrary') }}</h2>
      <form class="admin-filters" role="search" @submit.prevent>
        <div class="field is-search">
          <label class="field-label" for="library-search">{{ t('common.search') }}</label>
          <div class="input-with-icon">
            <IconGlyph class="input-icon" name="search" :size="16" />
            <input
              id="library-search"
              v-model="searchTerm"
              class="input"
              type="search"
              maxlength="200"
              autocomplete="off"
              :placeholder="t('adminLearning.searchPlaceholder')"
              data-testid="library-search"
            />
          </div>
        </div>
        <div class="field">
          <label class="field-label" for="library-type">{{ t('adminLearning.type') }}</label>
          <select id="library-type" v-model="typeFilter" class="select">
            <option value="">{{ t('adminLearning.allTypes') }}</option>
            <option v-for="option in PRACTICE_TYPES" :key="option" :value="option">
              {{ t(`practiceType.${option}`) }}
            </option>
          </select>
        </div>
        <div class="field">
          <label class="field-label" for="library-status">{{ t('adminLearning.status') }}</label>
          <select id="library-status" v-model="statusFilter" class="select">
            <option value="">{{ t('adminLearning.statusAll') }}</option>
            <option v-for="status in CONTENT_STATUSES" :key="status" :value="status">
              {{ t(setStatusKey(status)) }}
            </option>
          </select>
        </div>
      </form>

      <StatePanel
        v-if="library.loading.value"
        tone="loading"
        :title="t('adminLearning.libraryLoading')"
        :message="t('adminLearning.libraryLoadingHint')"
      />
      <StatePanel
        v-else-if="library.error.value"
        tone="error"
        :title="t('adminLearning.libraryError')"
        :message="library.error.value"
        @retry="library.reload"
      />
      <template v-else-if="rows.length">
        <div
          ref="tableWrapRef"
          class="data-table-wrap admin-table-wrap"
          role="region"
          aria-labelledby="library-title"
          tabindex="0"
        >
          <table class="data-table admin-table admin-table-fixed admin-library-table">
            <thead>
              <tr>
                <th scope="col">{{ t('adminLearning.title') }}</th>
                <th scope="col" class="admin-library-col-type">{{ t('adminLearning.type') }}</th>
                <th scope="col" class="admin-library-col-level">{{ t('adminLearning.level') }}</th>
                <th scope="col" class="admin-library-col-status">{{ t('adminLearning.status') }}</th>
                <th scope="col" class="admin-library-col-updated">{{ t('adminLearning.updated') }}</th>
                <th scope="col" class="col-actions admin-library-col-open">
                  <span class="sr-only">{{ t('adminLearning.actions') }}</span>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in rows"
                :key="`${row.type}-${row.id}`"
                :class="{ 'is-selected': inDrawer(row) }"
                :data-set-key="setKeyString(row)"
                data-testid="library-row"
                @click="onRowClick(row)"
              >
                <td class="admin-cell-strong">
                  <span
                    v-if="row.status === 'ARCHIVED'"
                    role="button"
                    tabindex="0"
                    :aria-label="t('adminLearning.openActivity', { title: row.title })"
                    @keydown.enter.prevent="openDetails(row)"
                    @keydown.space.prevent="openDetails(row)"
                  >
                    <CjkText :text="row.title" />
                  </span>
                  <CjkText v-else :text="row.title" />
                </td>
                <td>
                  <span class="tag is-outline">{{ t(`practiceType.${row.type}`) }}</span>
                </td>
                <td class="admin-cell-muted">
                  {{ row.hskLevel ? t('hsk.number', { level: row.hskLevel }) : '—' }}
                </td>
                <td><StatusPill :label="t(setStatusKey(row.status))" :tone="contentStatusTone(row.status)" /></td>
                <td class="admin-cell-date">{{ formatShortDate(row.updatedAt) }}</td>
                <td class="col-actions">
                  <!-- An archived set is brought back from its row (A4.14, A4.19); every other row opens. -->
                  <button
                    v-if="row.status === 'ARCHIVED'"
                    class="btn btn-secondary btn-sm"
                    type="button"
                    :disabled="busyKeys.has(setKeyString(row))"
                    :aria-busy="busyKeys.has(setKeyString(row)) || undefined"
                    :aria-label="t('adminLearning.restoreActivity', { title: row.title })"
                    data-testid="library-restore"
                    @click.stop="runAction('restore', row)"
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
                    {{ t('common.open') }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationBar
          :page="displayedPage"
          :total-pages="totalPages"
          :label="t('adminLearning.libraryPages')"
          icon-only
          @update:page="goToPage"
        />
      </template>
      <StatePanel
        v-else-if="hasFilters"
        tone="empty"
        icon="search"
        spacious
        :title="keyword ? t('adminLearning.noActivitiesSearch', { term: keyword }) : t('adminLearning.noActivities')"
        :message="searchOnly ? t('adminLearning.noActivitiesSearchHint') : t('adminLearning.noActivitiesHint')"
      >
        <template #actions>
          <button class="btn btn-secondary" type="button" data-testid="library-clear" @click="clearFilters">
            {{ searchOnly ? t('common.clearSearch') : t('common.clearFilters') }}
          </button>
        </template>
      </StatePanel>
      <StatePanel
        v-else
        tone="empty"
        icon="layers"
        spacious
        :title="t('adminLearning.libraryEmpty')"
        :message="t('adminLearning.libraryEmptyHint')"
      >
        <template #actions>
          <button class="btn btn-primary" type="button" @click="openEditor(null)">
            <IconGlyph name="plus" :size="16" />
            <span>{{ t('adminLearning.newActivity') }}</span>
          </button>
        </template>
      </StatePanel>
    </section>

    <PracticeSetDetails
      v-if="openRow !== null"
      :set="details.data.value"
      :loading="details.loading.value"
      :error="details.error.value"
      :busy="busyKeys.has(setKeyString(openRow))"
      :action-error="detailsActionError"
      @close="closeDetails()"
      @retry="details.reload"
      @edit="openEditor(details.data.value)"
      @action="onDetailsAction"
    />
    <PracticeSetEditor
      v-if="editing !== undefined"
      :existing="editing"
      :save-set="saveSet"
      @close="closeEditor"
      @saved="onSaved"
    />
    <AppToast :message="toast" :action="toastAction" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { adminLearningApi } from '@/api/learn'
import AdminContentTabs from '@/components/admin/AdminContentTabs.vue'
import { contentStatusTone } from '@/components/admin/adminDisplay'
import { reloadContentCounts, syncContentCount } from '@/components/admin/adminContentCounts'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import PracticeSetDetails from '@/components/learn/admin/PracticeSetDetails.vue'
import PracticeSetEditor from '@/components/learn/admin/PracticeSetEditor.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useDebouncedSearch } from '@/composables/useDebouncedSearch'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { usePageFilters, useResource, useServerPage } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { PRACTICE_TYPES } from '@/config/practiceActivities'
import { adminLearningSharedMessages } from '@/plugins/adminLearningSharedMessages'
import { adminPracticeLibraryMessages } from '@/plugins/adminPracticeLibraryMessages'
import { CONTENT_STATUSES, type ContentStatus } from '@/types/api'
import type { AdminPracticeSetDetail, AdminPracticeSetRow, PracticeSetAction, PracticeType } from '@/types/learn'
import { resolveApiError } from '@/utils/apiError'
import { formatShortDate } from '@/utils/format'
import { setStatusKey } from '@/utils/learnLabels'
import { firstQueryText, pageFromQuery, pageQuery, queryOption } from '@/utils/routeQuery'
import '@/styles/admin-shared.css'
import '@/styles/learn-admin.css'

/** A set by its type and id: each type keeps its sets in a table of its own, so an id alone is not unique. */
type SetKey = Pick<AdminPracticeSetRow, 'type' | 'id'>

const PAGE_SIZE = 10
/**
 * The change an Undo in the toast runs to take one back. Archive has its own inverse on the server.
 */
const UNDO_ACTIONS: Readonly<Partial<Record<PracticeSetAction, PracticeSetAction>>> = {
  publish: 'unpublish',
  unpublish: 'publish',
  archive: 'unarchive',
  restore: 'archive',
}

const { t } = useLocalMessages({ adminLearning: [adminLearningSharedMessages, adminPracticeLibraryMessages] })
const route = useRoute()
const router = useRouter()
const actionError = ref<string | null>(null)
const tableWrapRef = ref<HTMLElement | null>(null)
const { toast, toastAction, showToast, hideToast } = useToast()

// The list, kept in the address (q, type, status and page).

const {
  searchTerm,
  keyword,
  restore: restoreSearch,
  clear: clearSearch,
} = useDebouncedSearch(firstQueryText(route.query.q))
const typeFilter = ref<PracticeType | ''>(queryOption(route.query.type, PRACTICE_TYPES) ?? '')
const statusFilter = ref<ContentStatus | ''>(queryOption(route.query.status, CONTENT_STATUSES) ?? '')
const page = ref(pageFromQuery(route.query.page))

const library = useResource(() =>
  adminLearningApi.practiceSets({
    q: keyword.value,
    type: typeFilter.value || null,
    status: statusFilter.value || null,
    page: page.value,
    size: PAGE_SIZE,
  }),
)

function writeAddress(): void {
  void router.replace({
    query: {
      q: keyword.value || undefined,
      type: typeFilter.value || undefined,
      status: statusFilter.value || undefined,
      page: pageQuery(page.value),
    },
  })
}

const {
  items: rows,
  displayedPage,
  totalPages,
  goToPage,
} = useServerPage(library, page, {
  stepBackFromEmpty: true,
  onPageChange: writeAddress,
})

const followAddress = usePageFilters([keyword, typeFilter, statusFilter], page, library, writeAddress)

watch([() => route.query.q, () => route.query.type, () => route.query.status, () => route.query.page], () => {
  if (route.name !== 'admin-practice-library') return
  followAddress(pageFromQuery(route.query.page), () => {
    restoreSearch(firstQueryText(route.query.q))
    typeFilter.value = queryOption(route.query.type, PRACTICE_TYPES) ?? ''
    statusFilter.value = queryOption(route.query.status, CONTENT_STATUSES) ?? ''
  })
})

function clearFilters(): void {
  clearSearch()
  typeFilter.value = ''
  statusFilter.value = ''
}

const hasFilters = computed(() => Boolean(keyword.value || typeFilter.value || statusFilter.value))
/** Only the search box narrows the list: the empty state then offers "Clear search", not "Clear filters" (A4.15). */
const searchOnly = computed(() => Boolean(keyword.value) && !typeFilter.value && !statusFilter.value)

watch(
  library.data,
  (loaded) => {
    if (loaded && !hasFilters.value) syncContentCount('practice', loaded.totalItems)
  },
  { immediate: true },
)

function onRowClick(row: AdminPracticeSetRow): void {
  if (window.getSelection()?.toString()) return
  openDetails(row)
}

// The details drawer

const openRow = ref<SetKey | null>(null)
const details = useResource(() => adminLearningApi.practiceSet(openRow.value!.type, openRow.value!.id), false)
/** A failed action on the set in the open drawer, shown there: the drawer's scrim covers the page notice. */
const detailsActionError = ref<string | null>(null)
/** Counts the drawer's openings: a change that ends after the drawer was opened again leaves that drawer open. */
let detailsVersion = 0

/** The drawer is open on this set. */
function inDrawer(set: SetKey): boolean {
  return openRow.value?.type === set.type && openRow.value.id === set.id
}

/** Closes the details drawer and invalidates any in-flight detail fetch. */
function closeDetails(): void {
  openRow.value = null
  details.replace(null)
  detailsActionError.value = null
}

/** Opens the drawer on a set; details already at hand (a save's answer, with its History) are not loaded again. */
function openDetails(set: SetKey, loaded: AdminPracticeSetDetail | null = null): void {
  if (!loaded && toastAction.value) hideToast()
  detailsVersion += 1
  openRow.value = { type: set.type, id: set.id }
  details.replace(loaded)
  detailsActionError.value = null
  if (!loaded) void details.reload()
}

// The editor

const isUnmounted = useIsUnmounted()
const editing = ref<AdminPracticeSetDetail | null>()
/** Counts editor sessions so a background save from a closed editor reports its outcome on the page. */
let editorSession = 0

function openEditor(set: AdminPracticeSetDetail | null): void {
  if (toastAction.value) hideToast()
  editorSession += 1
  editing.value = set
  closeDetails()
}

function closeEditor(): void {
  editorSession += 1
  editing.value = undefined
}

/** A committed save refreshes the library even if its editor was closed while the request was pending. */
const saveSet: typeof adminLearningApi.savePracticeSet = async (type, id, payload) => {
  const session = editorSession
  const key = id === null ? null : setKeyString({ type, id })
  if (key !== null) busyKeys.add(key)
  try {
    const set = await adminLearningApi.savePracticeSet(type, id, payload)
    if (id === null) void reloadContentCounts()
    if (isUnmounted()) return set
    void library.reload()
    if (inDrawer(set)) details.replace(set)
    if (session !== editorSession && editing.value === undefined) {
      actionError.value = null
      showToast(t('adminLearning.activitySaved'))
    }
    return set
  } catch (reason) {
    if (!isUnmounted() && session !== editorSession) {
      const message = resolveApiError(reason, t('adminLearning.saveFailed'))
      if (id !== null && inDrawer({ type, id })) detailsActionError.value = message
      else actionError.value = message
    }
    throw reason
  } finally {
    if (key !== null) busyKeys.delete(key)
  }
}

/** "Activity saved.", for a new set or an edit (draft or published); an edited set's details open again. */
function onSaved(set: AdminPracticeSetDetail): void {
  const wasEditing = Boolean(editing.value)
  closeEditor()
  actionError.value = null
  showToast(t('adminLearning.activitySaved'))
  if (wasEditing) openDetails(set, set)
}

// Status changes run at once per set, without a question: each is easy to take back with the Undo in its toast.

const busyKeys = reactive(new Set<string>())
function setKeyString(set: SetKey): string {
  return `${set.type}:${set.id}`
}

/** What became of the set, as its toast says it (A4.13, A4.14, A4.18, A4.19). */
function outcome(action: PracticeSetAction, set: AdminPracticeSetDetail): string {
  const title = set.title
  switch (action) {
    case 'publish':
      return t('adminLearning.publishedNotice', { title })
    case 'unpublish':
      return t('adminLearning.unpublishedNotice', { title })
    case 'archive':
      return t('adminLearning.archivedNotice', { title })
    case 'restore':
    case 'unarchive':
      // The server decides where the set goes back to: the drafts, or published again.
      return t(set.status === 'DRAFT' ? 'adminLearning.restoredDraftNotice' : 'adminLearning.restoredNotice', { title })
  }
}

function onDetailsAction(action: PracticeSetAction): void {
  if (details.data.value) void runAction(action, details.data.value, { fromDrawer: true })
}

/**
 * Runs a status change of a set from its drawer or its row. Run from the drawer, the drawer then closes on the list,
 * which shows the new status; a drawer opened on the set meanwhile shows it instead. The toast's Undo runs the reverse
 * change, whose own toast offers none. A failure is said in the drawer while it shows the set, otherwise on the page.
 */
async function runAction(
  action: PracticeSetAction,
  target: SetKey,
  { undoable = true, fromDrawer = false }: { undoable?: boolean; fromDrawer?: boolean } = {},
): Promise<void> {
  const key = setKeyString(target)
  if (busyKeys.has(key)) return
  const active = document.activeElement
  const hadRowFocus = !fromDrawer && Boolean(active && tableWrapRef.value?.contains(active))
  hideToast()
  const version = detailsVersion
  const undo = undoable ? UNDO_ACTIONS[action] : undefined
  busyKeys.add(key)
  actionError.value = null
  if (fromDrawer) detailsActionError.value = null
  try {
    const updated = await adminLearningApi.changePracticeSet(target.type, target.id, action)
    showToast(
      outcome(action, updated),
      undo
        ? { label: t('common.undo'), run: () => void runAction(undo, updated, { undoable: false, fromDrawer: false }) }
        : undefined,
    )
    if (inDrawer(target)) {
      if (fromDrawer && version === detailsVersion) closeDetails()
      else {
        details.replace(updated)
        detailsActionError.value = null
      }
    }
    await library.reload()
    if (hadRowFocus && (!document.activeElement || document.activeElement === document.body)) {
      await nextTick()
      const rowBtn = tableWrapRef.value?.querySelector<HTMLButtonElement>(
        `tr[data-set-key="${key}"] .col-actions button`,
      )
      ;(rowBtn ?? tableWrapRef.value)?.focus()
    }
  } catch (reason) {
    const message = resolveApiError(reason, t('adminLearning.saveFailed'))
    if (inDrawer(target)) detailsActionError.value = message
    else actionError.value = message
  } finally {
    busyKeys.delete(key)
  }
}
</script>
