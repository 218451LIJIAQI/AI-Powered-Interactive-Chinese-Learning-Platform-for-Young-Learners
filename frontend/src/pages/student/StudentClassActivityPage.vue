<template>
  <div class="class-focus-page class-activity-page">
    <FocusTopBar
      :label="play ? play.lessonTitle : t('classes.loading')"
      :close-label="t('classPlayer.activity.leaveLabel')"
      :close-disabled="closeDisabled"
      :progress="progress"
      @close="leave"
    />

    <div ref="stageRef" class="focus-stage">
      <template v-if="play">
        <DataNotice :error="actionError" />
        <ClassFlashcards
          v-if="play.type === 'FLASHCARDS'"
          :key="`f-${run}`"
          :title="play.title"
          :cards="play.cards"
          :busy="saving"
          :done="outcome !== null"
          @finish="complete"
        />
        <ClassTracing
          v-else-if="play.type === 'TRACING'"
          :key="`t-${run}`"
          :title="play.title"
          :characters="play.characters"
          :busy="saving"
          :done="outcome !== null"
          @finish="complete"
        />
        <ClassMatching
          v-else
          :key="`m-${run}`"
          :title="play.title"
          :activity-id="play.id"
          :lesson-title="play.lessonTitle"
          :words="play.words"
          :meanings="play.meanings"
          :hints-allowed="play.hintsAllowed"
          :done="outcome !== null"
          @outcome="finished"
          @busy="matchingBusy = $event"
          @error="actionError = $event"
        />

        <FocusFooter v-if="outcome" tone="done">
          <template #start>
            <button type="button" class="btn btn-secondary btn-lg" @click="practiseAgain">
              {{ t('common.practiseAgain') }}
            </button>
          </template>
          <template #middle>
            <div class="class-focus-band" role="status" data-testid="activity-done">
              <span class="class-focus-band-mark" aria-hidden="true"><IconGlyph name="check" :size="24" /></span>
              <div class="class-focus-band-copy">
                <p class="class-focus-band-title">{{ t('classPlayer.activity.completed') }}</p>
                <p class="class-focus-sub"><CjkText :text="doneText" /></p>
              </div>
            </div>
          </template>
          <template #end>
            <RouterLink
              v-if="play.next"
              class="btn btn-primary btn-lg"
              :to="classActivityRoute(play.classId, play.lessonId, play.next)"
            >
              {{ t('classPlayer.activity.next') }}
            </RouterLink>
            <RouterLink
              v-else
              class="btn btn-primary btn-lg"
              :to="lessonRoute"
              data-testid="finish-lesson"
              @click="finishLesson"
            >
              {{ completesLesson ? t('classPlayer.finishLesson') : t('classPlayer.openLesson') }}
            </RouterLink>
          </template>
        </FocusFooter>
      </template>

      <template v-else>
        <h1 class="sr-only">{{ t('nav.classes') }}</h1>
        <StatePanel
          v-if="loading"
          heading-tag="h2"
          tone="loading"
          spacious
          :title="t('classes.loading')"
          :message="t('classPlayer.activity.loadingMessage')"
        />
        <ClassLoadError
          v-else-if="loadError"
          :title="t('classPlayer.activity.loadErrorTitle')"
          :message="loadError"
          :back-to="lessonRoute"
          :back-label="t('classPlayer.openLesson')"
          :on-retry="isArchivedConflict ? undefined : resource.reload"
        />
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { studentClassesApi } from '@/api/classes'
import { getAuthSessionVersion } from '@/api/http'
import ClassLoadError from '@/components/classes/ClassLoadError.vue'
import ClassFlashcards from '@/components/classes/ClassFlashcards.vue'
import ClassMatching from '@/components/classes/ClassMatching.vue'
import ClassTracing from '@/components/classes/ClassTracing.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import FocusFooter from '@/components/layout/FocusFooter.vue'
import FocusTopBar from '@/components/layout/FocusTopBar.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { queueClassNotice } from '@/composables/useClassNotice'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useRecordById } from '@/composables/useResource'
import { usePageRedirect } from '@/composables/usePageNavigation'
import { classPlayerMessages } from '@/plugins/classPlayerMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { CLASS_ACTIVITY_TYPES, type ActivityOutcome, type ClassActivityType } from '@/types/classes'
import { hasServerMessage, resolveApiError } from '@/utils/apiError'
import { classActivityRoute, classLessonRoute } from '@/utils/classes'
import { restoreFocusIfDropped } from '@/utils/focus'
import { formatList, ratioPercent } from '@/utils/format'
import { parseRecordId } from '@/utils/recordId'
import { SERVER_MESSAGE } from '@/utils/serverMessages'
import '@/styles/classes.css'

const VALID_ACTIVITY_TYPES: ReadonlySet<string> = new Set(CLASS_ACTIVITY_TYPES)

const { t, locale } = useLocalMessages({
  classPlayer: classPlayerMessages,
  classes: classesMessages,
})
const route = useRoute()
const router = useRouter()
const actionError = ref<string | null>(null)

