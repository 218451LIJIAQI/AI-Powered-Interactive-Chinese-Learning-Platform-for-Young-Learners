<template>
  <div
    class="practice-feedback"
    :class="correct ? 'is-correct' : 'is-wrong'"
    role="status"
    aria-live="polite"
    data-testid="check-bar"
  >
    <IconGlyph class="practice-feedback-icon" :name="correct ? 'correct' : 'incorrect'" :size="18" />
    <div class="practice-feedback-copy">
      <p>
        <strong class="practice-feedback-title">{{ title }}</strong>
        <slot />
      </p>
      <p v-if="note" class="practice-feedback-note">{{ note }}</p>
    </div>
    <span v-if="xp > 0" class="tag is-strong">{{ t('practice.xpTag', { xp }) }}</span>
  </div>
</template>

<script setup lang="ts">
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { practiceMessages } from '@/plugins/practiceMessages'

/** The verdict inside a stage: a bar with what happened (the default slot adds the answer) and any XP it pays. */
withDefaults(defineProps<{ correct: boolean; title: string; note?: string; xp?: number }>(), { note: '', xp: 0 })

const { t } = useLocalMessages({ practice: practiceMessages })
</script>
