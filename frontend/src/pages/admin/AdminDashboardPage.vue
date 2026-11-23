<template>
  <div class="page-stack">
    <PageHeaderBlock :title="t('adminHome.pageTitle')" hide-title />

    <!-- Loading: the cards' outline with placeholder lines (A1.06); the sidebar and header stay usable. -->
    <section
      v-if="dashboard.loading.value"
      class="admin-home"
      aria-busy="true"
      :aria-label="t('adminHome.loading')"
      data-testid="admin-home-loading"
    >
      <span class="skeleton skeleton-line admin-home-skeleton-title" aria-hidden="true" />
      <div class="admin-home-grid" aria-hidden="true">
        <div v-for="card in 3" :key="card" class="panel admin-home-card">
          <span class="skeleton skeleton-line is-short" />
          <div class="admin-home-skeleton-rows">
            <span class="skeleton skeleton-line" />
            <span class="skeleton skeleton-line is-short" />
            <span class="skeleton skeleton-line" />
            <span class="skeleton skeleton-line is-short" />
          </div>
        </div>
      </div>
      <p class="sr-only" role="status">{{ t('adminHome.loading') }}</p>
    </section>
    <StatePanel
      v-else-if="!data"
      heading-tag="h2"
      tone="error"
      icon="alert"
      spacious
      :title="t('adminHome.loadError')"
      :message="dashboard.error.value ?? ''"
      @retry="dashboard.reload"
    />
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
              <span class="admin-queue-icon"><IconGlyph :name="queue.icon" :size="19" /></span>
              {{ queue.title }}
              <span v-if="queue.count" class="sr-only">{{ t('adminHome.queueCount', { count: queue.count }) }}</span>
            </h3>
            <span v-if="queue.count" class="count-badge" aria-hidden="true">{{ formatBadgeCount(queue.count) }}</span>
          </header>
          <ul v-if="queue.items.length" class="row-list">
            <li v-for="item in queue.items" :key="item.key">
              <RouterLink class="admin-home-row" :to="item.to">
                <span class="row-list-copy">
                  <span class="row-list-title admin-home-row-title" :title="item.title">{{ item.title }}</span>
                  <span class="row-list-meta">{{ item.meta }}</span>
                </span>
                <IconGlyph class="admin-row-chevron" name="chevronRight" :size="16" />
              </RouterLink>
            </li>
          </ul>
          <p v-else class="admin-meta">{{ queue.empty }}</p>
          <RouterLink
            class="btn btn-ghost btn-sm admin-link-action"
            :to="queue.to"
            :aria-label="t('adminHome.viewAllLabel', { queue: queue.title })"
          >
            {{ t('adminHome.viewAll') }}<IconGlyph name="chevronRight" :size="16" />
          </RouterLink>
        </section>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

import { adminOverviewApi } from '@/api/ops'
import { reportReasonKey } from '@/components/ai/aiHelper'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { adminDashboardMessages } from '@/plugins/adminDashboardMessages'
import { formatBadgeCount, formatRelativeTime, formatShortDate } from '@/utils/format'
import '@/styles/admin-shared.css'
import '@/styles/ops-admin.css'

interface QueueItem {
  key: string
  title: string
  meta: string
  to: RouteLocationRaw
}

interface Queue {
  key: string
  icon: string
  title: string
  count: number
  empty: string
  to: RouteLocationRaw
  items: QueueItem[]
}

const { t } = useLocalMessages({ adminHome: adminDashboardMessages })

const dashboard = useResource(adminOverviewApi.dashboard)
const data = dashboard.data

/** A quick look (A1.01): the server sends the first two items of each queue; its page has the rest. */
const queues = computed<Queue[]>(() => {
  const value = data.value
  if (!value) return []
  return [
    {
      key: 'class-drafts',
      icon: 'content',
      title: t('adminHome.queueClassDrafts'),
      count: value.classDrafts.count,
      empty: t('adminHome.noClassDrafts'),
      to: { name: 'admin-classes', query: { type: 'CLASS', status: 'DRAFT' } },
      items: value.classDrafts.items.map((draft) => ({
        key: `class-${draft.id}`,
        title: draft.title,
        meta: t(
          'adminHome.classDraftMeta',
          { count: draft.lessonCount, date: formatShortDate(draft.updatedAt) },
          draft.lessonCount,
        ),
        to: { name: 'admin-class', params: { classId: draft.id } },
      })),
    },
    {
      key: 'reports',
      icon: 'flag',
      title: t('adminHome.queueReports'),
      count: value.aiReports.count,
      empty: t('adminHome.nothingWaiting'),
      to: { name: 'admin-ai-reports' },
      items: value.aiReports.items.map((report) => ({
        key: `report-${report.id}`,
        title: t(reportReasonKey(report.reason)),
        meta: t('adminHome.reportMeta', { student: report.studentName, date: formatShortDate(report.reportedAt) }),
        to: { name: 'admin-ai-reports', query: { report: report.id } },
      })),
    },
    {
      key: 'support',
      icon: 'support',
      title: t('adminHome.queueSupport'),
      count: value.support.count,
      empty: t('adminHome.nothingWaiting'),
      to: { name: 'admin-support' },
      items: value.support.items.map((request) => ({
        key: `support-${request.id}`,
        title: request.subject,
        meta: t('adminHome.supportMeta', {
          requester: request.requesterRole === 'VISITOR' ? t('adminHome.supportVisitor') : request.requesterName,
          time: formatRelativeTime(request.updatedAt),
        }),
        to: { name: 'admin-support', query: { request: request.id } },
      })),
    },
  ]
})
</script>
