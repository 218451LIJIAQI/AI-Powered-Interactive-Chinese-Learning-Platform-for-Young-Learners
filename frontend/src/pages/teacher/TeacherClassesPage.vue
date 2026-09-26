<template>
  <div class="page-stack teacher-classes-page">
    <PageHeaderBlock
      :title="t('teacherClasses.title')"
      :description="t('teacherClasses.description')"
      icon="classes"
      :eyebrow="t('teacherClasses.eyebrow')"
    >
      <template #actions>
        <button class="teacher-sample-link" type="button" :disabled="teacherApprovalLocked" @click="toggleComposer">
          {{ composerToggleLabel }}
        </button>
      </template>
    </PageHeaderBlock>

    <DataNotice
      :using-fallback="usingFallback"
      :error="noticeError"
      :success="actionSuccess"
      :fallback-title="t('teacherClasses.previewTitle')"
      :fallback-message="t('teacherClasses.previewMessage')"
      :error-title="t('teacherClasses.attentionTitle')"
      :detail="classesNoticeDetail"
    />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="teacher-classes-upcoming panel">
        <div class="row-between teacher-classes-section-head">
          <div class="student-section-heading">
            <strong>{{ t('teacherClasses.upcoming') }}</strong>
          </div>
          <div class="row teacher-classes-header-actions">
            <span class="teacher-classes-date-pill">{{ currentDateLabel }}</span>
            <button
              class="student-link-button teacher-classes-head-link"
              type="button"
              :disabled="teacherApprovalLocked"
              @click="openComposer"
            >
              {{ t('teacherClasses.openScheduler') }}
            </button>
          </div>
        </div>

        <div v-if="teacherApprovalLocked" class="teacher-classes-empty">
          {{ t('teacherClasses.verificationRequired') }}
        </div>
        <div v-else-if="!liveClassCards.length" class="teacher-classes-empty">
          {{ t('teacherClasses.noLiveClasses') }}
        </div>
        <div v-else class="teacher-classes-live-list">
          <article
            v-for="item in liveClassCards"
            :key="item.id"
            class="teacher-classes-live-item"
            :class="`is-${item.tone}`"
          >
            <div class="teacher-classes-live-time">
              <small>{{ item.dayLabel }}</small>
              <strong>{{ item.timeLabel }}</strong>
            </div>

            <div class="teacher-classes-live-media">
              <div class="teacher-classes-live-thumb" :class="`is-${item.art}`">
                <IconGlyph name="classes" :size="24" />
              </div>
              <div class="teacher-classes-live-copy">
                <div class="row u-wrap">
                  <strong>{{ item.title }}</strong>
                  <span class="teacher-classes-chip">{{ item.classCode }}</span>
                </div>
                <span>{{ item.topic }}</span>
                <small>{{ item.studentLabel }}</small>
              </div>
            </div>

            <div class="teacher-classes-live-platform">
              <span class="teacher-classes-platform-icon" :class="`is-${item.platformTone}`">
                <IconGlyph name="live" :size="18" />
              </span>
              <div class="stack u-gap-2">
                <strong>{{ item.platform }}</strong>
                <span>{{ item.platformMeta }}</span>
              </div>
            </div>

            <div class="teacher-classes-live-actions">
              <div class="teacher-classes-live-actions-main">
                <button class="btn btn-primary" type="button" @click="openMeeting(item.meetingLink)">
                  {{ t('teacherClasses.openMeeting') }}
                </button>
                <button class="teacher-outline-button" type="button" @click="selectClass(item.id)">
                  {{ item.editLabel }}
                </button>
                <button class="teacher-outline-button" type="button" @click="loadEnrolments(item.id)">
                  {{ t('teacherClasses.viewRoster') }}
                </button>
              </div>
              <button
                class="teacher-classes-live-more"
                type="button"
                :aria-label="t('teacherClasses.detailsAria')"
                @click="selectClass(item.id)"
              >
                {{ t('teacherClasses.details') }}
              </button>
            </div>
          </article>
        </div>

        <div class="teacher-classes-support-strip">
          <div class="row">
            <span class="teacher-classes-support-icon"><IconGlyph name="help" :size="14" /></span>
            <span>
              {{ t('teacherClasses.externalSupport') }}
            </span>
          </div>
          <div class="row teacher-classes-support-actions">
            <span>{{ t('teacherClasses.needHelp') }}</span>
            <button class="teacher-guide-button" type="button" :disabled="teacherApprovalLocked" @click="openComposer">
              {{ t('teacherClasses.openScheduler') }}
            </button>
          </div>
        </div>
      </section>

      <section v-if="showComposer" class="teacher-classes-composer-grid">
        <article class="panel teacher-classes-editor-card">
          <div class="row-between teacher-classes-section-head">
            <div class="student-section-heading">
              <strong>{{ composerTitle }}</strong>
            </div>
            <button v-if="selectedLiveClass" class="student-link-button" type="button" @click="resetComposer">
              {{ t('teacherClasses.newClass') }}
            </button>
          </div>

          <form class="teacher-classes-form" @submit.prevent="submitClass">
            <div class="form-grid two">
              <div class="field">
                <label for="teacher-class-title">{{ t('teacherClasses.classTitle') }}</label>
                <input
                  id="teacher-class-title"
                  v-model="classForm.title"
                  class="input"
                  maxlength="200"
                  required
                  :disabled="classComposerDisabled"
                />
              </div>
              <div class="field">
                <label for="teacher-class-platform">{{ t('teacherClasses.meetingPlatform') }}</label>
                <select
                  id="teacher-class-platform"
                  v-model="classForm.meetingPlatform"
                  class="select"
                  :disabled="classComposerDisabled"
                >
                  <option value="ZOOM">Zoom</option>
                  <option value="GOOGLE_MEET">Google Meet</option>
                  <option value="MICROSOFT_TEAMS">Microsoft Teams</option>
                  <option value="OTHER">{{ t('teacherClasses.other') }}</option>
                </select>
              </div>
            </div>

            <div class="field">
              <label for="teacher-class-description">{{ t('teacherClasses.topicDescription') }}</label>
              <textarea
                id="teacher-class-description"
                v-model="classForm.description"
                class="textarea"
                maxlength="1000"
                :disabled="classComposerDisabled"
              />
            </div>

            <div class="form-grid two">
              <div class="field">
                <label for="teacher-class-lesson">{{ t('teacherClasses.lesson') }}</label>
                <select
                  id="teacher-class-lesson"
                  v-model.number="classForm.lessonId"
                  class="select"
                  :disabled="classComposerDisabled"
                >
                  <option :value="0">{{ t('teacherClasses.noLesson') }}</option>
                  <option v-for="lesson in lessonOptions" :key="lesson.id" :value="lesson.id">
                    {{ lesson.title }}
                  </option>
                </select>
              </div>
              <div class="field">
                <label for="teacher-class-package">{{ t('teacherClasses.package') }}</label>
                <select
                  id="teacher-class-package"
                  v-model.number="classForm.packageId"
                  class="select"
                  :disabled="classComposerDisabled"
                >
                  <option :value="0">{{ t('teacherClasses.noPackage') }}</option>
                  <option v-for="coursePackage in packageOptions" :key="coursePackage.id" :value="coursePackage.id">
                    {{ coursePackage.title }}
                  </option>
                </select>
              </div>
            </div>

            <div class="form-grid two">
              <div class="field">
                <label for="teacher-class-start-time">{{ t('teacherClasses.startTime') }}</label>
                <input
                  id="teacher-class-start-time"
                  v-model="classForm.startTime"
                  class="input"
                  type="datetime-local"
                  required
                  :disabled="classComposerDisabled"
                />
              </div>
              <div class="field">
                <label for="teacher-class-end-time">{{ t('teacherClasses.endTime') }}</label>
                <input
                  id="teacher-class-end-time"
                  v-model="classForm.endTime"
                  class="input"
                  type="datetime-local"
                  required
                  :disabled="classComposerDisabled"
                />
              </div>
            </div>

            <div class="form-grid two">
              <div class="field">
                <label for="teacher-class-meeting-link">{{ t('teacherClasses.meetingLink') }}</label>
                <input
                  id="teacher-class-meeting-link"
                  v-model="classForm.meetingLink"
                  class="input"
                  type="url"
                  required
                  :disabled="classComposerDisabled"
                />
              </div>
              <div class="field">
                <label for="teacher-class-max-participants">{{ t('teacherClasses.maxParticipants') }}</label>
                <input
                  id="teacher-class-max-participants"
                  v-model.number="classForm.maxParticipants"
                  class="input"
                  type="number"
                  min="1"
                  :disabled="classComposerDisabled"
                />
              </div>
            </div>

            <div class="teacher-classes-form-footer">
              <div class="teacher-classes-form-note">
                {{ t('teacherClasses.formNote') }}
              </div>
              <div class="row u-justify-end u-wrap">
                <button
                  v-if="selectedLiveClass"
                  class="teacher-outline-button"
                  type="button"
                  :disabled="classesReadOnly || submittingCancel"
                  @click="cancelSelectedClass"
                >
                  {{ cancelClassButtonLabel }}
                </button>
                <button
                  class="btn btn-primary"
                  type="submit"
                  :disabled="classesReadOnly || submittingCreate || submittingUpdate"
                >
                  {{ submitClassButtonLabel }}
                </button>
              </div>
            </div>
          </form>
        </article>

        <article class="panel teacher-classes-roster-card">
          <div class="row-between teacher-classes-section-head">
            <div class="student-section-heading">
              <strong>{{ t('teacherClasses.selectedRoster') }}</strong>
            </div>
            <span class="teacher-classes-roster-count">{{
              t('teacherClasses.learners', { count: enrolments.length })
            }}</span>
          </div>

          <div class="teacher-classes-roster-list">
            <article v-for="student in enrolments" :key="student.id" class="teacher-classes-roster-item">
              <PortraitAvatar :size="52" rounded="soft" kind="student" />
              <div class="teacher-classes-roster-copy">
                <strong>{{ student.studentName }}</strong>
                <span>{{ student.studentEmail }}</span>
                <small>{{ formatDate(student.enrolledAt) }}</small>
              </div>
              <StatusPill :label="formatStatus(student.enrolmentStatus)" tone="info" />
            </article>

            <div v-if="!enrolments.length" class="teacher-classes-empty">
              {{ t('teacherClasses.emptyRoster') }}
            </div>
          </div>
        </article>
      </section>

      <section class="teacher-classes-dashboard-grid">
        <article class="panel teacher-classes-library-card">
          <div class="row-between teacher-classes-section-head">
            <div class="student-section-heading">
              <strong>{{ t('teacherClasses.library') }}</strong>
            </div>
            <button class="student-link-button" type="button" @click="openContent">
              {{ t('teacherClasses.viewAll') }}
            </button>
          </div>

          <div class="teacher-classes-library-list">
            <article v-for="item in recordingCards" :key="item.id" class="teacher-classes-library-item">
              <div class="teacher-classes-library-thumb" :class="`is-${item.art}`">
                <IconGlyph name="play" :size="24" />
              </div>
              <div class="teacher-classes-library-copy">
                <strong>{{ item.title }}</strong>
                <span>{{ item.meta }}</span>
              </div>
              <div class="teacher-classes-library-side">
                <strong>{{ item.lengthLabel }}</strong>
                <button class="teacher-play-button" type="button" @click="openContent">
                  {{ t('teacherClasses.open') }}
                </button>
              </div>
            </article>
          </div>

          <button class="teacher-footer-cta" type="button" @click="openContent">
            <span>{{ t('teacherClasses.browseRecordings') }}</span>
            <IconGlyph name="chevronRight" :size="16" aria-hidden="true" />
          </button>
        </article>

        <div class="teacher-classes-center-column">
          <article class="panel teacher-classes-attendance-card">
            <div class="row-between teacher-classes-section-head">
              <div class="student-section-heading teacher-classes-title-with-note">
                <strong>{{ t('teacherClasses.attendance') }}</strong>
                <small class="teacher-classes-head-note">{{ t('teacherClasses.selectedClass') }}</small>
              </div>
              <button class="student-link-button" type="button" @click="loadEnrolments(primaryClass.id)">
                {{ t('teacherClasses.viewDetails') }}
              </button>
            </div>

            <div class="teacher-classes-attendance-wrap">
              <div class="teacher-classes-attendance-ring">
                <div class="teacher-classes-attendance-inner">
                  <small>{{ t('teacherClasses.checkedIn') }}</small>
                  <strong>{{ attendanceRate }}%</strong>
                  <span>{{ presentCount }} / {{ totalRosterCount }}</span>
                </div>
              </div>

              <div class="teacher-classes-attendance-legend">
                <div v-for="item in attendanceLegend" :key="item.label" class="teacher-classes-attendance-row">
                  <span class="teacher-classes-attendance-dot" :class="`is-${item.tone}`" />
                  <span>{{ item.label }}</span>
                  <strong>{{ item.count }} ({{ item.percent }}%)</strong>
                </div>
              </div>
            </div>
          </article>

          <article class="panel teacher-classes-reminders-card">
            <div class="row-between teacher-classes-section-head">
              <div class="student-section-heading">
                <strong>{{ t('teacherClasses.followups') }}</strong>
              </div>
              <button class="student-link-button" type="button" @click="openStudents">
                {{ t('teacherClasses.openStudents') }}
              </button>
            </div>

            <div class="teacher-classes-reminder-list">
              <article v-for="item in reminderCards" :key="item.title" class="teacher-classes-reminder-item">
                <span class="teacher-classes-reminder-icon" :class="`is-${item.tone}`"
                  ><IconGlyph :name="item.icon" :size="20"
                /></span>
                <div class="teacher-classes-reminder-copy">
                  <strong>{{ item.title }}</strong>
                  <span>{{ item.copy }}</span>
                </div>
                <span class="teacher-classes-reminder-tag" :class="`is-${item.tone}`">{{ item.badge }}</span>
              </article>
            </div>
          </article>
        </div>

        <div class="teacher-classes-right-column">
          <article class="panel teacher-classes-homework-card">
            <div class="row-between teacher-classes-section-head">
              <div class="student-section-heading">
                <strong>{{ t('teacherClasses.learningFollowups') }}</strong>
              </div>
              <button class="student-link-button" type="button" @click="openContent">
                {{ t('teacherClasses.openContent') }}
              </button>
            </div>

            <div class="teacher-classes-homework-list">
              <article v-for="item in assignmentCards" :key="item.title" class="teacher-classes-homework-item">
                <span class="teacher-classes-homework-icon" :class="`is-${item.tone}`"
                  ><IconGlyph :name="item.icon" :size="20"
                /></span>
                <div class="teacher-classes-homework-copy">
                  <div class="row u-wrap">
                    <strong>{{ item.title }}</strong>
                    <span class="teacher-classes-chip">{{ item.classCode }}</span>
                    <span class="teacher-classes-deadline">{{ item.deadline }}</span>
                  </div>
                  <span>{{ item.progressLabel }}</span>
                  <div class="teacher-classes-homework-progress">
                    <span :style="{ width: `${item.progress}%` }" />
                  </div>
                </div>
                <strong class="teacher-classes-homework-percent">{{ item.progress }}%</strong>
              </article>
            </div>
          </article>

          <article class="panel teacher-classes-actions-card">
            <div class="teacher-classes-actions-head">
              <div class="student-section-heading">
                <strong>{{ t('teacherClasses.quickActions') }}</strong>
              </div>
            </div>

            <div class="teacher-classes-actions-grid">
              <button
                v-for="item in quickActions"
                :key="item.title"
                class="teacher-classes-action-button"
                type="button"
                @click="item.action"
              >
                <span class="teacher-classes-action-icon" :class="`is-${item.tone}`"
                  ><IconGlyph :name="item.icon" :size="20"
                /></span>
                <span>{{ item.title }}</span>
              </button>
            </div>
          </article>
        </div>
      </section>
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
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { appEnv } from '@/config/env'
import { useResource } from '@/composables/useResource'
import { useTeacherAccess } from '@/composables/useTeacherAccess'
import type { TeacherLiveClassPayload } from '@/api/services'
import {
  teacherAnalytics,
  teacherCoursePackages,
  teacherEnrolments,
  teacherLessons,
  teacherLiveClasses,
  teacherRecordedVideos,
  teacherVideoProgress,
} from '@/demo/data'
import { teacherClassesMessages } from '@/plugins/teacherClassesMessages'
import { useAuthStore } from '@/stores/auth'
import type { CoursePackageResponse, LessonResponse } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { clampPercent, formatDate, getDisplayLocale, nonNegativeInteger, ratioPercent } from '@/utils/format'
import { openSafeHttpUrl } from '@/utils/url'

