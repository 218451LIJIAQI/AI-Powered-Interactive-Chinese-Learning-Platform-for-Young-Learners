<template>
  <div class="page-stack">
    <template v-if="page">
      <ClassPageHeader
        :crumbs="[
          page.joined
            ? { label: t('nav.classes'), to: { name: 'student-classes' } }
            : {
                label: t('classCatalogue.tabs.find'),
                to: { name: 'student-classes-find', query: catalogueQuery(route.query) },
              },
          { label: page.summary.title },
        ]"
        :class-title="page.summary.title"
        :title="page.summary.title"
        :meta="page.joined ? joinedFacts : (page.summary.topicName ?? undefined)"
      >
        <template v-if="page.joined && !page.archived" #actions>
          <button class="btn btn-secondary" type="button" data-testid="leave-class" @click="leaveOpen = true">
            {{ t('studentClasses.classPage.leave') }}
          </button>
        </template>
      </ClassPageHeader>

      <DataNotice :error="actionError" />

      <!-- Not joined yet: what the class holds, free to join, the lessons locked until then. -->
      <template v-if="!page.joined">
        <div class="panel student-class-join">
          <div class="student-class-join-copy">
            <p v-if="page.summary.description" class="student-class-join-about">{{ page.summary.description }}</p>
            <p class="type-caption" data-testid="class-preview-facts">{{ previewFacts }}</p>
          </div>
          <button
            type="button"
            class="btn btn-primary"
            :disabled="busy"
            :aria-busy="busy"
            data-testid="join-class"
            @click="changeMembership('join')"
          >
            {{ t('studentClasses.classPage.preview.join') }}
          </button>
        </div>

        <div class="student-class-preview">
          <section
            v-for="(unit, unitIndex) in page.preview"
            :key="unit.id"
            class="panel student-class-unit"
            :aria-labelledby="`class-unit-${unit.id}`"
          >
            <h2 :id="`class-unit-${unit.id}`" class="card-title">
              {{ t('classes.unitLabel', { number: unitIndex + 1, title: unit.title }) }}
            </h2>
            <ol class="row-list">
              <li v-for="lesson in unit.lessons" :key="lesson.id" class="student-class-locked">
                <span class="student-class-lock" aria-hidden="true"><IconGlyph name="lock" :size="16" /></span>
                <span class="row-list-title"><CjkText :text="lesson.title" /></span>
              </li>
            </ol>
          </section>
        </div>
      </template>

      <template v-else>
        <p v-if="page.archived" class="callout is-compact" data-testid="class-archived">
          <IconGlyph name="archive" :size="16" />
          <span>{{ archivedBanner }}</span>
        </p>

        <section v-else class="panel student-class-next" aria-labelledby="class-next-title" data-testid="class-next">
          <div class="row-list-copy">
            <h2 id="class-next-title" class="page-eyebrow">{{ t('studentClasses.classPage.nextStep') }}</h2>
            <p class="card-title">{{ nextStepText }}</p>
          </div>
          <div v-if="page.nextStep" class="btn-group">
            <RouterLink
              class="btn btn-primary"
              :to="classLessonRoute(page.summary.id, page.nextStep.lessonId)"
              data-testid="continue-class"
            >
              {{
                nextNotStarted
                  ? t('studentClasses.classPage.start', { title: page.nextStep.lessonTitle })
                  : t('classes.continueNamed', { title: page.nextStep.lessonTitle })
              }}
              <IconGlyph name="chevronRight" :size="16" />
            </RouterLink>
          </div>
        </section>

        <section
          v-for="(unit, unitIndex) in page.units"
          :key="unit.id"
          class="panel student-class-unit"
          :aria-labelledby="`class-unit-${unit.id}`"
        >
          <div class="row-list-copy">
            <h2 :id="`class-unit-${unit.id}`" class="card-title">
              {{ t('classes.unitLabel', { number: unitIndex + 1, title: unit.title }) }}
            </h2>
            <span class="row-list-meta">
              {{ t('studentClasses.classPage.unitFacts', { done: unit.completedCount, total: unit.lessonCount }) }}
            </span>
          </div>
          <ol class="row-list student-class-lessons">
            <li v-for="lesson in unit.lessons" :key="lesson.id">
              <RouterLink
                v-if="lesson.available"
                class="student-class-lesson"
                :to="classLessonRoute(page.summary.id, lesson.id)"
                :title="t('studentClasses.classPage.openLesson', { title: lesson.title })"
              >
                <strong class="row-list-title student-class-lesson-title"><CjkText :text="lesson.title" /></strong>
                <StatusPill :label="t(`studentClasses.lessonState.${lesson.status}`)" :tone="stateTone(lesson)" />
                <IconGlyph class="class-row-chevron" name="chevronRight" :size="16" />
              </RouterLink>
              <div v-else class="student-class-lesson is-unavailable">
                <strong class="row-list-title student-class-lesson-title"><CjkText :text="lesson.title" /></strong>
                <StatusPill :label="t('studentClasses.lessonState.unavailable')" tone="outline" />
              </div>
            </li>
          </ol>
        </section>

        <ConfirmDialog
          :open="leaveOpen"
          :busy="busy"
          :error="leaveError"
          :title="t('studentClasses.classPage.leaveTitle', { title: page.summary.title })"
          :message="t('studentClasses.classPage.leaveMessage')"
          :confirm-label="t('studentClasses.classPage.leave')"
          :cancel-label="t('studentClasses.classPage.stay')"
          @confirm="changeMembership('leave')"
          @cancel="closeLeave"
        />
      </template>
    </template>

    <template v-else>
      <h1 class="sr-only">{{ t('nav.classes') }}</h1>
      <StatePanel
        v-if="resource.loading.value"
        heading-tag="h2"
        tone="loading"
        spacious
        :title="t('classes.loading')"
        :message="t('nav.classes')"
      />
      <ClassLoadError
        v-else
        :title="t('classes.classLoadError')"
        :message="resource.error.value"
        :back-to="{ name: 'student-classes' }"
        :back-label="t('studentClasses.classPage.backToClasses')"
        @retry="resource.reload"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { studentClassesApi } from '@/api/classes'
