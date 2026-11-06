<template>
  <div class="page-stack notifications-page">
    <PageHeaderBlock :title="t('notifications.pageTitle')" hide-title>
      <template v-if="!hasNoNotifications" #actions>
        <p v-if="unreadResource.data.value" class="ops-toolbar-note" data-testid="notifications-unread">
          {{ t('notifications.unreadCount', { count: unreadTotal }, unreadTotal) }}
        </p>
        <button
          class="btn btn-secondary"
          type="button"
          data-testid="notifications-mark-all"
          :disabled="!unreadTotal || markingAll"
          :aria-busy="markingAll"
          @click="markAllRead"
        >
          <IconGlyph name="status" :size="16" />
          {{ t('notifications.markAllRead') }}
        </button>
      </template>
    </PageHeaderBlock>
    <AppToast :message="toast" />

    <DataNotice :error="actionError" />

    <!-- Nothing has ever arrived: the page is one empty card with the way back to the role's start page. -->
    <section v-if="hasNoNotifications" class="panel notifications-empty">
      <StatePanel tone="empty" icon="bell" spacious :title="t('notifications.emptyAllTitle')" :message="emptyMessage">
        <template #actions>
          <RouterLink class="btn btn-primary" :to="{ name: startPage.route }">{{ t(startPage.label) }}</RouterLink>
        </template>
      </StatePanel>
    </section>

    <section v-else class="panel notifications-feed" aria-labelledby="notifications-title">
      <h2 id="notifications-title" class="sr-only">{{ t('notifications.allNotifications') }}</h2>
      <FilterChips
        :model-value="filter"
        :label="t('notifications.filterLabel')"
        :options="filterOptions"
        @update:model-value="selectFilter"
      />

      <StatePanel
        v-if="resource.loading.value"
        tone="loading"
        :title="t('notifications.loadingTitle')"
        :message="t('notifications.loadingHint')"
      />
      <StatePanel
        v-else-if="resource.error.value"
        tone="error"
        icon="alert"
        :title="t('notifications.loadErrorTitle')"
        :message="resource.error.value"
      >
        <template #actions>
          <button class="btn btn-primary" type="button" @click="reload">{{ t('notifications.retry') }}</button>
        </template>
      </StatePanel>
      <ul v-else-if="items.length" class="row-list notifications-list" data-testid="notifications-list">
        <li v-for="item in items" :key="item.id" data-testid="notification-item">
          <div class="row-list-copy notifications-item-copy">
            <div class="notifications-item-head">
              <p class="row-list-title">{{ item.title }}</p>
              <StatusPill v-if="item.status === 'UNREAD'" :label="t('notifications.unread')" tone="strong" />
            </div>
            <p class="row-list-meta">{{ item.message }}</p>
            <p class="row-list-meta notifications-item-meta">
              <span class="tag">{{ t(notificationKind(item.type, auth.activeRole).labelKey) }}</span>
              <time :datetime="item.createdAt">{{ formatDateTime(item.createdAt, locale) }}</time>
            </p>
          </div>
          <div class="row-list-actions">
            <button
              v-if="item.status === 'UNREAD'"
              class="btn btn-ghost btn-sm"
              type="button"
              :disabled="pendingId === item.id"
              :aria-label="t('notifications.markReadLabel', { title: item.title })"
              data-testid="notification-mark-read"
              @click="markReadInRow(item, $event)"
            >
              {{ t('notifications.markRead') }}
            </button>
            <button
              v-if="targetOf(item)"
              class="btn btn-secondary btn-sm"
              type="button"
              :aria-label="t('notifications.openLabel', { title: item.title })"
              data-testid="notification-open"
              @click="openNotification(item)"
            >
              {{ t('notifications.open') }}
              <IconGlyph name="chevronRight" :size="16" />
            </button>
          </div>
        </li>
      </ul>
      <StatePanel
        v-else-if="filter === 'UNREAD'"
        tone="empty"
        icon="check"
        :title="t('notifications.emptyUnreadTitle')"
        :message="t('notifications.emptyUnreadMessage')"
      />
      <StatePanel
        v-else
        tone="empty"
        icon="bell"
        :title="t('notifications.emptyReadTitle')"
        :message="t('notifications.emptyReadMessage')"
      />

      <PaginationBar
        v-if="totalPages > 1"
        :current-page="page + 1"
        :total-pages="totalPages"
        :label="t('notifications.pagination')"
        @update:current-page="goToPage($event - 1)"
      />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { notificationsApi } from '@/api/services'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import FilterChips from '@/components/common/FilterChips.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource, useServerPage } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { notificationsMessages } from '@/plugins/notificationsMessages'
import { useAuthStore } from '@/stores/auth'
import type { NotificationResponse } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { formatDateTime } from '@/utils/format'
import {
  announceNotificationsRead,
  markNotificationRead,
  notificationKind,
  notificationQuery,
  type NotificationFilter,
} from '@/utils/notifications'
import { normalizeAppRoute } from '@/utils/routeCompat'
import '@/styles/ops-account.css'

