<template>
  <section class="quiz-review" :aria-labelledby="`${baseId}-title`">
    <h2 :id="`${baseId}-title`" class="card-title">
      {{ title }}<template v-if="correctSummary"> · {{ correctSummary }}</template>
    </h2>
    <ol v-if="wrong.length" class="row-list">
      <li v-for="question in wrong" :key="question.questionId" class="quiz-review-item">
        <div class="quiz-review-item-head">
          <strong class="row-list-title">{{ question.number }}. <CjkText :text="question.text" /></strong>
          <StatusPill :label="t('classPlayer.result.incorrect')" tone="danger" icon="close" />
        </div>
        <p class="quiz-review-line">
          {{ t('classPlayer.result.yourAnswer') }}
          <strong><CjkText :text="oneLine(question.yourAnswer) || t('classPlayer.result.noAnswer')" /></strong>
          <template v-if="question.correctAnswer">
            · {{ t('classPlayer.result.correctAnswer') }}
            <strong><CjkText :text="oneLine(question.correctAnswer)" /></strong>
          </template>
        </p>
        <!-- Why the pick was wrong (the admin's feedback on that answer), then why the right one is right. -->
        <p v-if="question.optionFeedback" class="quiz-review-line" data-testid="quiz-review-feedback">
          <CjkText :text="question.optionFeedback" />
        </p>
        <p v-if="question.explanation" class="quiz-review-line is-muted"><CjkText :text="question.explanation" /></p>
        <RouterLink
          class="btn btn-link quiz-review-ask"
          :to="{
            name: 'student-ai',
            params: { tab: 'ask' },
            query: {
              q: t('classPlayer.result.askQuestion', {
                question: question.text,
                answer: oneLine(question.yourAnswer) || t('classPlayer.result.noAnswer'),
                correct: oneLine(question.correctAnswer),
              }),
            },
          }"
        >
          <IconGlyph name="ai" :size="16" />{{ t('classPlayer.result.askSparkBot') }}
        </RouterLink>
      </li>
    </ol>
  </section>
</template>

<script setup lang="ts">
import { computed, useId } from 'vue'
import { RouterLink } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classPlayerMessages } from '@/plugins/classPlayerMessages'
import type { QuizResult } from '@/types/classes'
import { formatList } from '@/utils/format'

/**
 * The review of a lesson quiz attempt, under its result in focus mode and on an earlier attempt's page. Every wrong
 * answer is shown with what was right, the feedback on the answer picked (when the admin wrote one), the explanation
 * and a question for SparkBot; the right ones are named in the heading ("Questions 1–3 were correct.").
 */
const props = defineProps<{ result: QuizResult; title: string }>()

const { t, locale } = useLocalMessages({ classPlayer: classPlayerMessages })
const baseId = useId()

const wrong = computed(() => props.result.questions.filter((question) => !question.correct))

/** "Questions 1–3 and 5 were correct.": runs of numbers in a row read as a range. */
const correctSummary = computed(() => {
  const numbers = props.result.questions
    .filter((question) => question.correct)
    .map((question) => question.number)
    .sort((a, b) => a - b)
  if (!numbers.length) return ''
  const runs: string[] = []
  let start = numbers[0]!
  numbers.forEach((number, position) => {
    const next = numbers[position + 1]
    if (next === number + 1) return
    runs.push(start === number ? String(number) : `${start}–${number}`)
    if (next !== undefined) start = next
  })
  return t('classPlayer.result.correctSummary', { numbers: formatList(runs, locale.value) }, numbers.length)
})

/** An answer can hold its pinyin on a second line; here it runs on. */
function oneLine(answer: string | null): string {
  return (answer ?? '').replace(/\s*\n\s*/g, ' ')
}
</script>
