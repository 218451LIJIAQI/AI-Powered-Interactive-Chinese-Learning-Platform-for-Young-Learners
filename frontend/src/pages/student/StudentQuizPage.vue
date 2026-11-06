<template>
  <div class="class-focus-page class-quiz-page">
    <FocusTopBar
      :label="topLabel"
      :close-label="t('classPlayer.quiz.leaveLabel')"
      :progress="topProgress"
      @close="requestLeave"
    />

    <!-- Result -->
    <div v-if="result" class="focus-stage class-quiz-result">
      <header class="class-focus-head" data-testid="quiz-result">
        <p class="page-eyebrow">{{ t('classPlayer.quiz.eyebrow', { lesson: result.quiz.lessonTitle }) }}</p>
        <h1 class="focus-headline">
          {{ t('classPlayer.result.scored', { correct: result.correctCount, total: result.questions.length }) }}
        </h1>
        <p class="class-focus-sub">
          {{
            justSubmitted
              ? t('classPlayer.result.submittedNow', { percent: result.bestPercent ?? result.percent })
              : t('classPlayer.result.submittedAt', {
                  when: formatDateTime(result.submittedAt),
                  percent: result.bestPercent ?? result.percent,
                })
          }}
        </p>
      </header>
      <DataNotice :error="actionError" :info="timeUpNotice ? t('classPlayer.quiz.timeUp') : null" />
      <QuizReview :result="result" :title="t('classPlayer.result.toReview', result.wrongCount)" />

      <FocusFooter>
        <template #start>
          <button
            type="button"
            class="btn btn-secondary btn-lg"
            :disabled="starting"
            :aria-busy="starting"
            @click="start"
          >
            {{ t('classPlayer.result.retakeTitle') }}
          </button>
        </template>
        <template v-if="resultNote" #default>{{ resultNote }}</template>
        <template #end>
          <RouterLink v-if="result.suggestion" class="btn btn-primary btn-lg" :to="suggestionRoute(result)">
            {{ t('classPlayer.result.practiseSkill', { skill: t(SKILL_KEYS[result.suggestion.skill]).toLowerCase() }) }}
          </RouterLink>
          <RouterLink v-else class="btn btn-primary btn-lg" :to="lessonRoute">
            {{ t('classPlayer.result.lessonStep') }}
          </RouterLink>
        </template>
      </FocusFooter>
    </div>

    <!-- An attempt in progress -->
    <div v-else-if="session && question" class="focus-stage">
      <h1 class="sr-only">{{ t('classPlayer.quiz.title', { title: session.quiz.title }) }}</h1>
      <div class="class-quiz-top">
        <p class="page-eyebrow">{{ t('classPlayer.quiz.eyebrow', { lesson: session.quiz.lessonTitle }) }}</p>
        <nav class="class-quiz-dots" :aria-label="t('classPlayer.quiz.questionNav')">
          <button
            v-for="(item, itemIndex) in session.questions"
            :key="item.id"
            type="button"
            class="class-quiz-dot"
            :class="{ 'is-current': itemIndex === index, 'is-answered': isAnswered(item.id) }"
            :aria-current="itemIndex === index ? 'step' : undefined"
            :aria-label="
              isAnswered(item.id)
                ? t('classPlayer.quiz.goToAnswered', { number: itemIndex + 1 })
                : t('classPlayer.quiz.goTo', { number: itemIndex + 1 })
            "
            @click="goTo(itemIndex)"
          >
            {{ itemIndex + 1 }}
          </button>
        </nav>
      </div>

      <DataNotice :error="actionError" />

      <div class="class-quiz-question">
        <h2 class="focus-title"><CjkText :text="questionParts.title" /></h2>
        <div class="class-player-row">
          <p class="class-focus-sub">
            <template v-if="questionParts.aside"><CjkText :text="questionParts.aside" />&nbsp;</template>
            {{ isTypedQuestion(question.type) ? t('classPlayer.quiz.typeAnswer') : t('classPlayer.quiz.chooseBest') }}
          </p>
          <span class="btn-group is-sm class-player-tools">
            <button v-if="question.audioUrl" type="button" class="btn btn-secondary btn-sm" @click="playAudio">
              <IconGlyph name="audio" :size="16" />{{ t('classPlayer.quiz.playAudio') }}
            </button>
            <button
              v-if="question.hasHint && session.quiz.hintsAvailable"
              type="button"
              class="btn btn-secondary btn-sm"
              :disabled="hintBusy"
              :aria-expanded="hintOpen"
              @click="toggleHint(question.id)"
            >
              {{ hintOpen ? t('classPlayer.quiz.hideHint') : t('classPlayer.quiz.showHint') }}
            </button>
          </span>
        </div>
        <p v-if="hintOpen && hints[question.id]" class="class-quiz-hint" role="status">
          <CjkText :text="t('classPlayer.quiz.hint', { hint: hints[question.id] })" />
        </p>
      </div>

      <img v-if="question.imageUrl" class="class-quiz-image" :src="question.imageUrl" alt="" />

      <div v-if="isTypedQuestion(question.type)" class="field">
        <label class="field-label" :for="`answer-${question.id}`">{{ t('classPlayer.quiz.answerLabel') }}</label>
        <input
          :id="`answer-${question.id}`"
          class="input cjk-text"
          :value="typed[question.id] ?? ''"
          maxlength="500"
          autocomplete="off"
          @input="typeAnswer(question.id, ($event.target as HTMLInputElement).value)"
          @blur="saveTyped(question.id)"
        />
      </div>
      <div v-else class="class-quiz-options" role="radiogroup" :aria-label="question.text">
        <button
          v-for="(option, optionIndex) in question.options"
          :key="option.id"
          type="button"
          role="radio"
          class="option-row"
          :aria-checked="chosen[question.id] === option.id"
          :data-testid="`quiz-option-${option.id}`"
          @click="choose(question.id, option.id)"
        >
          <span class="option-keycap" aria-hidden="true">{{ LETTERS[optionIndex] ?? optionIndex + 1 }}</span>
          <span class="class-quiz-option-copy">
            <span class="class-quiz-option-text"><CjkText :text="optionLines(option.text).main" /></span>
            <span v-if="optionLines(option.text).sub" class="type-caption">{{ optionLines(option.text).sub }}</span>
          </span>
        </button>
      </div>

      <FocusFooter>
        <template #start>
          <button type="button" class="btn btn-secondary btn-lg" :disabled="index === 0" @click="goTo(index - 1)">
            {{ t('classPlayer.quiz.previous') }}
          </button>
        </template>
        <template #default>
          <span role="status">
            {{
              t('classPlayer.quiz.progress', {
                answered: answeredCount,
                total: session.questions.length,
                save: savingCount ? t('classPlayer.quiz.saving') : t('classPlayer.quiz.autosave'),
              })
            }}
          </span>
        </template>
        <template #end>
          <button
            v-if="index < session.questions.length - 1"
            type="button"
            class="btn btn-primary btn-lg"
            @click="goTo(index + 1)"
          >
            {{ t('classPlayer.quiz.next') }}
          </button>
          <button
            v-else
            type="button"
            class="btn btn-primary btn-lg"
            :disabled="remaining > 0 || submitting || savingCount > 0"
            :aria-busy="submitting"
            data-testid="quiz-submit"
            @click="submit"
          >
            {{ remaining > 0 ? t('classPlayer.quiz.answerMore', remaining) : t('classPlayer.quiz.submit') }}
          </button>
        </template>
      </FocusFooter>
    </div>

    <div v-else class="focus-stage">
      <h1 class="sr-only">{{ t('classPlayer.quiz.topLabelUntimed') }}</h1>
      <StatePanel
        v-if="starting || loadingResult"
        tone="loading"
        spacious
        :title="t('classes.loading')"
        :message="t('classPlayer.quiz.loadingMessage')"
      />
      <StatePanel
        v-else
        tone="error"
        icon="alert"
        spacious
        :title="t('classPlayer.quiz.loadErrorTitle')"
        :message="actionError ?? ''"
      >
        <template #actions>
          <button class="btn btn-secondary" type="button" @click="start">{{ t('classes.retry') }}</button>
          <RouterLink class="btn btn-primary" :to="{ name: 'student-classes' }">{{ t('classes.classes') }}</RouterLink>
        </template>
      </StatePanel>
    </div>

    <ConfirmDialog
      :open="leaveOpen"
      :title="t('classPlayer.quiz.leaveTitle')"
      :message="t('classPlayer.quiz.leaveMessage')"
      tone="leave"
      :confirm-label="t('classes.leave')"
      :cancel-label="t('classes.keepGoing')"
      @confirm="leave"
      @cancel="leaveOpen = false"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { activitySlug, studentClassesApi } from '@/api/classes'
