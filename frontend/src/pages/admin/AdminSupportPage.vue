<template>
  <div class="admin-support page-stack">
    <PageHeaderBlock :title="t('adminSupport.pageTitle')" :description="t('adminSupport.lede')" />

    <DataNotice
      :error="actionError"
      :success="actionSuccess"
      :success-title="t('adminSupport.noticeDone')"
      :error-title="t('adminSupport.noticeFailed')"
    />

    <div class="admin-inbox">
      <section class="admin-sheet admin-queue" aria-labelledby="admin-support-queue-title">
        <div class="admin-sheet-head">
          <h2 id="admin-support-queue-title" class="admin-sheet-title">{{ t('adminSupport.queueLabel') }}</h2>
          <SegmentedControl
            :model-value="status"
            :label="t('adminSupport.queueLabel')"
            :options="
              tabs.map((tab) => ({
                value: tab.status,
                label: tab.label,
                testId: `admin-support-tab-${tab.status.toLowerCase()}`,
              }))
            "
            @update:model-value="selectTab"
          />
        </div>

        <div v-if="queue.loading.value" class="admin-sheet-body">
          <StatePanel
            tone="loading"
            :title="t('adminSupport.listLoading')"
            :message="t('adminSupport.listLoadingHint')"
          />
        </div>
        <div v-else-if="queue.error.value && !requests.length" class="admin-sheet-body">
          <StatePanel tone="error" icon="alert" :title="t('adminSupport.listError')" :message="queue.error.value">
            <template #actions>
              <button class="btn btn-primary" type="button" @click="queue.reload">{{ t('adminSupport.retry') }}</button>
            </template>
          </StatePanel>
        </div>
        <template v-else-if="requests.length">
          <ul class="admin-queue-list" :aria-label="t('adminSupport.requestsLabel')">
            <li v-for="request in requests" :key="request.id">
              <button
                class="admin-queue-item"
                type="button"
                :aria-current="request.id === selectedId ? 'true' : undefined"
                data-testid="admin-support-item"
                @click="selectRequest(request.id)"
              >
                <span class="admin-support-number">#{{ request.number }}</span>
                <span class="admin-queue-item-copy">
                  <strong>{{ request.subject }}</strong>
                  <span class="admin-queue-item-meta">{{ requestMeta(request) }}</span>
                </span>
              </button>
            </li>
          </ul>
          <div v-if="totalPages > 1" class="admin-sheet-foot">
            <PaginationBar
              :current-page="page + 1"
              :total-pages="totalPages"
              :label="t('adminSupport.pagination')"
              icon-only
              @update:current-page="goToPage($event - 1)"
            />
          </div>
        </template>
        <div v-else class="admin-sheet-body">
          <StatePanel tone="empty" icon="inbox" :title="emptyState.title" :message="emptyState.message" />
        </div>
      </section>

      <div ref="detailRef" class="admin-reader-slot" tabindex="-1">
        <StatePanel
          v-if="selectedId === null"
          tone="empty"
          icon="support"
          spacious
          :title="t('adminSupport.chooseRequest')"
          :message="t('adminSupport.chooseRequestHint')"
        />
        <StatePanel
          v-else-if="detail.loading.value"
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
          class="panel admin-reader"
          aria-labelledby="admin-support-title"
          data-testid="admin-support-detail"
        >
          <header class="admin-reader-head">
            <div class="admin-reader-heading">
              <h2 id="admin-support-title">
                {{
                  t('adminSupport.detailTitle', { number: current.request.number, subject: current.request.subject })
                }}
              </h2>
              <p>{{ fromLine }}</p>
              <div class="admin-reader-tags">
                <StatusPill
                  :label="t(queueStatusKey(current.request.status))"
                  :tone="supportStatusTone(current.request.status)"
                />
              </div>
            </div>
            <RouterLink
              v-if="current.requesterUserId"
              class="btn btn-link"
              :to="{ name: 'admin-users', query: { user: current.requesterUserId } }"
              :aria-label="t('adminSupport.viewUserLabel', { name: requesterName })"
            >
              {{ t('adminSupport.viewUser') }}
              <IconGlyph name="chevronRight" :size="14" />
            </RouterLink>
          </header>

          <ol class="admin-support-thread" :aria-label="t('adminSupport.threadLabel')">
            <li
              v-for="message in current.messages"
              :key="message.id"
              class="admin-support-message"
              :class="{ 'is-support': message.fromSupport }"
            >
              <p class="admin-support-message-meta">
                <strong>{{ authorLabel(message) }}</strong>
                <span>{{ formatDateTime(message.createdAt) }}</span>
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
                {{ message.attachment.fileName }} · {{ formatFileSize(message.attachment.fileSize) }}
              </button>
            </li>
          </ol>

          <p v-if="statusNote" class="admin-support-status-note">
            <IconGlyph name="clock" :size="16" />
            {{ statusNote }}
          </p>

          <form
            v-if="current.request.status !== 'SOLVED'"
            class="admin-support-reply"
            novalidate
            @submit.prevent="sendReply"
          >
            <div class="field">
              <label class="field-label" for="admin-support-reply">{{ t('adminSupport.yourReply') }}</label>
              <textarea
                id="admin-support-reply"
                v-model="reply"
                class="textarea"
                rows="4"
                maxlength="4000"
                :aria-invalid="Boolean(replyError)"
                :aria-describedby="replyError ? 'admin-support-reply-error' : 'admin-support-reply-hint'"
                data-testid="admin-support-reply"
              />
              <p id="admin-support-reply-hint" class="field-hint" data-testid="admin-support-reply-hint">
                {{ replyHint }}
              </p>
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
                <IconGlyph name="check" :size="16" />
                {{ t('adminSupport.markSolved') }}
              </button>
              <button
                class="btn btn-primary"
                type="submit"
                :disabled="busy"
                :aria-busy="busy"
                data-testid="admin-support-send"
              >
                <IconGlyph name="send" :size="16" />
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
import SegmentedControl from '@/components/common/SegmentedControl.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import AppToast from '@/components/common/AppToast.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource, useServerPage } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { adminSupportMessages } from '@/plugins/adminSupportMessages'
import { opsLabelMessages } from '@/plugins/opsLabelMessages'
import type { SupportMessage, SupportRequestDetail, SupportRequestSummary, SupportStatus } from '@/types/ops'
import { resolveApiError } from '@/utils/apiError'
import { formatDate, formatDateTime, formatFileSize, formatRelativeTime } from '@/utils/format'
import { openFileDownload } from '@/utils/opsFiles'
import { queueStatusKey, roleKey, supportStatusTone, supportTopicKey } from '@/utils/opsLabels'
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
const { actionError, actionSuccess, clearFeedback, showError, showSuccess } = useActionFeedback()
const { toast, showToast } = useToast()
const detailRef = ref<HTMLElement | null>(null)

