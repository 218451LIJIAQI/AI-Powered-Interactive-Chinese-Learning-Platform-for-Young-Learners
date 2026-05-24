<template>
  <div class="page-stack teacher-feedback-page">
    <PageHeaderBlock
      title="Feedback Center"
      description="Review saved teacher feedback, refine comments, and create new feedback for linked learners."
      icon="help"
    />

    <DataNotice :using-fallback="usingFallback" :error="actionError || pageError" :success="actionSuccess" :detail="feedbackNoticeDetail" />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <div class="teacher-feedback-tabs">
        <button
          v-for="tab in feedbackTabs"
          :key="tab"
          class="teacher-feedback-tab"
          :class="{ 'is-active': activeTab === tab }"
          type="button"
          @click="activeTab = tab"
        >
          {{ tab }}
        </button>
      </div>

      <section class="teacher-feedback-layout">
        <article class="panel teacher-feedback-queue-card">
          <div class="row-between teacher-feedback-card-head">
            <div class="student-section-heading">
              <strong>{{ activeTab === 'History' ? 'Feedback History' : 'Create Feedback' }}</strong>
            </div>
            <div class="row teacher-feedback-queue-actions">
              <span class="teacher-feedback-queue-count">{{ filteredFeedbackItems.length }}</span>
              <select v-model="queueFilter" class="select teacher-feedback-filter" aria-label="Filter feedback by context">
                <option value="ALL">All Contexts</option>
                <option value="QUIZ">Quiz</option>
                <option value="LESSON">Lesson</option>
                <option value="GENERAL">General</option>
              </select>
            </div>
          </div>

          <div v-if="activeTab === 'History' && filteredFeedbackItems.length" class="teacher-feedback-queue-list">
            <button
              v-for="item in filteredFeedbackItems"
              :key="item.id"
              type="button"
              class="teacher-feedback-queue-item"
              :class="{ 'is-active': selectedFeedbackId === item.id }"
              @click="selectFeedback(item.id)"
            >
              <PortraitAvatar :size="54" rounded="soft" :kind="item.avatarKind" />
              <div class="teacher-feedback-queue-copy">
                <div class="row-between u-align-start">
                  <div class="stack u-gap-4 u-min-w-0">
                    <strong>{{ item.studentName }}</strong>
                    <div class="row u-wrap">
                      <span class="teacher-feedback-type-pill">{{ item.typeLabel }}</span>
                      <span>{{ item.title }}</span>
                    </div>
                  </div>
                  <span class="teacher-feedback-priority" :class="`is-${item.priorityTone}`">{{ item.priority }}</span>
                </div>
                <small>{{ item.createdLabel }}</small>
              </div>
            </button>
          </div>

          <div v-else-if="activeTab === 'History'">
            <StatePanel
              icon="help"
              title="No feedback found"
              message="No saved feedback matches the current filter."
            />
          </div>

          <div v-else class="teacher-feedback-create-help">
            <p>
              Choose a linked student, then optionally connect your message to one of your lessons or a submitted quiz
              attempt before sending feedback.
            </p>
            <ul class="teacher-feedback-bullet-list">
              <li>Select a linked student.</li>
              <li>Optionally attach a lesson or quiz attempt from your real teaching data.</li>
              <li>Write the final teacher message and send it directly.</li>
            </ul>
          </div>

          <button class="teacher-footer-link" type="button" @click="toggleTab">
            <span>{{ activeTab === 'History' ? 'Switch to create mode' : 'Open feedback history' }}</span>
            <IconGlyph name="chevronRight" :size="16" aria-hidden="true" />
          </button>
        </article>

        <div class="teacher-feedback-main-column">
          <article class="panel teacher-feedback-detail-card">
            <template v-if="activeTab === 'History' && selectedFeedbackItem">
              <div class="teacher-feedback-detail-head">
                <div class="teacher-feedback-profile">
                  <PortraitAvatar :size="68" rounded="soft" :kind="selectedFeedbackItem.avatarKind" />
                  <div class="stack u-gap-4">
                    <strong>{{ selectedFeedbackItem.studentName }}</strong>
                    <span>{{ selectedFeedbackItem.studentMeta }}</span>
                  </div>
                </div>
                <div class="teacher-feedback-detail-side">
                  <span>Saved: {{ selectedFeedbackItem.createdExact }}</span>
                  <span class="teacher-feedback-priority" :class="`is-${selectedFeedbackItem.priorityTone}`">{{ selectedFeedbackItem.priority }}</span>
                </div>
              </div>

              <div class="teacher-feedback-detail-grid">
                <article class="teacher-feedback-detail-panel">
                  <div class="teacher-feedback-detail-copy">
                    <small>Feedback Type</small>
                    <strong>{{ selectedFeedbackItem.typeDisplay }}</strong>
                  </div>

                  <div class="teacher-feedback-separator" />

                  <div class="teacher-feedback-detail-copy">
                    <small>Context</small>
                    <strong>{{ selectedFeedbackItem.title }}</strong>
                    <span>{{ selectedFeedbackItem.contextLine }}</span>
                  </div>

                  <div class="teacher-feedback-score-row">
                    <div class="stack u-gap-4">
                      <small>Latest student progress</small>
                      <strong class="teacher-feedback-score">{{ selectedFeedbackItem.progressLabel }}</strong>
                      <span>{{ selectedFeedbackItem.quizAverageLabel }}</span>
                    </div>
                    <div class="teacher-feedback-score-side">
                      <strong>{{ selectedFeedbackItem.feedbackTypeLabel }}</strong>
                      <small>Linked student</small>
                      <span>{{ selectedFeedbackItem.studentName }}</span>
                    </div>
                  </div>
                </article>

                <article class="teacher-feedback-detail-panel">
                  <div class="teacher-feedback-card-head">
                    <strong>Saved Message</strong>
                  </div>
                  <div class="teacher-feedback-message-box">
                    <p>{{ selectedFeedbackItem.message }}</p>
                  </div>
                  <button class="teacher-footer-link" type="button" @click="prepareEditFromSelected">
                    <span>Edit this feedback</span>
                    <IconGlyph name="chevronRight" :size="16" aria-hidden="true" />
                  </button>
                </article>

                <article class="teacher-feedback-detail-panel">
                  <div class="row-between teacher-feedback-card-head">
                    <strong>Linked Student Snapshot</strong>
                    <button class="student-link-button" type="button" @click="router.push('/teacher/students')">View Students</button>
                  </div>
                  <div class="teacher-feedback-progress-list">
                    <article v-for="metric in progressMetrics" :key="metric.label" class="teacher-feedback-progress-item">
                      <div class="teacher-feedback-progress-ring" :style="{ '--progress': metric.value }">
                        <strong>{{ metric.value }}%</strong>
                      </div>
                      <div class="teacher-feedback-progress-copy">
                        <strong>{{ metric.label }}</strong>
                        <span>{{ metric.caption }}</span>
                      </div>
                    </article>
                  </div>
                </article>
              </div>
            </template>

            <template v-else>
              <div class="teacher-feedback-detail-head">
                <div class="teacher-feedback-profile">
                  <PortraitAvatar :size="68" rounded="soft" :kind="selectedStudentOption?.avatarKind || 'student'" />
                  <div class="stack u-gap-4">
                    <strong>{{ selectedStudentOption?.label || 'Choose a student' }}</strong>
                    <span>{{ selectedStudentOption?.meta || 'Only linked students are available here.' }}</span>
                  </div>
                </div>
                <div class="teacher-feedback-detail-side">
                  <span>Create a new teacher feedback record</span>
                  <span class="teacher-feedback-priority is-medium">Draft</span>
                </div>
              </div>

              <div class="teacher-feedback-create-grid">
                <div class="field">
                  <label for="teacher-feedback-student">Student</label>
                  <select id="teacher-feedback-student" v-model.number="commentForm.studentId" class="select" :disabled="feedbackFormDisabled">
                    <option :value="0">Select a linked student</option>
                    <option v-for="option in studentOptions" :key="option.studentId" :value="option.studentId">
                      {{ option.label }}
                    </option>
                  </select>
                </div>

                <div class="field">
                  <label for="teacher-feedback-type">Feedback Type</label>
                  <select id="teacher-feedback-type" v-model="commentForm.feedbackType" class="select" :disabled="feedbackFormDisabled">
                    <option value="GENERAL">General</option>
                    <option value="PROGRESS">Progress</option>
                    <option value="ENCOURAGING">Encouraging</option>
                    <option value="CORRECTIVE">Corrective</option>
                    <option value="QUIZ">Quiz</option>
                  </select>
                </div>

                <div class="field">
                  <label for="teacher-feedback-lesson">Lesson (optional)</label>
                  <select id="teacher-feedback-lesson" v-model.number="commentForm.lessonId" class="select" :disabled="feedbackFormDisabled">
                    <option :value="0">No lesson context</option>
                    <option v-for="lesson in lessonOptions" :key="lesson.id" :value="lesson.id">
                      {{ lesson.title }}
                    </option>
                  </select>
                </div>

                <div class="field">
                  <label for="teacher-feedback-quiz">Quiz (optional)</label>
                  <select id="teacher-feedback-quiz" v-model.number="selectedQuizId" class="select" :disabled="feedbackFormDisabled || !commentForm.lessonId || loadingQuizzes">
                    <option :value="0">{{ loadingQuizzes ? 'Loading quizzes...' : 'No quiz context' }}</option>
                    <option v-for="quiz in quizOptions" :key="quiz.id" :value="quiz.id">
                      {{ quiz.title }}
                    </option>
                  </select>
                </div>

                <div class="field">
                  <label for="teacher-feedback-quiz-attempt">Quiz Attempt (optional)</label>
                  <select id="teacher-feedback-quiz-attempt" v-model.number="commentForm.quizAttemptId" class="select" :disabled="feedbackFormDisabled || !selectedQuizId || loadingQuizAttempts">
                    <option :value="0">{{ loadingQuizAttempts ? 'Loading attempts...' : 'No quiz attempt selected' }}</option>
                    <option v-for="attempt in quizAttemptOptions" :key="attempt.attemptId" :value="attempt.attemptId">
                      {{ attempt.studentName }} · {{ attempt.score }}/{{ attempt.maxScore }} · {{ formatDateTime(attempt.submittedAt) }}
                    </option>
                  </select>
                </div>
              </div>
            </template>
          </article>

          <section class="teacher-feedback-bottom-grid">
            <article class="panel teacher-feedback-comment-card">
              <div class="teacher-feedback-card-head">
                <strong>{{ activeTab === 'History' ? 'Edit Message' : 'Teacher Comment' }}</strong>
              </div>

              <textarea v-model="commentForm.message" class="textarea teacher-feedback-textarea" aria-label="Teacher feedback message" maxlength="2000" :disabled="feedbackFormDisabled" />

              <div class="teacher-feedback-toolbar">
                <span>Plain text</span>
                <span>Child-friendly tone</span>
                <small>{{ commentLength }}/2000</small>
              </div>
            </article>

            <article class="panel teacher-feedback-draft-card">
              <div class="row-between teacher-feedback-card-head">
                <div>
                  <strong>Draft Guidance</strong>
                  <p>Use the suggestion as a starting point, then send the final version yourself.</p>
                </div>
                <button class="student-link-button" type="button" :disabled="feedbackFormDisabled" @click="regenerateSuggestion">Regenerate</button>
              </div>

              <div class="teacher-feedback-draft-box">
                <p>{{ draftGuidance }}</p>
              </div>

              <button class="teacher-outline-button teacher-feedback-use-draft" type="button" :disabled="feedbackFormDisabled" @click="useSuggestedDraft">
                Use this guidance
              </button>
            </article>

            <article class="panel teacher-feedback-actions-card">
              <div class="teacher-feedback-card-head">
                <strong>Actions</strong>
              </div>

              <div class="teacher-feedback-actions-stack">
                <button class="btn btn-primary btn-block" type="button" :disabled="feedbackReadOnly || submittingCreate || submittingUpdate" @click="submitFeedback">
                  {{ isEditingExistingFeedback ? (submittingUpdate ? 'Saving...' : 'Save Feedback') : (submittingCreate ? 'Sending...' : 'Send Feedback') }}
                </button>
                <button
                  class="teacher-outline-button teacher-feedback-action-alt"
                  type="button"
                  :disabled="feedbackFormDisabled"
                  @click="prepareRetryMessage"
                >
                  Draft retry note
                </button>
                <button
                  class="teacher-feedback-action-success"
                  type="button"
                  :disabled="feedbackFormDisabled"
                  @click="openCreateForSelectedStudent"
                >
                  Create new feedback
                </button>
              </div>

              <small class="teacher-feedback-actions-note">
                Saved feedback stays editable in history, and optional lesson or quiz context is attached from live teacher content.
              </small>
            </article>
          </section>
        </div>
      </section>

      <div class="teacher-feedback-tip">
        <span>Tip</span>
        <span>Specific, calm feedback helps students understand the next step without overload.</span>
      </div>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
