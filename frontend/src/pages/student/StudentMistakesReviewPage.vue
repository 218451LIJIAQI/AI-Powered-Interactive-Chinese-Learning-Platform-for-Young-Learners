<template>
  <div class="practice-review">
    <AppToast :message="toast" />
    <FocusTopBar
      :label="topLabel"
      :close-label="t('learn.leaveReview')"
      :close-disabled="savingResult"
      :progress="progress"
      :progress-label="t('learn.reviewProgress')"
      @close="requestLeave"
    />
    <h1 class="sr-only">{{ t('learn.reviewTitle') }}</h1>
    <p class="sr-only" role="status">{{ doneAnnouncement }}</p>

    <div class="focus-stage">
      <StatePanel
        v-if="review.loading.value"
        heading-tag="h2"
        tone="loading"
        :title="t('learn.reviewLoading')"
        :message="t('learn.reviewLoadingHint')"
        spacious
      />
      <StatePanel
        v-else-if="review.error.value"
        heading-tag="h2"
        tone="error"
        :title="t('learn.reviewError')"
        :message="review.error.value"
        spacious
        @retry="review.reload"
      />
      <StatePanel
        v-else-if="!questions.length"
        heading-tag="h2"
        tone="empty"
        icon="correct"
        :title="t('learn.nothingToReview')"
        :message="t('learn.nothingToReviewHint')"
        spacious
      >
        <template #actions>
          <RouterLink class="btn btn-primary" :to="{ name: 'student-practice' }">{{
            t('learn.goToPractice')
          }}</RouterLink>
        </template>
      </StatePanel>

      <section v-else-if="result" class="practice-review-done" data-testid="review-complete">
        <h2 ref="doneHeading" class="focus-headline" tabindex="-1">
          {{ allFixed ? t('learn.allFixed') : t('learn.reviewComplete') }}
        </h2>
        <dl class="learn-done-stats">
          <div class="is-xp">
            <dt>{{ t('learn.xpEarned') }}</dt>
            <dd>+{{ result.xp.xpEarned + result.xp.bonusXp }}</dd>
          </div>
          <div>
            <dt>{{ t('learn.cleared') }}</dt>
            <dd>{{ result.cleared }}</dd>
          </div>
          <div>
            <dt>{{ t('learn.leftForLater') }}</dt>
            <dd>{{ result.left }}</dd>
          </div>
        </dl>
        <p class="practice-review-streak">
          <IconGlyph name="streak" :size="16" />
          {{ doneLine }}
        </p>
      </section>

      <QuestionStage
        v-else-if="question"
        v-model="answer"
        :question="question"
        :locked="checking || check !== null"
        :check="check"
        :context="contextLine"
        :ruled-out="hint.ruledOut.value"
      />
      <div v-if="actionError" class="callout is-danger" role="alert">
        <IconGlyph name="alert" :size="18" />
        <span>{{ actionError }}</span>
        <button
          v-if="mistakeMissing"
          class="btn btn-secondary btn-sm"
          type="button"
          data-testid="review-reload"
          @click="reloadBatch"
        >
          {{ t('learn.reloadMistakes') }}
        </button>
      </div>
    </div>

    <FocusFooter v-if="result">
      <template #start>
        <RouterLink class="btn btn-secondary btn-lg" :to="{ name: 'student-learn' }">{{
          t('learn.continueLearning')
        }}</RouterLink>
      </template>
      <template #end>
        <RouterLink
          class="btn btn-primary btn-lg"
          :to="{ name: 'student-practice', params: { type: 'mistakes' } }"
          data-testid="review-done"
        >
          {{ t('learn.done') }}
        </RouterLink>
      </template>
    </FocusFooter>
    <CheckBar
      v-else-if="question"
      :check="check"
      :can-check="answer !== null"
      :checking="checking"
      :skipped="check !== null && answer === null"
      :answer-line="question.answerKind === 'BUILD'"
      :correct-title="t('learn.reviewCorrect')"
      :note="check && !check.correct ? t('learn.reviewStaysNote') : ''"
      :continue-label="isLast && check ? t('learn.finishReview') : ''"
      @check="submit(answer)"
      @skip="submit(null)"
      @continue="next"
    >
      <!-- As in a lesson: SparkBot's hint takes the footer's middle once the learner is stuck; until then, the keys. -->
      <template #middle>
        <QuestionHint
          :question="question"
          :hint="hint"
          :report-context="`MISTAKE #${question.id}`"
          @reported="showToast(t('aiSafety.reportSent'))"
        />
      </template>
    </CheckBar>

    <ConfirmDialog
      :open="leaving"
      :title="t('learn.leaveReviewTitle')"
      :message="t('learn.leaveReviewMessage')"
      :confirm-label="t('learn.leave')"
      :cancel-label="t('learn.keepGoing')"
      @confirm="leave"
      @cancel="leaving = false"
    />
    <RankUpDialog
      v-if="rankingUp && result?.xp.rankUp"
      :rank-up="result.xp.rankUp"
      :streak="result.xp.currentStreak"
      source="practice"
      @close="dismissRankUp"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { mistakesApi } from '@/api/learn'
