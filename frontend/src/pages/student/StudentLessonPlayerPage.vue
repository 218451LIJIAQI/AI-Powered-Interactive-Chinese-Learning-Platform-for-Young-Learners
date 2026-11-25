<template>
  <div class="learn-lesson-page">
    <AppToast :message="toast" />
    <FocusTopBar
      :label="topLabel"
      :close-label="t('learn.leaveLesson')"
      :close-disabled="savingResult"
      :progress="progress"
      :progress-label="t('learn.lessonProgress')"
      @close="requestLeave"
    />

    <h1 class="sr-only">{{ lesson.data.value?.title ?? t('learn.lesson') }}</h1>

    <div ref="stageRef" class="focus-stage" :class="{ 'learn-done-stage': result !== null }">
      <StatePanel
        v-if="lesson.loading.value"
        heading-tag="h2"
        tone="loading"
        :title="t('learn.lessonLoading')"
        :message="t('learn.lessonLoadingHint')"
        spacious
      />
      <StatePanel
        v-else-if="lesson.error.value || !questions.length"
        heading-tag="h2"
        tone="error"
        :icon="isLocked ? 'lock' : 'alert'"
        :title="t('learn.lessonUnavailable')"
        :message="lesson.error.value ?? t('learn.lessonEmptyMessage')"
        spacious
      >
        <template #actions>
          <button v-if="canRetry" class="btn btn-secondary" type="button" @click="lesson.reload">
            {{ t('common.retry') }}
          </button>
          <RouterLink class="btn btn-primary" :to="{ name: 'student-learn' }">{{ t('learn.backToPath') }}</RouterLink>
        </template>
      </StatePanel>
      <LessonDone v-else-if="result" :result="result" />
      <SayStep v-else-if="saying && sayPhrase" v-bind="sayPhrase" />
      <QuestionStage
        v-else-if="question"
        v-model="answer"
        :question="question"
        :locked="checking || check !== null"
        :check="check"
        :ruled-out="hint.ruledOut.value"
      />

      <p v-if="actionError" class="callout is-danger" role="alert">{{ actionError }}</p>
    </div>

    <FocusFooter v-if="result">
      <template #start>
        <RouterLink
          v-if="result.accuracy < 100"
          class="btn btn-secondary btn-lg"
          :to="{ name: 'student-practice', params: { type: 'mistakes' } }"
        >
          {{ t('common.reviewMistakes') }}
        </RouterLink>
      </template>
      <template #end>
        <button
          class="btn btn-lg"
          :class="result.passed ? 'btn-primary' : 'btn-secondary'"
          type="button"
          data-testid="lesson-continue"
          @click="finish"
        >
          {{ t('learn.continue') }}
        </button>
        <button v-if="!result.passed" class="btn btn-primary btn-lg" type="button" @click="resetRun">
          {{ t('learn.tryAgain') }}
        </button>
      </template>
    </FocusFooter>
    <!-- Saying it is not graded: Continue moves on whether or not anything was recorded. -->
    <FocusFooter v-else-if="saying">
      <template #end>
        <button
          class="btn btn-primary btn-lg"
          type="button"
          :disabled="checking"
          :aria-busy="checking"
          data-testid="say-done"
          @click="complete"
        >
          {{ t('learn.continue') }}
        </button>
      </template>
    </FocusFooter>
    <CheckBar
      v-else-if="question"
      :check="check"
      :can-check="answer !== null"
      :checking="checking"
      :skipped="check !== null && answer === null"
      :answer-line="question.answerKind === 'BUILD'"
      :hint="isCheckpoint ? t('learn.checkpointHint', { percent: lesson.data.value?.passPercent }) : ''"
      :continue-label="isLast && check && !sayPhrase ? t('common.seeResult') : ''"
      @check="submit(answer)"
      @skip="submit(null)"
      @continue="next"
    >
      <!-- The hint of a stuck learner sits in the footer, so the question above it never moves; until then the footer
           names the keys. Checkpoints have no hints. -->
      <template v-if="!isCheckpoint" #middle>
        <QuestionHint
          :question="question"
          :hint="hint"
          :report-context="`LEARN #${lessonId} / ${question.id}`"
          @reported="showToast(t('aiSafety.reportSent'))"
        />
      </template>
    </CheckBar>

    <RankUpDialog
      v-if="rankingUp && result?.xp.rankUp"
      :rank-up="result.xp.rankUp"
      :streak="result.xp.currentStreak"
      source="lesson"
      @close="goToPath"
    />

    <ConfirmDialog
      :open="leaving"
      :title="t('learn.leaveLessonTitle')"
      :message="t('learn.leaveLessonMessage')"
      :confirm-label="t('learn.leave')"
      :cancel-label="t('learn.keepGoing')"
      @confirm="leave"
      @cancel="leaving = false"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { learnApi } from '@/api/learn'
import AppToast from '@/components/common/AppToast.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import FocusFooter from '@/components/layout/FocusFooter.vue'
import FocusTopBar from '@/components/layout/FocusTopBar.vue'
import CheckBar from '@/components/learn/CheckBar.vue'
import LessonDone from '@/components/learn/LessonDone.vue'
import QuestionHint from '@/components/learn/QuestionHint.vue'
import QuestionStage from '@/components/learn/QuestionStage.vue'
import RankUpDialog from '@/components/learn/RankUpDialog.vue'
import SayStep from '@/components/learn/SayStep.vue'
import { useQuestionRun } from '@/components/learn/useQuestionRun'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useRecordById } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { learnMessages } from '@/plugins/learnMessages'
import { learnSharedMessages } from '@/plugins/learnSharedMessages'
import type { AnswerCheck, QuestionView } from '@/types/learn'
import { hasHan } from '@/utils/cjkText'
import { stepProgress } from '@/utils/format'
import { parseRecordId } from '@/utils/recordId'
import '@/styles/learn-shared.css'
import '@/styles/learn-lesson.css'
import { finishedLessonState } from './finishedLesson'

