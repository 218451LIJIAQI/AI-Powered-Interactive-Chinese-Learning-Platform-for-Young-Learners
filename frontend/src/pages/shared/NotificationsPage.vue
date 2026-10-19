<template>
  <div class="page-stack notifications-page" data-testid="notifications-page">
    <PageHeaderBlock :title="t('notifications.title')" :description="t('notifications.description')" icon="bell">
      <template #actions>
        <button
          class="btn btn-primary"
          type="button"
          :disabled="markingAll || unreadOnPage === 0"
          :aria-busy="markingAll"
          data-testid="notifications-mark-all"
          @click="markAllRead"
        >
          <IconGlyph v-if="!markingAll" name="status" :size="18" />
          {{ markingAll ? t('notifications.markingAll') : t('notifications.markAllRead') }}
        </button>
      </template>
    </PageHeaderBlock>

    <DataNotice :using-fallback="resource.usingFallback.value" :error="actionError" :success="actionSuccess" />

    <div class="notifications-toolbar">
      <div class="tabs notifications-filters" role="group" :aria-label="t('notifications.filterLabel')">
        <button
          v-for="option in filterOptions"
          :key="option.value"
          class="tab-button"
          :class="{ 'is-active': filter === option.value }"
          type="button"
          :aria-pressed="filter === option.value"
          :data-testid="`notifications-filter-${option.value.toLowerCase()}`"
          @click="selectFilter(option.value)"
        >
          {{ option.label }}
        </button>
      </div>
      <p class="notifications-count" aria-live="polite">
        <span class="notifications-count-dot" aria-hidden="true" />
        {{ t('notifications.unreadCount', unreadOnPage) }}
      </p>
    </div>

    <ResourceSection :loading="resource.loading.value" :error="resource.error.value" @retry="reload">
      <ul v-if="items.length" class="notifications-list" data-testid="notifications-list">
        <li
          v-for="item in items"
          :key="item.id"
          class="panel notifications-item"
          :class="{ 'is-unread': item.status === 'UNREAD' }"
          data-testid="notification-item"
        >
          <span class="notifications-item-icon" aria-hidden="true"><IconGlyph name="bell" :size="20" /></span>
          <div class="notifications-item-body">
            <div class="notifications-item-head">
              <strong>{{ item.title }}</strong>
              <StatusPill
                :label="statusLabel('notificationStatus', item.status)"
                :tone="item.status === 'UNREAD' ? 'primary' : 'neutral'"
              />
            </div>
            <p class="notifications-item-message">{{ item.message }}</p>
            <div class="notifications-item-meta">
              <span class="tag">{{ statusLabel('notificationType', item.type) }}</span>
              <span>{{ t('notifications.received', { date: formatDateTime(item.createdAt) }) }}</span>
              <span v-if="item.readAt">{{ t('notifications.readOn', { date: formatDateTime(item.readAt) }) }}</span>
            </div>
          </div>
          <div class="notifications-item-actions">
            <button
              v-if="notificationTarget(item)"
              class="btn btn-secondary btn-sm"
              type="button"
              data-testid="notification-open"
              @click="openNotification(item)"
            >
              {{ t('notifications.open') }}
              <IconGlyph name="chevronRight" :size="16" />
            </button>
            <button
              v-if="item.status === 'UNREAD'"
              class="btn btn-ghost btn-sm"
              type="button"
              :disabled="pendingId === item.id"
              :aria-busy="pendingId === item.id"
              data-testid="notification-mark-read"
              @click="markRead(item)"
            >
              <IconGlyph v-if="pendingId !== item.id" name="status" :size="16" />
              {{ t('notifications.markRead') }}
            </button>
          </div>
        </li>
      </ul>

      <StatePanel v-else icon="bell" :title="emptyTitle" :message="emptyMessage" tone="empty">
        <template v-if="filter !== 'ALL'" #actions>
          <button class="btn btn-secondary btn-sm" type="button" @click="selectFilter('ALL')">
            {{ t('notifications.filterAll') }}
          </button>
        </template>
      </StatePanel>

      <PaginationBar
        v-if="totalPages > 1"
        :label="t('notifications.pagination')"
        :info="t('notifications.pageOf', { page: page + 1, total: totalPages })"
        :previous-label="t('notifications.previous')"
        :next-label="t('notifications.next')"
        :previous-disabled="page === 0"
        :next-disabled="page >= totalPages - 1"
        @previous="goToPage(page - 1)"
        @next="goToPage(page + 1)"
      />
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { notificationsApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { useStatusLabels } from '@/composables/useStatusLabels'
import { adminNotifications, studentNotifications, teacherNotifications } from '@/demo/data'
import { notificationsMessages } from '@/plugins/notificationsMessages'
import { useAuthStore } from '@/stores/auth'
import type { NotificationResponse, PageResponse, UserRole } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { formatDateTime } from '@/utils/format'
import {
  announceNotificationsRead,
  markNotificationRead,
  notificationQuery,
  notificationTarget,
  pagePreviewNotifications,
  type NotificationFilter,
} from '@/utils/notifications'

const { t } = useI18n({
  messages: {
    en: { notifications: notificationsMessages.en },
    ms: { notifications: notificationsMessages.ms },
    'zh-CN': { notifications: notificationsMessages['zh-CN'] },
  },
})
const { statusLabel } = useStatusLabels()

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const role = computed<UserRole>(() => (route.meta.previewRole as UserRole | undefined) ?? auth.activeRole ?? 'STUDENT')
const PREVIEW_FIXTURES: Record<UserRole, NotificationResponse[]> = {
  STUDENT: studentNotifications,
  TEACHER: teacherNotifications,
  ADMIN: adminNotifications,
}
/** Local copy so preview read actions are visible without touching the shared fixtures. */
const previewItems = ref<NotificationResponse[]>(PREVIEW_FIXTURES[role.value].map((item) => ({ ...item })))

const filter = ref<NotificationFilter>('ALL')
const page = ref(0)
const pendingId = ref<number | null>(null)
const markingAll = ref(false)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)

