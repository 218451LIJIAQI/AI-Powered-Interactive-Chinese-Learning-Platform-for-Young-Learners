<template>
  <div class="page-stack student-classes-page">
    <DataNotice :using-fallback="liveResource.usingFallback.value" :error="noticeError" :success="actionSuccess" />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="student-classes-layout">
        <div class="student-classes-main">
          <article class="panel student-live-hero">
            <div class="student-section-heading student-section-heading--live">
              <span class="student-live-dot">{{ t('studentClasses.liveNow') }}</span>
              <strong>{{ t('studentClasses.upcoming') }}</strong>
            </div>

            <template v-if="upcomingClass">
              <div class="student-live-hero-grid">
                <div class="student-live-date">
                  <span>{{ upcomingMonth }}</span>
                  <strong>{{ upcomingDay }}</strong>
                  <small>{{ upcomingWeekday }}</small>
                </div>

                <div class="student-live-copy">
                  <h2>{{ upcomingClass.title }}</h2>
                  <p>{{ upcomingClassDescription }}</p>

                  <div class="student-live-meta">
                    <span>{{ classTimeRange }}</span>
                    <span>{{ upcomingClassDurationLabel }}</span>
                    <span>{{ upcomingMeetingPlatformLabel }}</span>
                  </div>

                  <div class="student-live-teacher">
                    <span class="student-mini-avatar">{{ teacherInitials }}</span>
                    <span>{{ primaryTeacherName }}</span>
                  </div>

                  <div class="student-live-actions">
                    <button class="btn btn-secondary" type="button" @click="openLinkedLesson">
                      {{ t('studentClasses.viewContent') }}
                    </button>
                  </div>
                </div>

                <div class="student-live-scene">
                  <PandaIllustration
                    class="student-live-panda"
                    :size="164"
                    outfit="#7d56ff"
                    accent="#f6d056"
                    pose="wave"
                  />
                </div>
              </div>

              <div class="student-live-footer">
                <button
                  class="btn btn-accent student-live-join"
                  type="button"
                  :disabled="liveReadOnly || classBusy || waitingForJoinWindow"
                  @click="join(upcomingClass.id)"
                >
                  {{ upcomingClassActionLabel }}
                </button>
                <button
                  v-if="canCancelUpcomingEnrolment"
                  class="btn btn-secondary"
                  type="button"
                  :disabled="liveReadOnly || classBusy"
                  @click="cancelEnrolment(upcomingClass.id)"
                >
                  {{ t('studentClasses.cancelEnrolment') }}
                </button>
                <button class="student-live-setup" type="button" @click="openProfile">
                  {{ t('studentClasses.openProfile') }}
                </button>
              </div>
            </template>

            <StatePanel
              v-else
              icon="classes"
              :title="t('studentClasses.noUpcoming')"
              :message="t('studentClasses.noUpcomingHint')"
            />
          </article>

          <div class="student-classes-bottom">
            <article class="panel student-teacher-card">
              <div class="student-section-heading">
                <strong>{{ t('studentClasses.yourTeacher') }}</strong>
              </div>

              <template v-if="upcomingClass?.teacher">
                <div class="student-teacher-profile">
                  <PortraitAvatar :size="108" :kind="teacherPortraitKind" />
                  <div class="stack u-gap-6">
                    <div class="row">
                      <strong>{{ primaryTeacherName }}</strong>
                      <span class="student-teacher-badge">{{ t('studentClasses.linkedTeacher') }}</span>
                    </div>
                    <p>{{ t('studentClasses.teacherHint') }}</p>
                  </div>
                </div>
              </template>
              <StatePanel
                v-else
                icon="students"
                :title="t('studentClasses.noTeacher')"
                :message="t('studentClasses.noTeacherHint')"
              />

              <button class="btn btn-secondary btn-block" type="button" @click="openProfile">
                {{ t('studentClasses.viewFeedback') }}
              </button>
            </article>

            <article class="panel student-library-card">
              <div class="row-between u-mb-14">
                <div class="student-section-heading">
                  <strong>{{ t('studentClasses.library') }}</strong>
                </div>
                <button class="student-link-button" type="button" @click="openLinkedLesson">
                  {{ t('studentClasses.openLearn') }}
                </button>
              </div>

              <div v-if="recordedLessonRows.length" class="student-library-list">
                <article v-for="video in recordedLessonRows" :key="video.id" class="student-library-item">
                  <div class="student-library-copy">
                    <strong>{{ video.title }}</strong>
                    <span class="muted">{{ video.context }}</span>
                    <small>{{ video.meta }}</small>
                  </div>
                  <button class="student-library-watch" type="button" @click="openRecordedLesson(video)">
                    {{ t('studentClasses.watch') }}
                  </button>
                </article>
              </div>
              <StatePanel
                v-else
                icon="learn"
                :title="t('studentClasses.noRecordings')"
                :message="t('studentClasses.noRecordingsHint')"
              />
            </article>
          </div>
        </div>

        <aside class="student-classes-side">
          <article class="panel student-weekly-card">
            <div class="row-between u-mb-10">
              <div class="student-section-heading">
                <strong>{{ t('studentClasses.weekly') }}</strong>
              </div>
              <button class="student-link-button" type="button" @click="reloadAll">
                {{ t('studentClasses.refresh') }}
              </button>
            </div>

            <div v-if="weeklySchedule.length" class="student-schedule-list">
              <article
                v-for="item in weeklySchedule"
                :key="item.id"
                class="student-schedule-item"
                :class="{ 'is-live': item.canJoin }"
              >
                <div class="student-schedule-date">
                  <span>{{ item.weekdayShort }}</span>
                  <strong>{{ item.day }}</strong>
                </div>

                <div class="student-schedule-copy">
                  <strong>{{ item.title }}</strong>
                  <span>{{ item.timeLabel }}</span>
                  <small>{{ item.teacherName }}</small>
                </div>

                <div class="student-schedule-state">
                  <span v-if="item.canJoin" class="student-schedule-live">{{ t('studentClasses.liveNow') }}</span>
                  <span v-else>{{ item.statusLabel }}</span>
                </div>
              </article>
            </div>
            <StatePanel
              v-else
              icon="classes"
              :title="t('studentClasses.noClasses')"
              :message="t('studentClasses.noClassesHint')"
            />
          </article>

          <article class="panel student-watch-again-card">
            <div class="row-between u-mb-12">
              <div class="student-section-heading">
                <strong>{{ t('studentClasses.accessNotes') }}</strong>
              </div>
              <button class="student-link-button" type="button" @click="openLinkedLesson">
                {{ t('studentClasses.openLearn') }}
              </button>
            </div>

            <div class="student-watch-again-item">
              <div class="student-watch-copy">
                <strong>{{ watchAgainTitle }}</strong>
                <span>{{ watchAgainSubtitle }}</span>
                <small>{{ watchAgainMeta }}</small>
              </div>
            </div>
          </article>

          <article class="panel student-reminder-card">
            <div class="row-between u-mb-10">
              <div class="student-section-heading">
                <strong>{{ t('studentClasses.reminders') }}</strong>
              </div>
            </div>

            <div v-if="reminders.length" class="student-reminder-list">
              <article v-for="reminder in reminders" :key="reminder.title" class="student-reminder-item">
                <div class="student-reminder-copy">
                  <strong>{{ reminder.title }}</strong>
                  <span class="muted">{{ reminder.copy }}</span>
                </div>
                <span class="student-reminder-time">{{ reminder.time }}</span>
              </article>
            </div>
            <StatePanel
              v-else
              icon="bell"
              :title="t('studentClasses.noReminders')"
              :message="t('studentClasses.noRemindersHint')"
            />
          </article>
        </aside>
      </section>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { publicApi, studentApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import { useResource } from '@/composables/useResource'
import { publicCourseDetails, studentLiveClasses, teacherCoursePackages } from '@/demo/data'
import { studentClassesMessages } from '@/plugins/studentClassesMessages'
import type { CoursePackageResponse, LiveClassResponse } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { openSafeHttpUrl } from '@/utils/url'

const { locale, t } = useI18n({
  messages: {
    en: { studentClasses: studentClassesMessages.en },
    ms: { studentClasses: studentClassesMessages.ms },
    zh: { studentClasses: studentClassesMessages.zh },
  },
})

const dateLocale = computed(() => ({ en: 'en-US', ms: 'ms-MY', zh: 'zh-CN' })[locale.value] ?? 'en-US')

const router = useRouter()

const liveResource = useResource(() => studentApi.liveClasses(), studentLiveClasses)

const liveClasses = computed(
  () => liveResource.data.value ?? (liveResource.usingFallback.value ? studentLiveClasses : []),
)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
const classBusy = ref(false)
const pageLoading = computed(() => liveResource.loading.value)
const pageError = computed(() => liveResource.error.value)
const noticeError = computed(() => actionError.value || liveResource.error.value)
const liveReadOnly = computed(() => liveResource.usingFallback.value)

const upcomingClass = computed(() => liveClasses.value.find((item) => item.canJoin) ?? liveClasses.value[0] ?? null)
const canCancelUpcomingEnrolment = computed(() => {
  const liveClass = upcomingClass.value
  if (!liveClass?.enrolled || !['SCHEDULED', 'RESCHEDULED'].includes(liveClass.liveClassStatus)) {
    return false
  }

  return Boolean(liveClass.startTime) && new Date(liveClass.startTime).getTime() > Date.now()
})
const waitingForJoinWindow = computed(() => canCancelUpcomingEnrolment.value && !upcomingClass.value?.canJoin)
const primaryTeacherName = computed(
  () => upcomingClass.value?.teacher?.displayName || t('studentClasses.teacherUnavailable'),
)
const upcomingClassDescription = computed(() => upcomingClass.value?.description || t('studentClasses.noDescription'))
const upcomingClassDurationLabel = computed(() =>
  t('studentClasses.minutes', { count: upcomingClass.value?.durationMinutes ?? 45 }),
)
const upcomingClassActionLabel = computed(() => {
  if (classBusy.value) return t('studentClasses.updating')
  if (upcomingClass.value?.canJoin) return t('studentClasses.join')
  if (upcomingClass.value?.canEnrol) return t('studentClasses.enrolFirst')
  if (waitingForJoinWindow.value) return t('studentClasses.startsLater')
  return t('studentClasses.locked')
})
const teacherPortraitKind = computed<'female' | 'male' | 'hijab' | 'student'>(() => {
  const name = primaryTeacherName.value.toLowerCase()
  if (name.includes('siti')) return 'hijab'
  if (name.includes('chen')) return 'male'
  if (!upcomingClass.value?.teacher?.displayName) return 'student'
  return 'female'
})

const teacherInitials = computed(
  () =>
    primaryTeacherName.value
      .split(' ')
      .filter(Boolean)
      .map((part) => part[0])
      .join('')
      .slice(0, 2)
      .toUpperCase() || '?',
)

const classDate = computed(() => new Date(upcomingClass.value?.startTime ?? new Date().toISOString()))
const upcomingMonth = computed(() =>
  new Intl.DateTimeFormat(dateLocale.value, { month: 'short' }).format(classDate.value).toUpperCase(),
)
const upcomingDay = computed(() =>
  new Intl.DateTimeFormat(dateLocale.value, { day: 'numeric' }).format(classDate.value),
)
const upcomingWeekday = computed(() =>
  new Intl.DateTimeFormat(dateLocale.value, { weekday: 'short' }).format(classDate.value).toUpperCase(),
)
const classTimeRange = computed(() => {
  const formatter = new Intl.DateTimeFormat(dateLocale.value, {
    hour: 'numeric',
    minute: '2-digit',
  })

  if (!upcomingClass.value?.startTime || !upcomingClass.value?.endTime) {
    return t('studentClasses.scheduleUnavailable')
  }

  return `${formatter.format(new Date(upcomingClass.value.startTime))} - ${formatter.format(new Date(upcomingClass.value.endTime))}`
})

const weeklySchedule = computed(() =>
  [...liveClasses.value]
    .sort((left, right) => new Date(left.startTime).getTime() - new Date(right.startTime).getTime())
    .slice(0, 4)
    .map((item) => {
      const date = new Date(item.startTime)
      return {
        id: item.id,
        title: item.title,
        teacherName: item.teacher?.displayName || t('studentClasses.teacherUnavailable'),
        weekdayShort: new Intl.DateTimeFormat(dateLocale.value, { weekday: 'short' }).format(date).toUpperCase(),
        day: new Intl.DateTimeFormat(dateLocale.value, { day: 'numeric' }).format(date),
        timeLabel: new Intl.DateTimeFormat(dateLocale.value, { hour: 'numeric', minute: '2-digit' }).format(date),
        canJoin: Boolean(item.canJoin),
        statusLabel: formatStatus(item.liveClassStatus),
      }
    }),
)

const recordedLessonRows = computed(() =>
  (upcomingClass.value ? recordedRowsFromClass(upcomingClass.value) : []).slice(0, 4),
)

const watchAgainTitle = computed(() => {
  if (recordedLessonRows.value.length) {
    return recordedLessonRows.value[0].title
  }

  if (upcomingClass.value) {
    return t('studentClasses.accessFor', { title: upcomingClass.value.title })
  }

  return t('studentClasses.noRecordedAccess')
})

const watchAgainSubtitle = computed(() => {
  if (recordedLessonRows.value.length) {
    return t('studentClasses.recordingsLinked')
  }

  if (upcomingClass.value?.canJoin) {
    return t('studentClasses.joinFromPage')
  }

  return t('studentClasses.detailsLater')
})

const watchAgainMeta = computed(() => {
  if (upcomingClass.value) {
    return `${formatStatus(upcomingClass.value.liveClassStatus)} - ${upcomingMeetingPlatformLabel.value}`
  }

  return t('studentClasses.waitingSchedule')
})

const reminders = computed(() => {
  if (!upcomingClass.value) return []

  return [
    {
      title: upcomingClass.value.canJoin ? t('studentClasses.ready') : t('studentClasses.scheduled'),
      copy: upcomingClass.value.title,
      time: classTimeRange.value,
    },
    {
      title: upcomingClass.value.canEnrol ? t('studentClasses.enrolAvailable') : t('studentClasses.enrolRequired'),
      copy: t('studentClasses.status', { status: formatStatus(upcomingClass.value.liveClassStatus) }),
      time: upcomingMeetingPlatformLabel.value,
    },
  ]
})

const upcomingMeetingPlatformLabel = computed(() => formatStatus(upcomingClass.value?.meetingPlatform ?? 'OTHER'))

interface RecordedLessonRow {
  id: string
  lessonId: number | null
  packageId: number | null
  title: string
  context: string
  meta: string
}

function recordedRowsFromClass(item: LiveClassResponse): RecordedLessonRow[] {
  if (item.lesson?.lessonTitle) {
    return [
      {
        id: `lesson-${item.lesson.lessonId}`,
        lessonId: item.lesson.lessonId,
        packageId: null,
        title: item.lesson.lessonTitle,
        context: t('studentClasses.lessonLinked'),
        meta: `${formatStatus(item.lesson.learningType)} - ${formatStatus(item.lesson.accessType)}`,
      },
    ]
  }

  if (item.coursePackage?.packageTitle) {
    return [
      {
        id: `package-${item.coursePackage.packageId}`,
        lessonId: null,
        packageId: item.coursePackage.packageId,
        title: item.coursePackage.packageTitle,
        context: t('studentClasses.packageLinked'),
        meta: `${formatStatus(item.coursePackage.learningType)} - ${formatStatus(item.coursePackage.accessType)}`,
      },
    ]
  }

  return []
}

function packageCourseId(packageId: number): string {
  return `PACKAGE-${packageId}`
}

function fallbackLessonIdFromPackage(packageId: number): number | null {
  return (
    teacherCoursePackages
      .find((item: CoursePackageResponse) => item.id === packageId && item.contentStatus === 'PUBLISHED')
      ?.packageItems.find((item) => item.itemType === 'LESSON')?.itemId ?? null
  )
}

async function openPackageLinkedContent(packageId: number): Promise<void> {
  const courseId = packageCourseId(packageId)

  try {
    const detail = await publicApi.getCourseDetail(courseId)
    const lessonId = detail.packageItems.find((item) => item.type === 'LESSON')?.contentId ?? null

    if (lessonId) {
      await openStudentLearn(lessonId)
      return
    }

    await openCourseDetail(courseId)
  } catch (reason) {
    if (liveResource.usingFallback.value) {
      const fallbackLessonId = fallbackLessonIdFromPackage(packageId)
      if (fallbackLessonId) {
        await openStudentLearn(fallbackLessonId)
        return
      }

      if (publicCourseDetails[courseId]) {
        await openCourseDetail(courseId)
        return
      }
    }

    actionError.value = resolveApiError(reason, t('studentClasses.packageOpenError'))
  }
}

async function openRecordedLesson(video: RecordedLessonRow): Promise<void> {
  actionError.value = null
  actionSuccess.value = null

  if (video.lessonId) {
    await openStudentLearn(video.lessonId)
    return
  }

  if (video.packageId) {
    await openPackageLinkedContent(video.packageId)
    return
  }

  actionError.value = t('studentClasses.noRecordingLink')
}

async function openLinkedLesson(): Promise<void> {
  actionError.value = null
  actionSuccess.value = null
  const lessonId =
    upcomingClass.value?.lesson?.lessonId ?? recordedLessonRows.value.find((item) => item.lessonId)?.lessonId

  if (lessonId) {
    await openStudentLearn(lessonId)
    return
  }

  const packageId =
    upcomingClass.value?.coursePackage?.packageId ??
    recordedLessonRows.value.find((item) => item.packageId)?.packageId ??
    null

  if (packageId) {
    await openPackageLinkedContent(packageId)
    return
  }

  actionError.value = t('studentClasses.noClassLink')
}

async function enrol(id: number): Promise<void> {
  if (classBusy.value) return
  actionError.value = null
  actionSuccess.value = null
  if (liveReadOnly.value) {
    actionError.value = t('studentClasses.previewEnrol')
    return
  }

  classBusy.value = true
  try {
    await studentApi.enrolLiveClass(id)
    await liveResource.reload()
    actionSuccess.value = t('studentClasses.enrolled')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('studentClasses.enrolError'))
  } finally {
    classBusy.value = false
  }
}

