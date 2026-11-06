<template>
  <div class="focus-stage plan-check" :class="layoutClass" data-testid="level-check">
    <StatePanel
      v-if="error && !current"
      tone="error"
      icon="alert"
      :title="t('plan.check.loadError')"
      :message="error"
      spacious
    >
      <template #actions>
        <button class="btn btn-primary" type="button" @click="load">{{ t('plan.common.retry') }}</button>
      </template>
    </StatePanel>
    <StatePanel
      v-else-if="!current"
      tone="loading"
      :title="t('plan.check.loading')"
      :message="t('plan.check.loadingHint')"
      spacious
    />
    <template v-else>
      <p v-if="eyebrow" class="page-eyebrow" data-testid="level-check-skill">{{ eyebrow }}</p>
      <QuestionStage v-model="choice" :question="question!" :locked="verdict !== null" :check="verdict" />
    </template>

    <CheckBar
      v-if="current"
      :check="verdict"
      :can-check="choice !== null"
      :checking="pending"
      :skip-label="t('plan.check.dontKnow')"
      :wrong-title="skipped ? t('plan.check.noProblem') : t('plan.check.almost')"
      :continue-label="t('plan.common.continue')"
      @check="answer(selectedOptionId)"
      @skip="answer(null)"
      @continue="next"
    />
    <p v-if="error && current" class="alert alert-danger" role="alert">{{ error }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { levelCheckApi } from '@/api/learn'
import StatePanel from '@/components/common/StatePanel.vue'
import CheckBar from '@/components/learn/CheckBar.vue'
import QuestionStage from '@/components/learn/QuestionStage.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { planMessages } from '@/plugins/planMessages'
import type { AnswerCheck, LevelCheckStep, MandarinLevel, QuestionView } from '@/types/learn'
import type { LevelCheckAnswer } from '@/types/plan'
import { resolveApiError } from '@/utils/apiError'

import { SKILL_EYEBROW_KEYS } from './planLabels'

/**
 * The level check (setup step 4 and the retake from My learning plan): one question per skill with "I don't know yet",
 * friendly feedback after each answer, until the server says it is done. The server grades every answer and grades them
 * all again when the plan is saved, so nothing here decides the level. The idioms question is the stretch one.
 */
const props = defineProps<{ startLevel: MandarinLevel }>()

const emit = defineEmits<{
  progress: [answered: number, max: number]
  finished: [answers: LevelCheckAnswer[], result: NonNullable<LevelCheckStep['result']>]
}>()

const { t } = useLocalMessages({ plan: planMessages })

const answers = ref<LevelCheckAnswer[]>([])
const current = ref<LevelCheckStep | null>(null)
const after = ref<LevelCheckStep | null>(null)
const choice = ref<string | null>(null)
const pending = ref(false)
const error = ref<string | null>(null)

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

const eyebrow = computed(() => {
  const skill = current.value?.next?.skill
  if (!skill) return ''
  const label = t(SKILL_EYEBROW_KEYS[skill])
  return skill === 'IDIOMS' ? t('plan.check.stretch', { skill: label }) : label
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

const skipped = computed(() => after.value?.feedback?.skipped ?? false)

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

async function load(): Promise<void> {
  error.value = null
  try {
    current.value = await levelCheckApi.next(props.startLevel, answers.value)
    emit('progress', current.value.answeredCount, current.value.maxQuestions)
  } catch (reason) {
    error.value = resolveApiError(reason, t('plan.check.loadError'))
  }
}

async function answer(optionId: number | null): Promise<void> {
  const next = current.value?.next
  if (!next || pending.value) return
  pending.value = true
  error.value = null
  const sent = [...answers.value, { questionId: next.id, optionId }]
  try {
    after.value = await levelCheckApi.next(props.startLevel, sent)
    answers.value = sent
  } catch (reason) {
    error.value = resolveApiError(reason, t('plan.check.loadError'))
  } finally {
    pending.value = false
  }
}

function next(): void {
  const step = after.value
  if (!step) return
  if (step.done && step.result) {
    emit('finished', answers.value, step.result)
    return
  }
  current.value = step
  after.value = null
  choice.value = null
  // The top bar moves on with the next question, not while the answer to this one is still shown.
  emit('progress', step.answeredCount, step.maxQuestions)
}

onMounted(load)
</script>