const routeClassId = computed(() => parseRecordId(route.params.classId))
const routeLessonId = computed(() => parseRecordId(route.params.lessonId))
const activityId = computed(() => parseRecordId(route.params.activityId))
const activityType = computed<ClassActivityType | null>(() => {
  const raw = String(route.params.activityType ?? '').toUpperCase()
  return VALID_ACTIVITY_TYPES.has(raw) ? (raw as ClassActivityType) : null
})
const activityKey = computed(() =>
  route.name === 'student-class-activity' && activityType.value && activityId.value !== null
    ? `${activityType.value}:${activityId.value}`
    : null,
)
const resource = useRecordById(activityKey, (key) => {
  const [type, id] = key.split(':')
  return studentClassesApi.activity(type as ClassActivityType, Number(id))
})
const play = resource.data
const classId = computed(() => play.value?.classId ?? routeClassId.value)
const lessonId = computed(() => play.value?.lessonId ?? routeLessonId.value)
const lessonRoute = computed(() =>
  classId.value !== null && lessonId.value !== null
    ? classLessonRoute(classId.value, lessonId.value)
    : { name: 'student-classes' },
)
const needsEnrollment = computed(
  () =>
    resource.errorStatus.value === 409 &&
    hasServerMessage(resource.errorReason.value, SERVER_MESSAGE.JOIN_CLASS_TO_OPEN_LESSONS),
)
const isArchivedConflict = computed(() => resource.errorStatus.value === 409 && !needsEnrollment.value)
const loadError = computed(() => (needsEnrollment.value ? null : resource.error.value))
const loading = computed(() => resource.loading.value || needsEnrollment.value)

usePageRedirect(() => {
  if (needsEnrollment.value) return { name: 'unauthorized', query: { redirect: route.fullPath } }
  const current = play.value
  if (!current || current.type !== activityType.value || current.id !== activityId.value) return null
  if (current.classId === routeClassId.value && current.lessonId === routeLessonId.value) {
    return null
  }
  return {
    ...classActivityRoute(current.classId, current.lessonId, current),
    query: route.query,
    hash: route.hash,
  }
})

const outcome = ref<ActivityOutcome | null>(null)
const stageRef = ref<HTMLElement | null>(null)
/** Finished once on this visit: Practise again does not take the progress bar back. */
const finishedOnce = ref(false)
const saving = ref(false)
const matchingBusy = ref(false)
const closeDisabled = computed(() => saving.value || matchingBusy.value)
/** Bumped by Practise again or switching activities, so the player starts over. */
const run = ref(0)
let activityVersion = 0
onBeforeUnmount(() => {
  activityVersion++
})

watch(activityKey, () => {
  activityVersion++
  outcome.value = null
  finishedOnce.value = false
  saving.value = false
  matchingBusy.value = false
  actionError.value = null
  run.value++
})

/** The activities of the lesson done so far: half an activity while on it, the whole one once finished. */
const progress = computed(() => {
  const current = play.value
  if (!current) return null
  return ratioPercent(current.number - (finishedOnce.value ? 0 : 0.5), current.activityCount)
})

/** "All 5 cards reviewed.", "You traced 名 and 字.", "All 4 pairs are correct." */
const doneText = computed(() => {
  const current = play.value
  if (!current) return ''
  if (current.type === 'FLASHCARDS') return t('classPlayer.flashcards.done', current.cards.length)
  if (current.type === 'MATCHING') return t('classPlayer.matching.done', current.words.length)
  const characters = formatList(
    current.characters.map((item) => item.character),
    locale.value,
  )
  return t('classPlayer.tracing.doneResult', { characters })
})

function finished(result: ActivityOutcome): void {
  outcome.value = result
  finishedOnce.value = true
}

async function complete(): Promise<void> {
  const current = play.value
  if (!current || saving.value) return
  const version = activityVersion
  const session = getAuthSessionVersion()
  const isCurrent = () => version === activityVersion && session === getAuthSessionVersion()
  saving.value = true
  actionError.value = null
  let failed = false
  try {
    const result = await studentClassesApi.completeActivity(current.type, current.id)
    if (!isCurrent()) return
    finished(result)
  } catch (reason) {
    if (isCurrent()) {
      actionError.value = resolveApiError(reason, t('classPlayer.activity.saveError'))
      failed = true
    }
  } finally {
    if (version === activityVersion) saving.value = false
  }
  if (failed) {
    await restoreFocusIfDropped(
      () => stageRef.value?.querySelector<HTMLElement>('[data-testid="activity-finish"]'),
      isCurrent,
    )
  }
}

/** True only when finishing this activity completed the lesson on this visit (not on replay or while earlier steps remain). */
const completesLesson = computed(() => Boolean(outcome.value?.lessonCompletedNow))

function practiseAgain(): void {
  outcome.value = null
  run.value++
}

watch(
  outcome,
  (next) => {
    if (document.activeElement && document.activeElement !== document.body) return
    if (next) {
      stageRef.value?.querySelector<HTMLElement>('.focus-footer .btn-primary')?.focus({ preventScroll: true })
    } else {
      stageRef.value
        ?.querySelector<HTMLElement>('.class-player button:not(:disabled), .class-player [tabindex="0"]')
        ?.focus({ preventScroll: true })
    }
  },
  { flush: 'post' },
)

/** Finishing the last activity lands on the lesson, which confirms it if that completed the lesson. */
function finishLesson(event: MouseEvent): void {
  if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
  if (completesLesson.value) {
    queueClassNotice({ message: t('classes.lessonCompletedNotice'), targetRoute: 'student-class-lesson' })
  }
}

/** Each finished activity is saved, so leaving one midway loses nothing worth a question. */
function leave(): void {
  if (closeDisabled.value) return
  void router.push(lessonRoute.value)
}
</script>
