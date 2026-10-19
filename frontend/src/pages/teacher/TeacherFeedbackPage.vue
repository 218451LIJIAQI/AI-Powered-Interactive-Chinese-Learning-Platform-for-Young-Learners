<template>
  <div class="page-stack teacher-feedback-page">
    <PageHeaderBlock
      :title="t('teacherFeedback.title')"
      :description="t('teacherFeedback.description')"
      icon="moderation"
    />

    <DataNotice :using-fallback="usingFallback" :error="actionError" :success="actionSuccess" />

    <!-- D13: feedback and practice review are approved-teacher features; a 403 also shows the locked panel. -->
    <TeacherApprovalLockPanel
      v-if="teacherApprovalLocked"
      :title="teacherApprovalTitle"
      :message="teacherApprovalMessage"
    />

    <ResourceSection v-else :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <div class="tabs teacher-feedback-tabs" role="group" :aria-label="t('teacherFeedback.modeAria')">
        <button
          v-for="tab in feedbackTabs"
          :key="tab"
          class="tab-button"
          :class="{ 'is-active': activeTab === tab }"
          type="button"
          :aria-pressed="activeTab === tab"
          @click="selectTab(tab)"
        >
          <IconGlyph :name="tab === 'History' ? 'history' : 'pen'" :size="16" aria-hidden="true" />
          {{ tabLabel(tab) }}
        </button>
      </div>

      <section class="teacher-feedback-layout">
        <aside class="panel teacher-feedback-queue" aria-labelledby="teacher-feedback-queue-title">
          <div class="card-header">
            <h2 id="teacher-feedback-queue-title" class="card-title teacher-feedback-queue-title">
              {{ queueTitle }}
              <span v-if="isHistoryTab" class="badge">{{ filteredFeedbackItems.length }}</span>
            </h2>
          </div>

          <template v-if="isHistoryTab">
            <div class="field">
              <label class="sr-only" for="teacher-feedback-filter">{{ t('teacherFeedback.filterAria') }}</label>
              <select id="teacher-feedback-filter" v-model="queueFilter" class="select">
                <option value="ALL">{{ t('teacherFeedback.allContexts') }}</option>
                <option value="QUIZ">{{ t('teacherFeedback.quiz') }}</option>
                <option value="LESSON">{{ t('teacherFeedback.lesson') }}</option>
                <option value="GENERAL">{{ t('teacherFeedback.general') }}</option>
              </select>
            </div>

            <ul v-if="filteredFeedbackItems.length" class="teacher-feedback-list">
              <li v-for="item in filteredFeedbackItems" :key="item.id">
                <button
                  type="button"
                  class="teacher-feedback-row"
                  :class="{ 'is-active': selectedFeedbackRecordId === item.id }"
                  :aria-pressed="selectedFeedbackRecordId === item.id"
                  @click="selectFeedback(item.id)"
                >
                  <PortraitAvatar :size="40" rounded="soft" :name="item.studentName" aria-hidden="true" />
                  <span class="teacher-feedback-row-copy">
                    <span class="teacher-feedback-row-head">
                      <strong>{{ item.studentName }}</strong>
                      <StatusPill :label="item.priority" :tone="priorityPillTone(item.priorityTone)" />
                    </span>
                    <span class="teacher-feedback-row-context">
                      <span class="chip">{{ item.typeLabel }}</span>
                      <span>{{ item.title }}</span>
                    </span>
                    <span class="type-caption">{{ item.createdLabel }}</span>
                  </span>
                </button>
              </li>
            </ul>

            <StatePanel
              v-else
              compact
              icon="search"
              :title="t('teacherFeedback.noFeedback')"
              :message="t('teacherFeedback.noFeedbackHint')"
            />
          </template>

          <div v-else class="teacher-feedback-create-help">
            <p>{{ t('teacherFeedback.createHelp') }}</p>
            <ol>
              <li>{{ t('teacherFeedback.createStepStudent') }}</li>
              <li>{{ t('teacherFeedback.createStepContext') }}</li>
              <li>{{ t('teacherFeedback.createStepMessage') }}</li>
            </ol>
          </div>

          <div class="card-footer teacher-feedback-queue-footer">
            <button class="btn btn-secondary btn-sm" type="button" @click="toggleTab">
              <IconGlyph :name="isHistoryTab ? 'pen' : 'history'" :size="16" aria-hidden="true" />
              {{ tabToggleLabel }}
            </button>
          </div>
        </aside>

        <div class="teacher-feedback-main">
          <article class="panel teacher-feedback-detail" aria-labelledby="teacher-feedback-detail-title">
            <template v-if="isHistoryTab && selectedFeedbackItem">
              <div class="teacher-feedback-detail-head">
                <div class="teacher-feedback-person">
                  <PortraitAvatar
                    :size="56"
                    rounded="soft"
                    :name="selectedFeedbackItem.studentName"
                    aria-hidden="true"
                  />
                  <div class="teacher-feedback-person-copy">
                    <h2 id="teacher-feedback-detail-title" class="teacher-feedback-person-name">
                      {{ selectedFeedbackItem.studentName }}
                    </h2>
                    <span class="teacher-feedback-muted">{{ selectedFeedbackItem.studentMeta }}</span>
                  </div>
                </div>
                <div class="teacher-feedback-detail-side">
                  <StatusPill
                    :label="selectedFeedbackItem.priority"
                    :tone="priorityPillTone(selectedFeedbackItem.priorityTone)"
                  />
                  <span class="type-caption">
                    {{ t('teacherFeedback.savedAt', { date: selectedFeedbackItem.createdExact }) }}
                  </span>
                </div>
              </div>

              <div class="teacher-feedback-detail-grid">
                <section class="teacher-feedback-tile is-tall">
                  <dl class="teacher-feedback-facts">
                    <div>
                      <dt>{{ t('teacherFeedback.feedbackType') }}</dt>
                      <dd>{{ selectedFeedbackItem.typeDisplay }}</dd>
                    </div>
                    <div>
                      <dt>{{ t('teacherFeedback.context') }}</dt>
                      <dd>
                        {{ selectedFeedbackItem.title }}
                        <span class="teacher-feedback-muted">{{ selectedFeedbackItem.contextLine }}</span>
                      </dd>
                    </div>
                    <div>
                      <dt>{{ t('teacherFeedback.latestProgress') }}</dt>
                      <dd>
                        <strong class="teacher-feedback-score">{{ selectedFeedbackItem.progressLabel }}</strong>
                        <span class="teacher-feedback-muted">{{ selectedFeedbackItem.quizAverageLabel }}</span>
                      </dd>
                    </div>
                    <div>
                      <dt>{{ t('teacherFeedback.linkedStudent') }}</dt>
                      <dd>
                        {{ selectedFeedbackItem.studentName }}
                        <span class="teacher-feedback-muted">{{ selectedFeedbackItem.feedbackTypeLabel }}</span>
                      </dd>
                    </div>
                  </dl>
                </section>

                <section class="teacher-feedback-tile" aria-labelledby="teacher-feedback-saved-title">
                  <h3 id="teacher-feedback-saved-title" class="teacher-feedback-tile-title">
                    {{ t('teacherFeedback.savedMessage') }}
                  </h3>
                  <blockquote class="teacher-feedback-quote">{{ selectedFeedbackItem.message }}</blockquote>
                  <button class="btn btn-secondary btn-sm" type="button" @click="prepareEditFromSelected">
                    <IconGlyph name="pen" :size="16" aria-hidden="true" />
                    {{ t('teacherFeedback.editFeedback') }}
                  </button>
                </section>

                <section class="teacher-feedback-tile" aria-labelledby="teacher-feedback-snapshot-title">
                  <div class="teacher-feedback-tile-head">
                    <h3 id="teacher-feedback-snapshot-title" class="teacher-feedback-tile-title">
                      {{ t('teacherFeedback.snapshot') }}
                    </h3>
                    <button class="btn btn-link" type="button" @click="openStudents">
                      {{ t('teacherFeedback.viewStudents') }}
                    </button>
                  </div>
                  <ul class="teacher-feedback-snapshot">
                    <li v-for="metric in progressMetrics" :key="metric.label">
                      <ProgressRing
                        :progress="metric.value"
                        :title="metric.label"
                        :subtitle="metric.caption"
                        :tone="metric.tone"
                        size="sm"
                      />
                    </li>
                  </ul>
                </section>
              </div>
            </template>

            <template v-else>
              <div class="teacher-feedback-detail-head">
                <div class="teacher-feedback-person">
                  <PortraitAvatar :size="56" rounded="soft" :name="selectedStudentOption?.label" aria-hidden="true" />
                  <div class="teacher-feedback-person-copy">
                    <h2 id="teacher-feedback-detail-title" class="teacher-feedback-person-name">
                      {{ selectedStudentOption?.label || t('teacherFeedback.chooseStudent') }}
                    </h2>
                    <span class="teacher-feedback-muted">
                      {{ selectedStudentOption?.meta || t('teacherFeedback.linkedOnly') }}
                    </span>
                  </div>
                </div>
                <div class="teacher-feedback-detail-side">
                  <StatusPill :label="t('teacherFeedback.draft')" tone="info" icon="pen" />
                  <span class="type-caption">{{ t('teacherFeedback.createRecord') }}</span>
                </div>
              </div>

              <div class="form-grid two teacher-feedback-context">
                <div class="field">
                  <label class="field-label" for="teacher-feedback-student">
                    {{ t('teacherFeedback.student') }}<span class="required-mark" aria-hidden="true">*</span>
                  </label>
                  <select
                    id="teacher-feedback-student"
                    v-model.number="commentForm.studentId"
                    class="select"
                    required
                    :disabled="feedbackFormDisabled"
                  >
                    <option :value="0">{{ t('teacherFeedback.selectStudent') }}</option>
                    <option v-for="option in studentOptions" :key="option.studentId" :value="option.studentId">
                      {{ option.label }}
                    </option>
                  </select>
                </div>

                <div class="field">
                  <label class="field-label" for="teacher-feedback-type">{{ t('teacherFeedback.feedbackType') }}</label>
                  <select
                    id="teacher-feedback-type"
                    v-model="commentForm.feedbackType"
                    class="select"
                    :disabled="feedbackFormDisabled"
                  >
                    <option value="GENERAL">{{ t('teacherFeedback.general') }}</option>
                    <option value="PROGRESS">{{ t('teacherFeedback.progress') }}</option>
                    <option value="ENCOURAGING">{{ t('teacherFeedback.encouraging') }}</option>
                    <option value="CORRECTIVE">{{ t('teacherFeedback.corrective') }}</option>
                    <option value="QUIZ">{{ t('teacherFeedback.quiz') }}</option>
                  </select>
                </div>

                <div class="field">
                  <label class="field-label" for="teacher-feedback-lesson">
                    {{ t('teacherFeedback.lessonOptional') }}
                  </label>
                  <select
                    id="teacher-feedback-lesson"
                    v-model.number="commentForm.lessonId"
                    class="select"
                    :disabled="feedbackFormDisabled"
                  >
                    <option :value="0">{{ t('teacherFeedback.noLesson') }}</option>
                    <option v-for="lesson in lessonOptions" :key="lesson.id" :value="lesson.id">
                      {{ lesson.title }}
                    </option>
                  </select>
                </div>

                <div class="field">
                  <label class="field-label" for="teacher-feedback-quiz">{{ t('teacherFeedback.quizOptional') }}</label>
                  <select
                    id="teacher-feedback-quiz"
                    v-model.number="selectedQuizId"
                    class="select"
                    :aria-busy="loadingQuizzes"
                    :disabled="feedbackFormDisabled || !commentForm.lessonId || loadingQuizzes"
                  >
                    <option :value="0">{{ quizPlaceholder }}</option>
                    <option v-for="quiz in quizOptions" :key="quiz.id" :value="quiz.id">
                      {{ quiz.title }}
                    </option>
                  </select>
                </div>

                <div class="field teacher-feedback-context-wide">
                  <label class="field-label" for="teacher-feedback-quiz-attempt">
                    {{ t('teacherFeedback.attemptOptional') }}
                  </label>
                  <select
                    id="teacher-feedback-quiz-attempt"
                    v-model.number="commentForm.quizAttemptId"
                    class="select"
                    :aria-busy="loadingQuizAttempts"
                    :disabled="feedbackFormDisabled || !selectedQuizId || loadingQuizAttempts"
                  >
                    <option :value="0">
                      {{ quizAttemptPlaceholder }}
                    </option>
                    <option v-for="attempt in quizAttemptOptions" :key="attempt.attemptId" :value="attempt.attemptId">
                      {{ attempt.studentName }} · {{ attempt.score }}/{{ attempt.maxScore }} ·
                      {{ formatDateTime(attempt.submittedAt) }}
                    </option>
                  </select>
                </div>
              </div>
            </template>
          </article>

          <form
            class="panel teacher-feedback-composer"
            aria-labelledby="teacher-feedback-composer-title"
            @submit.prevent="submitFeedback"
          >
            <h2 id="teacher-feedback-composer-title" class="card-title">{{ commentCardTitle }}</h2>

            <div class="teacher-feedback-composer-grid">
              <div class="field teacher-feedback-message">
                <label class="field-label" for="teacher-feedback-message">
                  {{ t('teacherFeedback.messageAria') }}<span class="required-mark" aria-hidden="true">*</span>
                </label>
                <textarea
                  id="teacher-feedback-message"
                  v-model="commentForm.message"
                  class="textarea teacher-feedback-textarea"
                  required
                  :maxlength="PRACTICE_FEEDBACK_MAX_LENGTH"
                  :aria-invalid="Boolean(feedbackMessageError) || undefined"
                  :aria-describedby="feedbackDescribedBy"
                  :disabled="feedbackFormDisabled"
                />
                <div class="teacher-feedback-field-foot">
                  <p id="teacher-feedback-message-hint" class="field-hint">
                    {{ t('teacherFeedback.practiceLengthHint') }} · {{ t('teacherFeedback.plainText') }} ·
                    {{ t('teacherFeedback.learnerFriendly') }}
                  </p>
                  <span class="field-hint teacher-feedback-counter">
                    {{ commentLength }}/{{ PRACTICE_FEEDBACK_MAX_LENGTH }}
                  </span>
                </div>
                <p v-if="feedbackMessageError" id="teacher-feedback-message-error" class="field-error" role="alert">
                  {{ feedbackMessageError }}
                </p>
              </div>

              <section class="teacher-feedback-guidance" aria-labelledby="teacher-feedback-guidance-title">
                <div class="teacher-feedback-tile-head">
                  <h3 id="teacher-feedback-guidance-title" class="teacher-feedback-tile-title">
                    <IconGlyph name="hint" :size="16" aria-hidden="true" />
                    {{ t('teacherFeedback.draftGuidance') }}
                  </h3>
                  <button
                    class="btn btn-link"
                    type="button"
                    :disabled="feedbackFormDisabled"
                    @click="regenerateSuggestion"
                  >
                    <IconGlyph name="refresh" :size="14" aria-hidden="true" />
                    {{ t('teacherFeedback.regenerate') }}
                  </button>
                </div>
                <p class="teacher-feedback-muted">{{ t('teacherFeedback.draftHint') }}</p>
                <blockquote class="teacher-feedback-quote" aria-live="polite">{{ draftGuidance }}</blockquote>
                <button
                  class="btn btn-secondary btn-sm"
                  type="button"
                  :disabled="feedbackFormDisabled"
                  @click="useSuggestedDraft"
                >
                  {{ t('teacherFeedback.useGuidance') }}
                </button>
              </section>
            </div>

            <div class="card-footer teacher-feedback-actions">
              <p class="teacher-feedback-actions-note">{{ t('teacherFeedback.savedEditable') }}</p>
              <div class="teacher-feedback-actions-buttons" role="group" :aria-label="t('teacherFeedback.actions')">
                <button
                  class="btn btn-ghost"
                  type="button"
                  :disabled="feedbackFormDisabled"
                  @click="prepareRetryMessage"
                >
                  {{ t('teacherFeedback.retryNote') }}
                </button>
                <button
                  class="btn btn-secondary"
                  type="button"
                  :disabled="feedbackFormDisabled"
                  @click="openCreateForSelectedStudent"
                >
                  {{ t('teacherFeedback.createNew') }}
                </button>
                <button
                  class="btn btn-primary"
                  type="submit"
                  :aria-busy="submittingCreate || submittingUpdate"
                  :disabled="feedbackReadOnly || submittingCreate || submittingUpdate"
                >
                  {{ submitFeedbackLabel }}
                </button>
              </div>
            </div>
          </form>
        </div>
      </section>

      <TeacherPracticeReviewSection
        :locked="teacherApprovalLocked"
        @forbidden="markApprovalLocked"
        @reviewed="onPracticeReviewed"
      />

      <p class="teacher-feedback-tip">
        <IconGlyph name="hint" :size="16" aria-hidden="true" />
        <strong>{{ t('teacherFeedback.tip') }}</strong>
        <span>{{ t('teacherFeedback.tipCopy') }}</span>
      </p>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { teacherApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import ProgressRing from '@/components/common/ProgressRing.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import TeacherApprovalLockPanel from '@/components/teacher/TeacherApprovalLockPanel.vue'
import TeacherPracticeReviewSection from '@/components/teacher/TeacherPracticeReviewSection.vue'
import { useResource } from '@/composables/useResource'
import { useStatusLabels } from '@/composables/useStatusLabels'
import { useTeacherAccess } from '@/composables/useTeacherAccess'
import {
  teacherFeedback,
  teacherLessons,
  teacherQuizAttempts,
  teacherQuizzes,
  teacherStudentSummaries,
} from '@/demo/data'
import { teacherFeedbackMessages } from '@/plugins/teacherFeedbackMessages'
import type {
  LessonResponse,
  TeacherFeedbackResponse,
  TeacherQuizAttemptSummaryResponse,
  TeacherQuizResponse,
  TeacherStudentSummaryResponse,
} from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { clampPercent, formatDateTime, formatPercent } from '@/utils/format'
import {
  PRACTICE_FEEDBACK_MAX_LENGTH,
  PRACTICE_FEEDBACK_MIN_LENGTH,
  validatePracticeFeedbackMessage,
} from '@/utils/teacherPracticeReview'

const router = useRouter()
const { t } = useI18n({
  messages: {
    en: { teacherFeedback: teacherFeedbackMessages.en },
    ms: { teacherFeedback: teacherFeedbackMessages.ms },
    'zh-CN': { teacherFeedback: teacherFeedbackMessages['zh-CN'] },
  },
})
const { statusLabel } = useStatusLabels()
const { teacherApprovalLocked, teacherApprovalTitle, teacherApprovalMessage, guardTeacherRequest, markApprovalLocked } =
  useTeacherAccess()

type QueueFilter = 'ALL' | 'QUIZ' | 'LESSON' | 'GENERAL'
type FeedbackTab = 'History' | 'Create'

interface StudentOption extends TeacherStudentSummaryResponse {
  label: string
  meta: string
}

interface FeedbackQueueItem {
  id: number
  studentId: number
  studentName: string
  studentMeta: string
  title: string
  contextLine: string
  message: string
  typeLabel: string
  typeDisplay: string
  feedbackTypeLabel: string
  createdLabel: string
  createdExact: string
  priority: string
  priorityTone: 'high' | 'medium' | 'low'
  lessonId?: number | null
  quizAttemptId?: number | null
  progressLabel: string
  quizAverageLabel: string
  category: 'QUIZ' | 'LESSON' | 'GENERAL'
}

const feedbackResource = useResource(
  () => (teacherApprovalLocked.value ? Promise.resolve([]) : guardTeacherRequest(() => teacherApi.feedbackList())),
  teacherFeedback,
)
const studentsResource = useResource(
  () => (teacherApprovalLocked.value ? Promise.resolve([]) : guardTeacherRequest(() => teacherApi.students())),
  teacherStudentSummaries,
)
const lessonsResource = useResource(
  () => (teacherApprovalLocked.value ? Promise.resolve([]) : guardTeacherRequest(() => teacherApi.lessons())),
  teacherLessons,
)

const feedbackTabs: FeedbackTab[] = ['History', 'Create']
const activeTab = ref<FeedbackTab>('History')
const queueFilter = ref<QueueFilter>('ALL')
const selectedFeedbackRecordId = ref<number>(0)
const selectedQuizId = ref<number>(0)
const draftSeed = ref(0)
const loadingQuizzes = ref(false)
const loadingQuizAttempts = ref(false)
const submittingCreate = ref(false)
const submittingUpdate = ref(false)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
/** Message validation problems are also shown under the textarea (the page notice can be far away). */
const feedbackMessageError = ref<string | null>(null)
const quizzes = ref<TeacherQuizResponse[]>([])
const quizAttempts = ref<TeacherQuizAttemptSummaryResponse[]>([])
// Request counters: when the lesson or quiz changes again, a slower earlier response is ignored.
let quizzesRequest = 0
let quizAttemptsRequest = 0

const feedback = computed<TeacherFeedbackResponse[]>(
  () => feedbackResource.data.value ?? (feedbackResource.usingFallback.value ? teacherFeedback : []),
)
const lessons = computed<LessonResponse[]>(
  () => lessonsResource.data.value ?? (lessonsResource.usingFallback.value ? teacherLessons : []),
)
const studentOptions = computed<StudentOption[]>(() =>
  (studentsResource.data.value ?? []).map((item: TeacherStudentSummaryResponse) => ({
    ...item,
    progressPercent: clampPercent(item.progressPercent),
    quizAveragePercent: item.quizAveragePercent == null ? null : clampPercent(item.quizAveragePercent),
    label: item.displayName?.trim() || item.fullName,
    meta: item.currentLevel?.trim() || t('teacherFeedback.levelNotSet'),
  })),
)
const lessonOptions = computed<LessonResponse[]>(() =>
  lessons.value.filter((item: LessonResponse) => item.contentStatus !== 'ARCHIVED'),
)
const quizOptions = computed<TeacherQuizResponse[]>(() => quizzes.value)
const quizAttemptOptions = computed<TeacherQuizAttemptSummaryResponse[]>(() =>
  quizAttempts.value.filter(
    (item: TeacherQuizAttemptSummaryResponse) => !commentForm.studentId || item.studentId === commentForm.studentId,
  ),
)

const pageLoading = computed(
  () => feedbackResource.loading.value || studentsResource.loading.value || lessonsResource.loading.value,
)
const pageError = computed(() =>
  teacherApprovalLocked.value
    ? null
    : feedbackResource.error.value || studentsResource.error.value || lessonsResource.error.value,
)
const usingFallback = computed(
  () =>
    feedbackResource.usingFallback.value || studentsResource.usingFallback.value || lessonsResource.usingFallback.value,
)
const feedbackReadOnly = computed(() => teacherApprovalLocked.value || usingFallback.value)
const feedbackFormDisabled = computed(() => feedbackReadOnly.value || submittingCreate.value || submittingUpdate.value)
const isHistoryTab = computed(() => activeTab.value === 'History')
const queueTitle = computed(() =>
  isHistoryTab.value ? t('teacherFeedback.feedbackHistory') : t('teacherFeedback.createFeedback'),
)
const tabToggleLabel = computed(() =>
  isHistoryTab.value ? t('teacherFeedback.switchCreate') : t('teacherFeedback.openHistory'),
)
const commentCardTitle = computed(() =>
  isHistoryTab.value ? t('teacherFeedback.editMessage') : t('teacherFeedback.teacherComment'),
)
const quizPlaceholder = computed(() =>
  loadingQuizzes.value ? t('teacherFeedback.loadingQuizzes') : t('teacherFeedback.noQuizContext'),
)
const quizAttemptPlaceholder = computed(() =>
  loadingQuizAttempts.value ? t('teacherFeedback.loadingAttempts') : t('teacherFeedback.noAttempt'),
)
const submitFeedbackLabel = computed(() => {
  if (isEditingExistingFeedback.value) {
    return submittingUpdate.value ? t('teacherFeedback.saving') : t('teacherFeedback.saveFeedback')
  }

  return submittingCreate.value ? t('teacherFeedback.sending') : t('teacherFeedback.sendFeedback')
})

const commentForm = reactive({
  studentId: 0,
  lessonId: 0,
  quizAttemptId: 0,
  feedbackType: 'GENERAL',
  message: '',
})

const feedbackItems = computed<FeedbackQueueItem[]>(() =>
  feedback.value.map((item: TeacherFeedbackResponse) => {
    const linkedStudent = studentOptions.value.find((student: StudentOption) => student.studentId === item.studentId)
    const category = feedbackCategory(item)
    const quizAverage =
      linkedStudent?.quizAveragePercent == null
        ? t('teacherFeedback.noQuizData')
        : t('teacherFeedback.quizAverage', { average: formatPercent(linkedStudent.quizAveragePercent) })
    const progress = linkedStudent
      ? t('teacherFeedback.progressValue', { progress: formatPercent(linkedStudent.progressPercent) })
      : t('teacherFeedback.progressUnavailable')

    return {
      id: item.id,
      studentId: item.studentId,
      studentName: item.studentName,
      studentMeta: linkedStudent?.meta || t('teacherFeedback.linkedLearner'),
      title: item.quizTitle || item.lessonTitle || feedbackTypeLabel(item.feedbackType),
      contextLine: feedbackContextLine(item),
      message: item.message,
      typeLabel: feedbackCategoryLabel(category),
      typeDisplay: feedbackCategoryDisplay(category),
      feedbackTypeLabel: feedbackTypeLabel(item.feedbackType),
      createdLabel: formatDateTime(item.createdAt),
      createdExact: formatDateTime(item.createdAt),
      priority: feedbackPriorityLabel(linkedStudent),
      priorityTone: feedbackPriorityTone(linkedStudent),
      lessonId: item.lessonId,
      quizAttemptId: item.quizAttemptId,
      progressLabel: progress,
      quizAverageLabel: quizAverage,
      category,
    }
  }),
)

const filteredFeedbackItems = computed(() =>
  feedbackItems.value.filter((item: FeedbackQueueItem) =>
    queueFilter.value === 'ALL' ? true : item.category === queueFilter.value,
  ),
)

const selectedFeedbackItem = computed<FeedbackQueueItem | null>(
  () =>
    filteredFeedbackItems.value.find((item: FeedbackQueueItem) => item.id === selectedFeedbackRecordId.value) ??
    filteredFeedbackItems.value[0] ??
    null,
)
const selectedExistingFeedback = computed<TeacherFeedbackResponse | null>(
  () => feedback.value.find((item: TeacherFeedbackResponse) => item.id === selectedFeedbackRecordId.value) ?? null,
)
const isEditingExistingFeedback = computed(
  () => activeTab.value === 'History' && selectedExistingFeedback.value != null,
)
const selectedStudentOption = computed<StudentOption | null>(
  () => studentOptions.value.find((item: StudentOption) => item.studentId === commentForm.studentId) ?? null,
)

const progressMetrics = computed(() => {
  const student = studentOptions.value.find(
    (item: StudentOption) => item.studentId === (selectedExistingFeedback.value?.studentId ?? commentForm.studentId),
  )
  if (!student) {
    return []
  }

  return [
    {
      label: t('teacherFeedback.overallProgress'),
      value: clampPercent(student.progressPercent),
      caption: t('teacherFeedback.linkedLessonProgress'),
      tone: 'primary' as const,
    },
    {
      label: t('teacherFeedback.quizAverageLabel'),
      value: clampPercent(student.quizAveragePercent ?? 0),
      caption:
        student.quizAveragePercent == null
          ? t('teacherFeedback.noQuizAttempts')
          : t('teacherFeedback.submittedAttempts'),
      tone: 'primary' as const,
    },
    {
      label: t('teacherFeedback.riskSignal'),
      value: student.atRisk ? 100 : 0,
      caption: student.atRisk ? t('teacherFeedback.followupRecommended') : t('teacherFeedback.noRisk'),
      // An at-risk learner is a caution signal, not progress.
      tone: student.atRisk ? ('warning' as const) : ('primary' as const),
    },
  ]
})

const draftGuidance = computed(() => {
  const student =
    selectedStudentOption.value?.label.split(' ')[0] ??
    selectedFeedbackItem.value?.studentName.split(' ')[0] ??
    t('teacherFeedback.learner')
  const weakest =
    selectedStudentOption.value?.weakAreas[0] ??
    studentOptions.value.find((item: StudentOption) => item.studentId === selectedFeedbackItem.value?.studentId)
      ?.weakAreas[0] ??
    t('teacherFeedback.nextTarget')

  const variants = [
    t('teacherFeedback.guidanceOne', { student, target: weakest.toLowerCase() }),
    t('teacherFeedback.guidanceTwo', { student, target: weakest.toLowerCase() }),
    t('teacherFeedback.guidanceThree'),
  ]

  return variants[draftSeed.value % variants.length]
})

const commentLength = computed(() => commentForm.message.length)
const feedbackDescribedBy = computed(() =>
  feedbackMessageError.value
    ? 'teacher-feedback-message-error teacher-feedback-message-hint'
    : 'teacher-feedback-message-hint',
)

watch(
  filteredFeedbackItems,
  (items: FeedbackQueueItem[]) => {
    if (!items.some((item: FeedbackQueueItem) => item.id === selectedFeedbackRecordId.value)) {
      selectedFeedbackRecordId.value = items[0]?.id ?? 0
    }
  },
  { immediate: true, deep: true },
)

watch(
  [selectedExistingFeedback, studentOptions, activeTab],
  () => {
    if (activeTab.value === 'History' && selectedExistingFeedback.value) {
      syncCommentFormFromExisting()
      return
    }

    if (activeTab.value === 'Create' && !commentForm.studentId) {
      commentForm.studentId = studentOptions.value[0]?.studentId ?? 0
    }
  },
  { immediate: true, deep: true },
)

watch(
  [() => commentForm.lessonId, activeTab],
  ([lessonId, tab]: [number, FeedbackTab]) => {
    if (tab !== 'Create') return
    selectedQuizId.value = 0
    commentForm.quizAttemptId = 0
    if (!lessonId) {
      quizzesRequest++
      quizAttemptsRequest++
      loadingQuizzes.value = false
      loadingQuizAttempts.value = false
      quizzes.value = []
      quizAttempts.value = []
      return
    }
    void loadQuizzesForLesson(lessonId)
  },
  { immediate: true },
)

watch(
  [selectedQuizId, activeTab],
  ([quizId, tab]: [number, FeedbackTab]) => {
    if (tab !== 'Create') return
    commentForm.quizAttemptId = 0
    if (!quizId) {
      quizAttemptsRequest++
      loadingQuizAttempts.value = false
      quizAttempts.value = []
      return
    }
    void loadQuizAttempts(quizId)
  },
  { immediate: true },
)

watch(
  [() => commentForm.studentId, quizAttemptOptions],
  () => {
    if (
      commentForm.quizAttemptId &&
      !quizAttemptOptions.value.some(
        (item: TeacherQuizAttemptSummaryResponse) => item.attemptId === commentForm.quizAttemptId,
      )
    ) {
      commentForm.quizAttemptId = 0
    }
  },
  { immediate: true },
)

function feedbackCategory(item: TeacherFeedbackResponse): FeedbackQueueItem['category'] {
  if (item.quizAttemptId != null) return 'QUIZ'
  if (item.lessonId != null) return 'LESSON'
  return 'GENERAL'
}

function feedbackContextLine(item: TeacherFeedbackResponse): string {
  if (item.quizTitle) return t('teacherFeedback.quizAttempt', { id: item.quizAttemptId ?? '--' })
  if (item.lessonTitle) return t('teacherFeedback.lessonContext', { id: item.lessonId ?? '--' })
  return t('teacherFeedback.generalFeedback')
}

function feedbackCategoryLabel(category: FeedbackQueueItem['category']): string {
  if (category === 'QUIZ') return t('teacherFeedback.quiz')
  if (category === 'LESSON') return t('teacherFeedback.lesson')
  return t('teacherFeedback.general')
}

function feedbackCategoryDisplay(category: FeedbackQueueItem['category']): string {
  if (category === 'QUIZ') return t('teacherFeedback.quizFeedback')
  if (category === 'LESSON') return t('teacherFeedback.lessonFeedback')
  return t('teacherFeedback.generalFeedbackTitle')
}

// Priority reflects real roster data only: follow-up when the linked student is flagged at risk.
function feedbackPriorityLabel(student: StudentOption | undefined): string {
  return student?.atRisk ? t('teacherFeedback.followup') : t('teacherFeedback.saved')
}

function tabLabel(tab: FeedbackTab): string {
  return tab === 'History' ? t('teacherFeedback.history') : t('teacherFeedback.create')
}

function feedbackTypeLabel(type: string | null | undefined): string {
  return statusLabel('feedbackType', type?.trim() ? type.trim().toUpperCase() : 'GENERAL')
}

function onPracticeReviewed(): void {
  // The review is also stored as a teacher feedback record, so refresh the history list.
  void feedbackResource.reload()
}

function priorityPillTone(tone: FeedbackQueueItem['priorityTone']): 'warning' | 'info' | 'success' {
  if (tone === 'high') return 'warning'
  if (tone === 'medium') return 'info'
  return 'success'
}

function feedbackPriorityTone(student: StudentOption | undefined): FeedbackQueueItem['priorityTone'] {
  return student?.atRisk ? 'high' : 'low'
}

async function loadQuizzesForLesson(lessonId: number): Promise<void> {
  const request = ++quizzesRequest
  if (teacherApprovalLocked.value) {
    quizzes.value = []
    loadingQuizzes.value = false
    return
  }

  loadingQuizzes.value = true
  try {
    const items = usingFallback.value
      ? teacherQuizzes.filter((item: TeacherQuizResponse) => item.lessonId === lessonId)
      : await teacherApi.lessonQuizzes(lessonId)
    if (request === quizzesRequest) quizzes.value = items
  } catch (reason) {
    if (request !== quizzesRequest) return
    quizzes.value = []
    actionError.value = resolveApiError(reason, t('teacherFeedback.loadQuizzesError'))
  } finally {
    if (request === quizzesRequest) loadingQuizzes.value = false
  }
}

async function loadQuizAttempts(quizId: number): Promise<void> {
  const request = ++quizAttemptsRequest
  if (teacherApprovalLocked.value) {
    quizAttempts.value = []
    loadingQuizAttempts.value = false
    return
  }

  loadingQuizAttempts.value = true
  try {
    const items = usingFallback.value ? teacherQuizAttempts : await teacherApi.quizAttempts(quizId)
    if (request === quizAttemptsRequest) quizAttempts.value = items
  } catch (reason) {
    if (request !== quizAttemptsRequest) return
    quizAttempts.value = []
    actionError.value = resolveApiError(reason, t('teacherFeedback.loadAttemptsError'))
  } finally {
    if (request === quizAttemptsRequest) loadingQuizAttempts.value = false
  }
}

function syncCommentFormFromExisting(): void {
  const item = selectedExistingFeedback.value
  if (!item) {
    return
  }

  commentForm.studentId = item.studentId
  commentForm.lessonId = item.lessonId ?? 0
  commentForm.quizAttemptId = item.quizAttemptId ?? 0
  commentForm.feedbackType = item.feedbackType || 'GENERAL'
  commentForm.message = item.message
}

function selectTab(tab: FeedbackTab): void {
  activeTab.value = tab
}

function selectFeedback(id: number): void {
  selectedFeedbackRecordId.value = id
  activeTab.value = 'History'
  syncCommentFormFromExisting()
}

function prepareEditFromSelected(): void {
  activeTab.value = 'History'
  syncCommentFormFromExisting()
}

function openStudents(): void {
  void router.push('/teacher/students')
}

function openCreateForSelectedStudent(): void {
  activeTab.value = 'Create'
  const candidateStudentId = selectedFeedbackItem.value?.studentId ?? studentOptions.value[0]?.studentId ?? 0
  commentForm.studentId = candidateStudentId
  commentForm.lessonId = 0
  commentForm.quizAttemptId = 0
  selectedQuizId.value = 0
  quizzes.value = []
  quizAttempts.value = []
  commentForm.feedbackType = 'GENERAL'
  commentForm.message = ''
}

function regenerateSuggestion(): void {
  draftSeed.value += 1
}

function useSuggestedDraft(): void {
  commentForm.message = commentForm.message.trim()
    ? `${commentForm.message.trim()} ${draftGuidance.value}`
    : draftGuidance.value
  actionError.value = null
  actionSuccess.value = t('teacherFeedback.guidanceAdded')
}

function prepareRetryMessage(): void {
  commentForm.feedbackType = 'CORRECTIVE'
  commentForm.message = t('teacherFeedback.retryMessage')
  actionError.value = null
  actionSuccess.value = t('teacherFeedback.retryReady')
}

function toggleTab(): void {
  selectTab(isHistoryTab.value ? 'Create' : 'History')
  if (activeTab.value === 'Create') {
    openCreateForSelectedStudent()
  } else if (selectedExistingFeedback.value) {
    syncCommentFormFromExisting()
  }
}

async function submitFeedback(): Promise<void> {
  if (submittingCreate.value || submittingUpdate.value) return
  actionError.value = null
  actionSuccess.value = null
  feedbackMessageError.value = null

  if (feedbackReadOnly.value) {
    actionError.value = teacherApprovalLocked.value
      ? teacherApprovalMessage.value
      : t('teacherFeedback.previewMutation')
    return
  }

  // Same 5..2000 character rule as the backend feedback request.
  switch (validatePracticeFeedbackMessage(commentForm.message)) {
    case 'required':
      actionError.value = t('teacherFeedback.messageRequired')
      feedbackMessageError.value = actionError.value
      return
    case 'tooShort':
      actionError.value = t('teacherFeedback.practiceErrorTooShort', { min: PRACTICE_FEEDBACK_MIN_LENGTH })
      feedbackMessageError.value = actionError.value
      return
    case 'tooLong':
      actionError.value = t('teacherFeedback.practiceErrorTooLong', { max: PRACTICE_FEEDBACK_MAX_LENGTH })
      feedbackMessageError.value = actionError.value
      return
  }

  if (selectedExistingFeedback.value && activeTab.value === 'History') {
    submittingUpdate.value = true
    const feedbackId = selectedExistingFeedback.value.id
    try {
      await guardTeacherRequest(() =>
        teacherApi.updateFeedback(feedbackId, {
          feedbackType: commentForm.feedbackType,
          message: commentForm.message.trim(),
        }),
      )
      await feedbackResource.reload()
      syncCommentFormFromExisting()
      actionSuccess.value = t('teacherFeedback.updated')
    } catch (reason) {
      actionError.value = resolveApiError(reason, t('teacherFeedback.updateError'))
    } finally {
      submittingUpdate.value = false
    }
    return
  }

  if (!commentForm.studentId) {
    actionError.value = t('teacherFeedback.selectStudentError')
    return
  }

  submittingCreate.value = true
  try {
    await guardTeacherRequest(() =>
      teacherApi.createFeedback({
        studentId: commentForm.studentId,
        lessonId: commentForm.lessonId || undefined,
        quizAttemptId: commentForm.quizAttemptId || undefined,
        feedbackType: commentForm.feedbackType,
        message: commentForm.message.trim(),
      }),
    )
    await feedbackResource.reload()
    activeTab.value = 'History'
    actionSuccess.value = t('teacherFeedback.sent')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherFeedback.createError'))
  } finally {
    submittingCreate.value = false
  }
}

function reloadAll(): void {
  void feedbackResource.reload()
  void studentsResource.reload()
  void lessonsResource.reload()
  if (activeTab.value === 'Create' && commentForm.lessonId > 0) {
    void loadQuizzesForLesson(commentForm.lessonId)
  }
}
</script>

<style scoped>
.teacher-feedback-layout {
  display: grid;
  grid-template-columns: minmax(280px, 340px) minmax(0, 1fr);
  gap: var(--grid-gap);
  align-items: start;
}

.teacher-feedback-main {
  display: grid;
  gap: var(--grid-gap);
  min-width: 0;
}

.teacher-feedback-muted {
  display: block;
  color: var(--text-secondary);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-regular);
  line-height: var(--line-height-normal);
}

