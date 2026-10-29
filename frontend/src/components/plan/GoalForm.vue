<template>
  <div class="plan-goal-form">
    <section v-if="show('reason')" class="plan-form-section">
      <h3 v-if="variant === 'chips'" class="plan-form-label">{{ t('plan.setup.reasonsLabel') }}</h3>
      <PlanChoiceGrid
        :choices="reasonChoices"
        :selected="model.reason ? [model.reason] : []"
        :label="t('plan.setup.reasonsLabel')"
        :variant="variant"
        test-id="reason-choices"
        @toggle="pickReason"
      />
      <div v-if="model.reason === 'OTHER'" class="field">
        <label class="field-label" :for="`${baseId}-reason-text`">{{ t('plan.setup.otherLabel') }}</label>
        <input
          :id="`${baseId}-reason-text`"
          v-model="reasonText"
          class="input"
          maxlength="120"
          :placeholder="t('plan.setup.otherPlaceholder')"
        />
      </div>
    </section>

    <section v-if="show('goals')" class="plan-form-section">
      <h3 class="plan-form-label">{{ t('plan.setup.goalsLabel') }}</h3>
      <PlanChoiceGrid
        :choices="goalChoices"
        :selected="model.goals"
        :label="t('plan.setup.goalsLabel')"
        variant="chips"
        test-id="goal-choices"
        @toggle="pickGoal"
      />
      <div class="field">
        <label class="field-label" :for="`${baseId}-own-words`">{{ t('plan.setup.ownWordsLabel') }}</label>
        <textarea
          :id="`${baseId}-own-words`"
          v-model="goalText"
          class="textarea"
          rows="3"
          :maxlength="goalTextMax"
          :placeholder="t('plan.setup.ownWordsPlaceholder')"
          :aria-describedby="`${baseId}-own-words-hint`"
        />
        <div class="field-foot">
          <p :id="`${baseId}-own-words-hint`" class="field-hint">{{ t('plan.setup.ownWordsHint') }}</p>
          <p class="field-hint field-counter" aria-live="polite">
            {{ t('plan.setup.counter', { count: model.goalText.length, max: goalTextMax }) }}
          </p>
        </div>
      </div>
    </section>

    <section v-if="show('deadline')" class="plan-form-section">
      <h3 class="plan-form-label">{{ t('plan.setup.reachIn') }}</h3>
      <PlanChoiceGrid
        :choices="deadlineChoices"
        :selected="model.deadlineChoice ? [model.deadlineChoice] : []"
        :label="t('plan.setup.reachIn')"
        variant="chips"
        test-id="deadline-choices"
        @toggle="pickDeadline"
      />
      <div v-if="model.deadlineChoice === 'date'" class="field plan-date-field">
        <label class="field-label" :for="`${baseId}-date`">{{ t('plan.setup.dateLabel') }}</label>
        <input
          :id="`${baseId}-date`"
          v-model="customDate"
          class="input"
          type="date"
          :min="earliestDeadline()"
          :max="latestDeadline()"
        />
      </div>
      <p v-if="deadline" class="plan-form-note">
        <IconGlyph name="calendar" :size="16" />{{ t('plan.setup.thatsAround', { date: longDate(deadline) }) }}
      </p>
      <p v-else-if="model.deadlineChoice === 'none'" class="plan-form-note">
        <IconGlyph name="calendar" :size="16" />{{ t('plan.setup.noRush') }}
      </p>
    </section>

    <section v-if="show('pace')" class="plan-form-section">
      <h3 class="plan-form-label">{{ t('plan.setup.timeLabel') }}</h3>
      <PlanChoiceGrid
        :choices="paceChoices"
        :selected="model.pace ? [model.pace] : []"
        :label="t('plan.setup.timeLabel')"
        :variant="variant"
        test-id="pace-choices"
        @toggle="pickPace"
      />
      <p v-if="selectedPace" class="plan-form-note">
        <IconGlyph name="info" :size="16" />{{
          t('plan.setup.dailyGoalNote', { pace: t(PACES[selectedPace.key].label), xp: selectedPace.dailyGoalXp })
        }}
      </p>
    </section>

    <section v-if="show('focus')" class="plan-form-section">
      <h3 v-if="variant === 'chips'" class="plan-form-label">{{ t('plan.setup.focusLead') }}</h3>
      <PlanChoiceGrid
        :choices="focusChoices"
        :selected="model.focus"
        :label="t('plan.setup.focusTitle')"
        :variant="variant"
        :suggested-label="t('plan.setup.suggested')"
        test-id="focus-choices"
        @toggle="pickFocus"
      />
      <p v-if="focusLimit" class="field-error" role="alert">{{ t('plan.setup.focusLimit') }}</p>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, useId } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { planMessages } from '@/plugins/planMessages'
import type { PlanFocus, PlanGoal, PlanOptions, PlanPace, PlanReason } from '@/types/plan'

