<template>
  <div class="page-stack student-quiz-page">
    <PageHeaderBlock
      :title="quizTitle"
      :description="hasQuizSelection ? quizMetaLabel : t('studentQuiz.chooseQuizHint')"
      icon="quiz"
    >
      <template v-if="hasQuizSelection" #before>
        <LearningBreadcrumb :items="quizBreadcrumb" data-testid="quiz-breadcrumb" />
      </template>
      <template #meta>
        <LearningStageTag :stage="quiz.learningStage" />
      </template>
      <template #actions>
        <button class="btn btn-secondary" type="button" @click="goBack">
          <IconGlyph name="chevronLeft" :size="16" />
          {{ backLabel }}
        </button>
      </template>
    </PageHeaderBlock>

    <DataNotice :using-fallback="usingFallback" :error="actionError" />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <div class="student-quiz-layout">
        <div class="student-quiz-main">
          <section
            v-if="resultData"
            ref="resultCard"
            class="panel student-quiz-card student-quiz-result"
            data-testid="quiz-result"
            tabindex="-1"
            aria-labelledby="student-quiz-result-title"
          >
            <div class="student-quiz-result-head">
              <div class="student-quiz-result-copy">
                <h2 id="student-quiz-result-title" class="student-quiz-result-title">
                  {{ t('studentQuiz.resultTitle') }}
                </h2>
                <span>{{ resultData.quizTitle || quizTitle }}</span>
              </div>
              <StatusPill
                :label="resultData.passed ? t('studentQuiz.passed') : t('studentQuiz.notPassed')"
                :tone="resultData.passed ? 'success' : 'warning'"
                :icon="resultData.passed ? 'status' : 'retry'"
                data-testid="quiz-result-status"
              />
            </div>

            <p v-if="resultIsPreview" class="student-quiz-note" role="status" data-testid="quiz-preview-result">
              {{ t('studentQuiz.previewResult') }}
            </p>

            <dl class="student-quiz-result-stats">
              <div>
                <dt>{{ t('studentQuiz.score') }}</dt>
                <dd data-testid="quiz-result-score">{{ resultData.score }} / {{ resultData.maxScore }}</dd>
              </div>
              <div>
                <dt>{{ t('studentQuiz.percentage') }}</dt>
                <dd data-testid="quiz-result-percentage">{{ formatPercent(resultData.percentage) }}</dd>
              </div>
              <div>
                <dt>{{ t('studentQuiz.correctAnswers') }}</dt>
                <dd>{{ resultData.correctCount }} / {{ resultData.totalQuestions }}</dd>
              </div>
              <div>
                <dt>{{ t('studentQuiz.earnedPoints') }}</dt>
                <dd data-testid="quiz-result-points">{{ nonNegativeInteger(resultData.earnedPoints) }}</dd>
              </div>
            </dl>

            <h3 class="student-quiz-subtitle">{{ t('studentQuiz.questionReview') }}</h3>
            <ol class="student-quiz-result-list">
              <li
                v-for="answer in resultData.answers"
                :key="answer.questionId"
                class="student-quiz-result-item"
                :class="answer.correct ? 'is-correct' : 'is-wrong'"
                data-testid="quiz-result-question"
              >
                <div class="student-quiz-result-item-head">
                  <strong><CjkText :text="answer.questionText" /></strong>
                  <StatusPill
                    :label="answer.correct ? t('studentQuiz.correct') : t('studentQuiz.incorrect')"
                    :tone="answer.correct ? 'success' : 'danger'"
                    :icon="answer.correct ? 'status' : 'alert'"
                  />
                </div>
                <p>
                  <span class="student-quiz-label">{{ t('studentQuiz.yourAnswer') }}:</span>
                  <CjkText :text="selectedAnswerLabel(answer) || t('studentQuiz.noAnswer')" />
                </p>
                <p v-if="!answer.correct">
                  <span class="student-quiz-label">{{ t('studentQuiz.correctAnswer') }}:</span>
                  <CjkText :text="correctAnswerLabel(answer)" />
                </p>
                <p v-if="answer.explanation" class="student-quiz-explanation">
                  <strong>{{ t('studentQuiz.explanation') }}</strong>
                  <CjkText :text="answer.explanation" />
                </p>
                <StatusPill v-if="answer.hintUsed" :label="t('studentQuiz.hintUsedTag')" tone="warning" icon="hint" />
              </li>
            </ol>

            <div class="student-quiz-actions">
              <button class="btn btn-secondary" type="button" @click="goBack">{{ backLabel }}</button>
              <button class="btn btn-primary" type="button" data-testid="quiz-retake" @click="retakeQuiz">
                <IconGlyph name="retry" :size="16" />
                {{ t('studentQuiz.retake') }}
              </button>
            </div>
            <p class="student-quiz-muted">{{ t('studentQuiz.retakeHint') }}</p>
          </section>

          <form v-else-if="activeQuestion" class="panel student-quiz-card" @submit.prevent="submitQuiz">
            <div class="student-quiz-progress">
              <strong>{{
                t('studentQuiz.questionOf', { current: currentQuestionNumber, total: totalQuestionCount })
              }}</strong>
              <ProgressBar
                :progress="quizProgressPercent"
                :label="t('studentQuiz.questionOf', { current: currentQuestionNumber, total: totalQuestionCount })"
              />
              <span class="student-quiz-time">
                <IconGlyph name="history" :size="16" />
                {{ quizTimeLabel }}
              </span>
            </div>

            <div
              class="student-quiz-question"
              data-testid="quiz-question"
              :data-question-type="activeQuestion.questionType"
            >
              <h2><CjkText :text="activeQuestion.questionText" /></h2>
              <p>{{ questionPrompt }}</p>
            </div>

            <div v-if="activeIsTextQuestion" class="field student-quiz-text-field">
              <label class="field-label" :for="textInputId(activeQuestion.id)">{{ t('studentQuiz.typeAnswer') }}</label>
              <input
                :id="textInputId(activeQuestion.id)"
                class="input"
                type="text"
                autocomplete="off"
                maxlength="500"
                :placeholder="t('studentQuiz.textPlaceholder')"
                :value="textAnswerValue(activeQuestion.id)"
                data-testid="quiz-text-answer"
                @input="setTextAnswer(activeQuestion.id, ($event.target as HTMLInputElement).value)"
              />
            </div>

            <div
              v-else-if="activeQuestion.options.length"
              class="student-quiz-options"
              role="group"
              :aria-label="t('studentQuiz.answerOptions')"
            >
              <button
                v-for="(option, index) in sortedOptions(activeQuestion)"
                :key="option.id"
                class="student-quiz-option"
                :class="{ 'is-selected': answers[activeQuestion.id] === option.id }"
                :aria-pressed="answers[activeQuestion.id] === option.id"
                type="button"
                data-testid="quiz-option"
                @click="chooseOption(activeQuestion.id, option.id)"
              >
                <span class="student-quiz-option-letter" aria-hidden="true">
                  <IconGlyph v-if="answers[activeQuestion.id] === option.id" name="status" :size="16" />
                  <template v-else>{{ optionLetter(index) }}</template>
                </span>
                <span class="student-quiz-option-text"><CjkText :text="option.optionText" /></span>
                <span v-if="answers[activeQuestion.id] === option.id" class="student-quiz-option-state">
                  {{ t('studentQuiz.selectedOption') }}
                </span>
              </button>
            </div>

            <p v-else class="field-error" role="alert">{{ t('studentQuiz.noOptions') }}</p>

            <div v-if="activeCanHint" class="student-quiz-hint">
              <button
                class="btn btn-secondary btn-sm"
                type="button"
                data-testid="quiz-hint-button"
                :aria-expanded="openHintId === activeQuestion.id"
                :aria-busy="hintLoadingId === activeQuestion.id"
                :disabled="hintLoadingId !== null"
                @click="toggleHint(activeQuestion)"
              >
                <IconGlyph name="hint" :size="16" />
                {{ hintButtonLabel }}
              </button>
              <div
                v-if="openHintId === activeQuestion.id && hintTexts[activeQuestion.id]"
                class="student-quiz-hint-text"
                role="status"
                data-testid="quiz-hint-text"
              >
                <strong>{{ t('studentQuiz.hintTitle') }}</strong>
                <p><CjkText :text="hintTexts[activeQuestion.id] ?? ''" /></p>
              </div>
              <p v-if="hintErrors[activeQuestion.id]" class="field-error" role="alert">
                {{ hintErrors[activeQuestion.id] }}
              </p>
              <small v-if="hintsUsed[activeQuestion.id]" class="student-quiz-muted">
                {{ t('studentQuiz.hintUsedNote') }}
              </small>
            </div>
            <p v-else-if="quizIsIndependent" class="student-quiz-muted">{{ t('studentQuiz.independentNote') }}</p>

            <p class="student-quiz-tip">
              <IconGlyph name="fun" :size="18" />
              <span>
                <strong>{{ t('studentQuiz.tip') }}</strong> {{ t('studentQuiz.tipHint') }}
              </span>
            </p>

            <div class="student-quiz-nav">
              <button
                class="btn btn-secondary"
                type="button"
                :disabled="activeQuestionIndex === 0"
                @click="goToPreviousQuestion"
              >
                <IconGlyph name="chevronLeft" :size="16" />
                {{ t('studentQuiz.previous') }}
              </button>
              <span class="student-quiz-answered">{{
                t('studentQuiz.answered', { answered: answeredCount, total: totalQuestionCount })
              }}</span>
              <button class="btn btn-secondary" type="button" :disabled="isLastQuestion" @click="goToNextQuestion">
                {{ t('studentQuiz.next') }}
                <IconGlyph name="chevronRight" :size="16" />
              </button>
            </div>

            <div class="student-quiz-submit-row">
              <p v-if="quizReadOnly" class="student-quiz-note">{{ t('studentQuiz.previewSubmitNotice') }}</p>
              <button
                class="btn btn-primary btn-lg"
                :disabled="submitting || !allQuestionsAnswered"
                :aria-busy="submitting"
                type="submit"
                data-testid="quiz-submit"
              >
                {{ submitButtonLabel }}
              </button>
            </div>
          </form>

          <StatePanel v-else icon="quiz" :title="t('studentQuiz.noQuiz')" :message="quizEmptyMessage" tone="empty">
            <template #actions>
              <button class="btn btn-secondary" type="button" @click="openRecommendedPractice">
                {{ t('studentQuiz.practiceInstead') }}
              </button>
              <button class="btn btn-primary" type="button" @click="openLessons">
                {{ t('studentQuiz.openLessons') }}
              </button>
            </template>
          </StatePanel>

          <section
            v-if="!resultData && activeQuestion"
            class="student-quiz-locked"
            aria-labelledby="student-quiz-locked-title"
          >
            <span class="student-quiz-locked-icon" aria-hidden="true"><IconGlyph name="help" :size="18" /></span>
            <div>
              <h2 id="student-quiz-locked-title" class="card-title">{{ t('studentQuiz.feedbackLocked') }}</h2>
              <p>{{ t('studentQuiz.feedbackHint') }}</p>
            </div>
          </section>

          <section
            class="panel student-quiz-card"
            data-testid="wrong-answer-list"
            aria-labelledby="student-quiz-wrong-title"
          >
            <div class="student-quiz-card-head">
              <span class="student-quiz-head-icon" aria-hidden="true"><IconGlyph name="alert" :size="18" /></span>
              <h2 id="student-quiz-wrong-title" class="card-title">{{ t('studentQuiz.wrongAnswersTitle') }}</h2>
            </div>

            <template v-if="resultData">
              <h3 class="student-quiz-subtitle">{{ t('studentQuiz.attemptWrongTitle') }}</h3>
              <div v-if="attemptWrongAnswers.length" class="student-quiz-review-grid">
                <article
                  v-for="answer in attemptWrongAnswers"
                  :key="`attempt-${answer.questionId}`"
                  class="student-quiz-review-card"
                  data-testid="attempt-wrong-answer"
                >
                  <strong><CjkText :text="answer.questionText" /></strong>
                  <p>
                    <span class="student-quiz-label">{{ t('studentQuiz.yourAnswer') }}:</span>
                    <CjkText :text="selectedAnswerLabel(answer) || t('studentQuiz.noAnswer')" />
                  </p>
                  <p>
                    <span class="student-quiz-label">{{ t('studentQuiz.correctAnswer') }}:</span>
                    <CjkText :text="correctAnswerLabel(answer)" />
                  </p>
                  <p v-if="answer.explanation" class="student-quiz-explanation">
                    <CjkText :text="answer.explanation" />
                  </p>
                </article>
              </div>
              <p v-else class="student-quiz-muted">{{ t('studentQuiz.noWrongAttempt') }}</p>
            </template>

            <h3 class="student-quiz-subtitle">{{ t('studentQuiz.recentWrongTitle') }}</h3>
            <p v-if="wrongAnswersResource.loading.value" class="student-quiz-muted" role="status">
              {{ t('studentQuiz.loadingWrongAnswers') }}
            </p>
            <p v-else-if="wrongAnswersResource.error.value" class="field-error" role="alert">
              {{ wrongAnswersResource.error.value }}
            </p>
            <div v-else-if="recentWrongAnswers.length" class="student-quiz-review-grid">
              <article
                v-for="item in recentWrongAnswers"
                :key="`${item.attemptId}-${item.questionId}`"
                class="student-quiz-review-card"
                data-testid="wrong-answer-item"
              >
                <span class="student-quiz-review-context">
                  <template v-if="item.lessonTitle"
                    >{{ t('studentQuiz.wrongLesson', { title: item.lessonTitle }) }} ·
                  </template>
                  {{ item.quizTitle }}
                </span>
                <strong><CjkText :text="item.questionText" /></strong>
                <p>
                  <span class="student-quiz-label">{{ t('studentQuiz.yourAnswer') }}:</span>
                  <CjkText :text="item.studentAnswer || t('studentQuiz.noAnswer')" />
                </p>
                <p>
                  <span class="student-quiz-label">{{ t('studentQuiz.correctAnswer') }}:</span>
                  <CjkText :text="item.correctAnswer" />
                </p>
                <p v-if="item.explanation" class="student-quiz-explanation"><CjkText :text="item.explanation" /></p>
              </article>
            </div>
            <p v-else class="student-quiz-muted">{{ t('studentQuiz.noWrongAnswers') }}</p>
          </section>
        </div>

        <aside class="student-quiz-side">
          <section class="panel student-quiz-card" aria-labelledby="student-quiz-progress-title">
            <div class="student-quiz-card-head">
              <h2 id="student-quiz-progress-title" class="card-title">{{ t('studentQuiz.quizProgress') }}</h2>
              <button class="btn btn-link" type="button" @click="openRecommendedPractice">
                {{ t('studentQuiz.viewAll') }}
              </button>
            </div>
            <ProgressRing :progress="quizAveragePercent" :title="t('studentQuiz.quizAverage')" />
            <dl class="student-quiz-side-stats">
              <div>
                <dt>{{ t('studentQuiz.lessonsStarted') }}</dt>
                <dd>{{ startedLessonCount }}</dd>
              </div>
              <div>
                <dt>{{ t('studentQuiz.completed') }}</dt>
                <dd>{{ completedLessonCount }}</dd>
              </div>
            </dl>
          </section>

          <section class="panel student-quiz-card" aria-labelledby="student-quiz-recent-title">
            <div class="student-quiz-card-head">
              <h2 id="student-quiz-recent-title" class="card-title">{{ t('studentQuiz.recentProgress') }}</h2>
              <button class="btn btn-link" type="button" @click="openRecommendedPractice">
                {{ t('studentQuiz.openPractice') }}
              </button>
            </div>
            <ul v-if="performanceItems.length" class="student-quiz-performance">
              <li v-for="item in performanceItems" :key="item.title" class="student-quiz-performance-item">
                <span class="student-quiz-performance-icon" aria-hidden="true">
                  <IconGlyph :name="item.icon" :size="16" />
                </span>
                <span class="student-quiz-performance-copy">
                  <strong>{{ item.title }}</strong>
                  <small>{{ item.when }}</small>
                </span>
                <strong class="student-quiz-performance-score">{{ item.score }}</strong>
              </li>
            </ul>
            <p v-else class="student-quiz-muted">{{ t('studentQuiz.noProgress') }}</p>
          </section>

          <section
            class="panel student-quiz-card student-quiz-help"
            data-testid="quiz-ai-help"
            aria-labelledby="student-quiz-ai-title"
          >
            <div class="student-quiz-card-head">
              <span class="media-badge small student-quiz-ai-badge" aria-hidden="true">
                <IconGlyph name="ai" :size="18" />
              </span>
              <h2 id="student-quiz-ai-title" class="card-title">{{ t('studentQuiz.aiHelpTitle') }}</h2>
            </div>
            <p class="student-quiz-muted">{{ t('studentQuiz.aiHelpText') }}</p>
            <AiDisclaimer />
            <button class="btn btn-secondary btn-block" type="button" @click="openAiHelper">
              <IconGlyph name="ai" :size="16" />
              {{ t('studentQuiz.askSparkBot') }}
            </button>
          </section>
        </aside>
      </div>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { studentApi } from '@/api/services'