import { getAuthSessionVersion } from '@/api/http'
import ClassLoadError from '@/components/classes/ClassLoadError.vue'
import ClassPageHeader from '@/components/classes/ClassPageHeader.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill, { type PillTone } from '@/components/common/StatusPill.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { clearClassNotice, queueClassNotice } from '@/composables/useClassNotice'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { usePageActive } from '@/composables/usePageNavigation'
import { useRecordById } from '@/composables/useResource'
import { classCatalogueMessages } from '@/plugins/classCatalogueMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { studentClassesMessages } from '@/plugins/studentClassesMessages'
import type { LessonProgressRow } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { catalogueQuery, classLessonRoute } from '@/utils/classes'
import { formatShortDate } from '@/utils/format'
import { parseRecordId } from '@/utils/recordId'
import '@/styles/classes.css'

const { t } = useLocalMessages({
  studentClasses: studentClassesMessages,
  classes: classesMessages,
  classCatalogue: classCatalogueMessages,
})
const route = useRoute()
const router = useRouter()
const pageActive = usePageActive()
const actionError = ref<string | null>(null)
const leaveError = ref<string | null>(null)
const leaveOpen = ref(false)
/** A join or a leave on its way: one at a time. */
const busy = ref(false)
let pageVersion = 0
onBeforeUnmount(() => pageVersion++)

const classId = computed(() => parseRecordId(route.params.classId))
const resource = useRecordById(classId, studentClassesApi.classPage)
const page = resource.data

// Another class in this same page (Back or Forward): nothing of the last one stays.
watch(classId, () => {
  pageVersion++
  busy.value = false
  actionError.value = null
  closeLeave()
})

