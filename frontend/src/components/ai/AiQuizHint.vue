<template>
  <div v-if="settings && settings.mode !== 'OFF' && target && (revealed || offered)" class="ai-hint">
    <div v-if="revealed" class="ai-hint-card" role="status" aria-live="polite" data-testid="ai-quiz-hint">
      <p class="ai-hint-title"><IconGlyph name="hint" :size="16" />{{ t('hintChrome.hintTitle') }}</p>
      <p class="ai-hint-text">{{ t('aiHelper.hintRuleOut', { number: target.index + 1, option: target.option }) }}</p>
    </div>
    <div v-else class="ai-hint-offer" role="status" data-testid="ai-quiz-hint-offer">
      <IconGlyph name="hint" :size="16" />
      <span>{{ settings.mode === 'ON_REQUEST' ? t('hintChrome.hintOfferAsk') : t('hintChrome.hintOffer') }}</span>
      <button
        class="btn btn-secondary btn-sm"
        type="button"
        data-testid="ai-quiz-hint-show"
        @click="emit('ruleOut', target.index, target.optionIndex)"
      >
        {{ t('hintChrome.showHint') }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onActivated, onDeactivated, ref, watch } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useHintOffer } from '@/composables/useHintOffer'
import { useHintSettings } from '@/composables/useHintSettings'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { aiGeneratorMessages } from '@/plugins/aiGeneratorMessages'
import { hintChromeMessages } from '@/plugins/hintChromeMessages'

import type { ChoiceQuestion } from './AiChoiceQuestions.vue'

const props = defineProps<{
  questions: readonly ChoiceQuestion[]
  answers: readonly (number | null)[]
  correctOptions: readonly (readonly number[])[]
  ruledOut: Readonly<Partial<Record<number, number>>>
  checked: boolean
  paused?: boolean
}>()
const emit = defineEmits<{ ruleOut: [index: number, optionIndex: number] }>()

const { t } = useLocalMessages({ aiHelper: aiGeneratorMessages, hintChrome: hintChromeMessages })
const { settings } = useHintSettings()

// Replacing a quiz restarts the hint timer even when the first unanswered index stays the same.
const quizRun = ref(0)
watch(
  () => props.questions,
  () => quizRun.value++,
)

/** Rule out one wrong option only if another wrong choice remains. Equivalent answers can match several options. */
const target = computed(() => {
  if (props.checked) return null
  for (const [index, question] of props.questions.entries()) {
    const correct = props.correctOptions[index]
    if (props.answers[index] !== null || !correct?.length || question.options.length - correct.length < 2) continue
    const optionIndex = question.options.findIndex((_, at) => !correct.includes(at))
    return { index, optionIndex, option: question.options[optionIndex]! }
  }
  return null
})

const active = ref(true)
onActivated(() => {
  active.value = true
})
onDeactivated(() => {
  active.value = false
})

const revealed = computed(() => target.value !== null && props.ruledOut[target.value.index] !== undefined)
const { offered, poke } = useHintOffer({
  itemKey: computed(() => (target.value === null ? null : `${quizRun.value}:${target.value.index}`)),
  open: computed(() => {
    const limit = settings.value?.perSet
    return (
      active.value &&
      !props.paused &&
      target.value !== null &&
      !revealed.value &&
      (!limit || Object.keys(props.ruledOut).length < limit)
    )
  }),
})

// An answer is progress: the learner is not stuck, so an automatic offer waits again.
watch(() => [...props.answers], poke)
</script>
