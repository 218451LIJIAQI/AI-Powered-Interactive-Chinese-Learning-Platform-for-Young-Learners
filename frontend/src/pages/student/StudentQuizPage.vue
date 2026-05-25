<template>
  <div class="page-stack student-quiz-page">
    <section class="student-quiz-header">
      <button class="student-link-button" type="button" @click="router.push('/student/practice')">
        <IconGlyph name="chevronLeft" :size="16" />
        Back to Practice
      </button>
      <div class="student-quiz-title-row">
        <div class="student-quiz-title-badge">你好</div>
        <div>
          <h1>{{ quiz.title }}</h1>
          <p>{{ quizMetaLabel }}</p>
        </div>
      </div>
    </section>

    <DataNotice :using-fallback="usingFallback" :error="actionError || pageError" />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="student-quiz-layout">
        <div class="student-quiz-main">
          <form v-if="activeQuestion" class="student-quiz-card" @submit.prevent="submitQuiz">
            <div class="student-quiz-progress-row">
              <strong>Question {{ currentQuestionNumber }} of {{ totalQuestionCount }}</strong>
              <div class="student-quiz-progress-track"><span :style="{ width: quizProgressWidth }" /></div>
              <span><IconGlyph name="history" :size="15" /> {{ quizTimeLabel }}</span>
            </div>

            <div class="student-quiz-question">
              <span class="student-quiz-audio" aria-hidden="true"><IconGlyph name="live" :size="20" /></span>
              <h2>{{ activeQuestion.questionText }}</h2>
              <p>{{ quiz.description || 'Choose the best answer.' }}</p>
            </div>

            <div class="student-quiz-options">
              <button
                v-for="(option, index) in activeQuestion.options"
                :key="option.id"
                class="student-quiz-option"
                :class="{
                  'is-selected': answers[activeQuestion.id] === option.optionText,
                  'is-correct': answerTone(option.optionText) === 'correct',
                  'is-wrong': answerTone(option.optionText) === 'wrong',
                }"
                :aria-pressed="answers[activeQuestion.id] === option.optionText"
                :disabled="hasSubmitted"
                type="button"
                @click="chooseAnswer(activeQuestion.id, option.optionText)"
              >
                <span>{{ optionLetters[index] }}</span>
                <strong>{{ option.optionText }}</strong>
              </button>
            </div>

            <div class="student-quiz-tip">
              <span><IconGlyph name="fun" :size="20" /></span>
              <p><strong>Tip:</strong> Use the lesson context and pronunciation clues before submitting.</p>
            </div>

            <div class="row-between u-wrap u-gap-12">
              <button
                class="teacher-outline-button"
                type="button"
                :disabled="activeQuestionIndex === 0"
                @click="goToPreviousQuestion"
              >
                Previous
              </button>
              <span class="muted">{{ answeredCount }} / {{ totalQuestionCount }} answered</span>
              <button class="teacher-outline-button" type="button" :disabled="isLastQuestion" @click="goToNextQuestion">
                Next
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

          <StatePanel v-else icon="quiz" title="No quiz ready" :message="quizEmptyMessage" tone="empty">
            <template #actions>
              <button class="btn btn-primary" type="button" @click="router.push('/student/learn')">Open Lessons</button>
              <button class="btn btn-secondary" type="button" @click="router.push('/student/practice')">
                Practice Instead
              </button>
            </template>
          </StatePanel>

          <article v-if="hasSubmitted" class="student-quiz-review">
            <div class="row u-mb-12">
              <span class="student-review-alert"><IconGlyph name="alert" :size="14" /></span>
              <strong>Review · Let's learn from mistakes!</strong>
            </div>
            <div class="student-quiz-review-grid">
              <article v-for="answer in result.answers" :key="answer.questionId" class="student-quiz-review-card">
                <small>{{ answer.correct ? 'Correct answer' : 'Your answer' }}</small>
                <strong>{{ answer.questionText }}</strong>
                <p>{{ answer.studentAnswer || 'No answer submitted' }}</p>
                <span :class="answer.correct ? 'is-correct' : 'is-wrong'">
                  <IconGlyph :name="answer.correct ? 'status' : 'alert'" :size="14" />
                  {{ answer.correct ? 'Correct' : 'Review' }}
                </span>
              </article>
            </div>
          </article>

          <article v-else-if="activeQuestion" class="student-quiz-review student-quiz-review--pending">
            <div class="row u-mb-12">
              <span class="student-review-alert"><IconGlyph name="help" :size="14" /></span>
              <strong>Feedback unlocks after you submit</strong>
            </div>
            <p>
              Choose an answer first. SparkBot will show review notes after your attempt so the quiz stays fair and
              encouraging.
            </p>
          </article>
        </div>

        <aside class="student-quiz-side">
          <article class="panel student-quiz-side-card">
            <div class="row-between">
              <strong>Quiz Progress</strong>
              <button class="student-link-button" type="button" @click="router.push('/student/practice')">
                View All
              </button>
            </div>
            <div class="student-quiz-ring">
              <span>{{ progress.quizAverage }}%</span>
            </div>
            <div class="student-quiz-side-stats">
              <div>
                <small>Lessons Started</small>
                <strong>{{ progress.totalLessonsStarted }}</strong>
              </div>
              <div>
                <small>Completed</small>
                <strong>{{ progress.completedLessons }}</strong>
              </div>
            </div>
            <ProgressBar :progress="progress.quizAverage" />
          </article>

          <article class="panel student-quiz-side-card">
            <div class="row-between">
              <strong>Recent Lesson Progress</strong>
              <button class="student-link-button" type="button" @click="router.push('/student/practice')">
                Open Practice
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
            <p v-else class="muted u-m-0">Recent lesson progress will appear after you start learning.</p>
          </article>

          <article class="student-quiz-help-card">
            <strong>Need a hint?</strong>
            <p>SparkBot can explain the answer and help you try again.</p>
            <button class="btn btn-primary" type="button" @click="router.push('/student/ai-helper')">
              Ask SparkBot
            </button>
            <span><IconGlyph name="ai" :size="28" /></span>
          </article>
        </aside>
      </section>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