/**
 * A Learn lesson in focus mode: one question at a time (the footer names the keys, and offers SparkBot's hint once the
 * learner is stuck), the verdict straight after each answer (checked on the server), a last ungraded step to say a
 * phrase from the lesson aloud, and then the completion moment: the seal, the XP earned and accuracy, the streak, what
 * unlocked and any new badge. Continue goes back to the map, through the rank-up dialog when the lesson passed a rank.
 */
const route = useRoute()
const router = useRouter()
const { t } = useLocalMessages({ learn: [learnSharedMessages, learnMessages] })
const { toast, showToast } = useToast()

const lessonId = computed(() => parseRecordId(route.params.lessonId))
const lesson = useRecordById(lessonId, learnApi.lesson)

const leaving = ref(false)
const stageRef = ref<HTMLElement | null>(null)

const questions = computed(() => lesson.data.value?.questions ?? [])
const isCheckpoint = computed(() => lesson.data.value?.kind === 'CHECKPOINT')
const isLocked = computed(() => lesson.errorStatus.value === 409)
const canRetry = computed(
  () => Boolean(lesson.error.value) && lesson.errorStatus.value !== 409 && lesson.errorStatus.value !== 404,
)

/** The top bar's fixed column names the lesson by the part of its title before " · ". */
const topLabel = computed(() => lesson.data.value?.title.split(' · ')[0] ?? t('learn.lesson'))

// Saying a phrase from the lesson: not graded; a checkpoint is a test and has no such step.

interface SayPhrase {
  hanzi: string
  pinyin: string | null
  meaning: string | null
}

const saying = ref(false)
const sayPhrase = ref<SayPhrase | null>(null)

/** The Chinese of an answered question: shown on the question, or else revealed by the answer. */
function phraseOf(asked: QuestionView, verdict: AnswerCheck): SayPhrase | null {
  const hanzi = asked.hanzi ?? (hasHan(verdict.correctAnswer) ? verdict.correctAnswer : null)
  if (!hanzi) return null
  return { hanzi, pinyin: asked.pinyin ?? verdict.pinyin, meaning: asked.meaning ?? verdict.meaning }
}

const {
  index,
  answer,
  check,
  result,
  busy: checking,
  savingResult,
  actionError,
  answers,
  question,
  isLast,
  hint,
  submit,
  next,
  complete,
  reset,
} = useQuestionRun({
  questions,
  grade: (sent) =>
    lessonId.value === null
      ? Promise.reject(new Error('Missing lesson id'))
      : learnApi.checkLesson(lessonId.value, sent),
  saveResult: (sent) =>
    lessonId.value === null ? Promise.resolve(null) : learnApi.completeLesson(lessonId.value, sent),
  t,
  onChecked: (current, verdict) => {
    if (!isCheckpoint.value) sayPhrase.value = phraseOf(current, verdict) ?? sayPhrase.value
  },
  onLastQuestion: async () => {
    if (sayPhrase.value) saying.value = true
    else await complete()
  },
  keysBlocked: () => leaving.value || saying.value,
  hintsAllowed: () => !isCheckpoint.value,
  loadHint: (id) =>
    lessonId.value === null
      ? Promise.reject(new Error('Missing lesson id'))
      : learnApi.hint(lessonId.value, id, lesson.data.value),
})

const progress = computed(() => {
  if (result.value) return 100
  if (!questions.value.length) return null
  const total = questions.value.length + (isCheckpoint.value ? 0 : 1)
  return stepProgress(index.value, total, check.value !== null)
})

// The result screen

/** The rank-up dialog is open: the lesson took the learner past a rank, and Keep going goes on to the map. */
const rankingUp = ref(false)

watch(
  result,
  (next) => {
    if (!next || (document.activeElement && document.activeElement !== document.body)) return
    stageRef.value?.querySelector<HTMLElement>('h2[tabindex="-1"]')?.focus({ preventScroll: true })
  },
  { flush: 'post' },
)

function resetRun(): void {
  reset()
  saying.value = false
  sayPhrase.value = null
  leaving.value = false
  rankingUp.value = false
}

watch(lessonId, resetRun)

/** Continue: back to the map, which says what the lesson earned and unlocked; first the rank-up when there is one. */
function finish(): void {
  if (result.value?.xp.rankUp && !rankingUp.value) {
    rankingUp.value = true
    return
  }
  goToPath()
}

function goToPath(): void {
  rankingUp.value = false
  void router.push({ name: 'student-learn', state: finishedLessonState(result.value) })
}

// Leaving

function requestLeave(): void {
  if (savingResult.value) return
  // A finished lesson is saved: closing is the same as Continue, so the map still says what unlocked.
  if (result.value) finish()
  else if (!answers.value.length && answer.value === null) leave()
  else leaving.value = true
}

function leave(): void {
  leaving.value = false
  void router.push({ name: 'student-learn' })
}
</script>