async function cancelEnrolment(id: number): Promise<void> {
  if (classBusy.value) return
  actionError.value = null
  actionSuccess.value = null
  if (liveReadOnly.value) {
    actionError.value = t('studentClasses.previewCancel')
    return
  }

  classBusy.value = true
  try {
    await studentApi.cancelLiveClassEnrolment(id)
    await liveResource.reload()
    actionSuccess.value = t('studentClasses.cancelled')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('studentClasses.cancelError'))
  } finally {
    classBusy.value = false
  }
}

async function join(id: number): Promise<void> {
  if (classBusy.value) return
  const target = liveClasses.value.find((item) => item.id === id)
  actionError.value = null
  actionSuccess.value = null

  if (liveReadOnly.value) {
    actionError.value = t('studentClasses.previewJoin')
    return
  }

  if (target?.canJoin) {
    actionError.value = null
    try {
      const response = await studentApi.joinLiveClass(id)
      if (openSafeHttpUrl(response.meetingLink)) {
        actionSuccess.value = t('studentClasses.opening')
      } else {
        actionError.value = t('studentClasses.unsafeLink')
      }
    } catch (reason) {
      actionError.value = resolveApiError(reason, t('studentClasses.joinError'))
    }
    return
  }

  if (target?.canEnrol) {
    await enrol(id)
    return
  }

  actionError.value = t('studentClasses.lockedHint')
}

