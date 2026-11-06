<template>
  <div class="home-page">
    <PageHeaderBlock :title="greeting" hide-title />

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
        <p class="home-email-copy">{{ t('studentHome.confirmEmail', { email: home.email.email }) }}</p>
        <p v-if="sendError" class="field-error" role="alert">{{ sendError }}</p>
        <button
          class="btn btn-secondary btn-sm"
          type="button"
          :disabled="sending"
          :aria-busy="sending"
          data-testid="send-again"
          @click="sendAgain"
        >
          {{ t('studentHome.sendAgain') }}
        </button>
      </div>

      <!-- Any other day: the goal is a strip across the top. On the first day it is in the rail. -->
      <section
        v-if="plan && !home.firstTime"
        class="panel home-strip"
        aria-labelledby="home-goal-title"
        data-testid="home-goal"
      >
        <span class="plan-icon-tile" aria-hidden="true"
          ><IconGlyph :name="REASONS[plan.reason].icon" :size="20"
        /></span>
        <div class="home-strip-goal">
          <h2 id="home-goal-title" class="page-eyebrow">{{ t('studentHome.yourGoal') }}</h2>
          <p class="home-strip-title">{{ goalLine }}</p>
        </div>
        <div class="home-strip-progress">
          <ProgressBar class="is-sm" :progress="plan.percent" :label="t('studentHome.planProgress')" />
          <p class="type-caption">{{ goalMeta }}</p>
        </div>
        <RouterLink class="btn btn-secondary btn-sm" :to="{ name: 'student-plan' }">
          {{ t('studentHome.viewPlan') }}
        </RouterLink>
      </section>

      <div class="home-desk">
        <HomeNextTile :next="home.next" :has-plan="plan !== null" :first-time="home.firstTime" />

        <div class="home-rail">
          <!-- No plan yet, on any day: the offer to build one. -->
          <section v-if="!plan" class="home-build" aria-labelledby="home-build-title" data-testid="home-build">
            <h2 id="home-build-title" class="home-build-title">
              <span class="home-build-icon" aria-hidden="true"><IconGlyph name="magic" :size="20" /></span>
              {{ t('studentHome.buildTitle') }}
            </h2>
            <p>{{ t('studentHome.buildText') }}</p>
            <RouterLink class="btn btn-secondary btn-sm" :to="{ name: 'student-setup' }">
              {{ t('studentHome.buildPlan') }}
            </RouterLink>
          </section>

          <!-- Any other day: today's plan. -->
          <section
            v-if="!home.firstTime"
            class="home-rail-section"
            aria-labelledby="home-today-title"
            data-testid="home-today"
          >
            <div class="home-rail-head">
              <h2 id="home-today-title" class="card-title">{{ t('studentHome.todaysPlan') }}</h2>
              <span class="home-rail-figure">{{
                t('studentHome.xpOfGoal', { xp: home.today.todayXp, goal: home.today.dailyGoalXp })
              }}</span>
            </div>
            <ProgressBar
              class="is-sm"
              :progress="Math.round((home.today.todayXp * 100) / Math.max(1, home.today.dailyGoalXp))"
              :label="t('studentHome.todayXp')"
              :tone="home.today.goalReached ? 'success' : 'primary'"
            />
            <p class="type-caption">{{ xpLine }}</p>
            <HomeTimeline :items="today" :label="t('studentHome.todaysPlan')" />
          </section>

          <!-- First day with a plan: the goal and this week. -->
          <template v-else-if="plan">
            <section class="home-rail-goal" aria-labelledby="home-goal-title" data-testid="home-goal">
              <div class="home-rail-head">
                <h2 id="home-goal-title" class="card-title">{{ t('studentHome.yourGoal') }}</h2>
                <RouterLink class="btn btn-secondary btn-sm" :to="{ name: 'student-plan' }">
                  {{ t('studentHome.viewPlan') }}
                </RouterLink>
              </div>
              <div class="home-rail-card">
                <div class="home-rail-card-head">
                  <span class="plan-icon-tile" aria-hidden="true">
                    <IconGlyph :name="REASONS[plan.reason].icon" :size="20" />
                  </span>
                  <div>
                    <p class="home-strip-title">{{ planTitle }}</p>
                    <p class="type-caption">{{ firstWeekMeta }}</p>
                  </div>
                </div>
                <ProgressBar class="is-sm" :progress="plan.percent" :label="t('studentHome.planProgress')" />
                <p class="type-caption">{{ t('studentHome.percentOfPlan', { percent: plan.percent }) }}</p>
              </div>
            </section>
            <section class="home-rail-section" aria-labelledby="home-week-title" data-testid="home-week">
              <div class="home-rail-head">
                <h2 id="home-week-title" class="card-title">{{ t('studentHome.thisWeek') }}</h2>
                <span class="type-caption">{{ t('studentHome.dailyGoalXp', { xp: home.today.dailyGoalXp }) }}</span>
              </div>
              <HomeTimeline :items="firstWeekWithPlan" :label="t('studentHome.thisWeek')" />
            </section>
          </template>

          <!-- First day without a plan: the daily goal and a first week. -->
          <template v-else>
            <section class="home-rail-section" aria-labelledby="home-first-week" data-testid="home-week">
              <div class="home-rail-head">
                <h2 id="home-first-week" class="card-title">{{ t('studentHome.firstWeek') }}</h2>
                <span class="home-rail-figure">{{ t('studentHome.dailyGoal') }}</span>
              </div>
              <FilterChips
                :model-value="home.today.dailyGoalXp"
                :label="t('studentHome.dailyGoal')"
                :options="DAILY_GOALS.map((xp) => ({ value: xp, label: t('studentHome.dailyGoalOption', { xp }) }))"
                :disabled="savingGoal"
                @update:model-value="setDailyGoal"
              />
              <p class="type-caption">{{ t('studentHome.dailyGoalHint') }}</p>
              <p v-if="goalError" class="field-error" role="alert">{{ goalError }}</p>
              <HomeTimeline :items="firstWeekWithoutPlan" :label="t('studentHome.firstWeek')" />
            </section>
          </template>
        </div>
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
import FilterChips from '@/components/common/FilterChips.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import HomeNextTile from '@/components/home/HomeNextTile.vue'
import HomeTimeline, { type TimelineItem } from '@/components/home/HomeTimeline.vue'
import { goalTitle, shortDayDate } from '@/components/plan/planFormat'
import { PRACTICE_TYPE_KEYS, REASONS, STATUS_KEYS } from '@/components/plan/planLabels'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { notifyProgressChanged } from '@/composables/useProgressRefresh'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { planMessages } from '@/plugins/planMessages'
import { studentHomeMessages } from '@/plugins/studentHomeMessages'
import { resolveApiError } from '@/utils/apiError'
import { formatTime } from '@/utils/format'

