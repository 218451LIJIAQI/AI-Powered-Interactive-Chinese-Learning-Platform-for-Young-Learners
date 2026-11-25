<template>
  <div
    ref="group"
    class="filter-chips"
    :class="{ 'is-sm': size === 'sm' }"
    :role="label ? 'group' : undefined"
    :aria-label="label"
  >
    <button
      v-for="option in options"
      :key="option.value ?? nullOptionKey"
      class="filter-chip"
      type="button"
      :aria-pressed="option.value === model"
      :disabled="disabled"
      :data-testid="option.testId"
      @click="model = option.value"
    >
      {{ chipLabel(option) }}
    </button>
  </div>
</template>

<script setup lang="ts" generic="T extends string | number | null">
import { useTemplateRef } from 'vue'

/**
 * Filter chips for one list ("All · 3", "Needs support · 1"), each a toggle button with `aria-pressed`. Use this for
 * filters; `SegmentedControl` is only for switching a view mode. Give it a `label` unless a `<fieldset>` legend already
 * names the group. Size `md` is 32 high (filters, answer options), `sm` 26 high (suggestions, small stage controls).
 */
export interface FilterChipsHandle {
  readonly $el?: HTMLElement | null
  focusSelected: () => void
}

export interface FilterChipOption<V> {
  readonly value: V
  readonly label: string
  /** Shown as "Label · N"; leave it out for a chip without a count. */
  readonly count?: number
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
const nullOptionKey = Symbol('null option')
const group = useTemplateRef<HTMLElement>('group')

function chipLabel(option: FilterChipOption<T>): string {
  return option.count === undefined ? option.label : `${option.label}\u00a0· ${option.count}`
}

function focusSelected(): void {
  group.value?.querySelector<HTMLButtonElement>('button[aria-pressed="true"]')?.focus()
}

defineExpose({ focusSelected })
</script>
