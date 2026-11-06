<template>
  <div class="learn-lesson-page">
    <FocusTopBar
      :label="topLabel"
      :close-label="t('learn.leaveLesson')"
      :progress="progress"
      :progress-label="t('learn.lessonProgress')"
      @close="requestLeave"
    />

    <h1 class="sr-only">{{ lesson.data.value?.title ?? t('learn.lesson') }}</h1>

    <div class="focus-stage" :class="{ 'learn-done-stage': result !== null }">
      <StatePanel
        v-if="lesson.loading.value"
        tone="loading"
        :title="t('learn.lessonLoading')"
        :message="t('learn.lessonLoadingHint')"
        spacious
      />
      <StatePanel
        v-else-if="lesson.error.value"
        tone="error"
        icon="lock"
        :title="t('learn.lessonUnavailable')"
        :message="lesson.error.value"
        spacious
      >
        <template #actions>
          <RouterLink class="btn btn-primary" :to="{ name: 'student-learn' }">{{ t('learn.backToPath') }}</RouterLink>
        </template>
      </StatePanel>

      <section v-else-if="result" class="learn-done" data-testid="lesson-complete" aria-live="polite">
        <StampSeal
          :character="result.passed ? '棒' : '加'"
          :caption="result.passed ? t('learn.bangCaption') : t('learn.jiayouCaption')"
        />
        <h2 class="focus-headline">{{ completeTitle }}</h2>
        <p class="learn-done-sub">
          {{ t('learn.completeSub', { title: result.title, unit: result.unitNumber, position: result.position }) }}
        </p>
        <dl class="learn-done-stats">
          <div class="is-xp">
            <dt>{{ t('learn.xpEarned') }}</dt>
            <dd>+{{ result.xp.xpEarned + result.xp.bonusXp }}</dd>
          </div>
          <div>
            <dt>{{ t('learn.accuracy') }}</dt>
            <dd>{{ result.accuracy }}%</dd>
          </div>
          <div>
            <dt>{{ t('learn.time') }}</dt>
            <dd>{{ formatClock(result.durationSeconds) }}</dd>
          </div>
        </dl>
        <p v-if="!result.passed" class="learn-done-note">
          {{ t('learn.checkpointNotPassed', { need: result.passPercent ?? 70, got: result.accuracy }) }}
        </p>
        <p v-else-if="result.xp.xpEarned === 0" class="learn-done-note">{{ t('learn.alreadyEarned') }}</p>
        <p class="learn-done-streak">
          <span v-if="week.length" class="learn-done-week" aria-hidden="true">
            <span v-for="day in week" :key="day.date" :class="{ 'is-reached': day.goalReached }" />
          </span>
          <span>{{ streakLine }}</span>
        </p>
        <p v-if="result.nextLesson && result.passed" class="learn-done-next">
          <span class="learn-done-label">{{
            result.nextLesson.unlockedNow ? t('learn.unlocked') : t('learn.upNext')
          }}</span>
          <span class="learn-done-next-title">{{ t('learn.nextUp', { title: result.nextLesson.title }) }}</span>
        </p>
        <ul v-if="newBadges.length" class="learn-done-badges">
          <li v-for="badge in newBadges" :key="badge">
            <IconGlyph :name="BADGES[badge].icon" :size="16" />
            {{ t('learn.newBadge', { badge: t(BADGES[badge].nameKey) }) }}
          </li>
        </ul>
      </section>

      <SayStep v-else-if="saying && sayPhrase" v-bind="sayPhrase" />

      <QuestionStage
        v-else-if="question"
        v-model="answer"
        :question="question"
        :locked="check !== null"
        :check="check"
      />

      <p v-if="actionError" class="alert alert-danger" role="alert">{{ actionError }}</p>
    </div>

    <FocusFooter v-if="result">
      <template #start>
        <RouterLink
          v-if="wrongCount > 0"
          class="btn btn-secondary btn-lg"
          :to="{ name: 'student-practice', params: { type: 'mistakes' } }"
        >
          {{ t('learn.seeMistakesShort') }}
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
        <button v-if="!result.passed" class="btn btn-primary btn-lg" type="button" @click="restart">
          {{ t('learn.tryAgain') }}
        </button>
      </template>
    </FocusFooter>
    <FocusFooter v-else-if="saying">
      <template #start>
        <button
          class="btn btn-secondary btn-lg"
          type="button"
          :disabled="checking"
          data-testid="say-skip"
          @click="complete"
        >
          {{ t('learn.skip') }}
        </button>
      </template>
      <template #end>
        <button
          class="btn btn-primary btn-lg"
          type="button"
          :disabled="checking"
          :aria-busy="checking"
          data-testid="say-done"
          @click="complete"
        >
          {{ t('learn.done') }}
        </button>
      </template>
    </FocusFooter>
    <CheckBar
      v-else-if="question"
      :check="check"
      :can-check="answer !== null"
      :checking="checking"
      :hint="
        lesson.data.value?.kind === 'CHECKPOINT'
          ? t('learn.checkpointHint', { percent: lesson.data.value.passPercent ?? 70 })
          : ''
      "
      :continue-label="isLast && check && !sayPhrase ? t('learn.finishLesson') : ''"
      @check="submit(answer)"
      @skip="submit(null)"
      @continue="next"
    />

    <ConfirmDialog
      tone="leave"
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
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { learnApi, xpApi } from '@/api/learn'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import FocusFooter from '@/components/layout/FocusFooter.vue'
import FocusTopBar from '@/components/layout/FocusTopBar.vue'
import CheckBar from '@/components/learn/CheckBar.vue'
import QuestionStage from '@/components/learn/QuestionStage.vue'
import SayStep from '@/components/learn/SayStep.vue'
import StampSeal from '@/components/learn/StampSeal.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { notifyProgressChanged } from '@/composables/useProgressRefresh'
import { useResource } from '@/composables/useResource'
import { learnMessages } from '@/plugins/learnMessages'
import type { AnswerCheck, LessonResult, QuestionAnswer, QuestionView, TodaySummary } from '@/types/learn'
import { resolveApiError } from '@/utils/apiError'
import { formatClock, stepProgress } from '@/utils/format'
import { BADGES } from '@/utils/learnBadges'
import '@/styles/learn.css'

