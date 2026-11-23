<template>
  <div class="plan-result" data-testid="plan-result">
    <section class="plan-result-card" aria-labelledby="plan-result-level-title">
      <h3 id="plan-result-level-title" class="page-eyebrow">{{ t('plan.result.yourLevel') }}</h3>
      <LevelLadder :placed="plan.level.placed" :target="plan.level.target" />
      <p class="plan-result-level">{{ t(LEVEL_KEYS[plan.level.placed]) }}</p>
      <!-- What the level means for this learner, shown at once: what they can do and what the plan teaches. A learner who
           skipped the level check starts from the first lesson and can take the check later (S1.25). -->
      <div v-if="skippedCheck" class="plan-result-list" data-testid="plan-result-skipped">
        <h4 class="plan-result-sub">{{ t('plan.result.skippedTitle') }}</h4>
        <ul class="plan-icon-list">
          <li><IconGlyph name="learn" :size="16" />{{ t('plan.result.skippedStart') }}</li>
          <li><IconGlyph name="target" :size="16" />{{ t('plan.result.skippedLater') }}</li>
        </ul>
      </div>
      <div v-else class="plan-result-list" data-testid="plan-result-known">
        <h4 class="plan-result-sub">{{ knownHeading }}</h4>
        <ul class="plan-icon-list">
          <li v-for="(item, index) in known" :key="`${index}-${item}`">
            <IconGlyph :name="knownIcon" :size="16" />{{ item }}
          </li>
        </ul>
      </div>
      <div class="plan-result-list" data-testid="plan-result-learn">
        <h4 class="plan-result-sub">{{ learnHeading }}</h4>
        <ul class="plan-icon-list">
          <li v-for="(item, index) in toLearn" :key="`${index}-${item}`">
            <IconGlyph name="progress" :size="16" />{{ item }}
          </li>
        </ul>
      </div>
    </section>

    <section class="plan-result-card" aria-labelledby="plan-result-goal-title">
      <h3 id="plan-result-goal-title" class="page-eyebrow">{{ t('plan.result.yourGoal') }}</h3>
      <div class="plan-result-goal">
        <span class="plan-icon-tile" aria-hidden="true"
          ><IconGlyph :name="REASON_ICONS[plan.goal.reason]" :size="24"
        /></span>
        <div>
          <p class="plan-result-goal-title">{{ title }}</p>
          <p class="type-caption" data-testid="plan-result-when">{{ when }}</p>
        </div>
      </div>
      <p v-if="plan.milestone" class="callout is-compact" role="note" data-testid="plan-result-milestone">
        <IconGlyph name="info" :size="16" /><span>{{ milestoneReason }}</span>
      </p>
      <blockquote v-if="aiQuote" class="plan-result-quote">“{{ aiQuote }}”</blockquote>
      <p v-else-if="plan.milestone" class="plan-result-quote">{{ milestoneRules }}</p>
      <template v-else>
        <blockquote v-if="plan.goal.ownWords" class="plan-result-quote">“{{ plan.goal.ownWords }}”</blockquote>
        <!-- Without SparkBot's summary, the goals they picked say what the plan is for. -->
        <ul class="plan-result-picks">
          <li v-for="goal in plan.goal.goals" :key="goal" class="tag">{{ t(`plan.goal.${goal}`) }}</li>
        </ul>
      </template>
      <p v-if="plan.aiAssisted" class="plan-result-ai">
        <IconGlyph name="magic" :size="14" />{{ aiNote }}
        <PlanReportLink :plan="plan" @sent="emit('reported')" />
      </p>
      <div class="plan-result-week">
        <h4 class="plan-result-sub">{{ t('plan.result.typicalWeek') }}</h4>
        <dl class="plan-result-week-rows">
          <div v-for="row in week" :key="row.key">
            <dt>
              <span class="plan-result-week-icon" aria-hidden="true"><IconGlyph :name="row.icon" :size="16" /></span
              >{{ row.title }}
            </dt>
            <dd v-if="row.key === 'dailyGoal'"><PlanDailyGoal @saved="emit('dailyGoal', $event)" /></dd>
            <dd v-else>{{ row.meta }}</dd>
          </div>
        </dl>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { planPathMessages } from '@/plugins/planPathMessages'
import { planSetupMessages } from '@/plugins/planSetupMessages'
import { planSummaryMessages } from '@/plugins/planSummaryMessages'
import type { LearningPlan } from '@/types/plan'
import { daysBetween, formatWeekdayDate, formatWeekdayLongDate } from '@/utils/format'

import LevelLadder from './LevelLadder.vue'
import PlanDailyGoal from './PlanDailyGoal.vue'
import { goalTitle, paceDaily } from './planFormat'
import { FOCUS_ICONS, LEVEL_KEYS, REASON_ICONS } from './planLabels'
import PlanReportLink from './PlanReportLink.vue'

