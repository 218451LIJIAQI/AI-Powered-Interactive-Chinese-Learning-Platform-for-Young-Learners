<template>
  <div class="page-stack teacher-students-page">
    <PageHeaderBlock
      :title="t('teacherStudents.title')"
      :description="t('teacherStudents.description')"
      icon="students"
    />

    <DataNotice :using-fallback="usingFallback" :error="actionError" :success="actionSuccess" />

    <TeacherApprovalLockPanel
      v-if="teacherApprovalLocked"
      :title="teacherApprovalTitle"
      :message="teacherApprovalMessage"
    />

    <ResourceSection v-else :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="teacher-students-kpis">
        <article
          v-for="item in summaryCards"
          :key="item.label"
          class="panel teacher-students-kpi"
          :class="`is-${item.tone}`"
        >
          <span class="teacher-students-kpi-icon" aria-hidden="true"><IconGlyph :name="item.icon" :size="22" /></span>
          <div class="teacher-students-kpi-copy">
            <p class="teacher-students-kpi-label">{{ item.label }}</p>
            <p class="teacher-students-kpi-value">{{ item.value }}</p>
            <p class="teacher-students-kpi-caption">{{ item.caption }}</p>
            <button class="btn btn-link teacher-students-kpi-action" type="button" @click="item.action">
              {{ item.actionLabel }}
            </button>
          </div>
        </article>
      </section>

      <section class="teacher-students-workbench">
        <aside class="panel teacher-students-directory" :aria-label="t('teacherStudents.searchAria')">
          <p class="card-title">{{ t('teacherStudents.allStudents', { count: filteredStudents.length }) }}</p>

          <div class="teacher-students-tools">
            <div class="input-with-icon">
              <IconGlyph class="input-icon" name="search" :size="16" aria-hidden="true" />
              <label class="sr-only" for="teacher-students-search">{{ t('teacherStudents.searchAria') }}</label>
              <input
                id="teacher-students-search"
                v-model="searchTerm"
                class="input"
                type="search"
                :placeholder="t('teacherStudents.searchPlaceholder')"
              />
            </div>
            <div class="segmented teacher-students-sort" role="group" :aria-label="t('teacherStudents.sortBy')">
              <button
                class="segmented-option"
                type="button"
                :aria-pressed="sortMode === 'progress'"
                @click="setSort('progress')"
              >
                {{ t('teacherStudents.sortProgress') }}
              </button>
              <button
                class="segmented-option"
                type="button"
                :aria-pressed="sortMode === 'name'"
                @click="setSort('name')"
              >
                {{ t('teacherStudents.sortName') }}
              </button>
            </div>
          </div>

          <ul v-if="filteredStudents.length" class="teacher-students-roster">
            <li v-for="student in filteredStudents" :key="student.studentId">
              <button
                type="button"
                class="teacher-student-row"
                :class="{ 'is-active': selectedStudent?.studentId === student.studentId }"
                :aria-pressed="selectedStudent?.studentId === student.studentId"
                @click="selectStudent(student.studentId)"
              >
                <PortraitAvatar :size="44" rounded="soft" :name="student.displayName" aria-hidden="true" />
                <span class="teacher-student-row-copy">
                  <strong>{{ student.displayName }}</strong>
                  <span class="type-caption">{{ student.levelLabel }}</span>
                  <StatusPill
                    v-if="student.needsSupport"
                    :label="t('teacherStudents.needsSupportBadge')"
                    tone="warning"
                    icon="alert"
                    data-testid="needs-support-indicator"
                  />
                  <StatusPill v-else :label="t('teacherStudents.onTrack')" tone="success" />
                </span>
                <span class="teacher-student-row-figures">
                  <strong :class="`is-${riskTone(student.needsSupport)}`">{{ student.progressPercent }}%</strong>
                  <span class="type-caption">{{ student.quizAverageLabel }}</span>
                </span>
              </button>
            </li>
          </ul>

          <StatePanel
            v-else
            compact
            icon="search"
            :title="t('teacherStudents.noStudents')"
            :message="t('teacherStudents.noStudentsHint')"
          >
            <template v-if="searchTerm" #actions>
              <button class="btn btn-secondary btn-sm" type="button" @click="searchTerm = ''">
                {{ t('teacherStudents.clearSearch') }}
              </button>
            </template>
          </StatePanel>
        </aside>

        <div class="teacher-students-detail">
          <article id="teacher-students-detail" class="panel teacher-students-profile">
            <template v-if="selectedStudent">
              <div class="teacher-students-profile-head">
                <div class="teacher-students-profile-identity">
                  <PortraitAvatar :size="64" rounded="soft" :name="selectedStudent.displayName" aria-hidden="true" />
                  <div class="teacher-students-profile-copy">
                    <div class="teacher-students-profile-name-row">
                      <h2 class="teacher-students-profile-name">{{ selectedStudent.displayName }}</h2>
                      <StatusPill
                        :label="selectedStudentBadgeLabel"
                        :tone="selectedStudent.needsSupport ? 'warning' : 'success'"
                        :icon="selectedStudent.needsSupport ? 'alert' : 'status'"
                      />
                    </div>
                    <span class="teacher-students-muted">{{ selectedStudent.levelLabel }}</span>
                    <span class="type-caption">{{ selectedJoinedLabel }}</span>
                  </div>
                </div>

                <p class="teacher-students-profile-progress">
                  <strong>{{ selectedStudent.progressPercent }}%</strong>
                  <span>{{ t('teacherStudents.courseProgress') }}</span>
                </p>
              </div>

              <dl class="teacher-students-facts">
                <div>
                  <dt>{{ t('teacherStudents.masteredLessons') }}</dt>
                  <dd>{{ selectedMasteredLabel }}</dd>
                </div>
                <div>
                  <dt>{{ t('teacherStudents.quizAverage') }}</dt>
                  <dd>{{ selectedStudent.quizAverageLabel }}</dd>
                </div>
                <div>
                  <dt>{{ t('teacherStudents.latestActivity') }}</dt>
                  <dd>{{ selectedLatestActivityLabel }}</dd>
                </div>
                <div>
                  <dt>{{ t('teacherStudents.latestFeedback') }}</dt>
                  <dd>{{ selectedLatestFeedbackLabel }}</dd>
                </div>
              </dl>
            </template>

            <StatePanel
              v-else
              compact
              icon="students"
              :title="t('teacherStudents.noStudentsAvailable')"
              :message="t('teacherStudents.noStudentsAvailableHint')"
            />
          </article>

          <div v-if="selectedStudent" class="teacher-students-grid">
            <article class="panel teacher-students-card" aria-labelledby="teacher-students-summary-title">
              <h2 id="teacher-students-summary-title" class="card-title">{{ t('teacherStudents.learningSummary') }}</h2>

              <dl class="teacher-students-mini-stats">
                <div class="teacher-students-mini-stat">
                  <dt>{{ t('teacherStudents.progress') }}</dt>
                  <dd>
                    <strong>{{ selectedStudent.progressPercent }}%</strong>
                    <span>{{ t('teacherStudents.linkedLessons') }}</span>
                  </dd>
                </div>
                <div class="teacher-students-mini-stat">
                  <dt>{{ t('teacherStudents.quizAverage') }}</dt>
                  <dd>
                    <strong>{{ selectedStudent.quizAverageLabel }}</strong>
                    <span>{{ t('teacherStudents.submittedAttempts') }}</span>
                  </dd>
                </div>
                <div
                  class="teacher-students-mini-stat is-wide"
                  :class="{ 'is-warning': selectedStudent.needsSupport }"
                  data-testid="student-support-status"
                >
                  <dt>{{ t('teacherStudents.riskStatus') }}</dt>
                  <dd>
                    <strong class="teacher-students-support-value">{{ selectedRiskStatusLabel }}</strong>
                    <span v-if="!selectedSupportReasons.length">{{ t('teacherStudents.noRisk') }}</span>
                    <ul
                      v-else
                      class="teacher-students-reason-list"
                      :aria-label="t('teacherStudents.supportReasonsTitle')"
                    >
                      <li v-for="reason in selectedSupportReasons" :key="reason">
                        {{ statusLabel('supportReason', reason) }}
                      </li>
                    </ul>
                  </dd>
                </div>
              </dl>
              <p class="teacher-students-note">{{ t('teacherStudents.supportRuleNote') }}</p>
            </article>

            <article
              class="panel teacher-students-card"
              data-testid="student-lesson-mastery"
              aria-labelledby="teacher-students-mastery-title"
            >
              <div class="teacher-students-card-heading">
                <h2 id="teacher-students-mastery-title" class="card-title">{{ t('teacherStudents.lessonMastery') }}</h2>
                <p class="teacher-students-note">{{ t('teacherStudents.lessonMasteryHint') }}</p>
              </div>

              <ul v-if="selectedMasteryRows.length" class="list-divided teacher-students-plain-list">
                <li
                  v-for="row in selectedMasteryRows"
                  :key="row.lessonId"
                  class="teacher-students-mastery-row"
                  data-testid="student-lesson-mastery-row"
                >
                  <div class="teacher-students-mastery-head">
                    <strong>{{ row.lessonTitle }}</strong>
                    <MasteryChip :mastery="row.mastery" />
                  </div>
                  <ProgressBar class="is-sm" :progress="row.completionPercent" :label="row.lessonTitle" />
                  <div class="teacher-students-mastery-meta">
                    <span>{{
                      t('teacherStudents.lessonCompletion', { percent: formatPercent(row.completionPercent) })
                    }}</span>
                    <span>{{
                      row.bestQuizPercentage == null
                        ? t('teacherStudents.noBestQuiz')
                        : t('teacherStudents.bestQuiz', { percent: formatPercent(row.bestQuizPercentage) })
                    }}</span>
                  </div>
                </li>
              </ul>
              <p v-else class="teacher-students-empty">{{ t('teacherStudents.noLessonMastery') }}</p>
            </article>

            <article class="panel teacher-students-card" aria-labelledby="teacher-students-weak-title">
              <div class="teacher-students-card-heading">
                <h2 id="teacher-students-weak-title" class="card-title">{{ t('teacherStudents.weakAreas') }}</h2>
                <p class="teacher-students-note">{{ t('teacherStudents.weakAreasHint') }}</p>
              </div>

              <ul v-if="selectedWeakAreas.length" class="teacher-students-weak-list">
                <li v-for="lesson in selectedWeakAreas" :key="lesson" class="teacher-students-weak-item">
                  <IconGlyph name="alert" :size="16" aria-hidden="true" />
                  <span>{{ lesson }}</span>
                </li>
              </ul>
              <p v-else class="teacher-students-empty">{{ t('teacherStudents.noWeakAreas') }}</p>

              <div class="card-footer teacher-students-card-footer">
                <button class="btn btn-secondary btn-sm" type="button" @click="openFeedback">
                  {{ t('teacherStudents.openFeedback') }}
                  <IconGlyph name="chevronRight" :size="16" aria-hidden="true" />
                </button>
              </div>
            </article>

            <article class="panel teacher-students-card" aria-labelledby="teacher-students-insights-title">
              <h2 id="teacher-students-insights-title" class="card-title">{{ t('teacherStudents.insights') }}</h2>

              <ul class="teacher-students-plain-list teacher-students-insights">
                <li v-for="item in selectedInsights" :key="item.message" class="teacher-students-insight">
                  <span class="teacher-student-tile" :class="`is-${item.tone}`" aria-hidden="true">
                    <IconGlyph :name="item.icon" :size="18" />
                  </span>
                  <span>{{ item.message }}</span>
                </li>
              </ul>
            </article>

            <article class="panel teacher-students-card" aria-labelledby="teacher-students-feedback-title">
              <div class="card-header">
                <h2 id="teacher-students-feedback-title" class="card-title">
                  {{ t('teacherStudents.recentFeedback') }}
                </h2>
                <button class="btn btn-link" type="button" @click="openFeedback">
                  {{ t('teacherStudents.viewAll') }}
                </button>
              </div>

              <ul v-if="selectedFeedbackCards.length" class="list-divided teacher-students-plain-list">
                <li v-for="item in selectedFeedbackCards" :key="item.id" class="teacher-students-feedback">
                  <span class="teacher-student-tile" :class="`is-${item.tone}`" aria-hidden="true">
                    <IconGlyph :name="item.icon" :size="18" />
                  </span>
                  <div class="teacher-students-feedback-copy">
                    <div class="teacher-students-feedback-head">
                      <strong>{{ item.title }}</strong>
                      <span class="type-caption">{{ item.time }}</span>
                    </div>
                    <p>{{ item.message }}</p>
                  </div>
                </li>
              </ul>
              <p v-else class="teacher-students-empty">{{ t('teacherStudents.noFeedback') }}</p>
            </article>

            <article
              class="panel teacher-students-card teacher-students-followup"
              aria-labelledby="teacher-students-followup-title"
            >
              <div class="teacher-students-card-heading">
                <h2 id="teacher-students-followup-title" class="card-title">
                  {{ t('teacherStudents.draftGuidance') }}
                </h2>
                <p class="teacher-students-note">{{ t('teacherStudents.draftHint') }}</p>
              </div>

              <form class="teacher-students-followup-form" @submit.prevent="submitFollowUpFeedback">
                <div class="form-grid two">
                  <div class="field">
                    <label class="field-label" for="teacher-followup-tone">{{ t('teacherStudents.tone') }}</label>
                    <select
                      id="teacher-followup-tone"
                      v-model="followUpTone"
                      class="select"
                      :disabled="followUpDisabled"
                    >
                      <option value="ENCOURAGING">{{ t('teacherStudents.encouraging') }}</option>
                      <option value="SPECIFIC">{{ t('teacherStudents.specific') }}</option>
                      <option value="CORRECTIVE">{{ t('teacherStudents.corrective') }}</option>
                    </select>
                  </div>
                  <div class="field">
                    <label class="field-label" for="teacher-followup-focus">{{ t('teacherStudents.focus') }}</label>
                    <select
                      id="teacher-followup-focus"
                      v-model="followUpFocus"
                      class="select"
                      :disabled="followUpDisabled"
                    >
                      <option value="WEAK_AREAS">{{ t('teacherStudents.weakAreasOption') }}</option>
                      <option value="QUIZ_ACCURACY">{{ t('teacherStudents.quizAccuracy') }}</option>
                      <option value="CONSISTENCY">{{ t('teacherStudents.consistency') }}</option>
                    </select>
                  </div>
                </div>

                <div class="field">
                  <label class="field-label" for="teacher-followup-message">
                    {{ t('teacherStudents.feedbackAria') }}<span class="required-mark" aria-hidden="true">*</span>
                  </label>
                  <p class="teacher-students-greeting">
                    {{ t('teacherStudents.greeting', { name: selectedStudentName }) }}
                  </p>
                  <textarea
                    id="teacher-followup-message"
                    v-model="followUpMessage"
                    class="textarea teacher-students-followup-textarea"
                    required
                    :maxlength="PRACTICE_FEEDBACK_MAX_LENGTH"
                    :aria-invalid="Boolean(followUpMessageError) || undefined"
                    :aria-describedby="followUpDescribedBy"
                    :disabled="followUpDisabled"
                  />
                  <div class="teacher-students-field-foot">
                    <p id="teacher-followup-hint" class="field-hint">{{ t('teacherStudents.draftKeptHint') }}</p>
                    <span class="field-hint teacher-students-counter">
                      {{ followUpMessage.length }} / {{ PRACTICE_FEEDBACK_MAX_LENGTH }}
                    </span>
                  </div>
                  <p v-if="followUpMessageError" id="teacher-followup-error" class="field-error" role="alert">
                    {{ followUpMessageError }}
                  </p>
                </div>

                <p class="teacher-students-ready">
                  <IconGlyph name="status" :size="16" aria-hidden="true" />
                  {{ t('teacherStudents.readyAs', { type: followUpFeedbackTypeLabel }) }}
                </p>

                <div class="form-actions">
                  <button class="btn btn-ghost" type="button" :disabled="followUpDisabled" @click="resetFollowUpDraft">
                    <IconGlyph name="retry" :size="16" aria-hidden="true" />
                    {{ t('teacherStudents.resetDraft') }}
                  </button>
                  <button
                    class="btn btn-primary"
                    type="submit"
                    :aria-busy="submittingFollowUp"
                    :disabled="feedbackReadOnly || submittingFollowUp || !selectedStudent"
                  >
                    {{ sendFeedbackLabel }}
                  </button>
                </div>
              </form>
            </article>
          </div>
        </div>
      </section>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { teacherApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import MasteryChip from '@/components/common/MasteryChip.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import TeacherApprovalLockPanel from '@/components/teacher/TeacherApprovalLockPanel.vue'
import { useResource } from '@/composables/useResource'
import { useStatusLabels } from '@/composables/useStatusLabels'
import { useTeacherAccess } from '@/composables/useTeacherAccess'
import { teacherFeedback, teacherStudentSummaries } from '@/demo/data'
import { teacherStudentsMessages } from '@/plugins/teacherStudentsMessages'
import type { StudentSupportReason, TeacherFeedbackResponse, TeacherStudentSummaryResponse } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { clampPercent, formatDate, formatDateTime, formatPercent } from '@/utils/format'
import { countMastery } from '@/utils/progressSummary'
import {
  PRACTICE_FEEDBACK_MAX_LENGTH,
  PRACTICE_FEEDBACK_MIN_LENGTH,
  validatePracticeFeedbackMessage,
} from '@/utils/teacherPracticeReview'
import {
  buildFollowUpSuggestion,
  followUpFeedbackType,
  lessonMasteryRows,
  shouldApplySuggestion,
  studentNeedsSupport,
  studentSupportReasons,
  weakAreaLessons,
  type FollowUpFocus,
  type FollowUpTone,
  type LessonMasteryRow,
} from '@/utils/teacherRoster'

const router = useRouter()
const { t } = useI18n({
  messages: {
    en: { teacherStudents: teacherStudentsMessages.en },
    ms: { teacherStudents: teacherStudentsMessages.ms },
    'zh-CN': { teacherStudents: teacherStudentsMessages['zh-CN'] },
  },
})
const { statusLabel } = useStatusLabels()

type SortMode = 'progress' | 'name'
type StudentRecord = TeacherStudentSummaryResponse & {
  displayName: string
  levelLabel: string
  quizAverageLabel: string
  supportReasonList: StudentSupportReason[]
  masteryRows: LessonMasteryRow[]
  weakLessons: string[]
}

// D13: the roster and feedback history are approved-teacher features; a 403 also switches to the locked state.
const { teacherApprovalLocked, teacherApprovalTitle, teacherApprovalMessage, guardTeacherRequest } = useTeacherAccess()
const studentsResource = useResource(
  () => (teacherApprovalLocked.value ? Promise.resolve([]) : guardTeacherRequest(() => teacherApi.students())),
  teacherStudentSummaries,
)
const feedbackResource = useResource(
  () => (teacherApprovalLocked.value ? Promise.resolve([]) : guardTeacherRequest(() => teacherApi.feedbackList())),
  teacherFeedback,
)

const searchTerm = ref('')
const sortMode = ref<SortMode>('progress')
const selectedStudentId = ref<number | null>(null)
const followUpTone = ref<FollowUpTone>('ENCOURAGING')
const followUpFocus = ref<FollowUpFocus>('WEAK_AREAS')
const followUpMessage = ref('')
/** The last suggestion written into the textarea; typed text that differs from it is never overwritten (A5 #11). */
const lastSuggestion = ref<string | null>(null)
const submittingFollowUp = ref(false)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
/** Message validation problems are also shown under the textarea (the page notice can be far away). */
const followUpMessageError = ref<string | null>(null)

const students = computed<StudentRecord[]>(() =>
  (studentsResource.data.value ?? []).map((item) => ({
    ...item,
    displayName: item.displayName?.trim() || item.fullName,
    levelLabel: item.currentLevel?.trim() || t('teacherStudents.levelNotSet'),
    progressPercent: clampPercent(item.progressPercent),
    quizAveragePercent: item.quizAveragePercent == null ? null : clampPercent(item.quizAveragePercent),
    quizAverageLabel:
      item.quizAveragePercent == null ? t('teacherStudents.noQuizData') : formatPercent(item.quizAveragePercent),
    // "Needs support" comes only from the server's needsSupport / supportReasons, never from received feedback.
    needsSupport: studentNeedsSupport(item),
    supportReasonList: studentSupportReasons(item),
    masteryRows: lessonMasteryRows(item.lessonMastery),
    weakLessons: weakAreaLessons(item.weakAreas),
  })),
)

const feedback = computed<TeacherFeedbackResponse[]>(() => feedbackResource.data.value ?? [])

const pageLoading = computed(() => studentsResource.loading.value || feedbackResource.loading.value)
// The page content (and so this error) is only shown when the lock panel is not.
const pageError = computed(() => studentsResource.error.value || feedbackResource.error.value)
const usingFallback = computed(() => studentsResource.usingFallback.value || feedbackResource.usingFallback.value)
const feedbackReadOnly = computed(() => usingFallback.value)
const followUpDisabled = computed(() => feedbackReadOnly.value || submittingFollowUp.value || !selectedStudent.value)
const followUpDescribedBy = computed(() =>
  followUpMessageError.value ? 'teacher-followup-error teacher-followup-hint' : 'teacher-followup-hint',
)
const sendFeedbackLabel = computed(() =>
  submittingFollowUp.value ? t('teacherStudents.sending') : t('teacherStudents.sendNow'),
)

const filteredStudents = computed(() => {
  const keyword = searchTerm.value.trim().toLowerCase()
  const result = students.value.filter((item) => {
    if (!keyword) return true
    return (
      item.displayName.toLowerCase().includes(keyword) ||
      item.levelLabel.toLowerCase().includes(keyword) ||
      item.weakLessons.some((entry) => entry.toLowerCase().includes(keyword)) ||
      item.masteryRows.some((row) => row.lessonTitle.toLowerCase().includes(keyword))
    )
  })

  result.sort((left, right) => {
    if (sortMode.value === 'name') {
      return left.displayName.localeCompare(right.displayName)
    }

    const progressDiff = right.progressPercent - left.progressPercent
    if (progressDiff !== 0) return progressDiff
    return left.displayName.localeCompare(right.displayName)
  })

  return result
})

const selectedStudent = computed(
  () =>
    filteredStudents.value.find((item) => item.studentId === selectedStudentId.value) ??
    filteredStudents.value[0] ??
    null,
)

const summaryCards = computed(() => {
  const supportCount = students.value.filter((item) => item.needsSupport).length
  const quizStudents = students.value.filter((item) => item.quizAveragePercent != null)
  const averageProgress = students.value.length
    ? clampPercent(students.value.reduce((sum, item) => sum + item.progressPercent, 0) / students.value.length)
    : 0
  const averageQuiz = quizStudents.length
    ? clampPercent(quizStudents.reduce((sum, item) => sum + (item.quizAveragePercent ?? 0), 0) / quizStudents.length)
    : 0

  return [
    {
      label: t('teacherStudents.linkedStudents'),
      value: String(students.value.length),
      caption: t('teacherStudents.linkedStudentsHint'),
      icon: 'students',
      tone: 'purple',
      actionLabel: t('teacherStudents.viewList'),
      action: selectFirstFilteredStudent,
    },
    {
      label: t('teacherStudents.averageProgress'),
      value: `${averageProgress}%`,
      caption: t('teacherStudents.averageProgressHint'),
      icon: 'progress',
      tone: 'purple-soft',
      actionLabel: t('teacherStudents.openStudent'),
      action: selectFirstFilteredStudent,
    },
    {
      label: t('teacherStudents.atRisk'),
      value: String(supportCount),
      caption: t('teacherStudents.atRiskHint'),
      icon: 'alert',
      tone: 'orange',
      actionLabel: t('teacherStudents.filterAtRisk'),
      action: selectFirstSupportStudent,
    },
    {
      label: t('teacherStudents.quizAverage'),
      value: quizStudents.length ? `${averageQuiz}%` : '--',
      caption: t('teacherStudents.submittedAttempts'),
      icon: 'quiz',
      tone: 'green',
      actionLabel: t('teacherStudents.feedbackCenter'),
      action: openFeedback,
    },
  ]
})

const FEEDBACK_CARD_STYLES = [
  { icon: 'file', tone: 'green' },
  { icon: 'status', tone: 'orange' },
  { icon: 'help', tone: 'purple' },
  { icon: 'history', tone: 'blue' },
] as const

const selectedFeedbackCards = computed(() =>
  feedback.value
    .filter((item) => item.studentId === selectedStudent.value?.studentId)
    .slice(0, 4)
    .map((item, index) => ({
      id: item.id,
      title: item.quizTitle
        ? t('teacherStudents.feedbackOnQuiz', { quiz: item.quizTitle })
        : item.lessonTitle
          ? t('teacherStudents.lessonFeedback', { lesson: item.lessonTitle })
          : feedbackTypeLabel(item.feedbackType),
      message: item.message,
      time: formatDateTime(item.createdAt),
      // Decorative icon only; it carries no priority meaning.
      ...FEEDBACK_CARD_STYLES[index % FEEDBACK_CARD_STYLES.length],
    })),
)

const selectedSupportReasons = computed(() => selectedStudent.value?.supportReasonList ?? [])
const selectedMasteryRows = computed(() => selectedStudent.value?.masteryRows ?? [])
const selectedWeakAreas = computed(() => selectedStudent.value?.weakLessons ?? [])
const selectedMasteredLabel = computed(() => {
  const rows = selectedMasteryRows.value
  if (!rows.length) return '--'
  return t('teacherStudents.masteredLessonsValue', { mastered: countMastery(rows).MASTERED, total: rows.length })
})

const selectedInsights = computed(() => {
  if (!selectedStudent.value) {
    return []
  }

  const learner = selectedStudentName.value
  const weakArea = selectedWeakAreas.value[0]
  const quizAverage =
    selectedStudent.value.quizAveragePercent == null
      ? t('teacherStudents.noQuizAverage')
      : t('teacherStudents.quizAverageValue', { average: formatPercent(selectedStudent.value.quizAveragePercent) })
  const reasons = selectedSupportReasons.value.map((reason) => statusLabel('supportReason', reason)).join(', ')

  return [
    {
      icon: selectedStudent.value.needsSupport ? 'alert' : 'status',
      tone: selectedStudent.value.needsSupport ? 'orange' : 'green',
      message: selectedStudent.value.needsSupport
        ? t('teacherStudents.riskInsight', {
            name: learner,
            reasons: reasons || t('teacherStudents.needsSupportBadge'),
          })
        : t('teacherStudents.onTrackInsight', { name: learner }),
    },
    {
      icon: weakArea ? 'help' : 'status',
      tone: weakArea ? 'blue' : 'green',
      message: weakArea
        ? t('teacherStudents.visibleWeakArea', { area: weakArea })
        : t('teacherStudents.noWeakAreasInsight'),
    },
    {
      icon: 'progress',
      tone: 'purple',
      message: quizAverage,
    },
  ]
})

const selectedJoinedLabel = computed(() =>
  selectedStudent.value?.joinedAt
    ? t('teacherStudents.joined', { date: formatDate(selectedStudent.value.joinedAt) })
    : t('teacherStudents.joinUnavailable'),
)
const selectedLatestActivityLabel = computed(() =>
  selectedStudent.value?.latestActivityAt
    ? formatDateTime(selectedStudent.value.latestActivityAt)
    : t('teacherStudents.noActivity'),
)
// Informational only: the latest feedback never affects the support signal.
const selectedLatestFeedbackLabel = computed(() => {
  if (!selectedStudent.value?.latestFeedbackType) {
    return t('teacherStudents.noFeedbackRecorded')
  }
  const normalized = feedbackTypeLabel(selectedStudent.value.latestFeedbackType)
  return selectedStudent.value.latestFeedbackAt
    ? t('teacherStudents.feedbackOnDate', {
        type: normalized,
        date: formatDateTime(selectedStudent.value.latestFeedbackAt),
      })
    : normalized
})
const selectedRiskStatusLabel = computed(() =>
  selectedStudent.value?.needsSupport ? t('teacherStudents.atRisk') : t('teacherStudents.stable'),
)
const selectedStudentBadgeLabel = computed(() =>
  selectedStudent.value?.needsSupport ? t('teacherStudents.alert') : t('teacherStudents.active'),
)
// Full display name: the first word is often the surname (Chen Hao) or a title-like name part (Nur Aisyah).
const selectedStudentName = computed(() => selectedStudent.value?.displayName ?? t('teacherStudents.student'))

const manualFollowupDraft = computed(() => {
  if (!selectedStudent.value) {
    return t('teacherStudents.openLearnerDraft')
  }

  return buildFollowUpSuggestion(
    {
      name: selectedStudentName.value,
      tone: followUpTone.value,
      focus: followUpFocus.value,
      weakLesson: selectedWeakAreas.value[0] ?? null,
      quizAverageLabel:
        selectedStudent.value.quizAveragePercent == null
          ? null
          : formatPercent(selectedStudent.value.quizAveragePercent),
    },
    (key, params) => t(`teacherStudents.${key}`, params ?? {}),
  )
})
const followUpType = computed(() => followUpFeedbackType(followUpTone.value, followUpFocus.value))
const followUpFeedbackTypeLabel = computed(() => feedbackTypeLabel(followUpType.value))

watch(
  filteredStudents,
  (items) => {
    if (!items.some((item) => item.studentId === selectedStudentId.value)) {
      selectedStudentId.value = items[0]?.studentId ?? null
    }
  },
  { immediate: true },
)

// A5 #11: refresh the suggestion when tone / focus / learner change, but only while the teacher has not typed
// their own text (empty textarea, or it still equals the previous suggestion).
watch(
  manualFollowupDraft,
  (suggestion) => {
    if (shouldApplySuggestion(followUpMessage.value, lastSuggestion.value)) {
      followUpMessage.value = suggestion
    }
    lastSuggestion.value = suggestion
  },
  { immediate: true },
)

function applySuggestion(): void {
  followUpMessage.value = manualFollowupDraft.value
  lastSuggestion.value = manualFollowupDraft.value
}

function selectStudent(studentId: number): void {
  selectedStudentId.value = studentId
  // In the one-column layout the learner detail sits below the roster, so bring it into view.
  if (window.matchMedia?.('(max-width: 1200px)').matches) void revealSelectedStudent()
}

async function revealSelectedStudent(): Promise<void> {
  await nextTick()
  const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  document
    .getElementById('teacher-students-detail')
    ?.scrollIntoView?.({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'start' })
}

function selectFirstFilteredStudent(): void {
  selectedStudentId.value = filteredStudents.value[0]?.studentId ?? null
}

function selectFirstSupportStudent(): void {
  const target = filteredStudents.value.find((item) => item.needsSupport)
  if (target) selectedStudentId.value = target.studentId
}

function openFeedback(): void {
  void router.push('/teacher/feedback')
}

function riskTone(needsSupport: boolean): string {
  return needsSupport ? 'risk' : 'good'
}

function feedbackTypeLabel(type: string | null | undefined): string {
  return statusLabel('feedbackType', type?.trim() || 'GENERAL')
}

function setSort(mode: SortMode): void {
  sortMode.value = mode
}

function resetFollowUpDraft(): void {
  applySuggestion()
  actionError.value = null
  actionSuccess.value = t('teacherStudents.draftReset')
}

async function submitFollowUpFeedback(): Promise<void> {
  if (submittingFollowUp.value) return
  actionError.value = null
  actionSuccess.value = null
  followUpMessageError.value = null

  if (feedbackReadOnly.value) {
    actionError.value = t('teacherStudents.previewFeedback')
    return
  }

  if (!selectedStudent.value) {
    actionError.value = t('teacherStudents.selectStudent')
    return
  }

  // Same 5..2000 character rule as the backend feedback request (the textarea already caps the maximum).
  const messageProblem = validatePracticeFeedbackMessage(followUpMessage.value)
  if (messageProblem === 'required') {
    actionError.value = t('teacherStudents.messageRequired')
    followUpMessageError.value = actionError.value
    return
  }
  if (messageProblem === 'tooShort') {
    actionError.value = t('teacherStudents.messageTooShort', { min: PRACTICE_FEEDBACK_MIN_LENGTH })
    followUpMessageError.value = actionError.value
    return
  }

  submittingFollowUp.value = true
  try {
    const payload = {
      studentId: selectedStudent.value.studentId,
      feedbackType: followUpType.value,
      message: followUpMessage.value.trim(),
    }
    await guardTeacherRequest(() => teacherApi.createFeedback(payload))
    await feedbackResource.reload()
    await studentsResource.reload()
    applySuggestion()
    actionSuccess.value = t('teacherStudents.feedbackSent')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherStudents.feedbackError'))
  } finally {
    submittingFollowUp.value = false
  }
}

function reloadAll(): void {
  void studentsResource.reload()
  void feedbackResource.reload()
}
</script>

<style scoped>
/* KPI row ---------------------------------------------------------------- */
.teacher-students-kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--grid-gap);
}