import AppToast from '@/components/common/AppToast.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import FocusFooter from '@/components/layout/FocusFooter.vue'
import FocusTopBar from '@/components/layout/FocusTopBar.vue'
import CheckBar from '@/components/learn/CheckBar.vue'
import QuestionHint from '@/components/learn/QuestionHint.vue'
import { useRankUpLater } from '@/components/learn/practice/useRankUpLater'
import QuestionStage from '@/components/learn/QuestionStage.vue'
import RankUpDialog from '@/components/learn/RankUpDialog.vue'
import { useQuestionRun } from '@/components/learn/useQuestionRun'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { learnMessages } from '@/plugins/learnMessages'
import { learnSharedMessages } from '@/plugins/learnSharedMessages'
import { restoreFocusIfDropped } from '@/utils/focus'
import { stepProgress } from '@/utils/format'
import '@/styles/learn-shared.css'

/**
 * The mistakes review in focus mode: one waiting mistake at a time with where it came from (the lesson or set and the
 * answer given, or a flashcard marked "Still learning"), the verdict after Check or Skip, SparkBot's hint once the
 * learner is stuck; leaving asks first once something is answered. The done screen leads with the XP, the mistakes
 * cleared and those left for later; a review that passes a rank opens the rank-up dialog.
 */
const router = useRouter()
const { t } = useLocalMessages({ learn: [learnSharedMessages, learnMessages] })
const { toast, showToast } = useToast()

const review = useResource(mistakesApi.review)
const leaving = ref(false)
const doneHeading = ref<HTMLElement | null>(null)
const isUnmounted = useIsUnmounted()

const questions = computed(() => review.data.value?.questions ?? [])
const {
  index,
  answer,
  check,
  result,
  busy: checking,
  savingResult,
  actionError,
  actionErrorStatus,
  answers,
  question,
  isLast,
  hint,
  submit,
  next,
  reset,
} = useQuestionRun({
  questions,
  grade: mistakesApi.check,
  saveResult: mistakesApi.complete,
  t,
  keysBlocked: () => leaving.value,
  loadHint: (id) => mistakesApi.hint(id, review.data.value),
})
const mistakeMissing = computed(() => actionErrorStatus.value === 404)
const progress = computed(() => {
  if (result.value) return 100
  if (review.loading.value || review.error.value || !questions.value.length) return null
  return stepProgress(index.value, questions.value.length, check.value !== null)
})

const topLabel = computed(() =>
  questions.value.length && !result.value
    ? t('learn.reviewCounter', { current: index.value + 1, total: questions.value.length })
    : t('learn.reviewTitle'),
)

const allFixed = computed(() => result.value !== null && result.value.left === 0 && result.value.cleared > 0)

/** Where the mistake came from: a flashcard still being learned, or the lesson or set and the answer given. */
const contextLine = computed(() => {
  const context = review.data.value?.context[index.value]
  if (!context) return ''
  if (context.stillLearning) return t('learn.markedStillLearning')
  const source = t(context.fromLearn ? 'learn.fromLearn' : 'learn.fromPractice', { title: context.sourceTitle })
  // The server sends the last answer as the lesson and the Mistakes tab show it (Chinese tiles closed up).
  return context.yourAnswer
    ? `${source} · ${t('learn.youAnsweredLastTime', { answer: context.yourAnswer })}`
    : `${source} · ${t('learn.youSkippedLastTime')}`
})

/** "8-day streak · 30 / 30 XP today". */
const doneLine = computed(() => {
  const xp = result.value?.xp
  if (!xp) return ''
  const values = { xp: xp.todayXp, goal: xp.dailyGoalXp, days: xp.currentStreak }
  return xp.currentStreak > 0
    ? t('learn.reviewDoneLine', values, xp.currentStreak)
    : t('learn.reviewDoneLineNoStreak', values)
})

const doneAnnouncement = computed(() => {
  if (!result.value) return ''
  return `${allFixed.value ? t('learn.allFixed') : t('learn.reviewComplete')} ${doneLine.value}`.trim()
})

watch(
  result,
  (nextResult) => {
    if (nextResult) {
      void restoreFocusIfDropped(
        () => doneHeading.value,
        () => !isUnmounted(),
      )
    }
  },
  { flush: 'post' },
)

// A review that passed a rank says so a moment after its result appears.
const { rankingUp, dismiss: dismissRankUp } = useRankUpLater(() => result.value?.xp ?? null)

function reloadBatch(): void {
  reset()
  void review.reload()
}

function requestLeave(): void {
  if (savingResult.value) return
  if (result.value || (!answers.value.length && answer.value === null)) leave()
  else leaving.value = true
}

function leave(): void {
  leaving.value = false
  void router.push({ name: 'student-practice', params: { type: 'mistakes' } })
}
</script>

<style scoped>
/* The review question keeps its place between steps and leaves room for feedback in a short desktop window. */
.practice-review {
  --focus-label-width: 14rem;
  --focus-stage-top: clamp(var(--space-6), calc(45vh - 16rem), 152px);
}

.practice-review-done {
  display: grid;
  justify-items: center;
  gap: var(--space-4);
  text-align: center;
}

.practice-review-streak {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: var(--space-8);
  margin: 0;
  padding: var(--space-1) var(--space-4) var(--space-1) var(--space-3);
  border-radius: var(--radius-pill);
  background: var(--surface-sunken);
  font-size: var(--type-label-size);
  font-weight: var(--font-weight-strong);
}
</style>