import AiDisclaimer from '@/components/ai/AiDisclaimer.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import ProgressRing from '@/components/common/ProgressRing.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import LearningStageTag from '@/components/content/LearningStageTag.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import CjkText from '@/components/learning/CjkText.vue'
import LearningBreadcrumb, { type BreadcrumbItem } from '@/components/learning/LearningBreadcrumb.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { studentProgress as studentProgressDemo, studentQuiz, studentWrongAnswers } from '@/demo/data'
import { demoQuizHint, gradeDemoQuiz } from '@/demo/quizPreview'
import { learningContextMessages } from '@/plugins/learningContextMessages'
import { studentQuizMessages } from '@/plugins/studentQuizMessages'
import type { QuizAttemptResultResponse, StudentQuizQuestionResponse, StudentQuizResponse } from '@/types/api'
import { apiErrorStatus, resolveApiError } from '@/utils/apiError'
import { clampPercent, formatDate, formatPercent, nonNegativeInteger } from '@/utils/format'
import { normalizeLearningStage, sortByLearningStage } from '@/utils/learningStage'
import { inferPracticeActivityFromText, practiceRoute } from '@/utils/practiceRoute'
import { emptyStudentProgress } from '@/utils/progressSummary'
import {
  buildQuizSubmission,
  canRequestHint,
  correctAnswerLabel,
  countAnsweredQuestions,
  firstUnansweredIndex,
  isTextAnswerType,
  selectedAnswerLabel,
  wrongResultAnswers,
  type QuizAnswerValue,
} from '@/utils/quizAnswers'
import { positiveIntQuery } from '@/utils/routeCompat'