import QuizReview from '@/components/classes/QuizReview.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import FocusFooter from '@/components/layout/FocusFooter.vue'
import FocusTopBar from '@/components/layout/FocusTopBar.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classPlayerMessages } from '@/plugins/classPlayerMessages'
import { classesMessages } from '@/plugins/classesMessages'
import type { QuizResult, QuizSession } from '@/types/classes'
import { apiErrorStatus, resolveApiError } from '@/utils/apiError'
import { SKILL_KEYS, isTypedQuestion } from '@/utils/classes'
import { formatDateTime, ratioPercent } from '@/utils/format'
import '@/styles/classes.css'

const { t } = useLocalMessages({ classPlayer: classPlayerMessages, classes: classesMessages })
const route = useRoute()
const router = useRouter()
const { actionError, showError, clearFeedback } = useActionFeedback()

const LETTERS = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H']
const TYPING_SAVE_DELAY_MS = 700

const quizId = computed(() => Number(route.params.quizId))
const session = ref<QuizSession | null>(null)
const result = ref<QuizResult | null>(null)
const loadingResult = ref(false)
const timeUpNotice = ref(false)
const justSubmitted = ref(false)

const lessonRoute = computed(() => {
  const quiz = result.value?.quiz ?? session.value?.quiz
  return quiz
    ? { name: 'student-class-lesson', params: { classId: quiz.classId, lessonId: quiz.lessonId } }
    : { name: 'student-classes' }
})

