<template>
  <div class="page-stack notifications-page">
    <PageHeaderBlock :title="t('notifications.pageTitle')">
      <template #meta>
        <p class="notifications-unread" :class="{ 'has-unread': unreadTotal }" data-testid="notifications-unread">
          <span v-if="unreadTotal" class="notifications-dot" aria-hidden="true" />{{
            t('notifications.unreadCount', { count: unreadTotal }, unreadTotal)
          }}
        </p>
      </template>
      <template #actions>
        <button
          class="btn btn-secondary"
          type="button"
          data-testid="notifications-mark-all"
          :disabled="!unreadTotal || markingAll"
          :aria-busy="markingAll"
          @click="markAllRead"
        >
          <IconGlyph name="check" :size="16" />
          {{ t('notifications.markAllRead') }}
        </button>
      </template>
    </PageHeaderBlock>
    <AppToast :message="toast" />

    <DataNotice :error="actionError" />

    <section class="notifications-feed" aria-labelledby="notifications-title">
      <div class="notifications-feed-head">
        <h2 id="notifications-title" class="sr-only">{{ t('notifications.allNotifications') }}</h2>
        <SegmentedControl
          :model-value="filter"
          :label="t('notifications.filterLabel')"
          :options="
            FILTERS.map((option) => ({
              value: option.value,
              label: t(option.label),
              testId: `notifications-filter-${option.value.toLowerCase()}`,
            }))
          "
          @update:model-value="selectFilter"
        />
      </div>

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
      <!-- A timeline by day: each day a heading, its notifications on a thread beneath it. -->
      <div v-else-if="items.length" class="notifications-days" data-testid="notifications-list">
        <section
          v-for="group in groups"
          :key="group.key"
          class="notifications-day"
          :aria-labelledby="`notifications-day-${group.key}`"
        >
          <h3 :id="`notifications-day-${group.key}`" class="notifications-day-title">{{ group.label }}</h3>
          <ol class="notifications-list">
            <li
              v-for="item in group.items"
              :key="item.id"
              class="notifications-item"
              :class="{ 'is-unread': item.status === 'UNREAD' }"
              data-testid="notification-item"
            >
              <span class="notifications-item-icon" aria-hidden="true">
                <IconGlyph :name="kindOf(item).icon" :size="18" />
              </span>
              <div class="notifications-item-body">
                <p class="notifications-item-title">{{ item.title }}</p>
                <p class="notifications-item-message">{{ item.message }}</p>
                <p class="notifications-item-meta">
                  <span v-if="item.status === 'UNREAD'" class="notifications-item-new">
                    <span class="notifications-dot" aria-hidden="true" />{{ t('notifications.unread') }}
                  </span>
                  <span>{{ t(kindOf(item).labelKey) }}</span>
                  <time :datetime="item.createdAt">{{ formatTime(item.createdAt) }}</time>
                </p>
              </div>
              <div class="notifications-item-actions">
                <button
                  v-if="item.status === 'UNREAD'"
                  class="btn btn-ghost btn-sm"
                  type="button"
                  :disabled="pendingId === item.id"
                  :aria-label="t('notifications.markReadLabel', { title: item.title })"
                  data-testid="notification-mark-read"
                  @click="markRead(item)"
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
                  <IconGlyph name="chevronRight" :size="14" />
                </button>
              </div>
            </li>
          </ol>
        </section>
      </div>
      <StatePanel
        v-else
        tone="empty"
        :icon="filter === 'UNREAD' ? 'check' : 'bell'"
        :title="t(EMPTY_STATE[filter].title)"
        :message="t(EMPTY_STATE[filter].message)"
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
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import { notificationsApi } from '@/api/services'
import SegmentedControl from '@/components/common/SegmentedControl.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import AppToast from '@/components/common/AppToast.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource, useServerPage } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { notificationsMessages } from '@/plugins/notificationsMessages'
import { useAuthStore } from '@/stores/auth'
import type { NotificationResponse } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { formatDate, formatTime, parseDate } from '@/utils/format'
import {
  announceNotificationsRead,
  markNotificationRead,
  notificationKind,
  notificationQuery,
  type NotificationFilter,
} from '@/utils/notifications'
import { normalizeAppRoute } from '@/utils/routeCompat'
import '@/styles/ops-account.css'

const FILTERS = [
  { value: 'ALL', label: 'notifications.filterAll' },
  { value: 'UNREAD', label: 'notifications.filterUnread' },
  { value: 'READ', label: 'notifications.filterRead' },
] as const satisfies readonly { value: NotificationFilter; label: string }[]

const EMPTY_STATE: Record<NotificationFilter, { title: string; message: string }> = {
  ALL: { title: 'notifications.emptyAllTitle', message: 'notifications.emptyAllMessage' },
  UNREAD: { title: 'notifications.emptyUnreadTitle', message: 'notifications.emptyUnreadMessage' },
  READ: { title: 'notifications.emptyReadTitle', message: 'notifications.emptyReadMessage' },
}

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
// The account-wide unread total, whatever filter and page are on screen.
const unreadResource = useResource(() => notificationsApi.unreadCount())
const { items, totalPages, goToPage } = useServerPage(resource, page, { reload })
const unreadTotal = computed(() => unreadResource.data.value?.unreadCount ?? 0)

/** The page's notifications by local day, newest first as the server sends them: Today, Yesterday, then dates. */
const groups = computed(() => {
  const today = dayKey(new Date())
  const yesterday = dayKey(new Date(Date.now() - 86_400_000))
  const list: { key: string; label: string; items: NotificationResponse[] }[] = []
  for (const item of items.value) {
    const date = parseDate(item.createdAt)
    const key = date ? dayKey(date) : 'unknown'
    let group = list.find((entry) => entry.key === key)
    if (!group) {
      const label =
        key === today
          ? t('notifications.today')
          : key === yesterday
            ? t('notifications.yesterday')
            : formatDate(date, { weekday: 'long', month: 'long', day: 'numeric' }, locale.value)
      group = { key, label, items: [] }
      list.push(group)
    }
    group.items.push(item)
  }
  return list
})

function dayKey(date: Date): string {
  return `${date.getFullYear()}-${date.getMonth() + 1}-${date.getDate()}`
}

function kindOf(item: NotificationResponse) {
  return notificationKind(item.type, auth.activeRole)
}

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