function formatStatus(status: string): string {
  const keys: Record<string, string> = {
    SCHEDULED: 'statusScheduled',
    ONGOING: 'statusOngoing',
    COMPLETED: 'statusCompleted',
    CANCELLED: 'statusCancelled',
    RESCHEDULED: 'statusRescheduled',
    LIVE: 'typeLive',
    RECORDED: 'typeRecorded',
    HYBRID: 'typeHybrid',
    FREE: 'accessFree',
    PAID: 'accessPaid',
    ZOOM: 'platformZoom',
    GOOGLE_MEET: 'platformMeet',
    MICROSOFT_TEAMS: 'platformTeams',
    OTHER: 'platformOther',
  }
  const key = keys[status.toUpperCase()]
  if (key) return t(`studentClasses.${key}`)
  return status
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/(^|\s)\w/g, (match) => match.toUpperCase())
}

function reloadAll(): void {
  void liveResource.reload()
}

function openProfile(): void {
  void router.push('/student/profile')
}

async function openStudentLearn(lessonId: number): Promise<void> {
  await router.push({ name: 'student-learn', query: { lessonId: String(lessonId) } })
}

async function openCourseDetail(courseId: string): Promise<void> {
  await router.push({ name: 'course-detail', params: { courseId } })
}
</script>

