<template>
  <div class="admin-audit page-stack">
    <PageHeaderBlock :title="t('adminAudit.pageTitle')" :description="t('adminAudit.lede')">
      <template #actions>
        <button
          class="btn btn-secondary"
          type="button"
          :disabled="exporting || Boolean(rangeError)"
          :aria-busy="exporting"
          data-testid="admin-audit-export"
          @click="exportCsv"
        >
          <IconGlyph name="download" :size="16" />
          {{ t('adminAudit.exportCsv') }}
        </button>
      </template>
    </PageHeaderBlock>

    <DataNotice :error="actionError" />

    <section class="admin-sheet" aria-labelledby="admin-audit-table-title">
      <h2 id="admin-audit-table-title" class="sr-only">{{ t('adminAudit.tableLabel') }}</h2>
      <form class="admin-toolbar" role="search" :aria-label="t('adminAudit.filtersLabel')" @submit.prevent>
        <div class="field admin-toolbar-search">
          <label class="field-label" for="admin-audit-search">{{ t('adminAudit.searchLabel') }}</label>
          <span class="input-with-icon">
            <IconGlyph class="input-icon" name="search" :size="16" />
            <input
              id="admin-audit-search"
              v-model="searchTerm"
              class="input"
              type="search"
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
            :max="to || undefined"
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
            :min="from || undefined"
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
        <div class="field admin-audit-actor">
          <label class="field-label" for="admin-audit-actor">{{ t('adminAudit.actorLabel') }}</label>
          <input
            id="admin-audit-actor"
            v-model="actorTerm"
            class="input"
            type="search"
            autocomplete="off"
            placeholder="name@example.com"
            data-testid="admin-audit-actor"
          />
        </div>
        <p v-if="rangeError" id="admin-audit-range-error" class="field-error admin-audit-range-error" role="alert">
          {{ rangeError }}
        </p>
      </form>

      <div v-if="log.loading.value" class="admin-sheet-body">
        <StatePanel tone="loading" :title="t('adminAudit.loading')" :message="t('adminAudit.loadingHint')" />
      </div>
      <div v-else-if="log.error.value && !entries.length" class="admin-sheet-body">
        <StatePanel tone="error" icon="alert" :title="t('adminAudit.loadError')" :message="log.error.value">
          <template #actions>
            <button class="btn btn-primary" type="button" @click="log.reload">{{ t('adminAudit.retry') }}</button>
          </template>
        </StatePanel>
      </div>
      <template v-else-if="entries.length">
        <div class="data-table-wrap" role="region" :aria-label="t('adminAudit.tableLabel')" tabindex="0">
          <table class="data-table admin-table">
            <thead>
              <tr>
                <th scope="col">{{ t('adminAudit.colEvent') }}</th>
                <th scope="col">{{ t('adminAudit.colCategory') }}</th>
                <th scope="col">{{ t('adminAudit.colActor') }}</th>
                <th scope="col" class="col-numeric">{{ t('adminAudit.colTime') }}</th>
                <th scope="col" class="col-actions">
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
                @click="openEntry(entry.id)"
              >
                <td>
                  <span class="admin-cell-person">
                    <span class="admin-kind-mark" aria-hidden="true">
                      <IconGlyph :name="auditActionIcon(entry.actionType, entry.category)" :size="16" />
                    </span>
                    <span class="admin-cell-title">
                      <strong>{{ actionLabel(entry.actionType) }}</strong>
                      <span v-if="entry.targetLabel">{{ entry.targetLabel }}</span>
                    </span>
                  </span>
                </td>
                <td>
                  <span class="tag">{{ categoryLabel(entry.category) }}</span>
                </td>
                <td>
                  <span class="admin-cell-title">
                    <strong>{{ entry.actorName ?? entry.actorEmail ?? t('adminAudit.systemActor') }}</strong>
                    <span v-if="entry.actorName && entry.actorEmail">{{ entry.actorEmail }}</span>
                  </span>
                </td>
                <td class="col-numeric admin-cell-date">
                  {{ formatDateTime(entry.createdAt) }}
                </td>
                <td class="col-actions">
                  <button
                    class="btn btn-ghost btn-icon btn-sm"
                    type="button"
                    :aria-label="t('adminAudit.openEntry', { event: actionLabel(entry.actionType) })"
                    @click.stop="openEntry(entry.id)"
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
            :info="t('adminAudit.showing', showingRange)"
            :label="t('adminAudit.pagination')"
            icon-only
            test-id-prefix="admin-audit"
            @update:current-page="goToPage($event - 1)"
          />
        </div>
      </template>
      <div v-else-if="!rangeError" class="admin-sheet-body">
        <StatePanel tone="empty" icon="search" :title="t('adminAudit.emptyTitle')" :message="t('adminAudit.emptyHint')">
          <template #actions>
            <button class="btn btn-secondary" type="button" @click="clearFilters">
              {{ t('adminAudit.clearFilters') }}
            </button>
          </template>
        </StatePanel>
      </div>
    </section>

    <AppOverlay
      :open="selectedEntryId !== null"
      variant="drawer"
      :title="selectedEntry ? actionLabel(selectedEntry.actionType) : t('adminAudit.entryLoading')"
      :subtitle="
        selectedEntry
          ? t('adminAudit.entryMeta', {
              category: categoryLabel(selectedEntry.category),
              date: formatDateTime(selectedEntry.createdAt),
            })
          : ''
      "
      test-id="admin-audit-drawer"
      @close="closeEntry"
    >
      <p v-if="entryError" class="alert alert-danger" role="alert">{{ entryError }}</p>
      <template v-else-if="selectedEntry">
        <section class="admin-audit-what" aria-labelledby="admin-audit-what-title">
          <h3 id="admin-audit-what-title" class="admin-reader-label">{{ t('adminAudit.whatHappened') }}</h3>
          <p>{{ whatHappened }}</p>
        </section>
        <dl class="kv-list admin-audit-facts">
          <dt>{{ t('adminAudit.kvActor') }}</dt>
          <dd>
            <span class="admin-cell-title">
              <strong>{{ selectedEntry.actorName ?? selectedEntry.actorEmail ?? t('adminAudit.systemActor') }}</strong>
              <span v-if="selectedEntry.actorName && selectedEntry.actorEmail">{{ selectedEntry.actorEmail }}</span>
            </span>
          </dd>
          <template v-if="selectedEntry.targetLabel">
            <dt>{{ t('adminAudit.kvTarget') }}</dt>
            <dd>{{ selectedEntry.targetLabel }}</dd>
          </template>
          <template v-if="selectedEntry.reason">
            <dt>{{ t('adminAudit.kvReason') }}</dt>
            <dd data-testid="admin-audit-reason">{{ selectedEntry.reason }}</dd>
          </template>
          <dt>{{ t('adminAudit.kvTime') }}</dt>
          <dd>{{ formatFullDateTime(selectedEntry.createdAt) }}</dd>
          <dt>{{ t('adminAudit.kvCategory') }}</dt>
          <dd>{{ categoryLabel(selectedEntry.category) }}</dd>
        </dl>
        <p class="callout">
          <IconGlyph name="lock" :size="18" />
          <span>{{ t('adminAudit.immutable') }}</span>
        </p>
      </template>
      <template v-if="selectedEntry?.targetUserId" #footer>
        <RouterLink
          class="btn btn-secondary"
          :to="{ name: 'admin-users', query: { user: selectedEntry.targetUserId } }"
          data-testid="admin-audit-open-target"
        >
          {{ t('adminAudit.openTarget', { target: selectedEntry.targetLabel ?? selectedEntry.targetType ?? '' }) }}
          <IconGlyph name="chevronRight" :size="16" />
        </RouterLink>
      </template>
    </AppOverlay>

    <AppToast :message="toast" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter, type LocationQuery } from 'vue-router'

