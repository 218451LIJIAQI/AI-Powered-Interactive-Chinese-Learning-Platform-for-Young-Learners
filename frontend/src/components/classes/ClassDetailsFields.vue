<template>
  <div class="form-grid">
    <div class="field">
      <label class="field-label" :for="`${baseId}-title`">
        {{ t('teacherClasses.form.title') }}<span class="required-mark" aria-hidden="true">*</span>
      </label>
      <input
        :id="`${baseId}-title`"
        v-model="title"
        class="input"
        required
        maxlength="200"
        :disabled="disabled"
        :placeholder="t('teacherClasses.form.titlePlaceholder')"
        data-testid="class-title-input"
      />
    </div>
    <div class="field">
      <label class="field-label" :for="`${baseId}-description`">{{ t('teacherClasses.form.description') }}</label>
      <textarea
        :id="`${baseId}-description`"
        v-model="description"
        class="textarea"
        rows="3"
        maxlength="2000"
        :disabled="disabled"
      />
    </div>
    <div class="form-grid two">
      <div class="field">
        <label class="field-label" :for="`${baseId}-theme`">{{ t('teacherClasses.form.theme') }}</label>
        <select :id="`${baseId}-theme`" v-model="topicId" class="select" :disabled="disabled">
          <option :value="null">{{ t('teacherClasses.form.choose') }}</option>
          <option v-for="theme in themes" :key="theme.id" :value="theme.id">{{ theme.name }}</option>
        </select>
      </div>
      <div class="field">
        <label class="field-label" :for="`${baseId}-best-after`">{{ t('teacherClasses.form.bestAfter') }}</label>
        <select :id="`${baseId}-best-after`" v-model="bestAfterUnit" class="select" :disabled="disabled">
          <option :value="null">{{ t('teacherClasses.form.choose') }}</option>
          <option v-for="unit in LEARN_PATH_UNITS" :key="unit" :value="unit">
            {{ t('teacherClasses.form.unit', { unit }) }}
          </option>
        </select>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, useId } from 'vue'

import { useLocalMessages } from '@/composables/useLocalMessages'
import { teacherClassesMessages } from '@/plugins/teacherClassesMessages'
import type { LearningLevel } from '@/types/api'

export interface ClassDetailsForm {
  title: string
  description: string
  topicId: number | null
  bestAfterUnit: number | null
}

/** The Learn path's units a class can be recommended after (the server accepts 1 to 8). */
const LEARN_PATH_UNITS = [1, 2, 3, 4, 5, 6, 7, 8] as const

/** Title, description, theme and "best after" of a class (New class and the Details tab). */
const props = withDefaults(
  defineProps<{
    modelValue: ClassDetailsForm
    themes: readonly LearningLevel[]
    disabled?: boolean
  }>(),
  { disabled: false },
)
const emit = defineEmits<{ 'update:modelValue': [value: ClassDetailsForm] }>()

const { t } = useLocalMessages({ teacherClasses: teacherClassesMessages })
const baseId = useId()

function field<K extends keyof ClassDetailsForm>(key: K) {
  return computed<ClassDetailsForm[K]>({
    get: () => props.modelValue[key],
    set: (value) => emit('update:modelValue', { ...props.modelValue, [key]: value }),
  })
}

const title = field('title')
const description = field('description')
const topicId = field('topicId')
const bestAfterUnit = field('bestAfterUnit')
</script>