type MeetingPlatformValue = TeacherLiveClassPayload['meetingPlatform']

const router = useRouter()
const { t, locale } = useI18n({
  messages: {
    en: { teacherClasses: teacherClassesMessages.en },
    ms: { teacherClasses: teacherClassesMessages.ms },
    zh: { teacherClasses: teacherClassesMessages.zh },
  },
})
const auth = useAuthStore()
const { teacherApprovalLocked, teacherApprovalMessage } = useTeacherAccess()
const previewFallbackEnabled = computed(() => appEnv.useDemoData && auth.isPreviewSession)

const liveClassesResource = useResource(
  () => (teacherApprovalLocked.value ? Promise.resolve([]) : teacherApi.liveClasses()),
  teacherLiveClasses,
)
const analyticsResource = useResource(() => teacherApi.analytics(), teacherAnalytics)
const lessonsResource = useResource(
  () => (teacherApprovalLocked.value ? Promise.resolve([]) : teacherApi.lessons()),
  teacherLessons,
)
const packagesResource = useResource(
  () => (teacherApprovalLocked.value ? Promise.resolve([]) : teacherApi.packages()),
  teacherCoursePackages,
)
const selectedClassId = ref<number | null>(null)
const selectedLessonId = ref<number | null>(null)
const enrolmentsResource = useResource(
  () => {
    if (teacherApprovalLocked.value || !selectedClassId.value) {
      return Promise.resolve([])
    }
    return teacherApi.enrolments(selectedClassId.value)
  },
  teacherEnrolments,
  false,
)
const recordedResource = useResource(
  () => {
    if (teacherApprovalLocked.value || !selectedLessonId.value) {
      return Promise.resolve([])
    }
    return teacherApi.recordedVideos(selectedLessonId.value)
  },
  teacherRecordedVideos,
  false,
)
const progressResource = useResource(
  () => {
    if (teacherApprovalLocked.value || !selectedLessonId.value) {
      return Promise.resolve([])
    }
    return teacherApi.lessonVideoProgress(selectedLessonId.value)
  },
  teacherVideoProgress,
  false,
)

