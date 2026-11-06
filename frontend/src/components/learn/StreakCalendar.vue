<template>
  <div class="progress-calendar">
    <div class="progress-calendar-head">
      <slot />
      <div class="progress-calendar-month">
        <h3 class="card-title">{{ monthLabel }}</h3>
        <div class="btn-group is-sm">
          <button
            class="btn btn-ghost btn-icon btn-sm"
            type="button"
            :disabled="!calendar.previousMonth || busy"
            :aria-label="t('progress.previousMonth')"
            @click="emit('month', calendar.previousMonth!)"
          >
            <IconGlyph name="chevronLeft" :size="16" />
          </button>
          <button
            class="btn btn-ghost btn-icon btn-sm"
            type="button"
            :disabled="!calendar.nextMonth || busy"
            :aria-label="t('progress.nextMonth')"
            @click="emit('month', calendar.nextMonth!)"
          >
            <IconGlyph name="chevronRight" :size="16" />
          </button>
        </div>
      </div>
    </div>
    <table class="progress-calendar-grid" :class="{ 'is-busy': busy }" :aria-label="monthLabel">
      <thead>
        <tr>
          <th v-for="day in weekdays" :key="day.long" scope="col" :abbr="day.long">{{ day.short }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(week, index) in weeks" :key="index">
          <td v-for="(cell, dayIndex) in week" :key="dayIndex">
            <span v-if="cell" class="progress-day" :class="`is-${cell.state}`" :aria-label="cell.label">
              <span aria-hidden="true">{{ cell.day }}</span>
            </span>
          </td>
        </tr>
      </tbody>
    </table>
    <ul class="progress-calendar-legend">
      <li><span class="progress-legend-dot is-learned" aria-hidden="true" />{{ t('progress.legendLearned') }}</li>
      <li><span class="progress-legend-dot is-today" aria-hidden="true" />{{ t('progress.legendToday') }}</li>
      <li><span class="progress-legend-dot is-missed" aria-hidden="true" />{{ t('progress.legendMissed') }}</li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { progressMessages } from '@/plugins/progressMessages'
import type { ProgressOverview } from '@/types/learn'
import { formatDate, parseDate } from '@/utils/format'

/**
 * A month of the streak: every day the daily goal was reached is filled, today has a ring until it is, days still to
 * come are dashed and missed days plain. Each day says its state in words, not only in colour. The default slot holds
 * the streak figure, on the row of the month.
 */
interface Props {
  calendar: ProgressOverview['calendar']
  busy?: boolean
}

const props = withDefaults(defineProps<Props>(), { busy: false })
const emit = defineEmits<{ month: [month: string] }>()

const { t, locale } = useLocalMessages({ progress: progressMessages })

type DayState = 'learned' | 'today' | 'learned-today' | 'missed' | 'future' | 'before'

function iso(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

const monthStart = computed(() => parseDate(`${props.calendar.month}-01`) ?? new Date())

const monthLabel = computed(() => formatDate(monthStart.value, { month: 'long', year: 'numeric' }, locale.value))

/** Monday first. */
const weekdays = computed(() =>
  Array.from({ length: 7 }, (_, index) => {
    const date = new Date(2024, 0, 1 + index)
    return {
      short: formatDate(date, { weekday: 'narrow' }, locale.value),
      long: formatDate(date, { weekday: 'long' }, locale.value),
    }
  }),
)

const weeks = computed(() => {
  const start = monthStart.value
  const goalDays = new Set(props.calendar.goalDays)
  const daysInMonth = new Date(start.getFullYear(), start.getMonth() + 1, 0).getDate()
  const leading = (start.getDay() + 6) % 7
  const cells: ({ day: number; state: DayState; label: string } | null)[] = Array.from({ length: leading }, () => null)
  for (let day = 1; day <= daysInMonth; day++) {
    const date = new Date(start.getFullYear(), start.getMonth(), day)
    const key = iso(date)
    const learned = goalDays.has(key)
    let state: DayState
    if (key === props.calendar.today) state = learned ? 'learned-today' : 'today'
    else if (learned) state = 'learned'
    else if (key > props.calendar.today) state = 'future'
    else if (key < props.calendar.joinedOn) state = 'before'
    else state = 'missed'
    const dateLabel = formatDate(date, { month: 'long', day: 'numeric' }, locale.value)
    cells.push({ day, state, label: t(labelKey(state), { date: dateLabel }) })
  }
  while (cells.length % 7) cells.push(null)
  return Array.from({ length: cells.length / 7 }, (_, index) => cells.slice(index * 7, index * 7 + 7))
})

function labelKey(state: DayState): string {
  switch (state) {
    case 'learned':
      return 'progress.dayLearned'
    case 'learned-today':
      return 'progress.dayLearnedToday'
    case 'today':
      return 'progress.dayToday'
    case 'missed':
      return 'progress.dayMissed'
    default:
      return 'progress.dayPlain'
  }
}
</script>