/**
 * The student Home: the goal of the learning plan as a strip across the top, the next lesson as the one big action, and
 * the rest of the day in a rail beside it (mistakes to fix, today's live session, Xiaomei). The first day shows the goal
 * and the first week in the rail, or the offer to build a plan. A new student who has not seen the setup yet is sent to it.
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

/** The page's h1 (for screen readers: the header bar names the page). */
const greeting = computed(() => {
  const value = home.value
  if (!value) return t('studentHome.pageTitle')
  if (value.firstTime) {
    return value.firstName
      ? t('studentHome.welcomeFirst', { name: value.firstName })
      : t('studentHome.welcomeFirstNoName')
  }
  return value.firstName ? t('studentHome.welcomeBack', { name: value.firstName }) : t('studentHome.welcomeBackNoName')
})

const goalLine = computed(() => {
  const deadline = plan.value?.deadline
  return deadline ? t('studentHome.goalBy', { goal: planTitle.value, date: shortDayDate(deadline) }) : planTitle.value
})

const goalMeta = computed(() => {
  const summary = plan.value
  if (!summary) return ''
  return t('studentHome.stripMeta', {
    percent: summary.percent,
    week: summary.week,
    total: summary.totalWeeks,
    status: t(STATUS_KEYS[summary.status]).toLowerCase(),
  })
})

