<template>
  <div class="segmented" :role="label ? 'group' : undefined" :aria-label="label || undefined">
    <button
      v-for="option in options"
      :key="String(option.value)"
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

<script setup lang="ts" generic="T extends string | number | null">
import IconGlyph from '@/components/common/IconGlyph.vue'

/**
 * A row of mutually exclusive choices (a filter, a period, a type), each a toggle button with `aria-pressed`.
 * Give it a `label` unless it sits in a `<fieldset>` whose legend names the group already.
 */
export interface SegmentedOption<V> {
  readonly value: V
  readonly label: string
  readonly icon?: string
  readonly testId?: string
}

defineProps<{
  options: readonly SegmentedOption<T>[]
  label?: string
  disabled?: boolean
}>()

const model = defineModel<T>({ required: true })
</script>
