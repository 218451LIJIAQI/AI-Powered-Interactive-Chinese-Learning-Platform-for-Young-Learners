<template>
  <div class="page-stack teacher-classes-page">
    <PageHeaderBlock :title="t('teacherClasses.title')" :description="t('teacherClasses.description')" icon="classes">
      <template #actions>
        <button
          class="btn btn-primary"
          type="button"
          aria-controls="teacher-classes-composer"
          :aria-expanded="showComposer"
          :disabled="teacherApprovalLocked"
          @click="toggleComposer"
        >
          <IconGlyph :name="teacherApprovalLocked ? 'safe' : 'calendar'" :size="18" aria-hidden="true" />
          {{ composerToggleLabel }}
        </button>
      </template>
    </PageHeaderBlock>

    <DataNotice
      :using-fallback="usingFallback"
      :error="actionError"
      :success="actionSuccess"
      :fallback-title="t('teacherClasses.previewTitle')"
      :fallback-message="t('teacherClasses.previewMessage')"
      :error-title="t('teacherClasses.attentionTitle')"
      :detail="classesNoticeDetail"
    />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="panel teacher-classes-upcoming" aria-labelledby="teacher-classes-upcoming-title">
        <div class="section-header">
          <h2 id="teacher-classes-upcoming-title" class="section-title">{{ t('teacherClasses.upcoming') }}</h2>
          <span class="tag teacher-classes-date">
            <IconGlyph name="calendar" :size="14" aria-hidden="true" />
            {{ currentDateLabel }}
          </span>
        </div>

        <TeacherApprovalLockPanel
          v-if="teacherApprovalLocked"
          :title="t('teacherClasses.verificationPending')"
          :message="t('teacherClasses.verificationRequired')"
        />
        <StatePanel
          v-else-if="!liveClassCards.length"
          compact
          icon="classes"
          :title="t('teacherClasses.noLiveClasses')"
          :message="t('teacherClasses.noLiveClassesHint')"
        >
          <template #actions>
            <button class="btn btn-secondary" type="button" @click="createNewClass">
              {{ t('teacherClasses.scheduleClass') }}
            </button>
          </template>
        </StatePanel>
        <ul v-else class="teacher-live-list">
          <li
            v-for="item in liveClassCards"
            :key="item.id"
            class="teacher-live-item"
            :class="{ 'is-selected': item.selected }"
          >
            <p class="teacher-live-when">
              <span class="teacher-live-day">{{ item.dayLabel }}</span>
              <strong class="teacher-live-time">{{ item.timeLabel }}</strong>
            </p>

            <div class="teacher-live-main">
              <span class="teacher-class-tile" :class="`is-${item.art}`" aria-hidden="true">
                <IconGlyph name="classes" :size="22" />
              </span>
              <div class="teacher-live-copy">
                <div class="teacher-live-title-row">
                  <h3 :id="`teacher-live-title-${item.id}`" class="teacher-live-title">{{ item.title }}</h3>
                  <StatusPill :label="item.classCode" :tone="item.statusTone" />
                </div>
                <p class="teacher-live-topic">{{ item.topic }}</p>
                <ul class="teacher-live-meta">
                  <li>
                    <IconGlyph name="students" :size="16" aria-hidden="true" />
                    {{ item.studentLabel }}
                  </li>
                  <li>
                    <IconGlyph name="live" :size="16" aria-hidden="true" />
                    {{ item.platform }} · {{ item.platformMeta }}
                  </li>
                </ul>
              </div>
            </div>

            <div class="teacher-live-actions" role="group" :aria-labelledby="`teacher-live-title-${item.id}`">
              <button class="btn btn-secondary btn-sm" type="button" @click="openMeeting(item.meetingLink)">
                <IconGlyph name="live" :size="16" aria-hidden="true" />
                {{ t('teacherClasses.openMeeting') }}
              </button>
              <button
                class="btn btn-ghost btn-sm"
                type="button"
                :aria-pressed="item.selected"
                @click="selectClass(item.id)"
              >
                <IconGlyph name="pen" :size="16" aria-hidden="true" />
                {{ item.editLabel }}
              </button>
              <button class="btn btn-ghost btn-sm" type="button" @click="loadEnrolments(item.id)">
                <IconGlyph name="students" :size="16" aria-hidden="true" />
                {{ t('teacherClasses.viewRoster') }}
              </button>
            </div>
          </li>
        </ul>

        <DataNotice :info="t('teacherClasses.externalSupport')" :info-title="t('teacherClasses.needHelp')">
          <template #actions>
            <button
              class="btn btn-secondary btn-sm"
              type="button"
              :disabled="teacherApprovalLocked"
              @click="createNewClass"
            >
              {{ t('teacherClasses.openScheduler') }}
            </button>
          </template>
        </DataNotice>
      </section>

      <section class="teacher-classes-detail" :class="{ 'is-composing': showComposer }">
        <article
          v-if="showComposer"
          id="teacher-classes-composer"
          class="panel teacher-classes-composer"
          aria-labelledby="teacher-classes-composer-title"
        >
          <div class="card-header">
            <div>
              <h2 id="teacher-classes-composer-title" class="card-title">{{ composerTitle }}</h2>
              <p class="card-subtitle">{{ t('teacherClasses.requiredNote') }}</p>
            </div>
            <button v-if="selectedLiveClass" class="btn btn-ghost btn-sm" type="button" @click="resetComposer">
              <IconGlyph name="plus" :size="14" />
              {{ t('teacherClasses.newClass') }}
            </button>
          </div>

          <form class="teacher-classes-form" @submit.prevent="submitClass">
            <fieldset class="form-section">
              <legend class="form-section-title">{{ t('teacherClasses.composerDetails') }}</legend>
              <div class="field">
                <label class="field-label" for="teacher-class-title">
                  {{ t('teacherClasses.classTitle') }}<span class="required-mark" aria-hidden="true">*</span>
                </label>
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
                <label class="field-label" for="teacher-class-description">
                  {{ t('teacherClasses.topicDescription') }}
                </label>
                <textarea
                  id="teacher-class-description"
                  v-model="classForm.description"
                  class="textarea teacher-classes-textarea"
                  maxlength="1000"
                  :disabled="classComposerDisabled"
                />
              </div>

              <div class="form-grid two">
                <div class="field">
                  <label class="field-label" for="teacher-class-lesson">{{ t('teacherClasses.lesson') }}</label>
                  <select
                    id="teacher-class-lesson"
                    v-model.number="classForm.lessonId"
                    class="select"
                    aria-describedby="teacher-class-context-hint"
                    :disabled="classComposerDisabled"
                  >
                    <option :value="0">{{ t('teacherClasses.noLesson') }}</option>
                    <option v-for="lesson in lessonOptions" :key="lesson.id" :value="lesson.id">
                      {{ lesson.title }}
                    </option>
                  </select>
                </div>
                <div class="field">
                  <label class="field-label" for="teacher-class-package">{{ t('teacherClasses.package') }}</label>
                  <select
                    id="teacher-class-package"
                    v-model.number="classForm.packageId"
                    class="select"
                    aria-describedby="teacher-class-context-hint"
                    :disabled="classComposerDisabled"
                  >
                    <option :value="0">{{ t('teacherClasses.noPackage') }}</option>
                    <option v-for="coursePackage in packageOptions" :key="coursePackage.id" :value="coursePackage.id">
                      {{ coursePackage.title }}
                    </option>
                  </select>
                </div>
              </div>
              <p id="teacher-class-context-hint" class="field-hint">{{ t('teacherClasses.formNote') }}</p>
            </fieldset>

            <fieldset class="form-section">
              <legend class="form-section-title">{{ t('teacherClasses.composerSchedule') }}</legend>
              <div class="form-grid two">
                <div class="field">
                  <label class="field-label" for="teacher-class-start-time">
                    {{ t('teacherClasses.startTime') }}<span class="required-mark" aria-hidden="true">*</span>
                  </label>
                  <input
                    id="teacher-class-start-time"
                    v-model="classForm.startTime"
                    class="input"
                    type="datetime-local"
                    required
                    aria-describedby="teacher-class-time-hint"
                    :disabled="classComposerDisabled"
                  />
                </div>
                <div class="field">
                  <label class="field-label" for="teacher-class-end-time">
                    {{ t('teacherClasses.endTime') }}<span class="required-mark" aria-hidden="true">*</span>
                  </label>
                  <input
                    id="teacher-class-end-time"
                    v-model="classForm.endTime"
                    class="input"
                    type="datetime-local"
                    required
                    aria-describedby="teacher-class-time-hint"
                    :disabled="classComposerDisabled"
                  />
                </div>
              </div>
              <p id="teacher-class-time-hint" class="field-hint">{{ t('teacherClasses.timeZoneHint') }}</p>
            </fieldset>

            <fieldset class="form-section">
              <legend class="form-section-title">{{ t('teacherClasses.composerMeeting') }}</legend>
              <div class="form-grid two">
                <div class="field">
                  <label class="field-label" for="teacher-class-platform">
                    {{ t('teacherClasses.meetingPlatform') }}
                  </label>
                  <select
                    id="teacher-class-platform"
                    v-model="classForm.meetingPlatform"
                    class="select"
                    :disabled="classComposerDisabled"
                  >
                    <option v-for="platform in MEETING_PLATFORMS" :key="platform" :value="platform">
                      {{ statusLabel('platform', platform) }}
                    </option>
                  </select>
                </div>
                <div class="field">
                  <label class="field-label" for="teacher-class-max-participants">
                    {{ t('teacherClasses.maxParticipants') }}
                  </label>
                  <input
                    id="teacher-class-max-participants"
                    v-model.number="classForm.maxParticipants"
                    class="input"
                    type="number"
                    min="1"
                    inputmode="numeric"
                    aria-describedby="teacher-class-capacity-hint"
                    :disabled="classComposerDisabled"
                  />
                  <p id="teacher-class-capacity-hint" class="field-hint">{{ t('teacherClasses.capacityHint') }}</p>
                </div>
              </div>
              <div class="field">
                <label class="field-label" for="teacher-class-meeting-link">
                  {{ t('teacherClasses.meetingLink') }}<span class="required-mark" aria-hidden="true">*</span>
                </label>
                <input
                  id="teacher-class-meeting-link"
                  v-model="classForm.meetingLink"
                  class="input"
                  type="url"
                  inputmode="url"
                  maxlength="500"
                  required
                  placeholder="https://"
                  aria-describedby="teacher-class-link-hint"
                  :disabled="classComposerDisabled"
                />
                <p id="teacher-class-link-hint" class="field-hint">{{ t('teacherClasses.meetingLinkHint') }}</p>
              </div>
            </fieldset>

            <p v-if="actionError" class="inline-feedback is-error teacher-classes-form-error" role="alert">
              <IconGlyph name="alert" :size="16" aria-hidden="true" />
              {{ actionError }}
            </p>

            <div
              v-if="confirmingCancel && selectedLiveClass"
              class="teacher-classes-confirm"
              role="group"
              aria-labelledby="teacher-classes-confirm-title"
            >
              <p id="teacher-classes-confirm-title" class="teacher-classes-confirm-copy">
                <IconGlyph name="alert" :size="18" aria-hidden="true" />
                {{ t('teacherClasses.confirmCancelTitle', { title: selectedLiveClass.title }) }}
              </p>
              <div class="teacher-classes-confirm-actions">
                <button class="btn btn-ghost btn-sm" type="button" @click="confirmingCancel = false">
                  {{ t('teacherClasses.keepClass') }}
                </button>
                <button
                  class="btn btn-danger btn-sm"
                  type="button"
                  :aria-busy="submittingCancel"
                  :disabled="classesReadOnly || submittingCancel"
                  @click="cancelSelectedClass"
                >
                  {{ cancelClassButtonLabel }}
                </button>
              </div>
            </div>

            <div class="form-actions">
              <button
                v-if="selectedLiveClass && !confirmingCancel && !selectedClassTerminal"
                class="btn btn-danger-soft"
                type="button"
                :disabled="classesReadOnly || submittingCancel"
                @click="confirmingCancel = true"
              >
                {{ t('teacherClasses.cancelClass') }}
              </button>
              <button
                class="btn btn-primary"
                type="submit"
                :aria-busy="submittingCreate || submittingUpdate"
                :disabled="classComposerDisabled"
              >
                {{ submitClassButtonLabel }}
              </button>
            </div>
          </form>
        </article>

        <div class="teacher-classes-detail-side">
          <article class="panel teacher-classes-card" aria-labelledby="teacher-classes-roster-title">
            <div class="card-header">
              <div class="teacher-classes-card-heading">
                <h2 id="teacher-classes-roster-title" class="card-title">{{ t('teacherClasses.selectedRoster') }}</h2>
                <p v-if="rosterClass" class="card-subtitle">{{ rosterClass.title }}</p>
              </div>
              <span class="chip">{{ t('teacherClasses.learners', { count: enrolments.length }) }}</span>
            </div>

            <ul v-if="enrolments.length" class="list-divided teacher-roster-list">
              <li v-for="student in enrolments" :key="student.id" class="teacher-roster-item">
                <PortraitAvatar :size="40" rounded="soft" :name="student.studentName" aria-hidden="true" />
                <div class="teacher-roster-copy">
                  <strong>{{ student.studentName }}</strong>
                  <span class="type-caption">{{ formatDate(student.enrolledAt) }}</span>
                </div>
                <StatusPill
                  :label="enrolmentStatusLabel(student.enrolmentStatus)"
                  :tone="enrolmentTone(student.enrolmentStatus)"
                />
              </li>
            </ul>
            <StatePanel
              v-else
              compact
              icon="students"
              :title="t('teacherClasses.rosterEmptyTitle')"
              :message="t('teacherClasses.emptyRoster')"
            />
          </article>

          <article class="panel teacher-classes-card" aria-labelledby="teacher-classes-attendance-title">
            <div class="card-header">
              <div class="teacher-classes-card-heading">
                <h2 id="teacher-classes-attendance-title" class="card-title">{{ t('teacherClasses.attendance') }}</h2>
                <p class="card-subtitle">{{ t('teacherClasses.selectedClass') }}</p>
              </div>
              <button
                class="btn btn-link"
                type="button"
                :disabled="!attendanceClass"
                @click="attendanceClass && loadEnrolments(attendanceClass.id)"
              >
                {{ t('teacherClasses.viewDetails') }}
              </button>
            </div>

            <div class="teacher-classes-attendance">
              <div
                class="teacher-classes-attendance-ring"
                role="img"
                :aria-label="`${t('teacherClasses.checkedIn')}: ${attendanceRate}% (${attendedCount} / ${totalRosterCount})`"
              >
                <div class="teacher-classes-attendance-inner" aria-hidden="true">
                  <small>{{ t('teacherClasses.checkedIn') }}</small>
                  <strong>{{ attendanceRate }}%</strong>
                  <span>{{ attendedCount }} / {{ totalRosterCount }}</span>
                </div>
              </div>

              <ul class="teacher-classes-attendance-legend">
                <li v-for="item in attendanceLegend" :key="item.label" class="teacher-classes-attendance-row">
                  <span class="teacher-classes-attendance-dot" :class="`is-${item.tone}`" aria-hidden="true" />
                  <span>{{ item.label }}</span>
                  <strong>{{ item.count }} ({{ item.percent }}%)</strong>
                </li>
              </ul>
            </div>
          </article>
        </div>
      </section>

      <section class="teacher-classes-grid">
        <article class="panel teacher-classes-card" aria-labelledby="teacher-classes-library-title">
          <div class="card-header">
            <h2 id="teacher-classes-library-title" class="card-title">{{ t('teacherClasses.library') }}</h2>
            <button class="btn btn-link" type="button" @click="openContent">{{ t('teacherClasses.viewAll') }}</button>
          </div>

          <ul v-if="recordingCards.length" class="list-divided teacher-classes-list">
            <li v-for="item in recordingCards" :key="item.id" class="teacher-classes-row">
              <span class="teacher-class-tile" :class="`is-${item.art}`" aria-hidden="true">
                <IconGlyph name="play" :size="20" />
              </span>
              <div class="teacher-classes-row-copy">
                <strong :id="`teacher-recording-${item.id}`">{{ item.title }}</strong>
                <span class="type-caption">{{ item.meta }}</span>
              </div>
              <div class="teacher-classes-row-side">
                <span class="tag">{{ item.lengthLabel }}</span>
                <button
                  class="btn btn-ghost btn-sm"
                  type="button"
                  :aria-describedby="`teacher-recording-${item.id}`"
                  @click="openContent"
                >
                  {{ t('teacherClasses.open') }}
                </button>
              </div>
            </li>
          </ul>
          <StatePanel
            v-else
            compact
            icon="play"
            :title="t('teacherClasses.noRecordingsTitle')"
            :message="t('teacherClasses.noRecordingsHint')"
          >
            <template #actions>
              <button class="btn btn-secondary btn-sm" type="button" @click="openContent">
                {{ t('teacherClasses.browseRecordings') }}
              </button>
            </template>
          </StatePanel>
        </article>

        <article class="panel teacher-classes-card" aria-labelledby="teacher-classes-followups-title">
          <div class="card-header">
            <h2 id="teacher-classes-followups-title" class="card-title">{{ t('teacherClasses.followups') }}</h2>
            <button class="btn btn-link" type="button" @click="openStudents">
              {{ t('teacherClasses.openStudents') }}
            </button>
          </div>

          <ul class="list-divided teacher-classes-list">
            <li v-for="item in reminderCards" :key="item.title" class="teacher-classes-row is-top">
              <span class="teacher-class-tile" :class="`is-${item.tone}`" aria-hidden="true">
                <IconGlyph :name="item.icon" :size="20" />
              </span>
              <div class="teacher-classes-row-copy">
                <strong>{{ item.title }}</strong>
                <span class="type-caption">{{ item.copy }}</span>
              </div>
              <StatusPill :label="item.badge" :tone="item.pillTone" />
            </li>
          </ul>
        </article>

        <article class="panel teacher-classes-card" aria-labelledby="teacher-classes-learning-title">
          <div class="card-header">
            <h2 id="teacher-classes-learning-title" class="card-title">
              {{ t('teacherClasses.learningFollowups') }}
            </h2>
            <button class="btn btn-link" type="button" @click="openContent">
              {{ t('teacherClasses.openContent') }}
            </button>
          </div>

          <ul class="list-divided teacher-classes-list">
            <li v-for="item in assignmentCards" :key="item.title" class="teacher-classes-row is-top">
              <span class="teacher-class-tile" :class="`is-${item.tone}`" aria-hidden="true">
                <IconGlyph :name="item.icon" :size="20" />
              </span>
              <div class="teacher-classes-row-copy">
                <strong>{{ item.title }}</strong>
                <div class="teacher-classes-tags">
                  <span class="chip">{{ item.classCode }}</span>
                  <span class="tag">{{ item.deadline }}</span>
                </div>
                <span class="type-caption">{{ item.progressLabel }}</span>
                <ProgressBar class="is-sm" :progress="item.progress" :label="item.title" />
              </div>
              <strong class="teacher-classes-percent">{{ item.progress }}%</strong>
            </li>
          </ul>
        </article>

        <article class="panel teacher-classes-card" aria-labelledby="teacher-classes-quick-title">
          <h2 id="teacher-classes-quick-title" class="card-title">{{ t('teacherClasses.quickActions') }}</h2>
          <div class="teacher-classes-quick-grid">
            <button
              v-for="item in quickActions"
              :key="item.title"
              class="btn btn-secondary teacher-classes-quick"
              type="button"
              @click="item.action"
            >
              <span class="teacher-class-tile" :class="`is-${item.tone}`" aria-hidden="true">
                <IconGlyph :name="item.icon" :size="20" />
              </span>
              <span>{{ item.title }}</span>
            </button>
          </div>
        </article>
      </section>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { teacherApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import TeacherApprovalLockPanel from '@/components/teacher/TeacherApprovalLockPanel.vue'
import { appEnv } from '@/config/env'
import { useResource } from '@/composables/useResource'
import { useStatusLabels } from '@/composables/useStatusLabels'
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
import type {
  CoursePackageResponse,
  LessonResponse,
  LessonVideoProgressSummaryResponse,
  LiveClassEnrolmentResponse,
  RecordedLessonVideoResponse,
  TeacherAnalyticsResponse,
} from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { clampPercent, formatDate, getDisplayLocale, nonNegativeInteger, ratioPercent } from '@/utils/format'
import { effectiveLiveClassStatus, isTerminalLiveClass } from '@/utils/liveClassActions'
import { openSafeHttpUrl } from '@/utils/url'

type MeetingPlatformValue = TeacherLiveClassPayload['meetingPlatform']
const MEETING_PLATFORMS: readonly MeetingPlatformValue[] = ['ZOOM', 'GOOGLE_MEET', 'MICROSOFT_TEAMS', 'OTHER']
/** Malaysia time zone for new live classes (A5 #14). */
const DEFAULT_TIME_ZONE = 'Asia/Kuala_Lumpur'

const router = useRouter()
const { t, locale } = useI18n({
  messages: {
    en: { teacherClasses: teacherClassesMessages.en },
    ms: { teacherClasses: teacherClassesMessages.ms },
    'zh-CN': { teacherClasses: teacherClassesMessages['zh-CN'] },
  },
})
const auth = useAuthStore()
const { statusLabel } = useStatusLabels()
const { teacherApprovalLocked, teacherApprovalMessage, guardTeacherRequest } = useTeacherAccess()
const previewFallbackEnabled = computed(() => appEnv.useDemoData && auth.isPreviewSession)

// D13: live classes, lessons and packages are approved-teacher endpoints; a 403 switches the page to the locked state.
const liveClassesResource = useResource(
  () => (teacherApprovalLocked.value ? Promise.resolve([]) : guardTeacherRequest(() => teacherApi.liveClasses())),
  teacherLiveClasses,
)
const analyticsResource = useResource<TeacherAnalyticsResponse | null>(
  () => (teacherApprovalLocked.value ? Promise.resolve(null) : guardTeacherRequest(() => teacherApi.analytics())),
  teacherAnalytics,
)
const lessonsResource = useResource(
  () => (teacherApprovalLocked.value ? Promise.resolve([]) : guardTeacherRequest(() => teacherApi.lessons())),
  teacherLessons,
)
const packagesResource = useResource(
  () => (teacherApprovalLocked.value ? Promise.resolve([]) : guardTeacherRequest(() => teacherApi.packages())),
  teacherCoursePackages,
)
const selectedClassId = ref<number | null>(null)
const selectedLessonId = ref<number | null>(null)
// Written directly by loadEnrolments / loadRecordedResources; useResource only provides the preview fixtures.
const enrolmentsResource = useResource<LiveClassEnrolmentResponse[]>(
  () => Promise.resolve([]),
  teacherEnrolments,
  false,
)
const recordedResource = useResource<RecordedLessonVideoResponse[]>(
  () => Promise.resolve([]),
  teacherRecordedVideos,
  false,
)
const progressResource = useResource<LessonVideoProgressSummaryResponse[]>(
  () => Promise.resolve([]),
  teacherVideoProgress,
  false,
)

const liveClasses = computed(
  () => liveClassesResource.data.value ?? (liveClassesResource.usingFallback.value ? teacherLiveClasses : []),
)
const totalActiveStudents = computed(() => analyticsResource.data.value?.overview.totalActiveStudents ?? 0)
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
    packagesResource.loading.value,
)
// While locked, errors of the approved-teacher endpoints are replaced by the verification message.
const pageError = computed(
  () =>
    (teacherApprovalLocked.value
      ? null
      : liveClassesResource.error.value ||
        lessonsResource.error.value ||
        packagesResource.error.value ||
        analyticsResource.error.value) || null,
)
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
const classComposerDisabled = computed(
  () => classesReadOnly.value || selectedClassTerminal.value || submittingCreate.value || submittingUpdate.value,
)
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

  if (actionError.value) {
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
/** Cancelling a live class is a two-step action: the first click asks for confirmation inside the composer. */
const confirmingCancel = ref(false)
const submittingCreate = ref(false)
const submittingUpdate = ref(false)
const submittingCancel = ref(false)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
let enrolmentsRequest = 0
let recordingsRequest = 0

const classForm = reactive<TeacherLiveClassPayload>({
  title: '',
  description: '',
  lessonId: 0,
  packageId: 0,
  startTime: '',
  endTime: '',
  timeZone: DEFAULT_TIME_ZONE,
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

const focusClass = computed(() => liveClasses.value[0] ?? null)
const selectedClassTerminal = computed(() =>
  Boolean(selectedLiveClass.value && isTerminalLiveClass(selectedLiveClass.value)),
)
const selectedLiveClass = computed(() => liveClasses.value.find((item) => item.id === selectedClassId.value) ?? null)
/** Class whose roster is currently loaded (View Roster does not change the class being edited). */
const rosterClassId = ref<number | null>(null)
const rosterClass = computed(() => liveClasses.value.find((item) => item.id === rosterClassId.value) ?? null)
const attendanceClass = computed(() => selectedLiveClass.value ?? focusClass.value)

const liveClassCards = computed(() =>
  liveClasses.value.map((item, index) => {
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
      classCode: liveClassStatusLabel(effectiveLiveClassStatus(item)),
      statusTone: liveClassStatusTone(effectiveLiveClassStatus(item)),
      platform: platformLabel,
      platformMeta,
      meetingLink: item.meetingLink,
      selected: selectedClassId.value === item.id,
      editLabel: selectedClassId.value === item.id ? t('teacherClasses.editing') : t('teacherClasses.editClass'),
      art: rotatingClassArt(index),
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
const attendanceRate = computed(() => ratioPercent(attendedCount.value, totalRosterCount.value))
const pendingCheckInTone = computed(() => (pendingCheckInCount.value > 0 ? 'orange' : 'green'))

const attendanceLegend = computed(() => {
  return [
    {
      label: t('teacherClasses.checkedIn'),
      count: attendedCount.value,
      percent: ratioPercent(attendedCount.value, totalRosterCount.value),
      tone: 'green',
    },
    {
      label: t('teacherClasses.pending'),
      count: pendingCheckInCount.value,
      percent: ratioPercent(pendingCheckInCount.value, totalRosterCount.value),
      tone: 'yellow',
    },
    {
      label: t('teacherClasses.cancelled'),
      count: cancelledCount.value,
      percent: ratioPercent(cancelledCount.value, totalRosterCount.value),
      tone: 'red',
    },
  ]
})

const reminderCards = computed(() => {
  const activeClass = selectedLiveClass.value ?? focusClass.value

  return [
    {
      title: activeClass?.lesson?.lessonTitle
        ? t('teacherClasses.reviewLesson', { lesson: activeClass.lesson.lessonTitle })
        : t('teacherClasses.selectClassReview'),
      copy: activeClass
        ? `${activeClass.title} - ${formatDate(activeClass.startTime)}`
        : t('teacherClasses.classDetailsPending'),
      badge: activeClass?.liveClassStatus
        ? liveClassStatusLabel(effectiveLiveClassStatus(activeClass))
        : t('teacherClasses.noClass'),
      icon: 'status',
      tone: 'green',
      pillTone: 'success' as const,
    },
    {
      title: pendingCheckInCount.value > 0 ? t('teacherClasses.pendingFollowup') : t('teacherClasses.rosterClear'),
      copy: t('teacherClasses.pendingAttendance', { count: pendingCheckInCount.value }),
      badge: t('teacherClasses.rosterCount', { count: enrolments.value.length }),
      icon: 'alert',
      tone: pendingCheckInTone.value,
      pillTone: pendingCheckInCount.value > 0 ? ('warning' as const) : ('success' as const),
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
        ? liveClassStatusLabel(effectiveLiveClassStatus(selectedLiveClass.value))
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
      deadline: t('teacherClasses.totalActive', { count: totalActiveStudents.value }),
      progressLabel: t('teacherClasses.selectedRosterCount', { count: enrolments.value.length }),
      progress: ratioPercent(enrolments.value.length, totalActiveStudents.value),
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

// W4: every enum shown to teachers goes through the shared status labels (never raw ENROLLED / ZOOM values).
function liveClassStatusLabel(status: string | null | undefined): string {
  return statusLabel('liveClass', (status ?? '').trim().toUpperCase())
}

function liveClassStatusTone(status: string | null | undefined): 'info' | 'success' | 'neutral' | 'danger' {
  switch ((status ?? '').trim().toUpperCase()) {
    case 'ONGOING':
      return 'success'
    case 'COMPLETED':
      return 'neutral'
    case 'CANCELLED':
      return 'danger'
    default:
      return 'info'
  }
}

function enrolmentStatusLabel(status: string | null | undefined): string {
  return statusLabel('enrolment', (status ?? '').trim().toUpperCase())
}

function enrolmentTone(status: string | null | undefined): 'success' | 'warning' | 'danger' | 'info' {
  switch ((status ?? '').trim().toUpperCase()) {
    case 'ATTENDED':
      return 'success'
    case 'MISSED':
      return 'warning'
    case 'CANCELLED':
      return 'danger'
    default:
      return 'info'
  }
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
  return statusLabel('platform', normalizeMeetingPlatform(value))
}

function applyClassToForm(item: (typeof liveClasses.value)[number]): void {
  classForm.title = item.title
  classForm.description = item.description ?? ''
  classForm.lessonId = item.lesson?.lessonId ?? 0
  classForm.packageId = item.coursePackage?.packageId ?? 0
  selectedLessonId.value = item.lesson?.lessonId ?? null
  classForm.startTime = toInputDateTime(item.startTime)
  classForm.endTime = toInputDateTime(item.endTime)
  classForm.timeZone = item.timeZone || DEFAULT_TIME_ZONE
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
  // Read the selected class before clearing the selection (selectedLiveClass becomes null afterwards).
  const previousClass = selectedLiveClass.value
  confirmingCancel.value = false

  selectedClassId.value = null
  classForm.title = ''
  classForm.description = ''
  classForm.lessonId = previousClass?.lesson?.lessonId ?? focusClass.value?.lesson?.lessonId ?? 0
  classForm.packageId = previousClass?.coursePackage?.packageId ?? focusClass.value?.coursePackage?.packageId ?? 0
  selectedLessonId.value = classForm.lessonId || null
  classForm.startTime = toInputDateTime(tomorrow.toISOString())
  classForm.endTime = toInputDateTime(end.toISOString())
  classForm.timeZone = DEFAULT_TIME_ZONE
  classForm.meetingLink = ''
  classForm.meetingPlatform = 'ZOOM'
  classForm.status = 'SCHEDULED'
  classForm.maxParticipants = 20
}

/** Brings the composer into view once it renders (it sits below the class list). */
async function revealComposer(): Promise<void> {
  await nextTick()
  const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  document
    .getElementById('teacher-classes-composer')
    ?.scrollIntoView?.({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'start' })
}

function openComposer(): void {
  showComposer.value = true
  void revealComposer()
}

function toggleComposer(): void {
  if (showComposer.value) {
    showComposer.value = false
  } else {
    createNewClass()
  }
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
  confirmingCancel.value = false
  selectedClassId.value = classId
  const item = liveClasses.value.find((entry) => entry.id === classId)
  if (item) {
    applyClassToForm(item)
    openComposer()
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
    confirmingCancel.value = false
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
    confirmingCancel.value = false
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
    rosterClassId.value = classId
    if (announceSuccess) actionSuccess.value = t('teacherClasses.rosterPreview')
    return
  }

  // Only the roster of the most recently requested class may land (clicks and watchers can overlap).
  const request = ++enrolmentsRequest
  try {
    const items = await teacherApi.enrolments(classId)
    if (request !== enrolmentsRequest) return
    enrolmentsResource.data.value = items
    rosterClassId.value = classId
    if (announceSuccess) actionSuccess.value = t('teacherClasses.rosterLoaded')
  } catch (reason) {
    if (request !== enrolmentsRequest) return
    actionSuccess.value = null
    actionError.value = resolveApiError(reason, t('teacherClasses.rosterError'))
  }
}

async function loadRecordedResources(lessonId: number | null): Promise<void> {
  const request = ++recordingsRequest
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
    const videos = await teacherApi.recordedVideos(lessonId)
    if (request === recordingsRequest) recordedResource.data.value = videos
  } catch (reason) {
    if (request === recordingsRequest) actionError.value = resolveApiError(reason, t('teacherClasses.recordingsError'))
  }

  try {
    const progressRows = await teacherApi.lessonVideoProgress(lessonId)
    if (request === recordingsRequest) progressResource.data.value = progressRows
  } catch (reason) {
    if (request === recordingsRequest) actionError.value = resolveApiError(reason, t('teacherClasses.progressError'))
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
    applyClassToForm(activeClass)
    void loadDependentResources(activeClass.id, activeClass.lesson?.lessonId ?? null)
  },
  { immediate: true },
)
</script>

<style scoped>
/* Shared pieces --------------------------------------------------------- */
.teacher-class-tile {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}

.teacher-class-tile.is-orange {
  background: var(--color-accent-100);
  color: var(--color-accent-800);
}

.teacher-class-tile.is-green {
  background: var(--color-success-soft);
  color: var(--color-success-text);
}

.teacher-class-tile.is-blue,
.teacher-class-tile.is-festival {
  background: var(--color-info-soft);
  color: var(--color-info-text);
}

.teacher-classes-card {
  display: grid;
  align-content: start;
  gap: var(--section-gap);
  min-width: 0;
}

.teacher-classes-card-heading {
  display: grid;
  gap: var(--space-0-5);
  min-width: 0;
}

/* Upcoming live classes ------------------------------------------------- */
.teacher-classes-upcoming {
  display: grid;
  gap: var(--section-gap);
}

.teacher-classes-date {
  gap: var(--space-1-5);
}

.teacher-live-list {
  display: grid;
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.teacher-live-item {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr) auto;
  gap: var(--space-4) var(--space-5);
  align-items: center;
  padding: var(--space-4) var(--space-5);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
  transition: border-color var(--duration-fast) var(--ease-standard);
}

.teacher-live-item.is-selected {
  border-color: var(--border-interactive);
  background: var(--surface-hover);
}

.teacher-live-when {
  display: grid;
  gap: var(--space-1);
  margin: 0;
}

.teacher-live-day {
  color: var(--text-brand);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
}

.teacher-live-time {
  color: var(--color-primary-600);
  font-family: var(--font-display);
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
  white-space: nowrap;
}

.teacher-live-main {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  min-width: 0;
}

.teacher-live-copy {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.teacher-live-title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.teacher-live-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-snug);
  overflow-wrap: anywhere;
}

.teacher-live-topic {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-md);
}

.teacher-live-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1) var(--space-4);
  margin: 0;
  padding: 0;
  list-style: none;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.teacher-live-meta li {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1-5);
  min-width: 0;
  overflow-wrap: anywhere;
}

.teacher-live-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2);
}

/* Composer, roster and attendance ------------------------------------- */
.teacher-classes-detail {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--grid-gap);
  align-items: start;
}

.teacher-classes-detail.is-composing {
  grid-template-columns: minmax(0, 1.6fr) minmax(300px, 1fr);
}

.teacher-classes-detail-side {
  display: contents;
}

.teacher-classes-detail.is-composing .teacher-classes-detail-side {
  display: grid;
  gap: var(--grid-gap);
  align-content: start;
}

.teacher-classes-composer {
  scroll-margin-top: calc(var(--topbar-height) + var(--space-4));
  display: grid;
  gap: var(--section-gap);
  min-width: 0;
}

.teacher-classes-form {
  display: grid;
  gap: var(--space-5);
}

.teacher-classes-textarea {
  min-height: 96px;
}

.teacher-classes-form-error {
  margin: 0;
}

.teacher-classes-confirm {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-danger-border);
  border-radius: var(--radius-lg);
  background: var(--color-danger-soft);
}

.teacher-classes-confirm-copy {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  margin: 0;
  color: var(--color-danger-text);
  font-weight: var(--font-weight-semibold);
}

.teacher-classes-confirm-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-2);
}

