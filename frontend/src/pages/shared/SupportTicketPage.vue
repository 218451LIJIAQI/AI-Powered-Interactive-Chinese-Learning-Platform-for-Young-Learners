<template>
  <div class="page-stack support-ticket-page">
    <AppToast :message="toast" />

    <template v-if="detail">
      <!-- The status sits beside the H1, a slot PageHeaderBlock does not have, so the header is written out here. -->
      <header class="page-header">
        <div class="page-header-before">
          <AppBreadcrumb
            :items="[
              { label: t('support.pageTitle'), to: { name: supportRouteName } },
              { label: `#${detail.request.number}` },
            ]"
          />
        </div>
        <div class="page-title">
          <div class="page-title-copy">
            <div class="support-ticket-heading">
              <h1>{{ detail.request.subject }}</h1>
              <StatusPill :label="status.label" :tone="status.tone" />
            </div>
            <p>
              {{
                t('support.ticketMeta', {
                  number: detail.request.number,
                  topic: t(supportTopicKey(detail.request.topic)),
                  date: formatDateTime(detail.request.createdAt),
                })
              }}
            </p>
          </div>
        </div>
      </header>

      <DataNotice :error="actionError" />

      <div class="support-ticket-layout">
        <section class="panel support-conversation" :aria-label="t('support.thread')">
          <ol class="support-thread" data-testid="support-thread">
            <li
              v-for="message in detail.messages"
              :key="message.id"
              class="support-message"
              :class="message.fromSupport ? 'is-support' : 'is-requester'"
            >
              <span v-if="message.fromSupport" class="support-message-avatar" aria-hidden="true">
                <IconGlyph name="help" :size="18" />
              </span>
              <PortraitAvatar v-else :name="message.authorName" :size="36" />
              <div class="support-message-body">
                <p class="support-message-head">
                  <strong>{{
                    message.fromSupport ? (message.authorName ?? t('support.supportAuthor')) : t('support.you')
                  }}</strong>
                  <span>
                    <template v-if="message.fromSupport">{{ t('support.supportAuthor') }} · </template>
                    <template v-else-if="message.authorName">{{ message.authorName }} · </template>
                    <time :datetime="message.createdAt">{{ formatDateTime(message.createdAt) }}</time>
                  </span>
                </p>
                <p class="support-message-text">{{ message.body }}</p>
                <button
                  v-if="message.attachment"
                  class="btn btn-secondary btn-sm support-message-attachment"
                  type="button"
                  @click="downloadAttachment(message.id, message.attachment.fileName)"
                >
                  <IconGlyph name="attach" :size="14" />
                  {{
                    t('support.attachment', {
                      name: message.attachment.fileName,
                      size: formatFileSize(message.attachment.fileSize),
                    })
                  }}
                </button>
              </div>
            </li>
          </ol>

          <form v-if="!solved" class="support-reply" novalidate @submit.prevent="sendReply">
            <label class="sr-only" for="support-reply">{{ t('support.replyLabel') }}</label>
            <textarea
              id="support-reply"
              v-model="reply"
              class="textarea"
              rows="3"
              maxlength="4000"
              :placeholder="t('support.replyPlaceholder')"
              :aria-invalid="Boolean(replyError)"
              :aria-describedby="replyError ? 'support-reply-error' : undefined"
            />
            <p v-if="replyError" id="support-reply-error" class="field-error" role="alert">{{ replyError }}</p>
            <div class="form-actions">
              <button
                class="btn btn-primary"
                type="submit"
                :disabled="replying"
                :aria-busy="replying"
                data-testid="support-send-reply"
              >
                {{ t('support.sendReply') }}
              </button>
            </div>
          </form>
        </section>

        <aside
          v-if="solved"
          class="support-side-card support-status"
          aria-labelledby="support-solved-title"
          data-testid="support-solved"
        >
          <h2 id="support-solved-title" class="card-title">{{ t('support.solvedTitle') }}</h2>
          <p>
            {{ t('support.solvedOnDate', { date: formatShortDate(detail.request.solvedAt) }) }}
            <template v-if="detail.request.closedBy === 'AUTO'"> {{ t('support.solvedAutomatically') }}</template>
          </p>
          <p>{{ t('support.solvedAgain') }}</p>
          <RouterLink class="btn btn-secondary" :to="{ name: supportRouteName }">{{
            t('support.newRequest')
          }}</RouterLink>
        </aside>
        <aside v-else class="support-side-card support-status" aria-labelledby="support-sorted-title">
          <h2 id="support-sorted-title" class="card-title">{{ t('support.allSorted') }}</h2>
          <p>
            {{
              detail.request.status === 'ANSWERED' && detail.request.closesAt
                ? t('support.allSortedAnswered', { date: formatShortDate(detail.request.closesAt) })
                : t('support.allSortedOpen')
            }}
          </p>
          <button
            class="btn btn-secondary"
            type="button"
            :disabled="solving"
            :aria-busy="solving"
            data-testid="support-solve"
            @click="solve"
          >
            {{ t('support.markSolved') }}
          </button>
        </aside>
      </div>
    </template>

    <template v-else>
      <h1 class="sr-only">{{ t('support.pageTitle') }}</h1>
      <StatePanel
        v-if="resource.loading.value"
        tone="loading"
        spacious
        :title="t('support.ticketLoadingTitle')"
        :message="t('support.ticketLoadingHint')"
      />
      <StatePanel v-else tone="error" icon="alert" spacious :title="t('support.ticketErrorTitle')" :message="loadError">
        <template #actions>
          <RouterLink class="btn btn-primary" :to="{ name: supportRouteName }">{{
            t('support.backToSupport')
          }}</RouterLink>
        </template>
      </StatePanel>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { supportApi } from '@/api/ops'
