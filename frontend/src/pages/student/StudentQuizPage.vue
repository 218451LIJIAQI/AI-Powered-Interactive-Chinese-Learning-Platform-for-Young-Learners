<template>
  <div class="class-focus-page">
    <FocusTopBar
      :label="topLabel"
      :close-label="t('classPlayer.quiz.leaveLabel')"
      :progress="topProgress"
      @close="requestLeave"
    />

    <div class="class-focus-main page-stack" :class="{ 'is-wide': result }">
      <!-- Result -->
      <template v-if="result">
        <header class="class-focus-header">
          <span class="page-eyebrow">{{ t('classPlayer.result.eyebrow', { lesson: result.quiz.lessonTitle }) }}</span>
          <h1 class="class-focus-title">{{ t('classPlayer.quiz.title', { title: result.quiz.title }) }}</h1>
        </header>
        <DataNotice :info="timeUpNotice ? t('classPlayer.quiz.timeUp') : null" />

        <div class="quiz-result-layout">
          <section class="quiz-score" aria-labelledby="quiz-score-title" data-testid="quiz-result">
            <h2 id="quiz-score-title" class="page-eyebrow">{{ t('classPlayer.result.yourResult') }}</h2>
            <dl class="quiz-score-figures">
              <div class="quiz-score-figure is-main">
                <dt>{{ t('classPlayer.result.percentage') }}</dt>
                <dd class="stat-figure">{{ result.percent }}%</dd>
              </div>
              <div class="quiz-score-figure">
                <dt>{{ t('classPlayer.result.score') }}</dt>
                <dd>{{ t('classPlayer.result.marks', { score: result.score, max: result.maxScore }) }}</dd>
              </div>
              <div class="quiz-score-figure">
                <dt>{{ t('classPlayer.result.best') }}</dt>
                <dd>{{ result.bestPercent ?? result.percent }}%</dd>
              </div>
            </dl>
            <p class="type-caption">
              {{
                t('classPlayer.result.submitted', {
                  when: formatDateTime(result.submittedAt),
                  hints: t('classPlayer.result.hintsUsed', result.hintsUsed),
                })
              }}
            </p>
            <p class="callout" :class="{ 'is-success': result.lessonStatus === 'MASTERED' }">
              <IconGlyph :name="result.lessonStatus === 'MASTERED' ? 'check' : 'target'" :size="16" />
              <span>
                {{ masteryText }}
                <span v-if="result.lessonStatus !== 'MASTERED'" class="quiz-score-rule">
                  <template v-if="result.activitiesLeft">
                    {{ t('classPlayer.result.activitiesLeft', result.activitiesLeft) }} ·
                  </template>
                  {{ t('classPlayer.result.masteryRule') }}
                </span>
              </span>
            </p>
          </section>

          <aside class="quiz-result-side" aria-labelledby="quiz-next-title">
            <h2 id="quiz-next-title" class="quiz-section-title">{{ t('classPlayer.result.nextStep') }}</h2>
            <ol class="quiz-next-steps">
              <li v-if="result.suggestion" class="quiz-next-step">
                <span class="page-eyebrow student-classes-pigment">{{ t('classPlayer.result.practise') }}</span>
                <strong>{{
                  t('classPlayer.result.practiseSkill', { skill: t(SKILL_KEYS[result.suggestion.skill]) })
                }}</strong>
                <p class="type-caption">
                  {{
                    result.suggestion.activity
                      ? t('classPlayer.result.practiseText', {
                          skill: t(SKILL_KEYS[result.suggestion.skill]),
                          activity: result.suggestion.activity.title,
                        })
                      : t('classPlayer.result.practiseNoActivity', { skill: t(SKILL_KEYS[result.suggestion.skill]) })
                  }}
                </p>
                <RouterLink
                  class="btn btn-primary btn-sm"
                  :to="
                    result.suggestion.activity
                      ? activityRoute(result.suggestion.activity.type, result.suggestion.activity.id)
                      : lessonRoute
                  "
                >
                  <IconGlyph name="target" :size="16" />
                  {{ t('classPlayer.result.practiseSkill', { skill: t(SKILL_KEYS[result.suggestion.skill]) }) }}
                </RouterLink>
              </li>
              <li class="quiz-next-step">
                <span class="page-eyebrow student-classes-pigment">{{ t('classPlayer.result.retake') }}</span>
                <strong>{{ t('classPlayer.result.retakeTitle') }}</strong>
                <p class="type-caption">{{ t('classPlayer.result.retakeText') }}</p>
                <button
                  type="button"
                  class="btn btn-sm"
                  :class="result.suggestion ? 'btn-secondary' : 'btn-primary'"
                  :disabled="starting"
                  :aria-busy="starting"
                  @click="start"
                >
                  <IconGlyph name="retry" :size="16" />{{ t('classPlayer.result.retakeTitle') }}
                </button>
              </li>
            </ol>
            <RouterLink class="btn btn-ghost btn-block" :to="lessonRoute">
              <IconGlyph name="chevronLeft" :size="16" />{{ t('classPlayer.result.lessonStep') }}
            </RouterLink>
          </aside>

          <section v-if="result.feedback.length" class="quiz-feedback" aria-labelledby="quiz-feedback-title">
            <h2 id="quiz-feedback-title" class="quiz-section-title">{{ t('classPlayer.result.feedback') }}</h2>
            <ul class="quiz-feedback-list">
              <li v-for="item in result.feedback" :key="item.id" class="quiz-feedback-item">
                <PortraitAvatar :size="40" :name="item.teacherName" />
                <figure class="quiz-feedback-note">
                  <blockquote>{{ item.message }}</blockquote>
                  <figcaption class="type-caption">
                    {{ item.teacherName }} · {{ formatDateTime(item.createdAt) }}
                  </figcaption>
                </figure>
              </li>
            </ul>
          </section>

          <section class="quiz-review" aria-labelledby="quiz-review-title">
            <div class="quiz-review-head">
              <div>
                <h2 id="quiz-review-title" class="quiz-section-title">{{ t('classPlayer.result.review') }}</h2>
                <p class="type-caption">
                  {{ t('classPlayer.result.reviewFacts', { wrong: result.wrongCount, correct: result.correctCount }) }}
                </p>
              </div>
              <label class="choice">
                <input v-model="showAllQuestions" type="checkbox" />
                {{ t('classPlayer.result.showAll') }}
              </label>
            </div>
            <p v-if="!reviewed.length" class="callout is-success is-compact">
              <IconGlyph name="check" :size="16" /><span>{{ t('classPlayer.result.allCorrect') }}</span>
            </p>
            <ol class="quiz-review-list">
              <li
                v-for="reviewedQuestion in reviewed"
                :key="reviewedQuestion.questionId"
                class="quiz-review-item"
                :class="reviewedQuestion.correct ? 'is-correct' : 'is-wrong'"
              >
                <div class="quiz-review-item-head">
                  <span class="quiz-review-number" aria-hidden="true">{{ reviewedQuestion.number }}</span>
                  <strong class="quiz-review-question">
                    <span class="sr-only">{{ reviewedQuestion.number }}. </span>
                    <CjkText :text="reviewedQuestion.text" />
                  </strong>
                  <StatusPill
                    :label="
                      reviewedQuestion.correct ? t('classPlayer.result.correct') : t('classPlayer.result.incorrect')
                    "
                    :tone="reviewedQuestion.correct ? 'success' : 'danger'"
                    :icon="reviewedQuestion.correct ? 'check' : 'close'"
                  />
                </div>
                <dl class="quiz-review-answers">
                  <div>
                    <dt>{{ t('classPlayer.result.yourAnswer') }}</dt>
                    <dd>
                      <CjkText :text="reviewedQuestion.yourAnswer || t('classPlayer.result.noAnswer')" />
                      <span v-if="reviewedQuestion.hintUsed" class="tag">{{
                        t('classPlayer.result.hintUsedNote')
                      }}</span>
                    </dd>
                  </div>
                  <div v-if="!reviewedQuestion.correct && reviewedQuestion.correctAnswer" class="is-correct">
                    <dt>{{ t('classPlayer.result.correctAnswer') }}</dt>
                    <dd><CjkText :text="reviewedQuestion.correctAnswer" /></dd>
                  </div>
                </dl>
                <p v-if="reviewedQuestion.explanation" class="quiz-review-explanation">
                  <strong>{{ t('classPlayer.result.explanation') }}</strong> ·
                  <CjkText :text="reviewedQuestion.explanation" />
                </p>
                <RouterLink
                  v-if="!reviewedQuestion.correct"
                  class="btn btn-link"
                  :to="{
                    name: 'student-ai',
                    params: { tab: 'ask' },
                    query: {
                      q: t('classPlayer.result.askQuestion', {
                        question: reviewedQuestion.text,
                        answer: reviewedQuestion.yourAnswer ?? '',
                        correct: reviewedQuestion.correctAnswer ?? '',
                      }),
                    },
                  }"
                >
                  <IconGlyph name="ai" :size="16" />{{ t('classPlayer.result.askSparkBot') }}
                </RouterLink>
              </li>
            </ol>
          </section>
        </div>
      </template>

      <template v-else-if="currentQuiz">
        <header class="class-focus-header">
          <span class="page-eyebrow">
            {{ t('classPlayer.quiz.eyebrow', { classTitle: currentQuiz.classTitle, lesson: currentQuiz.lessonTitle }) }}
          </span>
          <h1 class="class-focus-title">{{ t('classPlayer.quiz.title', { title: currentQuiz.title }) }}</h1>
          <p class="type-caption">{{ quizFacts(currentQuiz) }}</p>
        </header>
        <DataNotice :error="actionError" />

        <!-- An attempt in progress -->
        <template v-if="session">
          <section v-if="question" class="quiz-question-panel">
            <div class="quiz-question-top">
              <strong class="quiz-question-count">{{
                t('classPlayer.quiz.question', { number: index + 1, total: session.questions.length })
              }}</strong>
              <nav class="quiz-question-dots" :aria-label="t('classPlayer.quiz.questionNav')">
                <button
                  v-for="(item, itemIndex) in session.questions"
                  :key="item.id"
                  type="button"
                  class="quiz-question-dot"
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

            <div class="quiz-question-stage">
              <h2 class="quiz-question-text"><CjkText :text="question.text" /></h2>
              <audio
                v-if="question.audioUrl"
                class="quiz-question-audio"
                :src="question.audioUrl"
                controls
                :aria-label="t('classPlayer.quiz.audioLabel')"
              />
              <img v-if="question.imageUrl" class="quiz-question-image" :src="question.imageUrl" alt="" />
              <p class="quiz-question-instruction">
                {{
                  isTypedQuestion(question.type) ? t('classPlayer.quiz.typeAnswer') : t('classPlayer.quiz.chooseBest')
                }}
              </p>
              <button
                v-if="question.hasHint && session.quiz.hintsAvailable && !hints[question.id]"
                type="button"
                class="btn btn-secondary btn-sm"
                :disabled="hintBusy"
                @click="showHint(question.id)"
              >
                <IconGlyph name="hint" :size="16" />{{ t('classPlayer.quiz.showHint') }}
              </button>
              <p v-if="hints[question.id]" class="callout is-compact quiz-question-hint" role="status">
                <IconGlyph name="hint" :size="16" /><span>{{
                  t('classPlayer.quiz.hint', { hint: hints[question.id] })
                }}</span>
              </p>
            </div>

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
            <div v-else class="quiz-options" role="radiogroup" :aria-label="question.text">
              <button
                v-for="(option, optionIndex) in question.options"
                :key="option.id"
                type="button"
                role="radio"
                class="quiz-option"
                :class="{ 'is-selected': chosen[question.id] === option.id }"
                :aria-checked="chosen[question.id] === option.id"
                :data-testid="`quiz-option-${option.id}`"
                @click="choose(question.id, option.id)"
              >
                <span class="quiz-option-letter" aria-hidden="true">
                  <IconGlyph v-if="chosen[question.id] === option.id" name="check" :size="14" />
                  <template v-else>{{ LETTERS[optionIndex] ?? optionIndex + 1 }}</template>
                </span>
                <CjkText :text="option.text" />
              </button>
            </div>

            <div class="class-player-nav">
              <button type="button" class="btn btn-outline btn-sm" :disabled="index === 0" @click="goTo(index - 1)">
                <IconGlyph name="chevronLeft" :size="16" />{{ t('classPlayer.quiz.previous') }}
              </button>
              <button
                type="button"
                class="btn btn-outline btn-sm"
                :disabled="index === session.questions.length - 1"
                @click="goTo(index + 1)"
              >
                {{ t('classPlayer.quiz.next') }}<IconGlyph name="chevronRight" :size="16" />
              </button>
            </div>
          </section>

          <div class="quiz-submit-row">
            <div class="quiz-submit-copy">
              <strong>{{
                t('classPlayer.quiz.answered', { answered: answeredCount, total: session.questions.length })
              }}</strong>
              <span class="type-caption" role="status">
                <IconGlyph name="check" :size="14" />{{
                  savingCount ? t('classPlayer.quiz.saving') : t('classPlayer.quiz.autosave')
                }}
              </span>
            </div>
            <button
              type="button"
              class="btn btn-primary btn-lg"
              :disabled="remaining > 0 || submitting || savingCount > 0"
              :aria-busy="submitting"
              data-testid="quiz-submit"
              @click="submit"
            >
              {{ remaining > 0 ? t('classPlayer.quiz.answerMore', remaining) : t('classPlayer.quiz.submit') }}
            </button>
          </div>
        </template>

        <!-- Before the attempt (`currentQuiz` is the intro here) -->
        <section v-else class="quiz-intro">
          <p class="quiz-intro-text">
            <IconGlyph :name="currentQuiz.timeLimitMinutes ? 'timer' : 'clock'" :size="20" />
            <span>{{
              currentQuiz.timeLimitMinutes
                ? t('classPlayer.quiz.introTimed', { minutes: currentQuiz.timeLimitMinutes })
                : t('classPlayer.quiz.introUntimed')
            }}</span>
          </p>
          <div class="quiz-intro-foot">
            <p v-if="currentQuiz.bestPercent !== null" class="quiz-intro-best">
              <IconGlyph name="trophy" :size="16" />{{
                t('classPlayer.quiz.bestSoFar', { percent: currentQuiz.bestPercent })
              }}
            </p>
            <div class="quiz-intro-actions">
              <RouterLink class="btn btn-ghost" :to="lessonRoute">{{ t('classPlayer.quiz.backToLesson') }}</RouterLink>
              <button
                type="button"
                class="btn btn-primary"
                :disabled="starting"
                :aria-busy="starting"
                data-testid="quiz-start"
                @click="start"
              >
                {{ currentQuiz.openAttemptId ? t('classPlayer.quiz.resume') : t('classPlayer.quiz.start') }}
              </button>
            </div>
          </div>
        </section>
      </template>

      <template v-else>
        <h1 class="sr-only">{{ t('classPlayer.quiz.topLabelUntimed') }}</h1>
        <StatePanel
          v-if="introResource.loading.value || loadingResult"
          tone="loading"
          spacious
          :title="t('classes.loading')"
          :message="t('classPlayer.quiz.topLabelUntimed')"
        />
        <StatePanel
          v-else
          tone="error"
          icon="alert"
          spacious
          :title="t('classPlayer.quiz.loadErrorTitle')"
          :message="introResource.error.value ?? actionError ?? ''"
        >
          <template #actions>
            <button class="btn btn-secondary" type="button" @click="introResource.reload">
              {{ t('classes.retry') }}
            </button>
            <RouterLink class="btn btn-primary" :to="{ name: 'student-classes' }">{{
              t('classes.classes')
            }}</RouterLink>
          </template>
        </StatePanel>
      </template>
    </div>

    <ConfirmDialog
      :open="leaveOpen"
      :title="t('classPlayer.quiz.leaveTitle')"
      :message="session?.expiresAt ? t('classPlayer.quiz.leaveMessageTimed') : t('classPlayer.quiz.leaveMessage')"
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
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import FocusTopBar from '@/components/layout/FocusTopBar.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classPlayerMessages } from '@/plugins/classPlayerMessages'
import { classesMessages } from '@/plugins/classesMessages'
import type { ClassActivityType, QuizIntro, QuizResult, QuizSession } from '@/types/classes'
import { apiErrorStatus, resolveApiError } from '@/utils/apiError'
import { SKILL_KEYS, STAGE_KEYS, isTypedQuestion } from '@/utils/classes'
import { formatClock, formatDateTime, ratioPercent } from '@/utils/format'
import '@/styles/classes.css'

