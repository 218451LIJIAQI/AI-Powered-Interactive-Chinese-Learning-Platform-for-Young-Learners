<template>
  <div class="home-page">
    <PageHeaderBlock :title="greeting" hide-title />

    <!-- No connection: say so once, above everything, with a way to try again. -->
    <div v-if="!online" class="home-notice is-warning" role="status" data-testid="home-offline">
      <IconGlyph name="alert" :size="18" />
      <p class="home-notice-copy">{{ t('studentHome.offline') }}</p>
      <button
        class="btn btn-secondary btn-sm"
        type="button"
        :disabled="retrying"
        :aria-busy="retrying"
        data-testid="home-retry"
        @click="retryLoad"
      >
        {{ t('common.retry') }}
      </button>
    </div>

    <StatePanel
      v-if="(resource.loading.value && !home) || redirectingToSetup"
      heading-tag="h2"
      tone="loading"
      :title="t('studentHome.loading')"
      :message="t('studentHome.loadingHint')"
      spacious
    />
    <StatePanel
      v-else-if="resource.error.value && !home"
      heading-tag="h2"
      tone="error"
      icon="alert"
      :title="t('studentHome.loadError')"
      :message="resource.error.value"
      spacious
      @retry="resource.reload"
    />

    <template v-else-if="home && !redirectingToSetup">
      <div v-if="!home.email.confirmed" class="home-notice is-info" role="status" data-testid="home-email">
        <IconGlyph name="mail" :size="18" />
        <p class="home-notice-copy">{{ t('studentHome.confirmEmail', { email: home.email.email }) }}</p>
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

      <!-- The goal is a strip across the top, the first day included (S3.01, S3.03, S3.15, S3.16). -->
      <section v-if="plan" class="panel home-strip" aria-labelledby="home-goal-title" data-testid="home-goal">
        <span class="plan-icon-tile" aria-hidden="true"
          ><IconGlyph :name="REASON_ICONS[plan.reason]" :size="20"
        /></span>
        <div class="home-strip-goal">
          <h2 id="home-goal-title" class="page-eyebrow">{{ t('plan.result.yourGoal') }}</h2>
          <p class="home-strip-title">{{ goalLine }}</p>
        </div>
        <div class="home-strip-progress">
          <ProgressBar class="is-sm" :progress="plan.percent" :label="t('plan.page.planProgress')" />
          <p class="type-caption" data-testid="home-goal-status">{{ goalStatus }}</p>
        </div>
        <RouterLink class="btn btn-secondary btn-sm" :to="goalAction.to" data-testid="home-goal-action">
          {{ goalAction.label }}
        </RouterLink>
      </section>

      <!-- A plan that is behind, or whose date has passed: what happened and what to do, in one line. -->
      <div v-if="planNotice" class="home-notice is-warning" role="status" data-testid="home-plan-notice">
        <IconGlyph :name="ended ? 'calendar' : 'alert'" :size="18" />
        <p class="home-notice-copy">{{ planNotice }}</p>
      </div>

      <div class="home-desk">
        <HomeNextTile :next="home.next" :has-plan="planRunning" :first-time="home.firstTime" />

        <div class="home-rail">
          <!-- No plan yet, on any day: the offer to build one. -->
          <section v-if="!plan" class="home-build" aria-labelledby="home-build-title" data-testid="home-build">
            <h2 id="home-build-title" class="home-build-title">
              <span class="home-build-icon" aria-hidden="true"><IconGlyph name="magic" :size="20" /></span>
              {{ t('plan.cards.buildTitle') }}
            </h2>
            <p>{{ t('plan.cards.buildText') }}</p>
            <RouterLink class="btn btn-secondary btn-sm" :to="{ name: 'student-setup' }">
              {{ t('plan.page.buildPlan') }}
            </RouterLink>
          </section>

          <!-- The first day without a plan: the daily goal and a first week. -->
          <section
            v-if="!plan && home.firstTime"
            class="home-rail-section"
            aria-labelledby="home-first-week"
            data-testid="home-week"
          >
            <div class="home-rail-head">
              <h2 id="home-first-week" class="card-title">{{ t('studentHome.firstWeek') }}</h2>
              <span class="type-caption">{{ t('common.dailyGoal') }}</span>
            </div>
            <FilterChips
              :model-value="home.today.dailyGoalXp"
              :label="t('common.dailyGoal')"
              :options="dailyGoalOptions"
              :disabled="savingGoal"
              @update:model-value="setDailyGoal"
            />
            <p v-if="goalError" class="field-error" role="alert">{{ goalError }}</p>
            <HomeTimeline :items="firstWeekWithoutPlan" :label="t('studentHome.firstWeek')" />
          </section>

          <!-- Every other day, and the first day with a plan: today, against the daily goal. -->
          <section v-else class="home-rail-section" aria-labelledby="home-today-title" data-testid="home-today">
            <div class="home-rail-head">
              <h2 id="home-today-title" class="card-title">{{ todayTitle }}</h2>
              <span class="home-rail-figure">{{
                t('studentHome.xpOfGoal', { xp: home.today.todayXp, goal: home.today.dailyGoalXp })
              }}</span>
            </div>
            <ProgressBar
              v-if="!home.firstTime"
              class="is-sm"
              :progress="ratioPercent(home.today.todayXp, home.today.dailyGoalXp)"
              :label="t('studentHome.todayXp')"
              :tone="home.today.goalReached ? 'success' : 'primary'"
            />
            <HomeTimeline :items="todayItems" :label="todayTitle" />
          </section>
        </div>
      </div>
    </template>

    <AppToast :message="toast" />
  </div>
