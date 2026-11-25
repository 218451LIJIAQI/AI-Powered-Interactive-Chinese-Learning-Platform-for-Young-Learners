<template>
  <div class="page-stack">
    <h1 class="sr-only">{{ t('nav.support') }}</h1>

    <DataNotice :error="actionError" :error-title="t('adminSupport.noticeFailed')" />

    <div class="admin-list-detail">
      <section class="panel admin-list" aria-labelledby="admin-support-queue-title">
        <h2 id="admin-support-queue-title" class="sr-only">{{ t('adminSupport.queueLabel') }}</h2>
        <FilterChips
          :model-value="status"
          :label="t('adminSupport.queueLabel')"
          :options="filterOptions"
          @update:model-value="selectTab"
        />

        <StatePanel
          v-if="queue.loading.value"
          tone="loading"
          :title="t('adminSupport.listLoading')"
          :message="t('adminSupport.listLoadingHint')"
        />
        <StatePanel
          v-else-if="queue.error.value"
          tone="error"
          :title="t('adminSupport.listError')"
          :message="queue.error.value"
          @retry="queue.reload"
        />
        <template v-else-if="requests.length">
          <ul class="admin-select-list" :aria-label="t('adminSupport.requestsLabel')">
            <li v-for="request in requests" :key="request.id">
              <button
                class="select-card"
                type="button"
                :aria-current="request.id === selectedId ? 'true' : undefined"
                data-testid="admin-support-item"
                @click="selectRequest(request.id)"
              >
                <span class="admin-meta">#{{ request.number }}</span>
                <strong class="admin-select-title">{{ request.subject }}</strong>
                <span class="admin-meta">{{ requestMeta(request) }}</span>
              </button>
            </li>
          </ul>
          <PaginationBar
            :page="displayedPage"
            :total-pages="totalPages"
            :label="t('adminSupport.pagination')"
            icon-only
            @update:page="goToPage"
          />
        </template>
        <StatePanel
          v-else
          tone="empty"
          icon="inbox"
          :title="t(EMPTY_KEYS[status].title)"
          :message="t(EMPTY_KEYS[status].hint)"
        />
      </section>

      <div ref="detailRef" class="admin-detail-slot" tabindex="-1">
        <StatePanel
          v-if="selectedId === null && !queue.loading.value && !requests.length"
          class="panel"
          tone="empty"
          icon="support"
          spacious
          :title="t('adminSupport.chooseRequest')"
          :message="t('adminSupport.chooseRequestHint')"
        />
        <StatePanel
          v-else-if="detail.loading.value || selectedId === null"
          class="panel"
          tone="loading"
          spacious
          :title="t('adminSupport.detailLoading')"
          :message="t('adminSupport.detailLoadingHint')"
        />
        <StatePanel
          v-else-if="detail.error.value || !current"
          class="panel"
          tone="error"
          spacious
          :title="t('adminSupport.detailError')"
          :message="detail.error.value"
          @retry="detail.reload"
        />
        <article
          v-else
          class="panel admin-detail"
          aria-labelledby="admin-support-title"
          data-testid="admin-support-detail"
        >
          <header class="admin-detail-head">
            <div class="admin-detail-heading">
              <div class="page-title-row">
                <h2 id="admin-support-title" class="admin-detail-title">
                  {{
                    t('adminSupport.detailTitle', { number: current.request.number, subject: current.request.subject })
                  }}
                </h2>
                <StatusPill
                  :label="t(QUEUE_STATUS_KEYS[current.request.status])"
                  :tone="adminSupportTone(current.request.status)"
                />
              </div>
              <p class="admin-meta">{{ fromLine }}</p>
            </div>
            <RouterLink
              v-if="current.requesterUserId"
              class="btn btn-ghost btn-sm admin-link-action"
              :to="{ name: 'admin-users', query: { user: current.requesterUserId } }"
              :aria-label="t('adminSupport.viewUserLabel', { name: current.request.requesterName })"
            >
              {{ t('adminSupport.viewUser') }}
              <IconGlyph name="chevronRight" :size="16" />
            </RouterLink>
          </header>

          <ol class="admin-support-thread" :aria-label="t('adminSupport.threadLabel')">
            <li
              v-for="message in messages"
              :key="message.id"
              class="admin-support-message"
              :class="{ 'is-support': message.fromSupport }"
            >
              <p class="admin-meta">
                <strong>{{ authorLabel(message) }}</strong> · {{ formatDateTime(message.createdAt) }}
              </p>
              <p class="admin-support-message-body">{{ message.body }}</p>
              <button
                v-if="message.attachment"
                class="btn btn-secondary btn-sm admin-support-attachment"
                type="button"
                :aria-label="
                  t('adminSupport.attachmentLabel', {
                    name: message.attachment.fileName,
                    size: message.attachmentSize,
                  })
                "
                :disabled="openingAttachments.has(message.id)"
                :aria-busy="openingAttachments.has(message.id)"
                @click="openAttachment(message.id)"
              >
                <IconGlyph name="attach" :size="14" />
                <span>{{ message.attachment.fileName }} · {{ message.attachmentSize }}</span>
              </button>
            </li>
          </ol>

          <p v-if="statusNote" class="admin-meta">{{ statusNote }}</p>

          <!-- A solved request takes no reply; a reply goes to the requester by e-mail (A1.03, A1.04). -->
          <form v-if="current.request.status !== 'SOLVED'" class="admin-section" novalidate @submit.prevent="sendReply">
            <div class="field">
              <label class="field-label" for="admin-support-reply">{{ t('adminSupport.yourReply') }}</label>
              <textarea
                id="admin-support-reply"
                v-model="reply"
                class="textarea"
                rows="4"
                maxlength="4000"
                :disabled="pendingAction !== null"
                :placeholder="current.request.status === 'ANSWERED' ? t('adminSupport.replyPlaceholder') : undefined"
                data-testid="admin-support-reply"
              />
            </div>
            <div class="form-actions">
              <button
                class="btn btn-secondary"
                type="button"
                :disabled="pendingAction !== null"
                :aria-busy="pendingAction === 'solve'"
                data-testid="admin-support-solve"
                @click="markSolved"
              >
                {{ t('adminSupport.markSolved') }}
              </button>
              <button
                class="btn btn-primary"
                type="submit"
                :disabled="pendingAction !== null || !reply.trim()"
                :aria-busy="pendingAction === 'reply'"
                data-testid="admin-support-send"
              >
                {{ t('adminSupport.sendReply') }}
              </button>
            </div>
          </form>
          <div v-else class="form-actions">
            <button
              class="btn btn-secondary"
              type="button"
              :disabled="pendingAction !== null"
              :aria-busy="pendingAction === 'reopen'"
              data-testid="admin-support-reopen"
              @click="reopen(current.request.id, true)"
            >
              {{ t('adminSupport.reopen') }}
            </button>
          </div>
        </article>
      </div>
    </div>

    <AppToast :message="toast" :action="toastAction" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue'