const liveClasses = computed(
  () => liveClassesResource.data.value ?? (liveClassesResource.usingFallback.value ? teacherLiveClasses : []),
)
const analytics = computed(
  () =>
    analyticsResource.data.value ??
    (analyticsResource.usingFallback.value
      ? teacherAnalytics
      : { ...teacherAnalytics, overview: { ...teacherAnalytics.overview, totalActiveStudents: 0 } }),
)
const lessons = computed<LessonResponse[]>(
  () => lessonsResource.data.value ?? (lessonsResource.usingFallback.value ? teacherLessons : []),
)
const coursePackages = computed<CoursePackageResponse[]>(
  () => packagesResource.data.value ?? (packagesResource.usingFallback.value ? teacherCoursePackages : []),
)
const enrolments = computed(
  () => enrolmentsResource.data.value ?? (enrolmentsResource.usingFallback.value ? teacherEnrolments : []),
)
const recordedVideos = computed(
  () => recordedResource.data.value ?? (recordedResource.usingFallback.value ? teacherRecordedVideos : []),
)
const videoProgress = computed(
  () => progressResource.data.value ?? (progressResource.usingFallback.value ? teacherVideoProgress : []),
)
const lessonOptions = computed<LessonResponse[]>(() =>
  lessons.value.filter((item: LessonResponse) => item.contentStatus !== 'ARCHIVED'),
)
const packageOptions = computed<CoursePackageResponse[]>(() =>
  coursePackages.value.filter((item: CoursePackageResponse) => item.contentStatus !== 'ARCHIVED'),
)

