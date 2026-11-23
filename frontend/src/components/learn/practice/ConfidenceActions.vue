<template>
  <div class="practice-confidence" role="group" :aria-labelledby="`${baseId}-label`">
    <span :id="`${baseId}-label`" class="practice-confidence-label">
      {{ hintUsed && picked ? t('answer.hintUsedGuess') : t('answer.howSure') }}
    </span>
    <div class="btn-group">
      <button
        class="btn btn-secondary"
        type="button"
        :disabled="disabled"
        :aria-busy="busy && chosen === 'GUESS'"
        data-testid="answer-guess"
        @click="answer('GUESS')"
      >
        {{ t('answer.guessing') }}
      </button>
      <button
        class="btn btn-secondary"
        type="button"
        :disabled="disabled || hintUsed"
        :aria-busy="busy && chosen === 'SURE'"
        data-testid="answer-sure"
        @click="answer('SURE')"
      >
        {{ t('answer.sure') }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, useId, watch } from 'vue'

import { useLocalMessages } from '@/composables/useLocalMessages'
import { practiceAnswerMessages } from '@/plugins/practiceAnswerMessages'
import type { AnswerConfidence } from '@/types/learn'

/**
 * Submits an answer with how sure the student is. The two buttons look the same, so neither is the "right" one to
 * press. Until an answer is picked the label asks "How sure are you?" with both buttons off. After a hint the answer
 * can only be a guess: once one is picked, Sure stays off and the label says why.
 */
const props = defineProps<{
  /**
   * No answer chosen yet, the answer is being checked, or a hint is on its way: whether the answer can still be Sure
   * depends on that hint (the server counts an answer after a hint it gave as a guess).
   */
  disabled: boolean
  busy: boolean
  hintUsed: boolean
  /** An answer is chosen: only then does the label say a hint makes it a guess. */
  picked: boolean
}>()

const emit = defineEmits<{ answer: [confidence: AnswerConfidence] }>()

const { t } = useLocalMessages({ answer: practiceAnswerMessages })
const baseId = useId()
const chosen = ref<AnswerConfidence | null>(null)

watch(
  () => props.busy,
  (busy) => {
    if (!busy) chosen.value = null
  },
)

function answer(confidence: AnswerConfidence): void {
  chosen.value = confidence
  emit('answer', confidence)
}
</script>