import { adminOverviewApi } from '@/api/ops'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import AppOverlay from '@/components/common/AppOverlay.vue'
import AppToast from '@/components/common/AppToast.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useAuditLabels } from '@/composables/useAuditLabels'
import { useDebouncedSearch } from '@/composables/useDebouncedSearch'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource, useServerPage } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { adminAuditMessages } from '@/plugins/adminAuditMessages'
import { auditActionMessages } from '@/plugins/auditActionMessages'
import type { PageResponse } from '@/types/api'
import type { AuditEntry, AuditQuery } from '@/types/ops'
import { resolveApiError } from '@/utils/apiError'
import { auditActionIcon } from '@/utils/auditActions'
import { saveBlobDownload } from '@/utils/download'
import { formatDateTime, formatFullDateTime } from '@/utils/format'
import '@/styles/ops-admin.css'

const PAGE_SIZE = 10
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

const route = useRoute()
const router = useRouter()
const { t } = useLocalMessages({ adminAudit: adminAuditMessages, auditActions: auditActionMessages })
const { actionLabel, categoryLabel } = useAuditLabels(t)
const { actionError, clearFeedback, showError } = useActionFeedback()
const { toast, showToast } = useToast()

function queryText(name: string): string {
  const value = route.query[name]
  return typeof value === 'string' ? value.trim() : ''
}

