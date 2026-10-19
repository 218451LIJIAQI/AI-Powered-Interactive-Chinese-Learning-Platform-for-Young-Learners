<template>
  <div class="page-stack student-classes-page">
    <PageHeaderBlock
      :title="t('studentClasses.pageTitle')"
      :description="t('studentClasses.pageDescription')"
      icon="classes"
    />

    <DataNotice :using-fallback="liveResource.usingFallback.value" :error="actionError" :success="actionSuccess" />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <div class="student-classes-layout">
        <div class="student-classes-main">
          <section class="panel student-classes-card student-live-hero" aria-labelledby="student-live-upcoming-title">
            <div class="student-classes-card-head">
              <h2 id="student-live-upcoming-title" class="card-title">{{ t('studentClasses.upcoming') }}</h2>
              <StatusPill
                v-if="upcomingClass && isLiveNow(upcomingClass)"
                :label="t('studentClasses.liveNow')"
                tone="danger"
              />
            </div>

            <template v-if="upcomingClass">
              <div class="student-live-hero-grid">
                <div class="student-live-date" aria-hidden="true">
                  <span>{{ upcomingMonth }}</span>
                  <strong>{{ upcomingDay }}</strong>
                  <small>{{ upcomingWeekday }}</small>
                </div>

                <div class="student-live-copy">
                  <h3 class="student-live-title">{{ upcomingClass.title }}</h3>
                  <p class="student-live-description">{{ upcomingClassDescription }}</p>

                  <ul class="student-live-meta">
                    <li><IconGlyph name="calendar" :size="16" />{{ classTimeRange }}</li>
                    <li v-if="upcomingClassDurationLabel">
                      <IconGlyph name="history" :size="16" />{{ upcomingClassDurationLabel }}
                    </li>
                    <li><IconGlyph name="monitor" :size="16" />{{ upcomingMeetingPlatformLabel }}</li>
                  </ul>

                  <div class="student-live-teacher">
                    <PortraitAvatar :size="32" :name="primaryTeacherName" />
                    <span>{{ primaryTeacherName }}</span>
                  </div>
                </div>

                <PandaIllustration
                  class="student-live-panda"
                  :size="132"
                  outfit="var(--color-primary-500)"
                  accent="var(--color-accent-400)"
                  pose="wave"
                />
              </div>

              <div class="student-live-footer">
                <button
                  class="btn btn-primary"
                  type="button"
                  data-testid="student-next-class-action"
                  :disabled="primaryActionDisabled(upcomingClass)"
                  @click="runPrimaryAction(upcomingClass)"
                >
                  {{ primaryActionLabel(upcomingClass) }}
                </button>
                <button class="btn btn-secondary" type="button" @click="openLinkedLesson">
                  {{ t('studentClasses.viewContent') }}
                </button>
                <StatusPill :label="seatLabel(upcomingClass)" :tone="seatTone(upcomingClass)" />
                <button class="btn btn-ghost student-live-profile" type="button" @click="openProfile">
                  {{ t('studentClasses.openProfile') }}
                </button>
              </div>
            </template>

            <StatePanel
              v-else
              compact
              icon="classes"
              :title="t('studentClasses.noUpcoming')"
              :message="t('studentClasses.noUpcomingHint')"
            />
          </section>

          <section
            class="panel student-classes-card"
            data-testid="student-class-list"
            aria-labelledby="student-class-list-title"
          >
            <div class="student-classes-card-head">
              <div class="student-classes-card-heading">
                <h2 id="student-class-list-title" class="card-title">{{ t('studentClasses.allClasses') }}</h2>
                <p class="card-subtitle">{{ t('studentClasses.allClassesHint') }}</p>
              </div>
              <span class="tag">{{ t('studentClasses.classCount', { count: classCards.length }) }}</span>
            </div>

            <ul v-if="classCards.length" class="student-class-list">
              <li
                v-for="card in classCards"
                :key="card.item.id"
                class="student-class-card"
                :class="{ 'is-enrolled': card.item.enrolled, 'is-full': card.seats.kind === 'full' }"
                data-testid="student-class-card"
              >
                <div class="student-class-card-main">
                  <div class="student-class-card-title">
                    <h3>{{ card.item.title }}</h3>
                    <StatusPill
                      :label="statusLabel('liveClass', card.item.liveClassStatus)"
                      :tone="statusTone(card.item.liveClassStatus)"
                    />
                    <StatusPill
                      v-if="card.item.enrolled"
                      :label="t('studentClasses.enrolledBadge')"
                      tone="primary"
                      icon="status"
                    />
                    <StatusPill v-if="isLiveNow(card.item)" :label="t('studentClasses.liveNow')" tone="danger" />
                  </div>

                  <p class="student-class-card-meta">
                    <span>{{ card.teacherLabel }}</span>
                    <span>{{ card.contentLabel }}</span>
                  </p>

                  <p class="student-class-card-meta">
                    <span>{{ card.dateLabel }}</span>
                    <span>{{ card.timeLabel }}</span>
                    <span>{{ card.platformLabel }}</span>
                  </p>

                  <StatusPill :label="card.seatLabel" :tone="seatTone(card.item)" data-testid="student-class-seats" />
                </div>

                <div class="student-class-card-actions">
                  <template v-if="confirmCancelId === card.item.id">
                    <span class="student-class-confirm" role="status">{{ t('studentClasses.confirmCancel') }}</span>
                    <button
                      class="btn btn-ghost btn-sm"
                      type="button"
                      :disabled="isPending(card.item.id)"
                      @click="confirmCancelId = null"
                    >
                      {{ t('studentClasses.keepSeat') }}
                    </button>
                    <button
                      class="btn btn-danger-soft btn-sm"
                      type="button"
                      data-testid="student-class-confirm-cancel"
                      :disabled="isPending(card.item.id)"
                      :aria-busy="isPending(card.item.id)"
                      @click="cancelEnrolment(card.item)"
                    >
                      {{
                        isPending(card.item.id) ? t('studentClasses.updating') : t('studentClasses.confirmCancelYes')
                      }}
                    </button>
                  </template>
                  <template v-else>
                    <button
                      class="btn btn-sm"
                      :class="
                        card.action.primary === 'join' || card.action.primary === 'enrol'
                          ? 'btn-outline'
                          : 'btn-secondary'
                      "
                      type="button"
                      data-testid="student-class-action"
                      :data-action="card.action.primary"
                      :disabled="primaryActionDisabled(card.item)"
                      :aria-busy="isPending(card.item.id)"
                      @click="runPrimaryAction(card.item)"
                    >
                      {{ primaryActionLabel(card.item) }}
                    </button>
                    <button
                      v-if="card.action.canCancel"
                      class="btn btn-ghost btn-sm"
                      type="button"
                      data-testid="student-class-cancel"
                      :disabled="isPending(card.item.id)"
                      @click="requestCancel(card.item)"
                    >
                      {{ t('studentClasses.cancelEnrolment') }}
                    </button>
                  </template>
                </div>
              </li>
            </ul>
            <StatePanel
              v-else
              compact
              icon="classes"
              :title="t('studentClasses.noUpcoming')"
              :message="t('studentClasses.noUpcomingHint')"
            />
          </section>

          <div class="student-classes-bottom">
            <section class="panel student-classes-card" aria-labelledby="student-teacher-title">
              <h2 id="student-teacher-title" class="card-title">{{ t('studentClasses.yourTeacher') }}</h2>

              <div v-if="upcomingClass?.teacher" class="student-teacher-profile">
                <PortraitAvatar :size="56" :name="upcomingClass.teacher.displayName" />
                <div class="student-teacher-copy">
                  <div class="student-teacher-name">
                    <strong>{{ primaryTeacherName }}</strong>
                    <StatusPill :label="t('studentClasses.linkedTeacher')" tone="primary" icon="verified" />
                  </div>
                  <p>{{ t('studentClasses.teacherHint') }}</p>
                </div>
              </div>
              <StatePanel
                v-else
                compact
                icon="students"
                :title="t('studentClasses.noTeacher')"
                :message="t('studentClasses.noTeacherHint')"
              />

              <button class="btn btn-secondary btn-block" type="button" @click="openProfile">
                {{ t('studentClasses.viewFeedback') }}
              </button>
            </section>

            <section class="panel student-classes-card" aria-labelledby="student-library-title">
              <div class="student-classes-card-head">
                <h2 id="student-library-title" class="card-title">{{ t('studentClasses.library') }}</h2>
                <button class="btn btn-link" type="button" @click="openLinkedLesson">
                  {{ t('studentClasses.openLearn') }}
                </button>
              </div>

              <ul v-if="recordedLessonRows.length" class="student-library-list">
                <li v-for="video in recordedLessonRows" :key="video.id" class="student-library-item">
                  <span class="student-library-icon" aria-hidden="true"><IconGlyph name="play" :size="18" /></span>
                  <div class="student-library-copy">
                    <strong>{{ video.title }}</strong>
                    <span>{{ video.context }}</span>
                    <small>{{ video.meta }}</small>
                  </div>
                  <button class="btn btn-secondary btn-sm" type="button" @click="openRecordedLesson(video)">
                    {{ t('studentClasses.watch') }}
                  </button>
                </li>
              </ul>
              <StatePanel
                v-else
                compact
                icon="learn"
                :title="t('studentClasses.noRecordings')"
                :message="t('studentClasses.noRecordingsHint')"
              />
            </section>
          </div>
        </div>

        <aside class="student-classes-side">
          <section class="panel student-classes-card" aria-labelledby="student-weekly-title">
            <div class="student-classes-card-head">
              <h2 id="student-weekly-title" class="card-title">{{ t('studentClasses.weekly') }}</h2>
              <button class="btn btn-link" type="button" @click="reloadAll">
                <IconGlyph name="refresh" :size="16" />
                {{ t('studentClasses.refresh') }}
              </button>
            </div>

            <ul v-if="weeklySchedule.length" class="student-schedule-list">
              <li
                v-for="item in weeklySchedule"
                :key="item.id"
                class="student-schedule-item"
                :class="{ 'is-live': item.liveNow }"
              >
                <div class="student-schedule-date" aria-hidden="true">
                  <span>{{ item.weekdayShort }}</span>
                  <strong>{{ item.day }}</strong>
                </div>

                <div class="student-schedule-copy">
                  <strong>{{ item.title }}</strong>
                  <span>{{ item.timeLabel }} · {{ item.teacherName }}</span>
                  <StatusPill v-if="item.liveNow" :label="t('studentClasses.liveNow')" tone="danger" />
                  <StatusPill v-else :label="item.statusLabel" :tone="statusTone(item.status)" />
                </div>
              </li>
            </ul>
            <StatePanel
              v-else
              compact
              icon="classes"
              :title="t('studentClasses.noClasses')"
              :message="t('studentClasses.noClassesHint')"
            />
          </section>

          <section class="panel student-classes-card" aria-labelledby="student-access-title">
            <div class="student-classes-card-head">
              <h2 id="student-access-title" class="card-title">{{ t('studentClasses.accessNotes') }}</h2>
              <button class="btn btn-link" type="button" @click="openLinkedLesson">
                {{ t('studentClasses.openLearn') }}
              </button>
            </div>

            <div class="student-access-note">
              <strong>{{ watchAgainTitle }}</strong>
              <span>{{ watchAgainSubtitle }}</span>
              <small>{{ watchAgainMeta }}</small>
            </div>
          </section>

          <section class="panel student-classes-card" aria-labelledby="student-reminders-title">
            <h2 id="student-reminders-title" class="card-title">{{ t('studentClasses.reminders') }}</h2>

            <ul v-if="reminders.length" class="student-reminder-list">
              <li v-for="reminder in reminders" :key="reminder.title" class="student-reminder-item">
                <span class="student-reminder-icon" aria-hidden="true"><IconGlyph name="bell" :size="16" /></span>
                <div class="student-reminder-copy">
                  <strong>{{ reminder.title }}</strong>
                  <span>{{ reminder.copy }}</span>
                  <small>{{ reminder.time }}</small>
                </div>
              </li>
            </ul>
            <StatePanel
              v-else
              compact
              icon="bell"
              :title="t('studentClasses.noReminders')"
              :message="t('studentClasses.noRemindersHint')"
            />
          </section>
        </aside>
      </div>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { publicApi, studentApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { useStatusLabels } from '@/composables/useStatusLabels'
import { publicCourseDetails, studentLiveClasses, teacherCoursePackages } from '@/demo/data'
import { humanizeEnumValue } from '@/plugins/statusLabelMessages'
import { studentClassesMessages } from '@/plugins/studentClassesMessages'
import type { CoursePackageResponse, LiveClassResponse } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { getDisplayLocale } from '@/utils/format'
import {
  formatLiveClassSchedule,
  isTerminalLiveClass,
  liveClassActionState,
  liveClassSeatState,
  replaceLiveClass,
  safeTimeZone,
  sortLiveClassesByStart,
} from '@/utils/liveClassActions'
import { openSafeHttpUrl } from '@/utils/url'

const { locale, t } = useI18n({
  messages: {
    en: { studentClasses: studentClassesMessages.en },
    ms: { studentClasses: studentClassesMessages.ms },
    'zh-CN': { studentClasses: studentClassesMessages['zh-CN'] },
  },
})

const { statusLabel } = useStatusLabels()
const dateLocale = computed(() => getDisplayLocale(locale.value))

const router = useRouter()

/** Current time, refreshed every 30 seconds so the "live" badge follows the real class window. */
const now = ref(Date.now())
let nowTimer: ReturnType<typeof setInterval> | null = null
onMounted(() => {
  nowTimer = setInterval(() => {
    now.value = Date.now()
  }, 30_000)
})
onBeforeUnmount(() => {
  if (nowTimer) clearInterval(nowTimer)
})

/** A class is "live" only while the current time is between its start and end time (D21). */
function isLiveNow(item: LiveClassResponse): boolean {
  if (isTerminalLiveClass(item)) return false
  const start = new Date(item.startTime).getTime()
  const end = new Date(item.endTime).getTime()
  if (!Number.isFinite(start) || !Number.isFinite(end)) return false
  return start <= now.value && now.value < end
}

const liveResource = useResource(() => studentApi.liveClasses(), studentLiveClasses)

const liveClasses = computed(() => sortLiveClassesByStart(liveResource.data.value ?? []))
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
type PendingAction = 'enrol' | 'cancel' | 'join'
/** Per-class pending state so one class's request never blocks the others. */
const pendingActions = ref<Record<number, PendingAction>>({})
const confirmCancelId = ref<number | null>(null)
const pageLoading = computed(() => liveResource.loading.value)
const pageError = computed(() => liveResource.error.value)
const liveReadOnly = computed(() => liveResource.usingFallback.value)

/** "Next class" highlight: a joinable class first, then the next enrolled class, then the next open one. */
const upcomingClass = computed(
  () =>
    liveClasses.value.find((item) => item.canJoin) ??
    liveClasses.value.find((item) => item.enrolled && !isTerminalLiveClass(item)) ??
    liveClasses.value.find((item) => !isTerminalLiveClass(item)) ??
    liveClasses.value[0] ??
    null,
)

const classCards = computed(() =>
  liveClasses.value.map((item) => {
    const schedule = formatLiveClassSchedule(item, dateLocale.value)
    const seats = liveClassSeatState(item)
    return {
      item,
      seats,
      seatLabel: seatLabel(item),
      action: liveClassActionState(item, now.value),
      teacherLabel: t('studentClasses.teacherLabel', {
        name: item.teacher?.displayName || t('studentClasses.teacherUnavailable'),
      }),
      contentLabel: classContentLabel(item),
      dateLabel: schedule?.date ?? t('studentClasses.scheduleUnavailable'),
      timeLabel: schedule
        ? t('studentClasses.timeInZone', { time: schedule.time, zone: schedule.timeZone })
        : t('studentClasses.scheduleUnavailable'),
      platformLabel: statusLabel('platform', item.meetingPlatform || 'OTHER'),
    }
  }),
)

function classContentLabel(item: LiveClassResponse): string {
  if (item.lesson?.lessonTitle && item.coursePackage?.packageTitle) {
    return t('studentClasses.lessonInCourse', {
      lesson: item.lesson.lessonTitle,
      course: item.coursePackage.packageTitle,
    })
  }
  if (item.lesson?.lessonTitle) return t('studentClasses.lessonLabel', { title: item.lesson.lessonTitle })
  if (item.coursePackage?.packageTitle) {
    return t('studentClasses.courseLabel', { title: item.coursePackage.packageTitle })
  }
  return t('studentClasses.noLinkedContent')
}

function seatLabel(item: LiveClassResponse): string {
  const seats = liveClassSeatState(item)
  if (seats.kind === 'unlimited') return t('studentClasses.seatsUnlimited')
  if (seats.kind === 'full') return t('studentClasses.seatsFull')
  return t('studentClasses.seatsLeft', { remaining: seats.remaining, total: seats.total })
}

function isPending(id: number): boolean {
  return pendingActions.value[id] !== undefined
}

function setPending(id: number, action: PendingAction | null): void {
  const next = { ...pendingActions.value }
  if (action) next[id] = action
  else delete next[id]
  pendingActions.value = next
}

function primaryActionLabel(item: LiveClassResponse): string {
  if (isPending(item.id)) return t('studentClasses.updating')
  const { primary } = liveClassActionState(item, now.value)
  if (primary === 'join') return t('studentClasses.join')
  if (primary === 'enrol') return t('studentClasses.enrol')
  if (primary === 'full') return t('studentClasses.classFull')
  if (primary === 'waiting') return t('studentClasses.startsLater')
  return isTerminalLiveClass(item) ? statusLabel('liveClass', item.liveClassStatus) : t('studentClasses.notJoinable')
}

function primaryActionDisabled(item: LiveClassResponse): boolean {
  if (isPending(item.id)) return true
  const { primary } = liveClassActionState(item, now.value)
  return primary !== 'join' && primary !== 'enrol'
}

async function runPrimaryAction(item: LiveClassResponse): Promise<void> {
  const { primary } = liveClassActionState(item, now.value)
  if (primary === 'join') {
    await join(item)
    return
  }
  if (primary === 'enrol') {
    await enrol(item)
    return
  }
  actionSuccess.value = null
  actionError.value = t('studentClasses.notJoinableHint')
}

function applyUpdatedClass(updated: LiveClassResponse | null | undefined): void {
  if (!updated || typeof updated.id !== 'number') {
    void liveResource.reload()
    return
  }
  liveResource.data.value = replaceLiveClass(liveClasses.value, updated)
}

const primaryTeacherName = computed(
  () => upcomingClass.value?.teacher?.displayName || t('studentClasses.teacherUnavailable'),
)
const upcomingClassDescription = computed(() => upcomingClass.value?.description || t('studentClasses.noDescription'))
const upcomingClassDurationLabel = computed(() => {
  const liveClass = upcomingClass.value
  if (!liveClass) return ''
  let minutes = liveClass.durationMinutes ?? null
  if (!minutes) {
    const start = new Date(liveClass.startTime).getTime()
    const end = new Date(liveClass.endTime).getTime()
    minutes = Number.isFinite(start) && Number.isFinite(end) && end > start ? Math.round((end - start) / 60_000) : null
  }
  return minutes ? t('studentClasses.minutes', { count: minutes }) : ''
})

const classDate = computed(() => new Date(upcomingClass.value?.startTime ?? new Date().toISOString()))
const upcomingTimeZone = computed(() => safeTimeZone(upcomingClass.value?.timeZone))
const upcomingMonth = computed(() =>
  new Intl.DateTimeFormat(dateLocale.value, { month: 'short', timeZone: upcomingTimeZone.value })
    .format(classDate.value)
    .toUpperCase(),
)
const upcomingDay = computed(() =>
  new Intl.DateTimeFormat(dateLocale.value, { day: 'numeric', timeZone: upcomingTimeZone.value }).format(
    classDate.value,
  ),
)
const upcomingWeekday = computed(() =>
  new Intl.DateTimeFormat(dateLocale.value, { weekday: 'short', timeZone: upcomingTimeZone.value })
    .format(classDate.value)
    .toUpperCase(),
)
const classTimeRange = computed(() => {
  if (!upcomingClass.value?.startTime || !upcomingClass.value?.endTime) {
    return t('studentClasses.scheduleUnavailable')
  }

  const schedule = formatLiveClassSchedule(upcomingClass.value, dateLocale.value)
  return schedule
    ? t('studentClasses.timeInZone', { time: schedule.time, zone: schedule.timeZone })
    : t('studentClasses.scheduleUnavailable')
})

const weeklySchedule = computed(() =>
  liveClasses.value.slice(0, 4).map((item) => {
    const date = new Date(item.startTime)
    const timeZone = safeTimeZone(item.timeZone)
    return {
      id: item.id,
      title: item.title,
      teacherName: item.teacher?.displayName || t('studentClasses.teacherUnavailable'),
      weekdayShort: new Intl.DateTimeFormat(dateLocale.value, { weekday: 'short', timeZone })
        .format(date)
        .toUpperCase(),
      day: new Intl.DateTimeFormat(dateLocale.value, { day: 'numeric', timeZone }).format(date),
      timeLabel: new Intl.DateTimeFormat(dateLocale.value, { hour: 'numeric', minute: '2-digit', timeZone }).format(
        date,
      ),
      liveNow: isLiveNow(item),
      status: item.liveClassStatus,
      statusLabel: statusLabel('liveClass', item.liveClassStatus),
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
    return `${statusLabel('liveClass', upcomingClass.value.liveClassStatus)} - ${upcomingMeetingPlatformLabel.value}`
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
      copy: t('studentClasses.status', { status: statusLabel('liveClass', upcomingClass.value.liveClassStatus) }),
      time: upcomingMeetingPlatformLabel.value,
    },
  ]
})

const upcomingMeetingPlatformLabel = computed(() =>
  statusLabel('platform', upcomingClass.value?.meetingPlatform ?? 'OTHER'),
)

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
        meta: formatLearningType(item.lesson.learningType),
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
        meta: formatLearningType(item.coursePackage.learningType),
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

async function enrol(item: LiveClassResponse): Promise<void> {
  if (isPending(item.id)) return
  actionError.value = null
  actionSuccess.value = null
  if (liveReadOnly.value) {
    actionError.value = t('studentClasses.previewEnrol')
    return
  }

  setPending(item.id, 'enrol')
  try {
    const updated = await studentApi.enrolLiveClass(item.id)
    applyUpdatedClass(updated)
    actionSuccess.value = t('studentClasses.enrolledIn', { title: updated?.title ?? item.title })
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('studentClasses.enrolError'))
    // Seat counts may have changed (e.g. the class just filled up), so refresh the list quietly.
    void liveResource.reload()
  } finally {
    setPending(item.id, null)
  }
}