/**
 * A Learn lesson in focus mode: one question at a time, the footer with the verdict straight after each answer (checked
 * on the server), a last ungraded step to say a phrase from the lesson aloud, and then the completion moment: the seal,
 * the XP earned, accuracy and time, the streak, what unlocked and any new badge, then back to the path.
 */
const route = useRoute()
const router = useRouter()
const { t } = useLocalMessages({ learn: learnMessages })

const lessonId = computed(() => Number(route.params.lessonId))
const lesson = useResource(() => learnApi.lesson(lessonId.value))

const index = ref(0)
const answer = ref<string | null>(null)
const check = ref<AnswerCheck | null>(null)
const checking = ref(false)
const actionError = ref<string | null>(null)
const answers = ref<QuestionAnswer[]>([])
const wrongCount = ref(0)
const result = ref<LessonResult | null>(null)
const week = ref<TodaySummary['week']>([])
const leaving = ref(false)
let startedAt = Date.now()

const questions = computed(() => lesson.data.value?.questions ?? [])
const question = computed(() => questions.value[index.value] ?? null)
const isLast = computed(() => index.value >= questions.value.length - 1)

/** The top bar's fixed column names the lesson by the part of its title before " · ". */
const topLabel = computed(() => lesson.data.value?.title.split(' · ')[0] ?? t('learn.lesson'))

// ----- saying a phrase from the lesson (not graded; a checkpoint is a test and has no such step) -----

interface SayPhrase {
  hanzi: string
  pinyin: string | null
  meaning: string | null
}

const saying = ref(false)
const sayPhrase = ref<SayPhrase | null>(null)

