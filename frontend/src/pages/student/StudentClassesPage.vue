<template>
  <div class="page-stack student-classes-page">
    <DataNotice :using-fallback="liveResource.usingFallback.value" :error="actionError || liveResource.error.value" :success="actionSuccess" />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="student-classes-layout">
        <div class="student-classes-main">
          <article class="panel student-live-hero">
            <div class="student-section-heading student-section-heading--live">
              <span class="student-live-dot">LIVE</span>
              <strong>Upcoming Live Class</strong>
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
                  <p>{{ upcomingClass.description || 'No class description provided yet.' }}</p>

                  <div class="student-live-meta">
                    <span>{{ classTimeRange }}</span>
                    <span>{{ upcomingClass.durationMinutes || 45 }} min</span>
                    <span>{{ upcomingClass.meetingPlatform }}</span>
                  </div>

                  <div class="student-live-teacher">
                    <span class="student-mini-avatar">{{ teacherInitials }}</span>
                    <span>{{ primaryTeacherName }}</span>
                  </div>

                  <div class="student-live-actions">
                    <button class="btn btn-secondary" type="button" @click="openLinkedLesson">View Class Content</button>
                  </div>
                </div>

                <div class="student-live-scene">
                  <PandaIllustration class="student-live-panda" :size="164" outfit="#7d56ff" accent="#f6d056" pose="wave" />
                </div>
              </div>

              <div class="student-live-footer">
                <button class="btn btn-accent student-live-join" type="button" :disabled="liveReadOnly" @click="join(upcomingClass.id)">
                  {{ upcomingClass.canJoin ? 'Join Class' : upcomingClass.canEnrol ? 'Enrol First' : 'Class Locked' }}
                </button>
                <button class="student-live-setup" type="button" @click="router.push('/student/profile')">Open Profile</button>
              </div>
            </template>

            <StatePanel
              v-else
              icon="classes"
              title="No upcoming live class yet"
              message="Live classes will appear here when a teacher schedules them for you."
            />
          </article>

          <div class="student-classes-bottom">
            <article class="panel student-teacher-card">
              <div class="student-section-heading">
                <strong>Your Teacher</strong>
              </div>

              <template v-if="upcomingClass?.teacher">
                <div class="student-teacher-profile">
                  <PortraitAvatar :size="108" :kind="teacherPortraitKind" />
                  <div class="stack u-gap-6">
                    <div class="row">
                      <strong>{{ primaryTeacherName }}</strong>
                      <span class="student-teacher-badge">Linked Teacher</span>
                    </div>
                    <p>This teacher is attached to your upcoming live class through the real live class API.</p>
                  </div>
                </div>
              </template>
              <StatePanel
                v-else
                icon="students"
                title="No teacher assigned"
                message="Teacher details will appear here when a live class includes teacher information."
              />

              <button class="btn btn-secondary btn-block" type="button" @click="router.push('/student/profile')">View Feedback</button>
            </article>

            <article class="panel student-library-card">
              <div class="row-between u-mb-14">
                <div class="student-section-heading">
                  <strong>Recorded Lessons Library</strong>
                </div>
                <button class="student-link-button" type="button" @click="openLinkedLesson">Open Learn</button>
              </div>

              <div v-if="recordedLessonRows.length" class="student-library-list">
                <article v-for="video in recordedLessonRows" :key="video.id" class="student-library-item">
                  <div class="student-library-copy">
                    <strong>{{ video.title }}</strong>
                    <span class="muted">{{ video.context }}</span>
                    <small>{{ video.meta }}</small>
                  </div>
                  <button class="student-library-watch" type="button" @click="openRecordedLesson(video)">Watch</button>
                </article>
              </div>
              <StatePanel
                v-else
                icon="learn"
                title="No recorded lessons"
                message="Recorded lesson access will appear here once your lesson player includes videos."
              />
            </article>
          </div>
        </div>

        <aside class="student-classes-side">
          <article class="panel student-weekly-card">
            <div class="row-between u-mb-10">
              <div class="student-section-heading">
                <strong>My Weekly Schedule</strong>
              </div>
              <button class="student-link-button" type="button" @click="reloadAll">Refresh</button>
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
                  <span v-if="item.canJoin" class="student-schedule-live">LIVE</span>
                  <span v-else>{{ item.statusLabel }}</span>
                </div>
              </article>
            </div>
            <StatePanel
              v-else
              icon="classes"
              title="No classes scheduled"
              message="Weekly live classes will appear here when the backend returns them."
            />
          </article>

          <article class="panel student-watch-again-card">
            <div class="row-between u-mb-12">
              <div class="student-section-heading">
                <strong>Lesson Access Notes</strong>
              </div>
              <button class="student-link-button" type="button" @click="openLinkedLesson">Open Learn</button>
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
                <strong>Class Reminders</strong>
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
              title="No reminders"
              message="Reminders will appear here when you have upcoming live classes or enrolment actions."
            />
          </article>
        </aside>
      </section>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
