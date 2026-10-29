<template>
  <div class="learn-player practice-review">
    <FocusTopBar
      :label="topLabel"
      :close-label="t('learn.leaveReview')"
      :progress="progress"
      :progress-label="t('learn.reviewProgress')"
      @close="requestLeave"
    />
    <h1 class="sr-only">{{ t('learn.reviewTitle') }}</h1>

    <div class="learn-player-body">
      <StatePanel
        v-if="review.loading.value"
        tone="loading"
        :title="t('learn.reviewLoading')"
        :message="t('learn.reviewLoadingHint')"
        spacious
      />
      <StatePanel
        v-else-if="review.error.value"
        tone="error"
        icon="alert"
        :title="t('learn.reviewError')"
        :message="review.error.value"
        spacious
      >
        <template #actions>
          <button class="btn btn-primary" type="button" @click="review.reload">{{ t('learn.retry') }}</button>
        </template>
      </StatePanel>
      <StatePanel
        v-else-if="!questions.length && !result"
        tone="empty"
        icon="check"
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

      <section v-else-if="result" class="practice-review-done" data-testid="review-complete" aria-live="polite">
        <figure class="practice-review-stamp">
          <span
            class="seal practice-review-seal"
            :class="{ 'is-outline': result.cleared === 0 }"
            lang="zh-CN"
            aria-hidden="true"
            >{{ result.cleared > 0 ? '好' : '加' }}</span
          >
          <figcaption class="practice-review-caption">
            {{ result.cleared > 0 ? t('learn.haoCaption') : t('practice.jiayouCaption') }}
          </figcaption>
        </figure>
        <h2 class="practice-review-title">{{ allFixed ? t('practice.allFixed') : t('learn.reviewComplete') }}</h2>
        <p class="practice-review-sub">{{ allFixed ? t('practice.allFixedSub') : t('learn.reviewCompleteSub') }}</p>

        <ol class="practice-review-tally" :aria-label="t('practice.reviewTally')">
          <li v-for="mark in tally" :key="mark.n" :class="mark.className">
            <IconGlyph v-if="mark.icon" :name="mark.icon" :size="14" />
            <span class="sr-only">{{ mark.label }}</span>
          </li>
        </ol>

        <dl class="practice-review-stats">
          <div>
            <dt>{{ t('learn.xpEarned') }}</dt>
            <dd class="is-xp">+{{ result.xp.xpEarned + result.xp.bonusXp }}</dd>
          </div>
          <div>
            <dt>{{ t('learn.cleared') }}</dt>
            <dd>{{ result.cleared }} / {{ result.answered }}</dd>
          </div>
          <div>
            <dt>{{ t('learn.leftForLater') }}</dt>
            <dd>{{ result.left }}</dd>
          </div>
        </dl>
        <p v-if="result.cleared > 0 && result.xp.xpEarned === 0" class="practice-review-note">
          {{ t('learn.reviewXpOncePerDay') }}
        </p>
        <p v-if="result.cleared === 0" class="practice-review-note">{{ t('learn.reviewNoneCleared') }}</p>
        <p class="practice-review-streak">
          <IconGlyph name="streak" :size="16" />
          {{ streakLine }}
        </p>
        <p v-if="result.left > 0" class="practice-review-later">
          <strong>{{ t('learn.stillOnList') }}</strong>
          {{ t('learn.mistakesToTryAgain', { count: result.left }, result.left) }}
        </p>
      </section>

      <template v-else-if="question">
        <div class="practice-review-track">
          <ol class="practice-review-tally" :aria-label="t('practice.reviewTally')">
            <li v-for="mark in tally" :key="mark.n" :class="mark.className">
              <IconGlyph v-if="mark.icon" :name="mark.icon" :size="14" />
              <span class="sr-only">{{ mark.label }}</span>
            </li>
          </ol>
          <p class="practice-review-left">{{ t('practice.reviewLeft', { count: left }, left) }}</p>
        </div>
        <QuestionStage
          v-model="answer"
          :question="question"
          :locked="check !== null"
          :check="check"
          :context="contextLine"
        />
      </template>
    </div>

    <div v-if="result" class="learn-player-footer">
      <RouterLink class="btn btn-outline btn-lg" :to="{ name: 'student-learn' }">{{
        t('learn.continueLearning')
      }}</RouterLink>
      <RouterLink
        class="btn btn-primary btn-lg"
        :to="{ name: 'student-practice', params: { type: 'mistakes' } }"
        data-testid="review-done"
      >
        {{ t('learn.done') }}
      </RouterLink>
    </div>
    <CheckBar
      v-else-if="question"
      :check="check"
      :can-check="answer !== null"
      :checking="checking"
      :continue-label="isLast && check ? t('learn.finishReview') : ''"
      @check="submit(answer)"
      @skip="submit(null)"
      @continue="next"
    />
    <p v-if="actionError" class="alert alert-danger learn-player-error" role="alert">{{ actionError }}</p>

    <ConfirmDialog
      tone="leave"
      :open="leaving"
      :title="t('learn.leaveReviewTitle')"
      :message="t('learn.leaveReviewMessage')"
      :confirm-label="t('learn.leave')"
      :cancel-label="t('learn.keepGoing')"
      @confirm="leave"
      @cancel="leaving = false"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { mistakesApi } from '@/api/learn'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import FocusTopBar from '@/components/layout/FocusTopBar.vue'