/**
 * Quiz player surface — start, submit, score, and wrong-answer
 * review for a single quiz attached to a published lesson.
 *
 * <h2>Answer-key boundary</h2>
 * <p>The student-side response strips correct-option / correct-answer
 * fields at the {@code QuizMapper} backend layer, so a malicious
 * learner cannot inspect the network payload to cheat. The page
 * also never persists answers to localStorage so a refresh restarts
 * the attempt rather than enabling repeated submission.</p>
 */
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { studentApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import { useResource } from '@/composables/useResource'
import { studentProgress as studentProgressDemo, studentQuiz } from '@/demo/data'
import type { QuizAttemptResultResponse, StudentQuizResponse } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { formatDate } from '@/utils/format'

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
  title: 'Choose a quiz',
  description: 'Pick a published quiz from your available lessons to start.',
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
}
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
const result = computed(() => resultData.value ?? emptyResult)
const pageLoading = computed(() => quizResource.loading.value || progressResource.loading.value)
const pageError = computed(() => quizResource.error.value || progressResource.error.value)
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
    quiz.value.lessonTitle || `Lesson ${quiz.value.lessonId}`,
    `${totalQuestionCount.value} question${totalQuestionCount.value === 1 ? '' : 's'}`,
    `${quiz.value.totalPoints} point${quiz.value.totalPoints === 1 ? '' : 's'}`,
  ]
  return parts.filter(Boolean).join(' - ')
})
const quizTimeLabel = computed(() =>
  quiz.value.timeLimitMinutes ? `${quiz.value.timeLimitMinutes}:00` : 'No time limit',
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
  if (submitting.value) return 'Submitting...'
  if (hasSubmitted.value) return 'Submitted'
  if (!allQuestionsAnswered.value) return `Answer ${unansweredQuestionCount.value} more`
  return 'Submit quiz'
})
const quizEmptyMessage = computed(() => {
  if (hasQuizSelection.value) {
    return 'This published quiz does not have questions yet. Try another lesson or practice activity.'
  }

  if (requestedLessonId.value) {
    return 'No published quiz is available for this lesson yet. Continue the lesson or pick another practice activity.'
  }

  return 'No published quiz is available to this student account yet. Continue lessons first, then return when a teacher has published a quiz.'
})
const performanceItems = computed(() =>
  progress.value.recentLessons.slice(0, 3).map((lesson) => ({
    icon: iconForLessonProgress(lesson.completionPercent, lesson.completed),
    title: lesson.lessonTitle,
    when: lesson.lastAccessedAt
      ? formatDate(lesson.lastAccessedAt)
      : progressStatusLabel(lesson.completionPercent, lesson.completed),
    score: `${Math.round(lesson.completionPercent)}%`,
  })),
)

