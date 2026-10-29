<template>
  <div class="teacher-dash-page page-stack">
    <PageHeaderBlock :eyebrow="todayLabel" :title="t('teacherDashboard.title')" :description="summary" />

    <ResourceSection :loading="resource.loading.value" :error="resource.error.value" @retry="resource.reload">
      <div v-if="dashboard" class="teacher-dash-grid">
        <section class="teacher-dash-figures" :aria-label="t('teacherDashboard.numbers')">
          <dl class="teacher-figures-list">
            <div class="teacher-figure">
              <dt>{{ t('teacherDashboard.students') }}</dt>
              <dd class="stat-figure">{{ dashboard.studentCount }}</dd>
              <dd class="type-caption">{{ t('teacherDashboard.needsSupport', dashboard.needsSupportCount) }}</dd>
            </div>
            <div class="teacher-figure">
              <dt>{{ t('teacherDashboard.publishedContent') }}</dt>
              <dd class="stat-figure">{{ dashboard.publishedClasses + dashboard.publishedLessons }}</dd>
              <dd class="type-caption">
                {{
                  t('teacherDashboard.publishedFacts', {
                    classes: t('teacherDashboard.classes', dashboard.publishedClasses),
                    lessons: t('teacherDashboard.lessons', dashboard.publishedLessons),
                    inReview: dashboard.inReviewCount,
                  })
                }}
              </dd>
            </div>
            <div class="teacher-figure">
              <dt>{{ t('teacherDashboard.upcomingSessions') }}</dt>
              <dd class="stat-figure">{{ dashboard.upcomingSessionCount }}</dd>
              <dd class="type-caption">{{ t('teacherDashboard.bookings', dashboard.upcomingBookings) }}</dd>
            </div>
            <div class="teacher-figure">
              <dt>{{ t('teacherDashboard.quizAverage') }}</dt>
              <dd class="stat-figure">{{ dashboard.quizAverage !== null ? `${dashboard.quizAverage}%` : '—' }}</dd>
              <dd class="type-caption">
                {{
                  dashboard.quizAverage !== null
                    ? t('teacherDashboard.quizAverageFacts')
                    : t('teacherDashboard.noQuizzes')
                }}
              </dd>
            </div>
          </dl>
        </section>

        <section class="panel teacher-dash-queue" aria-labelledby="teacher-dash-todo-title">
          <div class="teacher-dash-head">
            <h2 id="teacher-dash-todo-title" class="section-title">{{ t('teacherDashboard.todo') }}</h2>
            <p class="type-caption">
              {{ t('teacherDashboard.todoFacts', { needs: needsYou.length, watching: watching.length }) }}
            </p>
          </div>

          <div v-if="!needsYou.length && !watching.length" class="teacher-dash-clear">
            <span class="seal is-outline" aria-hidden="true">好</span>
            <p>{{ t('teacherDashboard.todoEmpty') }}</p>
          </div>

          <div v-for="group in groups" :key="group.key" class="teacher-dash-group" :class="`is-${group.key}`">
            <h3 class="teacher-dash-group-title">
              <IconGlyph :name="group.key === 'needs' ? 'alert' : 'history'" :size="14" />
              {{ group.label }}
              <span class="teacher-dash-group-count">{{ group.items.length }}</span>
            </h3>
            <ol class="teacher-dash-todo">
              <li
                v-for="(item, index) in group.items"
                :key="`${item.kind}-${item.classId}-${item.lessonId}-${index}`"
                class="teacher-dash-todo-item"
              >
                <div class="teacher-dash-todo-copy">
                  <strong>{{ t(TODO_KEYS[item.kind] ?? TODO_KEYS.LESSON_IN_REVIEW!, { title: item.title }) }}</strong>
                  <blockquote v-if="item.needsYou && item.note" class="teacher-dash-note">
                    <p>{{ item.note }}</p>
                    <footer class="type-caption">
                      {{ t('teacherDashboard.noteFrom', { name: item.noteBy ?? '—', date: formatShortDate(item.at) }) }}
                    </footer>
                  </blockquote>
                  <span v-else-if="!item.needsYou" class="type-caption">{{ waitingDetail(item) }}</span>
                </div>
                <RouterLink
                  v-if="todoRoute(item)"
                  class="btn"
                  :class="item.needsYou ? 'btn-primary btn-sm' : 'btn-link'"
                  :to="todoRoute(item)!"
                >
                  {{
                    item.needsYou
                      ? item.lessonId
                        ? t('teacherDashboard.editLesson')
                        : t('teacherDashboard.editClass')
                      : t('teacherDashboard.open')
                  }}
                  <IconGlyph name="chevronRight" :size="16" />
                </RouterLink>
              </li>
            </ol>
          </div>
        </section>

        <section class="teacher-dash-agenda" aria-labelledby="teacher-dash-agenda-title">
          <div class="section-header">
            <h2 id="teacher-dash-agenda-title" class="section-title">{{ t('teacherDashboard.sessionsTitle') }}</h2>
            <RouterLink class="btn btn-link" :to="{ name: 'teacher-live-sessions' }">
              {{ t('teacherDashboard.allSessions') }}<IconGlyph name="chevronRight" :size="16" />
            </RouterLink>
          </div>
          <div v-if="!dashboard.upcomingSessions.length" class="teacher-dash-empty">
            <p class="type-caption">{{ t('teacherDashboard.sessionsEmpty') }}</p>
            <RouterLink class="btn btn-secondary btn-sm" :to="{ name: 'teacher-live-sessions' }">
              {{ t('teacherDashboard.schedule') }}
            </RouterLink>
          </div>
          <ol v-else class="teacher-agenda">
            <li v-for="day in days" :key="day.key">
              <h3 class="teacher-agenda-date">
                <span>{{ dayHeading(day.at) }}</span>
                <span v-if="relativeDay(day.at)" class="type-caption">{{ formatWeekdayDate(day.at) }}</span>
              </h3>
              <ul class="teacher-agenda-rows">
                <li v-for="session in day.items" :key="session.id">
                  <RouterLink
                    class="teacher-agenda-row"
                    :class="{ 'is-next': session.id === nextSessionId }"
                    :to="{ name: 'teacher-live-sessions', query: { session: session.id } }"
                    :aria-label="t('teacherDashboard.openSession', { title: session.title })"
                  >
                    <span class="teacher-agenda-time">
                      <strong>{{ formatTime(session.startsAt) }}</strong>
                      <span>{{ formatTime(session.endsAt) }}</span>
                    </span>
                    <span class="teacher-agenda-copy">
                      <span class="teacher-agenda-title">
                        <strong>{{ session.title }}</strong>
                        <span v-if="session.id === nextSessionId" class="teacher-next-tag">
                          {{ t('teacherDashboard.next') }}
                        </span>
                      </span>
                      <span class="teacher-agenda-booking">
                        <span v-if="session.capacity" class="teacher-meter" aria-hidden="true">
                          <span :style="{ width: `${bookedPercent(session.bookedCount, session.capacity)}%` }" />
                        </span>
                        <span class="type-caption">{{ bookedLine(session) }}</span>
                      </span>
                      <StatusPill
                        v-if="session.status === 'RESCHEDULED' || session.status === 'FULL'"
                        :label="t(SESSION_STATUS_KEYS[session.status])"
                        :tone="SESSION_STATUS_TONES[session.status]"
                      />
                    </span>
                    <IconGlyph name="chevronRight" :size="18" class="teacher-agenda-chevron" />
                  </RouterLink>
                </li>
              </ul>
            </li>
          </ol>
        </section>

        <section class="teacher-dash-support" aria-labelledby="teacher-dash-support-title">
          <div class="section-header">
            <h2 id="teacher-dash-support-title" class="section-title">{{ t('teacherDashboard.supportTitle') }}</h2>
            <RouterLink class="btn btn-link" :to="{ name: 'teacher-students' }">
              {{ t('teacherDashboard.allStudents') }}<IconGlyph name="chevronRight" :size="16" />
            </RouterLink>
          </div>
          <p v-if="!dashboard.studentsNeedingSupport.length" class="teacher-dash-empty type-caption">
            {{ t('teacherDashboard.supportEmpty') }}
          </p>
          <ul v-else class="teacher-dash-people">
            <li v-for="student in dashboard.studentsNeedingSupport" :key="student.studentId">
              <RouterLink
                class="teacher-dash-person"
                :to="{ name: 'teacher-students', query: { student: student.studentId } }"
                :aria-label="t('teacherDashboard.openStudent', { name: student.name })"
              >
                <PortraitAvatar :size="40" :name="student.name" tone="warning" />
                <span class="teacher-dash-person-copy">
                  <strong>{{ student.name }}</strong>
                  <span class="type-caption">{{ supportFacts(student) }}</span>
                </span>
                <IconGlyph name="chevronRight" :size="18" />
              </RouterLink>
            </li>
          </ul>
        </section>
      </div>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

