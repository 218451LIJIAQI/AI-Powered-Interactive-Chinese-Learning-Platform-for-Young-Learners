<template>
  <div class="admin-home page-stack">
    <PageHeaderBlock :title="t('adminHome.pageTitle')" :eyebrow="today" :description="summary">
      <template #meta>
        <div class="admin-home-status">
          <p class="admin-home-health" :class="`is-${healthView.tone}`" role="status" data-testid="admin-home-health">
            <span class="admin-home-health-dot" aria-hidden="true" />
            <strong>{{ healthView.title }}</strong>
            <span v-if="healthView.detail">{{ healthView.detail }}</span>
          </p>
          <button
            class="btn btn-link"
            type="button"
            :disabled="checkingHealth"
            :aria-busy="checkingHealth"
            @click="checkHealth"
          >
            {{ t('adminHome.checkAgain') }}
          </button>
        </div>
      </template>
    </PageHeaderBlock>

    <StatePanel
      v-if="dashboard.loading.value"
      tone="loading"
      spacious
      :title="t('adminHome.loading')"
      :message="t('adminHome.loadingHint')"
    />
    <StatePanel
      v-else-if="!data"
      tone="error"
      icon="alert"
      spacious
      :title="t('adminHome.loadError')"
      :message="dashboard.error.value ?? ''"
    >
      <template #actions>
        <button class="btn btn-primary" type="button" @click="dashboard.reload">{{ t('adminHome.retry') }}</button>
      </template>
    </StatePanel>
    <div v-else class="admin-home-grid">
      <section class="admin-home-attention" aria-labelledby="admin-home-attention-title">
        <div class="section-header">
          <div>
            <h2 id="admin-home-attention-title" class="section-title">{{ t('adminHome.needsAttention') }}</h2>
            <p class="section-description">{{ t('adminHome.needsAttentionHint') }}</p>
          </div>
        </div>
        <ul class="admin-home-queues">
          <li v-for="queue in queues" :key="queue.key">
            <article
              class="admin-home-queue"
              :class="{ 'is-clear': !queue.count }"
              :aria-labelledby="`admin-home-queue-${queue.key}`"
              :data-testid="`admin-home-queue-${queue.key}`"
            >
              <span class="stat-figure admin-home-queue-count" aria-hidden="true">{{ queue.count }}</span>
              <div class="admin-home-queue-head">
                <h3 :id="`admin-home-queue-${queue.key}`">
                  {{ queue.title }} <span class="sr-only">{{ t('adminHome.queueCount', { count: queue.count }) }}</span>
                </h3>
                <p v-if="!queue.count" class="admin-home-queue-clear">
                  <IconGlyph name="check" :size="14" />
                  {{ t('adminHome.nothingWaiting') }}
                </p>
              </div>
              <RouterLink
                v-if="queue.count"
                class="btn btn-secondary btn-sm admin-home-queue-action"
                :to="queue.to"
                :aria-label="t('adminHome.reviewLabel', { queue: queue.title })"
              >
                {{ t('adminHome.review') }}
                <IconGlyph name="chevronRight" :size="14" />
              </RouterLink>
              <ul v-if="queue.items.length" class="admin-home-queue-items">
                <li v-for="item in queue.items" :key="item.key">
                  <RouterLink class="admin-home-queue-item" :to="item.to">
                    <strong>{{ item.title }}</strong>
                    <span>{{ item.meta }}</span>
                  </RouterLink>
                </li>
              </ul>
            </article>
          </li>
        </ul>
      </section>

      <section class="admin-home-activity" aria-labelledby="admin-home-activity-title">
        <div class="admin-home-side-head">
          <h2 id="admin-home-activity-title" class="admin-home-side-title">{{ t('adminHome.activity') }}</h2>
          <RouterLink class="btn btn-link" :to="{ name: 'admin-audit-log' }">
            {{ t('adminHome.openAuditLog') }}
            <IconGlyph name="chevronRight" :size="14" />
          </RouterLink>
        </div>
        <p v-if="activity.loading.value" class="admin-home-side-note" role="status">
          <span class="spinner is-sm" aria-hidden="true" />
          {{ t('adminHome.activityLoading') }}
        </p>
        <p v-else-if="activity.error.value && !entries.length" class="admin-home-side-note" role="alert">
          {{ t('adminHome.activityError') }}
          <button class="btn btn-link" type="button" @click="activity.reload">{{ t('adminHome.retry') }}</button>
        </p>
        <ol v-else-if="entries.length" class="admin-home-feed" data-testid="admin-home-activity">
          <li v-for="entry in entries" :key="entry.id">
            <RouterLink class="admin-home-feed-item" :to="{ name: 'admin-audit-log', query: { entry: entry.id } }">
              <span class="admin-home-feed-what">
                <strong>{{ actionLabel(entry.actionType) }}</strong>
                <span v-if="entry.targetLabel"> · {{ entry.targetLabel }}</span>
              </span>
              <span class="admin-home-feed-meta">
                {{ entry.actorName ?? entry.actorEmail ?? t('adminHome.systemActor') }} ·
                <time :datetime="entry.createdAt">{{ formatRelativeTime(entry.createdAt) }}</time>
              </span>
            </RouterLink>
          </li>
        </ol>
        <p v-else class="admin-home-side-note">{{ t('adminHome.activityEmpty') }}</p>
      </section>

      <section class="admin-home-numbers" :aria-label="t('adminHome.numbersLabel')" data-testid="admin-home-numbers">
        <dl class="admin-figures">
          <div v-for="number in numbers" :key="number.label" class="admin-figure">
            <dt>{{ number.label }}</dt>
            <dd>
              <strong class="stat-figure">{{ number.value }}</strong>
              <span>{{ number.detail }}</span>
            </dd>
          </div>
        </dl>
      </section>

      <section class="admin-home-registrations" aria-labelledby="admin-home-registrations-title">
        <div class="admin-home-side-head">
          <h2 id="admin-home-registrations-title" class="admin-home-side-title">
            {{ t('adminHome.registrations') }}
          </h2>
          <RouterLink class="btn btn-link" :to="{ name: 'admin-analytics' }">
            {{ t('adminHome.openAnalytics') }}
            <IconGlyph name="chevronRight" :size="14" />
          </RouterLink>
        </div>
        <p class="admin-home-side-note">
          {{ t('adminHome.registrationsDetail', { count: newAccounts }, newAccounts) }}
        </p>
        <ol class="admin-home-bars" :aria-label="t('adminHome.registrationsChart')" data-testid="admin-home-bars">
          <li v-for="day in registrationBars" :key="day.date">
            <span class="sr-only">{{ t('adminHome.dayCount', { day: day.label, count: day.count }) }}</span>
            <span class="admin-home-bar-count" aria-hidden="true">{{ day.count }}</span>
            <span class="admin-home-bar-track" aria-hidden="true">
              <span class="admin-home-bar" :style="{ height: `${day.height}%` }" />
            </span>
            <span class="admin-home-bar-day" aria-hidden="true">{{ day.weekday }}</span>
          </li>
        </ol>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