</template>

<script setup lang="ts">
import '@/styles/home.css'

import { computed, ref } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

import { getAuthSessionVersion } from '@/api/http'
import { xpApi } from '@/api/learn'
import { emailConfirmationApi, planApi } from '@/api/plan'
import AppToast from '@/components/common/AppToast.vue'
import FilterChips from '@/components/common/FilterChips.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import HomeNextTile from '@/components/home/HomeNextTile.vue'
import HomeTimeline, { type TimelineItem } from '@/components/home/HomeTimeline.vue'
import { mistakesReview, practiceSetRoute } from '@/components/learn/practice/practiceTypes'
import { goalTitle, planExpired, practiceSetTitle } from '@/components/plan/planFormat'
import { REASON_ICONS } from '@/components/plan/planLabels'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { usePageRedirect } from '@/composables/usePageNavigation'
import { notifyProgressChanged } from '@/composables/useProgressRefresh'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useOnlineStatus } from '@/composables/useOnlineStatus'
import { useResource } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { planSummaryMessages } from '@/plugins/planSummaryMessages'
import { studentHomeMessages } from '@/plugins/studentHomeMessages'
import { hasServerMessage, resolveApiError } from '@/utils/apiError'
import { formatWeekdayDate, ratioPercent, shiftDay } from '@/utils/format'
import { SERVER_MESSAGE } from '@/utils/serverMessages'
import { DAILY_GOALS } from '@/utils/xpLevel'

/**
 * The learner Home: the goal of the learning plan as a strip across the top (its week and whether it is on track), the
 * next lesson as the one big action, and today's plan in a rail beside it (mistakes to fix, Xiaomei). A plan that is
 * behind, or whose date has passed, says so in one line with the way to adjust it. The first day shows the goal and
 * today's first steps in the rail, or the offer to build a plan. A new learner who has not seen the setup yet is sent to
 * it. Every state comes from the Home request: the plan's status, dates and weeks, and today's XP.
 */
/** How much later "move your goal date" suggests: one calendar week of the plan. */
const MOVE_DATE_DAYS = 7

const { t, locale } = useLocalMessages({ studentHome: studentHomeMessages, plan: planSummaryMessages })
const resource = useResource(planApi.home)
const isUnmounted = useIsUnmounted()
const { toast, showToast } = useToast()
// Offline is said on the page; when the connection returns, Home reads today again.
const { online } = useOnlineStatus(() => {
  showToast(t('studentHome.backOnline'))
  void resource.reload()
})