/**
 * "Your plan is ready!", two equal cards. Left, where the learner stands: the level ladder and the level, then what
 * they can already do (or, when they skipped the level check, that they start from the first lesson) and what they will
 * learn, shown at once. Right, where they are going: the goal and its date, SparkBot's summary to confirm (a date too
 * close for the plan gets a first milestone instead), and their typical week: the daily time, the daily goal (S1.09)
 * and the focus.
 */
const props = withDefaults(defineProps<{ plan: LearningPlan; skippedCheck?: boolean }>(), { skippedCheck: false })
const emit = defineEmits<{ reported: []; dailyGoal: [message: string] }>()

const { t, locale } = useLocalMessages({ plan: [planSummaryMessages, planSetupMessages, planPathMessages] })

/** The lists stay short: the road and the rail on the next screen carry the rest. */
const LIST_LENGTH = 3

const title = computed(() => goalTitle(t, props.plan.goal.title, props.plan.goal.reason))

const when = computed(() =>
  props.plan.goal.deadline
    ? t('plan.result.byDate', { date: formatWeekdayLongDate(props.plan.goal.deadline, locale.value) })
    : t('plan.result.atYourPace'),
)

/** "14 days is short, so we set a first milestone." for a date too close, else why SparkBot set one. */
const milestoneReason = computed(() => {
  const { deadline } = props.plan.goal
  if (!deadline || props.plan.weeks.content <= props.plan.weeks.total) return t('plan.result.ambitious')
  const days = daysBetween(props.plan.today, deadline)
  return t('plan.result.shortDate', { days: t('plan.common.days', { count: days }, days) })
})

/** A first milestone built from rules, named by the plan's own steps: "First milestone by Mon 1 Jun: Say hello." */
const milestoneRules = computed(() => {
  const { milestone, steps } = props.plan
  if (!milestone) return ''
  const date = formatWeekdayDate(milestone.date, locale.value)
  const titles = steps.filter((step) => milestone.stepPositions.includes(step.position)).map((step) => step.title)
  if (!titles.length) return t('plan.result.milestoneRulesDate', { date })
  return t('plan.result.milestoneRules', { date, steps: titles.join(', ') })
})

/** SparkBot's text to confirm: the first milestone it suggests, or its summary of the goal. */
const aiQuote = computed(() => {
  const { milestone, goal } = props.plan
  if (milestone) return milestone.text || null
  return goal.summary || null
})
const aiNote = computed(() => {
  if (!aiQuote.value) return t('plan.result.planNote')
  return props.plan.milestone?.text ? t('plan.result.milestoneNote') : t('plan.result.summaryNote')
})

const canAlready = computed(() => props.plan.level.knownSkills.length > 0)
const knownHeading = computed(() => (canAlready.value ? t('plan.result.canAlready') : t('plan.result.startWith')))
const knownIcon = computed(() => (canAlready.value ? 'check' : 'learn'))

/** What the check found known, else the first Learn units of the plan. */
const known = computed(() => {
  if (canAlready.value) {
    return props.plan.level.knownSkills.slice(0, LIST_LENGTH).map((skill) => t(`plan.skillCanDo.${skill}`))
  }
  return props.plan.steps
    .filter((step) => step.kind === 'LEARN_UNIT')
    .slice(0, LIST_LENGTH)
    .map((step) => step.title)
})

const learnHeading = computed(() =>
  props.plan.milestone
    ? t('plan.result.learnMilestone')
    : t('plan.result.learnFor', { for: t(`plan.reason.${props.plan.goal.reason}_for`) }),
)

/** What the check found new, else SparkBot's list, else the goals themselves. */
const toLearn = computed(() => {
  const { newSkills } = props.plan.level
  if (newSkills.length) return newSkills.slice(0, LIST_LENGTH).map((skill) => t(`plan.skillLearn.${skill}`))
  if (props.plan.goal.workOn.length) return props.plan.goal.workOn.slice(0, LIST_LENGTH)
  return props.plan.goal.goals.slice(0, LIST_LENGTH).map((goal) => t(`plan.outcome.${goal}`))
})

/** What the daily time is for: Learn lessons, and the first class from its week, or after the first milestone. */
const dailyTimeFor = computed(() => {
  const classWeek = props.plan.steps.find((step) => step.kind === 'CLASS')?.weekFrom
  if (classWeek === undefined) return t('plan.result.weekLearn')
  return props.plan.milestone
    ? t('plan.result.weekAfterMilestone')
    : t('plan.result.weekLearnClass', { week: classWeek })
})

const week = computed(() => {
  const rows = [
    { key: 'pace', icon: 'clock', title: paceDaily(t, props.plan.pace), meta: dailyTimeFor.value },
    { key: 'dailyGoal', icon: 'target', title: t('common.dailyGoal'), meta: '' },
  ]
  for (const focus of props.plan.goal.focus) {
    rows.push({
      key: focus,
      icon: FOCUS_ICONS[focus],
      title: t(`plan.focus.${focus}_week`),
      meta: t(`plan.focus.${focus}_where`),
    })
  }
  return rows
})
</script>