const { t } = useI18n({
  messages: {
    en: { studentQuiz: studentQuizMessages.en, learningContext: learningContextMessages.en },
    ms: { studentQuiz: studentQuizMessages.ms, learningContext: learningContextMessages.ms },
    'zh-CN': { studentQuiz: studentQuizMessages['zh-CN'], learningContext: learningContextMessages['zh-CN'] },
  },
})

const RECENT_WRONG_ANSWER_LIMIT = 6

const route = useRoute()
const router = useRouter()
const submitting = ref(false)
const actionError = ref<string | null>(null)
const answers = reactive<Record<number, QuizAnswerValue | undefined>>({})
const hintTexts = reactive<Record<number, string | undefined>>({})
const hintsUsed = reactive<Record<number, boolean | undefined>>({})
const hintErrors = reactive<Record<number, string | undefined>>({})
const openHintId = ref<number | null>(null)
const hintLoadingId = ref<number | null>(null)
const activeQuestionIndex = ref(0)
const resultData = ref<QuizAttemptResultResponse | null>(null)
const resultIsPreview = ref(false)
const resultCard = ref<HTMLElement | null>(null)
/** Bumped by resetAttempt(); hint and submit responses of an earlier attempt are ignored. */
let attemptSequence = 0
const currentQuizId = computed(() => positiveIntQuery(route.params.quizId))
const requestedLessonId = computed(() => positiveIntQuery(route.query.lessonId))

