<template>
  <div class="page-stack">
    <PageHeaderBlock :title="t('notifications.pageTitle')" hide-title />
    <AppToast :message="toast" :action="toastAction" />

    <DataNotice :error="actionError" />

    <!-- Nothing has ever arrived: the page is one empty card with the way back to the role's start page. The button
         only leaves an empty page, so it is a secondary one. -->
    <section v-if="hasNoNotifications" class="panel notifications-empty">
      <StatePanel
        heading-tag="h2"
        tone="empty"
        icon="bell"
        spacious
        :title="t('notifications.emptyAllTitle')"
        :message="emptyMessage"
      >
        <template #actions>
          <RouterLink class="btn btn-secondary" :to="startPage.to" data-testid="notifications-start-page">
            {{ startPage.label }}
          </RouterLink>
        </template>
      </StatePanel>
    </section>

    <section v-else class="panel notifications-feed" aria-labelledby="notifications-title">
      <h2 id="notifications-title" class="sr-only">{{ t('notifications.allNotifications') }}</h2>
      <div ref="toolbar" class="notifications-toolbar">
        <FilterChips
          :model-value="filter"
          :label="t('notifications.filterLabel')"
          :options="filterOptions"
          @update:model-value="selectFilter"
        />
        <button
          v-if="unreadTotal > 0 && filter !== 'READ'"
          class="btn btn-secondary"
          type="button"
          data-testid="notifications-mark-all"
          :disabled="markAllBusy"
          :aria-busy="markingAll"
          @click="markAllRead"
        >
          <IconGlyph name="status" :size="16" />
          {{ t('notifications.markAllRead') }}
        </button>
      </div>

      <StatePanel
        v-if="loadingList"
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
        @retry="refresh"
      />
      <!-- By the day they arrived; a coloured mark says what each is about while it is unread. -->
      <div v-else-if="rows.length" class="notifications-days">
        <section
          v-for="day in days"
          :key="day.key"
          class="notifications-day"
          :aria-labelledby="`notifications-day-${day.key}`"
          :data-testid="`notifications-day-${day.key}`"
        >
          <h3 :id="`notifications-day-${day.key}`" class="notifications-day-title">{{ day.label }}</h3>
          <ul class="row-list notifications-list">
            <li
              v-for="item in day.rows"
              :key="item.id"
              :class="{ 'is-unread': item.status === 'UNREAD' }"
              :aria-busy="markingIds.has(item.id)"
              tabindex="-1"
              data-testid="notification-item"
            >
              <span class="notifications-type" :class="`tint-${item.kind.tone}`" aria-hidden="true">
                <IconGlyph :name="item.kind.icon" :size="18" />
              </span>
              <div class="row-list-copy notifications-item-copy">
                <div class="notifications-item-head">
                  <p class="row-list-title">{{ item.title }}</p>
                  <StatusPill v-if="item.status === 'UNREAD'" :label="t('notifications.unread')" tone="strong" />
                </div>
                <p class="notifications-item-message">{{ item.message }}</p>
                <p class="row-list-meta">
                  <time :datetime="item.createdAt">{{ formatDateTime(item.createdAt, locale) }}</time>
                </p>
              </div>
              <div class="row-list-actions">
                <button
                  v-if="item.status === 'UNREAD'"
                  class="btn btn-ghost btn-sm"
                  type="button"
                  :disabled="markReadBusy(item.id)"
                  :aria-label="t('notifications.markReadLabel', { title: item.title })"
                  data-testid="notification-mark-read"
                  @click="markReadInRow(item, $event)"
                >
                  {{ t('notifications.markRead') }}
                </button>
                <button
                  v-if="item.target"
                  class="btn btn-secondary btn-sm"
                  type="button"
                  :disabled="item.status === 'UNREAD' && markReadBusy(item.id)"
                  :aria-label="t('notifications.openLabel', { title: item.title })"
                  data-testid="notification-open"
                  @click="openNotification(item, item.target)"
                >
                  {{ t('common.open') }}
                  <IconGlyph name="chevronRight" :size="16" />
                </button>
              </div>
            </li>
          </ul>
        </section>
      </div>
      <!-- An empty filter says why and offers the way out. -->
      <StatePanel
        v-else
        tone="empty"
        :icon="emptyFilter.icon"
        :title="emptyFilter.title"
        :message="emptyFilter.message"
        data-testid="notifications-filter-empty"
      >
        <template #actions>
          <RouterLink class="btn btn-secondary" :to="startPage.to" data-testid="notifications-start-page">
            {{ startPage.label }}
          </RouterLink>
        </template>
      </StatePanel>

      <PaginationBar
        :page="displayedPage"
        :total-pages="totalPages"
        :label="t('notifications.pagination')"
        @update:page="goToPage"
      />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { getAuthSessionVersion } from '@/api/http'
