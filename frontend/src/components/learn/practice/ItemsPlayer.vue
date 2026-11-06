<template>
  <div class="practice-player" :data-testid="`${set.type.toLowerCase()}-player`">
    <PracticeProgress :dots="dots" :label="counterLabel" :list-label="t('practice.setProgress')" />

    <div v-if="isQuiz" class="practice-combo">
      <span class="tag" :class="{ 'is-strong': inARow > 0 }" data-testid="in-a-row">
        <IconGlyph name="streak" :size="12" />{{ t('practice.inARow', { count: inARow }) }}
      </span>
      <span class="practice-combo-note">{{ t('practice.quizHint', { xp: QUIZ_STREAK_BONUS }) }}</span>
    </div>

    <div v-if="isListening && item?.audioText" class="practice-audio">
      <ListenButtons :text="item.audioText" :label="t('learn.theAudio')" large slow />
    </div>

    <QuestionStage
      v-if="question"
      v-model="answer"
      :question="question"
      :locked="check !== null || run.busy.value"
      :check="check"
      :draggable-tiles="isSentence"
    />

    <PracticeFeedback
      v-if="check"
      :correct="check.correct"
      :title="check.correct ? t('practice.correct') : t('learn.correctAnswer')"
      :note="check.explanation ?? ''"
      :xp="xpOfAnswer"
    >
      <span :lang="/\p{Script=Han}/u.test(check.correctAnswer) ? 'zh-CN' : undefined">{{ check.correctAnswer }}</span>
      <template v-if="check.meaning && check.meaning !== check.correctAnswer"> · {{ check.meaning }}</template>
      <template v-if="check.pinyin && check.pinyin !== check.correctAnswer"> · {{ check.pinyin }}</template>
    </PracticeFeedback>
    <p v-if="run.error.value" class="field-error" role="alert">{{ run.error.value }}</p>

    <div class="practice-footer">
      <div v-if="!check" class="btn-group">
        <button
          v-if="isQuiz"
          class="btn btn-secondary"
          type="button"
          :disabled="run.busy.value"
          data-testid="skip"
          @click="submit(null)"
        >
          {{ t('practice.skip') }}
        </button>
        <button
          v-if="isSentence"
          class="btn btn-secondary"
          type="button"
          :disabled="answer === null"
          data-testid="reset"
          @click="answer = null"
        >
          {{ t('practice.reset') }}
        </button>
      </div>
      <div class="practice-footer-end">
        <button
          v-if="check"
          ref="nextButton"
          class="btn btn-primary"
          type="button"
          :disabled="run.busy.value"
          :aria-busy="run.busy.value"
          data-testid="continue"
          @click="next"
        >
          {{ isLast ? t('practice.seeResult') : t('practice.nextItem') }}
        </button>
        <button
          v-else-if="!isQuiz"
          class="btn btn-primary"
          type="button"
          :disabled="answer === null || run.busy.value"
          :aria-busy="run.busy.value"
          data-testid="check"
          @click="submit(answer)"
        >
          <IconGlyph name="check" :size="16" />{{ t('practice.check') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import ListenButtons from '@/components/learn/ListenButtons.vue'
import QuestionStage from '@/components/learn/QuestionStage.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { learnMessages } from '@/plugins/learnMessages'
import { practiceMessages } from '@/plugins/practiceMessages'
import type { AnswerCheck, PracticeItemAnswer, PracticeResult, PracticeSet, QuestionView } from '@/types/learn'

import PracticeFeedback from './PracticeFeedback.vue'
import PracticeProgress, { type PracticeDot } from './PracticeProgress.vue'
import { usePracticeRun } from './usePracticeRun'

/**
 * Practice sets answered one item at a time: quizzes (click an answer for instant feedback, with the in-a-row counter),
 * listening (listen, choose, check) and sentences (put the word tiles in order, check). The first answer to each item
 * counts; the finished run is graded on the server.
 */
const props = defineProps<{ set: PracticeSet }>()
const emit = defineEmits<{ finished: [result: PracticeResult] }>()

/** The server's rule (RewardRules): answers in a row that pay a quiz's one-time bonus, and the bonus. */
const QUIZ_STREAK_LENGTH = 3
const QUIZ_STREAK_BONUS = 5

const { t } = useLocalMessages({ practice: practiceMessages, learn: learnMessages })
const run = usePracticeRun(
  () => props.set,
  () => t('practice.saveFailed'),
)

const isQuiz = computed(() => props.set.type === 'QUIZZES')
const isListening = computed(() => props.set.type === 'LISTENING')
const isSentence = computed(() => props.set.type === 'DRAG_DROP')

const index = ref(0)
const answer = ref<string | null>(null)
const check = ref<AnswerCheck | null>(null)
const answers = ref<PracticeItemAnswer[]>([])
const allRight = ref(true)
const inARow = ref(0)
const nextButton = ref<HTMLButtonElement | null>(null)

const items = computed(() => props.set.items)
const item = computed(() => items.value[index.value] ?? null)
const isLast = computed(() => index.value >= items.value.length - 1)

const dots = computed<PracticeDot[]>(() =>
  items.value.map((_, position) => {
    if (position === index.value) return 'current'
    return position < index.value ? 'seen' : 'ahead'
  }),
)

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
    // Listening plays from the strip above the question.
    audioText: isQuiz.value ? current.hanzi : null,
    options: current.options,
    tiles: [],
  }
})

/**
 * XP is paid per finished set, not per answer: the bar names it only at the answer that earns it, the third in a row of
 * a quiz (its bonus) and the last of a run with every answer right (the set's XP). A set already done pays nothing new.
 */
const xpOfAnswer = computed(() => {
  if (!check.value?.correct || props.set.done) return 0
  const bonus = isQuiz.value && inARow.value === QUIZ_STREAK_LENGTH ? QUIZ_STREAK_BONUS : 0
  return bonus + (isLast.value && allRight.value ? props.set.xpReward : 0)
})

// A quiz checks as soon as an answer is clicked.
watch(answer, (value) => {
  if (isQuiz.value && value !== null && check.value === null) void submit(value)
})

async function submit(given: string | null): Promise<void> {
  const current = item.value
  if (!current || check.value || run.busy.value) return
  const verdict = await run.check(current.key, given)
  if (!verdict) {
    // A quiz answer is sent on click: clear it, so the same answer can be sent again.
    if (isQuiz.value) answer.value = null
    return
  }
  check.value = verdict
  answers.value.push({ itemKey: current.key, answer: given })
  inARow.value = verdict.correct ? inARow.value + 1 : 0
  if (!verdict.correct) allRight.value = false
  // The verdict is announced; Next takes the focus, so Enter moves on.
  await nextTick()
  nextButton.value?.focus()
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
</script>