const quizResource = useResource(loadQuiz, () => studentQuiz)
const progressResource = useResource(() => studentApi.progress(), studentProgressDemo)
const wrongAnswersResource = useResource(
  () => studentApi.wrongAnswers({ size: RECENT_WRONG_ANSWER_LIMIT }),
  () => studentWrongAnswers,
)

const emptyQuiz = {
  id: 0,
  lessonId: 0,
  lessonTitle: '',
  title: '',
  description: '',
  timeLimitMinutes: null,
  totalPoints: 0,
  learningStage: null,
  questions: [],
  moduleId: null,
  moduleTitle: null,
} satisfies StudentQuizResponse
const emptyProgress = emptyStudentProgress()

const quiz = computed(() => quizResource.data.value ?? (quizResource.usingFallback.value ? studentQuiz : emptyQuiz))
const progress = computed(
  () => progressResource.data.value ?? (progressResource.usingFallback.value ? studentProgressDemo : emptyProgress),
)
const quizAveragePercent = computed(() => clampPercent(progress.value.quizAverage))
const startedLessonCount = computed(() => nonNegativeInteger(progress.value.totalLessonsStarted))
const completedLessonCount = computed(() => nonNegativeInteger(progress.value.completedLessons))
const pageLoading = computed(() => quizResource.loading.value || progressResource.loading.value)
const pageError = computed(() => quizResource.error.value || progressResource.error.value)
const usingFallback = computed(() => quizResource.usingFallback.value || progressResource.usingFallback.value)
/** Preview quiz: graded locally with the demo key, nothing is sent to the API. */
const quizReadOnly = computed(() => quizResource.usingFallback.value)
const hasQuizSelection = computed(() => Boolean(quiz.value.id))
// Translated when shown (not frozen at setup), so it follows a language switch.
const quizTitle = computed(() => (hasQuizSelection.value ? quiz.value.title : t('studentQuiz.chooseQuiz')))
const quizIsIndependent = computed(() => normalizeLearningStage(quiz.value.learningStage) === 'INDEPENDENT')
const orderedQuestions = computed(() =>
  [...quiz.value.questions].sort((first, second) => first.displayOrder - second.displayOrder),
)
const activeQuestion = computed<StudentQuizQuestionResponse | undefined>(
  () => orderedQuestions.value[activeQuestionIndex.value],
)
const activeIsTextQuestion = computed(() => isTextAnswerType(activeQuestion.value?.questionType))
const activeCanHint = computed(() =>
  activeQuestion.value ? canRequestHint(activeQuestion.value, quiz.value.learningStage) : false,
)
const questionPrompt = computed(() => {
  const type = activeQuestion.value?.questionType
  if (type === 'FILL_BLANK') return t('studentQuiz.fillBlankHelp')
  if (type === 'REARRANGE_SENTENCE') return t('studentQuiz.rearrangeHelp')
  return t('studentQuiz.chooseBest')
})
const hintButtonLabel = computed(() => {
  const questionId = activeQuestion.value?.id
  if (questionId !== undefined && hintLoadingId.value === questionId) return t('studentQuiz.loadingHint')
  return openHintId.value === questionId ? t('studentQuiz.hideHint') : t('studentQuiz.showHint')
})
const optionLetters = ['A', 'B', 'C', 'D', 'E', 'F']
const totalQuestionCount = computed(() => orderedQuestions.value.length)
const quizMetaLabel = computed(() => {
  const questionCount = totalQuestionCount.value
  const points = nonNegativeInteger(quiz.value.totalPoints)
  const parts = [
    quiz.value.lessonTitle || (quiz.value.lessonId ? t('studentQuiz.lesson', { id: quiz.value.lessonId }) : ''),
    t('studentQuiz.questions', { count: questionCount }, questionCount),
    t('studentQuiz.points', { count: points }, points),
  ]
  return parts.filter(Boolean).join(' - ')
})
const quizTimeLabel = computed(() => {
  const minutes = nonNegativeInteger(quiz.value.timeLimitMinutes)
  return minutes > 0 ? t('studentQuiz.timeLimit', { count: minutes }) : t('studentQuiz.noTimeLimit')
})
const currentQuestionNumber = computed(() =>
  Math.min(activeQuestionIndex.value + 1, Math.max(totalQuestionCount.value, 1)),
)
const answeredCount = computed(() => countAnsweredQuestions(orderedQuestions.value, answers))
const unansweredQuestionCount = computed(() => Math.max(0, totalQuestionCount.value - answeredCount.value))
const allQuestionsAnswered = computed(
  () => totalQuestionCount.value > 0 && answeredCount.value === totalQuestionCount.value,
)
const isLastQuestion = computed(() => activeQuestionIndex.value >= totalQuestionCount.value - 1)
const quizProgressPercent = computed(() =>
  clampPercent((currentQuestionNumber.value / Math.max(totalQuestionCount.value, 1)) * 100),
)
const submitButtonLabel = computed(() => {
  if (submitting.value) return t('studentQuiz.submitting')
  if (!allQuestionsAnswered.value) return t('studentQuiz.answerMore', { count: unansweredQuestionCount.value })
  return t('studentQuiz.submit')
})
const quizEmptyMessage = computed(() => {
  if (hasQuizSelection.value) return t('studentQuiz.emptyQuestions')
  if (requestedLessonId.value) return t('studentQuiz.emptyLesson')
  return t('studentQuiz.emptyAccount')
})
const attemptWrongAnswers = computed(() => wrongResultAnswers(resultData.value))
const recentWrongAnswers = computed(() => (wrongAnswersResource.data.value ?? []).slice(0, RECENT_WRONG_ANSWER_LIMIT))
const backLabel = computed(() => (quiz.value.lessonId > 0 ? t('studentQuiz.backToLesson') : t('studentQuiz.back')))
/** FR-LRN-02: Learn / Module / Lesson / Quiz. */
const quizBreadcrumb = computed<BreadcrumbItem[]>(() => [
  { kind: 'section', label: t('learningContext.learn'), to: { name: 'student-learn' } },
  { kind: 'module', label: quiz.value.moduleTitle },
  {
    kind: 'lesson',
    label: quiz.value.lessonTitle,
    to:
      quiz.value.lessonId > 0 ? { name: 'student-learn', query: { lessonId: String(quiz.value.lessonId) } } : undefined,
  },
  { kind: 'quiz', label: quiz.value.title },
])
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
      attemptWrongAnswers.value[0]?.questionText,
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