import { notificationsApi } from '@/api/services'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import FilterChips from '@/components/common/FilterChips.vue'
import IconGlyph, { type IconName } from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { unreadNotificationsKey } from '@/composables/navigationCounts'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { usePageActive } from '@/composables/usePageNavigation'
import { useResource, useServerPage } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { useWorkspaceRole } from '@/composables/useWorkspaceRole'
import { notificationsMessages } from '@/plugins/notificationsMessages'
import type { NotificationResponse, NotificationStatus } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { normalizeAppRoute } from '@/utils/appRoute'
import { formatDateTime } from '@/utils/format'
import { normalizeAppLanguage } from '@/utils/locale'
import { defaultRouteForRole } from '@/utils/role'
import { localizeNotificationMessage } from '@/utils/notificationMessages'
import '@/styles/ops-account.css'

/**
 * The notifications of a learner or an admin: All · Unread · Read with their counts, the rows by day (Today, Yesterday,
 * Earlier), each with a mark of what it is about (coloured while unread), [Mark as read] and [Open ›]. Marking one, or
 * all, as read happens at once and the toast offers Undo; opening a notification reads it on the way. The bell in the
 * header follows every change.
 */
type NotificationFilter = 'ALL' | NotificationStatus

const PAGE_SIZE = 20
const UNREAD_BATCH_SIZE = 1000 // Maximum ids accepted by PATCH /notifications/unread.
const DAY_KEYS = ['today', 'yesterday', 'earlier'] as const
type DayKey = (typeof DAY_KEYS)[number]

/** What a notification is about, as a mark beside it: an icon and its tint (tokens.css `--tint-*`). */
interface NotificationKind {
  readonly icon: IconName
  readonly tone: 'violet' | 'blue' | 'teal' | 'green' | 'amber' | 'rose' | 'neutral'
}

const NOTIFICATION_KINDS: Readonly<Record<string, NotificationKind>> = {
  DAILY_GOAL_REMINDER: { icon: 'target', tone: 'amber' },
  NEW_PRACTICE_SET: { icon: 'practice', tone: 'teal' },
  MISTAKES_TO_REVIEW: { icon: 'retry', tone: 'rose' },
  RANK_REACHED: { icon: 'trophy', tone: 'violet' },
  AI_REPORT_REVIEW: { icon: 'flag', tone: 'blue' },
  LESSON_UPDATED: { icon: 'classes', tone: 'green' },
  ADMIN_TASK: { icon: 'checklist', tone: 'amber' },
  SUPPORT_REQUEST: { icon: 'support', tone: 'blue' },
}
const OTHER_KIND: NotificationKind = { icon: 'bell', tone: 'neutral' }

const { t, locale } = useLocalMessages({ notifications: notificationsMessages })
const role = useWorkspaceRole()
const router = useRouter()
const pageActive = usePageActive()
const { toast, toastAction, showToast, hideToast } = useToast()

const filter = ref<NotificationFilter>('ALL')
const page = ref(0)
/** The notifications being marked as read: each row waits for its own request only. */
const markingIds = reactive(new Set<number>())
const markingAll = ref(false)
const pendingUndos = ref(0)
const batchBusy = computed(() => markingAll.value || pendingUndos.value > 0)
const markAllBusy = computed(() => batchBusy.value || markingIds.size > 0)

function markReadBusy(id: number): boolean {
  return batchBusy.value || markingIds.has(id)
}
const actionError = ref<string | null>(null)
const toolbar = ref<HTMLElement | null>(null)

const resource = useResource(() =>
  notificationsApi.page({
    status: filter.value === 'ALL' ? undefined : filter.value,
    page: page.value,
    size: PAGE_SIZE,
  }),
)
const { data: unread, reload: reloadUnread } = inject(unreadNotificationsKey)!

