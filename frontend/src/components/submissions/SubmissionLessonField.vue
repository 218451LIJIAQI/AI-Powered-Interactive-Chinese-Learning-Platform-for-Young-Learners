<template>
  <div class="field">
    <label class="field-label" :for="inputId">{{
      lessons.length ? t('practiceSubmissions.lesson') : t('practiceSubmissions.lessonIdLabel')
    }}</label>
    <select
      v-if="lessons.length"
      :id="inputId"
      class="select"
      :value="modelValue"
      :disabled="disabled"
      :aria-describedby="describedBy"
      :aria-invalid="Boolean(error) || undefined"
      data-testid="submission-lesson"
      @change="onInput"
    >
      <option value="">{{ t('practiceSubmissions.lessonNone') }}</option>
      <option v-for="lesson in lessons" :key="lesson.lessonId" :value="String(lesson.lessonId)">
        {{ lesson.lessonTitle }}
      </option>
    </select>
    <input
      v-else
      :id="inputId"
      class="input"
      type="text"
      inputmode="numeric"
      pattern="[0-9]*"
      autocomplete="off"
      :value="modelValue"
      :disabled="disabled"
      :aria-describedby="describedBy"
      :aria-invalid="Boolean(error) || undefined"
      data-testid="submission-lesson"
      @input="onInput"
    />
    <small :id="`${inputId}-hint`" class="field-hint">{{ t('practiceSubmissions.lessonHint') }}</small>
    <small v-if="error" :id="`${inputId}-error`" class="field-error">{{ error }}</small>
  </div>
</template>

<script setup lang="ts">
import { computed, useId } from 'vue'

import type { PracticeLessonOption } from '@/utils/practiceUpload'

import { usePracticeSubmissionText } from './practiceSubmissionText'

/** Optional lesson for an upload: a select of the learner's lessons, or a numeric lesson id when none are known. */
const props = withDefaults(
  defineProps<{
    modelValue: string
    lessons: readonly PracticeLessonOption[]
    disabled?: boolean
    error?: string | null
  }>(),
  { disabled: false, error: null },
)
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const { t } = usePracticeSubmissionText()
const inputId = `${useId()}-lesson`
const describedBy = computed(() => [`${inputId}-hint`, props.error ? `${inputId}-error` : ''].filter(Boolean).join(' '))

function onInput(event: Event): void {
  emit('update:modelValue', (event.target as HTMLInputElement | HTMLSelectElement).value)
}
</script>