import {
  DEADLINE_CHOICES,
  deadlineFor,
  earliestDeadline,
  latestDeadline,
  toggledFocus,
  toggledGoals,
  type DeadlineChoice,
  type GoalDraft,
} from './goalDraft'
import PlanChoiceGrid, { type PlanChoice } from './PlanChoiceGrid.vue'
import { longDate } from './planFormat'
import { FOCUSES, GOAL_KEYS, PACES, REASONS } from './planLabels'

type Section = 'reason' | 'goals' | 'deadline' | 'pace' | 'focus'

/**
 * The goal's fields: the setup shows one section per step (as cards), "Edit my goal" and "Update my goal" show
 * several (as chips). Every change replaces the draft, so the parent owns it.
 */
const props = withDefaults(
  defineProps<{
    options: PlanOptions
    sections: readonly Section[]
    variant?: 'cards' | 'chips'
    /** The focus the level check suggests ("listening was new to you"). */
    suggestedFocus?: PlanFocus | null
  }>(),
  { variant: 'cards', suggestedFocus: null },
)

const model = defineModel<GoalDraft>({ required: true })

const { t } = useLocalMessages({ plan: planMessages })
const baseId = useId()
const focusLimit = ref(false)

const goalTextMax = computed(() => props.options.goalTextMax)
const deadline = computed(() =>
  model.value.deadlineChoice === 'none' ? null : deadlineFor(model.value.deadlineChoice, model.value.customDate),
)
const selectedPace = computed(() => props.options.paces.find((pace) => pace.key === model.value.pace) ?? null)

const reasonText = computed({
  get: () => model.value.reasonText,
  set: (value: string) => update({ reasonText: value }),
})
const goalText = computed({
  get: () => model.value.goalText,
  set: (value: string) => update({ goalText: value.slice(0, goalTextMax.value) }),
})
const customDate = computed({
  get: () => model.value.customDate,
  set: (value: string) => update({ customDate: value }),
})

const reasonChoices = computed<PlanChoice[]>(() =>
  props.options.reasons.map((reason) => ({
    value: reason.key,
    label: t(REASONS[reason.key].label),
    hint: t(REASONS[reason.key].hint),
    icon: REASONS[reason.key].icon,
  })),
)

/** The reason's suggested goals, any goal already picked, and "Not sure yet" last. */
const goalChoices = computed<PlanChoice[]>(() => {
  const suggested = props.options.reasons.find((reason) => reason.key === model.value.reason)?.goals ?? []
  const shown: PlanGoal[] = [...suggested]
  for (const goal of model.value.goals) if (!shown.includes(goal) && goal !== 'NOT_SURE') shown.push(goal)
  shown.push('NOT_SURE')
  return shown.map((goal) => ({ value: goal, label: t(GOAL_KEYS[goal]) }))
})

const deadlineChoices = computed<PlanChoice[]>(() =>
  DEADLINE_CHOICES.map((choice) => ({ value: choice, label: deadlineLabel(choice) })),
)

const paceChoices = computed<PlanChoice[]>(() =>
  props.options.paces.map((pace) => ({
    value: pace.key,
    label: t(PACES[pace.key].label),
    hint: t(PACES[pace.key].hint),
    icon: PACES[pace.key].icon,
  })),
)

const focusChoices = computed<PlanChoice[]>(() =>
  props.options.focuses.map((focus) => ({
    value: focus,
    label: t(FOCUSES[focus].label),
    hint: t(FOCUSES[focus].hint),
    icon: FOCUSES[focus].icon,
    suggested: focus === props.suggestedFocus,
  })),
)

function show(section: Section): boolean {
  return props.sections.includes(section)
}

function update(patch: Partial<GoalDraft>): void {
  model.value = { ...model.value, ...patch }
}

function deadlineLabel(choice: DeadlineChoice): string {
  switch (choice) {
    case '2w':
      return t('plan.common.weeks', { count: 2 }, 2)
    case '1m':
      return t('plan.common.months', { count: 1 }, 1)
    case '2m':
      return t('plan.common.months', { count: 2 }, 2)
    case '3m':
      return t('plan.common.months', { count: 3 }, 3)
    case '6m':
      return t('plan.common.months', { count: 6 }, 6)
    case 'none':
      return t('plan.setup.noDeadline')
    default:
      return t('plan.setup.pickDate')
  }
}

function pickReason(value: string): void {
  // Another reason suggests other goals; the ones already picked stay picked.
  update({ reason: value as PlanReason })
}

function pickGoal(value: string): void {
  update({ goals: toggledGoals(model.value.goals, value as PlanGoal) })
}

function pickDeadline(value: string): void {
  update({ deadlineChoice: value as DeadlineChoice })
}

function pickPace(value: string): void {
  update({ pace: value as PlanPace })
}

function pickFocus(value: string): void {
  const next = toggledFocus(model.value.focus, value as PlanFocus)
  focusLimit.value = next === null
  if (next) update({ focus: next })
}
</script>