function queryDate(name: string): string {
  const value = queryText(name)
  return DATE_PATTERN.test(value) ? value : ''
}

// ----- filters (kept in the URL: q, from, to, action, actor) -----

const { searchTerm, keyword } = useDebouncedSearch()
const { searchTerm: actorTerm, keyword: actor } = useDebouncedSearch()
searchTerm.value = keyword.value = queryText('q')
actorTerm.value = actor.value = queryText('actor')
const from = ref(queryDate('from'))
const to = ref(queryDate('to'))
const action = ref(queryText('action'))
const page = ref(0)

const rangeError = computed(() =>
  from.value && to.value && from.value > to.value ? t('adminAudit.invalidRange') : null,
)

function filters(): Omit<AuditQuery, 'page' | 'size'> {
  const query: Omit<AuditQuery, 'page' | 'size'> = {}
  if (keyword.value) query.keyword = keyword.value
  if (action.value) query.actionType = action.value
  if (actor.value) query.actor = actor.value
  if (from.value) query.from = from.value
  if (to.value) query.to = to.value
  return query
}

const log = useResource<PageResponse<AuditEntry> | null>(() =>
  rangeError.value
    ? Promise.resolve(null)
    : adminOverviewApi.auditLog({ ...filters(), page: page.value, size: PAGE_SIZE }),
)
const { items: entries, totalPages, range: showingRange, goToPage } = useServerPage(log, page)

watch([keyword, actor, from, to, action], () => {
  page.value = 0
  const query: LocationQuery = { ...route.query }
  const values: Record<string, string> = {
    q: keyword.value,
    actor: actor.value,
    from: from.value,
    to: to.value,
    action: action.value,
  }
  for (const [name, value] of Object.entries(values)) {
    if (value) query[name] = value
    else delete query[name]
  }
  void router.replace({ query })
  if (rangeError.value) {
    log.data.value = null
    return
  }
  void log.reload()
})

function clearFilters(): void {
  searchTerm.value = actorTerm.value = ''
  keyword.value = actor.value = ''
  from.value = to.value = action.value = ''
}

const actions = useResource(() => adminOverviewApi.auditActions())
const actionOptions = computed(() =>
  (actions.data.value ?? [])
    .map((value) => ({ value, label: actionLabel(value) }))
    .sort((left, right) => left.label.localeCompare(right.label)),
)

// ----- one entry (`?entry=<id>`) -----

const selectedEntryId = computed<number | null>(() => {
  const id = Number(route.query.entry)
  return Number.isInteger(id) && id > 0 ? id : null
})
const fetchedEntry = ref<AuditEntry | null>(null)
const entryError = ref<string | null>(null)

const selectedEntry = computed<AuditEntry | null>(() => {
  const id = selectedEntryId.value
  if (id === null) return null
  return entries.value.find((entry) => entry.id === id) ?? (fetchedEntry.value?.id === id ? fetchedEntry.value : null)
})

watch(
  [selectedEntryId, log.data],
  async ([id]) => {
    entryError.value = null
    if (id === null || selectedEntry.value || log.loading.value) return
    try {
      fetchedEntry.value = await adminOverviewApi.auditEntry(id)
    } catch (failure) {
      entryError.value = resolveApiError(failure, t('adminAudit.entryError'))
    }
  },
  { immediate: true },
)

function openEntry(id: number): void {
  void router.replace({ query: { ...route.query, entry: String(id) } })
}

function closeEntry(): void {
  const query = { ...route.query }
  delete query.entry
  void router.replace({ query })
}

const whatHappened = computed(() => {
  const entry = selectedEntry.value
  if (!entry) return ''
  const description = entry.description?.trim() || actionLabel(entry.actionType)
  const actorName = entry.actorName ?? entry.actorEmail
  return actorName ? t('adminAudit.happenedBy', { actor: actorName, description }) : description
})

// ----- export -----

const exporting = ref(false)

async function exportCsv(): Promise<void> {
  if (rangeError.value) return
  exporting.value = true
  clearFeedback()
  try {
    const download = await adminOverviewApi.exportAuditLog(filters())
    saveBlobDownload(download, `audit-log-${new Date().toISOString().slice(0, 10)}.csv`)
    showToast(t('adminAudit.csvDownloaded'))
  } catch (failure) {
    showError(resolveApiError(failure, t('adminAudit.exportFailed')))
  } finally {
    exporting.value = false
  }
}
</script>
