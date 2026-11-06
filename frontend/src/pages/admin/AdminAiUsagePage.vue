<template>
  <div class="page-stack">
    <PageHeaderBlock :title="t('adminAi.usageTitle')" hide-title />
    <AdminAiTabs />
    <DataNotice :error="exportError" />

    <StatePanel
      v-if="summaryResource.loading.value"
      tone="loading"
      :title="t('adminAi.usageLoading')"
      :message="t('adminAi.usageLoadingHint')"
    />
    <StatePanel
      v-else-if="summaryResource.error.value"
      tone="error"
      icon="alert"
      :title="t('adminAi.usageError')"
      :message="summaryResource.error.value"
    >
      <template #actions>
        <button class="btn btn-primary" type="button" @click="summaryResource.reload">{{ t('adminAi.retry') }}</button>
      </template>
    </StatePanel>
    <dl v-else-if="summary" class="admin-ai-totals" :aria-label="t('adminAi.totalsLabel')">
      <div class="panel admin-figure" data-testid="admin-ai-kpi-total">
        <dt>{{ t('adminAi.requests') }}</dt>
        <dd>
          <strong class="admin-figure-value">{{ formatNumber(summary.totalRequests) }}</strong>
          <span class="admin-meta">{{
            t('adminAi.notAnsweredShare', { percent: percentOf(summary.unavailable + summary.failed) })
          }}</span>
        </dd>
      </div>
      <div class="panel admin-figure">
        <dt>{{ t('adminAi.successful') }}</dt>
        <dd>
          <strong class="admin-figure-value">{{ formatNumber(summary.successful) }}</strong>
          <span class="admin-meta">{{ percentOf(summary.successful) }}</span>
        </dd>
      </div>
      <div class="panel admin-figure">
        <dt>{{ t('adminAi.unavailable') }}</dt>
        <dd>
          <strong class="admin-figure-value">{{ formatNumber(summary.unavailable) }}</strong>
          <span class="admin-meta">{{ t('adminAi.unavailableHint') }}</span>
        </dd>
      </div>
      <div class="panel admin-figure">
        <dt>{{ t('adminAi.failed') }}</dt>
        <dd>
          <strong class="admin-figure-value">{{ formatNumber(summary.failed) }}</strong>
          <span class="admin-meta">{{ t('adminAi.failedHint') }}</span>
        </dd>
      </div>
    </dl>

    <div class="admin-ai-usage">
      <section class="panel admin-table-card" :aria-labelledby="`${baseId}-log`">
        <div class="admin-section-head">
          <div class="admin-list-head">
            <h2 :id="`${baseId}-log`" class="card-title">{{ t('adminAi.requestLog') }}</h2>
            <p class="admin-meta">
              {{ t('adminAi.requestLogCount', { count: formatNumber(totalItems) }, totalItems) }}
            </p>
          </div>
          <button
            class="btn btn-secondary"
            type="button"
            :disabled="exporting"
            :aria-busy="exporting"
            @click="exportCsv"
          >
            <IconGlyph name="download" :size="16" />
            <span>{{ t('adminAi.exportCsv') }}</span>
          </button>
        </div>

        <div class="admin-filters">
          <div class="field">
            <label class="field-label" :for="`${baseId}-feature`">{{ t('adminAi.feature') }}</label>
            <select :id="`${baseId}-feature`" v-model="filters.featureType" class="select" @change="applyFilters">
              <option value="">{{ t('adminAi.allFeatures') }}</option>
              <option v-for="feature in FEATURES" :key="feature" :value="feature">{{ featureLabel(feature) }}</option>
            </select>
          </div>
          <div class="field">
            <label class="field-label" :for="`${baseId}-result`">{{ t('adminAi.result') }}</label>
            <select :id="`${baseId}-result`" v-model="filters.status" class="select" @change="applyFilters">
              <option value="">{{ t('adminAi.allResults') }}</option>
              <option v-for="result in RESULTS" :key="result" :value="result">
                {{ t(RESULT_META[result].labelKey) }}
              </option>
            </select>
          </div>
          <div class="field">
            <label class="field-label" :for="`${baseId}-role`">{{ t('adminAi.role') }}</label>
            <select :id="`${baseId}-role`" v-model="filters.userRole" class="select" @change="applyFilters">
              <option value="">{{ t('adminAi.allRoles') }}</option>
              <option value="STUDENT">{{ t('adminAi.roleStudent') }}</option>
              <option value="TEACHER">{{ t('adminAi.roleTeacher') }}</option>
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
          v-else-if="logResource.error.value && !rows.length"
          tone="error"
          icon="alert"
          :title="t('adminAi.logError')"
          :message="logResource.error.value"
        >
          <template #actions>
            <button class="btn btn-primary" type="button" @click="logResource.reload">
              {{ t('adminAi.retry') }}
            </button>
          </template>
        </StatePanel>
        <div
          v-else-if="rows.length"
          class="data-table-wrap admin-table-wrap"
          role="region"
          :aria-label="t('adminAi.requestLog')"
          tabindex="0"
        >
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
                <td class="admin-cell-strong">{{ row.userName || t('adminAi.unknownUser') }}</td>
                <td>
                  <span class="tag is-outline">{{ roleLabel(row.userRole) }}</span>
                </td>
                <td class="admin-cell-muted">{{ featureLabel(row.featureKey) }}</td>
                <td :title="row.errorSummary ?? undefined">
                  <StatusPill :label="t(resultMeta(row.status).labelKey)" :tone="resultMeta(row.status).tone" />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <StatePanel
          v-else
          tone="empty"
          icon="search"
          :title="t('adminAi.noRequests')"
          :message="t('adminAi.noRequestsHint')"
        />
        <PaginationBar
          v-if="totalPages > 1"
          icon-only
          :current-page="page + 1"
          :total-pages="totalPages"
          :label="t('adminAi.requestLog')"
          :info="t('adminAi.showing', range)"
          @update:current-page="goToPage($event - 1)"
        />
        <p class="callout is-compact">
          <IconGlyph name="shield" :size="16" />
          <span>{{ t('adminAi.retentionNote') }}</span>
        </p>
      </section>

      <section v-if="summary" class="panel admin-list" :aria-labelledby="`${baseId}-features`">
        <header class="admin-list-head">
          <h2 :id="`${baseId}-features`" class="card-title">{{ t('adminAi.byFeature') }}</h2>
          <p class="admin-meta">
            {{ t('adminAi.byFeatureTotal', { count: formatNumber(summary.totalRequests) }) }}
          </p>
        </header>
        <ul class="admin-shares">
          <li v-for="row in summary.byFeature" :key="row.featureKey">
            <span class="admin-share-head">
              <span>{{ featureLabel(row.featureKey) }}</span>
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
import { useToast } from '@/composables/useToast'
import { adminAiMessages } from '@/plugins/adminAiMessages'
import { aiFeatureMessages } from '@/plugins/aiFeatureMessages'
import type { AiFeatureKey } from '@/types/api'
import type { AdminAiUsageFilters, AiResultStatus } from '@/types/ai'
import { resolveApiError } from '@/utils/apiError'
import { saveBlobDownload } from '@/utils/download'
import { formatDateTime, formatNumber, formatPercent, ratioPercent } from '@/utils/format'
import '@/styles/ops-admin.css'