/**
 * Teacher feedback surface — review student attempts, leave
 * narrative comments, attach rubric scores, and broadcast
 * announcements to a class.
 *
 * <p>Scopes student lookups to the teacher's own classroom — the
 * backend further enforces this in
 * {@code TeacherFeedbackService.assertTeachesStudent()} so a
 * malicious teacher cannot leave feedback for a student outside
 * their roster.</p>
 */
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { teacherApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { useTeacherAccess } from '@/composables/useTeacherAccess'
import { teacherFeedback, teacherLessons, teacherQuizAttempts, teacherQuizzes, teacherStudentSummaries } from '@/demo/data'
import type {
  LessonResponse,
  TeacherFeedbackResponse,
  TeacherQuizAttemptSummaryResponse,
  TeacherQuizResponse,
  TeacherStudentSummaryResponse,
} from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { formatDateTime } from '@/utils/format'
import { formatTeacherFeedbackType } from '@/utils/teacherFeedback'

const router = useRouter()
const { teacherApprovalLocked, teacherApprovalMessage } = useTeacherAccess()

type QueueFilter = 'ALL' | 'QUIZ' | 'LESSON' | 'GENERAL'
type FeedbackTab = 'History' | 'Create'
type AvatarKind = 'student' | 'male' | 'female' | 'hijab'

interface StudentOption extends TeacherStudentSummaryResponse {
  avatarKind: AvatarKind
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
  avatarKind: AvatarKind
  lessonId?: number | null
  quizAttemptId?: number | null
  progressLabel: string
  quizAverageLabel: string
  category: 'QUIZ' | 'LESSON' | 'GENERAL'
}

const feedbackResource = useResource(
  () => (teacherApprovalLocked.value ? Promise.resolve([]) : teacherApi.feedbackList()),
  teacherFeedback,
)
const studentsResource = useResource(
  () => (teacherApprovalLocked.value ? Promise.resolve([]) : teacherApi.students()),
  teacherStudentSummaries,
)
const lessonsResource = useResource(
  () => (teacherApprovalLocked.value ? Promise.resolve([]) : teacherApi.lessons()),
  teacherLessons,
)

const feedbackTabs: FeedbackTab[] = ['History', 'Create']
const activeTab = ref<FeedbackTab>('History')
const queueFilter = ref<QueueFilter>('ALL')
const selectedFeedbackId = ref<number>(0)
const selectedQuizId = ref<number>(0)
const draftSeed = ref(0)
const loadingQuizzes = ref(false)
const loadingQuizAttempts = ref(false)
const submittingCreate = ref(false)
const submittingUpdate = ref(false)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
const quizzes = ref<TeacherQuizResponse[]>([])
const quizAttempts = ref<TeacherQuizAttemptSummaryResponse[]>([])

const feedback = computed<TeacherFeedbackResponse[]>(() => feedbackResource.data.value ?? (feedbackResource.usingFallback.value ? teacherFeedback : []))
const lessons = computed<LessonResponse[]>(() => lessonsResource.data.value ?? (lessonsResource.usingFallback.value ? teacherLessons : []))
const studentOptions = computed<StudentOption[]>(() =>
  (studentsResource.data.value ?? []).map((item: TeacherStudentSummaryResponse, index: number) => ({
    ...item,
    avatarKind: (['student', 'male', 'female', 'hijab'] as const)[index % 4],
    label: item.displayName?.trim() || item.fullName,
    meta: `${item.currentLevel?.trim() || 'Level not set'} • ${item.email}`,
  })),
)
const lessonOptions = computed<LessonResponse[]>(() => lessons.value.filter((item: LessonResponse) => item.contentStatus !== 'ARCHIVED'))
const quizOptions = computed<TeacherQuizResponse[]>(() => quizzes.value)
const quizAttemptOptions = computed<TeacherQuizAttemptSummaryResponse[]>(() =>
  quizAttempts.value.filter((item: TeacherQuizAttemptSummaryResponse) => !commentForm.studentId || item.studentId === commentForm.studentId),
)

const pageLoading = computed(() => feedbackResource.loading.value || studentsResource.loading.value || lessonsResource.loading.value)
const pageError = computed(() => feedbackResource.error.value || studentsResource.error.value || lessonsResource.error.value)
const usingFallback = computed(
  () => feedbackResource.usingFallback.value || studentsResource.usingFallback.value || lessonsResource.usingFallback.value,
)
const feedbackReadOnly = computed(() => teacherApprovalLocked.value || usingFallback.value)
const feedbackFormDisabled = computed(() => feedbackReadOnly.value || submittingCreate.value || submittingUpdate.value)
const feedbackNoticeDetail = computed(() => (teacherApprovalLocked.value ? teacherApprovalMessage.value : null))

const commentForm = reactive({
  studentId: 0,
  lessonId: 0,
  quizAttemptId: 0,
  feedbackType: 'GENERAL',
  message: '',
})

const feedbackItems = computed<FeedbackQueueItem[]>(() =>
  feedback.value.map((item: TeacherFeedbackResponse, index: number) => {
    const linkedStudent = studentOptions.value.find((student: StudentOption) => student.studentId === item.studentId)
    const category: FeedbackQueueItem['category'] =
      item.quizAttemptId != null ? 'QUIZ' : item.lessonId != null ? 'LESSON' : 'GENERAL'
    const quizAverage = linkedStudent?.quizAveragePercent == null ? 'No quiz data' : `${Math.round(linkedStudent.quizAveragePercent)}% quiz average`
    const progress = linkedStudent ? `${linkedStudent.progressPercent}% progress` : 'Progress unavailable'

    return {
      id: item.id,
      studentId: item.studentId,
      studentName: item.studentName,
      studentMeta: linkedStudent?.meta || 'Linked learner',
      title: item.quizTitle || item.lessonTitle || formatTeacherFeedbackType(item.feedbackType),
      contextLine:
        item.quizTitle
          ? `Quiz attempt ${item.quizAttemptId ?? '--'}`
          : item.lessonTitle
            ? `Lesson ${item.lessonId ?? '--'}`
            : 'General teacher feedback',
      message: item.message,
      typeLabel: category === 'QUIZ' ? 'Quiz' : category === 'LESSON' ? 'Lesson' : 'General',
      typeDisplay: category === 'QUIZ' ? 'Quiz Feedback' : category === 'LESSON' ? 'Lesson Feedback' : 'General Feedback',
      feedbackTypeLabel: formatTeacherFeedbackType(item.feedbackType),
      createdLabel: formatDateTime(item.createdAt),
      createdExact: formatDateTime(item.createdAt),
      priority: index === 0 ? 'Recent' : index === 1 ? 'Follow-up' : 'Saved',
      priorityTone: index === 0 ? 'high' : index === 1 ? 'medium' : 'low',
      avatarKind: linkedStudent?.avatarKind || (['student', 'male', 'female', 'hijab'] as const)[index % 4],
      lessonId: item.lessonId,
      quizAttemptId: item.quizAttemptId,
      progressLabel: progress,
      quizAverageLabel: quizAverage,
      category,
    }
  }),
)

const filteredFeedbackItems = computed(() =>
  feedbackItems.value.filter((item: FeedbackQueueItem) => (queueFilter.value === 'ALL' ? true : item.category === queueFilter.value)),
)

const selectedFeedbackItem = computed<FeedbackQueueItem | null>(
  () => filteredFeedbackItems.value.find((item: FeedbackQueueItem) => item.id === selectedFeedbackId.value) ?? filteredFeedbackItems.value[0] ?? null,
)
const selectedExistingFeedback = computed<TeacherFeedbackResponse | null>(
  () => feedback.value.find((item: TeacherFeedbackResponse) => item.id === selectedFeedbackId.value) ?? null,
)
const isEditingExistingFeedback = computed(() => activeTab.value === 'History' && selectedExistingFeedback.value != null)
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
      label: 'Overall Progress',
      value: Math.round(student.progressPercent),
      caption: 'From linked lesson progress',
    },
    {
      label: 'Quiz Average',
      value: Math.round(student.quizAveragePercent ?? 0),
      caption: student.quizAveragePercent == null ? 'No quiz attempts yet' : 'From submitted quiz attempts',
    },
    {
      label: 'Risk Signal',
      value: student.atRisk ? 100 : 0,
      caption: student.atRisk ? 'Current follow-up recommended' : 'No immediate risk signal',
    },
  ]
})

