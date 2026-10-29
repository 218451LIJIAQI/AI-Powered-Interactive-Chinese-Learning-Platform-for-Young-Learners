<template>
  <div class="learn-player learn-lesson-page">
    <FocusTopBar
      :label="lesson.data.value?.title ?? t('learn.lesson')"
      :close-label="t('learn.leaveLesson')"
      :progress="progress"
      :progress-label="t('learn.lessonProgress')"
      @close="requestLeave"
    />

    <h1 class="sr-only">{{ lesson.data.value?.title ?? t('learn.lesson') }}</h1>

    <div class="learn-player-body" :class="{ 'is-done': result !== null }">
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
        <div class="learn-done-copy">
          <h2 class="learn-done-title">{{ completeTitle }}</h2>
          <p class="learn-done-sub">
            {{ t('learn.completeSub', { title: result.title, unit: result.unitNumber, position: result.position }) }}
          </p>
        </div>
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
        <ul class="learn-done-after">
          <li>
            <IconGlyph name="streak" :size="18" />
            <span>{{ streakLine }}</span>
          </li>
          <li v-if="result.nextLesson && result.passed" class="is-next">
            <IconGlyph :name="result.nextLesson.unlockedNow ? 'unlock' : 'route'" :size="18" />
            <span class="learn-done-next">
              <span class="learn-done-label">{{
                result.nextLesson.unlockedNow ? t('learn.unlocked') : t('learn.upNext')
              }}</span>
              <span>{{ t('learn.nextUp', { title: result.nextLesson.title }) }}</span>
            </span>
          </li>
          <li v-for="badge in newBadges" :key="badge">
            <IconGlyph :name="BADGES[badge].icon" :size="18" />
            <span>{{ t('learn.newBadge', { badge: t(BADGES[badge].nameKey) }) }}</span>
          </li>
        </ul>
      </section>

      <QuestionStage
        v-else-if="question"
        v-model="answer"
        :question="question"
        :locked="check !== null"
        :check="check"
      />
    </div>

    <div v-if="result" class="learn-player-footer">
      <RouterLink
        v-if="wrongCount > 0"
        class="btn btn-outline btn-lg"
        :to="{ name: 'student-practice', params: { type: 'mistakes' } }"
      >
        {{ t('learn.seeMistakesShort') }}
      </RouterLink>
      <button v-if="!result.passed" class="btn btn-outline btn-lg" type="button" @click="restart">
        {{ t('learn.tryAgain') }}
      </button>
      <button class="btn btn-primary btn-lg" type="button" data-testid="lesson-continue" @click="finish">
        {{ t('learn.continue') }}
      </button>
    </div>
    <CheckBar
      v-else-if="question"
      :check="check"
      :can-check="answer !== null"
      :checking="checking"
      :hint="
        lesson.data.value?.kind === 'CHECKPOINT'
          ? t('learn.checkpointHint', { percent: lesson.data.value.passPercent ?? 70 })
          : t('learn.questionCounter', { current: index + 1, total: questions.length })
      "
      :continue-label="isLast && check ? t('learn.finishLesson') : ''"
      @check="submit(answer)"
      @skip="submit(null)"
      @continue="next"
    />
    <p v-if="actionError" class="alert alert-danger learn-player-error" role="alert">{{ actionError }}</p>

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

import { learnApi } from '@/api/learn'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import FocusTopBar from '@/components/layout/FocusTopBar.vue'
import CheckBar from '@/components/learn/CheckBar.vue'
import QuestionStage from '@/components/learn/QuestionStage.vue'
import StampSeal from '@/components/learn/StampSeal.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { notifyProgressChanged } from '@/composables/useProgressRefresh'
import { useResource } from '@/composables/useResource'
import { learnMessages } from '@/plugins/learnMessages'
import type { AnswerCheck, LessonResult, QuestionAnswer } from '@/types/learn'
import { resolveApiError } from '@/utils/apiError'
import { formatClock, stepProgress } from '@/utils/format'
import { BADGES } from '@/utils/learnBadges'
import '@/styles/learn.css'

/**
 * A Learn lesson in focus mode: one question at a time on a quiet stage, the check bar pinned at the bottom with the
 * verdict straight after each answer (checked on the server), and at the end the completion moment: the seal is
 * stamped, the XP earned, accuracy and time, the streak, what unlocked and any new badge, then back to the path.
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
const leaving = ref(false)
let startedAt = Date.now()

const questions = computed(() => lesson.data.value?.questions ?? [])
const question = computed(() => questions.value[index.value] ?? null)
const isLast = computed(() => index.value >= questions.value.length - 1)
const progress = computed(() =>
  result.value && questions.value.length
    ? 100
    : stepProgress(index.value, questions.value.length, check.value !== null),
)

async function submit(given: string | null): Promise<void> {
  const current = question.value
  if (!current || checking.value) return
  checking.value = true
  actionError.value = null
  try {
    check.value = await learnApi.checkLesson(lessonId.value, { questionId: current.id, answer: given })
    answer.value = given
    answers.value.push({ questionId: current.id, answer: given })
    if (!check.value.correct) wrongCount.value += 1
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
    const seconds = Math.round((Date.now() - startedAt) / 1000)
    result.value = await learnApi.completeLesson(lessonId.value, answers.value, seconds)
    notifyProgressChanged()
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
  if (result.value || !answers.value.length) {
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
  if (leaving.value || result.value || !question.value) return
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