const firstWeekMeta = computed(() => {
  const summary = plan.value
  if (!summary) return ''
  const week = t('studentHome.weekOf', { week: summary.week, total: summary.totalWeeks })
  return summary.deadline ? t('studentHome.byDate', { date: shortDayDate(summary.deadline), week }) : week
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

function xiaomeiItem(first: boolean): TimelineItem {
  return {
    key: 'xiaomei',
    when: t('studentHome.anytime'),
    title: first ? t('studentHome.sayHi') : t('studentHome.talkXiaomei'),
    meta: t('studentHome.talkMeta'),
    action: { label: t('studentHome.startTalking'), to: { name: 'student-ai', params: { tab: 'talk' } } },
  }
}

/** Then · mistakes to fix, today's live session at its time, Anytime · Xiaomei. The next lesson is the big card. */
const today = computed<TimelineItem[]>(() => {
  const value = home.value
  if (!value) return []
  const items: TimelineItem[] = []
  const mistakes = value.today.mistakesWaiting
  if (mistakes > 0) {
    items.push({
      key: 'mistakes',
      when: t('studentHome.then'),
      title: t('studentHome.fixMistakes', { count: mistakes }, mistakes),
      meta: t('studentHome.fixMeta'),
      action: { label: t('studentHome.startReview'), to: { name: 'student-practice-review' } },
    })
  }
  const live = value.liveToday
  if (live) {
    items.push({
      key: 'live',
      when: formatTime(live.startsAt, locale.value),
      title: live.title,
      meta: t('studentHome.liveMeta', { teacher: live.teacherName }),
      action: { label: t('studentHome.view'), to: { name: 'student-live-sessions' } },
    })
  }
  items.push(xiaomeiItem(false))
  return items
})

/** Then · a practice set of the focus, Anytime · Xiaomei, Optional · a class. */
const firstWeekWithPlan = computed<TimelineItem[]>(() => {
  const summary = plan.value
  if (!summary) return []
  const items: TimelineItem[] = []
  const practice = summary.practice
  if (practice) {
    items.push({
      key: 'practice',
      when: t('studentHome.then'),
      title: t('plan.practiceType.setTitle', { type: t(PRACTICE_TYPE_KEYS[practice.type]), title: practice.title }),
      meta: t('studentHome.practiceMeta'),
      action: {
        label: t('studentHome.practise'),
        to: {
          name: 'student-practice',
          params: { type: PRACTICE_TYPE_SLUGS[practice.type] },
          query: { set: String(practice.setId) },
        },
      },
    })
  }
  items.push(xiaomeiItem(true))
  const optional = summary.optionalClass
  if (optional) {
    items.push({
      key: 'class',
      when: t('studentHome.optional'),
      title: optional.title,
      meta: t('studentHome.classMeta', { week: optional.weekFrom }),
      action: {
        label: t('studentHome.seeClass'),
        to: { name: 'student-class', params: { classId: optional.classId } },
      },
    })
  }
  return items
})

/** Then · a practice set, Later · a class, Anytime · Xiaomei. */
const firstWeekWithoutPlan = computed<TimelineItem[]>(() => [
  {
    key: 'practice',
    when: t('studentHome.then'),
    title: t('studentHome.tryPractice'),
    meta: t('studentHome.tryPracticeMeta'),
    action: { label: t('studentHome.openPractice'), to: { name: 'student-practice' } },
  },
  {
    key: 'class',
    when: t('studentHome.later'),
    title: t('studentHome.joinClass'),
    meta: t('studentHome.joinClassMeta'),
    action: { label: t('studentHome.findClass'), to: { name: 'student-classes-find' } },
  },
  xiaomeiItem(true),
])

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
