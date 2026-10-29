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
        rows="4"
        maxlength="2000"
        :disabled="disabled"
        :aria-describedby="`${baseId}-description-hint`"
      />
      <div class="field-foot">
        <p :id="`${baseId}-description-hint`" class="field-hint">{{ t('teacherClasses.form.descriptionHint') }}</p>
        <p class="field-hint field-counter">{{ description.length }} / 2000</p>
      </div>
    </div>
    <div class="form-grid two">
      <div class="field">
        <label class="field-label" :for="`${baseId}-level`">{{ t('teacherClasses.form.level') }}</label>
        <select :id="`${baseId}-level`" v-model="levelId" class="select" :disabled="disabled">
          <option :value="null">{{ t('teacherClasses.form.choose') }}</option>
          <option v-for="level in levels" :key="level.id" :value="level.id">{{ level.name }}</option>
        </select>
      </div>
      <div class="field">
        <label class="field-label" :for="`${baseId}-topic`">{{ t('teacherClasses.form.topic') }}</label>
        <select :id="`${baseId}-topic`" v-model="topicId" class="select" :disabled="disabled">
          <option :value="null">{{ t('teacherClasses.form.choose') }}</option>
          <option v-for="topic in topics" :key="topic.id" :value="topic.id">{{ topic.name }}</option>
        </select>
      </div>
    </div>
    <fieldset class="field-set">
      <legend class="field-label">{{ t('teacherClasses.form.learningType') }}</legend>
      <SegmentedControl
        v-model="learningType"
        :options="LEARNING_TYPES.map((type) => ({ value: type, label: t(LEARNING_TYPE_KEYS[type]) }))"
        :disabled="disabled"
      />
    </fieldset>
  </div>
</template>

<script setup lang="ts">
import { computed, useId } from 'vue'

import SegmentedControl from '@/components/common/SegmentedControl.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { teacherClassesMessages } from '@/plugins/teacherClassesMessages'
import type { LearningLevel } from '@/types/api'
import type { LearningType } from '@/types/classes'
import { LEARNING_TYPES, LEARNING_TYPE_KEYS } from '@/utils/classes'

export interface ClassDetailsForm {
  title: string
  description: string
  levelId: number | null
  topicId: number | null
  learningType: LearningType
}

/** Title, description, difficulty, topic and learning type of a class (New class and the Details tab). */
const props = withDefaults(
  defineProps<{
    modelValue: ClassDetailsForm
    levels: readonly LearningLevel[]
    topics: readonly LearningLevel[]
    disabled?: boolean
  }>(),
  { disabled: false },
)
const emit = defineEmits<{ 'update:modelValue': [value: ClassDetailsForm] }>()

const { t } = useLocalMessages({ teacherClasses: teacherClassesMessages, classes: classesMessages })
const baseId = useId()

function field<K extends keyof ClassDetailsForm>(key: K) {
  return computed<ClassDetailsForm[K]>({
    get: () => props.modelValue[key],
    set: (value) => emit('update:modelValue', { ...props.modelValue, [key]: value }),
  })
}

const title = field('title')
const description = field('description')
const levelId = field('levelId')
const topicId = field('topicId')
const learningType = field('learningType')
</script>
