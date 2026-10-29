<template>
  <div class="home-page page-stack">
    <h1 v-if="!home" class="sr-only">{{ t('studentHome.pageTitle') }}</h1>

    <StatePanel
      v-if="resource.loading.value"
      tone="loading"
      :title="t('studentHome.loading')"
      :message="t('studentHome.loadingHint')"
      spacious
    />
    <StatePanel
      v-else-if="resource.error.value && !home"
      tone="error"
      icon="alert"
      :title="t('studentHome.loadError')"
      :message="resource.error.value"
      spacious
    >
      <template #actions>
        <button class="btn btn-primary" type="button" @click="resource.reload">{{ t('studentHome.retry') }}</button>
      </template>
    </StatePanel>

    <template v-else-if="home">
      <div v-if="!home.email.confirmed" class="home-email" role="status" data-testid="home-email">
        <IconGlyph name="mail" :size="18" />
        <p>{{ t('studentHome.confirmEmail', { email: home.email.email }) }}</p>
        <button
          class="btn btn-ghost btn-sm"
          type="button"
          :disabled="sending"
          :aria-busy="sending"
          data-testid="send-again"
          @click="sendAgain"
        >
          {{ t('studentHome.sendAgain') }}
        </button>
        <p v-if="sendError" class="field-error" role="alert">{{ sendError }}</p>
      </div>

      <header class="home-header">
        <div class="home-greeting">
          <p class="page-eyebrow">{{ weekdayDate(home.today.date) }}</p>
          <h1 class="home-title">{{ greeting }}</h1>
        </div>
        <div class="home-streak" data-testid="home-streak">
          <p class="home-streak-count" :class="{ 'is-zero': home.today.currentStreak === 0 }">
            <strong class="stat-figure">{{ home.today.currentStreak }}</strong>
            <span>{{ home.today.currentStreak > 0 ? t('studentHome.streak') : t('studentHome.streakStart') }}</span>
          </p>
          <StreakWeek :week="home.today.week" :today="home.today.date" />
        </div>
      </header>

      <div class="home-desk">
        <HomeNextTile class="home-desk-next" :next="home.next" :has-plan="plan !== null" :first-time="home.firstTime" />

        <!-- The goal of the learning plan, or the offer to build one. -->
        <section v-if="plan" class="home-goal home-desk-goal" aria-labelledby="home-goal-title" data-testid="home-goal">
          <div class="home-goal-head">
            <h2 id="home-goal-title" class="home-overline">{{ t('studentHome.yourGoal') }}</h2>
            <RouterLink class="btn btn-link" :to="{ name: 'student-plan' }">
              {{ t('studentHome.viewPlan') }}<IconGlyph name="chevronRight" :size="14" />
            </RouterLink>
          </div>
          <p class="home-goal-title">
            <IconGlyph :name="REASONS[plan.reason].icon" :size="18" />
            <span>{{
              plan.deadline ? t('studentHome.goalBy', { goal: planTitle, date: shortDate(plan.deadline) }) : planTitle
            }}</span>
          </p>
          <ProgressBar class="is-sm" :progress="plan.percent" :label="t('studentHome.planProgress')" />
          <p class="type-caption">{{ goalMeta }}</p>
        </section>
        <section v-else class="home-build home-desk-goal" aria-labelledby="home-build-title" data-testid="home-build">
          <h2 id="home-build-title" class="home-build-title">{{ t('studentHome.buildTitle') }}</h2>
          <p>{{ t('studentHome.buildText') }}</p>
          <RouterLink class="btn btn-secondary" :to="{ name: 'student-setup' }">
            <IconGlyph name="magic" :size="16" />{{ t('studentHome.buildPlan') }}
          </RouterLink>
        </section>

        <!-- Any other day: today's plan. -->
        <section
          v-if="!home.firstTime"
          class="home-day home-desk-day"
          aria-labelledby="home-today-title"
          data-testid="home-today"
        >
          <div class="home-day-head">
            <h2 id="home-today-title" class="home-day-title">{{ t('studentHome.todaysPlan') }}</h2>
            <span class="home-day-xp">{{
              t('studentHome.xpOfGoal', { xp: home.today.todayXp, goal: home.today.dailyGoalXp })
            }}</span>
          </div>
          <div class="home-day-goal">
            <ProgressBar
              class="is-sm"
              :progress="Math.round((home.today.todayXp * 100) / Math.max(1, home.today.dailyGoalXp))"
              :label="t('studentHome.todayXp')"
              :tone="home.today.goalReached ? 'success' : 'primary'"
            />
            <p class="type-caption"><IconGlyph v-if="home.today.goalReached" name="check" :size="14" />{{ xpLine }}</p>
          </div>
          <HomeTimeline :items="today" :label="t('studentHome.todaysPlan')" />
        </section>

        <!-- First day with a plan: this week. -->
        <section
          v-else-if="plan"
          class="home-day home-desk-day"
          aria-labelledby="home-week-title"
          data-testid="home-week"
        >
          <div class="home-day-head">
            <h2 id="home-week-title" class="home-day-title">{{ t('studentHome.thisWeek') }}</h2>
            <span class="type-caption">{{ t('studentHome.dailyGoalXp', { xp: home.today.dailyGoalXp }) }}</span>
          </div>
          <HomeTimeline :items="firstWeekWithPlan" :label="t('studentHome.thisWeek')" />
        </section>

        <!-- First day without a plan: the daily goal and a first week. -->
        <section v-else class="home-day home-desk-day" aria-labelledby="home-first-week" data-testid="home-week">
          <div class="home-day-head">
            <h2 id="home-first-week" class="home-day-title">{{ t('studentHome.firstWeek') }}</h2>
            <span class="type-caption">{{ t('studentHome.dailyGoal') }}</span>
          </div>
          <div class="home-day-goal">
            <SegmentedControl
              class="home-goal-picker"
              :model-value="home.today.dailyGoalXp"
              :label="t('studentHome.dailyGoal')"
              :options="DAILY_GOALS.map((xp) => ({ value: xp, label: t('studentHome.dailyGoalOption', { xp }) }))"
              :disabled="savingGoal"
              @update:model-value="setDailyGoal"
            />
            <p class="type-caption">{{ t('studentHome.dailyGoalHint') }}</p>
            <p v-if="goalError" class="field-error" role="alert">{{ goalError }}</p>
          </div>
          <HomeTimeline :items="firstWeekWithoutPlan" :label="t('studentHome.firstWeek')" />
        </section>
      </div>
    </template>

    <AppToast :message="toast" />
  </div>