import { teacherClassesApi } from '@/api/classes'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classesMessages } from '@/plugins/classesMessages'
import { teacherDashboardMessages } from '@/plugins/teacherDashboardMessages'
import type { RosterRow, TeacherSession, TodoItem } from '@/types/classes'
import { SESSION_STATUS_KEYS, SESSION_STATUS_TONES, bookedPercent, groupByDay, relativeDay } from '@/utils/classes'
import { formatDate, formatDateTime, formatShortDate, formatTime, formatWeekdayDate } from '@/utils/format'
import '@/styles/classes.css'

/**
 * The approved teacher's desk for today: what needs them (changes requested first, then what waits on review), the
 * live sessions coming up as a timetable, the key numbers and the students who need support.
 */
const { t, locale } = useLocalMessages({ teacherDashboard: teacherDashboardMessages, classes: classesMessages })

const resource = useResource(() => teacherClassesApi.dashboard())
const dashboard = computed(() => resource.data.value)

// The next live session is the first row of the timetable, so it is not repeated in the to-do list.
const queue = computed(() => dashboard.value?.todo.filter((item) => item.kind !== 'NEXT_LIVE_SESSION') ?? [])
const needsYou = computed(() => queue.value.filter((item) => item.needsYou))
const watching = computed(() => queue.value.filter((item) => !item.needsYou))
const groups = computed(() =>
  [
    { key: 'needs', label: t('teacherDashboard.groupNeedsYou'), items: needsYou.value },
    { key: 'waiting', label: t('teacherDashboard.groupWaiting'), items: watching.value },
  ].filter((group) => group.items.length),
)

