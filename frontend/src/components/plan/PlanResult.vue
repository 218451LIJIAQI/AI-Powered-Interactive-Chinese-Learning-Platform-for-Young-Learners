<template>
  <div class="plan-result" data-testid="plan-result">
    <section class="plan-result-goal" aria-labelledby="plan-result-goal">
      <h2 id="plan-result-goal" class="plan-result-label">{{ t('plan.result.yourGoal') }}</h2>
      <p class="plan-result-goal-title">{{ title }}</p>
      <p class="plan-result-note">{{ when }}</p>
      <figure v-if="plan.goal.summary" class="plan-quote">
        <blockquote>“{{ plan.goal.summary }}”</blockquote>
        <figcaption><IconGlyph name="ai" :size="14" />{{ t('plan.result.summaryNote') }}</figcaption>
      </figure>
      <figure v-else-if="plan.goal.ownWords" class="plan-quote">
        <blockquote>“{{ plan.goal.ownWords }}”</blockquote>
        <figcaption>{{ t('plan.result.yourWords') }}</figcaption>
      </figure>
      <ul class="plan-chips">
        <li v-for="goal in plan.goal.goals" :key="goal" class="plan-chip is-soft">{{ t(GOAL_KEYS[goal]) }}</li>
      </ul>
      <div v-if="plan.milestone" class="plan-milestone" role="note">
        <IconGlyph name="target" :size="18" />
        <div>
          <p>{{ t('plan.result.ambitious') }}</p>
          <p class="plan-milestone-text">
            {{
              plan.milestone.text ??
              t('plan.result.milestoneRules', { date: longDate(plan.milestone.date), steps: milestoneSteps })
            }}
          </p>
        </div>
      </div>
    </section>

    <section class="plan-result-level" aria-labelledby="plan-result-level">
      <h2 id="plan-result-level" class="plan-result-label">{{ t('plan.result.yourLevel') }}</h2>
      <p class="plan-result-level-name">
        <span>{{ t(LEVEL_KEYS[plan.level.placed]) }}</span>
        <span class="chip">{{
          plan.level.placed === 'NEW_TO_MANDARIN' ? t('plan.result.startingFresh') : t('plan.result.headStart')
        }}</span>
      </p>
      <LevelLadder :placed="plan.level.placed" :target="plan.level.target" />
      <p v-if="plan.level.placed === 'NEW_TO_MANDARIN'" class="plan-result-note">{{ t('plan.result.freshNote') }}</p>
      <template v-if="plan.level.knownSkills.length">
        <h3 class="plan-result-sub">{{ t('plan.result.alreadyKnow') }}</h3>
        <ul class="plan-chips">
          <li v-for="skill in plan.level.knownSkills" :key="skill" class="plan-chip is-known">
            {{ t(SKILL_KEYS[skill]) }}
          </li>
        </ul>
      </template>
      <h3 class="plan-result-sub">{{ t('plan.result.workOn') }}</h3>
      <ul class="plan-chips">
        <li v-for="item in workOn" :key="item" class="plan-chip">{{ item }}</li>
      </ul>
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
import { FOCUSES, GOAL_KEYS, LEVEL_KEYS, SKILL_KEYS } from './planLabels'

/**
 * "Your plan is ready!": the goal first, as the headline (SparkBot's summary for the student to confirm, or their own
 * words in a rule-based plan; an ambitious timeline gets a first milestone), then the Mandarin level from the level
 * check with the ladder and the head start.
 */
const props = defineProps<{ plan: LearningPlan }>()

const { t } = useLocalMessages({ plan: planMessages })

const title = computed(() => goalTitle(t, props.plan.goal.title, props.plan.goal.reason))

const when = computed(() =>
  props.plan.goal.deadline
    ? t('plan.result.byDate', {
        date: longDate(props.plan.goal.deadline),
        span: spanUntil(t, props.plan.goal.deadline),
      })
    : t('plan.result.atYourPace'),
)

/** SparkBot's skills to work on, else what the level check found new and the student's focus. */
const workOn = computed(() => {
  if (props.plan.goal.workOn.length) return props.plan.goal.workOn
  const items = [
    ...props.plan.level.newSkills.map((skill) => t(SKILL_KEYS[skill])),
    ...props.plan.goal.focus.map((focus) => t(FOCUSES[focus].label)),
  ]
  if (!items.length) items.push(...props.plan.goal.goals.map((goal) => t(GOAL_KEYS[goal])))
  return [...new Set(items)].slice(0, 4)
})

const milestoneSteps = computed(() =>
  props.plan.steps
    .filter((step) => props.plan.milestone?.stepPositions.includes(step.position))
    .map((step) => step.title)
    .join(', '),
)
</script>