const draftGuidance = computed(() => {
  const student = selectedStudentOption.value?.label.split(' ')[0]
    ?? selectedFeedbackItem.value?.studentName.split(' ')[0]
    ?? 'learner'
  const weakest = selectedStudentOption.value?.weakAreas[0]
    ?? studentOptions.value.find((item: StudentOption) => item.studentId === selectedFeedbackItem.value?.studentId)?.weakAreas[0]
    ?? 'the next practice target'

  const variants = [
    `Start with one thing ${student} did well, then give one practical step for ${weakest.toLowerCase()}.`,
    `Keep the note warm and specific. Mention one success, then one short follow-up task for ${weakest.toLowerCase()}.`,
    `Use child-friendly wording. Praise effort, name the next step clearly, and keep the message easy to act on.`,
  ]

  return variants[draftSeed.value % variants.length]
})

const commentLength = computed(() => commentForm.message.length)

watch(
  filteredFeedbackItems,
  (items: FeedbackQueueItem[]) => {
    if (!items.some((item: FeedbackQueueItem) => item.id === selectedFeedbackId.value)) {
      selectedFeedbackId.value = items[0]?.id ?? 0
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
    if (commentForm.quizAttemptId && !quizAttemptOptions.value.some((item: TeacherQuizAttemptSummaryResponse) => item.attemptId === commentForm.quizAttemptId)) {
      commentForm.quizAttemptId = 0
    }
  },
  { immediate: true },
)

async function loadQuizzesForLesson(lessonId: number) {
  if (teacherApprovalLocked.value) {
    quizzes.value = []
    return
  }

  loadingQuizzes.value = true
  try {
    quizzes.value = usingFallback.value
      ? teacherQuizzes.filter((item: TeacherQuizResponse) => item.lessonId === lessonId)
      : await teacherApi.lessonQuizzes(lessonId)
  } catch (reason) {
    quizzes.value = []
    actionError.value = resolveApiError(reason, 'Unable to load lesson quizzes.')
  } finally {
    loadingQuizzes.value = false
  }
}

async function loadQuizAttempts(quizId: number) {
  if (teacherApprovalLocked.value) {
    quizAttempts.value = []
    return
  }

  loadingQuizAttempts.value = true
  try {
    quizAttempts.value = usingFallback.value
      ? teacherQuizAttempts
      : await teacherApi.quizAttempts(quizId)
  } catch (reason) {
    quizAttempts.value = []
    actionError.value = resolveApiError(reason, 'Unable to load quiz attempts.')
  } finally {
    loadingQuizAttempts.value = false
  }
}

function syncCommentFormFromExisting() {
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

function selectFeedback(id: number) {
  selectedFeedbackId.value = id
  activeTab.value = 'History'
  syncCommentFormFromExisting()
}

function prepareEditFromSelected() {
  activeTab.value = 'History'
  syncCommentFormFromExisting()
}

function openCreateForSelectedStudent() {
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

function regenerateSuggestion() {
  draftSeed.value += 1
}

function useSuggestedDraft() {
  commentForm.message = commentForm.message.trim()
    ? `${commentForm.message.trim()} ${draftGuidance.value}`
    : draftGuidance.value
  actionError.value = null
  actionSuccess.value = 'Draft guidance added to the feedback message.'
}

function prepareRetryMessage() {
  commentForm.feedbackType = 'CORRECTIVE'
  commentForm.message = 'Please review the key pattern once more and try again. You are close, and one careful retry will help.'
  actionError.value = null
  actionSuccess.value = 'Retry note drafted for review.'
}

function toggleTab() {
  activeTab.value = activeTab.value === 'History' ? 'Create' : 'History'
  if (activeTab.value === 'Create') {
    openCreateForSelectedStudent()
  } else if (selectedExistingFeedback.value) {
    syncCommentFormFromExisting()
  }
}

async function submitFeedback() {
  if (submittingCreate.value || submittingUpdate.value) return
  actionError.value = null
  actionSuccess.value = null

  if (feedbackReadOnly.value) {
    actionError.value = teacherApprovalLocked.value
      ? teacherApprovalMessage.value
      : 'Feedback cannot be created or updated while preview data is active.'
    return
  }

  if (!commentForm.message.trim()) {
    actionError.value = 'Feedback message is required.'
    return
  }

  if (selectedExistingFeedback.value && activeTab.value === 'History') {
    submittingUpdate.value = true
    try {
      await teacherApi.updateFeedback(selectedExistingFeedback.value.id, {
        feedbackType: commentForm.feedbackType,
        message: commentForm.message.trim(),
      })
      await feedbackResource.reload()
      syncCommentFormFromExisting()
      actionSuccess.value = 'Feedback updated and history refreshed.'
    } catch (reason) {
      actionError.value = resolveApiError(reason, 'Unable to update this feedback.')
    } finally {
      submittingUpdate.value = false
    }
    return
  }

  if (!commentForm.studentId) {
    actionError.value = 'Select a linked student before sending feedback.'
    return
  }

  submittingCreate.value = true
  try {
    await teacherApi.createFeedback({
      studentId: commentForm.studentId,
      lessonId: commentForm.lessonId || undefined,
      quizAttemptId: commentForm.quizAttemptId || undefined,
      feedbackType: commentForm.feedbackType,
      message: commentForm.message.trim(),
    })
    await feedbackResource.reload()
    activeTab.value = 'History'
    actionSuccess.value = 'Feedback sent and history refreshed.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to create this feedback.')
  } finally {
    submittingCreate.value = false
  }
}

function reloadAll() {
  void feedbackResource.reload()
  void studentsResource.reload()
  void lessonsResource.reload()
  if (activeTab.value === 'Create' && commentForm.lessonId > 0) {
    void loadQuizzesForLesson(commentForm.lessonId)
  }
}
</script>

<style scoped>
.teacher-feedback-page {
  position: relative;
}

.teacher-feedback-tabs {
  display: flex;
  align-items: center;
  gap: 24px;
  padding-left: 6px;
}

.teacher-feedback-tab {
  min-height: 38px;
  padding: 6px 4px 12px;
  border: 0;
  background: transparent;
  color: #697693;
  font-size: 1.08rem;
  font-weight: 700;
}

.teacher-feedback-tab.is-active {
  color: var(--primary);
  border-bottom: 3px solid var(--primary);
}

.teacher-feedback-layout {
  display: grid;
  grid-template-columns: 356px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.teacher-feedback-main-column {
  display: grid;
  gap: 18px;
}

.teacher-feedback-queue-card,
.teacher-feedback-detail-card,
.teacher-feedback-comment-card,
.teacher-feedback-draft-card,
.teacher-feedback-actions-card {
  border-radius: 28px;
}

.teacher-feedback-card-head {
  display: grid;
  gap: 8px;
}

.teacher-feedback-card-head p {
  margin: 0;
  color: #68748f;
  line-height: 1.55;
}

.teacher-feedback-queue-actions {
  align-items: center;
}

.teacher-feedback-queue-count {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: rgba(125, 88, 255, 0.12);
  color: var(--primary);
  font-weight: 800;
}

.teacher-feedback-filter {
  min-width: 136px;
}

.teacher-feedback-queue-list {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.teacher-feedback-queue-item {
  width: 100%;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  padding: 13px 14px;
  border-radius: 20px;
  border: 1px solid rgba(236, 239, 247, 0.96);
  background: rgba(255, 255, 255, 0.98);
  color: var(--text);
  text-align: left;
}

.teacher-feedback-queue-item.is-active {
  border-color: rgba(127, 97, 255, 0.42);
  box-shadow: inset 0 0 0 1px rgba(127, 97, 255, 0.14);
  background: linear-gradient(135deg, rgba(249, 246, 255, 0.98), rgba(255, 255, 255, 0.98));
}

.teacher-feedback-queue-copy {
  display: grid;
  gap: 6px;
}

.teacher-feedback-queue-copy span,
.teacher-feedback-queue-copy small {
  color: #737f9b;
}

.teacher-feedback-type-pill {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(124, 88, 255, 0.12);
  color: var(--primary);
  font-size: 0.76rem;
  font-weight: 700;
}

.teacher-feedback-priority {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 92px;
  padding: 7px 10px;
  border-radius: 999px;
  font-size: 0.76rem;
  font-weight: 800;
}

.teacher-feedback-priority.is-high {
  color: #ff5449;
  background: rgba(255, 113, 113, 0.14);
}

.teacher-feedback-priority.is-medium {
  color: #ea9a1d;
  background: rgba(255, 182, 75, 0.14);
}

.teacher-feedback-priority.is-low {
  color: #57af4d;
  background: rgba(109, 206, 100, 0.14);
}

.teacher-feedback-detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.teacher-feedback-profile {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 14px;
  align-items: center;
}

.teacher-feedback-profile span,
.teacher-feedback-detail-side span,
.teacher-feedback-detail-copy small,
.teacher-feedback-detail-copy span,
.teacher-feedback-score-row small,
.teacher-feedback-score-row span,
.teacher-feedback-progress-copy span,
.teacher-feedback-create-help p {
  color: #6f7b97;
}

.teacher-feedback-detail-side {
  display: grid;
  justify-items: end;
  gap: 10px;
}

.teacher-feedback-detail-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.teacher-feedback-create-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.teacher-feedback-detail-panel {
  display: grid;
  gap: 12px;
  padding: 16px;
  border-radius: 20px;
  border: 1px solid rgba(234, 237, 246, 0.96);
  background: rgba(255, 255, 255, 0.98);
}

.teacher-feedback-detail-copy {
  display: grid;
  gap: 5px;
}

.teacher-feedback-separator {
  height: 1px;
  background: rgba(236, 239, 247, 0.96);
}

.teacher-feedback-score-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: end;
}

.teacher-feedback-score {
  font-family: 'Fredoka', sans-serif;
  font-size: 2.35rem;
  color: #7255ff;
  line-height: 1;
}

.teacher-feedback-score-side {
  display: grid;
  justify-items: end;
  gap: 4px;
}

.teacher-feedback-message-box,
.teacher-feedback-create-help {
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(247, 249, 253, 0.96);
}

.teacher-feedback-message-box p,
.teacher-feedback-draft-box p {
  margin: 0;
  color: #5d6984;
  line-height: 1.6;
}

.teacher-feedback-progress-list {
  display: grid;
  gap: 14px;
}

.teacher-feedback-progress-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 14px;
  align-items: center;
}

.teacher-feedback-progress-ring {
  --progress: 68;
  width: 66px;
  height: 66px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background:
    radial-gradient(circle closest-side, white 67%, transparent 68% 100%),
    conic-gradient(#785bff calc(var(--progress) * 1%), rgba(229, 233, 244, 0.96) 0);
}

.teacher-feedback-progress-ring strong {
  font-family: 'Fredoka', sans-serif;
  font-size: 1rem;
  color: #6f52ff;
}

.teacher-feedback-progress-copy {
  display: grid;
  gap: 4px;
}

.teacher-feedback-bottom-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 0.84fr) 276px;
  gap: 18px;
}

.teacher-feedback-comment-card,
.teacher-feedback-draft-card,
.teacher-feedback-actions-card {
  display: grid;
  gap: 14px;
}

.teacher-feedback-textarea {
  min-height: 160px;
}

.teacher-feedback-toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  color: #697490;
  padding-top: 8px;
  border-top: 1px solid rgba(236, 239, 247, 0.96);
}