function requestCancel(item: LiveClassResponse): void {
  actionError.value = null
  actionSuccess.value = null
  confirmCancelId.value = item.id
}

async function cancelEnrolment(item: LiveClassResponse): Promise<void> {
  if (isPending(item.id)) return
  actionError.value = null
  actionSuccess.value = null
  if (liveReadOnly.value) {
    confirmCancelId.value = null
    actionError.value = t('studentClasses.previewCancel')
    return
  }

  setPending(item.id, 'cancel')
  try {
    const updated = await studentApi.cancelLiveClassEnrolment(item.id)
    applyUpdatedClass(updated)
    actionSuccess.value = t('studentClasses.cancelledFor', { title: updated?.title ?? item.title })
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('studentClasses.cancelError'))
  } finally {
    setPending(item.id, null)
    confirmCancelId.value = null
  }
}

async function join(item: LiveClassResponse): Promise<void> {
  if (isPending(item.id)) return
  actionError.value = null
  actionSuccess.value = null

  if (liveReadOnly.value) {
    actionError.value = t('studentClasses.previewJoin')
    return
  }

  if (!item.canJoin) {
    actionError.value = t('studentClasses.notJoinableHint')
    return
  }

  setPending(item.id, 'join')
  try {
    const response = await studentApi.joinLiveClass(item.id)
    if (openSafeHttpUrl(response.meetingLink)) {
      actionSuccess.value = t('studentClasses.opening')
    } else {
      actionError.value = t('studentClasses.unsafeLink')
    }
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('studentClasses.joinError'))
  } finally {
    setPending(item.id, null)
  }
}