/** The Chinese of an answered question: shown on the question, or else revealed by the answer. */
function phraseOf(asked: QuestionView, verdict: AnswerCheck): SayPhrase | null {
  const hanzi = asked.hanzi ?? (/\p{Script=Han}/u.test(verdict.correctAnswer) ? verdict.correctAnswer : null)
  if (!hanzi) return null
  return { hanzi, pinyin: asked.pinyin ?? verdict.pinyin, meaning: asked.meaning ?? verdict.meaning }
}

const hasSayStep = computed(() => lesson.data.value?.kind !== 'CHECKPOINT')

const progress = computed(() => {
  if (result.value && questions.value.length) return 100
  const total = questions.value.length + (hasSayStep.value ? 1 : 0)
  return stepProgress(index.value, total, check.value !== null)
})

// ----- answering -----

async function submit(given: string | null): Promise<void> {
  const current = question.value
  if (!current || checking.value) return
  checking.value = true
  actionError.value = null
  try {
    const verdict = await learnApi.checkLesson(lessonId.value, { questionId: current.id, answer: given })
    check.value = verdict
    answer.value = given
    answers.value.push({ questionId: current.id, answer: given })
    if (!verdict.correct) wrongCount.value += 1
    if (hasSayStep.value) sayPhrase.value = phraseOf(current, verdict) ?? sayPhrase.value
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
  if (sayPhrase.value) {
    saying.value = true
    return
  }
  await complete()
}

async function complete(): Promise<void> {
  checking.value = true
  actionError.value = null
  try {
    const seconds = Math.round((Date.now() - startedAt) / 1000)
    result.value = await learnApi.completeLesson(lessonId.value, answers.value, seconds)
    saying.value = false
    notifyProgressChanged()
    week.value = (await xpApi.today().catch(() => null))?.week ?? []
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('learn.completeFailed'))
  } finally {
    checking.value = false
  }
}

function restart(): void {
  index.value = 0
  answer.value = null
  check.value = null
  answers.value = []
  wrongCount.value = 0
  result.value = null
  saying.value = false
  sayPhrase.value = null
  startedAt = Date.now()
}

const completeTitle = computed(() => {
  const value = result.value
  if (!value) return ''
  if (value.kind === 'CHECKPOINT') return value.passed ? t('learn.checkpointPassed') : t('learn.checkpointFailed')
  return t('learn.lessonComplete')
})

const streakLine = computed(() => {
  const xp = result.value?.xp
  if (!xp) return ''
  const goal = t('learn.goalProgress', { xp: xp.todayXp, goal: xp.dailyGoalXp })
  if (xp.goalReached) return t('learn.streakGoalReached', { days: xp.currentStreak, goal }, xp.currentStreak)
  return t('learn.streakGoalPending', { xp: Math.max(0, xp.dailyGoalXp - xp.todayXp), goal })
})

const newBadges = computed(() => result.value?.xp.newBadges ?? [])

function finish(): void {
  void router.push({ name: 'student-learn', state: { lessonResult: JSON.parse(JSON.stringify(result.value)) } })
}

// ----- leaving -----

function requestLeave(): void {
  // A finished lesson is saved: closing is the same as Continue, so the path still says what unlocked.
  if (result.value) {
    finish()
    return
  }
  if (!answers.value.length && answer.value === null) {
    void router.push({ name: 'student-learn' })
    return
  }
  leaving.value = true
}

function leave(): void {
  leaving.value = false
  void router.push({ name: 'student-learn' })
}

/** Enter checks or continues; 1–9 picks an option. */
function onKeydown(event: KeyboardEvent): void {
  const target = event.target as HTMLElement | null
  if (leaving.value || result.value || saying.value || !question.value) return
  if (target && ['INPUT', 'TEXTAREA', 'BUTTON', 'A'].includes(target.tagName) && event.key === 'Enter') return
  if (event.key === 'Enter') {
    if (check.value) void next()
    else if (answer.value !== null) void submit(answer.value)
    return
  }
  const number = Number(event.key)
  const options = question.value.options
  if (!check.value && question.value.answerKind === 'CHOICE' && number >= 1 && number <= options.length) {
    answer.value = options[number - 1]!.text
  }
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>
