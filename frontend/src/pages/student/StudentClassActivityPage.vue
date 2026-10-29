<template>
  <div class="class-focus-page">
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
      :progress="play ? ratioPercent(play.number, play.activityCount) : null"
      @close="requestLeave"
    />

    <div class="class-focus-main page-stack">
      <template v-if="play">
        <header class="class-focus-header">
          <span class="page-eyebrow">
            {{
              play.stage
                ? t('classPlayer.activity.eyebrow', {
                    number: play.number,
                    total: play.activityCount,
                    stage: t(STAGE_KEYS[play.stage]),
                    lesson: play.lessonTitle,
                  })
                : t('classPlayer.activity.eyebrowNoStage', {
                    number: play.number,
                    total: play.activityCount,
                    lesson: play.lessonTitle,
                  })
            }}
          </span>
          <h1 class="class-focus-title"><CjkText :text="play.title" /></h1>
          <p v-if="play.done && !outcome" class="class-focus-note">
            <IconGlyph name="check" :size="14" />{{ t('classPlayer.activity.doneBefore') }}
          </p>
        </header>

        <DataNotice :error="actionError" />

        <section v-if="outcome" class="class-activity-done" role="status" data-testid="activity-done">
          <span class="seal class-activity-seal" aria-hidden="true">{{
            outcome.lessonStatus === 'MASTERED' ? '棒' : '好'
          }}</span>
          <div class="class-activity-done-copy">
            <h2 class="class-activity-done-title">{{ t('classPlayer.activity.saved') }}</h2>
            <p>{{ outcomeText }}</p>
          </div>
          <RouterLink
            v-if="lessonFinished || !play.next"
            class="btn btn-primary"
            :to="lessonRoute"
            data-testid="finish-lesson"
          >
            {{ t('classPlayer.activity.finishLesson') }}
          </RouterLink>
        </section>

        <section v-else class="class-activity-sheet">
          <ClassFlashcards
            v-if="play.type === 'FLASHCARDS'"
            :key="`f-${play.id}`"
            :cards="play.cards"
            :stage="play.stage"
            :busy="saving"
            @finish="complete"
          />
          <ClassTracing
            v-else-if="play.type === 'TRACING'"
            :key="`t-${play.id}`"
            :characters="play.characters"
            :stage="play.stage"
            :busy="saving"
            @finish="complete"
          />
          <ClassMatching
            v-else
            :key="`m-${play.id}`"
            :activity-id="play.id"
            :words="play.words"
            :meanings="play.meanings"
            :stage="play.stage"
            @outcome="outcome = $event"
            @error="showError"
          />
        </section>

        <nav
          class="class-focus-nav"
          :aria-label="t('classPlayer.activity.position', { number: play.number, total: play.activityCount })"
        >
          <RouterLink
            v-if="play.previous"
            class="btn btn-outline"
            :to="activityRoute(play.previous.type, play.previous.id)"
          >
            <IconGlyph name="chevronLeft" :size="16" />{{ t('classPlayer.activity.previous') }}
          </RouterLink>
          <span v-else />
          <span class="class-focus-nav-copy">
            <strong>{{
              t('classPlayer.activity.position', { number: play.number, total: play.activityCount })
            }}</strong>
            <span class="type-caption">{{ t('classPlayer.activity.counts') }}</span>
          </span>
          <RouterLink v-if="play.next" class="btn btn-outline" :to="activityRoute(play.next.type, play.next.id)">
            {{ t('classPlayer.activity.next') }}<IconGlyph name="chevronRight" :size="16" />
          </RouterLink>
          <RouterLink v-else class="btn btn-outline" :to="lessonRoute">
            {{ t('classPlayer.activity.backToLesson') }}<IconGlyph name="chevronRight" :size="16" />
          </RouterLink>
        </nav>
      </template>

      <template v-else>
        <h1 class="sr-only">{{ t('classPlayer.activity.loadErrorTitle') }}</h1>
        <StatePanel
          v-if="resource.loading.value"
          tone="loading"
          spacious
          :title="t('classes.loading')"
          :message="t('classPlayer.activity.counts')"
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
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import ClassFlashcards from '@/components/classes/ClassFlashcards.vue'
import ClassMatching from '@/components/classes/ClassMatching.vue'
import ClassTracing from '@/components/classes/ClassTracing.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import FocusTopBar from '@/components/layout/FocusTopBar.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classPlayerMessages } from '@/plugins/classPlayerMessages'
import { classesMessages } from '@/plugins/classesMessages'
import type { ActivityOutcome, ClassActivityType } from '@/types/classes'
import { STAGE_KEYS } from '@/utils/classes'
import { ratioPercent } from '@/utils/format'
import '@/styles/classes.css'

const { t } = useLocalMessages({ classPlayer: classPlayerMessages, classes: classesMessages })
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

watch([activityType, activityId], () => {
  outcome.value = null
  clearFeedback()
  void resource.reload()
})

const lessonFinished = computed(
  () => outcome.value?.lessonStatus === 'COMPLETED' || outcome.value?.lessonStatus === 'MASTERED',
)

const outcomeText = computed(() => {
  const result = outcome.value
  if (!result) return ''
  if (result.lessonStatus === 'MASTERED') return t('classPlayer.activity.lessonMastered')
  if (result.lessonStatus === 'COMPLETED') return t('classPlayer.activity.lessonCompleted')
  return t('classPlayer.activity.stepsLeft', { done: result.stepsDone, total: result.stepsTotal })
})

function activityRoute(type: ClassActivityType, id: number) {
  return {
    name: 'student-class-activity',
    params: { classId: classId.value, lessonId: lessonId.value, activityType: activitySlug(type), activityId: id },
  }
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
