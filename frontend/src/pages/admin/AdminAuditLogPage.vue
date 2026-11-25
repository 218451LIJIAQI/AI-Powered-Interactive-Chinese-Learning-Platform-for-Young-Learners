<template>
  <div class="page-stack">
    <h1 class="sr-only">{{ t('nav.auditLog') }}</h1>
    <div class="page-toolbar">
      <button
        class="btn btn-secondary"
        type="button"
        :disabled="exporting || Boolean(rangeError)"
        :aria-busy="exporting"
        data-testid="admin-audit-export"
        @click="exportCsv"
      >
        <IconGlyph name="download" :size="16" />
        <span>{{ t('common.exportCsv') }}</span>
      </button>
    </div>

    <DataNotice :error="exportError" />

    <section class="panel admin-table-card">
      <h2 id="admin-audit-table-title" class="sr-only">{{ t('adminAudit.tableLabel') }}</h2>
      <form class="admin-filters" role="search" :aria-label="t('adminAudit.filtersLabel')" @submit.prevent>
        <div class="field is-search">
          <label class="field-label" for="admin-audit-search">{{ t('common.search') }}</label>
          <span class="input-with-icon">
            <IconGlyph class="input-icon" name="search" :size="16" />
            <input
              id="admin-audit-search"
              v-model="searchTerm"
              class="input"
              type="search"
              maxlength="200"
              autocomplete="off"
              :placeholder="t('adminAudit.searchPlaceholder')"
              data-testid="admin-audit-search"
            />
          </span>
        </div>
        <div class="field">
          <label class="field-label" for="admin-audit-from">{{ t('adminAudit.fromLabel') }}</label>
          <input
            id="admin-audit-from"
            v-model="from"
            class="input"
            type="date"
            :min="MIN_AUDIT_DATE"
            :max="validTo || MAX_AUDIT_DATE"
            :aria-invalid="Boolean(rangeError)"
            :aria-describedby="rangeError ? 'admin-audit-range-error' : undefined"
            data-testid="admin-audit-from"
          />
        </div>
        <div class="field">
          <label class="field-label" for="admin-audit-to">{{ t('adminAudit.toLabel') }}</label>
          <input
            id="admin-audit-to"
            v-model="to"
            class="input"
            type="date"
            :min="validFrom || MIN_AUDIT_DATE"
            :max="MAX_AUDIT_DATE"
            :aria-invalid="Boolean(rangeError)"
            :aria-describedby="rangeError ? 'admin-audit-range-error' : undefined"
            data-testid="admin-audit-to"
          />
        </div>
        <div class="field">
          <label class="field-label" for="admin-audit-action">{{ t('adminAudit.actionLabel') }}</label>
          <select id="admin-audit-action" v-model="action" class="select" data-testid="admin-audit-action">
            <option value="">{{ t('adminAudit.actionAll') }}</option>
            <option v-for="option in actionOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </div>
        <div class="field">
          <label class="field-label" for="admin-audit-actor">{{ t('adminAudit.actorLabel') }}</label>
          <input
            id="admin-audit-actor"
            v-model="actorTerm"
            class="input"
            type="search"
            maxlength="200"
            autocomplete="off"
            placeholder="name@example.com"
          />
        </div>
        <p v-if="rangeError" id="admin-audit-range-error" class="field-error" role="alert">
          {{ rangeError }}
        </p>
      </form>
      <DataNotice
        :error="actions.error.value"
        :error-title="t('adminAudit.actionsLoadFailed')"
        @retry="actions.reload"
      />
      <div v-if="userId !== null" class="filter-chips" role="group" :aria-label="t('adminAudit.userFilterLabel')">
        <button
          class="filter-chip is-removable"
          type="button"
          :aria-label="t('adminAudit.userChipRemove', { name: userLabel })"
          data-testid="admin-audit-user-chip"
          @click="userId = null"
        >
          <span>{{ t('adminAudit.userChip', { name: userLabel }) }}</span>
          <IconGlyph name="close" :size="14" />
        </button>
      </div>

      <StatePanel
        v-if="log.loading.value"
        tone="loading"
        :title="t('adminAudit.loading')"
        :message="t('adminAudit.loadingHint')"
      />
      <StatePanel
        v-else-if="log.error.value"
        tone="error"
        :title="t('adminAudit.loadError')"
        :message="log.error.value"
        @retry="log.reload"
      />
      <template v-else-if="entries.length">
        <div
          class="data-table-wrap admin-table-wrap"
          role="region"
          aria-labelledby="admin-audit-table-title"
          tabindex="0"
        >
          <table class="data-table admin-table admin-table-fixed">
            <thead>
              <tr>
                <th scope="col">{{ t('adminAudit.colEvent') }}</th>
                <th scope="col" class="admin-audit-col-category">{{ t('adminAudit.category') }}</th>
                <th scope="col" class="admin-audit-col-actor">{{ t('adminAudit.actor') }}</th>
                <th scope="col" class="admin-audit-col-time">{{ t('adminAudit.time') }}</th>
                <th scope="col" class="col-actions admin-col-chevron">
                  <span class="sr-only">{{ t('adminAudit.whatHappened') }}</span>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="entry in entries"
                :key="entry.id"
                :class="{ 'is-selected': entry.id === selectedEntryId }"
                data-testid="admin-audit-row"
                @click="onRowClick(entry.id)"
              >
                <td>
                  <span class="admin-cell-title">
                    <strong>{{ auditActionLabel(t, entry.actionType) }}</strong>
                    <span v-if="entry.targetLabel">{{ entry.targetLabel }}</span>
                  </span>
                </td>
                <td>
                  <span class="tag is-outline">{{ auditCategoryLabel(t, entry.category) }}</span>
                </td>
                <td>
                  <span class="admin-cell-title">
                    <strong>{{ entry.actorName ?? entry.actorEmail ?? t('adminAudit.systemActor') }}</strong>
                    <span v-if="entry.actorName && entry.actorEmail">{{ entry.actorEmail }}</span>
                  </span>
                </td>
                <td class="admin-cell-date">{{ formatDateTime(entry.createdAt) }}</td>
                <td class="col-actions">
                  <button
                    class="btn btn-ghost btn-icon btn-sm"
                    type="button"
                    :aria-label="t('adminAudit.openEntry', { event: auditActionLabel(t, entry.actionType) })"
                    @click.stop="selectEntry(entry.id)"
                  >
                    <IconGlyph name="chevronRight" :size="16" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PaginationBar
          :page="displayedPage"
          :total-pages="totalPages"
          :summary="t('adminAudit.eventCount', { count: formatNumber(totalItems, locale) }, totalItems)"
          :label="t('adminAudit.pagination')"
          icon-only
          @update:page="goToPage"
        />
      </template>
      <StatePanel
        v-else-if="!rangeError"
        tone="empty"
        icon="search"
        spacious
        :title="keyword ? t('adminAudit.emptyTitleSearch', { term: keyword }) : t('adminAudit.emptyTitle')"
        :message="searchOnly ? t('adminAudit.emptyHintSearch') : t('adminAudit.emptyHint')"
      >
        <template v-if="hasFilters" #actions>
          <button class="btn btn-secondary" type="button" data-testid="admin-audit-clear" @click="clearFilters">
            {{ searchOnly ? t('common.clearSearch') : t('common.clearFilters') }}
          </button>
        </template>
      </StatePanel>
    </section>

    <AdminAuditEntryDrawer
      :open="selectedEntryId !== null"
      :entry="selectedEntry"
      :error="entryDetail.error.value"
      @close="selectEntry(null)"
      @retry="entryDetail.reload"
    />

    <AppToast :message="toast" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter, type LocationQueryRaw } from 'vue-router'