.teacher-students-kpi {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  min-width: 0;
  padding: var(--space-4) var(--space-5);
}

.teacher-students-kpi-icon {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}

.teacher-students-kpi.is-orange .teacher-students-kpi-icon {
  background: var(--color-warning-soft);
  color: var(--color-warning-text);
}

.teacher-students-kpi.is-green .teacher-students-kpi-icon {
  background: var(--color-success-soft);
  color: var(--color-success-text);
}

.teacher-students-kpi-copy {
  display: grid;
  justify-items: start;
  gap: var(--space-1);
  min-width: 0;
}

.teacher-students-kpi-copy p {
  margin: 0;
}

.teacher-students-kpi-label {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
}

.teacher-students-kpi-value {
  font-family: var(--font-display);
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
}

.teacher-students-kpi-caption {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.teacher-students-kpi-action {
  margin-top: var(--space-1);
  font-size: var(--font-size-sm);
  text-align: left;
}

/* Workbench: roster list + learner detail ------------------------------ */
.teacher-students-workbench {
  display: grid;
  grid-template-columns: minmax(280px, 340px) minmax(0, 1fr);
  gap: var(--grid-gap);
  align-items: start;
}

.teacher-students-directory {
  position: sticky;
  top: calc(var(--topbar-height) + var(--space-4));
  display: grid;
  gap: var(--space-4);
  min-width: 0;
}

.teacher-students-tools {
  display: grid;
  gap: var(--space-2);
}

.teacher-students-sort {
  justify-self: start;
}

.teacher-students-roster,
.teacher-students-plain-list,
.teacher-students-weak-list,
.teacher-students-reason-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.teacher-students-roster {
  display: grid;
  gap: var(--space-2);
}

.teacher-student-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
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

.teacher-student-row:hover {
  border-color: var(--border-interactive);
}

.teacher-student-row.is-active {
  border-color: var(--border-interactive);
  background: var(--surface-hover);
  box-shadow: inset 3px 0 0 var(--color-primary-500);
}

.teacher-student-row-copy {
  display: grid;
  justify-items: start;
  gap: var(--space-1);
  min-width: 0;
}

.teacher-student-row-copy strong {
  overflow-wrap: anywhere;
}

.teacher-student-row-figures {
  display: grid;
  justify-items: end;
  gap: var(--space-1);
  text-align: right;
}

.teacher-student-row-figures strong {
  font-family: var(--font-display);
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
  color: var(--color-success-text);
}

.teacher-student-row-figures strong.is-risk {
  color: var(--color-danger-text);
}

.teacher-students-detail {
  display: grid;
  gap: var(--grid-gap);
  min-width: 0;
}

/* Selected learner ---------------------------------------------------- */
.teacher-students-profile {
  scroll-margin-top: calc(var(--topbar-height) + var(--space-4));
  display: grid;
  gap: var(--section-gap);
}

.teacher-students-profile-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.teacher-students-profile-identity {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  min-width: 0;
}

.teacher-students-profile-copy {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.teacher-students-profile-name-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.teacher-students-profile-name {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--type-h2-size);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-snug);
  overflow-wrap: anywhere;
}

