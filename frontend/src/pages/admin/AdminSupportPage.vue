<template>
  <div class="page-stack">
    <PageHeaderBlock :title="t('adminSupport.pageTitle')" hide-title />

    <DataNotice :error="actionError" :error-title="t('adminSupport.noticeFailed')" />

    <div class="list-detail admin-list-detail">
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
          v-else-if="queue.error.value && !requests.length"
          tone="error"
          icon="alert"
          :title="t('adminSupport.listError')"
          :message="queue.error.value"
        >
          <template #actions>
            <button class="btn btn-primary" type="button" @click="queue.reload">{{ t('adminSupport.retry') }}</button>
          </template>
        </StatePanel>
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
            v-if="totalPages > 1"
            :current-page="page + 1"
            :total-pages="totalPages"
            :label="t('adminSupport.pagination')"
            icon-only
            @update:current-page="goToPage($event - 1)"
          />
        </template>
        <StatePanel v-else tone="empty" icon="inbox" :title="emptyState.title" :message="emptyState.message" />
      </section>

      <div ref="detailRef" class="admin-detail-slot" tabindex="-1">
        <div v-if="selectedId === null && !queue.loading.value" class="panel">
          <StatePanel
            tone="empty"
            icon="support"
            spacious
            :title="t('adminSupport.chooseRequest')"
            :message="t('adminSupport.chooseRequestHint')"
          />
        </div>
        <StatePanel
          v-else-if="detail.loading.value || selectedId === null"
          tone="loading"
          spacious
          :title="t('adminSupport.detailLoading')"
          :message="t('adminSupport.detailLoadingHint')"
        />
        <StatePanel
          v-else-if="detail.error.value || !current"
          tone="error"
          icon="alert"
          spacious
          :title="t('adminSupport.detailError')"
          :message="detail.error.value ?? ''"
        >
          <template #actions>
            <button class="btn btn-primary" type="button" @click="detail.reload">{{ t('adminSupport.retry') }}</button>
          </template>
        </StatePanel>
        <article
          v-else
          class="panel admin-detail"
          aria-labelledby="admin-support-title"
          data-testid="admin-support-detail"
        >
          <header class="admin-detail-head">
            <div class="admin-detail-heading">
              <div class="admin-support-title-row">
                <h2 id="admin-support-title" class="admin-detail-title">
                  {{
                    t('adminSupport.detailTitle', { number: current.request.number, subject: current.request.subject })
                  }}
                </h2>
                <StatusPill
                  :label="t(queueStatusKey(current.request.status))"
                  :tone="adminSupportTone(current.request.status)"
                />
              </div>
              <p class="admin-meta">{{ fromLine }}</p>
            </div>
            <RouterLink
              v-if="current.requesterUserId"
              class="btn btn-ghost btn-sm admin-link-action"
              :to="{ name: 'admin-users', query: { user: current.requesterUserId } }"
              :aria-label="t('adminSupport.viewUserLabel', { name: requesterName })"
            >
              {{ t('adminSupport.viewUser') }}
              <IconGlyph name="chevronRight" :size="16" />
            </RouterLink>
          </header>

          <ol class="admin-support-thread" :aria-label="t('adminSupport.threadLabel')">
            <li
              v-for="message in current.messages"
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
                    size: formatFileSize(message.attachment.fileSize),
                  })
                "
                :disabled="openingAttachment === message.id"
                :aria-busy="openingAttachment === message.id"
                @click="openAttachment(message.id)"
              >
                <IconGlyph name="attach" :size="14" />
                <span>{{ message.attachment.fileName }} · {{ formatFileSize(message.attachment.fileSize) }}</span>
              </button>
            </li>
          </ol>

          <p v-if="statusNote" class="admin-meta">{{ statusNote }}</p>

          <form v-if="current.request.status !== 'SOLVED'" class="admin-section" novalidate @submit.prevent="sendReply">
            <div class="field">
              <label class="field-label" for="admin-support-reply">{{ t('adminSupport.yourReply') }}</label>
              <textarea
                id="admin-support-reply"
                v-model="reply"
                class="textarea"
                rows="4"
                maxlength="4000"
                :placeholder="current.request.status === 'ANSWERED' ? t('adminSupport.replyPlaceholder') : undefined"
                :aria-invalid="Boolean(replyError)"
                :aria-describedby="replyError ? 'admin-support-reply-error' : undefined"
                data-testid="admin-support-reply"
              />
              <p v-if="replyError" id="admin-support-reply-error" class="field-error" role="alert">{{ replyError }}</p>
            </div>
            <div class="form-actions">
              <button
                class="btn btn-secondary"
                type="button"
                :disabled="busy"
                data-testid="admin-support-solve"
                @click="markSolved"
              >
                {{ t('adminSupport.markSolved') }}
              </button>
              <button
                class="btn btn-primary"
                type="submit"
                :disabled="busy"
                :aria-busy="busy"
                data-testid="admin-support-send"
              >
                {{ t('adminSupport.sendReply') }}
              </button>
            </div>
          </form>
        </article>
      </div>
    </div>

    <AppToast :message="toast" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { adminSupportApi } from '@/api/ops'