import { adminOverviewApi, adminUsersApi } from '@/api/ops'
import AdminAuditEntryDrawer from '@/components/admin/AdminAuditEntryDrawer.vue'
import { useCsvExport } from '@/components/admin/useCsvExport'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import { useDebouncedSearch } from '@/composables/useDebouncedSearch'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { usePageFilters, useRecordById, useResource, useServerPage } from '@/composables/useResource'
import { adminAuditMessages } from '@/plugins/adminAuditMessages'
import { auditActionMessages } from '@/plugins/auditActionMessages'
import type { PageResponse } from '@/types/api'
import type { AuditEntry } from '@/types/ops'
import { auditActionLabel, auditCategoryLabel } from '@/utils/auditActions'
import { formatDateTime, formatNumber, isoDay, parseDate } from '@/utils/format'
import { firstQueryText, pageFromQuery, pageQuery } from '@/utils/routeQuery'
import { parseRecordId } from '@/utils/recordId'
import '@/styles/admin-shared.css'
import '@/styles/ops-admin.css'

const PAGE_SIZE = 10
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/
const MIN_AUDIT_DATE = '1001-01-01'
const MAX_AUDIT_DATE = '9998-12-31'

const route = useRoute()
const router = useRouter()
const { t, locale } = useLocalMessages({ adminAudit: adminAuditMessages, auditActions: auditActionMessages })

function isAuditDateInBounds(value: string): boolean {
  return DATE_PATTERN.test(value) && Boolean(parseDate(value)) && value >= MIN_AUDIT_DATE && value <= MAX_AUDIT_DATE
}

function queryDate(name: string): string {
  const value = firstQueryText(route.query[name])
  return isAuditDateInBounds(value) ? value : ''
}

// Filters and page, kept in the address: q, from, to, action, actor, user and page.

const {
  searchTerm,
  keyword,
  restore: restoreSearch,
  clear: clearSearch,
} = useDebouncedSearch(firstQueryText(route.query.q))
const {
  searchTerm: actorTerm,
  keyword: actor,
  restore: restoreActor,
  clear: clearActor,
} = useDebouncedSearch(firstQueryText(route.query.actor))
const from = ref(queryDate('from'))
const to = ref(queryDate('to'))
const validFrom = computed(() => (from.value && isAuditDateInBounds(from.value) ? from.value : ''))
const validTo = computed(() => (to.value && isAuditDateInBounds(to.value) ? to.value : ''))
const action = ref(firstQueryText(route.query.action))
const userId = ref<number | null>(parseRecordId(route.query.user))
const page = ref(pageFromQuery(route.query.page))

