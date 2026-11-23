<template>
  <div class="page-stack">
    <PageHeaderBlock :title="t('nav.classes')" hide-title />
    <!-- New class sits at the right end of the tab row, like New activity and Invite admin (A3.01). -->
    <AdminContentTabs>
      <button class="btn btn-primary" type="button" data-testid="admin-classes-new" @click="openNewClass">
        <IconGlyph name="plus" :size="16" />{{ t('adminClasses.newClass.title') }}
      </button>
    </AdminContentTabs>
    <DataNotice :error="pageError" />

    <section class="panel admin-table-card">
      <h2 id="admin-classes-list-title" class="sr-only">{{ t('adminClasses.list.tableLabel') }}</h2>
      <form class="admin-filters" role="search" @submit.prevent>
        <div class="field is-search">
          <label class="field-label" for="admin-classes-search">{{ t('common.search') }}</label>
          <span class="input-with-icon">
            <IconGlyph class="input-icon" name="search" :size="16" />
            <input
              id="admin-classes-search"
              v-model="searchTerm"
              class="input"
              type="search"
              maxlength="200"
              autocomplete="off"
              :placeholder="t('adminClasses.list.searchPlaceholder')"
              data-testid="admin-classes-search"
            />
          </span>
        </div>
        <div class="field">
          <label class="field-label" for="admin-classes-type">{{ t('adminClasses.list.type') }}</label>
          <select id="admin-classes-type" v-model="type" class="select" data-testid="admin-classes-type">
            <option :value="null">{{ t('adminClasses.list.allTypes') }}</option>
            <option v-for="value in TYPES" :key="value" :value="value">{{ t(`adminClasses.types.${value}`) }}</option>
          </select>
        </div>
        <div class="field">
          <label class="field-label" for="admin-classes-status">{{ t('adminClasses.list.status') }}</label>
          <select id="admin-classes-status" v-model="status" class="select" data-testid="admin-classes-status">
            <option :value="null">{{ t('adminClasses.list.allStatuses') }}</option>
            <option v-for="value in CONTENT_STATUSES" :key="value" :value="value">
              {{ t(`adminClasses.statuses.${value}`) }}
            </option>
          </select>
        </div>
      </form>

      <StatePanel
        v-if="resource.loading.value"
        tone="loading"
        :title="t('adminClasses.list.loadingTitle')"
        :message="t('adminClasses.list.loadingHint')"
      />
      <StatePanel
        v-else-if="resource.error.value"
        tone="error"
        icon="alert"
        :title="t('adminClasses.list.loadErrorTitle')"
        :message="resource.error.value"
        @retry="resource.reload"
      />
      <template v-else-if="items.length">
        <div
          class="data-table-wrap admin-table-wrap"
          role="region"
          aria-labelledby="admin-classes-list-title"
          tabindex="0"
        >
          <table class="data-table admin-table admin-table-fixed admin-classes-table">
            <thead>
              <tr>
                <th scope="col">{{ t('adminClasses.list.colTitle') }}</th>
                <th scope="col" class="admin-classes-col-type">{{ t('adminClasses.list.type') }}</th>
                <th scope="col" class="admin-classes-col-class">{{ t('adminClasses.list.colClass') }}</th>
                <th scope="col" class="admin-classes-col-status">{{ t('adminClasses.list.status') }}</th>
                <th scope="col" class="admin-classes-col-updated">{{ t('adminClasses.list.colUpdated') }}</th>
                <th scope="col" class="col-actions admin-classes-col-open">
                  <span class="sr-only">{{ t('adminClasses.list.colActions') }}</span>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in items"
                :key="`${row.classId}-${row.type}-${row.id}`"
                data-testid="admin-classes-row"
                @click="router.push(rowRoute(row))"
              >
                <td class="admin-cell-strong admin-cell-line" :title="row.title">{{ row.title }}</td>
                <td>
                  <span class="tag is-outline">{{ t(`adminClasses.types.${row.type}`) }}</span>
                </td>
                <td class="admin-cell-muted admin-cell-line" :title="row.classTitle ?? undefined">
                  <template v-if="row.classTitle">{{ row.classTitle }}</template>
                  <template v-else>
                    <span aria-hidden="true">—</span>
                  </template>
                </td>
                <td>
                  <StatusPill :label="t(`adminClasses.statuses.${row.status}`)" :tone="contentStatusTone(row.status)" />
                </td>
                <td class="admin-cell-date">{{ formatShortDate(row.updatedAt) }}</td>
                <td class="col-actions">
                  <RouterLink
                    class="btn btn-secondary btn-sm"
                    :to="rowRoute(row)"
                    :aria-label="t('adminClasses.list.openLabel', { title: row.title })"
                    @click.stop
                  >
                    {{ t('common.open') }}<IconGlyph name="chevronRight" :size="16" />
                  </RouterLink>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationBar
          :page="displayedPage"
          :total-pages="totalPages"
          :label="t('adminClasses.list.pagination')"
          icon-only
          @update:page="goToPage"
        />
      </template>
      <StatePanel
        v-else-if="filtered"
        tone="empty"
        icon="search"
        spacious
        :title="
          keyword
            ? t('adminClasses.list.emptyTitleSearch', { term: keyword })
            : t('adminClasses.list.emptyTitleFiltered')
        "
        :message="searchOnly ? t('adminClasses.list.emptyMessageSearch') : t('adminClasses.list.emptyMessageFiltered')"
      >
        <template #actions>
          <button class="btn btn-secondary" type="button" data-testid="admin-classes-clear" @click="clearFilters">
            {{ searchOnly ? t('common.clearSearch') : t('common.clearFilters') }}
          </button>
        </template>
      </StatePanel>
      <StatePanel
        v-else
        tone="empty"
        icon="classes"
        spacious
        :title="t('adminClasses.list.emptyTitle')"
        :message="t('adminClasses.list.emptyMessage')"
      >
        <template #actions>
          <button class="btn btn-primary" type="button" @click="openNewClass">
            <IconGlyph name="plus" :size="16" />{{ t('adminClasses.newClass.title') }}
          </button>
        </template>
      </StatePanel>
    </section>

    <AppOverlay
      :open="newClassOpen"
      :title="t('adminClasses.newClass.title')"
      :subtitle="t('adminClasses.newClass.subtitle')"
      :busy="creating"
      test-id="new-class-dialog"
      @close="newClassOpen = false"
    >
      <form :id="newClassFormId" novalidate @submit.prevent="createClass">
        <DataNotice :error="topicsError">
          <template #actions>
            <button class="btn btn-secondary btn-sm" type="button" @click="reloadTopics">
              {{ t('common.retry') }}
            </button>
          </template>
        </DataNotice>
        <ClassDetailsFields
          v-model="newClass"
          :themes="themes"
          :learn-units="learnUnits"
          :disabled="creating"
          :title-error="titleError"
          autofocus
          @title-blur="checkTitleOnBlur"
        />
        <p v-if="createError" class="field-error" role="alert">{{ createError }}</p>
      </form>
      <template #footer>
        <!-- A press on a footer button keeps the focus in the title: its check on blur would move the footer mid-click. -->
        <button
          class="btn btn-secondary"
          type="button"
          :disabled="creating"
          @mousedown.prevent
          @click="newClassOpen = false"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          class="btn btn-primary"
          type="submit"
          :form="newClassFormId"
          :disabled="creating || Boolean(titleError)"
          :aria-busy="creating"
          data-testid="new-class-create"
          @mousedown.prevent
        >
          {{ t('adminClasses.newClass.create') }}
        </button>
      </template>
    </AppOverlay>

    <AppToast :message="toast" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, useId, watch } from 'vue'