import { adminSupportTone } from '@/components/admin/adminDisplay'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import FilterChips from '@/components/common/FilterChips.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { notifyProgressChanged } from '@/composables/useProgressRefresh'
import { useResource, useServerPage } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { adminSupportMessages } from '@/plugins/adminSupportMessages'
import { opsLabelMessages } from '@/plugins/opsLabelMessages'
import type { SupportMessage, SupportRequestDetail, SupportRequestSummary, SupportStatus } from '@/types/ops'
import { resolveApiError } from '@/utils/apiError'
import { formatDate, formatDateTime, formatFileSize, formatRelativeTime } from '@/utils/format'
import { openFileDownload } from '@/utils/opsFiles'
import { queueStatusKey, roleKey, supportTopicKey } from '@/utils/opsLabels'
import '@/styles/ops-admin.css'

const PAGE_SIZE = 20
const STATUSES: readonly SupportStatus[] = ['OPEN', 'ANSWERED', 'SOLVED']
const TAB_KEYS: Record<SupportStatus, string> = {
  OPEN: 'adminSupport.tabOpen',
  ANSWERED: 'adminSupport.tabAnswered',
  SOLVED: 'adminSupport.tabSolved',
}
const EMPTY_KEYS: Record<SupportStatus, readonly [string, string]> = {
  OPEN: ['adminSupport.emptyOpenTitle', 'adminSupport.emptyOpen'],
  ANSWERED: ['adminSupport.emptyAnsweredTitle', 'adminSupport.emptyAnswered'],
  SOLVED: ['adminSupport.emptySolvedTitle', 'adminSupport.emptySolved'],
}

const route = useRoute()
const router = useRouter()
const { t } = useLocalMessages({ adminSupport: adminSupportMessages, opsLabels: opsLabelMessages })
const { actionError, clearFeedback, showError } = useActionFeedback()
const { toast, showToast } = useToast()
const detailRef = ref<HTMLElement | null>(null)

// ----- queue -----

const status = ref<SupportStatus>('OPEN')
const page = ref(0)
const queue = useResource(() => adminSupportApi.queue(status.value, page.value, PAGE_SIZE))
const { items: requests, totalPages, goToPage } = useServerPage(queue, page, { select: (data) => data.requests })

const filterOptions = computed(() =>
  STATUSES.map((value) => ({
    value,
    label: t(TAB_KEYS[value]),
    count: queue.data.value?.counts[value] ?? 0,
    testId: `admin-support-tab-${value.toLowerCase()}`,
  })),
)
const emptyState = computed(() => ({ title: t(EMPTY_KEYS[status.value][0]), message: t(EMPTY_KEYS[status.value][1]) }))

function selectTab(next: SupportStatus): void {
  if (status.value === next) return
  status.value = next
  page.value = 0
  void queue.reload()
}

function requestMeta(request: SupportRequestSummary): string {
  // "2 hr. ago" never breaks, and each "·" stays with the part before it, so no line starts with a separator.
  const time = formatRelativeTime(request.updatedAt).replaceAll(' ', ' ')
  const meta =
    request.requesterRole === 'VISITOR'
      ? t('adminSupport.itemVisitor', { email: request.requesterEmail, time })
      : t('adminSupport.itemMeta', {
          name: request.requesterName ?? t('adminSupport.deletedAuthor'),
          role: t(roleKey(request.requesterRole)).toLowerCase(),
          time,
        })
  return meta.replaceAll(' · ', ' · ')
}