const home = resource.data
const plan = computed(() => home.value?.plan ?? null)
const redirectingToSetup = computed(() => home.value?.setupState === 'NOT_STARTED')
const retrying = ref(false)
const sending = ref(false)
const sendError = ref<string | null>(null)
const savingGoal = ref(false)
const goalError = ref<string | null>(null)

async function retryLoad(): Promise<void> {
  if (retrying.value) return
  retrying.value = true
  try {
    await resource.reload()
  } finally {
    retrying.value = false
  }
}

usePageRedirect(() => (redirectingToSetup.value ? { name: 'student-setup' } : null))

/** The goal date has passed on an unfinished plan: the plan has ended until the learner picks a new date or goal. */
const ended = computed(() => {
  const summary = plan.value
  return summary !== null && planExpired(summary.status, summary.deadline, summary.today)
})

/** A plan that is still running guides the next step ("Next in your plan"). */
const planRunning = computed(() => plan.value !== null && plan.value.status !== 'COMPLETE' && !ended.value)

/** The page's h1 (for screen readers: the header bar names the page). */
const greeting = computed(() => {
  const value = home.value
  if (!value) return t('nav.home')
  return t(value.firstTime ? 'studentHome.welcomeFirst' : 'studentHome.welcomeBack', { name: value.firstName })
})

const goalLine = computed(() => {
  const summary = plan.value
  if (!summary) return ''
  const goal = goalTitle(t, summary.goalTitle, summary.reason)
  if (!summary.deadline) return t('studentHome.goalNoDate', { goal })
  if (ended.value) return t('studentHome.goalWasDue', { goal, date: formatWeekdayDate(summary.deadline, locale.value) })
  return summary.milestoneDate
    ? t('studentHome.goalMilestone', { goal, date: formatWeekdayDate(summary.milestoneDate, locale.value) })
    : t('studentHome.goalBy', { goal, date: formatWeekdayDate(summary.deadline, locale.value) })
})

/** "Week 2 of 10 · on track": each number once, the plan's own words for where it stands. */
const goalStatus = computed(() => {
  const summary = plan.value
  if (!summary) return ''
  if (ended.value) return t('plan.cards.planEnded')
  return t(`studentHome.weekStatus.${summary.status}`, { week: summary.week, total: summary.totalWeeks })
})

/** Home's way into the plan: to look at it, or to change it when it is behind or has ended. */
const goalAction = computed<{ label: string; to: RouteLocationRaw }>(() => {
  const editGoal = { name: 'student-plan', query: { edit: 'goal' } }
  if (ended.value) return { label: t('plan.retake.setNewGoal'), to: editGoal }
  if (plan.value?.status === 'BEHIND') return { label: t('studentHome.adjustPlan'), to: editGoal }
  return { label: t('plan.cards.viewPlan'), to: { name: 'student-plan' } }
})

const dailyGoalOptions = computed(() =>
  DAILY_GOALS.map((xp) => ({ value: xp, label: t('plan.result.dailyGoalOption', { xp }) })),
)

/** Behind, or past the goal date: what happened and what to do, once (the strip keeps the week). */
const planNotice = computed(() => {
  const summary = plan.value
  if (!summary) return null
  if (ended.value) return t('studentHome.ended')
  if (summary.status !== 'BEHIND') return null
  // No missed day yet, but progress well under the time that has passed: behind without a count.
  const days = summary.daysBehind
  const moved = summary.deadline ? formatWeekdayDate(shiftDay(summary.deadline, MOVE_DATE_DAYS), locale.value) : null
  if (days > 0) {
    return moved
      ? t('studentHome.behindDate', { count: days, date: moved }, days)
      : t('studentHome.behindNoDate', { count: days }, days)
  }
  return moved ? t('studentHome.behindPlanDate', { date: moved }) : t('studentHome.behindPlanNoDate')
})

/** "Today's plan" while a plan runs; plain "Today" without one or after its date. */
const todayTitle = computed(() => (planRunning.value ? t('studentHome.todaysPlan') : t('studentHome.today')))