const pageLoading = computed(
  () =>
    liveClassesResource.loading.value ||
    analyticsResource.loading.value ||
    lessonsResource.loading.value ||
    packagesResource.loading.value ||
    enrolmentsResource.loading.value ||
    recordedResource.loading.value ||
    progressResource.loading.value,
)
const pageError = computed(
  () =>
    liveClassesResource.error.value ||
    analyticsResource.error.value ||
    lessonsResource.error.value ||
    packagesResource.error.value ||
    enrolmentsResource.error.value ||
    recordedResource.error.value ||
    progressResource.error.value,
)
const noticeError = computed(() => actionError.value || pageError.value)
const usingFallback = computed(
  () =>
    liveClassesResource.usingFallback.value ||
    analyticsResource.usingFallback.value ||
    lessonsResource.usingFallback.value ||
    packagesResource.usingFallback.value ||
    enrolmentsResource.usingFallback.value ||
    recordedResource.usingFallback.value ||
    progressResource.usingFallback.value,
)
const classesReadOnly = computed(
  () =>
    teacherApprovalLocked.value ||
    liveClassesResource.usingFallback.value ||
    lessonsResource.usingFallback.value ||
    packagesResource.usingFallback.value,
)
const classComposerDisabled = computed(() => classesReadOnly.value || submittingCreate.value || submittingUpdate.value)
const composerToggleLabel = computed(() => {
  if (teacherApprovalLocked.value) return t('teacherClasses.verificationPending')
  return showComposer.value ? t('teacherClasses.closeScheduler') : t('teacherClasses.scheduleClass')
})
const composerTitle = computed(() =>
  selectedLiveClass.value ? t('teacherClasses.editLiveClass') : t('teacherClasses.scheduleLiveClass'),
)
const cancelClassButtonLabel = computed(() =>
  submittingCancel.value ? t('teacherClasses.cancelling') : t('teacherClasses.cancelClass'),
)
const submitClassButtonLabel = computed(() => {
  if (selectedLiveClass.value) {
    return submittingUpdate.value ? t('teacherClasses.saving') : t('teacherClasses.saveChanges')
  }

  return submittingCreate.value ? t('teacherClasses.creating') : t('teacherClasses.createClass')
})
const classesNoticeDetail = computed(() => {
  if (teacherApprovalLocked.value) {
    return teacherApprovalMessage.value
  }

  if (noticeError.value) {
    return selectedClassId.value ? t('teacherClasses.retryContext') : t('teacherClasses.reopenClass')
  }

  if (usingFallback.value) {
    return t('teacherClasses.previewFlows')
  }

  if (actionSuccess.value) {
    return selectedLiveClass.value
      ? t('teacherClasses.currentFocus', { title: selectedLiveClass.value.title })
      : t('teacherClasses.actionComplete')
  }

  return null
})

const showComposer = ref(false)
const submittingCreate = ref(false)
const submittingUpdate = ref(false)
const submittingCancel = ref(false)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)

const classForm = reactive<TeacherLiveClassPayload>({
  title: '',
  description: '',
  lessonId: 0,
  packageId: 0,
  startTime: '',
  endTime: '',
  timeZone: 'Asia/Singapore',
  meetingLink: '',
  meetingPlatform: 'ZOOM',
  status: 'SCHEDULED',
  maxParticipants: 20,
})

const currentDateLabel = computed(() =>
  new Intl.DateTimeFormat(getDisplayLocale(locale.value), {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    weekday: 'short',
  }).format(new Date()),
)

const primaryClass = computed(
  () => liveClasses.value[0] ?? (liveClassesResource.usingFallback.value ? teacherLiveClasses[0] : null),
)
const selectedLiveClass = computed(() => liveClasses.value.find((item) => item.id === selectedClassId.value) ?? null)

const liveClassCards = computed(() =>
  liveClasses.value.slice(0, 3).map((item, index) => {
    const start = new Date(item.startTime)
    const platform = normalizeMeetingPlatform(item.meetingPlatform)
    const platformLabel = formatMeetingPlatform(platform)
    const platformMeta =
      platform === 'GOOGLE_MEET'
        ? t('teacherClasses.meetingCode', { code: meetingCodeFromLink(item.meetingLink) })
        : t('teacherClasses.meetingId', { code: meetingCodeFromLink(item.meetingLink) })

    return {
      id: item.id,
      title: item.title,
      topic: t('teacherClasses.topic', {
        topic: item.lesson?.lessonTitle || item.description || t('teacherClasses.supportSession'),
      }),
      studentLabel: t('teacherClasses.students', { count: item.enrolledCount }),
      timeLabel: new Intl.DateTimeFormat(getDisplayLocale(locale.value), { hour: '2-digit', minute: '2-digit' }).format(
        start,
      ),
      dayLabel: formatClassDate(start),
      classCode: formatStatus(item.liveClassStatus),
      platform: platformLabel,
      platformMeta,
      meetingLink: item.meetingLink,
      editLabel: selectedClassId.value === item.id ? t('teacherClasses.editing') : t('teacherClasses.editClass'),
      platformTone: platformToneFor(platform),
      art: rotatingClassArt(index),
      tone: liveClassTone(index),
    }
  }),
)

const recordingCards = computed(() =>
  recordedVideos.value.slice(0, 3).map((item, index) => {
    const durationSeconds = nonNegativeInteger(item.durationSeconds)

    return {
      id: item.id,
      title: item.title,
      meta: t('teacherClasses.lessonMeta', { id: item.lessonId, date: formatDate(item.createdAt) }),
      lengthLabel:
        durationSeconds > 0
          ? t('teacherClasses.minutesShort', { count: Math.max(1, nonNegativeInteger(durationSeconds / 60)) })
          : t('teacherClasses.durationUnavailable'),
      art: rotatingClassArt(index),
    }
  }),
)

const attendedCount = computed(() => enrolments.value.filter((item) => item.attendedAt).length)
const pendingCheckInCount = computed(
  () => enrolments.value.filter((item) => item.enrolmentStatus !== 'CANCELLED' && !item.attendedAt).length,
)
const cancelledCount = computed(() => enrolments.value.filter((item) => item.enrolmentStatus === 'CANCELLED').length)
const totalRosterCount = computed(() => enrolments.value.length)
const presentCount = computed(() => attendedCount.value)
const attendanceRate = computed(() => ratioPercent(presentCount.value, totalRosterCount.value))
const pendingCheckInTone = computed(() => (pendingCheckInCount.value > 0 ? 'orange' : 'green'))

const attendanceLegend = computed(() => {
  const total = Math.max(totalRosterCount.value, 1)

  return [
    {
      label: t('teacherClasses.checkedIn'),
      count: presentCount.value,
      percent: ratioPercent(presentCount.value, total),
      tone: 'green',
    },
    {
      label: t('teacherClasses.pending'),
      count: pendingCheckInCount.value,
      percent: ratioPercent(pendingCheckInCount.value, total),
      tone: 'yellow',
    },
    {
      label: t('teacherClasses.cancelled'),
      count: cancelledCount.value,
      percent: ratioPercent(cancelledCount.value, total),
      tone: 'red',
    },
  ]
})

const reminderCards = computed(() => {
  const activeClass = selectedLiveClass.value ?? primaryClass.value

  return [
    {
      title: activeClass?.lesson?.lessonTitle
        ? t('teacherClasses.reviewLesson', { lesson: activeClass.lesson.lessonTitle })
        : t('teacherClasses.selectClassReview'),
      copy: activeClass
        ? `${activeClass.title} - ${formatDate(activeClass.startTime)}`
        : t('teacherClasses.classDetailsPending'),
      badge: activeClass?.liveClassStatus ? formatStatus(activeClass.liveClassStatus) : t('teacherClasses.noClass'),
      icon: 'status',
      tone: 'green',
    },
    {
      title: pendingCheckInCount.value > 0 ? t('teacherClasses.pendingFollowup') : t('teacherClasses.rosterClear'),
      copy: t('teacherClasses.pendingAttendance', { count: pendingCheckInCount.value }),
      badge: t('teacherClasses.rosterCount', { count: enrolments.value.length }),
      icon: 'alert',
      tone: pendingCheckInTone.value,
    },
  ]
})

