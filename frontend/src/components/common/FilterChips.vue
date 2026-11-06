<template>
  <div class="filter-chips" :class="{ 'is-sm': size === 'sm' }" :role="label ? 'group' : undefined" :aria-label="label">
    <button
      v-for="option in options"
      :key="String(option.value)"
      class="filter-chip"
      type="button"
      :aria-pressed="option.value === model"
      :disabled="disabled"
      :data-testid="option.testId"
      @click="model = option.value"
    >
      <IconGlyph v-if="option.icon" :name="option.icon" :size="14" />
      {{ chipLabel(option) }}
    </button>
  </div>
</template>

<script setup lang="ts" generic="T extends string | number | null">
import IconGlyph from '@/components/common/IconGlyph.vue'

/**
 * Filter chips for one list ("All · 3", "Needs support · 1"), each a toggle button with `aria-pressed`. Use this for
 * filters; `SegmentedControl` is only for switching a view mode. Give it a `label` unless a `<fieldset>` legend already
 * names the group. Size `md` is 32 high (filters, answer options), `sm` 26 high (suggestions, small stage controls).
 */
export interface FilterChipOption<V> {
  readonly value: V
  readonly label: string
  /** Shown as "Label · N"; leave it out for a chip without a count. */
  readonly count?: number
  readonly icon?: string
  readonly testId?: string
}

withDefaults(
  defineProps<{
    options: readonly FilterChipOption<T>[]
    label?: string
    size?: 'md' | 'sm'
    disabled?: boolean
  }>(),
  { label: undefined, size: 'md', disabled: false },
)

const model = defineModel<T>({ required: true })

function chipLabel(option: FilterChipOption<T>): string {
  return option.count === undefined ? option.label : `${option.label}\u00a0· ${option.count}`
}
</script>
