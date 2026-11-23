<template>
  <div class="segmented" role="group" :aria-label="label">
    <button
      v-for="option in options"
      :key="option.value"
      class="segmented-option"
      type="button"
      :aria-pressed="option.value === model"
      :disabled="disabled"
      :data-testid="option.testId"
      @click="model = option.value"
    >
      <IconGlyph v-if="option.icon" :name="option.icon" :size="16" />
      {{ option.label }}
    </button>
  </div>
</template>

<script setup lang="ts" generic="T extends string | number">
import IconGlyph, { type IconName } from '@/components/common/IconGlyph.vue'

/** A row of mutually exclusive choices (a period, a type), each a toggle button with `aria-pressed`. */
export interface SegmentedOption<V> {
  readonly value: V
  readonly label: string
  readonly icon?: IconName
  readonly testId?: string
}

defineProps<{
  options: readonly SegmentedOption<T>[]
  label: string
  disabled?: boolean
}>()

const model = defineModel<T>({ required: true })
</script>