const assignmentCards = computed(() => {
  const totalVideos = Math.max(recordedVideos.value.length, 1)
  const completedVideoViews = videoProgress.value.filter((item) => item.completed).length
  const progressAverage = clampPercent(
    videoProgress.value.reduce((sum, item) => sum + clampPercent(item.progressPercentage), 0) /
      Math.max(videoProgress.value.length, 1),
  )

  return [
    {
      title: selectedLiveClass.value?.lesson?.lessonTitle || t('teacherClasses.linkedRecordings'),
      classCode: selectedLiveClass.value
        ? formatStatus(selectedLiveClass.value.liveClassStatus)
        : t('teacherClasses.recordings'),
      deadline: t('teacherClasses.recordingCount', { count: recordedVideos.value.length }),
      progressLabel: t('teacherClasses.completedViews', { count: completedVideoViews }),
      progress: ratioPercent(completedVideoViews, videoProgress.value.length),
      icon: 'play',
      tone: 'green',
    },
    {
      title: t('teacherClasses.averageRecording'),
      classCode: t('teacherClasses.recordedProgress'),
      deadline: t('teacherClasses.videoCount', { count: totalVideos }),
      progressLabel: videoProgress.value.length
        ? t('teacherClasses.learnerProgressCount', { count: videoProgress.value.length })
        : t('teacherClasses.noVideoProgress'),
      progress: progressAverage,
      icon: 'status',
      tone: 'orange',
    },
    {
      title: t('teacherClasses.activeLearners'),
      classCode: t('teacherClasses.rosterContext'),
      deadline: t('teacherClasses.totalActive', { count: analytics.value.overview.totalActiveStudents }),
      progressLabel: t('teacherClasses.selectedRosterCount', { count: enrolments.value.length }),
      progress: ratioPercent(enrolments.value.length, analytics.value.overview.totalActiveStudents),
      icon: 'students',
      tone: 'purple',
    },
  ]
})

const quickActions = computed(() => [
  {
    title: t('teacherClasses.createNewClass'),
    icon: 'classes',
    tone: 'purple',
    action: createNewClass,
  },
  {
    title: t('teacherClasses.scheduleClass'),
    icon: 'calendar',
    tone: 'orange',
    action: openComposer,
  },
  {
    title: t('teacherClasses.createContent'),
    icon: 'content',
    tone: 'green',
    action: openContent,
  },
  {
    title: t('teacherClasses.sendFeedback'),
    icon: 'help',
    tone: 'blue',
    action: openFeedback,
  },
])

function platformToneFor(platform: MeetingPlatformValue): string {
  return platform === 'GOOGLE_MEET' ? 'green' : 'blue'
}

function liveClassTone(index: number): string {
  if (index === 0) return 'purple'
  if (index === 1) return 'orange'
  return 'blue'
}

function rotatingClassArt(index: number): string {
  return ['purple', 'orange', 'festival'][index % 3]
}

function toInputDateTime(value: string): string {
  const date = new Date(value)
  const offset = date.getTimezoneOffset()
  const local = new Date(date.getTime() - offset * 60_000)
  return local.toISOString().slice(0, 16)
}

function toApiDateTime(value: string): string {
  // The live-class API stores a LocalDateTime wall-clock value. Converting
  // datetime-local to UTC before removing the zone shifts the selected time.
  return value.length === 16 ? `${value}:00` : value
}

function meetingCodeFromLink(link?: string | null): string {
  if (!link) return t('teacherClasses.externalLink')

  return (
    link
      .replace(/^https?:\/\//, '')
      .replace(/^www\./, '')
      .split('/')
      .slice(-2)
      .join('-')
      .replace(/[^a-zA-Z0-9-]/g, '')
      .slice(0, 18) || t('teacherClasses.externalLink')
  )
}

function formatClassDate(date: Date): string {
  return new Intl.DateTimeFormat(getDisplayLocale(locale.value), {
    month: 'short',
    day: 'numeric',
    weekday: 'short',
  }).format(date)
}

function formatStatus(status: string): string {
  const keys: Record<string, string> = {
    SCHEDULED: 'statusScheduled',
    IN_PROGRESS: 'statusInProgress',
    COMPLETED: 'statusCompleted',
    CANCELLED: 'statusCancelled',
    ENROLLED: 'statusEnrolled',
    ATTENDED: 'statusAttended',
  }
  const key = keys[status.toUpperCase()]
  if (key) return t(`teacherClasses.${key}`)
  return status
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/(^|\s)\w/g, (match) => match.toUpperCase())
}

function normalizeMeetingPlatform(value: string | null | undefined): MeetingPlatformValue {
  const normalized = (value ?? '')
    .trim()
    .replace(/[\s-]+/g, '_')
    .toUpperCase()

  if (normalized === 'ZOOM') return 'ZOOM'
  if (normalized === 'GOOGLE_MEET' || normalized === 'MEET') return 'GOOGLE_MEET'
  if (normalized === 'MICROSOFT_TEAMS' || normalized === 'MS_TEAMS' || normalized === 'TEAMS') {
    return 'MICROSOFT_TEAMS'
  }
  return 'OTHER'
}

function formatMeetingPlatform(value: string | null | undefined): string {
  return formatStatus(normalizeMeetingPlatform(value))
}

function applyClassToForm(item: (typeof liveClasses.value)[number]): void {
  classForm.title = item.title
  classForm.description = item.description ?? ''
  classForm.lessonId = item.lesson?.lessonId ?? 0
  classForm.packageId = item.coursePackage?.packageId ?? 0
  selectedLessonId.value = item.lesson?.lessonId ?? null
  classForm.startTime = toInputDateTime(item.startTime)
  classForm.endTime = toInputDateTime(item.endTime)
  classForm.timeZone = item.timeZone || 'Asia/Singapore'
  classForm.meetingLink = item.meetingLink ?? ''
  classForm.meetingPlatform = normalizeMeetingPlatform(item.meetingPlatform)
  classForm.status = item.liveClassStatus || 'SCHEDULED'
  classForm.maxParticipants = item.maxParticipants ?? 20
}

function resetComposer(): void {
  const tomorrow = new Date()
  tomorrow.setDate(tomorrow.getDate() + 1)
  tomorrow.setHours(17, 0, 0, 0)
  const end = new Date(tomorrow.getTime() + 45 * 60_000)

  selectedClassId.value = null
  classForm.title = ''
  classForm.description = ''
  classForm.lessonId = selectedLiveClass.value?.lesson?.lessonId ?? primaryClass.value?.lesson?.lessonId ?? 0
  classForm.packageId =
    selectedLiveClass.value?.coursePackage?.packageId ?? primaryClass.value?.coursePackage?.packageId ?? 0
  selectedLessonId.value = classForm.lessonId || null
  classForm.startTime = toInputDateTime(tomorrow.toISOString())
  classForm.endTime = toInputDateTime(end.toISOString())
  classForm.timeZone = 'Asia/Singapore'
  classForm.meetingLink = ''
  classForm.meetingPlatform = 'ZOOM'
  classForm.status = 'SCHEDULED'
  classForm.maxParticipants = 20
}

function openComposer(): void {
  showComposer.value = true
}

function toggleComposer(): void {
  showComposer.value = !showComposer.value
}

