<template>
  <div class="plan-choices" :class="`is-${variant}`" role="group" :aria-label="label" :data-testid="testId">
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
      <span v-if="variant === 'cards' && choice.icon" class="plan-choice-icon" aria-hidden="true">
        <IconGlyph :name="choice.icon" :size="20" />
      </span>
      <IconGlyph v-else-if="variant === 'chips' && isSelected(choice.value)" name="check" :size="14" />
      <span class="plan-choice-copy">
        <span class="plan-choice-label">{{ choice.label }}</span>
        <span v-if="variant === 'cards' && choice.hint" class="plan-choice-hint">{{ choice.hint }}</span>
      </span>
      <span v-if="choice.suggested" class="plan-choice-badge">{{ suggestedLabel }}</span>
      <span v-if="variant === 'cards' && isSelected(choice.value)" class="plan-choice-tick" aria-hidden="true">
        <IconGlyph name="check" :size="14" />
      </span>
    </button>
  </div>
</template>

<script setup lang="ts">
import IconGlyph from '@/components/common/IconGlyph.vue'

/**
 * The setup's choices: big cards with an icon and a hint (reasons, time, focus, how much Mandarin) or compact chips
 * (goals, deadlines, and every choice in the edit drawer). Selection is shown with a check and a border, never by
 * colour alone; `aria-pressed` tells screen readers.
 */
export interface PlanChoice {
  value: string
  label: string
  hint?: string
  icon?: string
  suggested?: boolean
}

const props = withDefaults(
  defineProps<{
    choices: readonly PlanChoice[]
    selected: readonly string[]
    label: string
    variant?: 'cards' | 'chips'
    suggestedLabel?: string
    testId?: string
  }>(),
  { variant: 'cards', suggestedLabel: '', testId: undefined },
)

const emit = defineEmits<{ toggle: [value: string] }>()

function isSelected(value: string): boolean {
  return props.selected.includes(value)
}
</script>