type PillTone = 'success' | 'warning' | 'danger' | 'info' | 'primary' | 'neutral'

function statusTone(status: string): PillTone {
  const tones: Record<string, PillTone> = {
    SCHEDULED: 'info',
    ONGOING: 'success',
    COMPLETED: 'neutral',
    CANCELLED: 'danger',
    RESCHEDULED: 'warning',
  }
  return tones[status] ?? 'neutral'
}

function seatTone(item: LiveClassResponse): PillTone {
  const kind = liveClassSeatState(item).kind
  if (kind === 'full') return 'danger'
  return kind === 'available' ? 'success' : 'neutral'
}

function formatLearningType(learningType: string): string {
  const keys: Record<string, string> = { LIVE: 'typeLive', RECORDED: 'typeRecorded', HYBRID: 'typeHybrid' }
  const key = keys[learningType.toUpperCase()]
  return key ? t(`studentClasses.${key}`) : humanizeEnumValue(learningType)
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
  grid-template-columns: minmax(0, 1fr) minmax(280px, 340px);
  gap: var(--page-gap);
  align-items: start;
}

.student-classes-main,
.student-classes-side {
  display: grid;
  gap: var(--page-gap);
  align-content: start;
  min-width: 0;
}

.student-classes-card {
  display: grid;
  gap: var(--space-4);
  align-content: start;
}