function sortedOptions(question: StudentQuizQuestionResponse): StudentQuizQuestionResponse['options'] {
  return [...question.options].sort((first, second) => first.displayOrder - second.displayOrder)
}

function textInputId(questionId: number): string {
  return `quiz-text-answer-${questionId}`
}

function textAnswerValue(questionId: number): string {
  const value = answers[questionId]
  return typeof value === 'string' ? value : ''
}

function chooseOption(questionId: number, optionId: number): void {
  answers[questionId] = optionId
  actionError.value = null
}

function setTextAnswer(questionId: number, value: string): void {
  answers[questionId] = value
  actionError.value = null
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

async function loadQuiz(): Promise<StudentQuizResponse> {
  const quizId = currentQuizId.value
  if (quizId) {
    return studentApi.quiz(quizId)
  }

  const quizPage = await studentApi.quizzes({
    lessonId: requestedLessonId.value ?? undefined,
    size: 20,
  })
  // Guided quizzes come first (D23); the backend already orders, the client sorts again defensively.
  const firstQuiz = sortByLearningStage(quizPage.items)[0]
  if (!firstQuiz) {
    return emptyQuiz
  }

  return studentApi.quiz(firstQuiz.id)
}

async function toggleHint(question: StudentQuizQuestionResponse): Promise<void> {
  if (hintLoadingId.value !== null) return
  if (openHintId.value === question.id) {
    openHintId.value = null
    return
  }

  hintErrors[question.id] = undefined
  if (hintTexts[question.id]) {
    openHintId.value = question.id
    return
  }

  if (!canRequestHint(question, quiz.value.learningStage)) return

  hintLoadingId.value = question.id
  const attempt = attemptSequence
  try {
    const hint = quizReadOnly.value
      ? (demoQuizHint(question.id)?.hint ?? '')
      : (await studentApi.quizHint(quiz.value.id, question.id)).hint
    if (attempt !== attemptSequence) return
    if (!hint?.trim()) {
      hintErrors[question.id] = t('studentQuiz.hintMissing')
      return
    }
    hintTexts[question.id] = hint.trim()
    hintsUsed[question.id] = true
    openHintId.value = question.id
  } catch (reason) {
    if (attempt !== attemptSequence) return
    const status = apiErrorStatus(reason)
    if (status === 403) hintErrors[question.id] = t('studentQuiz.hintIndependent')
    else if (status === 404) hintErrors[question.id] = t('studentQuiz.hintMissing')
    else hintErrors[question.id] = resolveApiError(reason, t('studentQuiz.hintError'))
  } finally {
    // resetAttempt() already cleared the loading state of an earlier attempt.
    if (attempt === attemptSequence) hintLoadingId.value = null
  }
}

function reloadAll(): void {
  void quizResource.reload()
  void progressResource.reload()
  void wrongAnswersResource.reload()
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

function goBack(): void {
  if (quiz.value.lessonId > 0) {
    void router.push({ name: 'student-learn', query: { lessonId: String(quiz.value.lessonId) } })
    return
  }
  openRecommendedPractice()
}

async function showResult(result: QuizAttemptResultResponse, preview: boolean): Promise<void> {
  resultData.value = result
  resultIsPreview.value = preview
  openHintId.value = null
  await nextTick()
  resultCard.value?.focus()
}

async function submitQuiz(): Promise<void> {
  if (submitting.value || resultData.value) return
  actionError.value = null
  if (!hasQuizSelection.value) {
    actionError.value = t('studentQuiz.chooseBeforeSubmit')
    return
  }

  if (!allQuestionsAnswered.value) {
    const firstMissingIndex = firstUnansweredIndex(orderedQuestions.value, answers)
    if (firstMissingIndex >= 0) goToQuestion(firstMissingIndex)
    actionError.value = t('studentQuiz.answerAll')
    return
  }

  const submission = buildQuizSubmission(orderedQuestions.value, answers, hintsUsed)
  if (quizReadOnly.value) {
    await showResult(gradeDemoQuiz(quiz.value, submission), true)
    return
  }

  submitting.value = true
  const attempt = attemptSequence
  try {
    const result = await studentApi.submitQuiz(quiz.value.id, submission)
    // A different quiz may have been opened (route change) while the answers were being saved.
    if (attempt === attemptSequence) await showResult(result, false)
    void wrongAnswersResource.reload()
    void progressResource.reload()
  } catch (reason) {
    if (attempt === attemptSequence) actionError.value = resolveApiError(reason, t('studentQuiz.submitError'))
  } finally {
    submitting.value = false
  }
}

function clearRecord(record: Record<number, unknown>): void {
  Object.keys(record).forEach((key) => {
    delete record[Number(key)]
  })
}

/** Clears answers, hints and the result so the learner can start a new attempt (retakes are unlimited). */
function resetAttempt(): void {
  attemptSequence += 1
  clearRecord(answers)
  clearRecord(hintTexts)
  clearRecord(hintsUsed)
  clearRecord(hintErrors)
  openHintId.value = null
  hintLoadingId.value = null
  resultData.value = null
  resultIsPreview.value = false
  activeQuestionIndex.value = 0
  actionError.value = null
}

function retakeQuiz(): void {
  resetAttempt()
  window.scrollTo({ top: 0, behavior: 'smooth' })
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

<style scoped>
.student-quiz-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 340px);
  gap: var(--page-gap);
  align-items: start;
}

.student-quiz-main,
.student-quiz-side {
  display: grid;
  gap: var(--page-gap);
  align-content: start;
  min-width: 0;
}

.student-quiz-card {
  display: grid;
  gap: var(--space-5);
  align-content: start;
}

.student-quiz-card-head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}

