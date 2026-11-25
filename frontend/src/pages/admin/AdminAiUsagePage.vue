<template>
  <div class="page-stack">
    <h1 class="sr-only">{{ t('adminAi.usageTitle') }}</h1>
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

    <div class="admin-ai-usage" :class="{ 'has-summary': Boolean(summary) }">
      <section class="panel admin-table-card">
        <div class="section-header">
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
            <select :id="`${baseId}-feature`" v-model="featureType" class="select">
              <option value="">{{ t('adminAi.allFeatures') }}</option>
              <option v-for="feature in AI_USAGE_FEATURES" :key="feature" :value="feature">
                {{ aiFeatureLabel(feature, t) }}
              </option>
            </select>
          </div>
          <div class="field">
            <label class="field-label" :for="`${baseId}-result`">{{ t('adminAi.result') }}</label>
            <select :id="`${baseId}-result`" v-model="status" class="select">
              <option value="">{{ t('adminAi.allResults') }}</option>
              <option v-for="(meta, result) in RESULT_META" :key="result" :value="result">
                {{ t(meta.labelKey) }}
              </option>
            </select>
          </div>
          <div class="field">
            <label class="field-label" :for="`${baseId}-role`">{{ t('adminAi.role') }}</label>
            <select :id="`${baseId}-role`" v-model="userRole" class="select">
              <option value="">{{ t('adminAi.allRoles') }}</option>
              <option v-for="role in USER_ROLES" :key="role" :value="role">{{ t(`roles.${role}`) }}</option>
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
                <template v-for="row in rows" :key="row.id">
                  <tr>
                    <td class="admin-cell-date">{{ formatDateTime(row.createdAt) }}</td>
                    <td class="admin-cell-strong">{{ row.userName }}</td>
                    <td>
                      <span v-if="isRole(row.userRole)" class="tag is-outline">{{ t(`roles.${row.userRole}`) }}</span>
                      <span v-else aria-hidden="true">—</span>
                    </td>
                    <td class="admin-cell-muted">{{ aiFeatureLabel(row.featureKey, t) }}</td>
                    <td :title="row.errorSummary ?? undefined">
                      <span class="admin-ai-result-cell">
                        <StatusPill :label="t(RESULT_META[row.status].labelKey)" :tone="RESULT_META[row.status].tone" />
                        <button
                          v-if="row.errorSummary"
                          class="btn btn-ghost btn-icon btn-sm"
                          type="button"
                          :aria-label="t('adminAi.errorDetail')"
                          :title="t('adminAi.errorDetail')"
                          :aria-expanded="expandedErrorId === row.id"
                          :aria-controls="`${baseId}-err-${row.id}`"
                          :data-testid="`admin-ai-error-toggle-${row.id}`"
                          @click="toggleError(row.id)"
                        >
                          <IconGlyph name="info" :size="14" />
                        </button>
                      </span>
                    </td>
                  </tr>
                  <tr
                    v-if="row.errorSummary && expandedErrorId === row.id"
                    :id="`${baseId}-err-${row.id}`"
                    class="admin-ai-log-detail"
                  >
                    <td colspan="5">{{ row.errorSummary }}</td>
                  </tr>
                </template>
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
          :message="hasFilters ? t('adminAi.noRequestsHint') : t('adminAi.noRequestsEmpty')"
        >
          <template v-if="hasFilters" #actions>
            <button class="btn btn-secondary" type="button" data-testid="admin-ai-usage-clear" @click="clearFilters">
              {{ t('common.clearFilters') }}
            </button>
          </template>
        </StatePanel>
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
            <span class="progress-bar is-sm" aria-hidden="true">
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
import { computed, ref, useId, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { adminAiApi } from '@/api/ai'
import { aiFeatureLabel } from '@/components/admin/adminDisplay'
import AdminAiTabs from '@/components/admin/AdminAiTabs.vue'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill, { type PillTone } from '@/components/common/StatusPill.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { usePageFilters, useResource, useServerPage } from '@/composables/useResource'
import { useCsvExport } from '@/components/admin/useCsvExport'
import { adminAiMessages } from '@/plugins/adminAiMessages'
import { opsLabelMessages } from '@/plugins/opsLabelMessages'
import type { AdminAiUsageFilters } from '@/types/ai'
import { AI_RESULT_STATUSES, AI_USAGE_FEATURES, USER_ROLES, type AiResultStatus, type UserRole } from '@/types/api'
import { formatDateTime, formatNumber, formatPercent, ratioPercent } from '@/utils/format'
import { pageFromQuery, pageQuery, queryOption } from '@/utils/routeQuery'
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
const route = useRoute()
const router = useRouter()

/** A role of today's platform; an older request may name one that no longer exists, which shows no tag. */
function isRole(role: string | null): role is UserRole {
  return USER_ROLES.some((current) => role === current)
}
const baseId = useId()

const summaryResource = useResource(adminAiApi.usageSummary)
const summary = summaryResource.data
const featureType = ref<AdminAiUsageFilters['featureType']>(queryOption(route.query.feature, AI_USAGE_FEATURES) ?? '')
const status = ref<AdminAiUsageFilters['status']>(queryOption(route.query.status, AI_RESULT_STATUSES) ?? '')
const userRole = ref<AdminAiUsageFilters['userRole']>(queryOption(route.query.role, USER_ROLES) ?? '')
const page = ref(pageFromQuery(route.query.page))
const expandedErrorId = ref<number | null>(null)

const currentFilters = computed<Required<AdminAiUsageFilters>>(() => ({
  featureType: featureType.value ?? '',
  status: status.value ?? '',
  userRole: userRole.value ?? '',
}))
const hasFilters = computed(() => Boolean(featureType.value || status.value || userRole.value))

const logResource = useResource(() => adminAiApi.usageLogs(currentFilters.value, page.value, PAGE_SIZE))

function writeAddress(): void {
  void router.replace({
    query: {
      feature: featureType.value || undefined,
      status: status.value || undefined,
      role: userRole.value || undefined,
      page: pageQuery(page.value),
    },
  })
}

const {
  items: rows,
  displayedPage,
  totalPages,
  goToPage,
} = useServerPage(logResource, page, {
  stepBackFromEmpty: true,
  onPageChange: writeAddress,
})
const followAddress = usePageFilters([featureType, status, userRole], page, logResource, () => {
  expandedErrorId.value = null
  writeAddress()
})

watch([() => route.query.feature, () => route.query.status, () => route.query.role, () => route.query.page], () => {
  if (route.name !== 'admin-ai-usage') return
  followAddress(pageFromQuery(route.query.page), () => {
    featureType.value = queryOption(route.query.feature, AI_USAGE_FEATURES) ?? ''
    status.value = queryOption(route.query.status, AI_RESULT_STATUSES) ?? ''
    userRole.value = queryOption(route.query.role, USER_ROLES) ?? ''
  })
})

function clearFilters(): void {
  featureType.value = ''
  status.value = ''
  userRole.value = ''
}

function toggleError(id: number): void {
  expandedErrorId.value = expandedErrorId.value === id ? null : id
}

const { exporting, exportError, toast, exportCsv } = useCsvExport({
  load: () => adminAiApi.exportUsageLogs(currentFilters.value),
  fileName: 'ai-usage-log.csv',
  successMessage: () => t('adminAi.csvDownloaded'),
  failureMessage: () => t('adminAi.csvError'),
})

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