// ----- queue -----

const status = ref<SupportStatus>('OPEN')
const page = ref(0)
const queue = useResource(() => adminSupportApi.queue(status.value, page.value, PAGE_SIZE))
const { items: requests, totalPages, goToPage } = useServerPage(queue, page, { select: (data) => data.requests })

const tabs = computed(() =>
  STATUSES.map((value) => ({
    status: value,
    label: t(TAB_KEYS[value], { count: queue.data.value?.counts[value] ?? 0 }),
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
  const time = formatRelativeTime(request.updatedAt).replaceAll(' ', '\u00a0')
  const meta =
    request.requesterRole === 'VISITOR'
      ? t('adminSupport.itemVisitor', { email: request.requesterEmail, time })
      : t('adminSupport.itemMeta', {
          name: request.requesterName ?? t('adminSupport.deletedAuthor'),
          role: t(roleKey(request.requesterRole)).toLowerCase(),
          time,
        })
  return meta.replaceAll(' · ', '\u00a0· ')
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

const replyHint = computed(() => {
  const request = current.value?.request
  if (!request) return ''
  if (request.requesterRole === 'VISITOR') return t('adminSupport.replyHintVisitor', { email: request.requesterEmail })
  return request.requesterRole === 'TEACHER' ? t('adminSupport.replyHintTeacher') : t('adminSupport.replyHintStudent')
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
    showSuccess(
      isVisitor.value
        ? t('adminSupport.replySentVisitor', { email: request.requesterEmail, number: request.number })
        : t('adminSupport.replySent', { name: requesterName.value, number: request.number }),
    )
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
