<template>
  <div class="focus-stage class-quiz-result">
    <header class="class-focus-head" data-testid="quiz-result">
      <h1 ref="scoreHeading" class="focus-headline" tabindex="-1">
        {{
          t('classPlayer.result.scored', {
            correct: result.correctCount,
            total: result.questions.length,
            percent: result.percent,
          })
        }}
      </h1>
      <p class="class-focus-sub">{{ submittedLine }}</p>
    </header>
    <DataNotice :error="actionError" />
    <DataNotice
      :error="lessonError"
      :error-title="t('classes.lessonLoadError')"
      @retry="emit('retryLesson', result.quiz.lessonId)"
    />
    <QuizReview :result="result" :title="t('classPlayer.result.toReview', result.wrongCount)" />

    <FocusFooter>
      <template v-if="playableLesson" #start>
        <button
          type="button"
          class="btn btn-secondary btn-lg"
          :disabled="starting"
          :aria-busy="starting"
          @click="emit('retake')"
        >
          {{ t('classes.retakeQuiz') }}
        </button>
      </template>
      <template v-if="resultNote" #default>{{ resultNote }}</template>
      <template #end>
        <RouterLink
          class="btn btn-primary btn-lg"
          :to="resultNext.to"
          data-testid="quiz-result-next"
          @click="finishLesson"
        >
          {{ resultNext.label }}
        </RouterLink>
      </template>
    </FocusFooter>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

import QuizReview from '@/components/classes/QuizReview.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import FocusFooter from '@/components/layout/FocusFooter.vue'
import { queueClassNotice } from '@/composables/useClassNotice'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classPlayerMessages } from '@/plugins/classPlayerMessages'
import { classesMessages } from '@/plugins/classesMessages'
import type { QuizResult, StudentLesson } from '@/types/classes'
import { classActivityRoute } from '@/utils/classes'
import { formatDateTime } from '@/utils/format'

const props = defineProps<{
  result: QuizResult
  justSubmitted: boolean
  playableLesson: StudentLesson | null
  lessonRoute: RouteLocationRaw
  starting: boolean
  actionError: string | null
  lessonError: string | null
}>()

const emit = defineEmits<{
  retake: []
  retryLesson: [lessonId: number]
}>()

const { t } = useLocalMessages({
  classPlayer: classPlayerMessages,
  classes: classesMessages,
})

const scoreHeading = ref<HTMLHeadingElement | null>(null)
onMounted(() => {
  if (props.justSubmitted) scoreHeading.value?.focus()
})

const submittedLine = computed(() => {
  const best = {
    best: props.result.bestCorrect,
    total: props.result.bestTotal,
    percent: props.result.bestPercent,
  }
  if (props.justSubmitted) return t('classPlayer.result.submittedNow', best)
  return t('classPlayer.result.submittedAt', { ...best, when: formatDateTime(props.result.submittedAt) })
})

const resultNext = computed(() => {
  const available = props.playableLesson
  const activity = available?.activities.find((item) => !item.done)
  if (props.result.activitiesLeft > 0 && activity) {
    const to = classActivityRoute(props.result.quiz.classId, props.result.quiz.lessonId, activity)
    return { to, label: t('classes.continueNamed', { title: activity.title }), finishesLesson: false }
  }
  const finishesLesson =
    props.justSubmitted &&
    props.result.lessonStatus === 'COMPLETED' &&
    available !== null &&
    available.status !== 'COMPLETED'
  const label = t(finishesLesson ? 'classPlayer.finishLesson' : 'classPlayer.openLesson')
  return { to: props.lessonRoute, label, finishesLesson }
})

const resultNote = computed(() => {
  if (!props.playableLesson) return ''
  if (props.result.activitiesLeft > 0) return t('classPlayer.result.activitiesLeft', props.result.activitiesLeft)
  return props.result.lessonStatus === 'COMPLETED' ? t('classPlayer.result.lessonCompleted') : ''
})

function finishLesson(event: MouseEvent): void {
  if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
  if (resultNext.value.finishesLesson) {
    queueClassNotice({ message: t('classes.lessonCompletedNotice'), targetRoute: 'student-class-lesson' })
  }
}
</script>