.student-classes-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  min-width: 0;
}

.student-classes-card-head > .card-title,
.student-classes-card-heading {
  flex: 1 1 auto;
  min-width: 0;
  overflow-wrap: anywhere;
}

.student-classes-card-head > .btn,
.student-classes-card-head > .tag {
  flex: 0 0 auto;
  white-space: nowrap;
}

.student-classes-card-heading .card-subtitle {
  margin-top: var(--space-1);
}

/* Upcoming class */
.student-live-hero {
  background: var(--gradient-brand-soft);
}

.student-live-hero-grid {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: var(--space-5);
  align-items: center;
}

.student-live-date {
  display: grid;
  justify-items: center;
  gap: var(--space-0-5);
  width: 96px;
  padding: var(--space-3) var(--space-2);
  border: 1px solid var(--color-primary-200);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
  box-shadow: var(--shadow-sm);
  text-transform: uppercase;
}

.student-live-date span,
.student-live-date small {
  color: var(--text-brand);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-extrabold);
  letter-spacing: var(--letter-spacing-wide);
}

.student-live-date strong {
  font-family: var(--font-display);
  font-size: var(--font-size-4xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
}

.student-live-copy {
  display: grid;
  gap: var(--space-2-5);
  min-width: 0;
}

.student-live-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-snug);
  overflow-wrap: anywhere;
}

