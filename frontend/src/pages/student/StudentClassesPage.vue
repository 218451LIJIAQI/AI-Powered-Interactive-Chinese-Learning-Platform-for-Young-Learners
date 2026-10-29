<template>
  <div class="student-classes-page page-stack">
    <PageHeaderBlock :title="t('studentClasses.mine.title')" :description="t('studentClasses.mine.lede')" />
    <StudentClassesTabs :overview="overview" :load-counts="false" />

    <ResourceSection
      class="page-stack"
      :loading="resource.loading.value"
      :error="resource.error.value"
      @retry="resource.reload"
    >
      <template v-if="overview">
        <!-- Today's live session, set like a line of a timetable. -->
        <section
          v-if="overview.liveToday"
          class="student-classes-today"
          :class="{ 'is-booked': overview.liveToday.booked }"
          aria-labelledby="live-today-title"
          data-testid="live-today"
        >
          <span class="student-classes-today-when">
            <span class="page-eyebrow student-classes-pigment">{{ t('studentClasses.mine.liveToday') }}</span>
            <strong class="student-classes-today-time">{{ formatTime(overview.liveToday.startsAt) }}</strong>
          </span>
          <div class="student-classes-today-copy">
            <h2 id="live-today-title" class="student-classes-today-title">{{ overview.liveToday.title }}</h2>
            <p class="student-classes-today-facts">
              <PortraitAvatar :size="24" :name="overview.liveToday.teacherName" />
              {{
                t('studentClasses.mine.liveTodayFacts', {
                  teacher: overview.liveToday.teacherName,
                  classTitle: overview.liveToday.classTitle,
                  platform: t(PLATFORM_KEYS[overview.liveToday.platform]),
                })
              }}
            </p>
          </div>
          <span class="student-classes-today-state">
            <StatusPill
              v-if="overview.liveToday.booked"
              :label="t('studentClasses.mine.youreBooked')"
              tone="success"
              icon="check"
            />
            <span v-if="overview.liveToday.booked" class="type-caption">
              {{ t('studentClasses.mine.joinOpens', { time: formatTime(joinOpens(overview.liveToday.startsAt)) }) }}
            </span>
          </span>
          <RouterLink
            class="btn btn-sm"
            :class="overview.liveToday.booked ? 'btn-secondary' : 'btn-primary'"
            :to="{ name: 'student-live-sessions' }"
          >
            {{ overview.liveToday.booked ? t('studentClasses.mine.details') : t('studentClasses.mine.bookASeat') }}
          </RouterLink>
        </section>

        <StatePanel
          v-if="!overview.classes.length"
          tone="empty"
          icon="classes"
          spacious
          :title="t('studentClasses.mine.emptyTitle')"
          :message="t('studentClasses.mine.emptyMessage')"
        >
          <template #actions>
            <RouterLink class="btn btn-primary" :to="{ name: 'student-classes-find' }">
              {{ t('classCatalogue.findClasses') }}
            </RouterLink>
          </template>
        </StatePanel>

        <!-- Each class reads like a course cover: its teacher, its title, then the course and the next step. -->
        <article
          v-for="entry in overview.classes"
          :key="entry.summary.id"
          class="student-classes-card"
          :aria-labelledby="`my-class-title-${entry.summary.id}`"
          :data-testid="`my-class-${entry.summary.id}`"
        >
          <div class="student-classes-byline">
            <PortraitAvatar :size="56" :name="entry.summary.teacher.name" />
            <span class="student-classes-byline-copy">
              <span class="student-classes-byline-label">{{ t('studentClasses.mine.yourTeacher') }}</span>
              <strong class="student-classes-teacher">{{ entry.summary.teacher.name }}</strong>
              <span v-if="entry.summary.teacher.verified" class="verified-mark">
                <IconGlyph name="verified" :size="14" />{{ t('classes.verified') }}
              </span>
            </span>
          </div>

          <div class="student-classes-heading">
            <h2 :id="`my-class-title-${entry.summary.id}`" class="student-classes-title">{{ entry.summary.title }}</h2>
            <p class="type-caption">
              {{
                t('studentClasses.mine.facts', {
                  units: t('classes.units', entry.summary.unitCount),
                  lessons: t('classes.lessons', entry.summary.lessonCount),
                  date: formatShortDate(entry.joinedAt),
                })
              }}
            </p>
            <div v-if="labels(entry).length" class="student-classes-labels">
              <span v-for="label in labels(entry)" :key="label" class="tag">{{ label }}</span>
            </div>
            <p v-if="entry.archived" class="callout is-warning is-compact">
              <IconGlyph name="archive" :size="16" /><span>{{ t('studentClasses.mine.archived') }}</span>
            </p>
          </div>

          <div class="student-classes-course">
            <div class="student-classes-progress">
              <span class="page-eyebrow">{{ t('studentClasses.mine.course') }}</span>
              <span class="type-caption">
                {{
                  t('studentClasses.mine.lessonsDone', {
                    done: entry.completedLessons,
                    total: entry.summary.lessonCount,
                    percent: entry.progressPercent,
                  })
                }}
              </span>
              <ProgressBar :progress="entry.progressPercent" :label="t('studentClasses.classPage.classProgress')" />
            </div>
            <ol class="student-classes-lessons">
              <li
                v-for="lesson in visibleLessons(entry)"
                :key="lesson.id"
                class="student-classes-lesson"
                :class="`is-${lessonMark(entry, lesson)}`"
              >
                <span class="student-classes-lesson-mark" aria-hidden="true">
                  <IconGlyph v-if="lessonMark(entry, lesson) === 'done'" name="check" :size="14" />
                  <template v-else>{{ lessonPosition(entry, lesson) }}</template>
                </span>
                <RouterLink
                  v-if="lesson.available"
                  class="student-classes-lesson-title"
                  :to="lessonRoute(entry.summary.id, lesson.id)"
                >
                  {{ lesson.title }}
                </RouterLink>
                <span v-else class="student-classes-lesson-title">{{ lesson.title }}</span>
                <span class="student-classes-lesson-state">{{ lessonState(entry, lesson) }}</span>
              </li>
            </ol>
            <p v-if="entry.lessons.length > visibleLessons(entry).length" class="type-caption">
              {{ t('studentClasses.mine.moreLessons', { count: entry.lessons.length - visibleLessons(entry).length }) }}
            </p>
          </div>

          <div class="student-classes-actions">
            <RouterLink
              v-if="entry.nextStep"
              class="btn btn-primary"
              :to="lessonRoute(entry.summary.id, entry.nextStep.lessonId)"
            >
              {{ t('studentClasses.mine.continue', { title: entry.nextStep.lessonTitle }) }}
            </RouterLink>
            <RouterLink
              class="btn"
              :class="entry.nextStep ? 'btn-secondary' : 'btn-primary'"
              :to="{ name: 'student-class', params: { classId: entry.summary.id } }"
            >
              {{ t('studentClasses.mine.openClass') }}
            </RouterLink>
            <span v-if="entry.nextLiveSession" class="student-classes-next-live">
              <IconGlyph name="live" :size="16" />
              {{ t('studentClasses.mine.nextSession', { when: sessionWhen(entry.nextLiveSession.startsAt) }) }}
            </span>
          </div>
        </article>

        <section v-if="overview.classes.length && overview.moreClassCount" class="student-classes-more">
          <span class="student-classes-more-avatars" aria-hidden="true">
            <PortraitAvatar
              v-for="teacher in overview.moreTeachers.slice(0, 3)"
              :key="teacher.id"
              :size="32"
              :name="teacher.name"
            />
          </span>
          <p class="student-classes-more-copy">{{ t('studentClasses.mine.moreClasses', overview.moreClassCount) }}</p>
          <RouterLink class="btn btn-link" :to="{ name: 'student-classes-find' }">
            {{ t('classCatalogue.findClasses') }}<IconGlyph name="chevronRight" :size="16" />
          </RouterLink>
        </section>
      </template>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import { studentClassesApi } from '@/api/classes'
