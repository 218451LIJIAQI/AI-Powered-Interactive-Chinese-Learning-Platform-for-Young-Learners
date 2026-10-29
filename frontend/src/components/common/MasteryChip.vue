<template>
  <span class="mastery-chip" :class="`is-${MASTERY_TONES[normalized]}`" data-testid="mastery-chip">
    <IconGlyph :name="MASTERY_ICONS[normalized]" :size="14" />
    {{ statusLabel('mastery', normalized) }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useStatusLabels } from '@/composables/useStatusLabels'
import type { LessonMastery } from '@/types/api'
import { MASTERY_ICONS, MASTERY_TONES, normalizeMastery } from '@/utils/mastery'

/** Lesson mastery indicator (FRPROG02): NOT_STARTED / IN_PROGRESS / COMPLETED / MASTERED. */
const props = withDefaults(defineProps<{ mastery?: LessonMastery | string | null }>(), { mastery: null })

const { statusLabel } = useStatusLabels()
const normalized = computed(() => normalizeMastery(props.mastery))
</script>

<style scoped>
.mastery-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  width: fit-content;
  min-height: 24px;
  padding: var(--space-0-5) var(--space-2-5);
  border-radius: var(--radius-pill);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
  line-height: var(--line-height-snug);
  white-space: nowrap;
}

.mastery-chip.is-neutral {
  background: var(--color-ink-100);
  color: var(--color-ink-700);
}

.mastery-chip.is-info {
  background: var(--color-info-soft);
  color: var(--color-info-text);
}

.mastery-chip.is-success {
  background: var(--color-success-soft);
  color: var(--color-success-text);
}

.mastery-chip.is-mastered {
  background: var(--color-accent-100);
  color: var(--color-accent-900);
}
</style>