function createNewClass(): void {
  resetComposer()
  openComposer()
}

function openContent(): void {
  void router.push('/teacher/content')
}

function openFeedback(): void {
  void router.push('/teacher/feedback')
}

function openStudents(): void {
  void router.push('/teacher/students')
}

async function selectClass(classId: number): Promise<void> {
  actionError.value = null
  actionSuccess.value = null
  selectedClassId.value = classId
  const item = liveClasses.value.find((entry) => entry.id === classId)
  if (item) {
    selectedLessonId.value = item.lesson?.lessonId ?? null
    applyClassToForm(item)
    showComposer.value = true
  }
  await loadDependentResources(classId, item?.lesson?.lessonId ?? null)
}

async function submitClass(): Promise<void> {
  if (submittingCreate.value || submittingUpdate.value || submittingCancel.value) return
  actionError.value = null
  actionSuccess.value = null
  if (classesReadOnly.value) {
    actionError.value = t('teacherClasses.previewMutation')
    return
  }
  if (!classForm.lessonId && !classForm.packageId) {
    actionError.value = t('teacherClasses.selectContext')
    return
  }

  const payload = {
    title: classForm.title,
    description: classForm.description,
    lessonId: classForm.lessonId || undefined,
    packageId: classForm.packageId || undefined,
    startTime: toApiDateTime(classForm.startTime),
    endTime: toApiDateTime(classForm.endTime),
    timeZone: classForm.timeZone,
    meetingLink: classForm.meetingLink,
    meetingPlatform: classForm.meetingPlatform,
    status: classForm.status,
    maxParticipants: classForm.maxParticipants,
  }

  if (selectedLiveClass.value) {
    submittingUpdate.value = true
    try {
      await teacherApi.updateLiveClass(selectedLiveClass.value.id, payload)
      await liveClassesResource.reload()
      actionSuccess.value = t('teacherClasses.updated')
    } catch (reason) {
      actionError.value = resolveApiError(reason, t('teacherClasses.updateError'))
    } finally {
      submittingUpdate.value = false
    }
    return
  }

  submittingCreate.value = true
  try {
    await teacherApi.createLiveClass(payload)
    await liveClassesResource.reload()
    showComposer.value = false
    actionSuccess.value = t('teacherClasses.created')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherClasses.createError'))
  } finally {
    submittingCreate.value = false
  }
}

async function cancelSelectedClass(): Promise<void> {
  if (submittingCancel.value || submittingCreate.value || submittingUpdate.value) return
  if (!selectedLiveClass.value) return
  if (classesReadOnly.value) {
    actionSuccess.value = null
    actionError.value = t('teacherClasses.previewCancel')
    return
  }

  actionError.value = null
  actionSuccess.value = null
  submittingCancel.value = true
  try {
    await teacherApi.cancelLiveClass(selectedLiveClass.value.id)
    await liveClassesResource.reload()
    resetComposer()
    actionSuccess.value = t('teacherClasses.cancelledSuccess')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherClasses.cancelError'))
  } finally {
    submittingCancel.value = false
  }
}

async function loadEnrolments(classId: number, announceSuccess = true): Promise<void> {
  if (announceSuccess) {
    actionError.value = null
    actionSuccess.value = null
  }
  if (previewFallbackEnabled.value) {
    enrolmentsResource.data.value = teacherEnrolments
    enrolmentsResource.usingFallback.value = true
    if (announceSuccess) actionSuccess.value = t('teacherClasses.rosterPreview')
    return
  }

  try {
    enrolmentsResource.data.value = await teacherApi.enrolments(classId)
    if (announceSuccess) actionSuccess.value = t('teacherClasses.rosterLoaded')
  } catch (reason) {
    actionSuccess.value = null
    actionError.value = resolveApiError(reason, t('teacherClasses.rosterError'))
  }
}

async function loadRecordedResources(lessonId: number | null): Promise<void> {
  if (!lessonId) {
    recordedResource.data.value = recordedResource.usingFallback.value ? teacherRecordedVideos : []
    progressResource.data.value = progressResource.usingFallback.value ? teacherVideoProgress : []
    return
  }

  selectedLessonId.value = lessonId

  if (previewFallbackEnabled.value) {
    recordedResource.data.value = teacherRecordedVideos
    progressResource.data.value = teacherVideoProgress
    recordedResource.usingFallback.value = true
    progressResource.usingFallback.value = true
    return
  }

  try {
    recordedResource.data.value = await teacherApi.recordedVideos(lessonId)
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherClasses.recordingsError'))
  }

  try {
    progressResource.data.value = await teacherApi.lessonVideoProgress(lessonId)
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherClasses.progressError'))
  }
}

async function loadDependentResources(classId: number, lessonId: number | null): Promise<void> {
  await loadEnrolments(classId, false)
  await loadRecordedResources(lessonId)
}

function openMeeting(link?: string | null): void {
  if (link) {
    if (openSafeHttpUrl(link)) {
      actionError.value = null
      actionSuccess.value = t('teacherClasses.openingMeeting')
    } else {
      actionSuccess.value = null
      actionError.value = t('teacherClasses.unsafeMeeting')
    }
    return
  }
  actionSuccess.value = null
  actionError.value = t('teacherClasses.noMeeting')
}

function reloadAll(): void {
  void liveClassesResource.reload()
  void analyticsResource.reload()
  void lessonsResource.reload()
  void packagesResource.reload()
  if (selectedClassId.value) {
    void loadDependentResources(
      selectedClassId.value,
      selectedLiveClass.value?.lesson?.lessonId ?? selectedLessonId.value,
    )
  }
}

resetComposer()

watch(
  () => classForm.lessonId,
  (lessonId) => {
    if (!showComposer.value) return

    selectedLessonId.value = lessonId || null

    if (selectedClassId.value && selectedLiveClass.value?.lesson?.lessonId === (lessonId || null)) {
      return
    }

    void loadRecordedResources(lessonId || null)
  },
)

watch(
  liveClasses,
  (items) => {
    if (!items.length) {
      selectedClassId.value = null
      selectedLessonId.value = null
      return
    }

    const activeClass = items.find((item) => item.id === selectedClassId.value) ?? items[0]
    if (!activeClass) {
      return
    }

    selectedClassId.value = activeClass.id
    selectedLessonId.value = activeClass.lesson?.lessonId ?? null
    applyClassToForm(activeClass)
    void loadDependentResources(activeClass.id, activeClass.lesson?.lessonId ?? null)
  },
  { immediate: true },
)
</script>

<style scoped>
.teacher-classes-page {
  position: relative;
  padding-bottom: 6px;
}

.teacher-classes-page :deep(.page-header) {
  align-items: center;
}

.teacher-classes-page :deep(.page-header-actions) {
  align-items: stretch;
}

.teacher-sample-link,
.teacher-guide-button,
.teacher-outline-button {
  border: 1px solid rgba(223, 227, 243, 0.96);
  background: rgba(255, 255, 255, 0.98);
  color: var(--text);
  border-radius: 16px;
  padding: 12px 18px;
  font-weight: 700;
}

.teacher-sample-link {
  color: var(--primary);
  box-shadow: 0 12px 24px rgba(118, 104, 204, 0.1);
}