.teacher-roster-list,
.teacher-classes-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.teacher-roster-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: var(--space-3);
  align-items: center;
}

.teacher-roster-copy,
.teacher-classes-row-copy {
  display: grid;
  gap: var(--space-0-5);
  min-width: 0;
}

.teacher-roster-copy strong,
.teacher-classes-row-copy strong {
  overflow-wrap: anywhere;
}

.teacher-classes-attendance {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-6);
  align-items: center;
}

.teacher-classes-attendance-ring {
  --attendance: v-bind(attendanceRate);
  display: grid;
  place-items: center;
  width: 136px;
  height: 136px;
  border-radius: var(--radius-round);
  background:
    radial-gradient(circle closest-side, var(--surface-card) 78%, transparent 79% 100%),
    conic-gradient(var(--color-success) calc(var(--attendance) * 1%), var(--color-ink-100) 0);
}

.teacher-classes-attendance-inner {
  display: grid;
  justify-items: center;
  gap: var(--space-0-5);
  text-align: center;
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
}

.teacher-classes-attendance-inner strong {
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
}

.teacher-classes-attendance-legend {
  display: grid;
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.teacher-classes-attendance-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: var(--space-2-5);
  align-items: center;
  color: var(--text-secondary);
  font-size: var(--font-size-md);
}

