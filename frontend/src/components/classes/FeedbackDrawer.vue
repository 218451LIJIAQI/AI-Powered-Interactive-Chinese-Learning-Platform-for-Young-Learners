<template>
  <AppOverlay
    variant="drawer"
    :open="open"
    :title="title"
    :subtitle="subtitle"
    :busy="busy"
    test-id="feedback-drawer"
    @close="emit('close')"
  >
    <form :id="formId" class="form-grid" @submit.prevent="save">
      <div v-if="targets" class="field">
        <label class="field-label" :for="`${formId}-about`">{{ t('teacherStudents.write.about') }}</label>
        <select :id="`${formId}-about`" v-model="targetKey" class="select" data-testid="feedback-about">
          <option value="">{{ t('teacherStudents.write.general') }}</option>
          <option v-for="target in targets" :key="`${target.kind}-${target.id}`" :value="`${target.kind}-${target.id}`">
            {{
              target.kind === 'QUIZ_ATTEMPT'
                ? t('teacherStudents.write.attempt', {
                    label: target.label,
                    percent: target.percent ?? 0,
                    date: formatShortDate(target.at),
                  })
                : t('teacherStudents.write.lesson', { label: target.label })
            }}
          </option>
        </select>
      </div>
      <div class="field">
        <label class="field-label" :for="`${formId}-message`">
          {{ t('teacherStudents.write.message') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <textarea
          :id="`${formId}-message`"
          v-model="message"
          class="textarea"
          rows="6"
          minlength="5"
          maxlength="2000"
          required
          :aria-describedby="`${formId}-hint`"
          data-testid="feedback-message"
        />
        <div class="field-foot">
          <p :id="`${formId}-hint`" class="field-hint">{{ t('teacherStudents.write.messageHint') }}</p>
          <p class="field-hint field-counter">{{ t('teacherStudents.write.counter', { count: message.length }) }}</p>
        </div>
      </div>
      <p v-if="error" class="field-error" role="alert">{{ error }}</p>
    </form>
    <template #footer>
      <button type="button" class="btn btn-secondary" :disabled="busy" @click="emit('close')">
        {{ t('common.cancel') }}
      </button>
      <button
        type="submit"
        class="btn btn-primary"
        :form="formId"
        :disabled="busy || message.trim().length < 5"
        :aria-busy="busy"
        data-testid="feedback-send"
      >
        <IconGlyph name="send" :size="16" />{{ t('teacherStudents.write.send') }}
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { ref, useId, watch } from 'vue'

import AppOverlay from '@/components/common/AppOverlay.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { commonMessages } from '@/plugins/commonMessages'
import { teacherStudentsMessages } from '@/plugins/teacherStudentsMessages'
import type { FeedbackTarget, FeedbackType } from '@/types/classes'
import { formatShortDate } from '@/utils/format'

export interface FeedbackDraft {
  type: FeedbackType
  message: string
  target: FeedbackTarget | null
}

/** The server still files feedback under a type; teachers no longer choose one. */
const DEFAULT_FEEDBACK_TYPE: FeedbackType = 'ENCOURAGING'

/** Write feedback to a student, about a quiz attempt, a lesson or in general. */
const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    subtitle?: string
    /** What the feedback can be about. */
    targets?: readonly FeedbackTarget[] | null
    busy: boolean
    error: string | null
  }>(),
  { subtitle: '', targets: null },
)
const emit = defineEmits<{ save: [draft: FeedbackDraft]; close: [] }>()

const { t } = useLocalMessages({ teacherStudents: teacherStudentsMessages, common: commonMessages })
const formId = useId()

const message = ref('')
const targetKey = ref('')

watch(
  () => props.open,
  (open) => {
    if (!open) return
    message.value = ''
    const latestAttempt = props.targets?.find((target) => target.kind === 'QUIZ_ATTEMPT')
    targetKey.value = latestAttempt ? `${latestAttempt.kind}-${latestAttempt.id}` : ''
  },
  { immediate: true },
)

function save(): void {
  const target = props.targets?.find((item) => `${item.kind}-${item.id}` === targetKey.value) ?? null
  emit('save', { type: DEFAULT_FEEDBACK_TYPE, message: message.value.trim(), target })
}
</script>