</template>

<script setup lang="ts">
import '@/styles/plan.css'

import { computed, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { PRACTICE_TYPE_SLUGS, xpApi } from '@/api/learn'
import { emailConfirmationApi, planApi } from '@/api/plan'
import AppToast from '@/components/common/AppToast.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import SegmentedControl from '@/components/common/SegmentedControl.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import HomeNextTile from '@/components/home/HomeNextTile.vue'
import HomeTimeline, { type TimelineItem } from '@/components/home/HomeTimeline.vue'
import StreakWeek from '@/components/learn/StreakWeek.vue'
import { goalTitle, shortDate, weekdayDate } from '@/components/plan/planFormat'
import { LEVEL_KEYS, PRACTICE_TYPE_KEYS, REASONS, STATUS_KEYS } from '@/components/plan/planLabels'
import { notifyProgressChanged } from '@/composables/useProgressRefresh'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { planMessages } from '@/plugins/planMessages'
import { studentHomeMessages } from '@/plugins/studentHomeMessages'
import { resolveApiError } from '@/utils/apiError'
import { formatTime } from '@/utils/format'

/**
 * The student Home, a desk for today: the date and greeting with the week's streak stamped as seals, the next lesson
 * as the one big action, the goal of the learning plan (or the offer to build one) under it, and the day's plan as a
 * timeline beside it (Learn, mistakes to fix, today's live session, Xiaomei). The first day shows the first week
 * instead. A new student who has not seen the setup yet is sent to it.
 */
const DAILY_GOALS = [10, 20, 30] as const

const router = useRouter()
const { t, locale } = useLocalMessages({ studentHome: studentHomeMessages, plan: planMessages })
const resource = useResource(() => planApi.home())
const { toast, showToast } = useToast()

const home = computed(() => resource.data.value ?? null)
const plan = computed(() => home.value?.plan ?? null)
const planTitle = computed(() => (plan.value ? goalTitle(t, plan.value.goalTitle, plan.value.reason) : ''))
const sending = ref(false)
const sendError = ref<string | null>(null)
const savingGoal = ref(false)
const goalError = ref<string | null>(null)

watch(home, (value) => {
  if (value?.setupState === 'NOT_STARTED') void router.replace({ name: 'student-setup' })
})

const greeting = computed(() => {
  const value = home.value
  if (!value) return ''
  if (value.firstTime) {
    return value.firstName
      ? t('studentHome.welcomeFirst', { name: value.firstName })
      : t('studentHome.welcomeFirstNoName')
  }
  return value.firstName ? t('studentHome.welcomeBack', { name: value.firstName }) : t('studentHome.welcomeBackNoName')
})

/** How far the plan is: the week and its status, or on the first day the levels it leads between. */
const goalMeta = computed(() => {
  const summary = plan.value
  if (!summary) return ''
  if (home.value?.firstTime) {
    return t('studentHome.goalFirstMeta', {
      from: t(LEVEL_KEYS[summary.level]),
      to: t(LEVEL_KEYS[summary.targetLevel]),
      week: summary.week,
      total: summary.totalWeeks,
    })
  }
  return t('studentHome.stripMeta', {
    percent: summary.percent,
    week: summary.week,
    total: summary.totalWeeks,
    status: t(STATUS_KEYS[summary.status]).toLowerCase(),
  })
})

const xpLine = computed(() => {
  const today = home.value?.today
  if (!today) return ''
  if (today.goalReached) return t('studentHome.goalReached')
  const remaining = Math.max(0, today.dailyGoalXp - today.todayXp)
  return today.currentStreak > 0
    ? t('studentHome.moreXpStreak', { xp: remaining, streak: today.currentStreak })
    : t('studentHome.moreXp', { xp: remaining })
})

function xiaomeiItem(when: string, first: boolean): TimelineItem {
  return {
    key: 'xiaomei',
    when,
    title: first ? t('studentHome.sayHi') : t('studentHome.talkXiaomei'),
    meta: first ? t('studentHome.sayHiMeta') : t('studentHome.talkMeta'),
    actions: [{ label: t('studentHome.startTalking'), to: { name: 'student-ai', params: { tab: 'talk' } } }],
  }
}

/** Now · next lesson, Next · mistakes to fix, today's live session at its time, Anytime · Xiaomei. */
const today = computed<TimelineItem[]>(() => {
  const value = home.value
  if (!value) return []
  const items: TimelineItem[] = []
  if (value.next) {
    items.push({
      key: 'learn',
      when: t('studentHome.now'),
      title: value.next.title,
      meta:
        value.next.kind === 'CHECKPOINT'
          ? t('studentHome.checkpointMeta', { minutes: value.next.minutes, xp: value.next.xpReward })
          : t('studentHome.learnMeta', {
              position: value.next.position,
              total: value.next.unitLessonCount,
              minutes: value.next.minutes,
            }),
      highlight: true,
    })
  }
  const mistakes = value.today.mistakesWaiting
  if (mistakes > 0) {
    items.push({
      key: 'mistakes',
      when: t('studentHome.next'),
      title: t('studentHome.fixMistakes', { count: mistakes }, mistakes),
      meta: t('studentHome.fixMeta'),
      actions: [{ label: t('studentHome.startReview'), to: { name: 'student-practice-review' } }],
    })
  }
  const live = value.liveToday
  if (live) {
    items.push({
      key: 'live',
      when: formatTime(live.startsAt, locale.value),
      title: t('studentHome.liveTitle', { title: live.title }),
      meta: t('studentHome.liveMeta', { teacher: live.teacherName, classTitle: live.classTitle }),
      actions: [
        { label: t('studentHome.details'), to: { name: 'student-live-sessions' } },
        { label: t('studentHome.openClass'), to: { name: 'student-class', params: { classId: live.classId } } },
      ],
    })
  }
  items.push(xiaomeiItem(t('studentHome.anytime'), false))
  return items
})

/** First · the first unit, Then · a practice set of the focus, Anytime · Xiaomei, Optional · a class. */
const firstWeekWithPlan = computed<TimelineItem[]>(() => {
  const value = home.value
  const summary = plan.value
  if (!value || !summary) return []
  const items: TimelineItem[] = []
  const first = summary.firstStep
  if (first && value.next) {
    items.push({
      key: 'first',
      when: t('studentHome.first'),
      title: first.quickReview
        ? t('studentHome.reviewTitle', { title: first.title })
        : t('studentHome.startTitle', { title: first.title }),
      meta: t('studentHome.learnLessons', { count: first.lessonsLeft, minutes: first.minutesLeft }, first.lessonsLeft),
      highlight: true,
    })
  }
  const practice = summary.practice
  if (practice) {
    items.push({
      key: 'practice',
      when: t('studentHome.then'),
      title: t('plan.practiceType.setTitle', { type: t(PRACTICE_TYPE_KEYS[practice.type]), title: practice.title }),
      meta: t('studentHome.practiceMeta'),
      actions: [
        {
          label: t('studentHome.practise'),
          to: {
            name: 'student-practice',
            params: { type: PRACTICE_TYPE_SLUGS[practice.type] },
            query: { set: String(practice.setId) },
          },
        },
      ],
    })
  }
  items.push(xiaomeiItem(t('studentHome.anytime'), true))
  const optional = summary.optionalClass
  if (optional) {
    items.push({
      key: 'class',
      when: t('studentHome.optional'),
      title: optional.title,
      meta: t('studentHome.classMeta', { week: optional.weekFrom }),
      actions: [
        { label: t('studentHome.seeClass'), to: { name: 'student-class', params: { classId: optional.classId } } },
      ],
    })
  }
  return items
})

/** First · Unit 1, Then · a practice set, Teacher · a class, Anytime · Xiaomei. */
const firstWeekWithoutPlan = computed<TimelineItem[]>(() => {
  const next = home.value?.next
  return [
    {
      key: 'first',
      when: t('studentHome.first'),
      title: next ? t('studentHome.startUnit', { number: next.unitNumber }) : t('studentHome.tryPractice'),
      meta: t('studentHome.oneLesson'),
      highlight: true,
    },
    {
      key: 'practice',
      when: t('studentHome.then'),
      title: t('studentHome.tryPractice'),
      meta: t('studentHome.tryPracticeMeta'),
      actions: [{ label: t('studentHome.openPractice'), to: { name: 'student-practice' } }],
    },
    {
      key: 'class',
      when: t('studentHome.teacher'),
      title: t('studentHome.joinClass'),
      meta: t('studentHome.joinClassMeta'),
      actions: [{ label: t('studentHome.findClass'), to: { name: 'student-classes-find' } }],
    },
    xiaomeiItem(t('studentHome.anytime'), true),
  ]
})

async function sendAgain(): Promise<void> {
  sending.value = true
  sendError.value = null
  try {
    const sent = await emailConfirmationApi.resend()
    showToast(t('studentHome.linkSent', { email: sent.email }))
  } catch (reason) {
    sendError.value = resolveApiError(reason, t('studentHome.sendError'))
  } finally {
    sending.value = false
  }
}

async function setDailyGoal(xp: number): Promise<void> {
  if (savingGoal.value || home.value?.today.dailyGoalXp === xp) return
  savingGoal.value = true
  goalError.value = null
  try {
    await xpApi.setDailyGoal(xp)
    await resource.reload()
    notifyProgressChanged()
    showToast(t('studentHome.dailyGoalSaved', { xp }))
  } catch (reason) {
    goalError.value = resolveApiError(reason, t('studentHome.dailyGoalError'))
  } finally {
    savingGoal.value = false
  }
}
</script>
