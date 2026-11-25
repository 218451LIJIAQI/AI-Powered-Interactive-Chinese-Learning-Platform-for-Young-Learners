<template>
  <div class="progress-calendar">
    <div class="progress-calendar-head">
      <slot />
      <div class="progress-calendar-month">
        <h3 class="card-title">{{ monthLabel }}</h3>
        <span class="sr-only" role="status" aria-live="polite" aria-atomic="true">{{ announcedMonth }}</span>
        <div class="btn-group is-sm">
          <button
            class="btn btn-ghost btn-icon btn-sm"
            type="button"
            :aria-disabled="!calendar.previousMonth || undefined"
            :aria-label="t('progress.previousMonth')"
            :title="t('progress.previousMonth')"
            @click="switchMonth(calendar.previousMonth)"
          >
            <IconGlyph name="chevronLeft" :size="16" />
          </button>
          <button
            class="btn btn-ghost btn-icon btn-sm"
            type="button"
            :aria-disabled="!calendar.nextMonth || undefined"
            :aria-label="t('progress.nextMonth')"
            :title="t('progress.nextMonth')"
            @click="switchMonth(calendar.nextMonth)"
          >
            <IconGlyph name="chevronRight" :size="16" />
          </button>
        </div>
      </div>
    </div>
    <table class="progress-calendar-grid" :aria-label="monthLabel">
      <thead>
        <tr>
          <th v-for="day in weekdays" :key="day.long" scope="col" :abbr="day.long">{{ day.short }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(week, index) in weeks" :key="index">
          <td v-for="(cell, dayIndex) in week" :key="dayIndex">
            <span v-if="cell" class="progress-day" :class="`is-${cell.state}`" role="img" :aria-label="cell.label">
              <span aria-hidden="true">{{ cell.day }}</span>
            </span>
          </td>
        </tr>
      </tbody>
    </table>
    <ul class="progress-calendar-legend">
      <li><span class="progress-legend-dot is-learned" aria-hidden="true" />{{ t('progress.legendLearned') }}</li>
      <li><span class="progress-legend-dot is-today" aria-hidden="true" />{{ t('progress.legendToday') }}</li>
      <li><span class="progress-legend-dot" aria-hidden="true" />{{ t('progress.legendMissed') }}</li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { progressMessages } from '@/plugins/progressMessages'
import type { ProgressOverview } from '@/types/learn'
import { calendarWeekdays, calendarWeeks, createDateFormatter, formatDate, parseDate } from '@/utils/format'

/**
 * A month of the streak: every day the daily goal was reached is filled, today has a ring until it is, days still to
 * come are faded and missed days plain. Each day says its state in words, not only in colour. The default slot holds
 * the streak figure, on the row of the month.
 */
const props = defineProps<{ calendar: ProgressOverview['calendar'] }>()
const emit = defineEmits<{ month: [month: string] }>()

const { t, locale } = useLocalMessages({ progress: progressMessages })

type DayState = 'learned' | 'today' | 'learned-today' | 'missed' | 'future' | 'before'

const LABEL_KEYS: Partial<Record<DayState, string>> = {
  learned: 'progress.dayLearned',
  'learned-today': 'progress.dayLearnedToday',
  today: 'progress.dayToday',
  missed: 'progress.dayMissed',
}

const monthStart = computed(() => parseDate(`${props.calendar.month}-01`)!)

const monthLabel = computed(() => formatDate(monthStart.value, { month: 'long', year: 'numeric' }, locale.value))
const announcedMonth = ref('')

watch(
  () => props.calendar.month,
  (month, previous) => {
    if (previous && month !== previous) announcedMonth.value = monthLabel.value
  },
)

function switchMonth(month: string | null): void {
  if (!month) return
  emit('month', month)
}

const weekdays = computed(() => calendarWeekdays(locale.value))

const weeks = computed(() => {
  const goalDays = new Set(props.calendar.goalDays)
  const formatDay = createDateFormatter({ month: 'long', day: 'numeric' }, locale.value)
  return calendarWeeks(monthStart.value, (date, day) => {
    const key = `${props.calendar.month}-${String(day).padStart(2, '0')}`
    const learned = goalDays.has(key)
    let state: DayState
    if (key === props.calendar.today) state = learned ? 'learned-today' : 'today'
    else if (learned) state = 'learned'
    else if (key > props.calendar.today) state = 'future'
    else if (key < props.calendar.joinedOn) state = 'before'
    else state = 'missed'
    const dateLabel = formatDay(date)
    const labelKey = LABEL_KEYS[state]
    return { day, state, label: labelKey ? t(labelKey, { date: dateLabel }) : dateLabel }
  })
})
</script>
