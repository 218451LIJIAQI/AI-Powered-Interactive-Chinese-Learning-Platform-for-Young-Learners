<template>
  <div class="form-grid">
    <div class="field">
      <label class="field-label" :for="`${baseId}-title`">
        {{ t('adminClasses.form.title') }}<span class="required-mark" aria-hidden="true">*</span>
      </label>
      <input
        :id="`${baseId}-title`"
        v-model="title"
        class="input"
        required
        maxlength="200"
        :disabled="disabled"
        :placeholder="t('adminClasses.form.titlePlaceholder')"
        :aria-invalid="Boolean(titleError)"
        :aria-describedby="titleError ? `${baseId}-title-error` : undefined"
        :data-autofocus="autofocus || undefined"
        data-testid="class-title"
        @blur="emit('titleBlur')"
      />
      <p v-if="titleError" :id="`${baseId}-title-error`" class="field-error" role="alert">{{ titleError }}</p>
    </div>
    <div class="field">
      <label class="field-label" :for="`${baseId}-description`">{{ t('adminClasses.form.description') }}</label>
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
        <label class="field-label" :for="`${baseId}-theme`">{{ t('adminClasses.form.theme') }}</label>
        <select :id="`${baseId}-theme`" v-model="categoryId" class="select" :disabled="disabled">
          <option :value="null">{{ t('adminClasses.form.notSet') }}</option>
          <option v-for="theme in themes" :key="theme.id" :value="theme.id">{{ theme.name }}</option>
        </select>
      </div>
      <div class="field">
        <label class="field-label" :for="`${baseId}-best-after`">{{ t('adminClasses.form.bestAfter') }}</label>
        <select :id="`${baseId}-best-after`" v-model="bestAfterUnit" class="select" :disabled="disabled">
          <option :value="null">{{ t('adminClasses.form.notSet') }}</option>
          <option v-for="unit in learnUnits" :key="unit.number" :value="unit.number">
            {{
              unit.title
                ? t('adminClasses.form.learnUnit', { number: unit.number, title: unit.title })
                : t('adminClasses.form.learnUnitNumber', { number: unit.number })
            }}
          </option>
        </select>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, useId } from 'vue'

import type { LearnUnitOption } from '@/components/admin/useClassFormOptions'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { adminClassesMessages } from '@/plugins/adminClassesMessages'
import type { ClassDetailsForm } from '@/types/adminClasses'
import type { ClassTopic } from '@/types/api'

/**
 * Title, description, theme and "best after" Learn unit of a class (New class and the class's Details tab). The page
 * checks the title (`titleError`, shown under the field) when the field is left (`titleBlur`) and when it saves.
 */
defineProps<{
  themes: readonly ClassTopic[]
  learnUnits: readonly LearnUnitOption[]
  disabled?: boolean
  titleError?: string | null
  /** The title takes the focus when the form opens (New class). */
  autofocus?: boolean
}>()
const emit = defineEmits<{ titleBlur: [] }>()
const model = defineModel<ClassDetailsForm>({ required: true })

const { t } = useLocalMessages({ adminClasses: adminClassesMessages })
const baseId = useId()

function field<K extends keyof ClassDetailsForm>(key: K) {
  return computed<ClassDetailsForm[K]>({
    get: () => model.value[key],
    set: (value) => (model.value = { ...model.value, [key]: value }),
  })
}

const title = field('title')
const description = field('description')
const categoryId = field('categoryId')
const bestAfterUnit = field('bestAfterUnit')
</script>