.student-quiz-card-head .card-title {
  flex: 1 1 auto;
  min-width: 0;
  overflow-wrap: anywhere;
}

.student-quiz-card-head > .btn {
  flex: 0 0 auto;
  white-space: nowrap;
}

.student-quiz-head-icon {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  background: var(--color-warning-soft);
  color: var(--color-warning-text);
}

.student-quiz-muted {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.student-quiz-label {
  margin-right: var(--space-1);
  color: var(--text-secondary);
  font-weight: var(--font-weight-semibold);
}

.student-quiz-subtitle {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-bold);
}

.student-quiz-note {
  margin: 0;
  padding: var(--space-2-5) var(--space-3-5);
  border: 1px solid var(--color-info-border);
  border-radius: var(--radius-md);
  background: var(--color-info-soft);
  color: var(--color-info-text);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
}

/* Question */
.student-quiz-progress {
  display: grid;
  grid-template-columns: auto minmax(120px, 1fr) auto;
  align-items: center;
  gap: var(--space-4);
  font-size: var(--font-size-sm);
}

.student-quiz-time {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1-5);
  color: var(--text-secondary);
  font-weight: var(--font-weight-semibold);
  white-space: nowrap;
}

.student-quiz-question {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-6) var(--space-5);
  border-radius: var(--radius-lg);
  background: linear-gradient(180deg, var(--color-primary-50), var(--surface-card));
  text-align: center;
}