import { RouterLink, useRoute, useRouter, type LocationQueryRaw } from 'vue-router'

import { adminSupportApi } from '@/api/ops'
import { adminSupportTone } from '@/components/admin/adminDisplay'
import { useSelectedRecord } from '@/components/admin/useSelectedRecord'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import FilterChips from '@/components/common/FilterChips.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { notifyProgressChanged } from '@/composables/useProgressRefresh'
import { useResource, useServerPage } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { adminSupportMessages } from '@/plugins/adminSupportMessages'
import { opsLabelMessages } from '@/plugins/opsLabelMessages'
import {
  SUPPORT_STATUSES,
  type SupportMessage,
  type SupportRequestDetail,
  type SupportRequestSummary,
  type SupportRequesterRole,
  type SupportStatus,
} from '@/types/ops'
import { apiErrorStatus, resolveApiError } from '@/utils/apiError'
import { formatDate, formatDateTime, formatFileSize, formatRelativeTime } from '@/utils/format'
import { openFileDownload } from '@/utils/download'
import { QUEUE_STATUS_KEYS, SUPPORT_TOPIC_KEYS } from '@/utils/opsLabels'
import { pageFromQuery, pageQuery, queryOption } from '@/utils/routeQuery'
import '@/styles/admin-shared.css'
import '@/styles/ops-admin.css'

