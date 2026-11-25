<template>
  <ol class="ai-questions" :class="{ 'is-two': columns === 2 }">
    <li v-for="(question, index) in questions" :key="index" class="ai-question">
      <fieldset>
        <legend class="ai-question-text">{{ index + 1 }}. {{ question.text }}</legend>
        <div class="ai-options">
          <label
            v-for="(option, optionIndex) in question.options"
            :key="optionIndex"
            class="ai-option"
            :class="{
              'is-selected': !checked && answers[index] === optionIndex,
              'is-correct': mark(index, optionIndex) === 'correct',
              'is-incorrect': mark(index, optionIndex) === 'incorrect',
              'is-ruled-out': !checked && ruledOut[index] === optionIndex,
            }"
          >
            <input
              v-model="answers[index]"
              class="sr-only"
              type="radio"
              :name="`${baseId}-q${index}`"
              :value="optionIndex"
              :disabled="checked || ruledOut[index] === optionIndex"
            />
            <!-- One marking everywhere: the right answer has the tick, the learner's wrong pick the cross. -->
            <template v-if="mark(index, optionIndex) === 'correct'">
              <IconGlyph name="check" :size="14" />
              <span class="sr-only">{{ t('aiHelper.markCorrectAnswer') }}</span>
            </template>
            <template v-else-if="mark(index, optionIndex) === 'incorrect'">
              <IconGlyph name="close" :size="14" />
              <span class="sr-only">{{ t('aiHelper.markYourAnswer') }}</span>
            </template>
            <span>{{ option }}</span>
            <span v-if="!checked && ruledOut[index] === optionIndex" class="sr-only">{{
              t('aiHelper.hintRuledOut')
            }}</span>
          </label>
        </div>
        <p v-if="checked && question.note" class="ai-question-note">{{ question.note }}</p>
      </fieldset>
    </li>
  </ol>

  <AiQuizHint
    v-if="hints"
    :questions="questions"
    :answers="answers"
    :correct-options="correctOptions"
    :ruled-out="ruledOut"
    :checked="checked"
    :paused="paused"
    @rule-out="(index, option) => (ruledOut[index] = option)"
  />

  <footer class="ai-result-foot">
    <p :class="{ 'is-checked': checked }" role="status" aria-live="polite">{{ footText }}</p>
    <button class="btn btn-primary btn-sm" type="button" :disabled="checked || !allAnswered" @click="check">
      <IconGlyph name="check" :size="16" />
      {{ t('aiHelper.checkAnswers') }}
    </button>
  </footer>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { aiGeneratorMessages } from '@/plugins/aiGeneratorMessages'
import { matchesAnswer } from '@/utils/aiPracticeQuiz'

import AiQuizHint from './AiQuizHint.vue'

/**
 * SparkBot's multiple-choice questions (Quick quiz, the Story's check), checked in the browser once every question has
 * an answer. Checking marks every question at once and ends the quiz: the right answer has a tick, a wrong pick a
 * cross, and each question shows its note. The Quick quiz also offers one hint per eligible question, within the
 * learner's hint budget. Checking and ruling out options both happen locally.
 */
export interface ChoiceQuestion {
  readonly text: string
  readonly options: readonly string[]
  readonly answer: string
  /** Shown once the answers are checked: the answer is on screen by then. */
  readonly note?: string | null
}

const props = defineProps<{
  questions: readonly ChoiceQuestion[]
  /** The Story sets its questions side by side. */
  columns?: 1 | 2
  /** Enable the Quick quiz's optional hints. */
  hints?: boolean
  /** Pause the hint timer while the parent regenerates a new quiz. */
  paused?: boolean
}>()

const { t } = useLocalMessages({ aiHelper: aiGeneratorMessages })
const baseId = useId()

const answers = ref<(number | null)[]>([])
/** Each question can have one hint, which rules out one option. */
const ruledOut = ref<Partial<Record<number, number>>>({})
const checked = ref(false)

/**
 * The options the answer key accepts, by the same rule as the check. None when SparkBot's key names no option: then
 * nothing is shown as right, as there is nothing to go by.
 */
const correctOptions = computed(() =>
  props.questions.map((question) => {
    const matches = matchesAnswer(question.answer)
    return question.options.flatMap((option, optionIndex) => (matches(option) ? [optionIndex] : []))
  }),
)

/** How an option is marked once checked: the right answer whether picked or not, and the wrong pick. */
function mark(index: number, optionIndex: number): 'correct' | 'incorrect' | null {
  if (!checked.value || answers.value[index] === null) return null
  if (correctOptions.value[index]?.includes(optionIndex)) return 'correct'
  return answers.value[index] === optionIndex ? 'incorrect' : null
}

const answeredCount = computed(() => answers.value.filter((answer) => answer !== null).length)
const allAnswered = computed(() => props.questions.length > 0 && answeredCount.value === props.questions.length)
const correctCount = computed(
  () =>
    answers.value.filter((answer, index) => answer !== null && correctOptions.value[index]?.includes(answer)).length,
)
const footText = computed(() => {
  const total = props.questions.length
  if (!checked.value) return t('aiHelper.answered', { done: answeredCount.value, total })
  return correctCount.value === total
    ? t('aiHelper.quizAllCorrect', { correct: total, total })
    : t('aiHelper.quizScore', { correct: correctCount.value, total })
})

watch(
  () => props.questions,
  (questions) => {
    answers.value = questions.map(() => null)
    ruledOut.value = {}
    checked.value = false
  },
  { immediate: true },
)

function check(): void {
  if (allAnswered.value) checked.value = true
}
</script>