.teacher-classes-attendance-row strong {
  color: var(--text-primary);
}

.teacher-classes-attendance-dot {
  width: 10px;
  height: 10px;
  border-radius: var(--radius-round);
  background: var(--color-success);
}

.teacher-classes-attendance-dot.is-yellow {
  background: var(--color-warning);
}

.teacher-classes-attendance-dot.is-red {
  background: var(--color-danger);
}

/* Library, follow-ups and quick actions -------------------------------- */
.teacher-classes-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--grid-gap);
  align-items: start;
}

.teacher-classes-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: var(--space-3);
  align-items: center;
}

.teacher-classes-row.is-top {
  align-items: start;
}

.teacher-classes-row-side {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2);
}

.teacher-classes-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1-5);
  margin: var(--space-1) 0;
}

.teacher-classes-row-copy .progress-bar {
  margin-top: var(--space-1-5);
}

.teacher-classes-percent {
  color: var(--text-secondary);
  font-size: var(--font-size-md);
}

.teacher-classes-quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.teacher-classes-quick.btn {
  flex-direction: column;
  height: auto;
  min-height: 96px;
  padding: var(--space-3);
  border-radius: var(--radius-lg);
  color: var(--text-primary);
  white-space: normal;
  text-align: center;
}

@media (max-width: 1200px) {
  .teacher-live-item {
    grid-template-columns: minmax(96px, max-content) minmax(0, 1fr);
  }

  .teacher-live-actions {
    grid-column: 2;
    justify-content: flex-start;
  }

  .teacher-classes-detail.is-composing {
    grid-template-columns: minmax(0, 1fr);
  }

  .teacher-classes-detail.is-composing .teacher-classes-detail-side {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .teacher-classes-detail,
  .teacher-classes-detail.is-composing .teacher-classes-detail-side,
  .teacher-classes-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .teacher-live-item {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--space-3);
    padding: var(--space-4);
  }

  .teacher-live-when {
    display: flex;
    align-items: baseline;
    gap: var(--space-2);
  }

  .teacher-live-actions {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
    grid-column: auto;
  }

  .teacher-live-actions > .btn:first-child {
    grid-column: 1 / -1;
  }

  .teacher-classes-attendance {
    grid-template-columns: minmax(0, 1fr);
    justify-items: center;
  }

  .teacher-classes-attendance-legend {
    width: 100%;
  }

  .teacher-roster-item {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .teacher-roster-item > .status-pill {
    grid-column: 2;
  }

  .teacher-classes-row,
  .teacher-classes-row.is-top {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .teacher-classes-row-side,
  .teacher-classes-percent,
  .teacher-classes-row > .status-pill {
    grid-column: 2;
    justify-content: flex-start;
  }
}
</style>