const PAGE_SIZE = 20
const NO_BREAK_SPACE = '\u00a0'
const EMPTY_KEYS: Record<SupportStatus, { title: string; hint: string }> = {
  OPEN: { title: 'adminSupport.emptyOpenTitle', hint: 'adminSupport.emptyOpen' },
  ANSWERED: { title: 'adminSupport.emptyAnsweredTitle', hint: 'adminSupport.emptyAnswered' },
  SOLVED: { title: 'adminSupport.emptySolvedTitle', hint: 'adminSupport.emptySolved' },
}

const { t, locale } = useLocalMessages({ adminSupport: adminSupportMessages, opsLabels: opsLabelMessages })
const actionError = ref<string | null>(null)
const { toast, toastAction, showToast, hideToast } = useToast()
const detailRef = ref<HTMLElement | null>(null)

// The queue: its tab is kept in the address (`?status=ANSWERED`); Open, the default, is left out.

const route = useRoute()
const router = useRouter()
function queryStatus(): SupportStatus {
  return queryOption(route.query.status, SUPPORT_STATUSES) ?? 'OPEN'
}

const status = ref<SupportStatus>(queryStatus())
const page = ref(pageFromQuery(route.query.page))
const queue = useResource(() => adminSupportApi.queue(status.value, page.value, PAGE_SIZE))
const queuePage = computed(() => queue.data.value?.requests ?? null)

function statusQuery(): LocationQueryRaw {
  return {
    status: status.value === 'OPEN' ? undefined : status.value,
    page: pageQuery(page.value),
  }
}

function writeAddress(): void {
  void router.replace({ query: { ...route.query, ...statusQuery() } })
}

// A later page emptied by a reply or a solve (its last request moved to another tab) steps back to one with requests,
// instead of saying the whole tab is empty while its pagination is gone.
const {
  items: requests,
  displayedPage,
  totalPages,
  goToPage,
} = useServerPage({ data: queuePage, reload: queue.reload }, page, {
  stepBackFromEmpty: true,
  onPageChange: writeAddress,
})

const counts = shallowRef<Record<SupportStatus, number> | null>(null)
watch(
  queue.data,
  (loaded) => {
    if (loaded) counts.value = loaded.counts
  },
  { immediate: true },
)

const filterOptions = computed(() =>
  SUPPORT_STATUSES.map((value) => ({
    value,
    label: t(QUEUE_STATUS_KEYS[value]),
    count: counts.value?.[value],
    testId: `admin-support-tab-${value.toLowerCase()}`,
  })),
)

function showTab(next: SupportStatus, nextPage = 0): void {
  if (status.value === next && page.value === nextPage) return
  status.value = next
  page.value = nextPage
  queue.data.value = null
  void queue.reload()
}

function selectTab(next: SupportStatus): void {
  showTab(next, 0)
  writeAddress()
}

// The address can also change under the page (the sidebar's Support link opens it with no tab): the queue follows it.
watch([() => route.query.status, () => route.query.page], () => {
  if (route.name === 'admin-support') showTab(queryStatus(), pageFromQuery(route.query.page))
})

/** "learner" for a learner's request; null for a role the platform no longer has (requests sent before 2026-11). */
function requesterRoleLabel(role: SupportRequesterRole): string | null {
  return role === 'STUDENT' ? t('roles.STUDENT').toLowerCase() : null
}

