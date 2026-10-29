<template>
  <div class="student-class-page page-stack">
    <template v-if="page">
      <!-- The teacher leads: their face and name, then the class they made, then what to do next. -->
      <header class="student-class-hero">
        <AppBreadcrumb
          :items="[{ label: t('classes.classes'), to: { name: 'student-classes' } }, { label: page.summary.title }]"
        />
        <div class="student-class-hero-grid">
          <div class="student-class-cover">
            <div class="student-class-teacher">
              <PortraitAvatar :size="96" :name="page.summary.teacher.name" />
              <span v-if="page.summary.teacher.verified" class="verified-mark">
                <IconGlyph name="verified" :size="14" />{{ t('classes.verified') }}
              </span>
            </div>
            <p class="student-class-facts">
              {{ teacherFacts.before }}<strong>{{ page.summary.teacher.name }}</strong
              >{{ teacherFacts.after }}
            </p>
            <h1 class="student-class-title">{{ page.summary.title }}</h1>
            <p v-if="page.summary.description" class="student-class-about">{{ page.summary.description }}</p>
            <ul v-if="labels.length" class="student-class-labels" :aria-label="t('studentClasses.classPage.about')">
              <li v-for="label in labels" :key="label" class="tag">{{ label }}</li>
            </ul>
          </div>

          <!-- Not joined yet: free to join. -->
          <section v-if="!page.joined" class="student-class-aside" aria-labelledby="class-join-title">
            <h2 id="class-join-title" class="page-eyebrow">
              {{ t('studentClasses.classPage.preview.title') }}
            </h2>
            <StatusPill :label="t('studentClasses.classPage.preview.free')" tone="success" icon="check" />
            <p class="student-class-aside-text">{{ t('studentClasses.classPage.preview.joinText') }}</p>
            <button
              type="button"
              class="btn btn-primary btn-block"
              :disabled="joining"
              :aria-busy="joining"
              data-testid="join-class"
              @click="join"
            >
              {{ t('studentClasses.classPage.preview.join') }}
            </button>
          </section>

          <!-- Joined: the teacher's note and the next step. -->
          <section v-else class="student-class-aside" aria-labelledby="class-next-title">
            <figure v-if="page.teacherNote" class="student-class-note">
              <blockquote class="student-class-note-quote">{{ page.teacherNote }}</blockquote>
              <figcaption class="student-class-note-by">
                <PortraitAvatar :size="24" :name="page.summary.teacher.name" />
                {{ t('studentClasses.classPage.noteFrom', { teacher: page.summary.teacher.name }) }}
              </figcaption>
            </figure>
            <h2 id="class-next-title" class="page-eyebrow">
              {{ t('studentClasses.classPage.nextStep') }}
            </h2>
            <p class="student-class-aside-text is-strong">
              {{ page.nextStep ? nextStepText : t('studentClasses.classPage.allDone') }}
            </p>
            <div class="student-class-progress">
              <ProgressBar :progress="page.progressPercent" :label="t('studentClasses.classPage.classProgress')" />
              <span class="type-caption">
                {{
                  t('studentClasses.classPage.progressText', {
                    percent: page.progressPercent,
                    done: page.completedLessons,
                    total: page.lessonCount,
                  })
                }}
              </span>
            </div>
            <RouterLink
              v-if="page.nextStep"
              class="btn btn-primary btn-block"
              :to="lessonRoute(page.nextStep.lessonId)"
              data-testid="continue-class"
            >
              {{ t('studentClasses.classPage.continue', { title: page.nextStep.lessonTitle }) }}
              <IconGlyph name="chevronRight" :size="16" />
            </RouterLink>
          </section>
        </div>
      </header>

      <DataNotice :error="actionError" :success="actionSuccess" />
      <DataNotice v-if="page.archived" :info="t('studentClasses.classPage.archived')" />

      <!-- Not joined yet: the course, locked, and the timetable. -->
      <div v-if="!page.joined" class="student-class-body">
        <div class="student-class-course">
          <p class="student-class-course-hint">
            <IconGlyph name="lock" :size="16" />{{ t('studentClasses.classPage.preview.lessonsHint') }}
          </p>
          <section
            v-for="(unit, unitIndex) in page.preview"
            :key="unit.id"
            class="student-class-unit"
            :aria-labelledby="`class-unit-${unit.id}`"
          >
            <div class="student-class-unit-head">
              <h2 :id="`class-unit-${unit.id}`" class="student-class-unit-title">
                {{ t('classes.unitLabel', { number: unitIndex + 1, title: unit.title }) }}
              </h2>
              <span class="type-caption">{{ t('classes.lessons', unit.lessons.length) }}</span>
            </div>
            <ol class="student-class-lessons">
              <li v-for="(lesson, lessonIndex) in unit.lessons" :key="lesson.id">
                <div class="student-class-lesson is-locked">
                  <span class="student-class-lesson-mark" aria-hidden="true">{{ lessonIndex + 1 }}</span>
                  <span class="student-class-lesson-copy">
                    <strong>{{ lesson.title }}</strong>
                    <span class="type-caption">{{ t('classes.activities', lesson.activityCount) }}</span>
                  </span>
                  <IconGlyph name="lock" :size="16" />
                </div>
              </li>
            </ol>
          </section>
        </div>
        <aside class="student-class-side">
          <ClassSessionsPanel :sessions="page.liveSessions" :title="t('studentClasses.classPage.sessionsTitle')" />
        </aside>
      </div>

      <template v-else>
        <RouteTabs :tabs="tabs" :label="t('studentClasses.classPage.tabsLabel')" />

        <!-- Lessons: the course, unit by unit, and the timetable. -->
        <div v-if="tab === 'lessons'" class="student-class-body">
          <div class="student-class-course">
            <section
              v-for="(unit, unitIndex) in page.units"
              :key="unit.id"
              class="student-class-unit"
              :aria-labelledby="`class-unit-${unit.id}`"
            >
              <div class="student-class-unit-head">
                <h2 :id="`class-unit-${unit.id}`" class="student-class-unit-title">
                  {{ t('classes.unitLabel', { number: unitIndex + 1, title: unit.title }) }}
                </h2>
                <span class="type-caption">
                  {{
                    t('studentClasses.classPage.unitFacts', {
                      lessons: t('classes.lessons', unit.lessonCount),
                      done: unit.completedCount,
                    })
                  }}
                </span>
              </div>
              <ol class="student-class-lessons">
                <li v-for="lesson in unit.lessons" :key="lesson.id">
                  <RouterLink
                    v-if="lesson.available"
                    class="student-class-lesson"
                    :class="`is-${lessonMark(lesson)}`"
                    :to="lessonRoute(lesson.id)"
                    :aria-label="t('studentClasses.classPage.openLesson', { title: lesson.title })"
                  >
                    <span class="student-class-lesson-mark" aria-hidden="true">
                      <IconGlyph v-if="lessonMark(lesson) === 'done'" name="check" :size="16" />
                      <template v-else>{{ lesson.number }}</template>
                    </span>
                    <span class="student-class-lesson-copy">
                      <strong>{{ lesson.title }}</strong>
                      <span class="type-caption">{{ lessonCaption(lesson) }}</span>
                    </span>
                    <span class="student-class-lesson-state">{{ lessonState(lesson) }}</span>
                    <IconGlyph class="student-class-lesson-chevron" name="chevronRight" :size="18" />
                  </RouterLink>
                  <div v-else class="student-class-lesson is-unavailable">
                    <span class="student-class-lesson-mark" aria-hidden="true">
                      <IconGlyph name="archive" :size="14" />
                    </span>
                    <span class="student-class-lesson-copy">
                      <strong>{{ lesson.title }}</strong>
                      <span class="type-caption">{{ t('studentClasses.lessonState.unavailable') }}</span>
                    </span>
                  </div>
                </li>
              </ol>
            </section>
          </div>
          <aside class="student-class-side">
            <ClassSessionsPanel :sessions="page.liveSessions" :title="t('studentClasses.classPage.sessionsTitle')" />
          </aside>
        </div>

        <!-- Quiz results -->
        <section v-else-if="tab === 'results'" class="student-class-sheet" aria-labelledby="class-results-title">
          <StatePanel
            v-if="results.loading.value"
            tone="loading"
            :title="t('classes.loading')"
            :message="t('studentClasses.classPage.tabResults')"
          />
          <StatePanel
            v-else-if="results.error.value"
            tone="error"
            icon="alert"
            :title="t('studentClasses.classPage.results.loadError')"
            :message="results.error.value"
          >
            <template #actions>
              <button class="btn btn-primary" type="button" @click="results.reload">{{ t('classes.retry') }}</button>
            </template>
          </StatePanel>
          <template v-else-if="results.data.value?.attempts.length">
            <div class="section-header">
              <div>
                <!-- The tab already names the view; the heading stays for screen readers. -->
                <h2 id="class-results-title" class="sr-only">{{ t('studentClasses.classPage.tabResults') }}</h2>
                <p class="section-description">
                  {{
                    results.data.value.averagePercent !== null
                      ? t('studentClasses.classPage.results.average', { percent: results.data.value.averagePercent })
                      : t('studentClasses.classPage.results.noAverage')
                  }}
                </p>
              </div>
            </div>
            <div
              class="data-table-wrap"
              role="region"
              :aria-label="t('studentClasses.classPage.results.tableLabel')"
              tabindex="0"
            >
              <table class="data-table">
                <thead>
                  <tr>
                    <th scope="col">{{ t('studentClasses.classPage.results.quiz') }}</th>
                    <th scope="col" class="col-numeric">{{ t('studentClasses.classPage.results.date') }}</th>
                    <th scope="col" class="col-numeric">
                      {{ t('studentClasses.classPage.results.marks') }}
                    </th>
                    <th scope="col" class="col-numeric">{{ t('studentClasses.classPage.results.score') }}</th>
                    <th scope="col" class="col-actions">
                      <span class="sr-only">{{ t('studentClasses.classPage.results.view') }}</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="attempt in results.data.value.attempts" :key="attempt.attemptId">
                    <td>
                      <!-- The lesson only when its name says more than the quiz's. -->
                      <span class="student-class-results-quiz">
                        <span>{{ attempt.quizTitle }}</span>
                        <span
                          v-if="attempt.lessonTitle && attempt.lessonTitle !== attempt.quizTitle"
                          class="type-caption"
                        >
                          {{ attempt.lessonTitle }}
                        </span>
                      </span>
                    </td>
                    <td class="col-numeric student-class-results-date">{{ formatShortDate(attempt.completedAt) }}</td>
                    <td class="col-numeric">{{ attempt.score }} / {{ attempt.maxScore }}</td>
                    <td class="col-numeric">
                      <strong>{{ attempt.percent }}%</strong>
                    </td>
                    <td class="col-actions">
                      <RouterLink
                        class="btn btn-link"
                        :to="{
                          name: 'student-quiz',
                          params: { quizId: attempt.quizId },
                          query: { attempt: attempt.attemptId },
                        }"
                        :aria-label="
                          t('studentClasses.classPage.results.viewLabel', {
                            quiz: attempt.quizTitle,
                            date: formatShortDate(attempt.completedAt),
                          })
                        "
                      >
                        {{ t('studentClasses.classPage.results.view') }}
                      </RouterLink>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </template>
          <StatePanel
            v-else
            tone="empty"
            icon="quiz"
            :title="t('studentClasses.classPage.results.emptyTitle')"
            :message="t('studentClasses.classPage.results.emptyMessage')"
          />
        </section>

        <!-- Feedback: notes from the teacher, newest first. -->
        <section v-else class="student-class-sheet" aria-labelledby="class-feedback-title">
          <StatePanel
            v-if="feedback.loading.value"
            tone="loading"
            :title="t('classes.loading')"
            :message="t('studentClasses.classPage.tabFeedback')"
          />
          <StatePanel
            v-else-if="feedback.error.value"
            tone="error"
            icon="alert"
            :title="t('studentClasses.classPage.feedback.loadError')"
            :message="feedback.error.value"
          >
            <template #actions>
              <button class="btn btn-primary" type="button" @click="feedback.reload">{{ t('classes.retry') }}</button>
            </template>
          </StatePanel>
          <template v-else-if="feedback.data.value?.length">
            <h2 id="class-feedback-title" class="sr-only">{{ t('studentClasses.classPage.tabFeedback') }}</h2>
            <ul class="student-class-feedback">
              <li v-for="item in feedback.data.value" :key="item.id" class="student-class-feedback-item">
                <PortraitAvatar :size="40" :name="item.teacherName" />
                <div class="student-class-feedback-body">
                  <div class="student-class-feedback-head">
                    <strong>{{
                      t(FEEDBACK_ABOUT_KEYS_STUDENT[item.about], { lesson: item.lessonTitle ?? '' })
                    }}</strong>
                    <span class="tag">{{ t(FEEDBACK_TYPE_KEYS[item.type]) }}</span>
                  </div>
                  <p class="student-class-feedback-message">{{ item.message }}</p>
                  <span class="type-caption">
                    {{
                      t('studentClasses.classPage.feedback.from', {
                        teacher: item.teacherName,
                        date: formatDateTime(item.createdAt),
                      })
                    }}
                    <template v-if="item.editedAt"> · {{ t('studentClasses.classPage.feedback.edited') }}</template>
                  </span>
                </div>
              </li>
            </ul>
          </template>
          <StatePanel
            v-else
            tone="empty"
            icon="chat"
            :title="t('studentClasses.classPage.feedback.emptyTitle')"
            :message="t('studentClasses.classPage.feedback.emptyMessage')"
          />
        </section>
      </template>
    </template>

    <template v-else>
      <h1 class="sr-only">{{ t('classes.classes') }}</h1>
      <StatePanel
        v-if="resource.loading.value"
        tone="loading"
        spacious
        :title="t('classes.loading')"
        :message="t('studentClasses.mine.title')"
      />
      <StatePanel
        v-else
        tone="error"
        icon="alert"
        spacious
        :title="t('studentClasses.classPage.loadErrorTitle')"
        :message="resource.error.value ?? ''"
      >
        <template #actions>
          <button class="btn btn-secondary" type="button" @click="resource.reload">{{ t('classes.retry') }}</button>
          <RouterLink class="btn btn-primary" :to="{ name: 'student-classes' }">
            {{ t('studentClasses.classPage.backToClasses') }}
          </RouterLink>
        </template>
      </StatePanel>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { studentClassesApi } from '@/api/classes'
