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
      <fieldset class="field-set">
        <legend class="field-label">{{ t('teacherStudents.write.type') }}</legend>
        <SegmentedControl
          v-model="type"
          class="feedback-types"
          :options="FEEDBACK_TYPES.map((value) => ({ value, label: t(FEEDBACK_TYPE_KEYS[value]) }))"
        />
      </fieldset>
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
          <p class="field-hint field-counter">{{ message.length }} / 2000</p>
        </div>
      </div>
      <div class="feedback-starters">
        <span class="field-label">{{ t('teacherStudents.write.starters') }}</span>
        <button
          type="button"
          class="chip"
          :aria-label="t('teacherStudents.write.useStarter', { text: starter })"
          @click="useStarter"
        >
          {{ starter }}
        </button>
      </div>
      <p v-if="error" class="field-error" role="alert">{{ error }}</p>
    </form>
    <template #footer>
      <button type="button" class="btn btn-ghost" :disabled="busy" @click="emit('close')">
        {{ t('classes.cancel') }}
      </button>
      <button
        type="submit"
        class="btn btn-primary"
        :form="formId"
        :disabled="busy || message.trim().length < 5"
        :aria-busy="busy"
        data-testid="feedback-send"
      >
        {{ initial ? t('teacherStudents.history.save') : t('teacherStudents.write.send') }}
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'

import { useLocalMessages } from '@/composables/useLocalMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { teacherStudentsMessages } from '@/plugins/teacherStudentsMessages'
import type { FeedbackRow, FeedbackTarget, FeedbackType } from '@/types/classes'
import { FEEDBACK_TYPES, FEEDBACK_TYPE_KEYS } from '@/utils/classes'
import { formatShortDate } from '@/utils/format'

import SegmentedControl from '@/components/common/SegmentedControl.vue'
import AppOverlay from '@/components/common/AppOverlay.vue'

export interface FeedbackDraft {
  type: FeedbackType
  message: string
  target: FeedbackTarget | null
}

/** Write feedback to a student (about a quiz attempt, a lesson or in general), or edit feedback already sent. */
const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    subtitle?: string
    /** What the feedback can be about (writing only). */
    targets?: readonly FeedbackTarget[] | null
    /** The feedback being edited. */
    initial?: FeedbackRow | null
    busy: boolean
    error: string | null
  }>(),
  { subtitle: '', targets: null, initial: null },
)
const emit = defineEmits<{ save: [draft: FeedbackDraft]; close: [] }>()

const { t } = useLocalMessages({ teacherStudents: teacherStudentsMessages, classes: classesMessages })
const formId = useId()

const STARTER_KEYS: Record<FeedbackType, string> = {
  ENCOURAGING: 'teacherStudents.write.starter.ENCOURAGING',
  LESSON: 'teacherStudents.write.starter.LESSON',
  WEAK_AREA: 'teacherStudents.write.starter.WEAK_AREA',
  CORRECTIVE: 'teacherStudents.write.starter.CORRECTIVE',
  PROGRESS: 'teacherStudents.write.starter.PROGRESS',
}

const type = ref<FeedbackType>('ENCOURAGING')
const message = ref('')
const targetKey = ref('')
const starter = computed(() => t(STARTER_KEYS[type.value]))

watch(
  () => [props.open, props.initial] as const,
  ([open, initial]) => {
    if (!open) return
    type.value = initial?.type ?? 'ENCOURAGING'
    message.value = initial?.message ?? ''
    const latestAttempt = props.targets?.find((target) => target.kind === 'QUIZ_ATTEMPT')
    targetKey.value = latestAttempt ? `${latestAttempt.kind}-${latestAttempt.id}` : ''
  },
  { immediate: true },
)

function useStarter(): void {
  message.value = message.value.trim() ? `${message.value.trim()} ${starter.value}` : starter.value
}

function save(): void {
  const target = props.targets?.find((item) => `${item.kind}-${item.id}` === targetKey.value) ?? null
  emit('save', { type: type.value, message: message.value.trim(), target })
}
</script>