function xiaomeiItem(first: boolean): TimelineItem {
  return {
    key: 'xiaomei',
    when: t('studentHome.anytime'),
    title: first ? t('studentHome.sayHi') : t('plan.page.xiaomei'),
    meta: t('studentHome.talkMeta'),
    action: { label: t('studentHome.startTalking'), to: { name: 'student-ai', params: { tab: 'talk' } } },
  }
}

/**
 * The first day with a plan: Then · a practice set of the focus, Anytime · Xiaomei, Optional · the plan's class.
 * Any other day: Then · mistakes to fix, Anytime · Xiaomei. The next lesson is the big card.
 */
const todayItems = computed<TimelineItem[]>(() => {
  const value = home.value
  if (!value) return []
  const items: TimelineItem[] = []
  const summary = plan.value
  if (value.firstTime && planRunning.value && summary) {
    const practice = summary.practice
    if (practice) {
      items.push({
        key: 'practice',
        when: t('studentHome.then'),
        title: practiceSetTitle(t, practice.type, practice.title),
        meta: t('plan.page.practice'),
        action: { label: t('studentHome.practise'), to: practiceSetRoute(practice.type, practice.setId) },
      })
    }
    items.push(xiaomeiItem(true))
    const optional = summary.optionalClass
    if (optional) {
      items.push({
        key: 'class',
        when: t('studentHome.optional'),
        title: optional.title,
        meta: optional.bestAfterUnit
          ? t('studentHome.classBestAfter', { unit: optional.bestAfterUnit })
          : t('studentHome.classFromWeek', { week: optional.weekFrom }),
        action: { label: t('studentHome.findClasses'), to: { name: 'student-classes-find' } },
      })
    }
    return items
  }
  const mistakes = value.today.mistakesWaiting
  if (mistakes > 0) {
    items.push({
      key: 'mistakes',
      when: t('studentHome.then'),
      title: t('studentHome.fixMistakes', { count: mistakes }, mistakes),
      // S3.01: where the review is, what it takes and pays, as the Practice tab's Mistakes footer says it.
      meta: t('studentHome.fixMeta', mistakesReview(mistakes)),
      action: { label: t('studentHome.startReview'), to: { name: 'student-practice-review' } },
    })
  }
  items.push(xiaomeiItem(false))
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
  if (sending.value) return
  const session = getAuthSessionVersion()
  const stillActive = () => !isUnmounted() && session === getAuthSessionVersion()
  sending.value = true
  sendError.value = null
  try {
    const sent = await emailConfirmationApi.resend()
    if (stillActive()) showToast(t('studentHome.linkSent', { email: sent.email }))
  } catch (reason) {
    if (!stillActive()) return
    if (home.value && hasServerMessage(reason, SERVER_MESSAGE.EMAIL_ALREADY_CONFIRMED)) {
      resource.replace({ ...home.value, email: { ...home.value.email, confirmed: true } })
    } else {
      sendError.value = resolveApiError(reason, t('studentHome.sendError'))
    }
  } finally {
    if (!isUnmounted()) sending.value = false
  }
}

async function setDailyGoal(xp: number): Promise<void> {
  if (savingGoal.value || home.value?.today.dailyGoalXp === xp) return
  const session = getAuthSessionVersion()
  const stillActive = () => !isUnmounted() && session === getAuthSessionVersion()
  savingGoal.value = true
  goalError.value = null
  try {
    const { dailyGoalXp } = await xpApi.setDailyGoal(xp)
    if (session === getAuthSessionVersion()) notifyProgressChanged()
    if (!stillActive()) return
    if (home.value) {
      const today = home.value.today
      resource.replace({ ...home.value, today: { ...today, dailyGoalXp, goalReached: today.todayXp >= dailyGoalXp } })
    }
    await resource.reload()
    if (stillActive()) showToast(t('plan.result.dailyGoalSaved', { xp: dailyGoalXp }))
  } catch (reason) {
    if (stillActive()) goalError.value = resolveApiError(reason, t('plan.result.dailyGoalError'))
  } finally {
    if (!isUnmounted()) savingGoal.value = false
  }
}
</script>