import { adminOverviewApi } from '@/api/ops'
import { systemApi } from '@/api/services'
import { aiFeatureLabel } from '@/components/admin/adminDisplay'
import { reportReasonKey } from '@/components/ai/aiHelper'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useAuditLabels } from '@/composables/useAuditLabels'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { adminDashboardMessages } from '@/plugins/adminDashboardMessages'
import { aiFeatureMessages } from '@/plugins/aiFeatureMessages'
import { aiSafetyMessages } from '@/plugins/aiSafetyMessages'
import { auditActionMessages } from '@/plugins/auditActionMessages'
import type { HealthStatusResponse, ReviewContentType } from '@/types/api'
import {
  formatDate,
  formatDateTime,
  formatNumber,
  formatRelativeTime,
  formatShortDate,
  shareOfMax,
} from '@/utils/format'
import '@/styles/ops-admin.css'

interface QueueItem {
  key: string
  title: string
  meta: string
  to: RouteLocationRaw
}

interface Queue {
  key: string
  title: string
  count: number
  /** Where "Review" goes: the oldest item when the queue page takes one in its address, otherwise the queue. */
  to: RouteLocationRaw
  items: QueueItem[]
}

const ACTIVITY_ITEMS = 6

const REVIEW_TYPE_KEYS: Readonly<Record<ReviewContentType, string>> = {
  PACKAGE: 'adminHome.typePackage',
  LESSON: 'adminHome.typeLesson',
}

