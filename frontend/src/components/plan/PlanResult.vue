<template>
  <div class="plan-result" data-testid="plan-result">
    <section class="plan-result-card" aria-labelledby="plan-result-level-title">
      <h3 id="plan-result-level-title" class="page-eyebrow">{{ t('plan.result.yourLevel') }}</h3>
      <LevelLadder :placed="plan.level.placed" :target="plan.level.target" />
      <p class="plan-result-level">
        <span>{{ t(LEVEL_KEYS[plan.level.placed]) }}</span>
        <span class="tag">{{
          plan.level.placed === 'NEW_TO_MANDARIN' ? t('plan.result.startingFresh') : t('plan.result.headStart')
        }}</span>
      </p>
      <div class="plan-result-list">
        <h4 class="plan-result-sub">{{ knownHeading }}</h4>
        <ul class="plan-icon-list">
          <li v-for="item in known" :key="item"><IconGlyph :name="knownIcon" :size="16" />{{ item }}</li>
        </ul>
      </div>
      <div class="plan-result-list">
        <h4 class="plan-result-sub">{{ learnHeading }}</h4>
        <ul class="plan-icon-list">
          <li v-for="item in toLearn" :key="item"><IconGlyph name="progress" :size="16" />{{ item }}</li>
        </ul>
      </div>
    </section>

    <section class="plan-result-card" aria-labelledby="plan-result-goal-title">
      <h3 id="plan-result-goal-title" class="page-eyebrow">{{ t('plan.result.yourGoal') }}</h3>
      <div class="plan-result-goal">
        <span class="plan-icon-tile" aria-hidden="true"
          ><IconGlyph :name="REASONS[plan.goal.reason].icon" :size="24"
        /></span>
        <div>
          <p class="plan-result-goal-title">{{ title }}</p>
          <p class="type-caption">{{ when }}</p>
        </div>
      </div>
      <div v-if="plan.milestone" class="callout" role="note">
        <IconGlyph name="info" :size="16" />
        <span>
          <p>{{ t('plan.result.ambitious') }}</p>
          <p>
            {{
              plan.milestone.text ??
              t('plan.result.milestoneRules', { date: longDate(plan.milestone.date), steps: milestoneSteps })
            }}
          </p>
        </span>
      </div>
      <template v-if="plan.goal.summary">
        <blockquote class="plan-result-quote">“{{ plan.goal.summary }}”</blockquote>
        <p class="plan-result-ai"><IconGlyph name="magic" :size="14" />{{ t('plan.result.summaryNote') }}</p>
      </template>
      <blockquote v-else-if="plan.goal.ownWords" class="plan-result-quote">“{{ plan.goal.ownWords }}”</blockquote>
      <!-- Without SparkBot's summary or the student's words, the goals they picked say what the plan is for. -->
      <ul v-if="!plan.goal.summary" class="plan-result-picks">
        <li v-for="goal in plan.goal.goals" :key="goal" class="tag">{{ t(GOAL_KEYS[goal]) }}</li>
      </ul>
      <div class="plan-result-week">
        <h4 class="plan-result-sub">{{ t('plan.result.typicalWeek') }}</h4>
        <dl class="plan-result-week-rows">
          <div v-for="row in week" :key="row.key">
            <dt>
              <span class="plan-result-week-icon" aria-hidden="true"><IconGlyph :name="row.icon" :size="16" /></span
              >{{ row.title }}
            </dt>
            <dd>{{ row.meta }}</dd>
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
import { planMessages } from '@/plugins/planMessages'
import type { LearningPlan } from '@/types/plan'

import LevelLadder from './LevelLadder.vue'
import { goalTitle, longDate, spanUntil } from './planFormat'
import {
  FOCUSES,
  GOAL_KEYS,
  LEVEL_KEYS,
  OUTCOME_KEYS,
  REASONS,
  SKILL_CAN_DO_KEYS,
  SKILL_LEARN_KEYS,
} from './planLabels'

/**
 * "Your plan is ready!", two equal cards. Left, where the student stands: the level ladder, what they can already do
 * and what they will learn (from the skills of the level check). Right, where they are going: the goal, SparkBot's
 * summary to confirm (an ambitious timeline gets a first milestone) and their typical week from the pace and focus.
 */
const props = defineProps<{ plan: LearningPlan }>()

const { t } = useLocalMessages({ plan: planMessages })

/** The lists stay short: the road and the rail on the next screen carry the rest. */
const LIST_LENGTH = 3

const title = computed(() => goalTitle(t, props.plan.goal.title, props.plan.goal.reason))

const when = computed(() =>
  props.plan.goal.deadline
    ? t('plan.result.byDate', {
        date: longDate(props.plan.goal.deadline),
        span: spanUntil(t, props.plan.goal.deadline),
      })
    : t('plan.result.atYourPace'),
)

const canAlready = computed(() => props.plan.level.knownSkills.length > 0)
const knownHeading = computed(() => (canAlready.value ? t('plan.result.canAlready') : t('plan.result.startWith')))
const knownIcon = computed(() => (canAlready.value ? 'check' : 'learn'))

/** What the check found known, else the first Learn units of the plan. */
const known = computed(() => {
  if (canAlready.value) return props.plan.level.knownSkills.map((skill) => t(SKILL_CAN_DO_KEYS[skill]))
  return props.plan.steps
    .filter((step) => step.kind === 'LEARN_UNIT')
    .slice(0, LIST_LENGTH)
    .map((step) => step.title)
})

const learnHeading = computed(() => {
  const forWhat = t(REASONS[props.plan.goal.reason].forLabel)
  return props.plan.milestone ? t('plan.result.learnMilestone') : t('plan.result.learnFor', { for: forWhat })
})

/** What the check found new, else SparkBot's list, else the goals themselves. */
const toLearn = computed(() => {
  const { newSkills } = props.plan.level
  if (newSkills.length) return newSkills.slice(0, LIST_LENGTH).map((skill) => t(SKILL_LEARN_KEYS[skill]))
  if (props.plan.goal.workOn.length) return props.plan.goal.workOn.slice(0, LIST_LENGTH)
  return props.plan.goal.goals.slice(0, LIST_LENGTH).map((goal) => t(OUTCOME_KEYS[goal]))
})

const firstClassWeek = computed(() => props.plan.steps.find((step) => step.kind === 'CLASS')?.weekFrom ?? null)

const week = computed(() => {
  const { pace } = props.plan
  const rows = [
    {
      key: 'pace',
      icon: 'clock',
      title:
        pace.daysPerWeek >= 7
          ? t('plan.result.weekEveryDay', { minutes: pace.minutesPerDay })
          : t('plan.result.weekDays', { minutes: pace.minutesPerDay, days: pace.daysPerWeek }),
      meta:
        firstClassWeek.value === null
          ? t('plan.result.weekLearn')
          : t('plan.result.weekLearnClass', { week: firstClassWeek.value }),
    },
  ]
  for (const focus of props.plan.goal.focus) {
    if (focus === 'BALANCED') continue
    rows.push({ key: focus, icon: FOCUSES[focus].icon, title: t(FOCUSES[focus].week), meta: t(FOCUSES[focus].where) })
  }
  return rows
})

const milestoneSteps = computed(() =>
  props.plan.steps
    .filter((step) => props.plan.milestone?.stepPositions.includes(step.position))
    .map((step) => step.title)
    .join(', '),
)
</script>