/* Queue ------------------------------------------------------------------ */
.teacher-feedback-queue {
  position: sticky;
  top: calc(var(--topbar-height) + var(--space-4));
  display: grid;
  gap: var(--space-4);
  min-width: 0;
}

.teacher-feedback-queue-title {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}

.teacher-feedback-list {
  display: grid;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.teacher-feedback-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-3);
  align-items: start;
  width: 100%;
  padding: var(--space-3);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
  color: var(--text-primary);
  text-align: left;
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background var(--duration-fast) var(--ease-standard);
}

.teacher-feedback-row:hover {
  border-color: var(--border-interactive);
}

.teacher-feedback-row.is-active {
  border-color: var(--border-interactive);
  background: var(--surface-hover);
  box-shadow: inset 3px 0 0 var(--color-primary-500);
}

.teacher-feedback-row-copy {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.teacher-feedback-row-head,
.teacher-feedback-row-context {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-1) var(--space-2);
  min-width: 0;
}

.teacher-feedback-row-context {
  justify-content: flex-start;
  color: var(--text-secondary);
  font-size: var(--font-size-md);
}

.teacher-feedback-create-help {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  border-radius: var(--radius-lg);
  background: var(--surface-subtle);
  color: var(--text-secondary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
}

.teacher-feedback-create-help p,
.teacher-feedback-create-help ol {
  margin: 0;
}

.teacher-feedback-create-help ol {
  display: grid;
  gap: var(--space-1-5);
  padding-left: var(--space-5);
}

.teacher-feedback-queue-footer {
  justify-content: flex-start;
}

/* Detail ------------------------------------------------------------------ */
.teacher-feedback-detail {
  display: grid;
  gap: var(--section-gap);
  min-width: 0;
}

.teacher-feedback-detail-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3) var(--space-4);
}