.teacher-students-muted,
.teacher-students-note,
.teacher-students-empty {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
}

.teacher-students-note {
  font-size: var(--font-size-sm);
}

.teacher-students-profile-progress {
  display: grid;
  justify-items: end;
  gap: var(--space-1);
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.teacher-students-profile-progress strong {
  color: var(--color-primary-600);
  font-family: var(--font-display);
  font-size: var(--font-size-5xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
}

.teacher-students-facts,
.teacher-students-mini-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: var(--space-3);
  margin: 0;
}

.teacher-students-mini-stats {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.teacher-students-facts > div,
.teacher-students-mini-stat {
  display: grid;
  align-content: start;
  gap: var(--space-1);
  min-width: 0;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-lg);
  background: var(--surface-subtle);
}

.teacher-students-facts dt,
.teacher-students-mini-stat dt {
  color: var(--text-secondary);
  font-size: var(--type-caption-size);
  font-weight: var(--font-weight-bold);
}

.teacher-students-facts dd {
  margin: 0;
  font-weight: var(--font-weight-bold);
  overflow-wrap: anywhere;
}

.teacher-students-mini-stat dd {
  display: grid;
  gap: var(--space-1);
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.teacher-students-mini-stat dd strong {
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
}

.teacher-students-mini-stat.is-wide {
  grid-column: 1 / -1;
}

.teacher-students-mini-stat.is-warning {
  background: var(--color-warning-soft);
}

.teacher-students-mini-stat dd .teacher-students-support-value {
  font-size: var(--font-size-lg);
}

.teacher-students-reason-list {
  display: grid;
  gap: var(--space-1);
}

.teacher-students-reason-list li::before {
  content: '• ';
}

/* Cards --------------------------------------------------------------- */
.teacher-students-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--grid-gap);
  align-items: start;
}