.student-live-description {
  margin: 0;
  color: var(--text-secondary);
}

.student-live-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-4);
  margin: 0;
  padding: 0;
  list-style: none;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
}

.student-live-meta li {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1-5);
}

.student-live-teacher {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
}

.student-live-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding-top: var(--space-4);
  border-top: 1px solid var(--border-subtle);
}

.student-live-profile {
  margin-left: auto;
}

/* All classes */
.student-class-list {
  display: grid;
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.student-class-card {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3) var(--space-4);
  padding: var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
}

.student-class-card.is-enrolled {
  border-color: var(--color-primary-200);
  background: var(--color-primary-50);
}

.student-class-card.is-full {
  background: var(--surface-subtle);
}

.student-class-card-main {
  display: grid;
  flex: 1 1 360px;
  gap: var(--space-2);
  justify-items: start;
  min-width: 0;
}

.student-class-card-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.student-class-card-title h3 {
  margin: 0;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-bold);
  overflow-wrap: anywhere;
}

.student-class-card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1) var(--space-4);
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.student-class-card-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2);
}

.student-class-confirm {
  flex-basis: 100%;
  color: var(--color-danger-text);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  text-align: right;
}

/* Teacher and library */
.student-classes-bottom {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 300px), 1fr));
  gap: var(--grid-gap);
  align-items: start;
}

