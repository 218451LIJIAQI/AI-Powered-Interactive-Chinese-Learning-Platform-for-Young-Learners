<template>
  <div class="class-focus-page class-activity-page">
    <FocusTopBar
      :label="play ? play.lessonTitle : t('classes.loading')"
      :close-label="t('classPlayer.activity.leaveLabel')"
      :close-disabled="closeDisabled"
      :progress="progress"
      @close="leave"
    />

    <div class="focus-stage">
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
              :to="classActivityRoute(classId, lessonId, play.next)"
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
          v-if="resource.loading.value"
          heading-tag="h2"
          tone="loading"
          spacious
          :title="t('classes.loading')"
          :message="t('classPlayer.activity.loadingMessage')"
        />
        <ClassLoadError
          v-else
          :title="t('classPlayer.activity.loadErrorTitle')"
          :message="resource.error.value"
          :back-to="lessonRoute"
          :back-label="t('classPlayer.openLesson')"
          @retry="resource.reload"
        />
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { studentClassesApi } from '@/api/classes'
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
import { useResource } from '@/composables/useResource'
import { usePageRedirect } from '@/composables/usePageNavigation'
import { classPlayerMessages } from '@/plugins/classPlayerMessages'
import { classesMessages } from '@/plugins/classesMessages'
import type { ActivityOutcome, ClassActivityType } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { classActivityRoute, classLessonRoute } from '@/utils/classes'
import { formatList, ratioPercent } from '@/utils/format'
import { parseRecordId } from '@/utils/recordId'
import '@/styles/classes.css'

const { t, locale } = useLocalMessages({
  classPlayer: classPlayerMessages,
  classes: classesMessages,
})
const route = useRoute()
const router = useRouter()
const actionError = ref<string | null>(null)

const activityId = computed(() => parseRecordId(route.params.activityId))
const activityType = computed(() => String(route.params.activityType).toUpperCase() as ClassActivityType)
const resource = useResource(() =>
  activityId.value === null ? Promise.resolve(null) : studentClassesApi.activity(activityType.value, activityId.value),
)
const play = resource.data
const classId = computed(() => play.value?.classId ?? parseRecordId(route.params.classId) ?? 0)
const lessonId = computed(() => play.value?.lessonId ?? parseRecordId(route.params.lessonId) ?? 0)
const lessonRoute = computed(() => classLessonRoute(classId.value, lessonId.value))

usePageRedirect(() => {
  const current = play.value
  if (!current || current.type !== activityType.value || current.id !== activityId.value) return null
  if (
    current.classId === parseRecordId(route.params.classId) &&
    current.lessonId === parseRecordId(route.params.lessonId)
  ) {
    return null
  }
  return {
    ...classActivityRoute(current.classId, current.lessonId, current),
    query: route.query,
    hash: route.hash,
  }
})

const outcome = ref<ActivityOutcome | null>(null)
/** Finished once on this visit: Practise again does not take the progress bar back. */
const finishedOnce = ref(false)
/** Once the lesson was already completed on this visit, replaying it won't claim "Finish lesson" again. */
const replayedCompletedLesson = ref(false)
const saving = ref(false)
const matchingBusy = ref(false)
const closeDisabled = computed(() => saving.value || matchingBusy.value)
/** Bumped by Practise again or switching activities, so the player starts over. */
const run = ref(0)
let activityVersion = 0
onBeforeUnmount(() => {
  activityVersion++
})

watch([activityType, activityId], () => {
  activityVersion++
  outcome.value = null
  finishedOnce.value = false
  replayedCompletedLesson.value = false
  saving.value = false
  matchingBusy.value = false
  actionError.value = null
  run.value++
  if (route.name !== 'student-class-activity' || activityId.value === null) {
    resource.clear()
    return
  }
  resource.data.value = null
  void resource.reload()
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
  if (current.type === 'FLASHCARDS') return t('classPlayer.flashcards.done', { count: current.cards.length })
  if (current.type === 'MATCHING') return t('classPlayer.matching.done', { count: current.words.length })
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
  saving.value = true
  actionError.value = null
  try {
    const result = await studentClassesApi.completeActivity(current.type, current.id)
    if (version !== activityVersion) return
    finished(result)
  } catch (reason) {
    if (version === activityVersion) actionError.value = resolveApiError(reason, t('classPlayer.activity.saveError'))
  } finally {
    if (version === activityVersion) saving.value = false
  }
}

/** True only when finishing this activity completed the lesson on this visit (not on replay or while earlier steps remain). */
const completesLesson = computed(() => !replayedCompletedLesson.value && outcome.value?.lessonStatus === 'COMPLETED')

function practiseAgain(): void {
  if (outcome.value?.lessonStatus === 'COMPLETED') replayedCompletedLesson.value = true
  outcome.value = null
  run.value++
}

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
