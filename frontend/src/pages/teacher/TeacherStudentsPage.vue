<template>
  <div class="page-stack teacher-students-page">
    <PageHeaderBlock
      :title="t('teacherStudents.title')"
      :description="t('teacherStudents.description')"
      icon="students"
    />

    <DataNotice :using-fallback="usingFallback" :error="noticeError" :success="actionSuccess" />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="teacher-students-stats">
        <article
          v-for="item in summaryCards"
          :key="item.label"
          class="teacher-students-stat-card"
          :class="`is-${item.tone}`"
        >
          <span class="teacher-students-stat-icon"><IconGlyph :name="item.icon" :size="22" /></span>
          <div class="teacher-students-stat-copy">
            <small>{{ item.label }}</small>
            <strong>{{ item.value }}</strong>
            <span>{{ item.caption }}</span>
          </div>
          <button class="student-link-button" type="button" @click="item.action">{{ item.actionLabel }}</button>
        </article>
      </section>

      <section class="teacher-students-workbench">
        <article class="panel teacher-students-directory-card">
          <div class="teacher-students-directory-head">
            <div class="student-section-heading">
              <strong>{{ t('teacherStudents.allStudents', { count: filteredStudents.length }) }}</strong>
            </div>
            <div class="teacher-students-directory-tools">
              <label class="teacher-students-search">
                <IconGlyph name="search" :size="16" />
                <input
                  v-model="searchTerm"
                  class="teacher-students-search-input"
                  :aria-label="t('teacherStudents.searchAria')"
                  :placeholder="t('teacherStudents.searchPlaceholder')"
                />
              </label>
              <button class="teacher-outline-button teacher-students-filter-button" type="button" @click="cycleSort">
                {{ t('teacherStudents.sort', { mode: sortModeLabel }) }}
              </button>
            </div>
          </div>

          <div v-if="filteredStudents.length" class="teacher-students-directory-list">
            <button
              v-for="student in filteredStudents"
              :key="student.studentId"
              type="button"
              class="teacher-students-directory-item"
              :class="{ 'is-active': selectedStudent?.studentId === student.studentId }"
              @click="selectStudent(student.studentId)"
            >
              <PortraitAvatar :size="52" rounded="soft" :kind="student.avatarKind" />
              <div class="teacher-students-directory-copy">
                <div class="row-between u-align-start">
                  <div class="stack u-gap-4 u-min-w-0">
                    <strong>{{ student.displayName }}</strong>
                    <span>{{ student.levelLabel }}</span>
                  </div>
                  <strong class="teacher-students-directory-progress" :class="`is-${riskTone(student.atRisk)}`">
                    {{ student.progressPercent }}%
                  </strong>
                </div>
                <div class="row-between">
                  <small>{{ riskStatusLabel(student.atRisk) }}</small>
                  <span class="teacher-students-directory-more">{{ student.quizAverageLabel }}</span>
                </div>
              </div>
            </button>
          </div>

          <StatePanel
            v-else
            icon="students"
            :title="t('teacherStudents.noStudents')"
            :message="t('teacherStudents.noStudentsHint')"
          />
        </article>

        <div class="teacher-students-main-column">
          <article class="panel teacher-students-profile-card">
            <template v-if="selectedStudent">
              <div class="teacher-students-profile-head">
                <div class="teacher-students-profile-meta">
                  <PortraitAvatar :size="74" rounded="soft" :kind="selectedStudent.avatarKind" />
                  <div class="stack u-gap-4">
                    <div class="row u-wrap">
                      <strong>{{ selectedStudent.displayName }}</strong>
                      <span class="teacher-students-star">{{ selectedStudentBadgeLabel }}</span>
                    </div>
                    <span>{{ selectedStudent.levelLabel }}</span>
                    <small>{{ selectedJoinedLabel }}</small>
                  </div>
                </div>

                <div class="teacher-students-profile-progress">
                  <strong>{{ selectedStudent.progressPercent }}%</strong>
                  <span>{{ t('teacherStudents.courseProgress') }}</span>
                </div>
              </div>

              <div class="teacher-students-contact-grid">
                <article>
                  <small>{{ t('teacherStudents.email') }}</small>
                  <strong>{{ selectedStudentEmailLabel }}</strong>
                </article>
                <article>
                  <small>{{ t('teacherStudents.quizAverage') }}</small>
                  <strong>{{ selectedStudent.quizAverageLabel }}</strong>
                </article>
                <article>
                  <small>{{ t('teacherStudents.latestActivity') }}</small>
                  <strong>{{ selectedLatestActivityLabel }}</strong>
                </article>
                <article>
                  <small>{{ t('teacherStudents.latestFeedback') }}</small>
                  <strong>{{ selectedLatestFeedbackLabel }}</strong>
                </article>
              </div>
            </template>

            <StatePanel
              v-else
              icon="students"
              :title="t('teacherStudents.noStudentsAvailable')"
              :message="t('teacherStudents.noStudentsAvailableHint')"
            />
          </article>

          <div v-if="selectedStudent" class="teacher-students-overview-grid">
            <article class="panel teacher-students-progress-card">
              <div class="teacher-students-card-head">
                <strong>{{ t('teacherStudents.learningSummary') }}</strong>
              </div>

              <div class="teacher-students-progress-summary">
                <article class="teacher-students-mini-stat">
                  <small>{{ t('teacherStudents.progress') }}</small>
                  <strong>{{ selectedStudent.progressPercent }}%</strong>
                  <span>{{ t('teacherStudents.linkedLessons') }}</span>
                </article>
                <article class="teacher-students-mini-stat">
                  <small>{{ t('teacherStudents.quizAverage') }}</small>
                  <strong>{{ selectedStudent.quizAverageLabel }}</strong>
                  <span>{{ t('teacherStudents.submittedAttempts') }}</span>
                </article>
                <article class="teacher-students-mini-stat">
                  <small>{{ t('teacherStudents.riskStatus') }}</small>
                  <strong>{{ selectedRiskStatusLabel }}</strong>
                  <span>{{ selectedRiskSummary }}</span>
                </article>
              </div>
            </article>

            <article class="panel teacher-students-weak-card">
              <div class="teacher-students-card-head">
                <strong>{{ t('teacherStudents.weakAreas') }}</strong>
              </div>

              <div v-if="selectedWeakAreaRows.length" class="teacher-students-weak-list">
                <article v-for="item in selectedWeakAreaRows" :key="item.label" class="teacher-students-weak-row">
                  <div class="row-between">
                    <strong>{{ item.label }}</strong>
                    <span>{{ item.detail }}</span>
                  </div>
                </article>
              </div>
              <p v-else class="teacher-students-empty-copy">{{ t('teacherStudents.noWeakAreas') }}</p>

              <button class="teacher-footer-link" type="button" @click="openFeedback">
                <span>{{ t('teacherStudents.openFeedback') }}</span>
                <IconGlyph name="chevronRight" :size="16" aria-hidden="true" />
              </button>
            </article>

            <article class="panel teacher-students-insights-card">
              <div class="teacher-students-card-head">
                <strong>{{ t('teacherStudents.insights') }}</strong>
              </div>

              <div class="teacher-students-insight-list">
                <article v-for="item in selectedInsights" :key="item.message" class="teacher-students-insight-item">
                  <span class="teacher-students-insight-icon" :class="`is-${item.tone}`"
                    ><IconGlyph :name="item.icon" :size="18"
                  /></span>
                  <span>{{ item.message }}</span>
                </article>
              </div>
            </article>
          </div>
        </div>

        <div class="teacher-students-side-column">
          <article class="panel teacher-students-feedback-card">
            <div class="row-between teacher-students-card-head">
              <strong>{{ t('teacherStudents.recentFeedback') }}</strong>
              <button class="student-link-button" type="button" @click="openFeedback">
                {{ t('teacherStudents.viewAll') }}
              </button>
            </div>

            <div v-if="selectedFeedbackCards.length" class="teacher-students-feedback-list">
              <article v-for="item in selectedFeedbackCards" :key="item.id" class="teacher-students-feedback-item">
                <span class="teacher-students-feedback-icon" :class="`is-${item.tone}`"
                  ><IconGlyph :name="item.icon" :size="18"
                /></span>
                <div class="teacher-students-feedback-copy">
                  <div class="row-between u-align-start">
                    <strong>{{ item.title }}</strong>
                    <small>{{ item.time }}</small>
                  </div>
                  <p>{{ item.message }}</p>
                </div>
              </article>
            </div>
            <p v-else class="teacher-students-empty-copy">{{ t('teacherStudents.noFeedback') }}</p>
          </article>

          <article class="panel teacher-students-followup-card">
            <div class="teacher-students-card-head">
              <div class="row-between teacher-students-followup-headline">
                <strong>{{ t('teacherStudents.draftGuidance') }}</strong>
                <span class="teacher-students-followup-info"><IconGlyph name="help" :size="14" /></span>
              </div>
              <p>{{ t('teacherStudents.draftHint') }}</p>
            </div>

            <form class="teacher-students-followup-box" @submit.prevent="submitFollowUpFeedback">
              <strong>{{ t('teacherStudents.greeting', { name: selectedStudentFirstName }) }}</strong>
              <textarea
                v-model="followUpMessage"
                class="textarea teacher-students-followup-textarea"
                :aria-label="t('teacherStudents.feedbackAria')"
                :disabled="followUpDisabled"
              />
              <span class="teacher-students-followup-sign">{{
                t('teacherStudents.readyAs', { type: followUpFeedbackTypeLabel })
              }}</span>
            </form>

            <div class="teacher-students-followup-fields">
              <div class="field">
                <label for="teacher-followup-tone">{{ t('teacherStudents.tone') }}</label>
                <select id="teacher-followup-tone" v-model="followUpTone" class="select" :disabled="followUpDisabled">
                  <option value="ENCOURAGING">{{ t('teacherStudents.encouraging') }}</option>
                  <option value="SPECIFIC">{{ t('teacherStudents.specific') }}</option>
                  <option value="CORRECTIVE">{{ t('teacherStudents.corrective') }}</option>
                </select>
              </div>
              <div class="field">
                <label for="teacher-followup-focus">{{ t('teacherStudents.focus') }}</label>
                <select id="teacher-followup-focus" v-model="followUpFocus" class="select" :disabled="followUpDisabled">
                  <option value="WEAK_AREAS">{{ t('teacherStudents.weakAreasOption') }}</option>
                  <option value="QUIZ_ACCURACY">{{ t('teacherStudents.quizAccuracy') }}</option>
                  <option value="CONSISTENCY">{{ t('teacherStudents.consistency') }}</option>
                </select>
              </div>
            </div>

            <div class="teacher-students-followup-actions">
              <button
                class="teacher-outline-button"
                type="button"
                :disabled="followUpDisabled"
                @click="resetFollowUpDraft"
              >
                {{ t('teacherStudents.resetDraft') }}
              </button>
              <button
                class="btn btn-primary"
                type="button"
                :disabled="feedbackReadOnly || submittingFollowUp || !selectedStudent"
                @click="submitFollowUpFeedback"
              >
                {{ sendFeedbackLabel }}
              </button>
            </div>
          </article>
        </div>
      </section>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { teacherApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { teacherFeedback, teacherStudentSummaries } from '@/demo/data'
import { teacherStudentsMessages } from '@/plugins/teacherStudentsMessages'
import type { TeacherFeedbackResponse, TeacherStudentSummaryResponse } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { clampPercent, formatDate, formatDateTime, formatPercent } from '@/utils/format'
import { formatTeacherFeedbackType } from '@/utils/teacherFeedback'

const router = useRouter()
const { t } = useI18n({
  messages: {
    en: { teacherStudents: teacherStudentsMessages.en },
    ms: { teacherStudents: teacherStudentsMessages.ms },
    zh: { teacherStudents: teacherStudentsMessages.zh },
  },
})

type SortMode = 'progress' | 'name'
type StudentAvatarKind = 'student' | 'male' | 'female' | 'hijab'
type StudentRecord = TeacherStudentSummaryResponse & {
  avatarKind: StudentAvatarKind
  displayName: string
  levelLabel: string
  quizAverageLabel: string
}

const studentsResource = useResource(() => teacherApi.students(), teacherStudentSummaries)
const feedbackResource = useResource(() => teacherApi.feedbackList(), teacherFeedback)

const searchTerm = ref('')
const sortMode = ref<SortMode>('progress')
const selectedStudentId = ref<number | null>(null)
const followUpTone = ref('ENCOURAGING')
const followUpFocus = ref('WEAK_AREAS')
const followUpMessage = ref('')
const submittingFollowUp = ref(false)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)

