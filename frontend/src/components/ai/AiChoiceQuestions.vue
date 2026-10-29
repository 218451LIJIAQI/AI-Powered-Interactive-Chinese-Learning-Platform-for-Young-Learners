<template>
  <ol class="ai-questions">
    <li v-for="(question, index) in questions" :key="index" class="ai-question">
      <fieldset>
        <legend>
          <span class="ai-question-number" aria-hidden="true">{{ index + 1 }}</span>
          <span class="ai-question-text">
            <span class="sr-only">{{ index + 1 }}. </span>{{ question.text }}
          </span>
          <StatusPill
            v-if="results[index] === 'correct'"
            :label="t('aiHelper.correct')"
            tone="success"
            icon="correct"
          />
          <StatusPill
            v-else-if="results[index] === 'incorrect'"
            :label="t('aiHelper.tryAgain')"
            tone="danger"
            icon="incorrect"
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
            <span class="ai-option-mark" aria-hidden="true">
              <IconGlyph
                v-if="results[index] === 'correct' && answers[index] === optionIndex"
                name="check"
                :size="14"
              />
              <IconGlyph
                v-else-if="results[index] === 'incorrect' && answers[index] === optionIndex"
                name="close"
                :size="14"
              />
            </span>
            <span class="cjk-text">{{ option }}</span>
          </label>
        </div>
        <p v-if="results[index] === 'correct' && question.note" class="ai-question-note">{{ question.note }}</p>
      </fieldset>
    </li>
  </ol>

  <footer class="ai-result-foot">
    <p role="status" aria-live="polite">
      {{
        checked
          ? t('aiHelper.quizScore', { correct: correctCount, total: questions.length })
          : t('aiHelper.answered', { done: answeredCount, total: questions.length })
      }}
    </p>
    <button class="btn btn-primary" type="button" :disabled="!answeredCount || allCorrect" @click="check">
      <IconGlyph name="check" :size="16" />
      {{ t('aiHelper.checkAnswers') }}
    </button>
  </footer>
  <p v-if="checked && !allCorrect" class="callout is-compact is-warning" role="status">
    <IconGlyph name="alert" :size="16" /> {{ t('aiHelper.quizRetryHint') }}
  </p>
  <p v-else-if="allCorrect" class="callout is-compact is-success" role="status">
    <IconGlyph name="celebrate" :size="16" /> {{ t('aiHelper.allCorrect') }}
  </p>
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
 * one is marked and can be tried again, a right one locks and shows its note. Nothing is sent, saved or counted.
 */
export interface ChoiceQuestion {
  readonly text: string
  readonly options: readonly string[]
  readonly answer: string
  /** Shown once the question is answered right. */
  readonly note?: string | null
}

type Result = 'correct' | 'incorrect' | null

const props = defineProps<{ questions: readonly ChoiceQuestion[] }>()

const { t } = useLocalMessages({ aiHelper: aiHelperMessages })
const baseId = useId()

const answers = ref<(number | null)[]>([])
const results = ref<Result[]>([])
const checked = ref(false)

const answeredCount = computed(() => answers.value.filter((answer) => answer !== null).length)
const correctCount = computed(() => results.value.filter((result) => result === 'correct').length)
const allCorrect = computed(() => props.questions.length > 0 && correctCount.value === props.questions.length)

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