import AppBreadcrumb from '@/components/common/AppBreadcrumb.vue'
import ClassSessionsPanel from '@/components/classes/ClassSessionsPanel.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import RouteTabs, { type RouteTab } from '@/components/common/RouteTabs.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classesMessages } from '@/plugins/classesMessages'
import { studentClassesMessages } from '@/plugins/studentClassesMessages'
import type { FeedbackAbout, LessonProgressRow } from '@/types/classes'
import { FEEDBACK_TYPE_KEYS, LEARNING_TYPE_KEYS } from '@/utils/classes'
import { formatDateTime, formatShortDate } from '@/utils/format'
import '@/styles/classes.css'

const { t } = useLocalMessages({ studentClasses: studentClassesMessages, classes: classesMessages })
const route = useRoute()
const { actionError, actionSuccess, runAction } = useActionFeedback()

const FEEDBACK_ABOUT_KEYS_STUDENT: Record<FeedbackAbout, string> = {
  QUIZ_ATTEMPT: 'studentClasses.classPage.feedback.about.QUIZ_ATTEMPT',
  LESSON: 'studentClasses.classPage.feedback.about.LESSON',
  GENERAL: 'studentClasses.classPage.feedback.about.GENERAL',
}

const LESSON_STATE_KEYS = {
  IN_PROGRESS: 'studentClasses.lessonState.IN_PROGRESS',
  COMPLETED: 'studentClasses.lessonState.COMPLETED',
  MASTERED: 'studentClasses.lessonState.MASTERED',
  NOT_STARTED: 'studentClasses.lessonState.NOT_STARTED',
} as const