const { t, locale } = useLocalMessages({
  adminHome: adminDashboardMessages,
  auditActions: auditActionMessages,
  aiFeatures: aiFeatureMessages,
  aiSafety: aiSafetyMessages,
})
const { actionLabel } = useAuditLabels(t)

const dashboard = useResource(() => adminOverviewApi.dashboard())
const data = computed(() => dashboard.data.value)
const activity = useResource(() => adminOverviewApi.auditLog({ page: 0, size: ACTIVITY_ITEMS }))
const entries = computed(() => activity.data.value?.items ?? [])

const today = computed(() => formatDate(new Date(), { weekday: 'long', day: 'numeric', month: 'long' }, locale.value))

// ----- system status -----

const health = ref<HealthStatusResponse | null | undefined>(undefined)
const checkingHealth = ref(false)

async function checkHealth(): Promise<void> {
  checkingHealth.value = true
  health.value = await systemApi.health().catch(() => null)
  checkingHealth.value = false
}
void checkHealth()

const healthView = computed<{ tone: 'ok' | 'warn' | 'unknown'; title: string; detail: string }>(() => {
  const value = health.value
  if (value === undefined) return { tone: 'unknown', title: t('adminHome.healthChecking'), detail: '' }
  if (value === null)
    return { tone: 'warn', title: t('adminHome.healthFailed'), detail: t('adminHome.healthFailedDetail') }
  const time = formatRelativeTime(value.timestamp)
  if (value.status !== 'UP' || value.database === 'DOWN') {
    return {
      tone: 'warn',
      title: t('adminHome.healthDatabaseDown'),
      detail: t('adminHome.healthDatabaseDownDetail', { time }),
    }
  }
  if (value.ai === 'UNAVAILABLE') {
    return { tone: 'warn', title: t('adminHome.healthNoAi'), detail: t('adminHome.healthNoAiDetail', { time }) }
  }
  return { tone: 'ok', title: t('adminHome.healthOk'), detail: t('adminHome.healthOkDetail', { time }) }
})

// ----- needs attention -----

function reviewTypeLabel(type: ReviewContentType): string {
  return t(REVIEW_TYPE_KEYS[type])
}