const { t } = useLocalMessages({ classPlayer: classPlayerMessages, classes: classesMessages })
const route = useRoute()
const router = useRouter()
const { actionError, showError, clearFeedback } = useActionFeedback()

const LETTERS = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H']
const TYPING_SAVE_DELAY_MS = 700

const quizId = computed(() => Number(route.params.quizId))
const introResource = useResource(() => studentClassesApi.quiz(quizId.value))
const intro = computed(() => introResource.data.value)
const session = ref<QuizSession | null>(null)
const result = ref<QuizResult | null>(null)
const loadingResult = ref(false)
const timeUpNotice = ref(false)
/** The quiz in the header before and during an attempt. */
const currentQuiz = computed(() => session.value?.quiz ?? intro.value)

const lessonRoute = computed(() => {
  const quiz = result.value?.quiz ?? currentQuiz.value
  return quiz
    ? { name: 'student-class-lesson', params: { classId: quiz.classId, lessonId: quiz.lessonId } }
    : { name: 'student-classes' }
})

function activityRoute(type: ClassActivityType, activityId: number) {
  const quiz = result.value!.quiz
  return {
    name: 'student-class-activity',
    params: { classId: quiz.classId, lessonId: quiz.lessonId, activityType: activitySlug(type), activityId },
  }
}