import StudentClassesTabs from '@/components/classes/StudentClassesTabs.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classCatalogueMessages } from '@/plugins/classCatalogueMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { studentClassesMessages } from '@/plugins/studentClassesMessages'
import type { LessonProgressRow, MyClassCard } from '@/types/classes'
import { LEARNING_TYPE_KEYS, PLATFORM_KEYS, relativeDay } from '@/utils/classes'
import { formatShortDate, formatTime, formatWeekdayDate } from '@/utils/format'
import '@/styles/classes.css'

const { t } = useLocalMessages({
  studentClasses: studentClassesMessages,
  classCatalogue: classCatalogueMessages,
  classes: classesMessages,
})

const resource = useResource(() => studentClassesApi.myClasses())
const overview = computed(() => resource.data.value)

/** The course list shows up to four lessons around where the student is. */
const VISIBLE_LESSONS = 4

const LESSON_STATE_KEYS = {
  IN_PROGRESS: 'studentClasses.lessonState.IN_PROGRESS',
  COMPLETED: 'studentClasses.lessonState.COMPLETED',
  MASTERED: 'studentClasses.lessonState.MASTERED',
  NOT_STARTED: 'studentClasses.lessonState.NOT_STARTED',
} as const

function labels(entry: MyClassCard): string[] {
  const { levelName, topicName, learningType } = entry.summary
  return [levelName, topicName, learningType ? t(LEARNING_TYPE_KEYS[learningType]) : null].filter(
    (label): label is string => Boolean(label),
  )
}