const students = computed<StudentRecord[]>(() =>
  (studentsResource.data.value ?? []).map((item) => ({
    ...item,
    avatarKind: 'student',
    displayName: item.displayName?.trim() || item.fullName,
    levelLabel: item.currentLevel?.trim() || t('teacherStudents.levelNotSet'),
    progressPercent: clampPercent(item.progressPercent),
    quizAveragePercent: item.quizAveragePercent == null ? null : clampPercent(item.quizAveragePercent),
    quizAverageLabel:
      item.quizAveragePercent == null ? t('teacherStudents.noQuizData') : formatPercent(item.quizAveragePercent),
  })),
)

const feedback = computed<TeacherFeedbackResponse[]>(
  () => feedbackResource.data.value ?? (feedbackResource.usingFallback.value ? teacherFeedback : []),
)

const pageLoading = computed(() => studentsResource.loading.value || feedbackResource.loading.value)
const pageError = computed(() => studentsResource.error.value || feedbackResource.error.value)
const noticeError = computed(() => actionError.value || pageError.value)
const usingFallback = computed(() => studentsResource.usingFallback.value || feedbackResource.usingFallback.value)
const feedbackReadOnly = computed(() => usingFallback.value)
const followUpDisabled = computed(() => feedbackReadOnly.value || submittingFollowUp.value || !selectedStudent.value)
const sendFeedbackLabel = computed(() =>
  submittingFollowUp.value ? t('teacherStudents.sending') : t('teacherStudents.sendNow'),
)