/** The activity the result suggests practising, or the lesson when it names none. */
function suggestionRoute(current: QuizResult) {
  const activity = current.suggestion?.activity
  if (!activity) return lessonRoute.value
  return {
    name: 'student-class-activity',
    params: {
      classId: current.quiz.classId,
      lessonId: current.quiz.lessonId,
      activityType: activitySlug(activity.type),
      activityId: activity.id,
    },
  }
}

// ----- the attempt -----

const index = ref(0)
const chosen = ref<Record<number, number>>({})
const typed = ref<Record<number, string>>({})
const savedTyped = ref<Record<number, string>>({})
const hints = ref<Record<number, string>>({})
const hintOpen = ref(false)
const savingCount = ref(0)
const starting = ref(false)
const submitting = ref(false)
const hintBusy = ref(false)
const leaveOpen = ref(false)
const typingTimers = new Map<number, ReturnType<typeof setTimeout>>()
let audio: HTMLAudioElement | null = null

const question = computed(() => session.value?.questions[index.value] ?? null)
const answeredCount = computed(() => session.value?.questions.filter((item) => isAnswered(item.id)).length ?? 0)
const remaining = computed(() => (session.value?.questions.length ?? 0) - answeredCount.value)

/** "Reply to “你叫什么名字？” (“What's your name?”)" reads as the title, then its gloss beside the instruction. */
const questionParts = computed(() => {
  const text = question.value?.text ?? ''
  const match = /^(.+?)\s*\(([^()]+)\)$/.exec(text)
  return match ? { title: match[1]!, aside: match[2]! } : { title: text, aside: '' }
})