import CheckBar from '@/components/learn/CheckBar.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import QuestionStage from '@/components/learn/QuestionStage.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { notifyProgressChanged } from '@/composables/useProgressRefresh'
import { useResource } from '@/composables/useResource'
import { learnMessages } from '@/plugins/learnMessages'
import { practiceMessages } from '@/plugins/practiceMessages'
import type { AnswerCheck, MistakeReviewResult, QuestionAnswer } from '@/types/learn'
import { resolveApiError } from '@/utils/apiError'
import { stepProgress } from '@/utils/format'
import '@/styles/learn.css'
import '@/styles/learn-practice.css'

const router = useRouter()
const { t } = useLocalMessages({ learn: learnMessages, practice: practiceMessages })

const review = useResource(() => mistakesApi.review())
const index = ref(0)
const answer = ref<string | null>(null)
const check = ref<AnswerCheck | null>(null)
const checking = ref(false)
const actionError = ref<string | null>(null)
const answers = ref<QuestionAnswer[]>([])
/** Whether each answer so far was right, in order. */
const verdicts = ref<boolean[]>([])
const result = ref<MistakeReviewResult | null>(null)
const leaving = ref(false)

const questions = computed(() => review.data.value?.questions ?? [])
const question = computed(() => questions.value[index.value] ?? null)
const isLast = computed(() => index.value >= questions.value.length - 1)
const progress = computed(() => stepProgress(index.value, questions.value.length, check.value !== null))

const topLabel = computed(() =>
  questions.value.length && !result.value
    ? t('learn.reviewCounter', { current: index.value + 1, total: questions.value.length })
    : t('learn.reviewTitle'),
)

/** Mistakes of this round still to answer: it counts down to zero as each one is checked. */
const left = computed(() => Math.max(0, questions.value.length - answers.value.length))
const allFixed = computed(() => result.value !== null && result.value.left === 0 && result.value.cleared > 0)

/** One mark per mistake of the round: right or not yet (once checked), the current one, or still to come. */
const tally = computed(() =>
  questions.value.map((_, position) => {
    const n = position + 1
    const verdict = verdicts.value[position]
    if (verdict !== undefined) {
      return verdict
        ? { n, className: 'is-right', icon: 'check', label: t('practice.tallyRight', { n }) }
        : { n, className: 'is-wrong', icon: 'close', label: t('practice.tallyWrong', { n }) }
    }
    if (position === index.value && !result.value) {
      return { n, className: 'is-now', icon: '', label: t('practice.tallyNow', { n }) }
    }
    return { n, className: 'is-ahead', icon: '', label: t('practice.tallyAhead', { n }) }
  }),
)

const contextLine = computed(() => {
  const context = review.data.value?.context[index.value]
  if (!context) return ''
  const source = context.fromLearn
    ? t('learn.fromLearn', { title: context.sourceTitle })
    : t('learn.fromPractice', { title: context.sourceTitle })
  return context.yourAnswer
    ? `${source} · ${t('learn.youAnsweredLastTime', { answer: context.yourAnswer.replace(/\s+/g, '') })}`
    : `${source} · ${t('learn.youSkippedLastTime')}`
})

async function submit(given: string | null): Promise<void> {
  const current = question.value
  if (!current || checking.value) return
  checking.value = true
  actionError.value = null
  try {
    const verdict = await mistakesApi.check({ questionId: current.id, answer: given })
    check.value = verdict
    answer.value = given
    answers.value.push({ questionId: current.id, answer: given })
    verdicts.value.push(verdict.correct)
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('learn.checkFailed'))
  } finally {
    checking.value = false
  }
}

async function next(): Promise<void> {
  if (!isLast.value) {
    index.value += 1
    answer.value = null
    check.value = null
    return
  }
  checking.value = true
  actionError.value = null
  try {
    result.value = await mistakesApi.complete(answers.value)
    notifyProgressChanged()
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('learn.completeFailed'))
  } finally {
    checking.value = false
  }
}

const streakLine = computed(() => {
  const xp = result.value?.xp
  if (!xp) return ''
  const goal = t('learn.goalProgress', { xp: xp.todayXp, goal: xp.dailyGoalXp })
  if (xp.goalReached) return t('learn.streakGoalReached', { days: xp.currentStreak, goal }, xp.currentStreak)
  return t('learn.streakGoalPending', { xp: Math.max(0, xp.dailyGoalXp - xp.todayXp), goal })
})

function requestLeave(): void {
  if (result.value || !answers.value.length) {
    void router.push({ name: 'student-practice', params: { type: 'mistakes' } })
    return
  }
  leaving.value = true
}

function leave(): void {
  leaving.value = false
  void router.push({ name: 'student-practice', params: { type: 'mistakes' } })
}
</script>
