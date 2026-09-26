<template>
  <div class="page-stack student-quiz-page">
    <section class="student-quiz-header">
      <button class="student-link-button" type="button" @click="openRecommendedPractice">
        <IconGlyph name="chevronLeft" :size="16" />
        {{ t('studentQuiz.back') }}
      </button>
      <div class="student-quiz-title-row">
        <div class="student-quiz-title-badge">你好</div>
        <div>
          <h1>{{ quiz.title }}</h1>
          <p>{{ quizMetaLabel }}</p>
        </div>
      </div>
    </section>

    <DataNotice :using-fallback="usingFallback" :error="noticeError" />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="student-quiz-layout">
        <div class="student-quiz-main">
          <form v-if="activeQuestion" class="student-quiz-card" @submit.prevent="submitQuiz">
            <div class="student-quiz-progress-row">
              <strong>{{
                t('studentQuiz.questionOf', { current: currentQuestionNumber, total: totalQuestionCount })
              }}</strong>
              <div class="student-quiz-progress-track"><span :style="{ width: quizProgressWidth }" /></div>
              <span><IconGlyph name="history" :size="15" /> {{ quizTimeLabel }}</span>
            </div>

            <div class="student-quiz-question">
              <span class="student-quiz-audio" aria-hidden="true"><IconGlyph name="live" :size="20" /></span>
              <h2>{{ activeQuestion.questionText }}</h2>
              <p>{{ quizDescriptionLabel }}</p>
            </div>

            <div class="student-quiz-options">
              <button
                v-for="(option, index) in activeQuestion.options"
                :key="option.id"
                class="student-quiz-option"
                :class="optionButtonClasses(option.optionText)"
                :aria-pressed="answers[activeQuestion.id] === option.optionText"
                :disabled="hasSubmitted"
                type="button"
                @click="chooseAnswer(activeQuestion.id, option.optionText)"
              >
                <span>{{ optionLetter(index) }}</span>
                <strong>{{ option.optionText }}</strong>
              </button>
            </div>

            <div class="student-quiz-tip">
              <span><IconGlyph name="fun" :size="20" /></span>
              <p>
                <strong>{{ t('studentQuiz.tip') }}</strong> {{ t('studentQuiz.tipHint') }}
              </p>
            </div>

            <div class="row-between u-wrap u-gap-12">
              <button
                class="student-quiz-nav-button"
                type="button"
                :disabled="activeQuestionIndex === 0"
                @click="goToPreviousQuestion"
              >
                {{ t('studentQuiz.previous') }}
              </button>
              <span class="muted">{{
                t('studentQuiz.answered', { answered: answeredCount, total: totalQuestionCount })
              }}</span>
              <button
                class="student-quiz-nav-button"
                type="button"
                :disabled="isLastQuestion"
                @click="goToNextQuestion"
              >
                {{ t('studentQuiz.next') }}
              </button>
            </div>

            <button
              class="btn btn-accent student-quiz-submit"
              :disabled="quizReadOnly || submitting || hasSubmitted || !allQuestionsAnswered"
              type="submit"
            >
              {{ submitButtonLabel }}
            </button>
          </form>

          <StatePanel v-else icon="quiz" :title="t('studentQuiz.noQuiz')" :message="quizEmptyMessage" tone="empty">
            <template #actions>
              <button class="btn btn-primary" type="button" @click="openLessons">
                {{ t('studentQuiz.openLessons') }}
              </button>
              <button class="btn btn-secondary" type="button" @click="openRecommendedPractice">
                {{ t('studentQuiz.practiceInstead') }}
              </button>
            </template>
          </StatePanel>

          <article v-if="hasSubmitted" class="student-quiz-review">
            <div class="row u-mb-12">
              <span class="student-review-alert"><IconGlyph name="alert" :size="14" /></span>
              <strong>{{ t('studentQuiz.reviewTitle') }}</strong>
            </div>
            <div class="student-quiz-review-grid">
              <article v-for="answer in result.answers" :key="answer.questionId" class="student-quiz-review-card">
                <small>{{ answer.correct ? t('studentQuiz.correctAnswer') : t('studentQuiz.yourAnswer') }}</small>
                <strong>{{ answer.questionText }}</strong>
                <p>{{ answer.studentAnswer || t('studentQuiz.noAnswer') }}</p>
                <span :class="answer.correct ? 'is-correct' : 'is-wrong'">
                  <IconGlyph :name="answer.correct ? 'status' : 'alert'" :size="14" />
                  {{ answer.correct ? t('studentQuiz.correct') : t('studentQuiz.review') }}
                </span>
              </article>
            </div>
          </article>

          <article v-else-if="activeQuestion" class="student-quiz-review student-quiz-review--pending">
            <div class="row u-mb-12">
              <span class="student-review-alert"><IconGlyph name="help" :size="14" /></span>
              <strong>{{ t('studentQuiz.feedbackLocked') }}</strong>
            </div>
            <p>
              {{ t('studentQuiz.feedbackHint') }}
            </p>
          </article>
        </div>

        <aside class="student-quiz-side">
          <article class="panel student-quiz-side-card">
            <div class="row-between">
              <strong>{{ t('studentQuiz.quizProgress') }}</strong>
              <button class="student-link-button" type="button" @click="openRecommendedPractice">
                {{ t('studentQuiz.viewAll') }}
              </button>
            </div>
            <div class="student-quiz-ring">
              <span>{{ quizAveragePercent }}%</span>
            </div>
            <div class="student-quiz-side-stats">
              <div>
                <small>{{ t('studentQuiz.lessonsStarted') }}</small>
                <strong>{{ startedLessonCount }}</strong>
              </div>
              <div>
                <small>{{ t('studentQuiz.completed') }}</small>
                <strong>{{ completedLessonCount }}</strong>
              </div>
            </div>
            <ProgressBar :progress="quizAveragePercent" />
          </article>

          <article class="panel student-quiz-side-card">
            <div class="row-between">
              <strong>{{ t('studentQuiz.recentProgress') }}</strong>
              <button class="student-link-button" type="button" @click="openRecommendedPractice">
                {{ t('studentQuiz.openPractice') }}
              </button>
            </div>
            <div v-if="performanceItems.length" class="student-quiz-performance-list">
              <div v-for="item in performanceItems" :key="item.title" class="student-quiz-performance-item">
                <span><IconGlyph :name="item.icon" :size="16" /></span>
                <div>
                  <strong>{{ item.title }}</strong>
                  <small>{{ item.when }}</small>
                </div>
                <b>{{ item.score }}</b>
              </div>
            </div>
            <p v-else class="muted u-m-0">{{ t('studentQuiz.noProgress') }}</p>
          </article>

          <article class="student-quiz-help-card">
            <strong>{{ t('studentQuiz.needHint') }}</strong>
            <p>{{ t('studentQuiz.hintHelp') }}</p>
            <button class="btn btn-primary" type="button" @click="openAiHelper">
              {{ t('studentQuiz.askSparkBot') }}
            </button>
            <span><IconGlyph name="ai" :size="28" /></span>
          </article>
        </aside>
      </section>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { studentApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import { useResource } from '@/composables/useResource'
import { studentProgress as studentProgressDemo, studentQuiz } from '@/demo/data'
import { studentQuizMessages } from '@/plugins/studentQuizMessages'
import type { QuizAttemptResultResponse, StudentQuizResponse } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { clampPercent, formatDate, nonNegativeInteger } from '@/utils/format'
import { inferPracticeActivityFromText, practiceRoute } from '@/utils/practiceRoute'

const { t } = useI18n({
  messages: {
    en: { studentQuiz: studentQuizMessages.en },
    ms: { studentQuiz: studentQuizMessages.ms },
    zh: { studentQuiz: studentQuizMessages.zh },
  },
})

type AnswerTone = '' | 'correct' | 'wrong'

const route = useRoute()
const router = useRouter()
const submitting = ref(false)
const hasSubmitted = ref(false)
const actionError = ref<string | null>(null)
const answers = reactive<Record<number, string>>({})
const activeQuestionIndex = ref(0)
const currentQuizId = computed(() => numericParam(route.params.quizId))
const requestedLessonId = computed(() => numericParam(route.query.lessonId))

const quizResource = useResource(loadQuiz, () => studentQuiz)
const progressResource = useResource(() => studentApi.progress(), studentProgressDemo)

const emptyQuiz = {
  id: 0,
  lessonId: 0,
  lessonTitle: '',
  title: t('studentQuiz.chooseQuiz'),
  description: t('studentQuiz.chooseQuizHint'),
  timeLimitMinutes: null,
  totalPoints: 0,
  questions: [],
} satisfies StudentQuizResponse
const emptyResult = {
  attemptId: 0,
  quizId: 0,
  quizTitle: '',
  score: 0,
  maxScore: 0,
  totalQuestions: 0,
  correctCount: 0,
  wrongCount: 0,
  earnedPoints: 0,
  totalPoints: 0,
  passed: false,
  startedAt: '',
  submittedAt: '',
  answers: [],
} satisfies QuizAttemptResultResponse
const resultData = ref<QuizAttemptResultResponse | null>(null)
const emptyProgress = {
  totalLessonsStarted: 0,
  completedLessons: 0,
  quizAverage: 0,
  overallCompletionPercent: 0,
  weakAreas: [],
  lastActivitySummary: null,
  recentLessons: [],
}

const quiz = computed(() => quizResource.data.value ?? (quizResource.usingFallback.value ? studentQuiz : emptyQuiz))
const progress = computed(
  () => progressResource.data.value ?? (progressResource.usingFallback.value ? studentProgressDemo : emptyProgress),
)
const quizAveragePercent = computed(() => clampPercent(progress.value.quizAverage))
const startedLessonCount = computed(() => nonNegativeInteger(progress.value.totalLessonsStarted))
const completedLessonCount = computed(() => nonNegativeInteger(progress.value.completedLessons))
const result = computed(() => resultData.value ?? emptyResult)
const pageLoading = computed(() => quizResource.loading.value || progressResource.loading.value)
const pageError = computed(() => quizResource.error.value || progressResource.error.value)
const noticeError = computed(() => actionError.value || pageError.value)
const usingFallback = computed(() => quizResource.usingFallback.value || progressResource.usingFallback.value)
const quizReadOnly = computed(() => quizResource.usingFallback.value)
const hasQuizSelection = computed(() => Boolean(quiz.value.id))
const orderedQuestions = computed(() =>
  [...quiz.value.questions].sort((first, second) => first.displayOrder - second.displayOrder),
)
const activeQuestion = computed(() => orderedQuestions.value[activeQuestionIndex.value])
const optionLetters = ['A', 'B', 'C', 'D', 'E', 'F']
const totalQuestionCount = computed(() => orderedQuestions.value.length)
const quizMetaLabel = computed(() => {
  const parts = [
    quiz.value.lessonTitle || t('studentQuiz.lesson', { id: quiz.value.lessonId }),
    t('studentQuiz.questions', { count: totalQuestionCount.value }),
    t('studentQuiz.points', { count: quiz.value.totalPoints }),
  ]
  return parts.filter(Boolean).join(' - ')
})
const quizDescriptionLabel = computed(() => quiz.value.description || t('studentQuiz.chooseBest'))
const quizTimeLabel = computed(() =>
  quiz.value.timeLimitMinutes ? `${quiz.value.timeLimitMinutes}:00` : t('studentQuiz.noTimeLimit'),
)
const currentQuestionNumber = computed(() =>
  Math.min(activeQuestionIndex.value + 1, Math.max(totalQuestionCount.value, 1)),
)
const answeredCount = computed(() => orderedQuestions.value.filter((question) => Boolean(answers[question.id])).length)
const unansweredQuestionCount = computed(() => Math.max(0, totalQuestionCount.value - answeredCount.value))
const allQuestionsAnswered = computed(
  () => totalQuestionCount.value > 0 && answeredCount.value === totalQuestionCount.value,
)
const isLastQuestion = computed(() => activeQuestionIndex.value >= totalQuestionCount.value - 1)
const quizProgressWidth = computed(
  () => `${Math.min(100, Math.max(8, (currentQuestionNumber.value / Math.max(totalQuestionCount.value, 1)) * 100))}%`,
)
const submitButtonLabel = computed(() => {
  if (submitting.value) return t('studentQuiz.submitting')
  if (hasSubmitted.value) return t('studentQuiz.submitted')
  if (!allQuestionsAnswered.value) return t('studentQuiz.answerMore', { count: unansweredQuestionCount.value })
  return t('studentQuiz.submit')
})
const quizEmptyMessage = computed(() => {
  if (hasQuizSelection.value) {
    return t('studentQuiz.emptyQuestions')
  }

  if (requestedLessonId.value) {
    return t('studentQuiz.emptyLesson')
  }

  return t('studentQuiz.emptyAccount')
})
const performanceItems = computed(() =>
  progress.value.recentLessons.slice(0, 3).map((lesson) => {
    const completionPercent = clampPercent(lesson.completionPercent)

    return {
      icon: iconForLessonProgress(completionPercent, lesson.completed),
      title: lesson.lessonTitle,
      when: lesson.lastAccessedAt
        ? formatDate(lesson.lastAccessedAt)
        : progressStatusLabel(completionPercent, lesson.completed),
      score: `${completionPercent}%`,
    }
  }),
)
const recommendedPracticeActivity = computed(() =>
  inferPracticeActivityFromText(
    [
      result.value.answers.find((answer) => !answer.correct)?.questionText,
      progress.value.weakAreas[0],
      quiz.value.lessonTitle,
      quiz.value.description,
    ].find((item): item is string => Boolean(item?.trim())),
  ),
)

function iconForLessonProgress(completionPercent: number, completed: boolean): string {
  if (completed || completionPercent >= 100) return 'status'
  if (completionPercent > 0) return 'learn'
  return 'star'
}

function progressStatusLabel(completionPercent: number, completed: boolean): string {
  if (completed || completionPercent >= 100) return t('studentQuiz.completed')
  if (completionPercent > 0) return t('studentQuiz.inProgress')
  return t('studentQuiz.notStarted')
}

function chooseAnswer(questionId: number, optionText: string): void {
  answers[questionId] = optionText
  actionError.value = null
}

function answerTone(optionText: string): AnswerTone {
  if (!hasSubmitted.value) return ''
  const selectedAnswer = activeQuestion.value ? answers[activeQuestion.value.id] : ''
  const matchingResult = result.value.answers.find((answer) => answer.questionId === activeQuestion.value?.id)
  if (!matchingResult) return ''
  if (matchingResult.correctAnswer === optionText) return 'correct'
  if (selectedAnswer === optionText && !matchingResult.correct) return 'wrong'
  return ''
}

function optionButtonClasses(optionText: string): Record<string, boolean> {
  const tone = answerTone(optionText)
  return {
    'is-selected': activeQuestion.value ? answers[activeQuestion.value.id] === optionText : false,
    'is-correct': tone === 'correct',
    'is-wrong': tone === 'wrong',
  }
}

function optionLetter(index: number): string {
  return optionLetters[index] ?? String(index + 1)
}

function goToQuestion(index: number): void {
  activeQuestionIndex.value = Math.min(Math.max(index, 0), Math.max(totalQuestionCount.value - 1, 0))
}

function goToPreviousQuestion(): void {
  goToQuestion(activeQuestionIndex.value - 1)
}

function goToNextQuestion(): void {
  goToQuestion(activeQuestionIndex.value + 1)
}

function firstUnansweredQuestionIndex(): number {
  return orderedQuestions.value.findIndex((question) => !answers[question.id])
}

function numericParam(value: unknown): number | null {
  const rawValue = Array.isArray(value) ? value[0] : value
  if (rawValue === null || rawValue === undefined || rawValue === '') return null

  const parsed = Number(rawValue)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

async function loadQuiz(): Promise<StudentQuizResponse> {
  const quizId = currentQuizId.value
  if (quizId) {
    return studentApi.quiz(quizId)
  }

  const quizPage = await studentApi.quizzes({
    lessonId: requestedLessonId.value ?? undefined,
    size: 20,
  })
  const firstQuiz = quizPage.items[0]
  if (!firstQuiz) {
    return emptyQuiz
  }

  return studentApi.quiz(firstQuiz.id)
}

function reloadAll(): void {
  void quizResource.reload()
  void progressResource.reload()
}

function openLessons(): void {
  void router.push('/student/learn')
}

function openAiHelper(): void {
  void router.push('/student/ai-helper')
}

function openRecommendedPractice(): void {
  void router.push(practiceRoute(recommendedPracticeActivity.value))
}

async function submitQuiz(): Promise<void> {
  if (submitting.value || hasSubmitted.value) return
  actionError.value = null
  if (!hasQuizSelection.value) {
    actionError.value = t('studentQuiz.chooseBeforeSubmit')
    return
  }

  if (quizReadOnly.value) {
    actionError.value = t('studentQuiz.previewSubmit')
    return
  }

  if (!allQuestionsAnswered.value) {
    const firstMissingIndex = firstUnansweredQuestionIndex()
    if (firstMissingIndex >= 0) {
      goToQuestion(firstMissingIndex)
    }
    actionError.value = t('studentQuiz.answerAll')
    return
  }

  submitting.value = true
  try {
    resultData.value = await studentApi.submitQuiz(
      quiz.value.id,
      orderedQuestions.value.map((question) => ({
        questionId: question.id,
        answerText: answers[question.id],
      })),
    )
    hasSubmitted.value = true
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('studentQuiz.submitError'))
  } finally {
    submitting.value = false
  }
}

function resetAttempt(): void {
  Object.keys(answers).forEach((questionId) => {
    delete answers[Number(questionId)]
  })
  hasSubmitted.value = false
  resultData.value = null
  actionError.value = null
}

watch(orderedQuestions, (questions) => {
  if (!questions.length) {
    activeQuestionIndex.value = 0
    return
  }

  if (activeQuestionIndex.value >= questions.length) {
    activeQuestionIndex.value = questions.length - 1
  }
})

watch(
  () => [route.params.quizId, route.query.lessonId],
  () => {
    resetAttempt()
    void quizResource.reload()
  },
)
</script>