.teacher-classes-upcoming,
.teacher-classes-library-card,
.teacher-classes-attendance-card,
.teacher-classes-reminders-card,
.teacher-classes-homework-card,
.teacher-classes-actions-card,
.teacher-classes-editor-card,
.teacher-classes-roster-card {
  border-radius: 28px;
}

.teacher-classes-section-head {
  margin-bottom: 16px;
}

.teacher-classes-header-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.teacher-classes-head-link {
  padding-inline: 4px;
}

.teacher-classes-date-pill {
  display: inline-flex;
  align-items: center;
  padding: 11px 16px;
  border-radius: 16px;
  border: 1px solid rgba(228, 232, 245, 0.96);
  background: rgba(255, 255, 255, 0.98);
  color: #37445f;
  font-weight: 700;
}

.teacher-classes-live-list {
  display: grid;
  gap: 12px;
}

.teacher-classes-live-item {
  display: grid;
  grid-template-columns: 118px minmax(0, 1.2fr) minmax(180px, 0.72fr) auto;
  gap: 18px;
  align-items: center;
  padding: 16px 18px;
  border-radius: 22px;
  border: 1px solid rgba(236, 239, 247, 0.96);
  background: rgba(255, 255, 255, 0.98);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.88);
}

.teacher-classes-live-item.is-purple {
  box-shadow:
    inset 0 0 0 1px rgba(134, 104, 255, 0.1),
    0 10px 20px rgba(124, 102, 222, 0.05);
}

.teacher-classes-live-time {
  display: grid;
  gap: 6px;
  align-content: center;
}

.teacher-classes-live-time small {
  color: var(--primary);
  font-weight: 700;
}

.teacher-classes-live-time strong {
  font-family: 'Fredoka', sans-serif;
  font-size: clamp(1.7rem, 1.9vw, 2.15rem);
  color: #6950ff;
  line-height: 1;
}

.teacher-classes-live-media {
  display: grid;
  grid-template-columns: 104px minmax(0, 1fr);
  gap: 14px;
  align-items: center;
}

.teacher-classes-live-thumb,
.teacher-classes-library-thumb {
  min-height: 60px;
  border-radius: 18px;
  display: grid;
  place-items: center;
  color: white;
  font-weight: 800;
  text-transform: lowercase;
  letter-spacing: 0.02em;
  overflow: hidden;
}

.teacher-classes-live-thumb.is-purple,
.teacher-classes-library-thumb.is-purple {
  background: linear-gradient(135deg, #c4b3ff, #8f6dff 52%, #ffd25a);
}

.teacher-classes-live-thumb.is-orange,
.teacher-classes-library-thumb.is-orange {
  background: linear-gradient(135deg, #ffd786, #ff9b6b 48%, #8b6cff);
}

.teacher-classes-live-thumb.is-festival,
.teacher-classes-library-thumb.is-festival {
  background: linear-gradient(135deg, #ffae4d, #ff6e57 44%, #7d55ff);
}

.teacher-classes-live-copy,
.teacher-classes-library-copy,
.teacher-classes-reminder-copy,
.teacher-classes-homework-copy,
.teacher-classes-roster-copy {
  display: grid;
  gap: 5px;
}

.teacher-classes-live-copy strong,
.teacher-classes-library-copy strong,
.teacher-classes-roster-copy strong {
  font-size: 1.04rem;
}

.teacher-classes-live-copy span,
.teacher-classes-library-copy span,
.teacher-classes-roster-copy span,
.teacher-classes-live-platform span,
.teacher-classes-reminder-copy span,
.teacher-classes-homework-copy span {
  color: #606d8a;
}

.teacher-classes-live-copy small,
.teacher-classes-roster-copy small {
  color: #7a86a2;
}

.teacher-classes-chip {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(131, 101, 255, 0.12);
  color: var(--primary);
  font-size: 0.76rem;
  font-weight: 700;
}

.teacher-classes-live-platform {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  align-items: center;
}

.teacher-classes-platform-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: white;
  font-weight: 800;
}

.teacher-classes-platform-icon.is-blue {
  background: linear-gradient(180deg, #4da0ff, #2a82f4);
}

.teacher-classes-platform-icon.is-green {
  background: linear-gradient(180deg, #7ac65b, #2ea34a);
}

.teacher-classes-live-actions {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
}

.teacher-classes-live-actions-main {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.teacher-classes-live-more {
  min-width: 72px;
  min-height: 38px;
  padding: 0 12px;
  border: 0;
  border-radius: 14px;
  background: rgba(126, 96, 255, 0.08);
  color: #7f6dff;
  font-size: 0.8rem;
  font-weight: 800;
  line-height: 1;
}

.teacher-classes-support-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-top: 16px;
  padding: 14px 18px;
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(243, 239, 255, 0.98), rgba(255, 249, 238, 0.98));
  color: var(--primary);
  font-weight: 600;
}

.teacher-classes-support-icon {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  border: 1px solid rgba(131, 101, 255, 0.22);
  font-size: 0.82rem;
}

.teacher-classes-support-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
  color: #6a58c9;
}

.teacher-guide-button {
  color: var(--primary);
  background: rgba(255, 255, 255, 0.98);
  padding: 10px 16px;
}

.teacher-classes-composer-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.14fr) 372px;
  gap: 18px;
}

.teacher-classes-form {
  display: grid;
  gap: 14px;
}

.teacher-classes-form-footer {
  display: grid;
  gap: 12px;
  margin-top: 6px;
}

.teacher-classes-form-note {
  padding: 14px 16px;
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(255, 248, 225, 0.98), rgba(255, 255, 255, 0.98));
  color: #695a2d;
  line-height: 1.5;
}

.teacher-classes-roster-count {
  color: var(--primary);
  font-weight: 700;
}

.teacher-classes-roster-list {
  display: grid;
  gap: 12px;
}

.teacher-classes-roster-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid rgba(235, 239, 246, 0.96);
}

.teacher-classes-roster-item:last-child {
  border-bottom: 0;
}

.teacher-classes-empty {
  padding: 18px 0 4px;
  color: var(--text-soft);
  line-height: 1.55;
}

.teacher-classes-dashboard-grid {
  display: grid;
  grid-template-columns: minmax(320px, 1fr) minmax(280px, 0.92fr) minmax(320px, 1.02fr);
  gap: 18px;
}

.teacher-classes-center-column,
.teacher-classes-right-column {
  display: grid;
  gap: 18px;
  align-content: start;
}

.teacher-classes-library-list,
.teacher-classes-reminder-list,
.teacher-classes-homework-list {
  display: grid;
  gap: 12px;
}

.teacher-classes-library-item,
.teacher-classes-reminder-item,
.teacher-classes-homework-item {
  display: grid;
  gap: 12px;
  align-items: center;
}

.teacher-classes-library-item {
  grid-template-columns: 72px minmax(0, 1fr) auto;
  padding: 8px 0;
  border-bottom: 1px solid rgba(236, 239, 247, 0.96);
}

.teacher-classes-library-item:last-child {
  border-bottom: 0;
}

.teacher-classes-library-side {
  display: grid;
  justify-items: end;
  gap: 8px;
  color: #5f6b85;
  font-size: 0.88rem;
}

