<template>
  <ol class="ai-questions" :class="{ 'is-two': columns === 2 }">
    <li v-for="(question, index) in questions" :key="index" class="ai-question">
      <fieldset>
        <legend>
          <span class="ai-question-text">{{ index + 1 }}. {{ question.text }}</span>
          <StatusPill v-if="results[index] === 'correct'" :label="t('aiHelper.correct')" tone="success" icon="check" />
          <StatusPill
            v-else-if="results[index] === 'incorrect'"
            :label="t('aiHelper.tryAgain')"
            tone="strong"
            icon="close"
          />
        </legend>
        <div class="ai-options">
          <label
            v-for="(option, optionIndex) in question.options"
            :key="optionIndex"
            class="ai-option"
            :class="{
              'is-selected': answers[index] === optionIndex,
              'is-correct': results[index] === 'correct' && answers[index] === optionIndex,
              'is-incorrect': results[index] === 'incorrect' && answers[index] === optionIndex,
            }"
          >
            <input
              v-model="answers[index]"
              class="sr-only"
              type="radio"
              :name="`${baseId}-q${index}`"
              :value="optionIndex"
              :disabled="results[index] === 'correct'"
              @change="results[index] = null"
            />
            <IconGlyph v-if="results[index] === 'correct' && answers[index] === optionIndex" name="check" :size="14" />
            <IconGlyph
              v-else-if="results[index] === 'incorrect' && answers[index] === optionIndex"
              name="close"
              :size="14"
            />
            <span>{{ option }}</span>
          </label>
        </div>
        <p v-if="results[index] === 'correct' && question.note" class="ai-question-note">{{ question.note }}</p>
      </fieldset>
    </li>
  </ol>

  <footer class="ai-result-foot">
    <p :class="{ 'is-checked': checked }" role="status" aria-live="polite">{{ footText }}</p>
    <button class="btn btn-primary btn-sm" type="button" :disabled="!hasUnchecked" @click="check">
      <IconGlyph name="check" :size="16" />
      {{ t('aiHelper.checkAnswers') }}
    </button>
  </footer>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import { answersMatch } from '@/utils/aiPracticeQuiz'

/**
 * SparkBot's multiple-choice questions (Quick quiz, the Story's check): answers are checked in the browser, a wrong
 * one is marked and can be tried again, a right one locks and shows its note. Check answers waits for a new answer.
 * Nothing is sent, saved or counted.
 */
export interface ChoiceQuestion {
  readonly text: string
  readonly options: readonly string[]
  readonly answer: string
  /** Shown once the question is answered right. */
  readonly note?: string | null
}

type Result = 'correct' | 'incorrect' | null

const props = defineProps<{
  questions: readonly ChoiceQuestion[]
  /** The Story sets its questions side by side. */
  columns?: 1 | 2
}>()

const { t } = useLocalMessages({ aiHelper: aiHelperMessages })
const baseId = useId()

const answers = ref<(number | null)[]>([])
const results = ref<Result[]>([])
const checked = ref(false)

const answeredCount = computed(() => answers.value.filter((answer) => answer !== null).length)
const correctCount = computed(() => results.value.filter((result) => result === 'correct').length)
const allCorrect = computed(() => props.questions.length > 0 && correctCount.value === props.questions.length)
const hasUnchecked = computed(() =>
  answers.value.some((answer, index) => answer !== null && results.value[index] === null),
)
const footText = computed(() => {
  const total = props.questions.length
  if (allCorrect.value) return t('aiHelper.quizAllCorrect', { correct: correctCount.value, total })
  return checked.value
    ? t('aiHelper.quizScore', { correct: correctCount.value, total })
    : t('aiHelper.answered', { done: answeredCount.value, total })
})

watch(
  () => props.questions,
  (questions) => {
    answers.value = questions.map(() => null)
    results.value = questions.map(() => null)
    checked.value = false
  },
  { immediate: true },
)

function check(): void {
  results.value = props.questions.map((question, index) => {
    const answer = answers.value[index]
    if (answer === null || answer === undefined) return null
    return answersMatch(question.options[answer], question.answer) ? 'correct' : 'incorrect'
  })
  checked.value = true
}
</script>
