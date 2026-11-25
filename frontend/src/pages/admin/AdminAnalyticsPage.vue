<template>
  <div class="page-stack">
    <h1 class="sr-only">{{ t('nav.analytics') }}</h1>
    <div class="page-toolbar">
      <SegmentedControl
        :model-value="data?.days ?? requestedDays"
        :disabled="changingPeriod || analytics.loading.value"
        :label="t('adminInsights.periodLabel')"
        :options="periodOptions"
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
        <span>{{ t('common.exportCsv') }}</span>
      </button>
    </div>

    <!-- A period that could not be loaded: the numbers of the one before stay, and the control says which. -->
    <DataNotice
      :error="data ? analytics.error.value : null"
      :error-title="t('adminInsights.loadError')"
      @retry="retryPeriod"
    />

    <StatePanel
      v-if="!data && analytics.loading.value"
      heading-tag="h2"
      tone="loading"
      spacious
      :title="t('adminInsights.loading')"
      :message="t('adminInsights.loadingHint')"
    />
    <StatePanel
      v-else-if="!data"
      heading-tag="h2"
      tone="error"
      spacious
      :title="t('adminInsights.loadError')"
      :message="analytics.error.value"
      @retry="retryPeriod"
    />
    <template v-else>
      <section class="panel admin-detail" aria-labelledby="admin-insights-registrations-title">
        <header class="section-header">
          <div class="admin-list-head">
            <h2 id="admin-insights-registrations-title" class="card-title">{{ t('adminInsights.registrations') }}</h2>
            <p class="admin-meta" data-testid="admin-insights-registrations-detail">
              {{
                t(
                  'adminInsights.registrationsDetail',
                  {
                    count: formatNumber(data.newRegistrations, locale),
                    from: formatShortDate(data.from, locale),
                    to: formatShortDate(data.to, locale),
                  },
                  data.newRegistrations,
                )
              }}
            </p>
          </div>
          <div class="admin-list-head admin-insights-users" data-testid="admin-insights-users">
            <strong class="row-list-title">
              {{
                t(
                  'adminInsights.usersTotal',
                  { count: formatNumber(data.numbers.users.total) },
                  data.numbers.users.total,
                )
              }}
            </strong>
            <p class="admin-meta">{{ usersBreakdown }}</p>
          </div>
        </header>
        <TrendChart :values="trend" :label="t('adminInsights.registrationsChart')" />
      </section>

      <div class="admin-insights-grid">
        <section class="panel admin-detail" aria-labelledby="admin-insights-learning-title">
          <h2 id="admin-insights-learning-title" class="card-title">{{ t('adminInsights.learning') }}</h2>
          <dl class="admin-insights-learning">
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
                    : formatPercent(data.learning.quizAverage)
                }}
              </dd>
            </div>
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
              <dt>{{ t('adminInsights.needsSupport') }}</dt>
              <dd>{{ formatNumber(data.learning.needsSupport) }}</dd>
            </div>
          </dl>
        </section>

        <section
          v-for="section in shareSections"
          :key="section.id"
          class="panel admin-detail"
          :aria-labelledby="`${section.id}-title`"
        >
          <header class="section-header">
            <div class="admin-list-head">
              <h2 :id="`${section.id}-title`" class="card-title">{{ section.title }}</h2>
              <p class="admin-meta">{{ section.summary }}</p>
            </div>
            <RouterLink class="btn btn-ghost btn-sm admin-link-action" :to="{ name: section.route }">
              {{ section.link }}
              <IconGlyph name="chevronRight" :size="16" />
            </RouterLink>
          </header>
          <ul class="admin-shares">
            <li v-for="row in section.rows" :key="row.status" :data-testid="`${section.id}-row`">
              <span class="admin-share-head">
                <span>{{ row.label }}</span>
                <span>{{ row.text }}</span>
              </span>
              <span class="progress-bar is-sm" aria-hidden="true"><span :style="{ width: `${row.percent}%` }" /></span>
            </li>
          </ul>
        </section>
      </div>
    </template>

    <AppToast :message="toast" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { adminOverviewApi } from '@/api/ops'
import TrendChart, { type TrendPoint } from '@/components/admin/TrendChart.vue'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import SegmentedControl from '@/components/common/SegmentedControl.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { adminAnalyticsMessages } from '@/plugins/adminAnalyticsMessages'
import type { AiResultStatus, ContentStatus } from '@/types/api'
import type { AdminAnalyticsResponse } from '@/types/ops'
import { csvBlob } from '@/utils/csv'
import { saveBlobDownload } from '@/utils/download'
import { formatNumber, formatPercent, formatShortDate, ratioPercent } from '@/utils/format'
import '@/styles/admin-shared.css'
import '@/styles/ops-admin.css'

type Period = AdminAnalyticsResponse['days']

const PERIODS: readonly Period[] = [7, 30]
const CONTENT_STATUSES: readonly (readonly [ContentStatus, string])[] = [
  ['PUBLISHED', 'adminInsights.statusPublished'],
  ['DRAFT', 'adminInsights.statusDraft'],
  ['ARCHIVED', 'adminInsights.statusArchived'],
]
const AI_STATUSES: readonly (readonly [AiResultStatus, string])[] = [
  ['SUCCESS', 'adminInsights.aiSuccess'],
  ['UNAVAILABLE', 'adminInsights.aiUnavailable'],
  ['FAILED', 'adminInsights.aiFailed'],
]
/** On the 30-day axis every fifth day is labelled, plus the last one. */
const AXIS_LABEL_STEP = 5

