<template>
  <div class="page-stack">
    <PageHeaderBlock :title="t('adminAi.usageTitle')" hide-title />
    <AdminAiTabs />
    <DataNotice :error="exportError" />

    <StatePanel
      v-if="summaryResource.loading.value"
      heading-tag="h2"
      tone="loading"
      :title="t('adminAi.usageLoading')"
      :message="t('adminAi.usageLoadingHint')"
    />
    <StatePanel
      v-else-if="summaryResource.error.value"
      heading-tag="h2"
      tone="error"
      icon="alert"
      :title="t('adminAi.usageError')"
      :message="summaryResource.error.value"
      @retry="summaryResource.reload"
    />
    <dl v-else-if="summary" class="admin-ai-totals" :aria-label="t('adminAi.totalsLabel')">
      <div v-for="figure in figures" :key="figure.key" class="panel admin-figure">
        <dt>{{ figure.label }}</dt>
        <dd>
          <strong class="admin-figure-value">{{ formatNumber(figure.value) }}</strong>
          <span class="admin-meta">{{ figure.note }}</span>
        </dd>
      </div>
    </dl>

    <div class="admin-ai-usage">
      <section class="panel admin-table-card">
        <div class="admin-section-head">
          <h2 :id="`${baseId}-log`" class="card-title">{{ t('adminAi.requestLog') }}</h2>
          <button
            class="btn btn-secondary"
            type="button"
            :disabled="exporting"
            :aria-busy="exporting"
            @click="exportCsv"
          >
            <IconGlyph name="download" :size="16" />
            <span>{{ t('common.exportCsv') }}</span>
          </button>
        </div>

        <div class="admin-filters">
          <div class="field">
            <label class="field-label" :for="`${baseId}-feature`">{{ t('adminAi.feature') }}</label>
            <select :id="`${baseId}-feature`" v-model="filters.featureType" class="select" @change="filterLog">
              <option value="">{{ t('adminAi.allFeatures') }}</option>
              <option v-for="feature in AI_FEATURES" :key="feature" :value="feature">
                {{ aiFeatureLabel(feature, t) }}
              </option>
            </select>
          </div>
          <div class="field">
            <label class="field-label" :for="`${baseId}-result`">{{ t('adminAi.result') }}</label>
            <select :id="`${baseId}-result`" v-model="filters.status" class="select" @change="filterLog">
              <option value="">{{ t('adminAi.allResults') }}</option>
              <option v-for="(meta, result) in RESULT_META" :key="result" :value="result">
                {{ t(meta.labelKey) }}
              </option>
            </select>
          </div>
          <div class="field">
            <label class="field-label" :for="`${baseId}-role`">{{ t('adminAi.role') }}</label>
            <select :id="`${baseId}-role`" v-model="filters.userRole" class="select" @change="filterLog">
              <option value="">{{ t('adminAi.allRoles') }}</option>
              <option v-for="role in USER_ROLES" :key="role" :value="role">{{ t(ROLE_KEYS[role]) }}</option>
            </select>
          </div>
        </div>

        <StatePanel
          v-if="logResource.loading.value"
          tone="loading"
          :title="t('adminAi.logLoading')"
          :message="t('adminAi.logLoadingHint')"
        />
        <StatePanel
          v-else-if="logResource.error.value"
          tone="error"
          icon="alert"
          :title="t('adminAi.logError')"
          :message="logResource.error.value"
          @retry="logResource.reload"
        />
        <template v-else-if="rows.length">
          <div class="data-table-wrap admin-table-wrap" role="region" :aria-labelledby="`${baseId}-log`" tabindex="0">
            <table class="data-table admin-table admin-table-static admin-ai-log">
              <thead>
                <tr>
                  <th scope="col">{{ t('adminAi.time') }}</th>
                  <th scope="col">{{ t('adminAi.user') }}</th>
                  <th scope="col">{{ t('adminAi.role') }}</th>
                  <th scope="col">{{ t('adminAi.feature') }}</th>
                  <th scope="col">{{ t('adminAi.result') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in rows" :key="row.id">
                  <td class="admin-cell-date">{{ formatDateTime(row.createdAt) }}</td>
                  <td class="admin-cell-strong">{{ row.userName }}</td>
                  <td>
                    <span v-if="isRole(row.userRole)" class="tag is-outline">{{ t(ROLE_KEYS[row.userRole]) }}</span>
                    <span v-else aria-hidden="true">—</span>
                  </td>
                  <td class="admin-cell-muted">{{ aiFeatureLabel(row.featureKey, t) }}</td>
                  <td :title="row.errorSummary ?? undefined">
                    <StatusPill :label="t(RESULT_META[row.status].labelKey)" :tone="RESULT_META[row.status].tone" />
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <PaginationBar
            icon-only
            :page="displayedPage"
            :total-pages="totalPages"
            :label="t('adminAi.requestLog')"
            @update:page="goToPage"
          />
        </template>
        <StatePanel
          v-else
          tone="empty"
          icon="search"
          :title="t('adminAi.noRequests')"
          :message="t('adminAi.noRequestsHint')"
        />
        <p class="callout is-compact">
          <IconGlyph name="shield" :size="16" />
          <span>{{ t('adminAi.logNote') }}</span>
        </p>
      </section>

      <section v-if="summary" class="panel admin-list" :aria-labelledby="`${baseId}-features`">
        <header class="admin-list-head">
          <h2 :id="`${baseId}-features`" class="card-title">{{ t('adminAi.byFeature') }}</h2>
        </header>
        <ul class="admin-shares">
          <li v-for="row in summary.byFeature" :key="row.featureKey">
            <span class="admin-share-head">
              <span>{{ aiFeatureLabel(row.featureKey, t) }}</span>
              <span>{{ formatNumber(row.requests) }} · {{ percentOf(row.requests) }}</span>
            </span>
            <span class="admin-share-bar" aria-hidden="true">
              <span :style="{ width: `${ratioPercent(row.requests, summary.totalRequests)}%` }" />
            </span>
          </li>
        </ul>
      </section>
    </div>
    <AppToast :message="toast" />
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, useId } from 'vue'

import { adminAiApi } from '@/api/ai'
import { aiFeatureLabel } from '@/components/admin/adminDisplay'
import AdminAiTabs from '@/components/admin/AdminAiTabs.vue'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill, { type PillTone } from '@/components/common/StatusPill.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource, useServerPage } from '@/composables/useResource'
import { useCsvExport } from '@/components/admin/useCsvExport'
import { adminAiMessages } from '@/plugins/adminAiMessages'
import { opsLabelMessages } from '@/plugins/opsLabelMessages'
import type { AdminAiUsageFilters } from '@/types/ai'
import { AI_FEATURES, USER_ROLES, type AiResultStatus, type UserRole } from '@/types/api'
import { formatDateTime, formatNumber, formatPercent, ratioPercent } from '@/utils/format'
import { ROLE_KEYS } from '@/utils/opsLabels'
import '@/styles/admin-shared.css'
import '@/styles/ops-admin.css'

/**
 * Admin AI usage: all-time totals by result, requests by feature, and the request log (who, when, which feature,
 * the result) with filters, paging and a CSV export. Message text is never logged.
 */
const RESULT_META: Readonly<Record<AiResultStatus, { labelKey: string; tone: PillTone }>> = {
  SUCCESS: { labelKey: 'adminAi.successful', tone: 'success' },
  UNAVAILABLE: { labelKey: 'adminAi.unavailable', tone: 'warning' },
  FAILED: { labelKey: 'adminAi.failed', tone: 'danger' },
}
const PAGE_SIZE = 10

const { t } = useLocalMessages({ adminAi: adminAiMessages, opsLabels: opsLabelMessages })

/** A role of today's platform; an older request may name one that no longer exists, which shows no tag. */
function isRole(role: string | null): role is UserRole {
  return USER_ROLES.some((current) => role === current)
}
const baseId = useId()

const summaryResource = useResource(adminAiApi.usageSummary)
const summary = summaryResource.data
const filters = reactive<Required<AdminAiUsageFilters>>({ featureType: '', status: '', userRole: '' })
const page = ref(0)
const logResource = useResource(() => adminAiApi.usageLogs(filters, page.value, PAGE_SIZE))
const { items: rows, displayedPage, totalPages, goToPage } = useServerPage(logResource, page)
const { exporting, exportError, toast, exportCsv } = useCsvExport({
  load: () => adminAiApi.exportUsageLogs(filters),
  fileName: 'ai-usage-log.csv',
  successMessage: () => t('adminAi.csvDownloaded'),
  failureMessage: () => t('adminAi.csvError'),
})

function filterLog(): void {
  logResource.data.value = null
  goToPage(0)
}

/** Share of all requests, e.g. "33.3%". */
function percentOf(count: number): string {
  return formatPercent(ratioPercent(count, summary.value?.totalRequests, { digits: 1 }), { digits: 1 })
}

/** The all-time totals: each figure with what it means (A5.04). */
const figures = computed(() => {
  const totals = summary.value
  if (!totals) return []
  return [
    { key: 'requests', label: t('adminAi.requests'), value: totals.totalRequests, note: t('adminAi.allTime') },
    { key: 'successful', label: t('adminAi.successful'), value: totals.successful, note: percentOf(totals.successful) },
    {
      key: 'unavailable',
      label: t('adminAi.unavailable'),
      value: totals.unavailable,
      note: t('adminAi.unavailableHint'),
    },
    { key: 'failed', label: t('adminAi.failed'), value: totals.failed, note: t('adminAi.failedHint') },
  ]
})
</script>