function listQuery(): LocationQueryRaw {
  return {
    q: keyword.value || undefined,
    actor: actor.value || undefined,
    from: validFrom.value || undefined,
    to: validTo.value || undefined,
    action: action.value || undefined,
    user: userId.value === null ? undefined : String(userId.value),
    page: pageQuery(page.value),
  }
}

const rangeError = computed(() => {
  if ((from.value && !isAuditDateInBounds(from.value)) || (to.value && !isAuditDateInBounds(to.value))) {
    return t('adminAudit.outOfRange')
  }
  return validFrom.value && validTo.value && validFrom.value > validTo.value ? t('adminAudit.invalidRange') : null
})

function filters() {
  return {
    keyword: keyword.value || undefined,
    actionType: action.value || undefined,
    actor: actor.value || undefined,
    from: validFrom.value || undefined,
    to: validTo.value || undefined,
    userId: userId.value ?? undefined,
  }
}

const log = useResource<PageResponse<AuditEntry> | null>(() =>
  rangeError.value
    ? Promise.resolve(null)
    : adminOverviewApi.auditLog({ ...filters(), page: page.value, size: PAGE_SIZE }),
)

const {
  items: entries,
  displayedPage,
  totalItems,
  totalPages,
  goToPage,
} = useServerPage(log, page, {
  stepBackFromEmpty: true,
  onPageChange: writeAddress,
})

function writeAddress(): void {
  void router.replace({ query: { ...route.query, ...listQuery() } })
}

const followAddress = usePageFilters(
  [keyword, actor, validFrom, validTo, action, userId, rangeError],
  page,
  log,
  () => {
    if (!rangeError.value) writeAddress()
  },
)

watch(
  [
    () => route.query.q,
    () => route.query.actor,
    () => route.query.from,
    () => route.query.to,
    () => route.query.action,
    () => route.query.user,
    () => route.query.page,
  ],
  () => {
    if (route.name !== 'admin-audit-log') return
    followAddress(pageFromQuery(route.query.page), () => {
      restoreSearch(firstQueryText(route.query.q))
      restoreActor(firstQueryText(route.query.actor))
      from.value = queryDate('from')
      to.value = queryDate('to')
      action.value = firstQueryText(route.query.action)
      userId.value = parseRecordId(route.query.user)
    })
  },
)

function clearFilters(): void {
  clearSearch()
  clearActor()
  from.value = to.value = action.value = ''
  userId.value = null
}

const hasFilters = computed(() =>
  Boolean(keyword.value || actor.value || from.value || to.value || action.value || userId.value !== null),
)

const searchOnly = computed(
  () => Boolean(keyword.value) && !actor.value && !from.value && !to.value && !action.value && userId.value === null,
)

const accountName = useRecordById(userId, async (id) => (await adminUsersApi.get(id)).fullName)
const userLabel = computed(() => accountName.data.value ?? t('adminAudit.userUnknown', { id: userId.value ?? '' }))

const actions = useResource(adminOverviewApi.auditActions)
const actionOptions = computed(() => {
  const available = new Set(actions.data.value ?? [])
  if (action.value) available.add(action.value)
  return [...available]
    .map((value) => ({ value, label: auditActionLabel(t, value) }))
    .sort((left, right) => left.label.localeCompare(right.label, locale.value))
})

// One entry (`?entry=<id>`), in a drawer: taken from the page on screen, or loaded on its own once that page is in.

const selectedEntryId = computed(() => parseRecordId(route.query.entry))
const listedEntry = computed(() => entries.value.find((entry) => entry.id === selectedEntryId.value))
const offPageId = ref<number | null>(null)
watch(
  [selectedEntryId, listedEntry, log.loading],
  ([id, listed, listLoading]) => {
    if (listed || id === null) offPageId.value = null
    else if (!listLoading) offPageId.value = id
    else if (offPageId.value !== id) offPageId.value = null
  },
  { immediate: true },
)
const entryDetail = useRecordById(offPageId, adminOverviewApi.auditEntry)
const selectedEntry = computed(() => listedEntry.value ?? entryDetail.data.value)

function onRowClick(id: number): void {
  if (window.getSelection()?.toString()) return
  selectEntry(id)
}

function selectEntry(id: number | null): void {
  void router.replace({ query: { ...route.query, ...listQuery(), entry: id === null ? undefined : String(id) } })
}

const { exporting, exportError, toast, exportCsv } = useCsvExport({
  load: () => adminOverviewApi.exportAuditLog(filters()),
  fileName: () => `audit-log-${isoDay(new Date())}.csv`,
  successMessage: () => t('adminAudit.csvDownloaded'),
  failureMessage: () => t('adminAudit.exportFailed'),
})
</script>
