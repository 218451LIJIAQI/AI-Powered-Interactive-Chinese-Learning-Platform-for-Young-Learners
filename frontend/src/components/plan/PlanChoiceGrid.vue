<template>
  <div
    class="plan-choices"
    :class="[`is-${variant}`, { 'is-compact': compact }]"
    :style="{ '--plan-choice-columns': columns }"
    role="group"
    :aria-label="label"
    :data-testid="testId"
  >
    <button
      v-for="choice in choices"
      :key="choice.value"
      class="plan-choice"
      :class="{ 'is-selected': isSelected(choice.value) }"
      type="button"
      :aria-pressed="isSelected(choice.value)"
      :data-value="choice.value"
      @click="emit('toggle', choice.value)"
    >
      <template v-if="variant === 'chips'">
        <IconGlyph v-if="isSelected(choice.value)" name="check" :size="14" />
        <IconGlyph v-else-if="choice.icon" :name="choice.icon" :size="14" />
        <span class="plan-choice-label">{{ choice.label }}</span>
      </template>
      <template v-else>
        <span v-if="choice.icon" class="plan-choice-icon" aria-hidden="true">
          <IconGlyph :name="choice.icon" :size="20" />
        </span>
        <span class="plan-choice-copy">
          <span class="plan-choice-head">
            <span class="plan-choice-label">{{ choice.label }}</span>
            <span v-if="choice.tag" class="tag is-sm is-outline">{{ choice.tag }}</span>
            <span v-if="choice.suggested" class="tag is-sm is-strong">{{ suggestedLabel }}</span>
          </span>
          <span v-if="choice.lead" class="plan-choice-box">
            <strong>{{ choice.lead }}:</strong> {{ choice.hint }}
          </span>
          <span v-else-if="choice.hint" class="plan-choice-hint">{{ choice.hint }}</span>
          <span v-if="choice.foot" class="plan-choice-foot">{{ choice.foot }}</span>
        </span>
        <span v-if="isSelected(choice.value)" class="plan-choice-tick" aria-hidden="true">
          <IconGlyph name="check" :size="14" />
        </span>
      </template>
    </button>
  </div>
</template>

<script setup lang="ts">
import IconGlyph from '@/components/common/IconGlyph.vue'

/**
 * The setup's choices, equal in width: cards with an icon over a title and hint (reasons, time, focus), row cards with
 * the icon beside them (how much Mandarin), or chips (goals, deadlines, and every choice in the edit drawer). Selection
 * is a 2px ring with a check, never colour alone; `aria-pressed` tells screen readers.
 */
export interface PlanChoice {
  value: string
  label: string
  hint?: string
  icon?: string
  suggested?: boolean
  /** A small tag beside the label ("HSK 1"). */
  tag?: string | null
  /** The hint in a box, led by this ("For your trip: …"). */
  lead?: string
  /** A last line under a rule ("+ listening sets each week"). */
  foot?: string
}

const props = withDefaults(
  defineProps<{
    choices: readonly PlanChoice[]
    selected: readonly string[]
    label: string
    variant?: 'cards' | 'rows' | 'chips'
    columns?: number
    /** 32 high chips, for the drawer and the dialog. */
    compact?: boolean
    suggestedLabel?: string
    testId?: string
  }>(),
  { variant: 'cards', columns: 4, compact: false, suggestedLabel: '', testId: undefined },
)

const emit = defineEmits<{ toggle: [value: string] }>()

function isSelected(value: string): boolean {
  return props.selected.includes(value)
}
</script>