/**
 * Admin AI usage: all-time totals by result, requests by feature, and the request log (who, when, which feature,
 * the result) with filters, paging and a CSV export. Message text is never logged.
 */
const FEATURES: readonly AiFeatureKey[] = [
  'CHAT_TUTOR',
  'XIAOMEI_CONVERSATION',
  'QUIZ_GENERATOR',
  'STORY_GENERATOR',
  'PRACTICE_CHECK',
  'TEACHER_DRAFT',
  'LEARNING_PLAN',
]
const RESULTS: readonly AiResultStatus[] = ['SUCCESS', 'UNAVAILABLE', 'FAILED']
const RESULT_META: Readonly<Record<AiResultStatus, { labelKey: string; tone: PillTone }>> = {
  SUCCESS: { labelKey: 'adminAi.successful', tone: 'success' },
  UNAVAILABLE: { labelKey: 'adminAi.unavailable', tone: 'warning' },
  FAILED: { labelKey: 'adminAi.failed', tone: 'danger' },
}
const PAGE_SIZE = 10

const { t } = useLocalMessages({ adminAi: adminAiMessages, aiFeatures: aiFeatureMessages })
const baseId = useId()
const { toast, showToast } = useToast()

const summaryResource = useResource(() => adminAiApi.usageSummary())
const summary = computed(() => summaryResource.data.value)
const filters = reactive<Required<AdminAiUsageFilters>>({ featureType: '', status: '', userRole: '' })
const page = ref(0)
const logResource = useResource(() => adminAiApi.usageLogs({ ...filters }, page.value, PAGE_SIZE))
const { items: rows, totalItems, totalPages, range, goToPage } = useServerPage(logResource, page)
const exporting = ref(false)
const exportError = ref<string | null>(null)

function featureLabel(featureKey: string): string {
  return aiFeatureLabel(featureKey, t)
}

function resultMeta(status: string) {
  return RESULT_META[status as AiResultStatus] ?? { labelKey: 'adminAi.failed', tone: 'neutral' as PillTone }
}

function roleLabel(role: string | null | undefined): string {
  if (role === 'STUDENT') return t('adminAi.roleStudent')
  if (role === 'TEACHER') return t('adminAi.roleTeacher')
  return role ? t('adminAi.roleOther') : t('adminAi.unknownRole')
}

/** Share of all requests, e.g. "33.3%". */
function percentOf(count: number): string {
  return formatPercent(ratioPercent(count, summary.value?.totalRequests, { digits: 1 }), { digits: 1 })
}

function applyFilters(): void {
  goToPage(0)
}

async function exportCsv(): Promise<void> {
  if (exporting.value) return
  exporting.value = true
  exportError.value = null
  try {
    const download = await adminAiApi.exportUsageLogs({ ...filters })
    saveBlobDownload(download, 'ai-usage-log.csv')
    showToast(t('adminAi.csvDownloaded'))
  } catch (reason) {
    exportError.value = resolveApiError(reason, t('adminAi.csvError'))
  } finally {
    exporting.value = false
  }
}
</script>