// Reading the last item of a later Unread page leaves that page empty: it steps back a page.
const { items, displayedPage, totalPages, goToPage } = useServerPage(resource, page, { stepBackFromEmpty: true })
const unreadTotal = computed(() => unread.value?.unreadCount ?? 0)
const rows = computed(() => {
  const language = normalizeAppLanguage(locale.value) ?? 'en'
  return items.value.map((item) => ({
    ...item,
    title: localizeNotificationMessage(item.title, language),
    message: localizeNotificationMessage(item.message, language),
    target: normalizeAppRoute(item.targetPath),
    kind: NOTIFICATION_KINDS[item.type] ?? OTHER_KIND,
  }))
})

/**
 * The rows by the day they arrived (Today, Yesterday, Earlier), newest first as the server sends them. A day is the
 * browser's, like the times on the rows.
 */
const days = computed(() => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const yesterday = new Date(today)
  yesterday.setDate(yesterday.getDate() - 1)
  const byDay: Record<DayKey, typeof rows.value> = { today: [], yesterday: [], earlier: [] }
  for (const row of rows.value) {
    const arrived = new Date(row.createdAt)
    const key: DayKey = arrived >= today ? 'today' : arrived >= yesterday ? 'yesterday' : 'earlier'
    byDay[key].push(row)
  }
  return DAY_KEYS.filter((key) => byDay[key].length).map((key) => ({
    key,
    label: t(`notifications.${key}`),
    rows: byDay[key],
  }))
})

// The account-wide total, whatever filter is on screen: the All filter reports it, and the page opens on All.
const allTotal = ref<number>()
watch(resource.data, (data) => {
  if (data && filter.value === 'ALL') allTotal.value = data.totalItems
})
const hasNoNotifications = computed(() => allTotal.value === 0)

const filterOptions = computed(() => {
  const all = allTotal.value
  const unreadCount = unread.value?.unreadCount
  return [
    { value: 'ALL' as const, label: t('notifications.filterAll'), count: all, testId: 'notifications-filter-all' },
    {
      value: 'UNREAD' as const,
      label: t('notifications.unread'),
      count: unreadCount,
      testId: 'notifications-filter-unread',
    },
    {
      value: 'READ' as const,
      label: t('notifications.filterRead'),
      count: all !== undefined && unreadCount !== undefined ? Math.max(0, all - unreadCount) : undefined,
      testId: 'notifications-filter-read',
    },
  ]
})

const emptyMessage = computed(() =>
  role.value === 'ADMIN' ? t('notifications.emptyAllAdmin') : t('notifications.emptyAllLearner'),
)

const loadingList = computed(
  () =>
    !resource.error.value &&
    !rows.value.length &&
    (resource.loading.value || (resource.data.value?.page ?? 0) > page.value),
)

const emptyFilter = computed<{ icon: IconName; title: string; message: string }>(() => {
  switch (filter.value) {
    case 'UNREAD':
      return {
        icon: 'check',
        title: t('notifications.emptyUnreadTitle'),
        message: t('notifications.emptyUnreadMessage'),
      }
    case 'READ':
      return {
        icon: 'bell',
        title: t('notifications.emptyReadTitle'),
        message: t('notifications.emptyReadMessage'),
      }
    default:
      return {
        icon: 'bell',
        title: t('notifications.emptyAllTitle'),
        message: emptyMessage.value,
      }
  }
})

/** The way out of an empty list: the role's start page. */
const startPage = computed(() => ({
  to: defaultRouteForRole(role.value),
  label: role.value === 'ADMIN' ? t('notifications.goToDashboard') : t('notifications.goToHome'),
}))

function selectFilter(value: NotificationFilter): void {
  if (filter.value === value) return
  filter.value = value
  page.value = 0
  actionError.value = null
  resource.data.value = null
  void resource.reload()
}

/**
 * Shows `ids` as read (or unread again) at once, and the counts follow. The rows stay where they are, even on Unread:
 * a row read there loses its badge and button but leaves the list only when the list is read again, on another tab or
 * page (S3.18), so nothing jumps under the pointer and Undo has its row to come back to.
 */
function showStatus(ids: readonly number[], status: NotificationStatus): void {
  const data = resource.data.value
  if (data) {
    const selected = new Set(ids)
    resource.replace({ ...data, items: data.items.map((row) => (selected.has(row.id) ? { ...row, status } : row)) })
  }
  if (unread.value) {
    const delta = status === 'READ' ? -ids.length : ids.length
    unread.value = { ...unread.value, unreadCount: Math.max(0, unread.value.unreadCount + delta) }
  }
}