.teacher-feedback-person {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}

.teacher-feedback-person-copy {
  display: grid;
  gap: var(--space-0-5);
  min-width: 0;
}

.teacher-feedback-person-name {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--type-h2-size);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-snug);
  overflow-wrap: anywhere;
}

.teacher-feedback-detail-side {
  display: grid;
  justify-items: end;
  gap: var(--space-1-5);
}

.teacher-feedback-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.teacher-feedback-tile.is-tall {
  grid-row: span 2;
}

.teacher-feedback-tile {
  display: grid;
  align-content: start;
  justify-items: start;
  gap: var(--space-3);
  min-width: 0;
  padding: var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
}

.teacher-feedback-tile-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  width: 100%;
}

.teacher-feedback-tile-title {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1-5);
  margin: 0;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-extrabold);
}

.teacher-feedback-facts {
  display: grid;
  gap: var(--space-3);
  width: 100%;
  margin: 0;
}

.teacher-feedback-facts dt {
  color: var(--text-secondary);
  font-size: var(--type-caption-size);
  font-weight: var(--font-weight-bold);
}

.teacher-feedback-facts dd {
  margin: var(--space-0-5) 0 0;
  font-weight: var(--font-weight-bold);
  overflow-wrap: anywhere;
}

.teacher-feedback-score {
  display: block;
  color: var(--color-primary-600);
  font-family: var(--font-display);
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
}

