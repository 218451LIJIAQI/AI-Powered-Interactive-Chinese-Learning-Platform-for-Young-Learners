<template>
  <div class="focus-stage plan-check" :class="layoutClass">
    <StatePanel
      v-if="error && !question"
      heading-tag="h2"
      tone="error"
      icon="alert"
      :title="t('plan.check.loadError')"
      :message="error"
      spacious
      @retry="load"
    />
    <StatePanel
      v-else-if="!question"
      heading-tag="h2"
      tone="loading"
      :title="t('plan.check.loading')"
      :message="t('plan.check.loadingHint')"
      spacious
    />
    <template v-else>
      <p v-if="eyebrow" class="page-eyebrow">{{ eyebrow }}</p>
      <QuestionStage v-model="choice" :question="question" :locked="pending || verdict !== null" :check="verdict" />
    </template>

    <CheckBar
      v-if="question"
      :check="verdict"
      :can-check="choice !== null"
      :checking="pending"
      :hint="t('common.choiceKeysHint', { count: question.options.length })"
      :skip-label="t('plan.check.dontKnow')"
      :wrong-title="wrongTitle"
      :continue-label="t('plan.common.continue')"
      @check="answer(selectedOptionId)"
      @skip="answer(null)"
      @continue="next"
    />
    <p v-if="error && question" class="alert alert-danger" role="alert">{{ error }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import { levelCheckApi } from '@/api/learn'
import { getAuthSessionVersion } from '@/api/http'
import StatePanel from '@/components/common/StatePanel.vue'
import CheckBar from '@/components/learn/CheckBar.vue'
import QuestionStage from '@/components/learn/QuestionStage.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { planSetupMessages } from '@/plugins/planSetupMessages'
import { planSummaryMessages } from '@/plugins/planSummaryMessages'
import type { AnswerCheck, LevelCheckStep, MandarinLevel, QuestionView } from '@/types/learn'
import type { LevelCheckAnswer } from '@/types/plan'
import { resolveApiError } from '@/utils/apiError'
import { questionShortcut } from '@/utils/questionShortcut'

/**
 * The level check (setup step 4 and the retake from My learning plan): one question per skill. After an answer, and
 * after "I don't know yet" on any question, the right option is marked in place (dark, with a white tick; a wrong pick
 * keeps a cross) with "No problem!" or "Almost!" and why, until the server says it is done. Number keys pick an answer and
 * Enter checks or goes on. The server grades every answer and grades them all again when the plan is saved, so nothing
 * here decides the level. The idioms question is the stretch one.
 */
const props = defineProps<{
  startLevel: MandarinLevel
  /** A dialog is open over the check: the keys belong to it. */
  paused?: boolean
}>()

const emit = defineEmits<{
  /** The question on screen: how many are answered and the most there can be, for the page's progress bar. */
  progress: [answered: number, max: number]
  finished: [answers: LevelCheckAnswer[], result: NonNullable<LevelCheckStep['result']>]
}>()

const { t } = useLocalMessages({ plan: [planSummaryMessages, planSetupMessages] })

const answers = ref<LevelCheckAnswer[]>([])
const current = ref<LevelCheckStep | null>(null)
const after = ref<LevelCheckStep | null>(null)
const choice = ref<string | null>(null)
const pending = ref(false)
const error = ref<string | null>(null)
const sessionVersion = getAuthSessionVersion()
let active = true
let requestVersion = 0

function isCurrent(version: number): boolean {
  return active && version === requestVersion && sessionVersion === getAuthSessionVersion()
}

const question = computed<QuestionView | null>(() => {
  const next = current.value?.next
  if (!next) return null
  return {
    id: next.id,
    answerKind: 'CHOICE',
    type: null,
    prompt: next.prompt,
    hanzi: next.hanzi,
    pinyin: next.pinyin,
    meaning: null,
    audioText: next.audioText,
    options: next.options.map((option) => ({ text: option.text, pinyin: null })),
    tiles: [],
  }
})

/** "Question 2 of 8 · Listening · 听力": the bar shows the progress, the eyebrow says it once more in words. */
const eyebrow = computed(() => {
  const step = current.value
  const skill = step?.next?.skill
  if (!step || !skill) return ''
  const label = t(`plan.skillEyebrow.${skill}`)
  return t('plan.check.question', {
    current: step.answeredCount + 1,
    total: step.maxQuestions,
    skill: skill === 'IDIOMS' ? t('plan.check.stretch', { skill: label }) : label,
  })
})

/** Single characters sit on four tiles, short Chinese answers in two columns, everything else in rows. */
const layoutClass = computed(() => {
  const options = current.value?.next?.options ?? []
  if (!options.length || !options.every((option) => /^\p{Script=Han}+$/u.test(option.text))) return undefined
  return options.every((option) => [...option.text].length === 1) ? 'has-tiles' : 'has-pairs'
})

const selectedOptionId = computed(
  () => current.value?.next?.options.find((option) => option.text === choice.value)?.id ?? null,
)

/** "No problem!" after I don't know yet, "Tricky one!" after a missed stretch question, "Almost!" otherwise. */
const wrongTitle = computed(() => {
  if (after.value?.feedback?.skipped) return t('plan.check.noProblem')
  return current.value?.next?.skill === 'IDIOMS' ? t('plan.check.tricky') : t('plan.check.almost')
})

/** The server's feedback in the shape of the shared check bar: the right answer and why. */
const verdict = computed<AnswerCheck | null>(() => {
  const feedback = after.value?.feedback
  if (!feedback) return null
  return {
    correct: feedback.correct,
    correctAnswer: feedback.correctAnswer ?? '',
    pinyin: null,
    meaning: null,
    explanation: feedback.explanation,
  }
})

/**
 * Puts `step`'s question on screen, or ends the check when the server says it is done (also before the first question,
 * when none is published). The progress bar moves on with the next question, not while the answer to this one is shown.
 */
function show(step: LevelCheckStep): void {
  if (step.done && step.result) {
    emit('finished', answers.value, step.result)
    return
  }
  if (!step.next) {
    error.value = t('plan.check.loadError')
    return
  }
  current.value = step
  after.value = null
  choice.value = null
  emit('progress', step.answeredCount, step.maxQuestions)
}

async function load(): Promise<void> {
  const version = ++requestVersion
  error.value = null
  try {
    const step = await levelCheckApi.next(props.startLevel, answers.value)
    if (isCurrent(version)) show(step)
  } catch (reason) {
    if (isCurrent(version)) error.value = resolveApiError(reason, t('plan.check.loadError'))
  }
}

/** Checks the chosen option, or sends "I don't know yet" (`null`): either way the feedback comes back first. */
async function answer(optionId: number | null): Promise<void> {
  const next = current.value?.next
  if (!next || pending.value || after.value) return
  const version = ++requestVersion
  pending.value = true
  error.value = null
  const sent = [...answers.value, { questionId: next.id, optionId }]
  try {
    const step = await levelCheckApi.next(props.startLevel, sent)
    if (!isCurrent(version)) return
    after.value = step
    if (optionId === null) choice.value = null
    answers.value = sent
  } catch (reason) {
    if (isCurrent(version)) error.value = resolveApiError(reason, t('plan.check.loadError'))
  } finally {
    if (isCurrent(version)) pending.value = false
  }
}

function next(): void {
  if (after.value) show(after.value)
}

/** Number keys select an option; Enter checks it or continues after feedback. */
function onKeydown(event: KeyboardEvent): void {
  if (props.paused || pending.value || !question.value) return
  const shortcut = questionShortcut(event)
  if (shortcut === null) return
  if (shortcut === 'enter') {
    if (after.value) next()
    else if (choice.value !== null) void answer(selectedOptionId.value)
    return
  }
  if (after.value) return
  const option = question.value.options[shortcut]
  if (option) choice.value = option.text
}

onMounted(() => {
  void load()
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  active = false
  requestVersion += 1
  document.removeEventListener('keydown', onKeydown)
})
</script>
