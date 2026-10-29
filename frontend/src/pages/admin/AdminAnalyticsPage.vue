<template>
  <div class="admin-insights page-stack">
    <PageHeaderBlock :title="t('adminInsights.pageTitle')" :description="t('adminInsights.lede')">
      <template #actions>
        <SegmentedControl
          :model-value="days"
          :label="t('adminInsights.periodLabel')"
          :options="
            PERIODS.map((option) => ({
              value: option,
              label: option === 7 ? t('adminInsights.last7') : t('adminInsights.last30'),
              testId: `admin-insights-period-${option}`,
            }))
          "
          @update:model-value="selectPeriod"
        />
        <button
          class="btn btn-secondary"
          type="button"
          :disabled="!data"
          data-testid="admin-insights-export"
          @click="exportCsv"
        >
          <IconGlyph name="download" :size="16" />
          {{ t('adminInsights.exportCsv') }}
        </button>
      </template>
    </PageHeaderBlock>

    <StatePanel
      v-if="analytics.loading.value"
      tone="loading"
      spacious
      :title="t('adminInsights.loading')"
      :message="t('adminInsights.loadingHint')"
    />
    <StatePanel
      v-else-if="!data"
      tone="error"
      icon="alert"
      spacious
      :title="t('adminInsights.loadError')"
      :message="analytics.error.value ?? ''"
    >
      <template #actions>
        <button class="btn btn-primary" type="button" @click="analytics.reload">{{ t('adminInsights.retry') }}</button>
      </template>
    </StatePanel>
    <template v-else>
      <section class="admin-insights-figures" :aria-label="t('adminInsights.figuresLabel')">
        <dl class="admin-figures">
          <div v-for="kpi in kpis" :key="kpi.label" class="admin-figure" data-testid="admin-insights-kpi">
            <dt>{{ kpi.label }}</dt>
            <dd>
              <strong class="stat-figure">{{ kpi.value }}</strong>
              <span>{{ kpi.detail }}</span>
            </dd>
          </div>
        </dl>
      </section>

      <section class="admin-sheet admin-insights-chart" aria-labelledby="admin-insights-registrations-title">
        <div class="admin-sheet-head">
          <div>
            <h2 id="admin-insights-registrations-title" class="admin-sheet-title">
              {{ t('adminInsights.registrations') }}
            </h2>
            <p class="admin-sheet-caption" data-testid="admin-insights-registrations-detail">
              {{
                t(
                  'adminInsights.registrationsDetail',
                  { count: data.newRegistrations, from: formatShortDate(data.from), to: formatShortDate(data.to) },
                  data.newRegistrations,
                )
              }}
            </p>
          </div>
        </div>
        <div class="admin-sheet-body admin-insights-plot">
          <TrendChart :values="trend" :label="t('adminInsights.registrationsChart')" />
          <ol class="admin-insights-axis" aria-hidden="true">
            <li
              v-for="(point, index) in trend"
              :key="data.registrations[index].date"
              :class="{ 'is-quiet': !showAxisLabel(index) }"
              :style="{ left: `${axisPosition(index)}%` }"
            >
              {{ t('adminInsights.dayCount', { day: point.label, count: point.value }) }}
            </li>
          </ol>
        </div>
      </section>

      <div class="admin-insights-grid">
        <section class="admin-insights-block" aria-labelledby="admin-insights-learning-title">
          <div class="admin-insights-head">
            <h2 id="admin-insights-learning-title" class="admin-insights-title">{{ t('adminInsights.learning') }}</h2>
            <p>{{ t('adminInsights.learningDetail') }}</p>
          </div>
          <dl class="admin-insights-learning">
            <div>
              <dt>{{ t('adminInsights.activeStudents') }}</dt>
              <dd>
                {{
                  t('adminInsights.activeOf', {
                    active: formatNumber(data.learning.activeStudents),
                    total: formatNumber(data.learning.totalStudents),
                  })
                }}
              </dd>
            </div>
            <div>
              <dt>{{ t('adminInsights.lessonsCompleted') }}</dt>
              <dd>{{ formatNumber(data.learning.lessonsCompleted) }}</dd>
            </div>
            <div>
              <dt>{{ t('adminInsights.quizAverage') }}</dt>
              <dd>
                {{
                  data.learning.quizAverage === null
                    ? t('adminInsights.noQuizzes')
                    : formatPercent(data.learning.quizAverage, { digits: 1 })
                }}
              </dd>
            </div>
            <div>
              <dt>{{ t('adminInsights.needsSupport') }}</dt>
              <dd>
                {{ formatNumber(data.learning.needsSupport) }}
                <span class="type-caption">{{ t('adminInsights.needsSupportHint') }}</span>
              </dd>
            </div>
          </dl>
        </section>

        <section class="admin-insights-block" aria-labelledby="admin-insights-content-title">
          <div class="admin-insights-head">
            <h2 id="admin-insights-content-title" class="admin-insights-title">{{ t('adminInsights.content') }}</h2>
            <p>{{ t('adminInsights.contentDetail', { total: formatNumber(contentTotal) }) }}</p>
          </div>
          <ul class="admin-shares">
            <li v-for="row in contentRows" :key="row.label" data-testid="admin-insights-content-row">
              <span class="admin-share-head">
                <span>{{ row.label }}</span>
                <span>{{ row.text }}</span>
              </span>
              <span class="admin-share-bar" aria-hidden="true"><span :style="{ width: `${row.percent}%` }" /></span>
            </li>
          </ul>
          <RouterLink class="btn btn-link" :to="{ name: 'admin-content-review' }">
            {{ t('adminInsights.openContent') }}
            <IconGlyph name="chevronRight" :size="14" />
          </RouterLink>
        </section>

        <section class="admin-insights-block" aria-labelledby="admin-insights-ai-title">
          <div class="admin-insights-head">
            <h2 id="admin-insights-ai-title" class="admin-insights-title">{{ t('adminInsights.ai') }}</h2>
            <p>{{ t('adminInsights.aiDetail', { total: formatNumber(aiTotal) }) }}</p>
          </div>
          <ul class="admin-shares">
            <li v-for="row in aiRows" :key="row.label" data-testid="admin-insights-ai-row">
              <span class="admin-share-head">
                <span>{{ row.label }}</span>
                <span>{{ row.text }}</span>
              </span>
              <span class="admin-share-bar" aria-hidden="true"><span :style="{ width: `${row.percent}%` }" /></span>
            </li>
          </ul>
          <RouterLink class="btn btn-link" :to="{ name: 'admin-ai-usage' }">
            {{ t('adminInsights.openAiUsage') }}
            <IconGlyph name="chevronRight" :size="14" />
          </RouterLink>
        </section>
      </div>
    </template>

    <AppToast :message="toast" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { adminOverviewApi } from '@/api/ops'
