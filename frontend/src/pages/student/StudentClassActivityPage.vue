<template>
  <div class="class-focus-page class-activity-page">
    <FocusTopBar
      :label="
        play
          ? t('classPlayer.activity.topLabel', {
              lesson: play.lessonTitle,
              number: play.number,
              total: play.activityCount,
            })
          : t('classes.loading')
      "
      :close-label="t('classPlayer.activity.leaveLabel')"
      :progress="play ? progress : null"
      @close="requestLeave"
    />

    <div class="focus-stage">
      <template v-if="play">
        <DataNotice :error="actionError" />
        <ClassFlashcards
          v-if="play.type === 'FLASHCARDS'"
          :key="`f-${play.id}-${run}`"
          :title="play.title"
          :cards="play.cards"
          :busy="saving"
          :done="outcome !== null"
          @finish="complete"
        />
        <ClassTracing
          v-else-if="play.type === 'TRACING'"
          :key="`t-${play.id}-${run}`"
          :title="play.title"
          :characters="play.characters"
          :busy="saving"
          :done="outcome !== null"
          @finish="complete"
        />
        <ClassMatching
          v-else
          :key="`m-${play.id}-${run}`"
          :title="play.title"
          :activity-id="play.id"
          :words="play.words"
          :meanings="play.meanings"
          :stage="play.stage"
          :done="outcome !== null"
          @outcome="finish"
          @error="showError"
        />

        <FocusFooter v-if="outcome" tone="done">
          <template #start>
            <div class="class-focus-band" role="status" data-testid="activity-done">
              <span class="class-focus-band-mark" aria-hidden="true"><IconGlyph name="check" :size="24" /></span>
              <div class="class-focus-band-copy">
                <p class="class-focus-band-title">{{ t('classPlayer.activity.completed') }}</p>
                <p class="class-focus-sub"><CjkText :text="doneText" /></p>
              </div>
            </div>
          </template>
          <template #end>
            <button type="button" class="btn btn-secondary btn-lg" @click="practiseAgain">
              {{ t('classPlayer.activity.practiseAgain') }}
            </button>
            <RouterLink
              v-if="play.next"
              class="btn btn-primary btn-lg"
              :to="activityRoute(play.next.type, play.next.id)"
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
              {{ t('classPlayer.activity.finishLesson') }}
            </RouterLink>
          </template>
        </FocusFooter>
      </template>

      <template v-else>
        <h1 class="sr-only">{{ t('classPlayer.activity.loadErrorTitle') }}</h1>
        <StatePanel
          v-if="resource.loading.value"
          tone="loading"
          spacious
          :title="t('classes.loading')"
          :message="t('classPlayer.activity.loadingMessage')"
        />
        <StatePanel
          v-else
          tone="error"
          icon="alert"
          spacious
          :title="t('classPlayer.activity.loadErrorTitle')"
          :message="resource.error.value ?? ''"
        >
          <template #actions>
            <button class="btn btn-secondary" type="button" @click="resource.reload">{{ t('classes.retry') }}</button>
            <RouterLink class="btn btn-primary" :to="lessonRoute">{{
              t('classPlayer.activity.backToLesson')
            }}</RouterLink>
          </template>
        </StatePanel>
      </template>
    </div>

    <ConfirmDialog
      :open="leaveOpen"
      :title="t('classPlayer.activity.leaveTitle')"
      :message="t('classPlayer.activity.leaveMessage')"
      tone="leave"
      :confirm-label="t('classes.leave')"
      :cancel-label="t('classes.keepGoing')"
      @confirm="leave"
      @cancel="leaveOpen = false"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { activitySlug, studentClassesApi } from '@/api/classes'
import ClassFlashcards from '@/components/classes/ClassFlashcards.vue'
import ClassMatching from '@/components/classes/ClassMatching.vue'
import ClassTracing from '@/components/classes/ClassTracing.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import FocusFooter from '@/components/layout/FocusFooter.vue'
import FocusTopBar from '@/components/layout/FocusTopBar.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { leaveClassNotice } from '@/composables/useClassNotice'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classPlayerMessages } from '@/plugins/classPlayerMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { studentClassesMessages } from '@/plugins/studentClassesMessages'
import type { ActivityOutcome, ClassActivityType } from '@/types/classes'
import { ratioPercent } from '@/utils/format'
import '@/styles/classes.css'

const { t, locale } = useLocalMessages({
  classPlayer: classPlayerMessages,
  classes: classesMessages,
  studentClasses: studentClassesMessages,
})
const route = useRoute()
const router = useRouter()
const { actionError, runAction, showError, clearFeedback } = useActionFeedback()

const classId = computed(() => Number(route.params.classId))
const lessonId = computed(() => Number(route.params.lessonId))
const activityId = computed(() => Number(route.params.activityId))
const activityType = computed(() => String(route.params.activityType).toUpperCase() as ClassActivityType)
const lessonRoute = computed(() => ({
  name: 'student-class-lesson',
  params: { classId: classId.value, lessonId: lessonId.value },
}))

const resource = useResource(() => studentClassesApi.activity(activityType.value, activityId.value))
const play = computed(() => resource.data.value)
const outcome = ref<ActivityOutcome | null>(null)
const saving = ref(false)
const leaveOpen = ref(false)
/** Bumped by Practise again, so the player starts over. */
const run = ref(0)

watch([activityType, activityId], () => {
  outcome.value = null
  clearFeedback()
  void resource.reload()
})

/** The activities of the lesson done so far: half an activity while on it, the whole one when finished. */
const progress = computed(() => {
  const current = play.value
  if (!current) return 0
  return ratioPercent(current.number - (outcome.value ? 0 : 0.5), current.activityCount)
})

/** "All 5 cards reviewed.", "You traced 名 and 字.", "All 4 pairs are correct." */
const doneText = computed(() => {
  const current = play.value
  if (!current) return ''
  if (current.type === 'FLASHCARDS') return t('classPlayer.flashcards.done', { count: current.cards.length })
  if (current.type === 'MATCHING') return t('classPlayer.matching.done', { count: current.words.length })
  const characters = new Intl.ListFormat(locale.value, { type: 'conjunction' }).format(
    current.characters.map((item) => item.character),
  )
  return t('classPlayer.tracing.doneResult', { characters })
})

function activityRoute(type: ClassActivityType, id: number) {
  return {
    name: 'student-class-activity',
    params: { classId: classId.value, lessonId: lessonId.value, activityType: activitySlug(type), activityId: id },
  }
}

function finish(result: ActivityOutcome): void {
  outcome.value = result
}

async function complete(): Promise<void> {
  if (!play.value) return
  saving.value = true
  await runAction(t('classPlayer.activity.saveError'), async () => {
    outcome.value = await studentClassesApi.completeActivity(play.value!.type, play.value!.id)
    return ''
  })
  saving.value = false
}

function practiseAgain(): void {
  outcome.value = null
  run.value++
}

/** Finishing the last activity lands on the lesson, which confirms it if that completed the lesson. */
function finishLesson(): void {
  if (outcome.value?.lessonStatus === 'COMPLETED' || outcome.value?.lessonStatus === 'MASTERED') {
    leaveClassNotice(t('studentClasses.lesson.completedNotice'))
  }
}

function requestLeave(): void {
  // Nothing is lost once the activity is finished, so there is nothing to confirm.
  if (outcome.value || !play.value) void router.push(lessonRoute.value)
  else leaveOpen.value = true
}

function leave(): void {
  leaveOpen.value = false
  void router.push(lessonRoute.value)
}
</script>
