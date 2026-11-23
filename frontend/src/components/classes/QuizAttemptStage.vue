<template>
  <div class="focus-stage">
    <h1 class="sr-only">{{ t('classPlayer.quiz.title', { lesson: session.quiz.lessonTitle }) }}</h1>
    <DataNotice :error="externalError ?? actionError" />

    <template v-if="question">
      <div class="class-quiz-question">
        <p class="page-eyebrow" data-testid="quiz-position">
          {{ t('classPlayer.quiz.position', { number: index + 1, total: session.questions.length }) }}
        </p>
        <h2 ref="questionHeading" class="focus-title" tabindex="-1"><CjkText :text="questionParts.title" /></h2>
        <div v-if="questionParts.aside || instruction || question.audioUrl" class="class-player-row">
          <p class="class-focus-sub">
            <template v-if="questionParts.aside"><CjkText :text="questionParts.aside" />&nbsp;</template>
            {{ instruction }}
          </p>
          <button v-if="question.audioUrl" type="button" class="btn btn-secondary btn-sm" @click="playAudio">
            <IconGlyph name="audio" :size="16" />{{ t('classPlayer.quiz.playAudio') }}
          </button>
        </div>
      </div>

      <img
        v-if="question.imageUrl"
        class="class-quiz-image"
        :src="question.imageUrl"
        :alt="t('classPlayer.quiz.imageAlt')"
      />

      <div v-if="isTypedQuestion(question.type)" class="field">
        <label class="field-label" :for="`answer-${question.id}`">{{ t('classPlayer.quiz.answerLabel') }}</label>
        <input
          :id="`answer-${question.id}`"
          class="input cjk-text"
          :value="typed[question.id] ?? ''"
          maxlength="1000"
          autocomplete="off"
          :disabled="submitting || navigating || leaving"
          @input="typeAnswer(question.id, ($event.target as HTMLInputElement).value)"
          @blur="saveTyped(question.id)"
        />
      </div>
      <div v-else class="class-quiz-options" role="radiogroup" :aria-label="question.text">
        <button
          v-for="(option, optionIndex) in optionDisplay"
          :key="option.id"
          type="button"
          role="radio"
          class="option-row"
          :aria-checked="chosen[question.id] === option.id"
          :data-testid="`quiz-option-${option.id}`"
          :disabled="savingOption || navigating || submitting || leaving"
          @click="choose(question.id, option.id)"
        >
          <span class="option-keycap" aria-hidden="true">{{ optionIndex + 1 }}</span>
          <span class="class-quiz-option-copy">
            <span class="class-quiz-option-text"><CjkText :text="option.main" /></span>
            <span v-if="option.sub" class="type-caption">{{ option.sub }}</span>
          </span>
        </button>
      </div>

      <FocusFooter>
        <template #start>
          <button
            type="button"
            class="btn btn-secondary btn-lg"
            :disabled="index === 0 || savingOption || navigating || submitting || leaving"
            @click="goTo(index - 1)"
          >
            {{ t('classPlayer.quiz.previous') }}
          </button>
        </template>
        <!-- A submit that failed says so here, beside the button that tries again (S11.26). -->
        <template v-if="submitError" #default>
          <span class="focus-footer-error" role="alert" data-testid="quiz-submit-error">{{ submitError }}</span>
        </template>
        <template v-else-if="question.hasHint && (offer.offered.value || hints[question.id])" #middle>
          <ClassHint
            :hint-id="question.id"
            :offered="offer.offered.value"
            :text="hints[question.id] ?? null"
            :loading="hintLoadingIds.has(question.id)"
            :error="hintError"
            :context="hintContext"
            :reported-ids="reportedHintIds"
            @show="showHint(question.id)"
            @reported="onHintReported"
          />
        </template>
        <template v-else-if="isLast && remaining === 0" #default>
          <span role="status">{{ t('classPlayer.quiz.allAnswered') }}</span>
        </template>
        <template #end>
          <!-- Each question is answered before the next one: Next waits for the answer (and its save). -->
          <button
            v-if="!isLast"
            type="button"
            class="btn btn-primary btn-lg"
            :disabled="!canGoOn"
            data-testid="quiz-next"
            @click="goTo(index + 1)"
          >
            {{ t('classPlayer.quiz.next') }}
          </button>
          <button
            v-else
            type="button"
            class="btn btn-primary btn-lg"
            :disabled="remaining > 0 || submitting || savingOption || navigating || leaving"
            :aria-busy="submitting"
            data-testid="quiz-submit"
            @click="submit"
          >
            {{ t('classPlayer.quiz.submit') }}
          </button>
        </template>
      </FocusFooter>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'