import AppBreadcrumb from '@/components/common/AppBreadcrumb.vue'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill, { type PillTone } from '@/components/common/StatusPill.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { opsLabelMessages } from '@/plugins/opsLabelMessages'
import { supportMessages } from '@/plugins/supportMessages'
import { resolveApiError } from '@/utils/apiError'
import { saveBlobDownload } from '@/utils/download'
import { formatDateTime, formatFileSize, formatShortDate } from '@/utils/format'
import { requestStatusKey, requestStatusTone, supportTopicKey } from '@/utils/opsLabels'
import '@/styles/ops-account.css'

const { t } = useLocalMessages({ support: supportMessages, opsLabels: opsLabelMessages })
const route = useRoute()
const router = useRouter()
const { toast, showToast } = useToast()

const ticketId = computed(() => Number(route.params.ticketId))
const supportRouteName = computed(() =>
  String(route.name).startsWith('teacher') ? 'teacher-support' : 'student-support',
)

const resource = useResource(() => supportApi.get(ticketId.value))
const detail = resource.data
watch(ticketId, () => {
  detail.value = null
  void resource.reload()
})

const loadError = computed(() => resource.error.value ?? t('support.ticketErrorTitle'))
const solved = computed(() => detail.value?.request.status === 'SOLVED')

/** After the requester answers, the request waits for support again: nothing is expected of them. */
const status = computed<{ label: string; tone: PillTone }>(() => {
  const request = detail.value?.request
  if (!request) return { label: '', tone: 'neutral' }
  if (request.status === 'OPEN' && request.lastSupportReplyAt) {
    return { label: t('support.waitingForSupport'), tone: 'info' }
  }
  return { label: t(requestStatusKey(request.status)), tone: requestStatusTone(request.status) }
})

const reply = ref('')
const replyError = ref<string | null>(null)
const replying = ref(false)
const solving = ref(false)
const actionError = ref<string | null>(null)

async function sendReply(): Promise<void> {
  if (replying.value || !detail.value) return
  if (!reply.value.trim()) {
    replyError.value = t('support.replyRequired')
    return
  }
  replying.value = true
  replyError.value = null
  try {
    detail.value = await supportApi.reply(detail.value.request.id, reply.value.trim())
    reply.value = ''
    showToast(t('support.replySent', { number: detail.value.request.number }))
  } catch (reason) {
    replyError.value = resolveApiError(reason, t('support.replyError'))
  } finally {
    replying.value = false
  }
}

/** A solved request is done with: back to the list, which confirms it (handed over in the history state). */
async function solve(): Promise<void> {
  if (solving.value || !detail.value) return
  solving.value = true
  actionError.value = null
  try {
    const solvedRequest = await supportApi.solve(detail.value.request.id)
    await router.push({ name: supportRouteName.value, state: { supportSolved: solvedRequest.request.number } })
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('support.solveError'))
  } finally {
    solving.value = false
  }
}

async function downloadAttachment(messageId: number, fileName: string): Promise<void> {
  try {
    saveBlobDownload(await supportApi.attachment(messageId), fileName)
  } catch (reason) {
    actionError.value = resolveApiError(reason)
  }
}
</script>