const queues = computed<Queue[]>(() => {
  const value = data.value
  if (!value) return []
  const oldestApplication = value.teacherApplications.items[0]
  const oldestRequest = value.support.items[0]
  const all: Queue[] = [
    {
      key: 'applications',
      title: t('adminHome.queueApplications'),
      count: value.teacherApplications.count,
      to: oldestApplication
        ? { name: 'admin-teacher-applications', query: { teacher: oldestApplication.teacherId } }
        : { name: 'admin-teacher-applications' },
      items: value.teacherApplications.items.map((application) => ({
        key: `application-${application.teacherId}`,
        title: application.fullName,
        meta: t(
          'adminHome.applicationMeta',
          { date: formatDateTime(application.submittedAt), count: application.documentCount },
          application.documentCount,
        ),
        to: { name: 'admin-teacher-applications', query: { teacher: application.teacherId } },
      })),
    },
    {
      key: 'review',
      title: t('adminHome.queueReview'),
      count: value.reviewQueue.count,
      to: { name: 'admin-content-review' },
      items: value.reviewQueue.items.map((item) => ({
        key: `review-${item.type}-${item.id}`,
        title: item.title,
        meta: item.parentTitle
          ? t('adminHome.reviewMetaWithParent', {
              type: reviewTypeLabel(item.type),
              parent: item.parentTitle,
              owner: item.ownerName ?? '—',
            })
          : t('adminHome.reviewMeta', { type: reviewTypeLabel(item.type), owner: item.ownerName ?? '—' }),
        to: { name: 'admin-content-review' },
      })),
    },
    {
      key: 'reports',
      title: t('adminHome.queueReports'),
      count: value.aiReports.count,
      to: { name: 'admin-ai-reports' },
      items: value.aiReports.items.map((report) => {
        const feature = aiFeatureLabel(report.featureKey, t)
        const date = formatShortDate(report.reportedAt)
        return {
          key: `report-${report.id}`,
          title: t(reportReasonKey(report.reason)),
          meta: report.studentName
            ? t('adminHome.reportMeta', { feature, student: report.studentName, date })
            : t('adminHome.reportMetaNoStudent', { feature, date }),
          to: { name: 'admin-ai-reports' },
        }
      }),
    },
    {
      key: 'support',
      title: t('adminHome.queueSupport'),
      count: value.support.count,
      to: oldestRequest ? { name: 'admin-support', query: { request: oldestRequest.id } } : { name: 'admin-support' },
      items: value.support.items.map((request) => ({
        key: `support-${request.id}`,
        title: request.subject,
        meta: t('adminHome.supportMeta', {
          requester: request.requesterName ?? t('adminHome.supportVisitor'),
          time: formatRelativeTime(request.updatedAt),
        }),
        to: { name: 'admin-support', query: { request: request.id } },
      })),
    },
  ]
  // Queues with work come first, in the order above; the clear ones follow as quiet lines. The server sends only the
  // oldest items of each queue; the queue page has the rest.
  return [...all.filter((queue) => queue.count > 0), ...all.filter((queue) => queue.count === 0)]
})

const waiting = computed(() => queues.value.reduce((sum, queue) => sum + queue.count, 0))

const summary = computed(() => {
  if (!data.value) return t('adminHome.lede')
  if (!waiting.value) return t('adminHome.summaryClear')
  const busy = queues.value.filter((queue) => queue.count > 0).length
  return t('adminHome.summary', {
    items: t('adminHome.summaryItems', { count: waiting.value }, waiting.value),
    queues: t('adminHome.summaryQueues', { count: busy }, busy),
  })
})

// ----- numbers and registrations -----

const numbers = computed(() => {
  const value = data.value?.numbers
  if (!value) return []
  return [
    {
      label: t('adminHome.users'),
      value: formatNumber(value.users.total),
      detail: [
        t('adminHome.countStudents', { count: formatNumber(value.users.students) }, value.users.students),
        t('adminHome.countTeachers', { count: formatNumber(value.users.teachers) }, value.users.teachers),
        t('adminHome.countAdmins', { count: formatNumber(value.users.admins) }, value.users.admins),
      ].join(' · '),
    },
    {
      label: t('adminHome.publishedContent'),
      value: formatNumber(value.publishedContent),
      detail: t('adminHome.publishedContentDetail', { total: formatNumber(value.totalContent) }),
    },
    {
      label: t('adminHome.aiRequests'),
      value: formatNumber(value.aiRequests),
      detail: t('adminHome.aiRequestsDetail', { successful: formatNumber(value.successfulAiRequests) }),
    },
    {
      label: t('adminHome.auditEvents'),
      value: formatNumber(value.auditEvents),
      detail: t('adminHome.auditEventsDetail'),
    },
  ]
})

const newAccounts = computed(() => (data.value?.registrations ?? []).reduce((sum, day) => sum + day.count, 0))

const registrationBars = computed(() => {
  const days = data.value?.registrations ?? []
  const counts = days.map((day) => day.count)
  return days.map((day) => ({
    date: day.date,
    count: day.count,
    label: formatShortDate(day.date, locale.value),
    weekday: formatDate(day.date, { weekday: 'short' }, locale.value),
    height: shareOfMax(day.count, counts),
  }))
})
</script>