import { studentClassesApi } from '@/api/classes'
import { getAuthSessionVersion } from '@/api/http'
import ClassHint from '@/components/classes/ClassHint.vue'
import { useQuizAttempt } from '@/components/classes/useQuizAttempt'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import FocusFooter from '@/components/layout/FocusFooter.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useHintOffer } from '@/composables/useHintOffer'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classPlayerMessages } from '@/plugins/classPlayerMessages'
import type { QuizResult, QuizSession } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { isTypedQuestion } from '@/utils/classes'
import { ratioPercent } from '@/utils/format'
import { questionShortcut } from '@/utils/questionShortcut'

/** A question that already says what to do ("Listen, then choose the phrase you hear.") needs no second line. */
const SAYS_WHAT_TO_DO = /\b(choose|pick|select|type|write)\b/i

const props = defineProps<{
  session: QuizSession
  account: number
  leaveOpen: boolean
  leaving: boolean
  externalError: string | null
}>()

const emit = defineEmits<{
  progress: [percent: number]
  submitting: [busy: boolean]
  submitted: [result: QuizResult]
  toast: [message: string]
  clearExternalError: []
}>()

const { t } = useLocalMessages({ classPlayer: classPlayerMessages })

const index = ref(0)
const hints = ref<Record<number, string>>({})
const navigating = ref(false)
const submitting = ref(false)
const hintLoadingIds = reactive(new Set<number>())
const reportedHintIds = reactive(new Set<number>())
const hintError = ref<string | null>(null)
const actionError = ref<string | null>(null)
const submitError = ref<string | null>(null)
let active = true
let audio: HTMLAudioElement | null = null

const ownsAttempt = (): boolean => active && props.account === getAuthSessionVersion()

const question = computed(() => props.session.questions[index.value] ?? null)
const isLast = computed(() => index.value >= props.session.questions.length - 1)
const questionHeading = ref<HTMLHeadingElement | null>(null)

// When Next becomes disabled or swaps to Submit on question transition, focus drops to document.body; move it to the new heading.
watch(
  question,
  (next, previous) => {
    if (next && previous && (!document.activeElement || document.activeElement === document.body)) {
      questionHeading.value?.focus()
    }
  },
  { flush: 'post' },
)

const {
  chosen,
  typed,
  savedTyped,
  savingOption,
  isAnswered,
  answeredCount,
  remaining,
  stopTimers: stopTypingTimers,
  choose,
  typeAnswer,
  saveTyped,
  flushTypedAnswers,
} = useQuizAttempt({
  session: props.session,
  locked: computed(() => navigating.value || submitting.value || props.leaving),
  leaving: computed(() => props.leaving),
  ownsAttempt,
  onPoke: () => offer.poke(),
  onClearSubmitError: () => {
    submitError.value = null
  },
  onActionError: (message) => {
    actionError.value = message
    if (message === null) emit('clearExternalError')
  },
  saveErrorMessage: () => t('classPlayer.quiz.saveError'),
})

watch(
  answeredCount,
  (answered) => {
    emit('progress', ratioPercent(answered, props.session.questions.length))
  },
  { immediate: true },
)

const canGoOn = computed(() => {
  const current = question.value
  if (!current || savingOption.value || navigating.value || submitting.value || props.leaving) return false
  return isAnswered(current.id)
})

const questionParts = computed(() => {
  const text = question.value?.text ?? ''
  const match = /^(.+?)\s*\(([^()]+)\)$/.exec(text)
  return match ? { title: match[1]!, aside: match[2]! } : { title: text, aside: '' }
})

const instruction = computed(() => {
  const current = question.value
  if (!current) return ''
  if (isTypedQuestion(current.type)) return t('classPlayer.quiz.typeAnswer')
  return SAYS_WHAT_TO_DO.test(questionParts.value.title) ? '' : t('classPlayer.quiz.chooseBest')
})

const optionDisplay = computed(() =>
  (question.value?.options ?? []).map(({ id, text }) => {
    const [main = '', ...rest] = text.split('\n')
    return { id, main, sub: rest.join(' ') }
  }),
)

