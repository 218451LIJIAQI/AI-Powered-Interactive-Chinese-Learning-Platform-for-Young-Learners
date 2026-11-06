<template>
  <div class="page-stack teacher-dash">
    <PageHeaderBlock :title="t('teacherDashboard.title')" hide-title />

    <ResourceSection :loading="resource.loading.value" :error="resource.error.value" @retry="resource.reload">
      <div v-if="dashboard" class="page-stack">
        <section class="panel teacher-card" aria-labelledby="teacher-dash-todo-title">
          <div class="teacher-card-head">
            <h2 id="teacher-dash-todo-title" class="card-title">{{ t('teacherDashboard.todo') }}</h2>
            <p v-if="queue.length" class="type-caption">
              {{ t('teacherDashboard.todoFacts', { needs: needsYou.length, watching: watching.length }) }}
            </p>
          </div>
          <StatePanel
            v-if="!queue.length"
            tone="empty"
            icon="checklist"
            :title="t('teacherDashboard.todoEmptyTitle')"
            :message="t('teacherDashboard.todoEmptyMessage')"
          />
          <ul v-else class="row-list teacher-todo">
            <li v-for="(item, index) in queue" :key="`${item.kind}-${item.classId}-${item.lessonId}-${index}`">
              <div class="row-list-copy">
                <span class="row-list-title">
                  {{ t(TODO_KEYS[item.kind] ?? TODO_KEYS.LESSON_IN_REVIEW!, { title: item.title }) }}
                </span>
                <span class="row-list-meta">{{ todoDetail(item) }}</span>
              </div>
              <div v-if="todoRoute(item)" class="row-list-actions">
                <RouterLink
                  v-if="item.needsYou"
                  class="btn btn-secondary btn-sm"
                  :to="todoRoute(item)!"
                  :aria-label="`${item.lessonId ? t('teacherDashboard.editLesson') : t('teacherDashboard.editClass')}: ${item.title}`"
                >
                  <IconGlyph name="edit" :size="16" />
                  {{ item.lessonId ? t('teacherDashboard.editLesson') : t('teacherDashboard.editClass') }}
                </RouterLink>
                <RouterLink
                  v-else
                  class="btn btn-secondary btn-sm"
                  :to="todoRoute(item)!"
                  :aria-label="t('teacherDashboard.openLabel', { title: item.title })"
                >
                  {{ t('teacherDashboard.open') }}<IconGlyph name="chevronRight" :size="16" />
                </RouterLink>
              </div>
            </li>
          </ul>
        </section>

        <div class="teacher-dash-pair">
          <section class="panel teacher-card" aria-labelledby="teacher-dash-support-title">
            <div class="section-header">
              <h2 id="teacher-dash-support-title" class="card-title">{{ t('teacherDashboard.supportTitle') }}</h2>
              <RouterLink class="btn btn-ghost btn-sm" :to="{ name: 'teacher-students' }">
                {{ t('teacherDashboard.allStudents') }}<IconGlyph name="chevronRight" :size="16" />
              </RouterLink>
            </div>
            <StatePanel
              v-if="!dashboard.studentsNeedingSupport.length"
              tone="empty"
              icon="students"
              :title="t('teacherDashboard.supportEmptyTitle')"
              :message="t('teacherDashboard.supportEmptyMessage')"
            />
            <ul v-else class="row-list">
              <li v-for="student in dashboard.studentsNeedingSupport" :key="student.studentId">
                <RouterLink
                  class="teacher-dash-link"
                  :to="{ name: 'teacher-students', query: { student: student.studentId } }"
                  :aria-label="t('teacherDashboard.openStudent', { name: student.name })"
                >
                  <PortraitAvatar :size="40" :name="student.name" />
                  <span class="row-list-copy">
                    <span class="row-list-title">{{ student.name }}</span>
                    <span class="row-list-meta">{{ supportFacts(student) }}</span>
                  </span>
                  <StatusPill :label="t('teacherDashboard.needsSupport')" tone="strong" />
                  <IconGlyph name="chevronRight" :size="16" class="teacher-dash-chevron" />
                </RouterLink>
              </li>
            </ul>
          </section>

          <section class="panel teacher-card" aria-labelledby="teacher-dash-sessions-title">
            <div class="section-header">
              <h2 id="teacher-dash-sessions-title" class="card-title">{{ t('teacherDashboard.sessionsTitle') }}</h2>
              <RouterLink class="btn btn-ghost btn-sm" :to="{ name: 'teacher-live-sessions' }">
                {{ t('teacherDashboard.allSessions') }}<IconGlyph name="chevronRight" :size="16" />
              </RouterLink>
            </div>
            <StatePanel
              v-if="!dashboard.upcomingSessions.length"
              tone="empty"
              icon="live"
              :title="t('teacherDashboard.sessionsEmptyTitle')"
              :message="t('teacherDashboard.sessionsEmptyMessage')"
            >
              <template #actions>
                <RouterLink class="btn btn-secondary btn-sm" :to="{ name: 'teacher-live-sessions' }">
                  {{ t('teacherDashboard.schedule') }}
                </RouterLink>
              </template>
            </StatePanel>
            <ul v-else class="row-list">
              <li v-for="session in dashboard.upcomingSessions" :key="session.id">
                <RouterLink
                  class="teacher-dash-link"
                  :to="{ name: 'teacher-live-sessions' }"
                  :aria-label="t('teacherDashboard.openSession', { title: session.title })"
                >
                  <SessionDateTile :at="session.startsAt" />
                  <span class="row-list-copy">
                    <span class="row-list-title cjk-text">{{ session.title }}</span>
                    <span class="row-list-meta">{{ sessionWhen(session) }}</span>
                  </span>
                  <IconGlyph name="chevronRight" :size="16" class="teacher-dash-chevron" />
                </RouterLink>
              </li>
            </ul>
          </section>
        </div>
      </div>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