const classId = computed(() => Number(route.params.classId))
const tab = computed(() =>
  route.params.tab === 'results' || route.params.tab === 'feedback' ? route.params.tab : 'lessons',
)

const resource = useResource(() => studentClassesApi.classPage(classId.value))
const page = computed(() => resource.data.value)
const results = useResource(() => studentClassesApi.quizResults(classId.value), false)
const feedback = useResource(() => studentClassesApi.feedback(classId.value), false)

watch(classId, () => void resource.reload())
watch(
  [tab, () => page.value?.joined],
  ([current, joined]) => {
    if (!joined) return
    if (current === 'results') void results.reload()
    if (current === 'feedback') void feedback.reload()
  },
  { immediate: true },
)

const tabs = computed<RouteTab[]>(() => [
  {
    label: t('studentClasses.classPage.tabLessons'),
    to: { name: 'student-class', params: { classId: classId.value } },
    icon: 'learn',
    count: page.value?.lessonCount ?? null,
  },
  {
    label: t('studentClasses.classPage.tabResults'),
    to: { name: 'student-class', params: { classId: classId.value, tab: 'results' } },
    icon: 'quiz',
    count: page.value?.quizAttemptCount ?? null,
  },
  {
    label: t('studentClasses.classPage.tabFeedback'),
    to: { name: 'student-class', params: { classId: classId.value, tab: 'feedback' } },
    icon: 'chat',
    count: page.value?.feedbackCount ?? null,
  },
])