function requestMeta(request: SupportRequestSummary): string {
  // "2 hr. ago" never breaks, and each "·" stays with the part before it, so no line starts with a separator.
  const time = formatRelativeTime(request.updatedAt).replaceAll(' ', NO_BREAK_SPACE)
  const role = requesterRoleLabel(request.requesterRole)
  let meta: string
  if (request.requesterRole === 'VISITOR') meta = t('adminSupport.itemVisitor', { email: request.requesterEmail, time })
  else if (role) meta = t('adminSupport.itemMeta', { name: request.requesterName, role, time })
  else meta = t('adminSupport.itemAccount', { name: request.requesterName, time })
  return meta.replaceAll(' · ', `${NO_BREAK_SPACE}· `)
}

// The selected request (`?request=<id>`).

/** The reply being written; Send reply waits until there is one (Mark as solved needs none). */
const reply = ref('')
type SupportAction = 'reply' | 'solve' | 'reopen'
const pendingActions = reactive(new Map<number, SupportAction>())
const isUnmounted = useIsUnmounted()
let selectionVersion = 0

const { selectedId, detail, select } = useSelectedRecord(
  'request',
  adminSupportApi.get,
  statusQuery,
  () => requests.value[0]?.id,
)
const current = detail.data
const pendingAction = computed<SupportAction | null>(() =>
  selectedId.value === null ? null : (pendingActions.get(selectedId.value) ?? null),
)
const messages = computed(() =>
  (current.value?.messages ?? []).map((message) => ({
    ...message,
    attachmentSize: message.attachment ? formatFileSize(message.attachment.fileSize, locale.value) : '',
  })),
)

let preserveErrorOnSelectionChange = false
watch(selectedId, () => {
  selectionVersion += 1
  if (preserveErrorOnSelectionChange) preserveErrorOnSelectionChange = false
  else actionError.value = null
  reply.value = ''
})
onBeforeUnmount(() => {
  selectionVersion += 1
})

async function selectRequest(id: number): Promise<void> {
  await select(id)
  await nextTick()
  const panel = detailRef.value
  if (!panel) return
  const { top } = panel.getBoundingClientRect()
  panel.focus({ preventScroll: top >= 0 && top < window.innerHeight })
}

const fromLine = computed(() => {
  const request = current.value?.request
  if (!request) return ''
  const shared = {
    email: request.requesterEmail,
    topic: t(SUPPORT_TOPIC_KEYS[request.topic]),
    date: formatDateTime(request.createdAt),
  }
  if (request.requesterRole === 'VISITOR') return t('adminSupport.fromVisitor', shared)
  const role = requesterRoleLabel(request.requesterRole)
  return role
    ? t('adminSupport.from', { ...shared, name: request.requesterName, role })
    : t('adminSupport.fromAccount', { ...shared, name: request.requesterName })
})

const statusNote = computed(() => {
  const request = current.value?.request
  if (!request) return ''
  if (request.status === 'ANSWERED' && request.closesAt) {
    return t('adminSupport.closesOn', { date: formatDate(request.closesAt) })
  }
  if (request.status === 'SOLVED' && request.solvedAt) {
    const date = formatDate(request.solvedAt)
    if (request.closedBy === 'AUTO') return t('adminSupport.closedAuto', { date })
    if (request.closedBy === 'REQUESTER') return t('adminSupport.closedRequester', { date })
    return t('adminSupport.closedSupport', { date })
  }
  return ''
})

/** Support's replies name who wrote them; only a visitor's message (or a deleted admin) has no author. */
function authorLabel(message: SupportMessage): string {
  if (message.fromSupport) {
    return message.authorName
      ? t('adminSupport.supportAuthor', { name: message.authorName })
      : t('adminSupport.supportFallback')
  }
  return message.authorName ?? t('adminSupport.visitorAuthor')
}

// Reply, solve, attachments.

/**
 * Runs a change of the request; its answer replaces the detail, and the queue's tabs and counts and the sidebar badge
 * catch up. `announce` shows its toast once it worked (with its Undo, if any).
 */