.student-teacher-profile {
  display: flex;
  align-items: flex-start;
  gap: var(--space-4);
}

.student-teacher-copy {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}

.student-teacher-name {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.student-teacher-copy p {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.student-library-list,
.student-schedule-list,
.student-reminder-list {
  display: grid;
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.student-library-item {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--surface-subtle);
}

.student-library-icon,
.student-reminder-icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}

.student-library-copy,
.student-reminder-copy,
.student-access-note {
  display: grid;
  gap: var(--space-0-5);
  min-width: 0;
}

.student-library-copy strong,
.student-reminder-copy strong,
.student-access-note strong {
  overflow-wrap: anywhere;
}

.student-library-copy span,
.student-reminder-copy span,
.student-access-note span {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.student-library-copy small,
.student-reminder-copy small,
.student-access-note small {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
}

/* Side */
.student-schedule-item {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr);
  gap: var(--space-3);
  align-items: start;
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--border-subtle);
}

.student-schedule-item:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.student-schedule-date {
  display: grid;
  justify-items: center;
  padding: var(--space-2) var(--space-1);
  border-radius: var(--radius-md);
  background: var(--color-primary-50);
  color: var(--color-primary-700);
}

.student-schedule-item.is-live .student-schedule-date {
  background: var(--color-danger-soft);
  color: var(--color-danger-text);
}