import SegmentedControl from '@/components/common/SegmentedControl.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import TrendChart from '@/components/common/TrendChart.vue'
import AppToast from '@/components/common/AppToast.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { adminAnalyticsMessages } from '@/plugins/adminAnalyticsMessages'
import type { AnalyticsTrendPoint, ContentStatus } from '@/types/api'
import type { AiRequestStatus } from '@/types/ops'
import { csvBlob, toCsv, type CsvCell } from '@/utils/csv'
import { saveBlobDownload } from '@/utils/download'
import { formatNumber, formatPercent, formatShortDate, ratioPercent } from '@/utils/format'
import '@/styles/ops-admin.css'

type Period = 7 | 30

const PERIODS: readonly Period[] = [7, 30]
const CONTENT_STATUSES: readonly (readonly [ContentStatus, string])[] = [
  ['PUBLISHED', 'adminInsights.statusPublished'],
  ['SUBMITTED', 'adminInsights.statusSubmitted'],
  ['REJECTED', 'adminInsights.statusRejected'],
  ['DRAFT', 'adminInsights.statusDraft'],
  ['ARCHIVED', 'adminInsights.statusArchived'],
]
const AI_STATUSES: readonly (readonly [AiRequestStatus, string])[] = [
  ['SUCCESS', 'adminInsights.aiSuccess'],
  ['UNAVAILABLE', 'adminInsights.aiUnavailable'],
  ['FAILED', 'adminInsights.aiFailed'],
]
/** On the 30-day axis every fifth day is labelled, plus the last one. */
const AXIS_LABEL_STEP = 5

const { t, locale } = useLocalMessages({ adminInsights: adminAnalyticsMessages })
const { toast, showToast } = useToast()

const days = ref<Period>(7)
const analytics = useResource(() => adminOverviewApi.analytics(days.value))
const data = computed(() => analytics.data.value)

function selectPeriod(period: Period): void {
  if (days.value === period) return
  days.value = period
  void analytics.reload()
}