async function changeRequest(
  targetId: number,
  action: SupportAction,
  change: () => Promise<SupportRequestDetail>,
  failureKey: string,
  announce?: () => void,
): Promise<boolean> {
  if (pendingActions.has(targetId)) return false
  hideToast()
  const version = selectionVersion
  pendingActions.set(targetId, action)
  if (selectedId.value === targetId || action === 'reopen') actionError.value = null
  try {
    const updated = await change()
    // The sidebar badge counts outlive this page: they catch up even when the admin has already moved on.
    notifyProgressChanged()
    if (isUnmounted()) return false
    // Update the open pane and deliver feedback whenever the modified request is currently displayed (including after switching back).
    if (selectedId.value === updated.request.id) {
      detail.replace(updated)
      announce?.()
    }
    void queue.reload()
    return version === selectionVersion
  } catch (failure) {
    const httpStatus = apiErrorStatus(failure)
    const stateDrift = httpStatus === 400 || httpStatus === 404 || httpStatus === 409
    if (stateDrift) notifyProgressChanged()
    if (isUnmounted()) return false
    if (selectedId.value === targetId || (action === 'reopen' && version === selectionVersion)) {
      actionError.value = resolveApiError(failure, t(failureKey))
    }
    if (stateDrift) {
      void queue.reload()
      if (selectedId.value === targetId) {
        if (httpStatus === 404) {
          preserveErrorOnSelectionChange = true
          void select(null)
        } else {
          void detail.reload()
        }
      }
    }
    return false
  } finally {
    pendingActions.delete(targetId)
  }
}

async function sendReply(): Promise<void> {
  const request = current.value?.request
  const body = reply.value.trim()
  if (!request || !body) return
  const sent = await changeRequest(
    request.id,
    'reply',
    () => adminSupportApi.reply(request.id, body),
    'adminSupport.replyFailed',
    () =>
      showToast(
        request.requesterRole === 'VISITOR'
          ? t('adminSupport.replySentVisitor', { email: request.requesterEmail, number: request.number })
          : t('adminSupport.replySent', { name: request.requesterName, number: request.number }),
      ),
  )
  if (sent) reply.value = ''
}

/**
 * Marks the request solved at once (A1.07, A1.08): it leaves the Open or Answered list, the counts change, and the
 * reply being written is dropped. The toast's Undo reopens it.
 */
async function markSolved(): Promise<void> {
  const request = current.value?.request
  if (!request) return
  const solved = await changeRequest(
    request.id,
    'solve',
    () => adminSupportApi.solve(request.id),
    'adminSupport.solveFailed',
    () =>
      showToast(t('adminSupport.solvedToast', { number: request.number }), {
        label: t('common.undo'),
        run: () => void reopen(request.id),
      }),
  )
  if (solved) reply.value = ''
}

/** Reopens a solved request (either from Undo or from the Solved detail footer): back to Answered or Open. */
async function reopen(id: number, announce = false): Promise<void> {
  const number = current.value?.request.id === id ? current.value.request.number : null
  await changeRequest(
    id,
    'reopen',
    () => adminSupportApi.reopen(id),
    'adminSupport.reopenFailed',
    announce && number !== null ? () => showToast(t('adminSupport.reopenedToast', { number })) : undefined,
  )
}

const openingAttachments = reactive(new Set<number>())

async function openAttachment(messageId: number): Promise<void> {
  if (openingAttachments.has(messageId)) return
  openingAttachments.add(messageId)
  const version = selectionVersion
  actionError.value = null
  try {
    await openFileDownload(() => adminSupportApi.attachment(messageId), 'screenshot')
  } catch (failure) {
    if (version === selectionVersion) actionError.value = resolveApiError(failure, t('adminSupport.attachmentFailed'))
  } finally {
    openingAttachments.delete(messageId)
  }
}
</script>