const { t, locale } = useLocalMessages({ notifications: notificationsMessages })
const auth = useAuthStore()
const router = useRouter()
const { toast, showToast } = useToast()

const filter = ref<NotificationFilter>('ALL')
const page = ref(0)
const pendingId = ref<number | null>(null)
const markingAll = ref(false)
const actionError = ref<string | null>(null)

const resource = useResource(() => notificationsApi.page(notificationQuery(filter.value, page.value)))
// The account-wide totals, whatever filter and page are on screen.
const unreadResource = useResource(() => notificationsApi.unreadCount())
const totalResource = useResource(() => notificationsApi.page({ page: 0, size: 1 }))
const { items, totalPages, goToPage } = useServerPage(resource, page, { reload })
const unreadTotal = computed(() => unreadResource.data.value?.unreadCount ?? 0)
const allTotal = computed(() => totalResource.data.value?.totalItems)
const hasNoNotifications = computed(
  () => allTotal.value === 0 || (filter.value === 'ALL' && resource.data.value?.totalItems === 0),
)

const filterOptions = computed(() => {
  const all = allTotal.value
  const unread = unreadResource.data.value?.unreadCount
  const hasCounts = all !== undefined && unread !== undefined
  return [
    {
      value: 'ALL' as const,
      label: t('notifications.filterAll'),
      count: all,
      testId: 'notifications-filter-all',
    },
    {
      value: 'UNREAD' as const,
      label: t('notifications.filterUnread'),
      count: unread,
      testId: 'notifications-filter-unread',
    },
    {
      value: 'READ' as const,
      label: t('notifications.filterRead'),
      count: hasCounts ? Math.max(0, all - unread) : undefined,
      testId: 'notifications-filter-read',
    },
  ]
})

const emptyMessage = computed(() => {
  if (auth.activeRole === 'ADMIN') return t('notifications.emptyAllAdmin')
  if (auth.activeRole === 'TEACHER') {
    return auth.isApprovedTeacher ? t('notifications.emptyAllTeacher') : t('notifications.emptyAllApplicant')
  }
  return t('notifications.emptyAllStudent')
})

const startPage = computed(() => {
  if (auth.activeRole === 'ADMIN') return { route: 'admin-dashboard', label: 'notifications.goToDashboard' }
  if (auth.activeRole === 'TEACHER') return { route: 'teacher-dashboard', label: 'notifications.goToDashboard' }
  return { route: 'student-home', label: 'notifications.goToHome' }
})

async function reload(): Promise<void> {
  await resource.reload()
  // Reading the last item of a later Unread page leaves that page empty: step back a page.
  const data = resource.data.value
  if (data && !data.items.length && page.value > 0) {
    page.value = Math.max(0, Math.min(page.value - 1, data.totalPages - 1))
    await resource.reload()
  }
}

function selectFilter(value: NotificationFilter): void {
  if (filter.value === value) return
  filter.value = value
  page.value = 0
  actionError.value = null
  void reload()
}

async function markRead(item: NotificationResponse, quiet = false): Promise<boolean> {
  pendingId.value = item.id
  actionError.value = null
  try {
    await notificationsApi.markAsRead(item.id)
    if (resource.data.value) {
      resource.data.value = { ...resource.data.value, items: markNotificationRead(resource.data.value.items, item.id) }
    }
    announceNotificationsRead()
    void unreadResource.reload()
    if (filter.value === 'UNREAD') void reload()
    if (!quiet) showToast(t('notifications.markedRead'))
    return true
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('notifications.markReadError'))
    return false
  } finally {
    pendingId.value = null
  }
}

/** The button that was clicked goes away with the Unread state: the keyboard continues from the row's Open. */
async function markReadInRow(item: NotificationResponse, event: Event): Promise<void> {
  const row = (event.currentTarget as HTMLElement).closest('li')
  if (!(await markRead(item))) return
  await nextTick()
  row?.querySelector<HTMLElement>('button')?.focus()
}

async function markAllRead(): Promise<void> {
  markingAll.value = true
  actionError.value = null
  try {
    await notificationsApi.markAllRead()
    announceNotificationsRead()
    await Promise.all([reload(), unreadResource.reload()])
    showToast(t('notifications.markedAllRead'))
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('notifications.markAllError'))
  } finally {
    markingAll.value = false
  }
}

/** A first-party page the notification points to (null for an unknown or unsafe path: no Open button). */
function targetOf(item: NotificationResponse): string | null {
  return normalizeAppRoute(item.targetPath)
}

/** Opening a notification also reads it; old stored paths redirect to their current page in the router. */
async function openNotification(item: NotificationResponse): Promise<void> {
  const target = targetOf(item)
  if (!target) return
  if (item.status === 'UNREAD') await markRead(item, true)
  await router.push(target)
}
</script>