const filteredStudents = computed(() => {
  const keyword = searchTerm.value.trim().toLowerCase()
  const result = students.value.filter((item) => {
    if (!keyword) return true
    return (
      item.displayName.toLowerCase().includes(keyword) ||
      (item.email ?? '').toLowerCase().includes(keyword) ||
      item.levelLabel.toLowerCase().includes(keyword) ||
      item.weakAreas.some((entry) => entry.toLowerCase().includes(keyword))
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
  const atRiskCount = students.value.filter((item) => item.atRisk).length
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
      value: String(atRiskCount),
      caption: t('teacherStudents.atRiskHint'),
      icon: 'alert',
      tone: 'orange',
      actionLabel: t('teacherStudents.filterAtRisk'),
      action: selectFirstAtRiskStudent,
    },
    {
      label: t('teacherStudents.quizAverage'),
      value: students.value.some((item) => item.quizAveragePercent != null) ? `${averageQuiz}%` : '--',
      caption: t('teacherStudents.submittedAttempts'),
      icon: 'quiz',
      tone: 'green',
      actionLabel: t('teacherStudents.feedbackCenter'),
      action: openFeedback,
    },
  ]
})

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
      icon: ['file', 'status', 'help', 'history'][index % 4],
      tone: (['green', 'orange', 'purple', 'blue'] as const)[index % 4],
    })),
)