// ----- selected request (`?request=<id>`) -----

const reply = ref('')
const replyError = ref<string | null>(null)

const selectedId = computed<number | null>(() => {
  const id = Number(route.query.request)
  return Number.isInteger(id) && id > 0 ? id : null
})

const detail = useResource<SupportRequestDetail | null>(
  () => (selectedId.value ? adminSupportApi.get(selectedId.value) : Promise.resolve(null)),
  false,
)
const current = computed(() => detail.data.value)

watch(
  selectedId,
  (id, previous) => {
    if (id !== previous) {
      detail.data.value = null
      reply.value = ''
      replyError.value = null
    }
    void detail.reload()
  },
  { immediate: true },
)

// Without a choice in the URL, the oldest request in the queue opens beside it.
watch(requests, (list) => {
  const oldest = list[0]
  if (selectedId.value === null && oldest) {
    void router.replace({ query: { ...route.query, request: String(oldest.id) } })
  }
})

async function selectRequest(id: number): Promise<void> {
  await router.replace({ query: { ...route.query, request: String(id) } })
  await nextTick()
  detailRef.value?.focus({ preventScroll: true })
}

const isVisitor = computed(() => current.value?.request.requesterRole === 'VISITOR')
const requesterName = computed(() => current.value?.request.requesterName ?? t('adminSupport.deletedAuthor'))

const fromLine = computed(() => {
  const request = current.value?.request
  if (!request) return ''
  const shared = {
    email: request.requesterEmail,
    topic: t(supportTopicKey(request.topic)),
    date: formatDateTime(request.createdAt),
  }
  if (request.requesterRole === 'VISITOR') return t('adminSupport.fromVisitor', shared)
  return t('adminSupport.from', {
    ...shared,
    name: requesterName.value,
    role: t(roleKey(request.requesterRole)).toLowerCase(),
  })
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

function authorLabel(message: SupportMessage): string {
  if (message.fromSupport) {
    return message.authorName
      ? t('adminSupport.supportAuthor', { name: message.authorName })
      : t('adminSupport.supportTeam')
  }
  if (isVisitor.value) return t('adminSupport.visitorAuthor')
  return message.authorName ?? t('adminSupport.deletedAuthor')
}

// ----- reply, solve, attachments -----

const busy = ref(false)

async function sendReply(): Promise<void> {
  const request = current.value?.request
  if (!request) return
  const body = reply.value.trim()
  replyError.value = body ? null : t('adminSupport.replyMissing')
  if (!body) return
  busy.value = true
  clearFeedback()
  try {
    detail.data.value = await adminSupportApi.reply(request.id, body)
    reply.value = ''
    showToast(
      isVisitor.value
        ? t('adminSupport.replySentVisitor', { email: request.requesterEmail, number: request.number })
        : t('adminSupport.replySent', { name: requesterName.value, number: request.number }),
    )
    notifyProgressChanged()
    void queue.reload()
  } catch (failure) {
    showError(resolveApiError(failure, t('adminSupport.replyFailed')))
  } finally {
    busy.value = false
  }
}

async function markSolved(): Promise<void> {
  const request = current.value?.request
  if (!request) return
  busy.value = true
  clearFeedback()
  try {
    detail.data.value = await adminSupportApi.solve(request.id)
    showToast(
      isVisitor.value
        ? t('adminSupport.solvedToastVisitor', { number: request.number })
        : t('adminSupport.solvedToast', { number: request.number, name: requesterName.value }),
    )
    notifyProgressChanged()
    void queue.reload()
  } catch (failure) {
    showError(resolveApiError(failure, t('adminSupport.solveFailed')))
  } finally {
    busy.value = false
  }
}

const openingAttachment = ref<number | null>(null)

async function openAttachment(messageId: number): Promise<void> {
  openingAttachment.value = messageId
  try {
    await openFileDownload(() => adminSupportApi.attachment(messageId), 'screenshot')
  } catch (failure) {
    showError(resolveApiError(failure, t('adminSupport.attachmentFailed')))
  } finally {
    openingAttachment.value = null
  }
}
</script>