function iconForLessonProgress(completionPercent: number, completed: boolean) {
  if (completed || completionPercent >= 100) return 'status'
  if (completionPercent > 0) return 'learn'
  return 'star'
}

function progressStatusLabel(completionPercent: number, completed: boolean) {
  if (completed || completionPercent >= 100) return 'Completed'
  if (completionPercent > 0) return 'In progress'
  return 'Not started'
}

function chooseAnswer(questionId: number, optionText: string) {
  answers[questionId] = optionText
  actionError.value = null
}

function answerTone(optionText: string) {
  if (!hasSubmitted.value) return ''
  const selectedAnswer = activeQuestion.value ? answers[activeQuestion.value.id] : ''
  const matchingResult = result.value.answers.find((answer) => answer.questionId === activeQuestion.value?.id)
  if (!matchingResult) return ''
  if (matchingResult.correctAnswer === optionText) return 'correct'
  if (selectedAnswer === optionText && !matchingResult.correct) return 'wrong'
  return ''
}

function goToQuestion(index: number) {
  activeQuestionIndex.value = Math.min(Math.max(index, 0), Math.max(totalQuestionCount.value - 1, 0))
}

function goToPreviousQuestion() {
  goToQuestion(activeQuestionIndex.value - 1)
}

function goToNextQuestion() {
  goToQuestion(activeQuestionIndex.value + 1)
}

function firstUnansweredQuestionIndex() {
  return orderedQuestions.value.findIndex((question) => !answers[question.id])
}

function numericParam(value: unknown) {
  const rawValue = Array.isArray(value) ? value[0] : value
  if (rawValue === null || rawValue === undefined || rawValue === '') return null

  const parsed = Number(rawValue)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

async function loadQuiz() {
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

function reloadAll() {
  void quizResource.reload()
  void progressResource.reload()
}

async function submitQuiz() {
  if (submitting.value || hasSubmitted.value) return
  actionError.value = null
  if (!hasQuizSelection.value) {
    actionError.value = 'Choose an available quiz before submitting an attempt.'
    return
  }

  if (quizReadOnly.value) {
    actionError.value = 'Preview quizzes cannot be submitted to the backend. Use a live quiz to save an attempt.'
    return
  }

  if (!allQuestionsAnswered.value) {
    const firstMissingIndex = firstUnansweredQuestionIndex()
    if (firstMissingIndex >= 0) {
      goToQuestion(firstMissingIndex)
    }
    actionError.value = 'Answer every question before submitting this quiz.'
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
    actionError.value = resolveApiError(reason, 'Unable to submit this quiz attempt.')
  } finally {
    submitting.value = false
  }
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
    Object.keys(answers).forEach((questionId) => {
      delete answers[Number(questionId)]
    })
    hasSubmitted.value = false
    resultData.value = null
    actionError.value = null
    void quizResource.reload()
  },
)
</script>