/** An invisible mark that stands in for the teacher's name, so the name can be set in bold in any language. */
const NAME_MARK = '⁣'

/** "Teacher Li Wei · 2 units · 5 lessons", split around the name. */
const teacherFacts = computed(() => {
  const summary = page.value?.summary
  if (!summary) return { before: '', after: '' }
  const [before = '', after = ''] = t('studentClasses.classPage.teacherFacts', {
    teacher: NAME_MARK,
    units: t('classes.units', summary.unitCount),
    lessons: t('classes.lessons', summary.lessonCount),
  }).split(NAME_MARK)
  return { before, after }
})

const labels = computed(() => {
  const summary = page.value?.summary
  if (!summary) return []
  return [
    summary.levelName,
    summary.topicName,
    summary.learningType ? t(LEARNING_TYPE_KEYS[summary.learningType]) : null,
  ].filter((label): label is string => Boolean(label))
})

const nextStepText = computed(() => {
  const next = page.value?.nextStep
  if (!next) return ''
  const allActivitiesDone = next.activityCount > 0 && next.activitiesDone >= next.activityCount
  return allActivitiesDone && !next.quizDone
    ? t('studentClasses.classPage.nextStepQuiz', { lesson: next.lessonTitle, number: next.lessonNumber })
    : t('studentClasses.classPage.nextStepText', {
        lesson: next.lessonTitle,
        number: next.lessonNumber,
        done: next.activitiesDone,
        total: next.activityCount,
      })
})