<style scoped>
.student-classes-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) 360px;
  gap: 18px;
}

.student-classes-main,
.student-classes-side {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.student-live-hero,
.student-teacher-card,
.student-library-card,
.student-weekly-card,
.student-watch-again-card,
.student-reminder-card {
  border-radius: 24px;
}

.student-live-hero-grid {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr) 220px;
  gap: 18px;
  align-items: center;
}

.student-live-date {
  display: grid;
  justify-items: center;
  padding: 18px;
  border-radius: 18px;
  background: rgba(247, 240, 255, 0.96);
}

.student-live-date span,
.student-live-copy p,
.student-live-meta span,
.student-live-teacher span,
.student-library-copy span,
.student-library-copy small,
.student-schedule-copy span,
.student-schedule-copy small,
.student-watch-copy span,
.student-watch-copy small,
.student-reminder-copy span,
.student-reminder-time {
  color: var(--text-soft);
}

.student-live-date strong,
.student-live-copy h2 {
  font-family: 'Fredoka', sans-serif;
}

.student-live-date strong {
  font-size: 2.1rem;
}

.student-live-copy {
  display: grid;
  gap: 12px;
}

.student-live-copy h2 {
  margin: 0;
  font-size: 2rem;
}

.student-live-copy p {
  margin: 0;
  line-height: 1.6;
}