.student-quiz-question h2 {
  margin: 0;
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-snug);
  overflow-wrap: anywhere;
}

.student-quiz-question p {
  margin: 0;
  color: var(--text-secondary);
}

.student-quiz-text-field {
  max-width: 520px;
}

/* Answer options: rest, hover, selected (check icon + text), focus and disabled. */
.student-quiz-options {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 260px), 1fr));
  gap: var(--space-3);
}

.student-quiz-option {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
  min-height: 56px;
  padding: var(--space-3) var(--space-4);
  border: var(--border-width-thick) solid var(--border-default);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
  color: var(--text-primary);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background var(--duration-fast) var(--ease-standard),
    box-shadow var(--duration-fast) var(--ease-standard);
}

.student-quiz-option:hover:not(:disabled) {
  border-color: var(--border-interactive);
  background: var(--surface-hover);
}

.student-quiz-option:active:not(:disabled) {
  background: var(--surface-selected);
}

.student-quiz-option.is-selected {
  border-color: var(--color-primary-500);
  background: var(--color-primary-50);
  box-shadow: var(--shadow-sm);
}

.student-quiz-option:disabled {
  border-color: var(--border-subtle);
  background: var(--surface-sunken);
  color: var(--text-secondary);
  cursor: not-allowed;
}

.student-quiz-option-letter {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-round);
  background: var(--surface-sunken);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-extrabold);
}

.student-quiz-option.is-selected .student-quiz-option-letter {
  border-color: transparent;
  background: var(--color-primary-500);
  color: var(--text-on-primary);
}

.student-quiz-option-text {
  flex: 1 1 auto;
  min-width: 0;
  overflow-wrap: anywhere;
}

.student-quiz-option-state {
  flex: 0 0 auto;
  color: var(--text-brand);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
}

/* Hint and tip */
.student-quiz-hint {
  display: grid;
  gap: var(--space-2);
  justify-items: start;
}