.teacher-feedback-quote {
  width: 100%;
  margin: 0;
  padding: var(--space-3) var(--space-4);
  border-left: 3px solid var(--color-primary-300);
  border-radius: var(--radius-sm);
  background: var(--surface-subtle);
  color: var(--text-primary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-relaxed);
  overflow-wrap: anywhere;
}

.teacher-feedback-snapshot {
  display: grid;
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.teacher-feedback-context {
  align-items: start;
}

.teacher-feedback-context .field {
  align-content: start;
}

.teacher-feedback-context-wide {
  grid-column: 1 / -1;
}

/* Composer ---------------------------------------------------------------- */
.teacher-feedback-composer {
  display: grid;
  gap: var(--section-gap);
  min-width: 0;
}

.teacher-feedback-composer-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(240px, 1fr);
  gap: var(--space-5);
  align-items: start;
}

.teacher-feedback-message {
  align-content: start;
}

.teacher-feedback-textarea {
  min-height: 180px;
}

.teacher-feedback-field-foot {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.teacher-feedback-counter {
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.teacher-feedback-guidance {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--color-primary-100);
  border-radius: var(--radius-lg);
  background: var(--color-primary-50);
}

.teacher-feedback-guidance .teacher-feedback-muted {
  margin: 0;
  font-size: var(--font-size-sm);
}

.teacher-feedback-guidance .teacher-feedback-quote {
  background: var(--surface-card);
}

.teacher-feedback-actions {
  justify-content: space-between;
}

.teacher-feedback-actions-note {
  flex: 1 1 260px;
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.teacher-feedback-actions-buttons {
  display: flex;
  margin-left: auto;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-2);
}

.teacher-feedback-tip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: var(--space-1-5);
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-md);
  text-align: center;
}

.teacher-feedback-tip strong {
  color: var(--text-primary);
}

@media (max-width: 1200px) {
  .teacher-feedback-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .teacher-feedback-queue {
    position: static;
  }

  .teacher-feedback-list {
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  }
}

@media (max-width: 900px) {
  .teacher-feedback-composer-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .teacher-feedback-detail-side {
    justify-items: start;
  }

  .teacher-feedback-actions-buttons {
    display: grid;
    justify-content: stretch;
    width: 100%;
  }

  .teacher-feedback-actions-buttons > .btn {
    width: 100%;
  }

  .teacher-feedback-actions-buttons > .btn-primary {
    order: -1;
  }

  .teacher-feedback-context-wide {
    grid-column: auto;
  }

  .teacher-feedback-detail-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .teacher-feedback-tile.is-tall {
    grid-row: auto;
  }
}
</style>
