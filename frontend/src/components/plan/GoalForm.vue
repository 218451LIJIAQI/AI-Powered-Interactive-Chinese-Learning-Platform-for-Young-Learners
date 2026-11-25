<template>
  <div class="plan-goal-form" :class="{ 'is-compact': compact }">
    <section
      v-if="show('reason')"
      class="plan-form-section"
      :aria-labelledby="compact ? `${baseId}-reason-label` : undefined"
    >
      <h3 v-if="compact" :id="`${baseId}-reason-label`" class="plan-form-label">
        {{ t('plan.setup.reasonsLabel') }}
      </h3>
      <PlanChoiceGrid
        :choices="reasonChoices"
        :selected="model.reason ? [model.reason] : []"
        :label="t('plan.setup.reasonsLabel')"
        :variant="compact ? 'chips' : 'cards'"
        :columns="3"
        :compact="compact"
        test-id="reason-choices"
        @toggle="pickReason"
      />
      <div v-if="model.reason === 'OTHER'" class="field">
        <label class="field-label" :for="`${baseId}-reason-text`">
          {{ t('plan.setup.otherLabel') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <input
          :id="`${baseId}-reason-text`"
          v-model="reasonText"
          class="input"
          maxlength="120"
          required
          aria-required="true"
          :placeholder="t('plan.setup.otherPlaceholder')"
          :aria-describedby="`${baseId}-reason-text-hint`"
        />
        <div class="field-foot">
          <p :id="`${baseId}-reason-text-hint`" class="field-hint">{{ t('plan.setup.ownWordsHint') }}</p>
          <p class="field-hint field-counter">
            {{ t('plan.setup.counter', { count: model.reasonText.length, max: 120 }) }}
          </p>
        </div>
      </div>
    </section>

    <section
      v-if="show('goals')"
      class="plan-form-section"
      :aria-labelledby="compact ? `${baseId}-goals-label` : undefined"
    >
      <h3 v-if="compact" :id="`${baseId}-goals-label`" class="plan-form-label">{{ t('plan.setup.goalsLabel') }}</h3>
      <PlanChoiceGrid
        :choices="goalChoices"
        :selected="model.goals"
        :label="t('plan.setup.goalsLabel')"
        variant="chips"
        :columns="compact ? 2 : 4"
        :compact="compact"
        test-id="goal-choices"
        @toggle="pickGoal"
      />
      <div class="field">
        <label class="field-label" :for="`${baseId}-own-words`">
          {{ t('plan.setup.ownWordsLabel') }}
          <span class="plan-optional">{{ t('plan.setup.optional') }}</span>
        </label>
        <textarea
          :id="`${baseId}-own-words`"
          v-model="goalText"
          class="textarea"
          rows="3"
          :maxlength="options.goalTextMax"
          :placeholder="t('plan.setup.ownWordsPlaceholder')"
          :aria-describedby="`${baseId}-own-words-hint`"
        />
        <div class="field-foot">
          <p :id="`${baseId}-own-words-hint`" class="field-hint">{{ t('plan.setup.ownWordsHint') }}</p>
          <p class="field-hint field-counter">
            {{ t('plan.setup.counter', { count: model.goalText.length, max: options.goalTextMax }) }}
          </p>
        </div>
      </div>
    </section>

    <section v-if="show('deadline')" class="plan-form-section">
      <h3 :id="`${baseId}-date-label`" class="plan-form-label">{{ t('plan.date.label') }}</h3>
      <!-- One row whether a date is picked or not: the field (click it to change the date) and "No date" beside it. -->
      <div class="plan-date-row" role="group" :aria-labelledby="`${baseId}-date-label`">
        <button
          class="plan-date-field"
          type="button"
          aria-haspopup="dialog"
          :aria-expanded="calendarOpen"
          data-testid="date-field"
          @click="calendarOpen = true"
        >
          <span :class="{ 'is-placeholder': !model.deadline }">{{ dateFieldText }}</span>
          <IconGlyph name="calendar" :size="18" />
        </button>
        <button
          class="plan-date-none"
          :class="{ 'is-selected': model.deadline === null }"
          type="button"
          :aria-pressed="model.deadline === null"
          data-testid="date-none"
          @click="chooseDate(null)"
        >
          <IconGlyph v-if="model.deadline === null" name="check" :size="14" />{{ t('plan.date.noDate') }}
        </button>
      </div>
      <p v-if="compact && studyLine" class="plan-form-note" aria-live="polite" data-testid="study-line">
        <IconGlyph name="calendarCheck" :size="14" />{{ studyLine }}
      </p>
    </section>

    <section v-if="show('pace')" class="plan-form-section" :aria-labelledby="`${baseId}-pace-label`">
      <h3 :id="`${baseId}-pace-label`" class="plan-form-label">{{ t('plan.setup.timeLabel') }}</h3>
      <PlanChoiceGrid
        :choices="paceChoices"
        :selected="[model.pace]"
        :label="t('plan.setup.timeLabel')"
        :variant="compact ? 'chips' : 'cards'"
        :columns="compact ? 2 : 4"
        :compact="compact"
        test-id="pace-choices"
        @toggle="pickPace"
      />
      <p v-if="!compact && studyLine" class="plan-form-note" aria-live="polite" data-testid="study-line">
        <IconGlyph name="calendarCheck" :size="14" />{{ studyLine }}
      </p>
    </section>

    <section
      v-if="show('focus')"
      class="plan-form-section"
      :aria-labelledby="compact ? `${baseId}-focus-label` : undefined"
    >
      <h3 v-if="compact" :id="`${baseId}-focus-label`" class="plan-form-label">{{ t('plan.setup.focusLabel') }}</h3>
      <PlanChoiceGrid
        :choices="focusChoices"
        :selected="model.focus"
        :label="t('plan.setup.focusTitle')"
        :variant="compact ? 'chips' : 'cards'"
        :compact="compact"
        :suggested-label="t('plan.setup.suggested')"
        test-id="focus-choices"
        @toggle="pickFocus"
      />
      <p v-if="focusLimit" class="field-error" role="alert">{{ t('plan.setup.focusLimit') }}</p>
    </section>

    <GoalDatePicker
      v-if="calendarOpen"
      :today="options.today"
      :value="model.deadline ?? null"
      :no-date-choice="calendarNoDate"
      @pick="chooseDate"
      @close="calendarOpen = false"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { planSetupMessages } from '@/plugins/planSetupMessages'
import { planSummaryMessages } from '@/plugins/planSummaryMessages'
import type { PlanFocus, PlanGoal, PlanOptions, PlanPace, PlanReason } from '@/types/plan'
import { daysBetween, formatWeekdayLongDate } from '@/utils/format'

import GoalDatePicker from './GoalDatePicker.vue'
import { MAX_FOCUS, toggledFocus, toggledGoals, type GoalDraft } from './goalDraft'
import PlanChoiceGrid, { type PlanChoice } from './PlanChoiceGrid.vue'
import { studyFormula } from './planFormat'
import { FOCUS_ICONS, PACES, REASON_ICONS } from './planLabels'

export type Section = 'reason' | 'goals' | 'deadline' | 'pace' | 'focus'

/**
 * The goal's fields: the setup shows one or two sections per step (as cards), "Edit my goal" and "Update my goal" show
 * several (`compact`, as chips). The goal date is picked on a calendar or set to "No date"; a line joins the date and
 * the daily time into the hours of Chinese they make. Every change replaces the draft, so the parent owns it.
 */
const props = withDefaults(
  defineProps<{
    options: PlanOptions
    sections: readonly Section[]
    /** Chips instead of cards, several sections at once (the edit dialog and drawer). */
    compact?: boolean
    /** The focus the level check suggests ("listening was new to you"). */
    suggestedFocus?: PlanFocus | null
    /** Whether the calendar repeats "No date" in its footer (false in compact edit dialog and plan drawer). */
    calendarNoDate?: boolean
  }>(),
  { compact: false, suggestedFocus: null, calendarNoDate: true },
)

const model = defineModel<GoalDraft>({ required: true })

const { t, locale } = useLocalMessages({ plan: [planSummaryMessages, planSetupMessages] })
const baseId = useId()
const focusLimit = ref(false)
const calendarOpen = ref(false)

watch(
  () => model.value.focus.length,
  (count) => {
    if (count < MAX_FOCUS) focusLimit.value = false
  },
)

const paceBand = computed(() => props.options.paces.find((pace) => pace.key === model.value.pace) ?? null)
const daysToGo = computed(() => (model.value.deadline ? daysBetween(props.options.today, model.value.deadline) : 0))

/**
 * The date field: "Sat 18 Jul 2026 · 61 days to go" in the setup, the date alone in the edit dialog and drawer, or the
 * placeholder until a day is picked.
 */
const dateFieldText = computed(() => {
  const { deadline } = model.value
  if (!deadline) return t('plan.date.placeholder')
  const date = formatWeekdayLongDate(deadline, locale.value)
  return props.compact ? date : t('plan.date.fieldDays', { date, count: daysToGo.value }, daysToGo.value)
})

/**
 * "1–2 hours a day adds up to 61–122 hours of Chinese." in the setup (the date field above already shows the date and
 * the days to go), "61 days × 1–2 hours a day = 61–122 hours of Chinese" in the dialog and drawer, or what "No date"
 * means.
 */
const studyLine = computed(() => {
  const { deadline } = model.value
  if (deadline === null) return props.compact ? t('plan.date.noDateLine') : t('plan.date.noDateLineLong')
  if (!deadline || !paceBand.value) return ''
  return studyFormula(t, daysToGo.value, paceBand.value, !props.compact)
})

/** A text field of the draft, for `v-model`. */
function textField(key: 'reasonText' | 'goalText') {
  return computed({ get: () => model.value[key], set: (value: string) => update({ [key]: value }) })
}

const reasonText = textField('reasonText')
const goalText = textField('goalText')

const reasonChoices = computed<PlanChoice<PlanReason>[]>(() =>
  (Object.keys(REASON_ICONS) as PlanReason[]).map((key) => ({
    value: key,
    label: t(`plan.reason.${key}`),
    hint: t(`plan.reason.${key}_hint`),
    icon: REASON_ICONS[key],
  })),
)

/** The reason's suggested goals, any goal already picked, and "Not sure yet" last. */
const goalChoices = computed<PlanChoice<PlanGoal>[]>(() => {
  const suggested = props.options.reasons.find((reason) => reason.key === model.value.reason)?.goals ?? []
  const shown: PlanGoal[] = [...suggested]
  for (const goal of model.value.goals) if (!shown.includes(goal) && goal !== 'NOT_SURE') shown.push(goal)
  shown.push('NOT_SURE')
  return shown.map((goal) => ({ value: goal, label: t(`plan.goal.${goal}`) }))
})

const paceChoices = computed<PlanChoice<PlanPace>[]>(() =>
  props.options.paces.map((pace) => ({ value: pace.key, label: t(PACES[pace.key].label), icon: PACES[pace.key].icon })),
)

const focusChoices = computed<PlanChoice<PlanFocus>[]>(() => {
  const lead = model.value.reason ? t(`plan.reason.${model.value.reason}_for`) : undefined
  return (Object.keys(FOCUS_ICONS) as PlanFocus[]).map((focus) => ({
    value: focus,
    label: t(`plan.focus.${focus}`),
    hint: t(`plan.focus.${focus}_hint`),
    icon: FOCUS_ICONS[focus],
    lead,
    foot: t(`plan.focus.${focus}_week`),
    suggested: focus === props.suggestedFocus,
  }))
})

function show(section: Section): boolean {
  return props.sections.includes(section)
}

function update(patch: Partial<GoalDraft>): void {
  model.value = { ...model.value, ...patch }
}

function pickReason(reason: PlanReason): void {
  // Another reason suggests other goals; the ones already picked stay picked.
  update({ reason })
}

function pickGoal(goal: PlanGoal): void {
  update({ goals: toggledGoals(model.value.goals, goal) })
}

function chooseDate(deadline: string | null): void {
  calendarOpen.value = false
  update({ deadline })
}

function pickPace(pace: PlanPace): void {
  update({ pace })
}

function pickFocus(focus: PlanFocus): void {
  const next = toggledFocus(model.value.focus, focus)
  focusLimit.value = next === null
  if (next) update({ focus: next })
}
</script>