/** An option can hold its pinyin on a second line. */
function optionLines(text: string): { main: string; sub: string } {
  const [main = '', ...rest] = text.split('\n')
  return { main, sub: rest.join(' ') }
}

function isAnswered(questionId: number): boolean {
  return chosen.value[questionId] !== undefined || Boolean(savedTyped.value[questionId]?.trim())
}

function stopTimers(): void {
  typingTimers.forEach((timer) => clearTimeout(timer))
  typingTimers.clear()
  audio?.pause()
  audio = null
}
onBeforeUnmount(stopTimers)

function begin(next: QuizSession): void {
  stopTimers()
  session.value = next
  result.value = null
  timeUpNotice.value = false
  justSubmitted.value = false
  index.value = 0
  chosen.value = {}
  typed.value = {}
  savedTyped.value = {}
  hints.value = {}
  hintOpen.value = false
  for (const answer of next.answers) {
    if (answer.optionId !== null) chosen.value[answer.questionId] = answer.optionId
    if (
      answer.answerText !== null &&
      isTypedQuestion(next.questions.find((q) => q.id === answer.questionId)?.type ?? '')
    ) {
      typed.value[answer.questionId] = answer.answerText
      savedTyped.value[answer.questionId] = answer.answerText
    }
  }
}

async function start(): Promise<void> {
  starting.value = true
  clearFeedback()
  try {
    begin(await studentClassesApi.startQuiz(quizId.value))
    if (route.query.attempt) void router.replace({ query: {} })
  } catch (reason) {
    showError(resolveApiError(reason, t('classPlayer.quiz.loadErrorTitle')))
  } finally {
    starting.value = false
  }
}

function goTo(next: number): void {
  if (!session.value) return
  const current = question.value
  if (current && isTypedQuestion(current.type)) void saveTyped(current.id)
  index.value = Math.min(Math.max(0, next), session.value.questions.length - 1)
  hintOpen.value = false
}

/** An answer after a quiz's time limit makes the server submit the attempt: then the result is shown. */
async function handleSaveFailure(reason: unknown): Promise<void> {
  if (apiErrorStatus(reason) === 409 && session.value) {
    await submitAttempt(true)
    return
  }
  showError(resolveApiError(reason, t('classPlayer.quiz.saveError')))
}

async function choose(questionId: number, optionId: number): Promise<void> {
  if (!session.value) return
  const previous = chosen.value[questionId]
  chosen.value = { ...chosen.value, [questionId]: optionId }
  savingCount.value++
  try {
    await studentClassesApi.saveAnswer(session.value.attemptId, { questionId, optionId })
  } catch (reason) {
    const next = { ...chosen.value }
    if (previous === undefined) delete next[questionId]
    else next[questionId] = previous
    chosen.value = next
    await handleSaveFailure(reason)
  } finally {
    savingCount.value--
  }
}

function typeAnswer(questionId: number, value: string): void {
  typed.value = { ...typed.value, [questionId]: value }
  const timer = typingTimers.get(questionId)
  if (timer) clearTimeout(timer)
  typingTimers.set(
    questionId,
    setTimeout(() => void saveTyped(questionId), TYPING_SAVE_DELAY_MS),
  )
}

async function saveTyped(questionId: number): Promise<void> {
  const timer = typingTimers.get(questionId)
  if (timer) clearTimeout(timer)
  typingTimers.delete(questionId)
  const value = (typed.value[questionId] ?? '').trim()
  if (!session.value || !value || value === savedTyped.value[questionId]) return
  savingCount.value++
  try {
    await studentClassesApi.saveAnswer(session.value.attemptId, { questionId, answerText: value })
    savedTyped.value = { ...savedTyped.value, [questionId]: value }
  } catch (reason) {
    await handleSaveFailure(reason)
  } finally {
    savingCount.value--
  }
}