function quizFacts(quiz: QuizIntro): string {
  const parts = [
    quiz.stage ? t(STAGE_KEYS[quiz.stage]) : null,
    t('classes.questions', quiz.questionCount),
    t('classes.marks', quiz.marks),
    quiz.timeLimitMinutes ? t('classPlayer.quiz.timeLimit', { minutes: quiz.timeLimitMinutes }) : null,
    quiz.hintsAvailable ? t('classPlayer.quiz.hintsAvailable') : null,
  ]
  return parts.filter(Boolean).join(' · ')
}

// ----- a past result (?attempt=) -----

async function showAttempt(attemptId: number): Promise<void> {
  loadingResult.value = true
  try {
    result.value = await studentClassesApi.quizResult(attemptId)
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
  },
  { immediate: true },
)

// ----- the attempt -----

const index = ref(0)
const chosen = ref<Record<number, number>>({})
const typed = ref<Record<number, string>>({})
const savedTyped = ref<Record<number, string>>({})
const hints = ref<Record<number, string>>({})
const savingCount = ref(0)
const starting = ref(false)
const submitting = ref(false)
const hintBusy = ref(false)
const leaveOpen = ref(false)
const showAllQuestions = ref(false)
const secondsLeft = ref<number | null>(null)
let ticker: ReturnType<typeof setInterval> | null = null
const typingTimers = new Map<number, ReturnType<typeof setTimeout>>()