.student-quiz-hint-text {
  display: grid;
  gap: var(--space-1);
  width: 100%;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-accent-200);
  border-radius: var(--radius-md);
  background: var(--color-accent-50);
  color: var(--color-accent-900);
}

.student-quiz-hint-text p {
  margin: 0;
}

.student-quiz-tip {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.student-quiz-tip > svg {
  flex: 0 0 auto;
  color: var(--color-primary-500);
}

.student-quiz-tip strong {
  color: var(--text-primary);
}

/* Navigation and submit */
.student-quiz-nav {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: var(--space-3);
  padding-top: var(--space-4);
  border-top: 1px solid var(--border-subtle);
}

.student-quiz-answered {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  text-align: center;
}

.student-quiz-submit-row {
  display: grid;
  gap: var(--space-3);
  justify-items: end;
}

.student-quiz-submit-row .student-quiz-note {
  justify-self: stretch;
}

/* Result */
.student-quiz-result {
  outline: none;
}

.student-quiz-result:focus-visible {
  outline: var(--border-width-focus) solid var(--focus-ring-color);
  outline-offset: var(--focus-ring-offset);
}

.student-quiz-result-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.student-quiz-result-copy {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.student-quiz-result-copy span {
  color: var(--text-secondary);
}

.student-quiz-result-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-semibold);
}

.student-quiz-result-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 140px), 1fr));
  gap: var(--space-3);
  margin: 0;
}

.student-quiz-result-stats > div {
  display: grid;
  gap: var(--space-1);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-subtle);
}

.student-quiz-result-stats dt {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.student-quiz-result-stats dd {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
}

.student-quiz-result-list {
  display: grid;
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.student-quiz-result-item {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--border-subtle);
  border-left-width: 4px;
  border-radius: var(--radius-md);
  background: var(--surface-card);
  overflow-wrap: anywhere;
}

.student-quiz-result-item.is-correct {
  border-left-color: var(--color-success);
}

.student-quiz-result-item.is-wrong {
  border-left-color: var(--color-danger);
}

.student-quiz-result-item p {
  margin: 0;
}

.student-quiz-result-item-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-2);
}

.student-quiz-explanation {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.student-quiz-explanation strong {
  margin-right: var(--space-1);
  color: var(--text-primary);
}

.student-quiz-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-3);
}

/* Locked feedback and wrong answers */
.student-quiz-locked {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  border: 1px dashed var(--border-default);
  border-radius: var(--radius-xl);
  background: var(--surface-subtle);
}

.student-quiz-locked-icon {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}

.student-quiz-locked .card-title {
  font-size: var(--font-size-base);
}

.student-quiz-locked p {
  margin: var(--space-1) 0 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.student-quiz-review-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 240px), 1fr));
  gap: var(--space-3);
}

.student-quiz-review-card {
  display: grid;
  align-content: start;
  gap: var(--space-2);
  min-width: 0;
  padding: var(--space-4);
  border: 1px solid var(--border-subtle);
  border-top: 3px solid var(--color-danger);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
  font-size: var(--font-size-sm);
  overflow-wrap: anywhere;
}

.student-quiz-review-card strong {
  font-size: var(--font-size-base);
  line-height: var(--line-height-snug);
}

.student-quiz-review-card p {
  margin: 0;
}

.student-quiz-review-context {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
}

/* Side */
.student-quiz-side-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
  margin: 0;
}

.student-quiz-side-stats > div {
  display: grid;
  gap: var(--space-0-5);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--surface-subtle);
}

.student-quiz-side-stats dt {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
}

.student-quiz-side-stats dd {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
}

.student-quiz-performance {
  display: grid;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.student-quiz-performance-item {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2-5) var(--space-3);
  border-radius: var(--radius-md);
  background: var(--surface-subtle);
}

.student-quiz-performance-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}

.student-quiz-performance-copy {
  display: grid;
  min-width: 0;
}

.student-quiz-performance-copy strong {
  font-size: var(--font-size-sm);
  overflow-wrap: anywhere;
}

.student-quiz-performance-copy small {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
}

.student-quiz-performance-score {
  font-family: var(--font-display);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

.student-quiz-help {
  border-color: var(--color-primary-100);
  background: linear-gradient(160deg, var(--color-primary-50), var(--surface-card) 70%);
}

.student-quiz-ai-badge {
  background: var(--color-primary-500);
  color: var(--text-on-primary);
}

@media (max-width: 1200px) {
  .student-quiz-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .student-quiz-side {
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr));
    align-items: start;
  }
}

@media (max-width: 640px) {
  .student-quiz-progress {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .student-quiz-progress > :deep(.progress-bar) {
    grid-column: 1 / -1;
    grid-row: 2;
  }

  .student-quiz-question {
    padding: var(--space-5) var(--space-4);
  }

  .student-quiz-question h2 {
    font-size: var(--font-size-2xl);
  }

  .student-quiz-nav {
    grid-template-columns: 1fr 1fr;
  }

  .student-quiz-answered {
    grid-column: 1 / -1;
    grid-row: 1;
  }

  .student-quiz-submit-row > .btn,
  .student-quiz-actions > .btn {
    width: 100%;
  }
}
</style>