const { t, locale } = useLocalMessages({ adminInsights: adminAnalyticsMessages })
const route = useRoute()
const router = useRouter()
const { toast, showToast } = useToast()

function periodFromQuery(raw: unknown): Period {
  return raw === '30' ? 30 : 7
}

const requestedDays = ref<Period>(periodFromQuery(route.query.days))
const changingPeriod = ref(false)
const analytics = useResource(() => adminOverviewApi.analytics(requestedDays.value))
const data = analytics.data

watch(
  () => route.query.days,
  (raw) => {
    if (route.name !== 'admin-analytics') return
    const next = periodFromQuery(raw)
    if (next !== requestedDays.value) void loadPeriod(next, { syncAddress: false })
  },
)

const periodOptions = computed(() =>
  PERIODS.map((period) => ({
    value: period,
    label: period === 7 ? t('adminInsights.last7') : t('adminInsights.last30'),
    testId: `admin-insights-period-${period}`,
  })),
)

async function loadPeriod(period: Period, { syncAddress = true } = {}): Promise<void> {
  requestedDays.value = period
  changingPeriod.value = true
  try {
    await analytics.reload()
    if (syncAddress && analytics.error.value === null) {
      void router.replace({ query: { ...route.query, days: period === 30 ? '30' : undefined } })
    }
  } finally {
    changingPeriod.value = false
  }
}

function selectPeriod(period: Period): void {
  if (changingPeriod.value || analytics.loading.value) return
  if (data.value?.days === period && analytics.error.value === null) return
  void loadPeriod(period)
}

function retryPeriod(): void {
  if (changingPeriod.value || analytics.loading.value) return
  void loadPeriod(requestedDays.value)
}

const usersBreakdown = computed(() => {
  const users = data.value?.numbers.users
  if (!users) return ''
  return [
    t('adminInsights.countStudents', { count: formatNumber(users.students) }, users.students),
    t('adminInsights.countAdmins', { count: formatNumber(users.admins) }, users.admins),
  ].join(' · ')
})

const trend = computed<TrendPoint[]>(() => {
  const days = data.value?.registrations ?? []
  const last = days.length - 1
  const sparse = data.value?.days === 30
  return days.map((day, index) => {
    const label = formatShortDate(day.date, locale.value)
    return {
      key: day.date,
      label,
      value: day.count,
      axisLabel: t('adminInsights.dayCount', { day: label, count: formatNumber(day.count, locale.value) }),
      quiet: sparse && index !== last && index % AXIS_LABEL_STEP !== 0,
    }
  })
})

function sharesByStatus<S extends string>(statuses: readonly (readonly [S, string])[], counts: Record<S, number>) {
  const total = statuses.reduce((sum, [status]) => sum + counts[status], 0)
  const rows = statuses.map(([status, key]) => {
    const count = counts[status]
    const percent = ratioPercent(count, total, { digits: 1 })
    return {
      status,
      label: t(key),
      count,
      percent,
      text: t('adminInsights.share', { count: formatNumber(count), percent: formatPercent(percent, { digits: 1 }) }),
    }
  })
  return { total, rows }
}

const shareSections = computed(() => {
  if (!data.value) return []
  const content = sharesByStatus(CONTENT_STATUSES, data.value.contentByStatus)
  const ai = sharesByStatus(AI_STATUSES, data.value.aiRequestsByStatus)
  return [
    {
      id: 'admin-insights-content',
      title: t('adminInsights.content'),
      summary: t('adminInsights.contentDetail', { total: formatNumber(content.total) }, content.total),
      link: t('adminInsights.openContent'),
      route: 'admin-classes',
      rows: content.rows,
    },
    {
      id: 'admin-insights-ai',
      title: t('adminInsights.ai'),
      summary: t(
        'adminInsights.aiDetail',
        { total: formatNumber(data.value.numbers.aiRequests) },
        data.value.numbers.aiRequests,
      ),
      link: t('adminInsights.openAiUsage'),
      route: 'admin-ai-usage',
      rows: ai.rows,
    },
  ]
})

function exportCsv(): void {
  const value = data.value
  if (!value) return
  const blob = csvBlob([
    [t('adminInsights.csvTitle')],
    [t('adminInsights.periodLabel'), value.from, value.to],
    [],
    [t('adminInsights.csvMetric'), t('adminInsights.csvValue')],
    [t('adminInsights.csvUsers'), value.numbers.users.total],
    [t('adminInsights.csvStudents'), value.numbers.users.students],
    [t('adminInsights.csvAdmins'), value.numbers.users.admins],
    [t('adminInsights.csvRegistrations'), value.newRegistrations],
    [t('adminInsights.csvPublished'), value.numbers.publishedContent],
    [t('adminInsights.ai'), value.numbers.aiRequests],
    [t('adminInsights.activeStudents'), value.learning.activeStudents],
    [t('adminInsights.lessonsCompleted'), value.learning.lessonsCompleted],
    [t('adminInsights.quizAverage'), value.learning.quizAverage],
    [t('adminInsights.needsSupport'), value.learning.needsSupport],
    [],
    [t('adminInsights.csvDate'), t('adminInsights.registrationsChart')],
    ...value.registrations.map((day) => [day.date, day.count]),
    ...shareSections.value.flatMap((section) => [
      [],
      [section.title, t('adminInsights.csvCount')],
      ...section.rows.map((row) => [row.label, row.count]),
    ]),
  ])
  saveBlobDownload({ blob }, `analytics-${value.days}d-${value.to}.csv`)
  showToast(t('adminInsights.csvDownloaded'))
}
</script>