.student-live-meta,
.student-live-teacher,
.student-live-actions,
.student-live-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.student-mini-avatar {
  width: 36px;
  height: 36px;
  display: inline-grid;
  place-items: center;
  border-radius: 50%;
  background: rgba(125, 88, 255, 0.14);
  color: #7658ff;
  font-weight: 800;
}

.student-live-scene {
  display: grid;
  place-items: center;
}

.student-live-footer {
  margin-top: 18px;
}

.student-live-join {
  min-width: 180px;
}

.student-live-setup {
  min-height: 44px;
  padding: 0 18px;
  border-radius: 14px;
  border: 1px solid rgba(229, 233, 245, 0.96);
  background: rgba(255, 255, 255, 0.98);
  color: #344260;
  font-weight: 700;
}

.student-classes-bottom {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) 1.1fr;
  gap: 18px;
}

.student-teacher-profile {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 16px;
  align-items: center;
}

.student-teacher-profile p {
  margin: 0;
  color: var(--text-soft);
  line-height: 1.6;
}

.student-teacher-badge {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(125, 88, 255, 0.12);
  color: #7a56ff;
  font-size: 0.76rem;
  font-weight: 700;
  margin-left: 10px;
}

.student-library-list,
.student-schedule-list,
.student-reminder-list {
  display: grid;
  gap: 12px;
}