/**
 * Student live-class + recorded-video hub.
 *
 * <p>Lists enrolments, upcoming live sessions, and recorded
 * playback. The join button only appears within the join window —
 * the gate is enforced server-side by
 * {@code LiveLearningAccessService} so a leaked meeting URL cannot
 * be reused outside the window.</p>
 */
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import { publicApi, studentApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import { useResource } from '@/composables/useResource'
import { publicCourseDetails, studentLiveClasses, teacherCoursePackages } from '@/demo/data'
import type { CoursePackageResponse, LiveClassResponse } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { openSafeHttpUrl } from '@/utils/url'

const router = useRouter()

const liveResource = useResource(() => studentApi.liveClasses(), studentLiveClasses)

const liveClasses = computed(() => liveResource.data.value ?? (liveResource.usingFallback.value ? studentLiveClasses : []))
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
const classBusy = ref(false)
const pageLoading = computed(() => liveResource.loading.value)
const pageError = computed(() => liveResource.error.value)
const liveReadOnly = computed(() => liveResource.usingFallback.value)

const upcomingClass = computed(() => liveClasses.value.find((item) => item.canJoin) ?? liveClasses.value[0] ?? null)
const primaryTeacherName = computed(() => upcomingClass.value?.teacher?.displayName || 'Teacher unavailable')
const teacherPortraitKind = computed<'female' | 'male' | 'hijab' | 'student'>(() => {
  const name = primaryTeacherName.value.toLowerCase()
  if (name.includes('siti')) return 'hijab'
  if (name.includes('chen')) return 'male'
  if (name.includes('unavailable')) return 'student'
  return 'female'
})

const teacherInitials = computed(() =>
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
  new Intl.DateTimeFormat('en-US', { month: 'short' }).format(classDate.value).toUpperCase(),
)
const upcomingDay = computed(() =>
  new Intl.DateTimeFormat('en-US', { day: 'numeric' }).format(classDate.value),
)
const upcomingWeekday = computed(() =>
  new Intl.DateTimeFormat('en-US', { weekday: 'short' }).format(classDate.value).toUpperCase(),
)
const classTimeRange = computed(() => {
  const formatter = new Intl.DateTimeFormat('en-US', {
    hour: 'numeric',
    minute: '2-digit',
  })

  if (!upcomingClass.value?.startTime || !upcomingClass.value?.endTime) {
    return 'Schedule unavailable'
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
        teacherName: item.teacher?.displayName || 'Teacher unavailable',
        weekdayShort: new Intl.DateTimeFormat('en-US', { weekday: 'short' }).format(date).toUpperCase(),
        day: new Intl.DateTimeFormat('en-US', { day: 'numeric' }).format(date),
        timeLabel: new Intl.DateTimeFormat('en-US', { hour: 'numeric', minute: '2-digit' }).format(date),
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
    return `Live class access for ${upcomingClass.value.title}`
  }

  return 'No recorded lesson access yet'
})

const watchAgainSubtitle = computed(() => {
  if (recordedLessonRows.value.length) {
    return 'Recorded lessons are available through the linked lesson or package.'
  }

  if (upcomingClass.value?.canJoin) {
    return 'You can join the upcoming live class directly from this page.'
  }

  return 'Recorded lesson and join details will appear here once a class is linked.'
})

const watchAgainMeta = computed(() => {
  if (upcomingClass.value) {
    return `${formatStatus(upcomingClass.value.liveClassStatus)} - ${upcomingClass.value.meetingPlatform}`
  }

  return 'Waiting for class scheduling data'
})

const reminders = computed(() => {
  if (!upcomingClass.value) return []

  return [
    {
      title: upcomingClass.value.canJoin ? 'Class is ready to join' : 'Upcoming class scheduled',
      copy: upcomingClass.value.title,
      time: classTimeRange.value,
    },
    {
      title: upcomingClass.value.canEnrol ? 'Enrolment available' : 'Class access depends on enrolment',
      copy: `Status: ${formatStatus(upcomingClass.value.liveClassStatus)}`,
      time: upcomingClass.value.meetingPlatform,
    },
  ]
})

function recordedRowsFromClass(item: LiveClassResponse) {
  if (item.lesson?.lessonTitle) {
    return [
      {
        id: `lesson-${item.lesson.lessonId}`,
        lessonId: item.lesson.lessonId,
        packageId: null,
        title: item.lesson.lessonTitle,
        context: 'Lesson linked to this live class',
        meta: `${item.lesson.learningType} - ${item.lesson.accessType}`,
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
        context: 'Package linked to this live class',
        meta: `${item.coursePackage.learningType} - ${item.coursePackage.accessType}`,
      },
    ]
  }

  return []
}

type RecordedLessonRow = ReturnType<typeof recordedRowsFromClass>[number]

function packageCourseId(packageId: number) {
  return `PACKAGE-${packageId}`
}

function fallbackLessonIdFromPackage(packageId: number) {
  return teacherCoursePackages
    .find((item: CoursePackageResponse) => item.id === packageId && item.contentStatus === 'PUBLISHED')
    ?.packageItems.find((item) => item.itemType === 'LESSON')?.itemId ?? null
}

async function openPackageLinkedContent(packageId: number) {
  const courseId = packageCourseId(packageId)

  try {
    const detail = await publicApi.getCourseDetail(courseId)
    const lessonId = detail.packageItems.find((item) => item.type === 'LESSON')?.contentId ?? null

    if (lessonId) {
      await router.push({ name: 'student-learn', query: { lessonId: String(lessonId) } })
      return
    }

    await router.push({ name: 'course-detail', params: { courseId } })
  } catch (reason) {
    if (liveResource.usingFallback.value) {
      const fallbackLessonId = fallbackLessonIdFromPackage(packageId)
      if (fallbackLessonId) {
        await router.push({ name: 'student-learn', query: { lessonId: String(fallbackLessonId) } })
        return
      }

      if (publicCourseDetails[courseId]) {
        await router.push({ name: 'course-detail', params: { courseId } })
        return
      }
    }

    actionError.value = resolveApiError(reason, 'Unable to open the linked package content.')
  }
}

async function openRecordedLesson(video: RecordedLessonRow) {
  actionError.value = null
  actionSuccess.value = null

  if (video.lessonId) {
    await router.push({ name: 'student-learn', query: { lessonId: String(video.lessonId) } })
    return
  }

  if (video.packageId) {
    await openPackageLinkedContent(video.packageId)
    return
  }

  actionError.value = 'No linked lesson or course is available for this recording yet.'
}

async function openLinkedLesson() {
  actionError.value = null
  actionSuccess.value = null
  const lessonId = upcomingClass.value?.lesson?.lessonId ?? recordedLessonRows.value.find((item) => item.lessonId)?.lessonId

  if (lessonId) {
    await router.push({ name: 'student-learn', query: { lessonId: String(lessonId) } })
    return
  }

  const packageId = upcomingClass.value?.coursePackage?.packageId ?? recordedLessonRows.value.find((item) => item.packageId)?.packageId ?? null

  if (packageId) {
    await openPackageLinkedContent(packageId)
    return
  }

  actionError.value = 'No linked lesson or course is available for this class yet.'
}

async function enrol(id: number) {
  if (classBusy.value) return
  actionError.value = null
  actionSuccess.value = null
  if (liveReadOnly.value) {
    actionError.value = 'Preview live classes cannot be enrolled. Use live class data to change enrolment.'
    return
  }

  classBusy.value = true
  try {
    await studentApi.enrolLiveClass(id)
    await liveResource.reload()
    actionSuccess.value = 'You are enrolled. The live class schedule has been refreshed.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to enrol in this class.')
  } finally {
    classBusy.value = false
  }
}

async function join(id: number) {
  if (classBusy.value) return
  const target = liveClasses.value.find((item) => item.id === id)
  actionError.value = null
  actionSuccess.value = null

  if (liveReadOnly.value) {
    actionError.value = 'Preview live classes cannot be joined. Use live class data to open a meeting link.'
    return
  }

  if (target?.canJoin) {
    actionError.value = null
    try {
      const response = await studentApi.joinLiveClass(id)
      if (openSafeHttpUrl(response.joinUrl)) {
        actionSuccess.value = 'Opening the live class in a new tab.'
      } else {
        actionError.value = 'The live class meeting link is not a safe web URL.'
      }
    } catch (reason) {
      actionError.value = resolveApiError(reason, 'Unable to join this class right now.')
    }
    return
  }

  if (target?.canEnrol) {
    await enrol(id)
    return
  }

  actionError.value = 'This class is locked. Open Learn to continue with available lessons first.'
}

function formatStatus(status: string) {
  return status
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/(^|\s)\w/g, (match) => match.toUpperCase())
}

function reloadAll() {
  void liveResource.reload()
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
