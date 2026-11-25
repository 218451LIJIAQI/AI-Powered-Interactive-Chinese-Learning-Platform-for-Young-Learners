<template>
  <AppOverlay
    :title="t('studentClasses.report.title')"
    :subtitle="email ? t('studentClasses.report.lead', { email }) : ''"
    :busy="sending"
    test-id="report-problem"
    @close="emit('close')"
  >
    <form :id="`${baseId}-form`" class="form-grid" novalidate @submit.prevent="send">
      <div class="field">
        <label class="field-label" :for="`${baseId}-topic`">
          {{ t('studentClasses.report.topic') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <select :id="`${baseId}-topic`" v-model="topic" class="select" required :disabled="sending">
          <option v-for="option in SUPPORT_TOPICS" :key="option" :value="option">
            {{ t(SUPPORT_TOPIC_KEYS[option]) }}
          </option>
        </select>
      </div>

      <div class="field">
        <label class="field-label" :for="`${baseId}-message`">
          {{ t('studentClasses.report.message') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <textarea
          :id="`${baseId}-message`"
          v-model="message"
          data-autofocus
          class="textarea"
          rows="4"
          required
          :maxlength="descriptionLimit"
          :disabled="sending"
          :aria-invalid="messageError ? 'true' : undefined"
          :aria-describedby="messageError ? `${baseId}-message-error` : undefined"
          data-testid="report-problem-message"
          @blur="checkMessage"
        />
        <p v-if="messageError" :id="`${baseId}-message-error`" class="field-error" role="alert">
          {{ messageError }}
        </p>
      </div>

      <p class="report-attached"><IconGlyph name="attach" :size="14" />{{ t('studentClasses.report.attached') }}</p>
      <p v-if="sendError" class="field-error" role="alert" data-testid="report-problem-error">{{ sendError }}</p>
    </form>

    <template #footer>
      <button class="btn btn-secondary" type="button" :disabled="sending" @mousedown.prevent @click="emit('close')">
        {{ t('common.cancel') }}
      </button>
      <button
        class="btn btn-primary"
        type="submit"
        :form="`${baseId}-form`"
        :disabled="sending || !message.trim() || Boolean(messageError)"
        :aria-busy="sending"
        data-testid="report-problem-send"
      >
        {{ t('studentClasses.report.send') }}
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'

import { supportApi } from '@/api/ops'
import AppOverlay from '@/components/common/AppOverlay.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useFieldCheck } from '@/composables/useFieldCheck'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { opsLabelMessages } from '@/plugins/opsLabelMessages'
import { studentClassesMessages } from '@/plugins/studentClassesMessages'
import { useAuthStore } from '@/stores/auth'
import type { SelectableSupportTopic } from '@/types/ops'
import { resolveApiError } from '@/utils/apiError'
import { SUPPORT_TOPICS, SUPPORT_TOPIC_KEYS } from '@/utils/opsLabels'
import { truncateText } from '@/utils/truncateText'

/**
 * "Contact support" (X2.21), opened from "Still not loading? Contact support ›" under a lesson video that did not load:
 * the learner's one way to write to the MandarinSpark team. A failed send keeps what was written (X2.30).
 * The topic starts at Classes and the message says which video of which lesson did not load; both can be changed. The
 * lesson, the page and the browser go with the message, and the team replies by e-mail.
 */
const props = defineProps<{ lessonTitle: string; classTitle: string; videoTitle: string }>()
const emit = defineEmits<{ close: []; sent: [] }>()

/** The server's limit for a support message (CreateSupportRequest). */
const MAX_MESSAGE = 4000

const { t } = useLocalMessages({ studentClasses: studentClassesMessages, opsLabels: opsLabelMessages })
const baseId = useId()
const auth = useAuthStore()
const isUnmounted = useIsUnmounted()

const email = computed(() => auth.currentUser?.email ?? '')
const topic = ref<SelectableSupportTopic>('CLASSES')
// Capture the failing page when the dialog opens. Long URLs and user-agent strings must leave room for the learner.
const diagnostics = [
  t('studentClasses.report.contextLesson', { lesson: props.lessonTitle, classTitle: props.classTitle }),
  t('studentClasses.report.contextPage', { page: `${window.location.pathname}${window.location.search}` }),
  t('studentClasses.report.contextBrowser', { browser: navigator.userAgent }),
]
  .map((line) => truncateText(line, 512))
  .join('\n')
// Blank line (\n\n) before diagnostics; CreateSupportRequest normalises CRLF/CR to LF before @Size(max = 4000).
const descriptionLimit = MAX_MESSAGE - diagnostics.length - 2
const message = ref(t('studentClasses.report.videoProblem', { video: props.videoTitle, lesson: props.lessonTitle }))
const { error: messageError, validate: checkMessage } = useFieldCheck(message, (value) => {
  const trimmed = value.trim()
  if (!trimmed) return t('studentClasses.report.messageRequired')
  if (trimmed.length > descriptionLimit) return t('studentClasses.report.messageTooLong')
  return null
})
const sending = ref(false)
const sendError = ref<string | null>(null)

watch([topic, message], () => {
  sendError.value = null
})

async function send(): Promise<void> {
  if (sending.value || !checkMessage()) return
  sending.value = true
  sendError.value = null
  try {
    await supportApi.create({
      topic: topic.value,
      subject: t('studentClasses.report.subject'),
      message: `${message.value.trim()}\n\n${diagnostics}`,
    })
    if (isUnmounted()) return
    emit('sent')
  } catch (reason) {
    if (!isUnmounted()) sendError.value = resolveApiError(reason, t('studentClasses.report.sendError'))
  } finally {
    if (!isUnmounted()) sending.value = false
  }
}
</script>