import { RouterLink, useRoute, useRouter, type RouteLocationRaw } from 'vue-router'

import { adminClassesApi } from '@/api/adminClasses'
import { reloadContentCounts } from '@/components/admin/adminContentCounts'
import AdminContentTabs from '@/components/admin/AdminContentTabs.vue'
import { contentStatusTone } from '@/components/admin/adminDisplay'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { pageFromQuery, pageQuery } from '@/utils/pageQuery'
import { useClassFormOptions } from '@/components/admin/useClassFormOptions'
import ClassDetailsFields from '@/components/classes/ClassDetailsFields.vue'
import AppOverlay from '@/components/common/AppOverlay.vue'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useDebouncedSearch } from '@/composables/useDebouncedSearch'
import { useFieldCheck } from '@/composables/useFieldCheck'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { usePageFilters, useResource, useServerPage } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { adminClassesMessages } from '@/plugins/adminClassesMessages'
import type { AdminContentRow, AdminContentType } from '@/types/adminClasses'
import { CONTENT_STATUSES, type ContentStatus } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { classDetailsForm, classDetailsPayload } from '@/utils/classes'
import { formatShortDate } from '@/utils/format'
import { firstQueryText, queryOption } from '@/utils/queryText'
import '@/styles/admin-shared.css'
import '@/styles/ops-admin.css'

const PAGE_SIZE = 10
const TYPES: readonly AdminContentType[] = ['CLASS', 'LESSON']

const { t } = useLocalMessages({ adminClasses: adminClassesMessages })
const route = useRoute()
const router = useRouter()
const { toast, showToast } = useToast()
const isUnmounted = useIsUnmounted()

// The list, kept in the address (q, type, status and page) so that Back from a class or a reload keeps it.