/** "Free · best after Unit 3 of Learn · join to open the lessons" */
const previewFacts = computed(() => {
  const unit = page.value?.summary.bestAfterUnit
  return [
    t('classes.free'),
    unit ? t('classes.bestAfterLearn', { unit }) : null,
    t('studentClasses.classPage.preview.joinToOpen'),
  ]
    .filter(Boolean)
    .join(' · ')
})

/** "2 of 5 lessons done", or "Archived · 5 of 5 lessons done". */
const joinedFacts = computed(() => {
  const current = page.value
  if (!current) return ''
  const counts = { done: current.completedLessons, total: current.summary.lessonCount }
  return t(current.archived ? 'studentClasses.archivedLessonsDone' : 'studentClasses.lessonsDone', counts)
})

const archivedBanner = computed(() => {
  const at = page.value?.archivedAt
  return at
    ? t('studentClasses.classPage.archivedOn', { date: formatShortDate(at) })
    : t('studentClasses.classPage.archivedBanner')
})

/** The lesson to continue with is not begun yet: "Start: University Life" instead of "Continue: …". */
const nextNotStarted = computed(() => {
  const next = page.value?.nextStep
  if (!next) return false
  const row = page.value?.units.flatMap((unit) => unit.lessons).find((lesson) => lesson.id === next.lessonId)
  return row ? row.status === 'NOT_STARTED' : next.stepsDone === 0
})

/** "Meeting Classmates · 3 of 4 steps done", "University Life · not started yet", or all done. */
const nextStepText = computed(() => {
  const next = page.value?.nextStep
  if (!next) return t('studentClasses.classPage.allDone')
  if (nextNotStarted.value) return t('studentClasses.classPage.nextNotStarted', { lesson: next.lessonTitle })
  if (!next.stepsTotal) return next.lessonTitle
  return t('studentClasses.classPage.nextInProgress', {
    lesson: next.lessonTitle,
    done: next.stepsDone,
    total: next.stepsTotal,
  })
})

function stateTone(lesson: LessonProgressRow): PillTone {
  if (lesson.status === 'IN_PROGRESS') return 'info'
  return lesson.status === 'NOT_STARTED' ? 'outline' : 'neutral'
}

function closeLeave(): void {
  leaveOpen.value = false
  leaveError.value = null
}

/** A join can be undone from My classes. Leaving is confirmed in the dialog. */
async function changeMembership(action: 'join' | 'leave'): Promise<void> {
  if (busy.value || !page.value) return
  const version = pageVersion
  const session = getAuthSessionVersion()
  const isCurrent = () => version === pageVersion && session === getAuthSessionVersion()
  const { id, title } = page.value.summary
  const error = action === 'join' ? actionError : leaveError
  busy.value = true
  error.value = null
  try {
    await studentClassesApi[action](id)
  } catch (reason) {
    if (isCurrent()) {
      const key =
        action === 'join' ? 'studentClasses.classPage.preview.joinError' : 'studentClasses.classPage.leaveError'
      error.value = resolveApiError(reason, t(key))
    }
    if (version === pageVersion) busy.value = false
    return
  }
  if (!isCurrent()) {
    if (version === pageVersion) busy.value = false
    return
  }
  if (action === 'leave') leaveOpen.value = false
  if (!pageActive.value) {
    busy.value = false
    return
  }
  if (action === 'join') {
    queueClassNotice({
      message: t('studentClasses.classPage.preview.joined', { title }),
      targetRoute: 'student-classes',
      joinedClassId: id,
      joinedClassTitle: title,
    })
  } else {
    queueClassNotice({ message: t('studentClasses.classPage.left', { title }), targetRoute: 'student-classes' })
  }
  try {
    try {
      await router.push({ name: 'student-classes' })
    } catch (reason) {
      if (isCurrent()) actionError.value = resolveApiError(reason)
    }
    if (isCurrent() && router.currentRoute.value.name === 'student-class') {
      clearClassNotice()
      await resource.reload()
    }
  } finally {
    if (version === pageVersion) busy.value = false
  }
}
</script>