.teacher-students-card {
  display: grid;
  align-content: start;
  gap: var(--section-gap);
  min-width: 0;
}

.teacher-students-card-heading {
  display: grid;
  gap: var(--space-1);
}

.teacher-students-card-footer {
  justify-content: flex-start;
}

.teacher-student-tile {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}

.teacher-student-tile.is-green {
  background: var(--color-success-soft);
  color: var(--color-success-text);
}

.teacher-student-tile.is-orange {
  background: var(--color-warning-soft);
  color: var(--color-warning-text);
}

.teacher-student-tile.is-blue {
  background: var(--color-info-soft);
  color: var(--color-info-text);
}

.teacher-students-mastery-row {
  display: grid;
  gap: var(--space-2);
}

.teacher-students-mastery-head,
.teacher-students-mastery-meta,
.teacher-students-feedback-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-1) var(--space-3);
}

.teacher-students-mastery-meta {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.teacher-students-weak-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.teacher-students-weak-item {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1-5);
  padding: var(--space-1-5) var(--space-3);
  border: 1px solid var(--color-warning-border);
  border-radius: var(--radius-pill);
  background: var(--color-warning-soft);
  color: var(--color-warning-text);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-bold);
}

.teacher-students-insights {
  display: grid;
  gap: var(--space-3);
}