const selectedWeakAreaRows = computed(() => {
  const weakAreas = selectedStudent.value?.weakAreas ?? []
  return weakAreas.slice(0, 4).map((label, index) => ({
    label,
    detail: index === 0 ? t('teacherStudents.primaryFocus') : t('teacherStudents.recordedFocus'),
  }))
})

const selectedInsights = computed(() => {
  if (!selectedStudent.value) {
    return []
  }

  const learner = selectedStudentFirstName.value
  const weakArea = selectedStudent.value.weakAreas[0]
  const quizAverage =
    selectedStudent.value.quizAveragePercent == null
      ? t('teacherStudents.noQuizAverage')
      : t('teacherStudents.quizAverageValue', { average: formatPercent(selectedStudent.value.quizAveragePercent) })

  return [
    {
      icon: selectedStudent.value.atRisk ? 'alert' : 'status',
      tone: selectedStudent.value.atRisk ? 'orange' : 'green',
      message: selectedStudent.value.atRisk
        ? t('teacherStudents.riskInsight', { name: learner })
        : t('teacherStudents.onTrackInsight', { name: learner }),
    },
    {
      icon: weakArea ? 'help' : 'status',
      tone: weakArea ? 'blue' : 'green',
      message: weakArea
        ? t('teacherStudents.visibleWeakArea', { area: weakArea.toLowerCase() })
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
const selectedRiskSummary = computed(() =>
  selectedStudent.value?.atRisk ? t('teacherStudents.needsFollowup') : t('teacherStudents.noRisk'),
)
const selectedRiskStatusLabel = computed(() =>
  selectedStudent.value?.atRisk ? t('teacherStudents.atRisk') : t('teacherStudents.stable'),
)
const selectedStudentBadgeLabel = computed(() =>
  selectedStudent.value?.atRisk ? t('teacherStudents.alert') : t('teacherStudents.active'),
)
const selectedStudentEmailLabel = computed(() => selectedStudent.value?.email || '--')
const selectedStudentFirstName = computed(
  () => selectedStudent.value?.displayName.split(' ')[0] ?? t('teacherStudents.student'),
)
const sortModeLabel = computed(() =>
  sortMode.value === 'progress' ? t('teacherStudents.sortProgress') : t('teacherStudents.sortName'),
)

const manualFollowupDraft = computed(() => {
  if (!selectedStudent.value) {
    return t('teacherStudents.openLearnerDraft')
  }

  const learner = selectedStudentFirstName.value
  const weakFocus = selectedStudent.value.weakAreas[0]
  const quizLabel =
    selectedStudent.value.quizAveragePercent == null
      ? t('teacherStudents.recentActivities')
      : t('teacherStudents.quizAveragePossessive', {
          average: formatPercent(selectedStudent.value.quizAveragePercent),
        })
  const toneLead =
    followUpTone.value === 'CORRECTIVE'
      ? t('teacherStudents.toneCorrective')
      : followUpTone.value === 'SPECIFIC'
        ? t('teacherStudents.toneSpecific')
        : t('teacherStudents.toneEncouraging')
  const focusText =
    followUpFocus.value === 'QUIZ_ACCURACY'
      ? quizLabel
      : followUpFocus.value === 'CONSISTENCY'
        ? t('teacherStudents.consistentPractice')
        : weakFocus
          ? weakFocus.toLowerCase()
          : t('teacherStudents.nextLessonTarget')

  return t('teacherStudents.draftMessage', { lead: toneLead, focus: focusText, name: learner })
})
const followUpFeedbackType = computed(() => {
  if (followUpTone.value === 'CORRECTIVE') return 'CORRECTIVE'
  if (followUpFocus.value === 'QUIZ_ACCURACY') return 'QUIZ'
  if (followUpFocus.value === 'WEAK_AREAS') return 'WEAK_AREA'
  return 'GENERAL'
})
const followUpFeedbackTypeLabel = computed(() => feedbackTypeLabel(followUpFeedbackType.value))

watch(
  filteredStudents,
  (items) => {
    if (!items.some((item) => item.studentId === selectedStudentId.value)) {
      selectedStudentId.value = items[0]?.studentId ?? null
    }
  },
  { immediate: true, deep: true },
)

watch(
  [selectedStudent, manualFollowupDraft],
  () => {
    followUpMessage.value = manualFollowupDraft.value
  },
  { immediate: true },
)

function selectStudent(studentId: number): void {
  selectedStudentId.value = studentId
}

function selectFirstFilteredStudent(): void {
  selectedStudentId.value = filteredStudents.value[0]?.studentId ?? null
}

function selectFirstAtRiskStudent(): void {
  const target = filteredStudents.value.find((item) => item.atRisk)
  if (target) selectedStudentId.value = target.studentId
}

function openFeedback(): void {
  void router.push('/teacher/feedback')
}

function riskTone(atRisk: boolean): string {
  return atRisk ? 'risk' : 'good'
}

function riskStatusLabel(atRisk: boolean): string {
  return atRisk ? t('teacherStudents.needsAttention') : t('teacherStudents.onTrack')
}

function feedbackTypeLabel(type: string | null | undefined): string {
  const keys: Record<string, string> = {
    GENERAL: 'feedbackGeneral',
    QUIZ: 'feedbackQuiz',
    LESSON: 'feedbackLesson',
    ENCOURAGING: 'feedbackEncouraging',
    CORRECTIVE: 'feedbackCorrective',
    PROGRESS: 'feedbackProgress',
    WEAK_AREA: 'feedbackWeakArea',
  }
  const key = type ? keys[type.toUpperCase()] : 'feedbackGeneral'
  return key ? t(`teacherStudents.${key}`) : formatTeacherFeedbackType(type)
}

function cycleSort(): void {
  sortMode.value = sortMode.value === 'progress' ? 'name' : 'progress'
}

function resetFollowUpDraft(): void {
  followUpMessage.value = manualFollowupDraft.value
  actionError.value = null
  actionSuccess.value = t('teacherStudents.draftReset')
}

async function submitFollowUpFeedback(): Promise<void> {
  if (submittingFollowUp.value) return
  actionError.value = null
  actionSuccess.value = null

  if (feedbackReadOnly.value) {
    actionError.value = t('teacherStudents.previewFeedback')
    return
  }

  if (!selectedStudent.value) {
    actionError.value = t('teacherStudents.selectStudent')
    return
  }

  if (!followUpMessage.value.trim()) {
    actionError.value = t('teacherStudents.messageRequired')
    return
  }

  submittingFollowUp.value = true
  try {
    await teacherApi.createFeedback({
      studentId: selectedStudent.value.studentId,
      feedbackType: followUpFeedbackType.value,
      message: followUpMessage.value.trim(),
    })
    await feedbackResource.reload()
    await studentsResource.reload()
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
.teacher-students-page {
  position: relative;
}

.teacher-students-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.teacher-students-stat-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  padding: 16px 18px;
  border-radius: 24px;
  border: 1px solid rgba(232, 236, 247, 0.96);
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 10px 22px rgba(145, 154, 201, 0.06);
}

.teacher-students-stat-card.is-purple {
  background: linear-gradient(135deg, rgba(246, 241, 255, 0.98), rgba(255, 255, 255, 0.98));
}

.teacher-students-stat-card.is-purple-soft {
  background: linear-gradient(135deg, rgba(250, 247, 255, 0.98), rgba(255, 255, 255, 0.98));
}

.teacher-students-stat-card.is-orange {
  background: linear-gradient(135deg, rgba(255, 245, 234, 0.98), rgba(255, 255, 255, 0.98));
}

.teacher-students-stat-card.is-green {
  background: linear-gradient(135deg, rgba(244, 255, 244, 0.98), rgba(255, 255, 255, 0.98));
}

.teacher-students-stat-icon {
  width: 62px;
  height: 62px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 0.9rem;
  font-weight: 800;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: inset 0 0 0 1px rgba(232, 236, 247, 0.96);
}

.teacher-students-stat-copy {
  display: grid;
  gap: 4px;
}

.teacher-students-stat-copy small,
.teacher-students-stat-copy span {
  color: #66738e;
}

.teacher-students-stat-copy strong {
  font-family: 'Fredoka', sans-serif;
  font-size: 2rem;
  line-height: 1;
}

.teacher-students-workbench {
  display: grid;
  grid-template-columns: 320px minmax(0, 1.12fr) 372px;
  gap: 18px;
  align-items: start;
}

.teacher-students-main-column,
.teacher-students-side-column {
  display: grid;
  gap: 18px;
}

.teacher-students-directory-card,
.teacher-students-profile-card,
.teacher-students-progress-card,
.teacher-students-weak-card,
.teacher-students-insights-card,
.teacher-students-feedback-card,
.teacher-students-followup-card {
  border-radius: 28px;
}

.teacher-students-directory-head,
.teacher-students-card-head {
  display: grid;
  gap: 10px;
}

.teacher-students-directory-tools {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
}

.teacher-students-search {
  display: grid;
  gap: 6px;
  padding: 10px 14px;
  border-radius: 16px;
  border: 1px solid rgba(230, 233, 245, 0.96);
  background: rgba(255, 255, 255, 0.98);
  color: #8b94ad;
}

.teacher-students-filter-button {
  min-height: 44px;
  padding-inline: 18px;
}

.teacher-students-search-input {
  min-height: 36px;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text);
  font: inherit;
}

.teacher-students-directory-list,
.teacher-students-feedback-list,
.teacher-students-insight-list {
  display: grid;
  gap: 10px;
}

.teacher-students-directory-item {
  width: 100%;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  padding: 10px 12px;
  border-radius: 18px;
  border: 1px solid rgba(235, 239, 247, 0.96);
  background: rgba(255, 255, 255, 0.98);
  text-align: left;
  color: var(--text);
}

.teacher-students-directory-item.is-active {
  border-color: rgba(128, 96, 255, 0.42);
  box-shadow: inset 0 0 0 1px rgba(128, 96, 255, 0.14);
  background: linear-gradient(135deg, rgba(248, 245, 255, 0.98), rgba(255, 255, 255, 0.98));
}

.teacher-students-directory-copy {
  display: grid;
  gap: 6px;
}

.teacher-students-directory-copy span,
.teacher-students-directory-copy small {
  color: #717d98;
}

.teacher-students-directory-progress {
  font-family: 'Fredoka', sans-serif;
  font-size: 1.52rem;
  line-height: 1;
}

.teacher-students-directory-progress.is-good {
  color: #49b549;
}

.teacher-students-directory-progress.is-risk {
  color: #ff6a5b;
}

.teacher-students-directory-more {
  color: #919bb6;
  font-size: 0.8rem;
  line-height: 1;
}

.teacher-students-profile-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.teacher-students-profile-meta {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 14px;
  align-items: center;
}

.teacher-students-profile-meta span,
.teacher-students-profile-meta small {
  color: #68748f;
}

.teacher-students-star {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 56px;
  height: 22px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(255, 184, 62, 0.18);
  color: #f0a81b;
  font-size: 0.76rem;
  font-weight: 800;
}

.teacher-students-profile-progress {
  display: grid;
  justify-items: end;
  gap: 6px;
}

.teacher-students-profile-progress strong {
  font-family: 'Fredoka', sans-serif;
  font-size: 2.85rem;
  color: #7253ff;
  line-height: 1;
}

.teacher-students-profile-progress span {
  color: #6c7893;
}

.teacher-students-contact-grid,
.teacher-students-progress-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.teacher-students-contact-grid article,
.teacher-students-mini-stat {
  display: grid;
  gap: 6px;
  padding: 14px;
  border-radius: 18px;
  background: rgba(247, 249, 253, 0.96);
}

.teacher-students-contact-grid small,
.teacher-students-mini-stat small,
.teacher-students-mini-stat span {
  color: #6d7893;
}

.teacher-students-mini-stat strong {
  font-family: 'Fredoka', sans-serif;
  font-size: 1.8rem;
  line-height: 1;
}

.teacher-students-overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 18px;
}

.teacher-students-weak-list {
  display: grid;
  gap: 18px;
}

.teacher-students-weak-row {
  display: grid;
  gap: 8px;
}

.teacher-students-weak-row span {
  color: #7d88a3;
}

.teacher-footer-link {
  width: fit-content;
  min-height: 38px;
  margin-top: 10px;
  border: 0;
  padding: 6px 0;
  background: transparent;
  color: var(--primary);
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.teacher-students-insight-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  align-items: start;
  padding: 8px 0;
  color: #596681;
}

.teacher-students-insight-icon,
.teacher-students-feedback-icon {
  width: 38px;
  height: 38px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  font-weight: 800;
}

.teacher-students-insight-icon.is-green,
.teacher-students-feedback-icon.is-green {
  background: rgba(100, 203, 86, 0.12);
  color: #45aa43;
}

.teacher-students-insight-icon.is-orange,
.teacher-students-feedback-icon.is-orange {
  background: rgba(255, 183, 75, 0.14);
  color: #de8d1f;
}

.teacher-students-insight-icon.is-blue {
  background: rgba(63, 144, 255, 0.12);
  color: #2f86f6;
}

.teacher-students-insight-icon.is-purple,
.teacher-students-feedback-icon.is-purple {
  background: rgba(131, 101, 255, 0.12);
  color: var(--primary);
}

.teacher-students-feedback-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  align-items: start;
  padding: 12px 0;
  border-bottom: 1px solid rgba(236, 239, 247, 0.96);
}

.teacher-students-feedback-item:last-child {
  border-bottom: 0;
}

.teacher-students-feedback-copy {
  display: grid;
  gap: 4px;
}

.teacher-students-feedback-copy small,
.teacher-students-followup-card p,
.teacher-students-followup-box p {
  color: #5f6c87;
}

.teacher-students-followup-card {
  display: grid;
  gap: 14px;
  background: linear-gradient(135deg, rgba(247, 243, 255, 0.98), rgba(255, 255, 255, 0.98));
}

.teacher-students-followup-headline {
  align-items: center;
}

.teacher-students-followup-info {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: rgba(131, 101, 255, 0.1);
  color: var(--primary);
  font-size: 0.78rem;
  font-weight: 700;
}

.teacher-students-followup-box {
  display: grid;
  gap: 10px;
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.98);
  border: 1px solid rgba(230, 233, 245, 0.96);
}

.teacher-students-followup-textarea {
  min-height: 132px;
}

.teacher-students-followup-sign {
  color: #6e63ca;
  font-weight: 700;
}

.teacher-students-followup-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.teacher-students-followup-actions {
  display: grid;
  grid-template-columns: minmax(0, 0.82fr) minmax(0, 1fr);
  gap: 12px;
}

.teacher-students-empty-copy {
  color: #838eaa;
  line-height: 1.5;
  margin: 0;
}

@media (max-width: 1480px) {
  .teacher-students-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .teacher-students-workbench {
    grid-template-columns: 320px minmax(0, 1fr);
  }

  .teacher-students-side-column {
    grid-column: 1 / -1;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1180px) {
  .teacher-students-workbench,
  .teacher-students-side-column,
  .teacher-students-contact-grid,
  .teacher-students-progress-summary {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .teacher-students-stats,
  .teacher-students-directory-tools,
  .teacher-students-followup-fields,
  .teacher-students-followup-actions {
    grid-template-columns: 1fr;
  }

  .teacher-students-stat-card,
  .teacher-students-profile-head {
    grid-template-columns: 1fr;
    justify-items: start;
  }

  .teacher-students-profile-head {
    display: grid;
  }
}
</style>