/** The first Show hint fetches it (the server counts a used hint); after that it only opens and closes. */
async function toggleHint(questionId: number): Promise<void> {
  if (!session.value) return
  if (hintOpen.value) {
    hintOpen.value = false
    return
  }
  if (!hints.value[questionId]) {
    hintBusy.value = true
    try {
      const hint = await studentClassesApi.hint(session.value.attemptId, questionId)
      hints.value = { ...hints.value, [questionId]: hint.hint }
    } catch (reason) {
      await handleSaveFailure(reason)
      return
    } finally {
      hintBusy.value = false
    }
  }
  hintOpen.value = true
}

function playAudio(): void {
  const url = question.value?.audioUrl
  if (!url) return
  audio?.pause()
  audio = new Audio(url)
  void audio.play().catch(() => showError(t('classPlayer.quiz.audioError')))
}

async function submit(): Promise<void> {
  const current = question.value
  if (current && isTypedQuestion(current.type)) await saveTyped(current.id)
  clearFeedback()
  await submitAttempt(false)
}

/** Submits the attempt and shows its result, with the "time is up" notice when `timeUp`. */
async function submitAttempt(timeUp: boolean): Promise<void> {
  if (!session.value || submitting.value) return
  submitting.value = true
  try {
    const next = await studentClassesApi.submitQuiz(session.value.attemptId)
    stopTimers()
    result.value = next
    session.value = null
    timeUpNotice.value = timeUp
    justSubmitted.value = true
    // A reload shows this result again instead of starting a new attempt.
    void router.replace({ query: { attempt: next.attemptId } })
  } catch (reason) {
    showError(resolveApiError(reason, t('classPlayer.quiz.submitError')))
  } finally {
    submitting.value = false
  }
}

// ----- footer and top bar -----

const resultNote = computed(() => {
  const current = result.value
  if (!current) return ''
  if (current.activitiesLeft > 0) return t('classPlayer.result.activitiesLeft', current.activitiesLeft)
  return current.lessonStatus === 'COMPLETED' || current.lessonStatus === 'MASTERED'
    ? t('classPlayer.result.lessonCompleted')
    : ''
})

const topLabel = computed(() => {
  if (result.value) return t('classPlayer.quiz.topLabelResult')
  if (session.value) {
    return t('classPlayer.quiz.question', { number: index.value + 1, total: session.value.questions.length })
  }
  return t('classPlayer.quiz.topLabelUntimed')
})

const topProgress = computed(() => {
  if (result.value) return 100
  if (!session.value) return null
  return ratioPercent(answeredCount.value, session.value.questions.length)
})

function requestLeave(): void {
  if (session.value) leaveOpen.value = true
  else void router.push(lessonRoute.value)
}

function leave(): void {
  leaveOpen.value = false
  stopTimers()
  void router.push(lessonRoute.value)
}

// ----- opening the page: a submitted attempt (?attempt=, so a reload keeps the result) or straight into the quiz -----

async function showAttempt(attemptId: number): Promise<void> {
  if (result.value?.attemptId === attemptId) return
  loadingResult.value = true
  clearFeedback()
  try {
    result.value = await studentClassesApi.quizResult(attemptId)
    session.value = null
  } catch (reason) {
    showError(resolveApiError(reason, t('classPlayer.quiz.loadErrorTitle')))
  } finally {
    loadingResult.value = false
  }
}

watch(
  () => route.query.attempt,
  (attempt) => {
    if (typeof attempt === 'string' && /^\d+$/.test(attempt)) void showAttempt(Number(attempt))
    else if (!session.value && !starting.value) void start()
  },
  { immediate: true },
)
</script>