const {
  searchTerm,
  keyword,
  restore: restoreSearch,
  clear: clearSearch,
} = useDebouncedSearch(firstQueryText(route.query.q))
const type = ref<AdminContentType | null>(queryOption(route.query.type, TYPES) ?? null)
const status = ref<ContentStatus | null>(queryOption(route.query.status, CONTENT_STATUSES) ?? null)
const page = ref(pageFromQuery(route.query.page))

const resource = useResource(() =>
  adminClassesApi.list({
    keyword: keyword.value || undefined,
    type: type.value ?? undefined,
    status: status.value ?? undefined,
    page: page.value,
    size: PAGE_SIZE,
  }),
)

/** Puts the list in the address; an empty filter and the first page are left out. */
function writeAddress(): void {
  void router.replace({
    query: {
      q: keyword.value || undefined,
      type: type.value ?? undefined,
      status: status.value ?? undefined,
      page: pageQuery(page.value),
    },
  })
}

// A page left without rows (a class deleted elsewhere, or an address past the end) steps back to the last one with rows.
const { items, displayedPage, totalPages, goToPage } = useServerPage(resource, page, {
  stepBackFromEmpty: true,
  onPageChange: writeAddress,
})

// Form changes start a search; navigating to a saved search also restores its page.
const followAddress = usePageFilters([keyword, type, status], page, resource, writeAddress)

// The address can also change under the page (the sidebar's Content link opens it with no query): the list follows.
watch([() => route.query.q, () => route.query.type, () => route.query.status, () => route.query.page], () => {
  if (route.name !== 'admin-classes') return
  followAddress(pageFromQuery(route.query.page), () => {
    restoreSearch(firstQueryText(route.query.q))
    type.value = queryOption(route.query.type, TYPES) ?? null
    status.value = queryOption(route.query.status, CONTENT_STATUSES) ?? null
  })
})

const filtered = computed(() => Boolean(keyword.value) || type.value !== null || status.value !== null)
/** Only the search box narrows the list: the empty state then offers "Clear search", not "Clear filters". */
const searchOnly = computed(() => Boolean(keyword.value) && type.value === null && status.value === null)

function clearFilters(): void {
  clearSearch()
  type.value = null
  status.value = null
}

/** A class row opens its class page; a lesson row opens the lesson in its editor. Drafts open the same pages. */
function rowRoute(row: AdminContentRow): RouteLocationRaw {
  return row.type === 'CLASS'
    ? { name: 'admin-class', params: { classId: row.id } }
    : { name: 'admin-lesson', params: { classId: row.classId, lessonId: row.id } }
}

// A class deleted on its page comes back here with `?deleted=<title>`: said once, then taken out of the address.
onMounted(() => {
  const deleted = route.query.deleted
  if (typeof deleted !== 'string' || !deleted) return
  showToast(t('adminClasses.list.deleted', { title: deleted }))
  void router.replace({ query: { ...route.query, deleted: undefined } })
})

// New class: a draft with its details, then its page.

const newClassFormId = useId()
const newClassOpen = ref(false)
const creating = ref(false)
const createError = ref<string | null>(null)
const pageError = ref<string | null>(null)
const newClass = ref(classDetailsForm())
const { themes, topicsError, reloadTopics, learnUnits, load: loadFormOptions } = useClassFormOptions()
const newClassTitle = computed(() => newClass.value.title)
const {
  error: titleError,
  validate: validateTitle,
  validateOnBlur: checkTitleOnBlur,
  reset: resetTitleError,
} = useFieldCheck(newClassTitle, (value) => (value.trim() ? null : t('adminClasses.form.titleMissing')))

watch(newClass, () => {
  createError.value = null
})

function openNewClass(): void {
  newClass.value = classDetailsForm()
  resetTitleError()
  createError.value = null
  pageError.value = null
  loadFormOptions()
  newClassOpen.value = true
}

async function createClass(): Promise<void> {
  if (creating.value || !validateTitle()) return
  const payload = classDetailsPayload(newClass.value)
  creating.value = true
  createError.value = null
  pageError.value = null
  let created
  try {
    created = await adminClassesApi.create(payload)
  } catch (reason) {
    if (!isUnmounted()) createError.value = resolveApiError(reason, t('adminClasses.newClass.error'))
    return
  } finally {
    if (!isUnmounted()) creating.value = false
  }
  if (isUnmounted()) return
  newClassOpen.value = false
  try {
    const failure = await router.push({ name: 'admin-class', params: { classId: created.id } })
    if (!failure) return
  } catch (reason) {
    if (!isUnmounted()) pageError.value = resolveApiError(reason)
  }
  if (!isUnmounted()) {
    void reloadContentCounts()
    await resource.reload()
  }
}
</script>