const resource = useResource<PageResponse<NotificationResponse>>(
  () => notificationsApi.page(notificationQuery(filter.value, page.value)),
  () => pagePreviewNotifications(previewItems.value, filter.value, page.value),
)

const filterOptions = computed(() => [
  { value: 'ALL' as const, label: t('notifications.filterAll') },
  { value: 'UNREAD' as const, label: t('notifications.filterUnread') },
  { value: 'READ' as const, label: t('notifications.filterRead') },
])
const items = computed(() => resource.data.value?.items ?? [])
const totalPages = computed(() => Math.max(1, resource.data.value?.totalPages ?? 1))
const unreadOnPage = computed(() => items.value.filter((item) => item.status === 'UNREAD').length)
const emptyTitle = computed(() =>
  filter.value === 'UNREAD'
    ? t('notifications.emptyUnreadTitle')
    : filter.value === 'READ'
      ? t('notifications.emptyReadTitle')
      : t('notifications.emptyAllTitle'),
)
const emptyMessage = computed(() =>
  filter.value === 'UNREAD'
    ? t('notifications.emptyUnreadMessage')
    : filter.value === 'READ'
      ? t('notifications.emptyReadMessage')
      : t('notifications.emptyAllMessage'),
)

function clearMessages(): void {
  actionError.value = null
  actionSuccess.value = null
}

function reload(): Promise<void> {
  return resource.reload()
}

function selectFilter(value: NotificationFilter): void {
  if (filter.value === value) return
  filter.value = value
  page.value = 0
  clearMessages()
  void reload()
}

function goToPage(next: number): void {
  page.value = Math.min(Math.max(0, next), totalPages.value - 1)
  void reload()
}

function applyLocalRead(id: number): void {
  if (resource.data.value) {
    resource.data.value = { ...resource.data.value, items: markNotificationRead(resource.data.value.items, id) }
  }
}

async function markRead(item: NotificationResponse): Promise<boolean> {
  clearMessages()
  if (resource.usingFallback.value) {
    previewItems.value = markNotificationRead(previewItems.value, item.id)
    applyLocalRead(item.id)
    actionSuccess.value = t('notifications.previewAction')
    return true
  }

  pendingId.value = item.id
  try {
    await notificationsApi.markAsRead(item.id)
    applyLocalRead(item.id)
    announceNotificationsRead(item.id)
    actionSuccess.value = t('notifications.markedRead')
    if (filter.value === 'UNREAD') void reload()
    return true
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('notifications.markReadError'))
    return false
  } finally {
    pendingId.value = null
  }
}

async function markAllRead(): Promise<void> {
  clearMessages()
  if (resource.usingFallback.value) {
    previewItems.value = previewItems.value.map((item) => ({
      ...item,
      status: 'READ',
      readAt: item.readAt ?? new Date().toISOString(),
    }))
    await reload()
    actionSuccess.value = t('notifications.previewAction')
    return
  }

  markingAll.value = true
  try {
    const result = await notificationsApi.markAllRead()
    announceNotificationsRead()
    await reload()
    actionSuccess.value = t('notifications.markedAllRead', result.updatedCount)
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('notifications.markAllError'))
  } finally {
    markingAll.value = false
  }
}

async function openNotification(item: NotificationResponse): Promise<void> {
  const target = notificationTarget(item)
  if (!target) return
  if (item.status === 'UNREAD') {
    await markRead(item)
  }
  await router.push(target)
}
</script>

<style scoped>
.notifications-page {
  display: grid;
  gap: var(--page-gap);
}

.notifications-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.notifications-count {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
}

.notifications-count-dot {
  width: var(--space-2);
  height: var(--space-2);
  border-radius: var(--radius-round);
  background: var(--color-primary-500);
}

.notifications-list {
  display: grid;
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.notifications-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: start;
  gap: var(--space-4);
  transition: border-color var(--duration-fast) var(--ease-standard);
}

.notifications-item.is-unread {
  border-color: var(--color-primary-200);
  background: linear-gradient(90deg, var(--color-primary-50), var(--surface-card) 40%);
  box-shadow:
    inset var(--border-width-thick) 0 0 var(--color-primary-500),
    var(--shadow-sm);
}

.notifications-item-icon {
  display: grid;
  place-items: center;
  width: var(--space-10);
  height: var(--space-10);
  border-radius: var(--radius-md);
  background: var(--surface-sunken);
  color: var(--text-secondary);
}

.is-unread .notifications-item-icon {
  background: var(--color-primary-100);
  color: var(--color-primary-700);
}

.notifications-item-body {
  display: grid;
  gap: var(--space-1-5);
  min-width: 0;
}

.notifications-item-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.notifications-item-head strong {
  flex: 1 1 200px;
  min-width: 0;
  color: var(--text-primary);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-extrabold);
  overflow-wrap: anywhere;
}

.notifications-item-message {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
  white-space: pre-line;
  overflow-wrap: anywhere;
}

.notifications-item-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-1-5) var(--space-3);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.notifications-item-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2);
}

@media (max-width: 640px) {
  .notifications-item {
    grid-template-columns: auto minmax(0, 1fr);
    gap: var(--space-3);
  }

  .notifications-item-actions {
    grid-column: 1 / -1;
    justify-content: flex-start;
  }

  .notifications-item-actions > .btn {
    flex: 1 1 auto;
  }
}
</style>
