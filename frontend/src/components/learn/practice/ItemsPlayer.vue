<template>
  <div class="practice-player" :data-testid="`${set.type.toLowerCase()}-player`">
    <div class="practice-meter">
      <span class="practice-counter">{{ counterLabel }}</span>
      <ProgressBar :progress="progress" :label="t('practice.setProgress')" class="is-sm practice-meter-bar" />
      <span v-if="isQuiz" class="practice-pill" :class="{ 'is-hot': inARow >= 3 }" data-testid="in-a-row">
        <IconGlyph name="streak" :size="14" />{{ t('practice.inARow', { count: inARow }) }}
      </span>
      <span v-if="isQuiz" class="practice-timer" :aria-label="t('practice.timeSpent', { time: formatClock(elapsed) })">
        <IconGlyph name="timer" :size="14" />{{ formatClock(elapsed) }}
      </span>
    </div>

    <QuestionStage
      v-if="question"
      v-model="answer"
      :question="question"
      :locked="check !== null || run.busy.value"
      :check="check"
    />

    <p v-if="isQuiz && !check" class="practice-hint">{{ t('practice.quizHint', { xp: bonusXp }) }}</p>

    <div v-if="isSentence && !check" class="practice-inline-tools">
      <button class="btn btn-ghost btn-sm" type="button" :disabled="answer === null" @click="answer = null">
        <IconGlyph name="undo" :size="14" />{{ t('practice.reset') }}
      </button>
    </div>

    <CheckBar
      inline
      :check="check"
      :can-check="answer !== null"
      :checking="run.busy.value"
      :skippable="isQuiz"
      :hint="isQuiz ? t('practice.clickAnAnswer') : ''"
      :correct-title="t('practice.correct')"
      show-answer-when-right
      :continue-label="isLast ? t('practice.seeResult') : t('practice.nextItem')"
      @check="submit(answer)"
      @skip="submit(null)"
      @continue="next"
    />
    <p v-if="run.error.value" class="field-error" role="alert">{{ run.error.value }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import CheckBar from '@/components/learn/CheckBar.vue'
import QuestionStage from '@/components/learn/QuestionStage.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { practiceMessages } from '@/plugins/practiceMessages'
import type { AnswerCheck, PracticeItemAnswer, PracticeResult, PracticeSet, QuestionView } from '@/types/learn'
import { formatClock, stepProgress } from '@/utils/format'

import { usePracticeRun } from './usePracticeRun'

/**
 * Practice sets answered one item at a time: quizzes (click an answer for instant feedback, in-a-row counter and a
 * timer), listening (listen, choose, check) and sentences (build the sentence from tiles, check). The first answer
 * to each item counts; the finished run is graded on the server.
 */
const props = defineProps<{ set: PracticeSet }>()
const emit = defineEmits<{ finished: [result: PracticeResult] }>()

const QUIZ_STREAK_BONUS = 5

const { t } = useLocalMessages({ practice: practiceMessages })
const run = usePracticeRun(
  () => props.set,
  () => t('practice.saveFailed'),
)

const isQuiz = computed(() => props.set.type === 'QUIZZES')
const isSentence = computed(() => props.set.type === 'DRAG_DROP')
const bonusXp = QUIZ_STREAK_BONUS

const index = ref(0)
const answer = ref<string | null>(null)
const check = ref<AnswerCheck | null>(null)
const answers = ref<PracticeItemAnswer[]>([])
const inARow = ref(0)
const elapsed = ref(0)

const items = computed(() => props.set.items)
const item = computed(() => items.value[index.value] ?? null)
const isLast = computed(() => index.value >= items.value.length - 1)
const progress = computed(() => stepProgress(index.value, items.value.length, check.value !== null))

const counterLabel = computed(() => {
  const values = { current: index.value + 1, total: items.value.length }
  return isSentence.value ? t('practice.sentenceOf', values) : t('practice.questionOf', values)
})

const question = computed<QuestionView | null>(() => {
  const current = item.value
  if (!current) return null
  if (isSentence.value) {
    return {
      id: index.value,
      answerKind: 'BUILD',
      type: 'TRANSLATE_BUILD',
      prompt: null,
      hanzi: null,
      pinyin: null,
      meaning: current.meaning,
      audioText: null,
      options: [],
      tiles: current.tiles,
    }
  }
  return {
    id: index.value,
    answerKind: 'CHOICE',
    type: null,
    prompt: current.prompt,
    hanzi: current.hanzi,
    pinyin: current.pinyin,
    meaning: null,
    audioText: isQuiz.value ? current.hanzi : current.audioText,
    options: current.options,
    tiles: [],
  }
})

// A quiz checks as soon as an answer is clicked.
watch(answer, (value) => {
  if (isQuiz.value && value !== null && check.value === null) void submit(value)
})

async function submit(given: string | null): Promise<void> {
  const current = item.value
  if (!current || check.value || run.busy.value) return
  const verdict = await run.check(current.key, given)
  if (!verdict) return
  check.value = verdict
  answers.value.push({ itemKey: current.key, answer: given })
  inARow.value = verdict.correct ? inARow.value + 1 : 0
}

async function next(): Promise<void> {
  if (!isLast.value) {
    index.value += 1
    answer.value = null
    check.value = null
    return
  }
  const result = await run.complete(answers.value)
  if (result) emit('finished', result)
}

let timer: ReturnType<typeof setInterval> | null = null
onMounted(() => {
  if (isQuiz.value) timer = setInterval(() => (elapsed.value += 1), 1000)
})
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>
