<template>
  <div class="page-stack">
    <PageHeaderBlock :title="t('studentClasses.mine.title')" hide-title />
    <StudentClassesTabs :overview="overview" :load-counts="false" />
    <AppToast :message="toast" />

    <ResourceSection
      class="page-stack"
      :loading="resource.loading.value"
      :error="resource.error.value"
      @retry="resource.reload"
    >
      <template v-if="overview">
        <!-- Today's live session: the one thing on this page with a time on it. -->
        <section
          v-if="overview.liveToday"
          class="student-classes-today"
          aria-labelledby="live-today-title"
          data-testid="live-today"
        >
          <span class="student-classes-today-avatar">
            <PortraitAvatar :size="40" :name="overview.liveToday.teacherName" />
          </span>
          <div class="student-classes-today-copy">
            <p class="page-eyebrow">
              {{ t('studentClasses.mine.liveToday', { time: formatTime(overview.liveToday.startsAt) }) }}
            </p>
            <h2 id="live-today-title" class="student-classes-today-title">
              <CjkText
                :text="
                  t('studentClasses.mine.liveTodayTitle', {
                    title: overview.liveToday.title,
                    teacher: overview.liveToday.teacherName,
                  })
                "
              />
            </h2>
            <p class="student-classes-today-facts">
              {{
                t('studentClasses.mine.liveTodayFacts', {
                  classTitle: overview.liveToday.classTitle,
                  time: formatTime(joinOpens(overview.liveToday.startsAt)),
                })
              }}
            </p>
          </div>
          <RouterLink
            class="btn btn-secondary"
            :to="{ name: 'student-live-sessions' }"
            :aria-label="t('studentClasses.mine.viewLive', { title: overview.liveToday.title })"
          >
            {{ t('studentClasses.mine.view') }}
          </RouterLink>
        </section>

        <div v-if="!overview.classes.length" class="panel student-classes-empty">
          <StatePanel
            tone="empty"
            icon="classes"
            :title="t('studentClasses.mine.emptyTitle')"
            :message="t('studentClasses.mine.emptyMessage')"
          >
            <template #actions>
              <RouterLink class="btn btn-primary" :to="{ name: 'student-classes-find' }">
                {{ t('classCatalogue.findClasses') }}
              </RouterLink>
            </template>
          </StatePanel>
        </div>

        <div v-if="overview.classes.length" class="student-classes-list">
          <article
            v-for="entry in overview.classes"
            :key="entry.summary.id"
            class="student-classes-card"
            :aria-labelledby="`my-class-title-${entry.summary.id}`"
            :data-testid="`my-class-${entry.summary.id}`"
          >
            <div class="student-classes-byline">
              <PortraitAvatar :size="84" :name="entry.summary.teacher.name" />
              <span class="row-list-title">{{ entry.summary.teacher.name }}</span>
            </div>

            <div class="student-classes-copy">
              <h2 :id="`my-class-title-${entry.summary.id}`" class="student-classes-title">
                <CjkText :text="entry.summary.title" />
              </h2>
              <p class="type-caption">
                {{
                  t('studentClasses.mine.facts', {
                    units: t('classes.units', entry.summary.unitCount),
                    lessons: t('classes.lessons', entry.summary.lessonCount),
                  })
                }}
              </p>
              <p v-if="entry.archived" class="type-caption">{{ t('studentClasses.mine.archived') }}</p>
              <div class="student-classes-progress">
                <ProgressBar :progress="entry.progressPercent" :label="t('studentClasses.classPage.classProgress')" />
                <span class="type-caption">{{ progressText(entry) }}</span>
              </div>
            </div>

            <div class="student-classes-actions">
              <RouterLink
                class="btn btn-secondary"
                :to="{ name: 'student-class', params: { classId: entry.summary.id } }"
              >
                {{ t('studentClasses.mine.openClass') }}
              </RouterLink>
              <RouterLink
                v-if="entry.nextStep"
                class="btn btn-primary"
                :to="{
                  name: 'student-class-lesson',
                  params: { classId: entry.summary.id, lessonId: entry.nextStep.lessonId },
                }"
                :aria-label="
                  isNotStarted(entry)
                    ? t('studentClasses.mine.startLessonLabel', {
                        number: entry.nextStep.lessonNumber,
                        title: entry.nextStep.lessonTitle,
                      })
                    : t('studentClasses.mine.continueLessonLabel', { title: entry.nextStep.lessonTitle })
                "
              >
                {{
                  isNotStarted(entry)
                    ? t('studentClasses.mine.startLesson', { number: entry.nextStep.lessonNumber })
                    : t('studentClasses.mine.continueLesson')
                }}
              </RouterLink>
            </div>
          </article>
        </div>
      </template>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import { studentClassesApi } from '@/api/classes'
import StudentClassesTabs from '@/components/classes/StudentClassesTabs.vue'
import AppToast from '@/components/common/AppToast.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import CjkText from '@/components/learning/CjkText.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useClassNotice } from '@/composables/useClassNotice'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classCatalogueMessages } from '@/plugins/classCatalogueMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { studentClassesMessages } from '@/plugins/studentClassesMessages'
import type { MyClassCard } from '@/types/classes'
import { formatTime } from '@/utils/format'
import '@/styles/classes.css'

/** Joining opens this long before a session starts (the server's join window). */
const JOIN_LEAD_MS = 15 * 60_000

const { t } = useLocalMessages({
  studentClasses: studentClassesMessages,
  classCatalogue: classCatalogueMessages,
  classes: classesMessages,
})
const { toast } = useClassNotice()

const resource = useResource(() => studentClassesApi.myClasses())
const overview = computed(() => resource.data.value)

function joinOpens(startsAt: string): string {
  return new Date(new Date(startsAt).getTime() - JOIN_LEAD_MS).toISOString()
}

/** Nothing begun yet in the whole class. */
function isNotStarted(entry: MyClassCard): boolean {
  return entry.lessons.every((lesson) => lesson.status === 'NOT_STARTED')
}

function progressText(entry: MyClassCard): string {
  if (isNotStarted(entry)) return t('studentClasses.mine.notStarted')
  return t('studentClasses.mine.lessonsDone', {
    done: entry.completedLessons,
    total: entry.summary.lessonCount,
    percent: entry.progressPercent,
  })
}
</script>