const kpis = computed(() => {
  const value = data.value
  if (!value) return []
  const { users } = value.numbers
  return [
    {
      label: t('adminInsights.kpiUsers'),
      value: formatNumber(users.total),
      detail: [
        t('adminInsights.countStudents', { count: formatNumber(users.students) }, users.students),
        t('adminInsights.countTeachers', { count: formatNumber(users.teachers) }, users.teachers),
        t('adminInsights.countAdmins', { count: formatNumber(users.admins) }, users.admins),
      ].join(' · '),
    },
    {
      label: t('adminInsights.kpiRegistrations'),
      value: formatNumber(value.newRegistrations),
      detail: t('adminInsights.kpiRegistrationsDetail', { days: value.days }),
    },
    {
      label: t('adminInsights.kpiPublished'),
      value: formatNumber(value.numbers.publishedContent),
      detail: t('adminInsights.kpiPublishedDetail', { total: formatNumber(value.numbers.totalContent) }),
    },
    {
      label: t('adminInsights.kpiAi'),
      value: formatNumber(value.numbers.aiRequests),
      detail: t('adminInsights.kpiAiDetail', { successful: formatNumber(value.numbers.successfulAiRequests) }),
    },
  ]
})

const trend = computed<AnalyticsTrendPoint[]>(() =>
  (data.value?.registrations ?? []).map((day) => ({
    label: formatShortDate(day.date, locale.value),
    value: day.count,
  })),
)

function showAxisLabel(index: number): boolean {
  const last = trend.value.length - 1
  return days.value === 7 || index === last || index % AXIS_LABEL_STEP === 0
}

/** Each label sits under its point: the chart spreads the points from edge to edge. */
function axisPosition(index: number): number {
  const last = trend.value.length - 1
  return last > 0 ? (index / last) * 100 : 50
}

function shareRows<S extends string>(
  statuses: readonly (readonly [S, string])[],
  counts: Record<S, number> | undefined,
): { label: string; count: number; percent: number; text: string }[] {
  const total = statuses.reduce((sum, [status]) => sum + (counts?.[status] ?? 0), 0)
  return statuses.map(([status, key]) => {
    const count = counts?.[status] ?? 0
    const percent = ratioPercent(count, total, { digits: 1 })
    return {
      label: t(key),
      count,
      percent,
      text: t('adminInsights.share', { count: formatNumber(count), percent: formatPercent(percent, { digits: 1 }) }),
    }
  })
}

const contentRows = computed(() => shareRows(CONTENT_STATUSES, data.value?.contentByStatus))
const contentTotal = computed(() => contentRows.value.reduce((sum, row) => sum + row.count, 0))
const aiRows = computed(() => shareRows(AI_STATUSES, data.value?.aiRequestsByStatus))
const aiTotal = computed(() => aiRows.value.reduce((sum, row) => sum + row.count, 0))

function exportCsv(): void {
  const value = data.value
  if (!value) return
  const rows: CsvCell[][] = [
    [t('adminInsights.csvTitle')],
    [t('adminInsights.csvPeriod'), value.from, value.to],
    [],
    [t('adminInsights.csvMetric'), t('adminInsights.csvValue')],
    [t('adminInsights.kpiUsers'), value.numbers.users.total],
    [t('adminInsights.csvStudents'), value.numbers.users.students],
    [t('adminInsights.csvTeachers'), value.numbers.users.teachers],
    [t('adminInsights.csvAdmins'), value.numbers.users.admins],
    [t('adminInsights.kpiRegistrations'), value.newRegistrations],
    [t('adminInsights.kpiPublished'), value.numbers.publishedContent],
    [t('adminInsights.kpiAi'), value.numbers.aiRequests],
    [t('adminInsights.activeStudents'), value.learning.activeStudents],
    [t('adminInsights.lessonsCompleted'), value.learning.lessonsCompleted],
    [t('adminInsights.quizAverage'), value.learning.quizAverage],
    [t('adminInsights.needsSupport'), value.learning.needsSupport],
    [],
    [t('adminInsights.csvDate'), t('adminInsights.registrationsChart')],
    ...value.registrations.map((day) => [day.date, day.count]),
    [],
    [t('adminInsights.content'), t('adminInsights.csvCount')],
    ...contentRows.value.map((row) => [row.label, row.count]),
    [],
    [t('adminInsights.ai'), t('adminInsights.csvCount')],
    ...aiRows.value.map((row) => [row.label, row.count]),
  ]
  saveBlobDownload({ blob: csvBlob(toCsv(rows)) }, `analytics-${value.days}d-${value.to}.csv`)
  showToast(t('adminInsights.csvDownloaded'))
}
</script>