const offer = useHintOffer({
  itemKey: computed(() => question.value?.id ?? null),
  open: computed(() => {
    const current = question.value
    return Boolean(current?.hasHint && !isAnswered(current.id) && !hints.value[current.id] && !submitting.value)
  }),
})

const hintContext = computed(() => `${props.session.quiz.lessonTitle} · ${question.value?.text ?? ''}`)

function stopAudio(): void {
  const clip = audio
  audio = null
  clip?.pause()
}

function stopTimersAndAudio(): void {
  stopTypingTimers()
  stopAudio()
}

watch(
  () => props.leaving,
  (busy) => {
    if (busy) {
      stopTimersAndAudio()
      actionError.value = null
    }
  },
  { flush: 'sync' },
)

async function goTo(next: number): Promise<void> {
  if (
    submitting.value ||
    savingOption.value ||
    navigating.value ||
    props.leaving ||
    (next > index.value && !canGoOn.value)
  ) {
    return
  }
  const current = question.value
  navigating.value = true
  try {
    if (current && isTypedQuestion(current.type)) {
      if (!(await saveTyped(current.id)) || !ownsAttempt() || question.value !== current) return
      const draft = typed.value[current.id]?.trim() ?? ''
      if (draft !== savedTyped.value[current.id] || (next > index.value && !draft)) return
    }
    if (!ownsAttempt()) return
    stopAudio()
    index.value = Math.min(Math.max(0, next), props.session.questions.length - 1)
    actionError.value = null
    emit('clearExternalError')
    hintError.value = null
    submitError.value = null
  } finally {
    if (ownsAttempt()) navigating.value = false
  }
}

async function showHint(questionId: number): Promise<void> {
  if (hintLoadingIds.has(questionId) || submitting.value || props.leaving || hints.value[questionId]) return
  hintLoadingIds.add(questionId)
  hintError.value = null
  try {
    const hint = await studentClassesApi.hint(props.session.attemptId, questionId)
    if (!ownsAttempt() || props.leaving) return
    hints.value = { ...hints.value, [questionId]: hint.hint }
  } catch (reason) {
    if (ownsAttempt() && !props.leaving && question.value?.id === questionId) {
      hintError.value = resolveApiError(reason, t('classPlayer.hint.error'))
    }
  } finally {
    if (ownsAttempt()) hintLoadingIds.delete(questionId)
  }
}

function onHintReported(hintId: number): void {
  reportedHintIds.add(hintId)
  emit('toast', t('aiSafety.reportSent'))
}

function playAudio(): void {
  const url = question.value?.audioUrl
  if (!url) return
  if (actionError.value === t('classPlayer.quiz.audioError')) actionError.value = null
  emit('clearExternalError')
  stopAudio()
  const clip = new Audio(url)
  audio = clip
  void clip.play().catch(() => {
    if (audio === clip) actionError.value = t('classPlayer.quiz.audioError')
  })
}

async function submit(): Promise<void> {
  if (
    submitting.value ||
    props.leaving ||
    savingOption.value ||
    navigating.value ||
    remaining.value > 0 ||
    !ownsAttempt()
  ) {
    return
  }
  submitting.value = true
  emit('submitting', true)
  submitError.value = null
  try {
    if (!(await flushTypedAnswers()) || !ownsAttempt()) return
    actionError.value = null
    emit('clearExternalError')
    const next = await studentClassesApi.submitQuiz(props.session.attemptId)
    if (!ownsAttempt()) return
    stopTimersAndAudio()
    emit('submitted', next)
  } catch (reason) {
    if (ownsAttempt()) submitError.value = resolveApiError(reason, t('classPlayer.quiz.submitError'))
  } finally {
    if (ownsAttempt()) {
      submitting.value = false
      emit('submitting', false)
    }
  }
}

// Keyboard: 1–9 picks an answer, Enter goes on (Next, or Submit once every answer is in).

function onKeydown(event: KeyboardEvent): void {
  const current = question.value
  if (!current || props.leaveOpen) return
  const action = questionShortcut(event)
  if (typeof action === 'number' && !isTypedQuestion(current.type)) {
    const option = current.options[action]
    if (!option) return
    event.preventDefault()
    void choose(current.id, option.id)
  } else if (action === 'enter') {
    event.preventDefault()
    if (isLast.value) void submit()
    else void goTo(index.value + 1)
  }
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => {
  active = false
  document.removeEventListener('keydown', onKeydown)
  stopTimersAndAudio()
  if (submitting.value) emit('submitting', false)
})
</script>