.teacher-students-insight,
.teacher-students-feedback {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-3);
  align-items: start;
  color: var(--text-secondary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
}

.teacher-students-insight > span:last-child {
  padding-top: var(--space-1-5);
}

.teacher-students-feedback-copy {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.teacher-students-feedback-copy strong {
  color: var(--text-primary);
}

.teacher-students-feedback-copy p {
  margin: 0;
}

/* Follow-up composer --------------------------------------------------- */
.teacher-students-followup {
  border-color: var(--color-primary-100);
  background: linear-gradient(180deg, var(--color-primary-50), var(--surface-card) 160px);
}

.teacher-students-followup-form {
  display: grid;
  gap: var(--space-4);
}

.teacher-students-followup-form .field {
  align-content: start;
}

.teacher-students-greeting {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
}

.teacher-students-followup-textarea {
  min-height: 140px;
}

.teacher-students-field-foot {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.teacher-students-counter {
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.teacher-students-ready {
  display: flex;
  align-items: center;
  gap: var(--space-1-5);
  margin: 0;
  color: var(--text-brand);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-bold);
}

@media (max-width: 1200px) {
  .teacher-students-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .teacher-students-workbench {
    grid-template-columns: minmax(0, 1fr);
  }

  .teacher-students-directory {
    position: static;
  }

  .teacher-students-roster {
    grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  }

  .teacher-students-tools {
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: center;
  }
}

@media (max-width: 900px) {
  .teacher-students-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .teacher-students-tools,
  .teacher-students-kpis {
    grid-template-columns: minmax(0, 1fr);
  }

  .teacher-students-profile-progress {
    justify-items: start;
  }

  .teacher-students-profile-progress strong {
    font-size: var(--font-size-4xl);
  }
}
</style>