.student-library-item,
.student-schedule-item,
.student-reminder-item {
  display: grid;
  gap: 12px;
  align-items: center;
}

.student-library-item {
  grid-template-columns: minmax(0, 1fr) auto;
}

.student-library-copy,
.student-schedule-copy,
.student-watch-copy,
.student-reminder-copy {
  display: grid;
  gap: 4px;
}

.student-library-watch {
  min-height: 40px;
  padding: 0 16px;
  border-radius: 12px;
  border: 1px solid rgba(229, 233, 245, 0.96);
  background: rgba(255, 255, 255, 0.98);
  color: #344260;
  font-weight: 700;
}

.student-schedule-item {
  grid-template-columns: 72px minmax(0, 1fr) auto;
}

.student-schedule-date {
  display: grid;
  justify-items: center;
  padding: 12px;
  border-radius: 16px;
  background: rgba(246, 248, 252, 0.96);
}

.student-schedule-date strong {
  font-family: 'Fredoka', sans-serif;
  font-size: 1.35rem;
}

.student-schedule-live {
  color: #4aa746;
  font-weight: 800;
}

.student-watch-again-item {
  padding: 14px;
  border-radius: 18px;
  background: rgba(246, 248, 252, 0.96);
}

.student-reminder-item {
  grid-template-columns: minmax(0, 1fr) auto;
}

@media (max-width: 1420px) {
  .student-classes-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1080px) {
  .student-live-hero-grid,
  .student-classes-bottom {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .student-library-item,
  .student-schedule-item,
  .student-reminder-item {
    grid-template-columns: 1fr;
  }
}
</style>
