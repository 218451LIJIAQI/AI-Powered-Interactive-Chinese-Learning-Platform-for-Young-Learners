<template>
  <div class="page-stack">
    <h1 class="sr-only">{{ t('classCatalogue.tabs.mine') }}</h1>
    <StudentClassesTabs :joined-count="overview?.classes.length" :catalogue-count="overview?.moreClassCount" />
    <AppToast :message="toast" :action="toastAction" />
    <DataNotice
      :error="undoError ?? (overview ? resource.error.value : null)"
      :on-retry="undoError ? undefined : resource.reload"
    />

    <div v-if="resource.loading.value && !overview" class="student-classes-list" aria-busy="true">
      <span class="sr-only" role="status">{{ t('classes.loading') }}</span>
      <div v-for="row in 2" :key="row" class="student-classes-card is-skeleton" aria-hidden="true">
        <span class="skeleton student-classes-skeleton-avatar" />
        <div class="student-classes-copy">
          <span class="skeleton skeleton-line student-classes-skeleton-title" />
          <span class="skeleton skeleton-line" />
        </div>
        <div class="student-classes-actions">
          <span class="skeleton student-classes-skeleton-button" />
          <span class="skeleton student-classes-skeleton-button" />
        </div>
      </div>
    </div>

    <StatePanel
      v-else-if="resource.error.value && !overview"
      heading-tag="h2"
      tone="error"
      :title="t('studentClasses.mine.loadErrorTitle')"
      :message="resource.error.value"
      @retry="resource.reload"
    />

    <template v-else-if="overview">
      <div v-if="!overview.classes.length" class="panel student-classes-empty">
        <StatePanel
          heading-tag="h2"
          tone="empty"
          icon="classes"
          :title="t('studentClasses.mine.emptyTitle')"
          :message="t('studentClasses.mine.emptyMessage')"
        >
          <template #actions>
            <RouterLink class="btn btn-primary" :to="{ name: 'student-classes-find' }">
              {{ t('classCatalogue.tabs.find') }}
            </RouterLink>
          </template>
        </StatePanel>
      </div>

      <ul v-else class="student-classes-list">
        <li
          v-for="entry in overview.classes"
          :key="entry.summary.id"
          class="student-classes-card"
          :aria-labelledby="`my-class-title-${entry.summary.id}`"
        >
          <ClassAvatar :title="entry.summary.title" :size="72" />

          <div class="student-classes-copy">
            <div class="student-classes-head">
              <h2 :id="`my-class-title-${entry.summary.id}`" class="student-classes-title">
                <CjkText :text="entry.summary.title" />
              </h2>
              <!-- What the main button opens, so the row says where the learner goes on. -->
              <p v-if="entry.nextStep && !entry.archived" class="student-classes-next" data-testid="my-class-next">
                <IconGlyph name="play" :size="12" />
                <span><CjkText :text="t('studentClasses.mine.next', { title: entry.nextStep.lessonTitle })" /></span>
              </p>
            </div>
            <div class="student-classes-progress">
              <ProgressBar
                :progress="entry.progressPercent"
                :label="t('studentClasses.mine.progressLabel', { title: entry.summary.title })"
              />
              <span class="type-caption">{{ progressText(entry) }}</span>
            </div>
          </div>

          <div class="student-classes-actions">
            <RouterLink
              class="btn btn-secondary"
              :to="{ name: 'student-class', params: { classId: entry.summary.id } }"
              :aria-label="t('studentClasses.mine.openClassLabel', { title: entry.summary.title })"
            >
              {{ t('classes.openClass') }}
            </RouterLink>
            <RouterLink
              v-if="entry.nextStep && !entry.archived"
              class="btn btn-primary"
              :to="classLessonRoute(entry.summary.id, entry.nextStep.lessonId)"
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
        </li>
      </ul>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { studentClassesApi } from '@/api/classes'
import { getAuthSessionVersion } from '@/api/http'
import ClassAvatar from '@/components/classes/ClassAvatar.vue'
import StudentClassesTabs from '@/components/classes/StudentClassesTabs.vue'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useClassNotice } from '@/composables/useClassNotice'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { usePageActive } from '@/composables/usePageNavigation'
import { useResource } from '@/composables/useResource'
import { classCatalogueMessages } from '@/plugins/classCatalogueMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { studentClassesMessages } from '@/plugins/studentClassesMessages'
import type { MyClassCard } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { classLessonRoute } from '@/utils/classes'
import { formatShortDate } from '@/utils/format'
import '@/styles/classes.css'

const { t } = useLocalMessages({
  studentClasses: studentClassesMessages,
  classCatalogue: classCatalogueMessages,
  classes: classesMessages,
})
const router = useRouter()
const pageActive = usePageActive()

const resource = useResource(studentClassesApi.myClasses)
const overview = resource.data
const undoError = ref<string | null>(null)
let undoing = false
const isUnmounted = useIsUnmounted()

/**
 * Undo of a join that ended here: the class is left again and the learner is back in Find classes, where it is listed
 * again. If navigation stops after the class was left, refresh this list to show the saved membership.
 */
async function undoJoin(classId: number, fallbackTitle?: string): Promise<void> {
  if (undoing) return
  undoing = true
  undoError.value = null
  const version = getAuthSessionVersion()
  const isCurrent = () => !isUnmounted() && version === getAuthSessionVersion()
  const title =
    overview.value?.classes.find((entry) => entry.summary.id === classId)?.summary.title ?? fallbackTitle ?? ''
  try {
    await studentClassesApi.leave(classId)
  } catch (reason) {
    if (isCurrent()) undoError.value = resolveApiError(reason, t('studentClasses.mine.undoError', { title }))
    return
  } finally {
    undoing = false
  }
  if (!isCurrent() || !pageActive.value) return
  try {
    await router.push({ name: 'student-classes-find' })
  } catch (reason) {
    if (isCurrent()) undoError.value = resolveApiError(reason)
  }
  if (isCurrent() && router.currentRoute.value.name === 'student-classes') {
    await resource.reload()
    if (isCurrent() && !resource.error.value) undoError.value = null
  }
}

const { toast, toastAction } = useClassNotice('student-classes', {
  label: t('common.undo'),
  run: (classId, title) => void undoJoin(classId, title),
})

/** Nothing begun yet in the whole class. */
function isNotStarted(entry: MyClassCard): boolean {
  return entry.lessons.every((lesson) => lesson.status === 'NOT_STARTED')
}

/** "2 of 5 lessons done", "Not started", or an archived class's "Archived 2 May · 5 of 5 lessons done". */
function progressText(entry: MyClassCard): string {
  const counts = { done: entry.completedLessons, total: entry.summary.lessonCount }
  if (entry.archived) {
    return entry.archivedAt
      ? t('studentClasses.mine.archivedOn', { ...counts, date: formatShortDate(entry.archivedAt) })
      : t('studentClasses.archivedLessonsDone', counts)
  }
  return isNotStarted(entry) ? t('studentClasses.lessonState.NOT_STARTED') : t('studentClasses.lessonsDone', counts)
}
</script>