function visibleLessons(entry: MyClassCard): LessonProgressRow[] {
  const nextIndex = entry.nextStep ? entry.lessons.findIndex((lesson) => lesson.id === entry.nextStep?.lessonId) : 0
  const start = Math.max(0, Math.min(nextIndex - 1, entry.lessons.length - VISIBLE_LESSONS))
  return entry.lessons.slice(start, start + VISIBLE_LESSONS)
}

/** "01", "02" …: the lesson's place in the whole class (the card lists lessons of several units in a row). */
function lessonPosition(entry: MyClassCard, lesson: LessonProgressRow): string {
  return String(entry.lessons.indexOf(lesson) + 1).padStart(2, '0')
}

/** The marker of a lesson row: finished, the one to work on now, still to come, or gone. */
function lessonMark(entry: MyClassCard, lesson: LessonProgressRow): 'done' | 'current' | 'todo' | 'unavailable' {
  if (!lesson.available) return 'unavailable'
  if (lesson.status === 'COMPLETED' || lesson.status === 'MASTERED') return 'done'
  if (lesson.status === 'IN_PROGRESS' || entry.nextStep?.lessonId === lesson.id) return 'current'
  return 'todo'
}

function lessonState(entry: MyClassCard, lesson: LessonProgressRow): string {
  if (!lesson.available) return t('studentClasses.lessonState.unavailable')
  if (lesson.status === 'NOT_STARTED' && entry.nextStep?.lessonId === lesson.id) {
    return t('studentClasses.lessonState.upNext')
  }
  return t(LESSON_STATE_KEYS[lesson.status], { percent: lesson.percent })
}

function lessonRoute(classId: number, lessonId: number) {
  return { name: 'student-class-lesson', params: { classId, lessonId } }
}

function joinOpens(startsAt: string): string {
  return new Date(new Date(startsAt).getTime() - 15 * 60_000).toISOString()
}

function sessionWhen(startsAt: string): string {
  return relativeDay(startsAt) === 'today'
    ? formatTime(startsAt)
    : `${formatWeekdayDate(startsAt)}, ${formatTime(startsAt)}`
}
</script>
