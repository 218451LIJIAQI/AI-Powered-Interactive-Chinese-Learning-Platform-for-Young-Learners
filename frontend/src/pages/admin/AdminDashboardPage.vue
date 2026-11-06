<template>
  <div class="page-stack">
    <PageHeaderBlock :title="t('adminHome.pageTitle')" hide-title />

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
    <section v-else class="admin-home" aria-labelledby="admin-home-attention-title">
      <h2 id="admin-home-attention-title" class="section-title">{{ t('adminHome.needsAttention') }}</h2>
      <div class="admin-home-grid">
        <section
          v-for="queue in queues"
          :key="queue.key"
          class="panel admin-home-card"
          :aria-labelledby="`admin-home-queue-${queue.key}`"
          :data-testid="`admin-home-queue-${queue.key}`"
        >
          <header class="admin-section-head">
            <h3 :id="`admin-home-queue-${queue.key}`" class="card-title title-with-count">
              {{ queue.title }}
              <span v-if="queue.count" class="count-badge is-attention" aria-hidden="true">{{ queue.count }}</span>
              <span class="sr-only">{{ t('adminHome.queueCount', { count: queue.count }) }}</span>
            </h3>
            <RouterLink
              class="btn btn-ghost btn-sm admin-link-action"
              :to="queue.to"
              :aria-label="t('adminHome.viewAllLabel', { queue: queue.title })"
            >
              {{ t('adminHome.viewAll') }}
              <IconGlyph name="chevronRight" :size="16" />
            </RouterLink>
          </header>
          <ul v-if="queue.items.length" class="row-list">
            <li v-for="item in queue.items" :key="item.key">
              <RouterLink class="admin-home-row" :to="item.to">
                <PortraitAvatar v-if="item.avatar" :size="36" :name="item.title" />
                <span class="row-list-copy">
                  <span class="row-list-title">{{ item.title }}</span>
                  <span class="row-list-meta">{{ item.meta }}</span>
                </span>
                <IconGlyph class="admin-row-chevron" name="chevronRight" :size="16" />
              </RouterLink>
            </li>
          </ul>
          <p v-else class="admin-meta">{{ t('adminHome.nothingWaiting') }}</p>
        </section>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

import { adminOverviewApi } from '@/api/ops'
import { aiFeatureLabel } from '@/components/admin/adminDisplay'
import { reportReasonKey } from '@/components/ai/aiHelper'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { adminDashboardMessages } from '@/plugins/adminDashboardMessages'
import { aiFeatureMessages } from '@/plugins/aiFeatureMessages'
import { aiSafetyMessages } from '@/plugins/aiSafetyMessages'
import type { ReviewContentType } from '@/types/api'
import { formatDateTime, formatRelativeTime, formatShortDate } from '@/utils/format'
import '@/styles/ops-admin.css'

interface QueueItem {
  key: string
  title: string
  meta: string
  avatar?: boolean
  to: RouteLocationRaw
}

interface Queue {
  key: string
  title: string
  count: number
  to: RouteLocationRaw
  items: QueueItem[]
}

/** The dashboard is a quick look: each card shows the oldest two; its queue page has the rest. */
const ROWS_PER_QUEUE = 2

const REVIEW_TYPE_KEYS: Readonly<Record<ReviewContentType, string>> = {
  PACKAGE: 'adminHome.typePackage',
  LESSON: 'adminHome.typeLesson',
}

const { t } = useLocalMessages({
  adminHome: adminDashboardMessages,
  aiFeatures: aiFeatureMessages,
  aiSafety: aiSafetyMessages,
})

const dashboard = useResource(() => adminOverviewApi.dashboard())
const data = computed(() => dashboard.data.value)

function reviewTypeLabel(type: ReviewContentType): string {
  return t(REVIEW_TYPE_KEYS[type])
}

const queues = computed<Queue[]>(() => {
  const value = data.value
  if (!value) return []
  return [
    {
      key: 'applications',
      title: t('adminHome.queueApplications'),
      count: value.teacherApplications.count,
      to: { name: 'admin-teacher-applications' },
      items: value.teacherApplications.items.slice(0, ROWS_PER_QUEUE).map((application) => ({
        key: `application-${application.teacherId}`,
        title: application.fullName,
        avatar: true,
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
      items: value.reviewQueue.items.slice(0, ROWS_PER_QUEUE).map((item) => ({
        key: `review-${item.type}-${item.id}`,
        title: item.title,
        meta: item.parentTitle
          ? t('adminHome.reviewMetaWithParent', {
              type: reviewTypeLabel(item.type),
              parent: item.parentTitle,
              owner: item.ownerName ?? '—',
            })
          : t('adminHome.reviewMeta', { type: reviewTypeLabel(item.type), owner: item.ownerName ?? '—' }),
        to: {
          name: 'admin-content-review',
          query: { item: `${item.type === 'PACKAGE' ? 'class' : 'lesson'}-${item.id}` },
        },
      })),
    },
    {
      key: 'reports',
      title: t('adminHome.queueReports'),
      count: value.aiReports.count,
      to: { name: 'admin-ai-reports' },
      items: value.aiReports.items.slice(0, ROWS_PER_QUEUE).map((report) => {
        const feature = aiFeatureLabel(report.featureKey, t)
        const date = formatShortDate(report.reportedAt)
        return {
          key: `report-${report.id}`,
          title: t(reportReasonKey(report.reason)),
          meta: report.studentName
            ? t('adminHome.reportMeta', { feature, student: report.studentName, date })
            : t('adminHome.reportMetaNoStudent', { feature, date }),
          to: { name: 'admin-ai-reports', query: { report: report.id } },
        }
      }),
    },
    {
      key: 'support',
      title: t('adminHome.queueSupport'),
      count: value.support.count,
      to: { name: 'admin-support' },
      items: value.support.items.slice(0, ROWS_PER_QUEUE).map((request) => ({
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
})
</script>