/** The list and shared unread count as the server has them now. */
async function refresh(): Promise<void> {
  await Promise.all([resource.reload(), reloadUnread()])
}

/**
 * Undo: the notifications are unread again, in place. Rows the list no longer shows (Unread read again after "Mark all
 * as read") come back with a fresh read of the list; a failed undo reads everything again to show the truth.
 */
async function markUnread(ids: readonly number[], version: number): Promise<void> {
  if (version !== getAuthSessionVersion()) return
  pendingUndos.value += 1
  actionError.value = null
  try {
    let missing = false
    for (let start = 0; start < ids.length; start += UNREAD_BATCH_SIZE) {
      const restored = await notificationsApi.markUnread(ids.slice(start, start + UNREAD_BATCH_SIZE))
      if (version !== getAuthSessionVersion()) return
      const shown = new Set(resource.data.value?.items.map((row) => row.id))
      missing ||= restored.some((id) => !shown.has(id))
      if (restored.length) showStatus(restored, 'UNREAD')
    }
    await (missing ? refresh() : reloadUnread())
  } catch (reason) {
    if (version !== getAuthSessionVersion()) return
    actionError.value = resolveApiError(reason, t('notifications.undoError'))
    await refresh()
  } finally {
    pendingUndos.value -= 1
  }
}

async function markRead(item: NotificationResponse, quiet = false): Promise<boolean> {
  if (markReadBusy(item.id)) return false
  const version = getAuthSessionVersion()
  markingIds.add(item.id)
  actionError.value = null
  try {
    await notificationsApi.markAsRead(item.id)
    if (version !== getAuthSessionVersion()) return false
    showStatus([item.id], 'READ')
    void reloadUnread()
    if (!quiet) {
      showToast(t('notifications.markedRead'), {
        label: t('common.undo'),
        run: () => void markUnread([item.id], version),
      })
    }
    return true
  } catch (reason) {
    if (version === getAuthSessionVersion())
      actionError.value = resolveApiError(reason, t('notifications.markReadError'))
    return false
  } finally {
    markingIds.delete(item.id)
  }
}

/** If the active control disappears, continue at Open or, for a notice without a destination, the row itself. */
async function markReadInRow(item: NotificationResponse, event: Event): Promise<void> {
  const button = event.currentTarget as HTMLButtonElement
  const row = button.closest('li')
  if (!(await markRead(item))) return
  await nextTick()
  if (row?.isConnected && (document.activeElement === document.body || document.activeElement === button)) {
    const next = row.querySelector<HTMLElement>('button') ?? row
    next.focus()
  }
}

async function markAllRead(): Promise<void> {
  if (markAllBusy.value) return
  const version = getAuthSessionVersion()
  markingAll.value = true
  hideToast()
  actionError.value = null
  try {
    const ids = await notificationsApi.markAllRead()
    if (version !== getAuthSessionVersion()) return
    showStatus(ids, 'READ')
    await refresh()
    if (version !== getAuthSessionVersion()) return
    showToast(
      t('notifications.markedAllRead'),
      ids.length ? { label: t('common.undo'), run: () => void markUnread(ids, version) } : undefined,
    )
  } catch (reason) {
    if (version === getAuthSessionVersion())
      actionError.value = resolveApiError(reason, t('notifications.markAllError'))
  } finally {
    markingAll.value = false
  }
  await nextTick()
  if (unreadTotal.value === 0 && document.activeElement === document.body) {
    toolbar.value?.querySelector<HTMLElement>('[aria-pressed="true"]')?.focus()
  }
}

/** Counts the Opens: one still reading its notification goes nowhere once another follows or the page is left. */
let navigationSequence = 0
onBeforeUnmount(() => {
  navigationSequence += 1
})

/** Opening a notification also reads it. An unknown or unsafe path gets no Open button. */
async function openNotification(item: NotificationResponse, target: string | null): Promise<void> {
  if (!target) return
  const sequence = ++navigationSequence
  const version = getAuthSessionVersion()
  if (item.status === 'UNREAD') await markRead(item, true)
  if (!pageActive.value || sequence !== navigationSequence || version !== getAuthSessionVersion()) return
  await router.push(target)
}
</script>