/** The marker of a lesson row: finished, the one to work on now, or still to come. */
function lessonMark(lesson: LessonProgressRow): 'done' | 'current' | 'todo' {
  if (lesson.status === 'COMPLETED' || lesson.status === 'MASTERED') return 'done'
  if (lesson.status === 'IN_PROGRESS' || page.value?.nextStep?.lessonId === lesson.id) return 'current'
  return 'todo'
}

function lessonState(lesson: LessonProgressRow): string {
  if (lesson.status === 'NOT_STARTED' && page.value?.nextStep?.lessonId === lesson.id) {
    return t('studentClasses.lessonState.upNext')
  }
  return t(LESSON_STATE_KEYS[lesson.status], { percent: lesson.percent })
}

function lessonCaption(lesson: LessonProgressRow): string {
  const parts = [t('classes.activities', lesson.activityCount)]
  if (lesson.bestQuizPercent !== null) {
    parts.push(t('studentClasses.classPage.bestQuiz', { percent: lesson.bestQuizPercent }))
  }
  return parts.join(' · ')
}

function lessonRoute(lessonId: number) {
  return { name: 'student-class-lesson', params: { classId: classId.value, lessonId } }
}

const joining = ref(false)

async function join(): Promise<void> {
  joining.value = true
  await runAction(t('studentClasses.classPage.preview.joinError'), async () => {
    resource.data.value = await studentClassesApi.join(classId.value)
    return t('studentClasses.classPage.preview.joined', { title: resource.data.value.summary.title })
  })
  joining.value = false
}
</script>