.student-schedule-date span {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-extrabold);
}

.student-schedule-date strong {
  font-family: var(--font-display);
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
}

.student-schedule-copy {
  display: grid;
  gap: var(--space-1);
  justify-items: start;
  min-width: 0;
}

.student-schedule-copy strong {
  font-size: var(--font-size-sm);
  overflow-wrap: anywhere;
}

.student-schedule-copy span {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
}

.student-access-note {
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  background: var(--surface-subtle);
}

.student-reminder-item {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  gap: var(--space-3);
  align-items: start;
}

@media (max-width: 1200px) {
  .student-classes-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .student-classes-side {
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr));
    align-items: start;
  }
}

@media (max-width: 900px) {
  .student-live-hero-grid {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .student-live-panda {
    display: none;
  }
}

@media (max-width: 640px) {
  .student-live-hero-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .student-live-date {
    grid-auto-flow: column;
    align-items: baseline;
    gap: var(--space-2);
    width: fit-content;
    padding: var(--space-2) var(--space-3);
  }

  .student-live-date strong {
    font-size: var(--font-size-2xl);
  }

  .student-live-title {
    font-size: var(--font-size-2xl);
  }

  .student-live-footer > .btn {
    width: 100%;
  }

  .student-live-profile {
    margin-left: 0;
  }

  .student-class-card-actions {
    width: 100%;
    justify-content: stretch;
  }

  .student-class-card-actions > .btn {
    flex: 1 1 0;
  }

  .student-class-confirm {
    text-align: left;
  }

  .student-library-item {
    grid-template-columns: 36px minmax(0, 1fr);
  }

  .student-library-item > .btn {
    grid-column: 2;
    justify-self: start;
  }
}
</style>