import { teacherClassesApi } from '@/api/classes'
import SessionDateTile from '@/components/classes/SessionDateTile.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { teacherDashboardMessages } from '@/plugins/teacherDashboardMessages'
import type { RosterRow, TodoItem } from '@/types/classes'
import { formatDateTime, formatShortDate } from '@/utils/format'
import { sessionWhen } from '@/utils/teacherSessions'
import '@/styles/classes.css'
import '@/styles/classes-teacher.css'

/** The approved teacher's desk: what needs them or waits on review, then who needs support and what is coming up. */
const { t } = useLocalMessages({ teacherDashboard: teacherDashboardMessages })

const resource = useResource(() => teacherClassesApi.dashboard())
const dashboard = computed(() => resource.data.value)

// The next live session is the first row of Upcoming live sessions, so it is not repeated in To do.
const queue = computed(() => dashboard.value?.todo.filter((item) => item.kind !== 'NEXT_LIVE_SESSION') ?? [])
const needsYou = computed(() => queue.value.filter((item) => item.needsYou))
const watching = computed(() => queue.value.filter((item) => !item.needsYou))

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

/** The admin's note on what needs the teacher; when something else is waiting, when it was submitted. */
function todoDetail(item: TodoItem): string {
  if (item.needsYou && item.note) {
    return item.noteBy
      ? t('teacherDashboard.noteFrom', { name: item.noteBy, date: formatShortDate(item.at), note: item.note })
      : item.note
  }
  return item.at ? t('teacherDashboard.submitted', { date: formatDateTime(item.at) }) : ''
}

function todoRoute(item: TodoItem): RouteLocationRaw | null {
  if (!item.classId) return null
  if (item.lessonId) return { name: 'teacher-lesson', params: { classId: item.classId, lessonId: item.lessonId } }
  return item.kind.startsWith('DETAILS')
    ? { name: 'teacher-class', params: { classId: item.classId, tab: 'details' } }
    : { name: 'teacher-class', params: { classId: item.classId } }
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