const days = computed(() => groupByDay(dashboard.value?.upcomingSessions ?? []))
const nextSessionId = computed(
  () =>
    dashboard.value?.todo.find((item) => item.kind === 'NEXT_LIVE_SESSION')?.sessionId ??
    dashboard.value?.upcomingSessions[0]?.id ??
    null,
)

const todayLabel = computed(() =>
  formatDate(new Date(), { weekday: 'long', month: 'long', day: 'numeric' }, locale.value),
)

const summary = computed(() => {
  const current = dashboard.value
  if (!current) return ''
  const parts = [t('teacherDashboard.summaryNeeds', needsYou.value.length)]
  const next = current.upcomingSessions.find((session) => session.id === nextSessionId.value)
  if (next) {
    const day = relativeDay(next.startsAt)
    parts.push(
      t('teacherDashboard.summaryNext', {
        day:
          day === 'today'
            ? t('teacherDashboard.when.today')
            : day === 'tomorrow'
              ? t('teacherDashboard.when.tomorrow')
              : t('teacherDashboard.when.on', { date: formatWeekdayDate(next.startsAt) }),
        time: formatTime(next.startsAt),
      }),
    )
  }
  return parts.join(' · ')
})

const TODO_KEYS: Record<string, string> = {
  LESSON_CHANGES_REQUESTED: 'teacherDashboard.kinds.LESSON_CHANGES_REQUESTED',
  REVISION_CHANGES_REQUESTED: 'teacherDashboard.kinds.REVISION_CHANGES_REQUESTED',
  CLASS_CHANGES_REQUESTED: 'teacherDashboard.kinds.CLASS_CHANGES_REQUESTED',
  DETAILS_CHANGES_REQUESTED: 'teacherDashboard.kinds.DETAILS_CHANGES_REQUESTED',
  REVISION_IN_REVIEW: 'teacherDashboard.kinds.REVISION_IN_REVIEW',
  LESSON_IN_REVIEW: 'teacherDashboard.kinds.LESSON_IN_REVIEW',
  CLASS_IN_REVIEW: 'teacherDashboard.kinds.CLASS_IN_REVIEW',
  DETAILS_IN_REVIEW: 'teacherDashboard.kinds.DETAILS_IN_REVIEW',
}

function waitingDetail(item: TodoItem): string {
  const date = formatDateTime(item.at)
  switch (item.kind) {
    case 'REVISION_IN_REVIEW':
      return t('teacherDashboard.submittedRevision', { date })
    case 'LESSON_IN_REVIEW':
      return t('teacherDashboard.submittedLesson', { date })
    case 'CLASS_IN_REVIEW':
      return item.detail
        ? t('teacherDashboard.submittedClass', { date, lesson: item.detail })
        : t('teacherDashboard.submittedClassNoLesson', { date })
    case 'DETAILS_IN_REVIEW':
      return t('teacherDashboard.submittedDetails', { date })
    default:
      return ''
  }
}

function todoRoute(item: TodoItem): RouteLocationRaw | null {
  if (!item.classId) return null
  if (item.lessonId) return { name: 'teacher-lesson', params: { classId: item.classId, lessonId: item.lessonId } }
  return item.kind.startsWith('DETAILS')
    ? { name: 'teacher-class', params: { classId: item.classId, tab: 'details' } }
    : { name: 'teacher-class', params: { classId: item.classId } }
}

function dayHeading(at: string): string {
  const day = relativeDay(at)
  if (day === 'today') return t('teacherDashboard.dayToday')
  if (day === 'tomorrow') return t('teacherDashboard.dayTomorrow')
  return formatWeekdayDate(at)
}

function bookedLine(session: TeacherSession): string {
  return session.capacity
    ? t('teacherDashboard.booked', { booked: session.bookedCount, capacity: session.capacity })
    : t('teacherDashboard.bookedOpen', { booked: session.bookedCount })
}

function supportFacts(student: RosterRow): string {
  const date = student.lastActiveAt ? formatShortDate(student.lastActiveAt) : t('teacherDashboard.noActivity')
  return student.quizAverage !== null
    ? t('teacherDashboard.supportFacts', {
        progress: student.progressPercent,
        average: `${student.quizAverage}%`,
        date,
      })
    : t('teacherDashboard.supportFactsNoQuiz', { progress: student.progressPercent, date })
}
</script>