.teacher-play-button {
  width: 38px;
  height: 38px;
  border: 1px solid rgba(223, 227, 243, 0.96);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.98);
  color: var(--primary);
  font-size: 0.8rem;
  font-weight: 700;
}

.teacher-footer-cta {
  margin-top: 12px;
  width: 100%;
  min-height: 44px;
  border: 0;
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(245, 240, 255, 0.98), rgba(255, 248, 241, 0.98));
  color: var(--primary);
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.teacher-classes-title-with-note {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.teacher-classes-head-note {
  color: #8a95ae;
  font-size: 0.82rem;
  font-weight: 600;
}

.teacher-classes-attendance-wrap {
  display: grid;
  grid-template-columns: 182px minmax(0, 1fr);
  gap: 20px;
  align-items: center;
}

.teacher-classes-attendance-ring {
  width: 148px;
  height: 148px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  margin: 0 auto;
  background:
    radial-gradient(circle closest-side, white 66%, transparent 67% 100%),
    conic-gradient(#66c64a 0 calc(var(--attendance) * 1%), rgba(228, 232, 243, 0.92) 0);
}

.teacher-classes-attendance-inner {
  display: grid;
  justify-items: center;
  gap: 4px;
  text-align: center;
}

.teacher-classes-attendance-inner small,
.teacher-classes-attendance-inner span {
  color: var(--text-soft);
}

.teacher-classes-attendance-inner strong {
  font-family: 'Fredoka', sans-serif;
  font-size: 2.4rem;
  line-height: 1;
}

.teacher-classes-attendance-legend {
  display: grid;
  gap: 14px;
}

.teacher-classes-attendance-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  color: #56627d;
}

.teacher-classes-attendance-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.teacher-classes-attendance-dot.is-green {
  background: #6acb4c;
}

.teacher-classes-attendance-dot.is-yellow {
  background: #ffb92f;
}

.teacher-classes-attendance-dot.is-red {
  background: #ff6969;
}

.teacher-classes-reminder-item {
  grid-template-columns: auto minmax(0, 1fr) auto;
  padding: 10px 0;
  border-bottom: 1px solid rgba(236, 239, 247, 0.96);
}

.teacher-classes-reminder-item:last-child {
  border-bottom: 0;
}

.teacher-classes-reminder-icon,
.teacher-classes-homework-icon,
.teacher-classes-action-icon {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  font-weight: 800;
}

.teacher-classes-reminder-icon.is-green,
.teacher-classes-homework-icon.is-green,
.teacher-classes-action-icon.is-green {
  background: rgba(99, 197, 95, 0.12);
  color: #4ca648;
}

.teacher-classes-reminder-icon.is-orange,
.teacher-classes-homework-icon.is-orange,
.teacher-classes-action-icon.is-orange {
  background: rgba(255, 184, 79, 0.14);
  color: #e3911c;
}

.teacher-classes-reminder-icon.is-purple,
.teacher-classes-homework-icon.is-purple,
.teacher-classes-action-icon.is-purple {
  background: rgba(131, 101, 255, 0.12);
  color: var(--primary);
}

.teacher-classes-action-icon.is-blue {
  background: rgba(62, 148, 255, 0.12);
  color: #2f86f6;
}

.teacher-classes-reminder-tag,
.teacher-classes-deadline {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 0.76rem;
  font-weight: 700;
}

.teacher-classes-reminder-tag.is-green {
  background: rgba(112, 211, 106, 0.12);
  color: #4ca648;
}

.teacher-classes-reminder-tag.is-orange,
.teacher-classes-deadline {
  background: rgba(255, 183, 75, 0.15);
  color: #d88b1e;
}

.teacher-classes-homework-item {
  grid-template-columns: auto minmax(0, 1fr) auto;
  padding: 10px 0;
  border-bottom: 1px solid rgba(236, 239, 247, 0.96);
}

.teacher-classes-homework-item:last-child {
  border-bottom: 0;
}

.teacher-classes-homework-progress {
  position: relative;
  height: 8px;
  margin-top: 8px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(223, 227, 241, 0.82);
}

.teacher-classes-homework-progress span {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: inherit;
  background: linear-gradient(90deg, #7c58ff, #9f82ff);
}

.teacher-classes-homework-percent {
  color: #616d89;
  font-size: 0.94rem;
}

.teacher-classes-actions-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.teacher-classes-action-button {
  min-height: 84px;
  border-radius: 20px;
  border: 1px solid rgba(230, 233, 245, 0.96);
  background: rgba(255, 255, 255, 0.98);
  display: grid;
  justify-items: center;
  align-content: center;
  gap: 10px;
  color: var(--text);
  font-weight: 700;
  text-align: center;
}

.teacher-classes-editor-card :deep(.textarea) {
  min-height: 112px;
}

.teacher-classes-attendance-card {
  --attendance: v-bind(attendanceRate);
}

@media (max-width: 1460px) {
  .teacher-classes-live-item {
    grid-template-columns: minmax(96px, 120px) minmax(0, 1fr);
  }

  .teacher-classes-live-platform,
  .teacher-classes-live-actions {
    grid-column: 2;
  }

  .teacher-classes-dashboard-grid {
    grid-template-columns: 1fr 1fr;
  }

  .teacher-classes-library-card {
    grid-column: 1 / -1;
  }
}

@media (max-width: 1180px) {
  .teacher-classes-composer-grid,
  .teacher-classes-dashboard-grid,
  .teacher-classes-attendance-wrap {
    grid-template-columns: 1fr;
  }

  .teacher-classes-support-strip {
    flex-direction: column;
    align-items: flex-start;
  }

  .teacher-classes-live-item {
    grid-template-columns: minmax(90px, 110px) minmax(0, 1fr);
    align-items: start;
  }

  .teacher-classes-live-platform,
  .teacher-classes-live-actions {
    grid-column: 1 / -1;
  }

  .teacher-classes-live-actions {
    grid-template-columns: 1fr;
  }

  .teacher-classes-live-actions-main {
    justify-content: flex-start;
  }
}

@media (max-width: 760px) {
  .teacher-classes-live-item,
  .teacher-classes-live-media,
  .teacher-classes-library-item,
  .teacher-classes-homework-item,
  .teacher-classes-roster-item {
    grid-template-columns: 1fr;
  }

  .teacher-classes-live-actions-main,
  .teacher-classes-header-actions {
    justify-content: flex-start;
  }

  .teacher-classes-actions-grid,
  .form-grid.two {
    grid-template-columns: 1fr;
  }

  .teacher-classes-upcoming,
  .teacher-classes-library-card,
  .teacher-classes-attendance-card,
  .teacher-classes-reminders-card,
  .teacher-classes-homework-card,
  .teacher-classes-actions-card,
  .teacher-classes-editor-card,
  .teacher-classes-roster-card {
    border-radius: 22px;
  }

  .teacher-classes-live-item {
    padding: 14px;
  }

  .teacher-classes-date-pill {
    width: 100%;
    justify-content: center;
  }

  .teacher-classes-support-actions {
    width: 100%;
    justify-content: space-between;
  }

  .teacher-classes-live-more,
  .teacher-classes-live-actions-main > button {
    width: 100%;
  }
}
</style>