const question = computed(() => session.value?.questions[index.value] ?? null)
const answeredCount = computed(() => session.value?.questions.filter((item) => isAnswered(item.id)).length ?? 0)
const remaining = computed(() => (session.value?.questions.length ?? 0) - answeredCount.value)

function isAnswered(questionId: number): boolean {
  return chosen.value[questionId] !== undefined || Boolean(savedTyped.value[questionId]?.trim())
}

function stopTimers(): void {
  if (ticker) clearInterval(ticker)
  ticker = null
  typingTimers.forEach((timer) => clearTimeout(timer))
  typingTimers.clear()
}
onBeforeUnmount(stopTimers)

function begin(next: QuizSession): void {
  stopTimers()
  session.value = next
  result.value = null
  timeUpNotice.value = false
  index.value = 0
  chosen.value = {}
  typed.value = {}
  savedTyped.value = {}
  hints.value = {}
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
  secondsLeft.value = next.secondsLeft
  if (next.secondsLeft !== null) {
    ticker = setInterval(() => {
      if (secondsLeft.value === null) return
      secondsLeft.value = Math.max(0, secondsLeft.value - 1)
      if (secondsLeft.value === 0) void finishOnTime()
    }, 1000)
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
}

/** An answer after the time limit makes the server submit the attempt: then the result is shown. */
async function handleSaveFailure(reason: unknown): Promise<void> {
  if (apiErrorStatus(reason) === 409 && session.value) {
    await finishOnTime()
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

async function showHint(questionId: number): Promise<void> {
  if (!session.value) return
  hintBusy.value = true
  try {
    const hint = await studentClassesApi.hint(session.value.attemptId, questionId)
    hints.value = { ...hints.value, [questionId]: hint.hint }
  } catch (reason) {
    await handleSaveFailure(reason)
  } finally {
    hintBusy.value = false
  }
}

async function submit(): Promise<void> {
  const current = question.value
  if (current && isTypedQuestion(current.type)) await saveTyped(current.id)
  clearFeedback()
  await submitAttempt(false)
}

/** The time ran out: the server has submitted (or now submits) the attempt with the saved answers. */
async function finishOnTime(): Promise<void> {
  if (submitting.value) return
  stopTimers()
  await submitAttempt(true)
}

/** Submits the attempt and shows its result, with the "time is up" notice when `timeUp`. */
async function submitAttempt(timeUp: boolean): Promise<void> {
  if (!session.value) return
  submitting.value = true
  try {
    showResult(await studentClassesApi.submitQuiz(session.value.attemptId))
    timeUpNotice.value = timeUp
  } catch (reason) {
    showError(resolveApiError(reason, t('classPlayer.quiz.submitError')))
  } finally {
    submitting.value = false
  }
}

function showResult(next: QuizResult): void {
  stopTimers()
  result.value = next
  session.value = null
  showAllQuestions.value = false
  void introResource.reload()
}

// ----- result -----

const reviewed = computed(() => {
  const questions = result.value?.questions ?? []
  const wrongFirst = [...questions].sort((a, b) => Number(a.correct) - Number(b.correct) || a.number - b.number)
  return showAllQuestions.value ? wrongFirst : wrongFirst.filter((item) => !item.correct)
})

const masteryText = computed(() => {
  const current = result.value
  if (!current) return ''
  if (current.lessonStatus === 'MASTERED') return t('classPlayer.result.mastered')
  if (current.percent >= 80 || (current.bestPercent ?? 0) >= 80) return t('classPlayer.result.masteredNeedsPractice')
  if (current.lessonStatus === 'COMPLETED') return t('classPlayer.result.completed')
  return t('classPlayer.result.keepGoing')
})

// ----- top bar and leaving -----

const topLabel = computed(() => {
  if (result.value) return t('classPlayer.quiz.topLabelResult')
  if (session.value && secondsLeft.value !== null)
    return t('classPlayer.quiz.topLabel', { time: formatClock(secondsLeft.value) })
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
</script>