.teacher-feedback-toolbar small {
  margin-left: auto;
}

.teacher-feedback-draft-card {
  background: linear-gradient(135deg, rgba(246, 242, 255, 0.98), rgba(255, 255, 255, 0.98));
}

.teacher-feedback-draft-box {
  padding: 14px 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.98);
  border: 1px solid rgba(232, 236, 247, 0.96);
}

.teacher-feedback-use-draft {
  justify-self: stretch;
}

.teacher-feedback-actions-stack {
  display: grid;
  gap: 10px;
}

.teacher-feedback-action-alt {
  color: #ef9824;
}

.teacher-feedback-action-success {
  width: 100%;
  min-height: 48px;
  border-radius: 16px;
  border: 1px solid rgba(108, 204, 97, 0.18);
  background: rgba(111, 209, 101, 0.1);
  color: #48a045;
  font-weight: 800;
}

.teacher-feedback-actions-note,
.teacher-feedback-create-help li {
  color: #7d89a5;
  line-height: 1.5;
}

.teacher-feedback-bullet-list {
  margin: 12px 0 0;
  padding-left: 18px;
}

.teacher-feedback-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #6b7694;
  font-weight: 600;
}

@media (max-width: 1480px) {
  .teacher-feedback-layout,
  .teacher-feedback-detail-grid,
  .teacher-feedback-bottom-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .teacher-feedback-tabs {
    gap: 16px;
    flex-wrap: wrap;
  }

  .teacher-feedback-detail-head,
  .teacher-feedback-score-row,
  .teacher-feedback-create-grid {
    grid-template-columns: 1fr;
    justify-items: start;
  }
}
</style>
