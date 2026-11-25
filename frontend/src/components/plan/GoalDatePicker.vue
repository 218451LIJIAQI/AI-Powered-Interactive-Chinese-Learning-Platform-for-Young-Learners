<template>
  <AppOverlay :title="t('plan.date.title')" size="lg" test-id="goal-date-picker" @close="emit('close')">
    <div ref="calendar" class="plan-calendar" @keydown="onKeydown">
      <div class="plan-calendar-months">
        <span class="sr-only" role="status" aria-live="polite" aria-atomic="true">{{ announcedRange }}</span>
        <div class="plan-calendar-nav">
          <button
            class="btn btn-ghost btn-icon btn-sm"
            type="button"
            :disabled="!canGoBack"
            :aria-label="t('plan.date.earlier')"
            :title="t('plan.date.earlier')"
            data-testid="calendar-earlier"
            @click="moveWindow(-1)"
          >
            <IconGlyph name="chevronLeft" :size="16" />
          </button>
          <button
            class="btn btn-ghost btn-icon btn-sm"
            type="button"
            :disabled="!canGoForward"
            :aria-label="t('plan.date.later')"
            :title="t('plan.date.later')"
            data-testid="calendar-later"
            @click="moveWindow(1)"
          >
            <IconGlyph name="chevronRight" :size="16" />
          </button>
        </div>
        <section
          v-for="(month, index) in months"
          :key="index"
          class="plan-calendar-month"
          :aria-labelledby="`${baseId}-${month.key}`"
        >
          <div class="plan-calendar-head">
            <h3 :id="`${baseId}-${month.key}`" class="plan-calendar-title">{{ month.title }}</h3>
          </div>
          <table class="plan-calendar-grid">
            <thead>
              <tr>
                <th v-for="weekday in weekdays" :key="weekday.long" scope="col" :abbr="weekday.long">
                  {{ weekday.short }}
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(week, row) in month.weeks" :key="row">
                <td v-for="(cell, column) in week" :key="column">
                  <button
                    v-if="cell"
                    class="plan-calendar-day"
                    :class="{
                      'is-today': cell.day === today,
                      'is-path': isOnPath(cell.day),
                      'is-picked': cell.day === pending,
                    }"
                    type="button"
                    :disabled="!isPickable(cell.day)"
                    :tabindex="cell.day === focusDay ? 0 : -1"
                    :aria-pressed="cell.day === pending"
                    :aria-current="cell.day === today ? 'date' : undefined"
                    :aria-label="cell.label"
                    :data-day="cell.day"
                    :data-autofocus="cell.day === focusDay ? '' : undefined"
                    @click="pick(cell.day)"
                  >
                    {{ cell.number }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </section>
      </div>
      <p class="plan-calendar-picked" aria-live="polite" data-testid="calendar-picked">
        <IconGlyph name="flag" :size="16" />
        <template v-if="pending">
          <strong>{{ formatWeekdayLongDate(pending, locale) }}</strong>
          <span>· {{ t('plan.date.fromToday', { count: pendingDays }, pendingDays) }}</span>
        </template>
        <span v-else>{{ t('plan.date.pickHint') }}</span>
      </p>
    </div>
    <template #footer>
      <button
        v-if="noDateChoice"
        class="btn btn-ghost is-far-left"
        type="button"
        data-testid="calendar-no-date"
        @click="emit('pick', null)"
      >
        {{ t('plan.date.noDate') }}
      </button>
      <button class="btn btn-secondary" type="button" @click="emit('close')">{{ t('common.cancel') }}</button>
      <button
        class="btn btn-primary"
        type="button"
        :disabled="!pending || !isPickable(pending)"
        data-testid="calendar-use"
        @click="pending && isPickable(pending) && emit('pick', pending)"
      >
        {{ t('plan.date.use') }}
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, useId, watch } from 'vue'

import AppOverlay from '@/components/common/AppOverlay.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { planSetupMessages } from '@/plugins/planSetupMessages'
import {
  calendarWeekdays,
  calendarWeeks,
  createDateFormatter,
  daysBetween,
  formatWeekdayLongDate,
  isoDay,
  parseDate,
  shiftDay,
} from '@/utils/format'

import { earliestDeadline, latestDeadline } from './goalDraft'

/**
 * "Pick your target date": three months at a time from the platform's today, which is ringed. The days from today to
 * the picked day are drawn as a light path and the picked day is dark, with "N days from today" under the months.
 * "Use this date" hands the day back, "No date" chooses no date, Cancel keeps what was there. Arrow keys move a day or
 * a week, Page Up / Page Down a month; the months turn with the focus.
 */
const props = withDefaults(
  defineProps<{
    /** The platform's day (yyyy-MM-dd), which the server checks a goal date against. */
    today: string
    /** The date chosen so far, or null. */
    value: string | null
    /** "No date" in the footer; compact forms (edit dialog and plan drawer) leave it to the chip beside the date field. */
    noDateChoice?: boolean
  }>(),
  { noDateChoice: true },
)

const emit = defineEmits<{ pick: [day: string | null]; close: [] }>()

const { t, locale } = useLocalMessages({ plan: planSetupMessages })
const baseId = useId()
const calendar = ref<HTMLElement | null>(null)

/** Three months are shown at once. */
const MONTHS_SHOWN = 3

const earliest = computed(() => earliestDeadline(props.today))
const latest = computed(() => latestDeadline(props.today))

function monthIndex(day: string): number {
  const date = parseDate(day)!
  return date.getFullYear() * 12 + date.getMonth()
}

function monthStart(index: number): Date {
  return new Date(Math.floor(index / 12), index % 12, 1)
}

const todayMonth = computed(() => monthIndex(props.today))
const lastMonth = computed(() => monthIndex(latest.value))
const pending = ref(props.value !== null && isPickable(props.value) ? props.value : null)
const focusDay = ref(pending.value ?? earliest.value)
/** The first month shown, as a count of months (year × 12 + month). */
const firstMonth = ref(windowFor(focusDay.value))
const announcedRange = ref('')

watch(
  () => props.today,
  () => {
    const activeDay = calendar.value?.contains(document.activeElement)
      ? (document.activeElement as HTMLElement | null)?.dataset.day
      : undefined
    if (pending.value !== null && !isPickable(pending.value)) pending.value = null
    if (focusDay.value < earliest.value) focusDay.value = earliest.value
    else if (focusDay.value > latest.value) focusDay.value = latest.value
    const maxStart = Math.max(todayMonth.value, lastMonth.value - (MONTHS_SHOWN - 1))
    if (firstMonth.value < todayMonth.value) firstMonth.value = todayMonth.value
    else if (firstMonth.value > maxStart) firstMonth.value = maxStart
    if (!inWindow(focusDay.value)) {
      const start = isoDay(monthStart(firstMonth.value))
      focusDay.value = start < earliest.value ? earliest.value : start
    }
    if (activeDay && (!isPickable(activeDay) || activeDay !== focusDay.value)) void focusDate()
  },
)

watch(firstMonth, () => {
  const shown = months.value
  if (shown.length) announcedRange.value = `${shown[0]!.title} – ${shown[shown.length - 1]!.title}`
})

const canGoBack = computed(() => firstMonth.value > todayMonth.value)
const canGoForward = computed(() => firstMonth.value + MONTHS_SHOWN - 1 < lastMonth.value)

/** The window that shows `day`: from today's month while it fits, else ending with the month of `day`. */
function windowFor(day: string): number {
  return Math.max(todayMonth.value, monthIndex(day) - (MONTHS_SHOWN - 1))
}

function inWindow(day: string): boolean {
  const month = monthIndex(day)
  return month >= firstMonth.value && month < firstMonth.value + MONTHS_SHOWN
}

function isPickable(day: string): boolean {
  return day >= earliest.value && day <= latest.value
}

/** Today and the days up to the picked one. */
function isOnPath(day: string): boolean {
  return pending.value !== null && day >= props.today && day < pending.value
}

const weekdays = computed(() => calendarWeekdays(locale.value))

const calendarFormatters = computed(() => ({
  day: createDateFormatter({ weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }, locale.value),
  month: createDateFormatter({ month: 'long', year: 'numeric' }, locale.value),
}))

const months = computed(() =>
  Array.from({ length: MONTHS_SHOWN }, (_, offset) => {
    const formatter = calendarFormatters.value
    const first = monthStart(firstMonth.value + offset)
    return {
      key: `${first.getFullYear()}-${first.getMonth() + 1}`,
      title: formatter.month(first),
      weeks: calendarWeeks(first, (date, number) => ({ day: isoDay(date), number, label: formatter.day(date) })),
    }
  }),
)

const pendingDays = computed(() => (pending.value ? daysBetween(props.today, pending.value) : 0))

function pick(day: string): void {
  pending.value = day
  focusDay.value = day
}

/** The arrows are disabled at either end (`canGoBack`, `canGoForward`), so a step stays within the dates. */
function moveWindow(step: -1 | 1): void {
  firstMonth.value += step
  // Keep a visible date in the tab order; only move focus onto it if the clicked arrow button is now disabled.
  if (!inWindow(focusDay.value)) {
    const start = isoDay(monthStart(firstMonth.value))
    focusDay.value = start < earliest.value ? earliest.value : start
  }
  if ((step === -1 && !canGoBack.value) || (step === 1 && !canGoForward.value)) void focusDate()
}

/** The same day of the month `months` away, or that month's last day. */
function shiftMonth(day: string, months: number): string {
  const date = parseDate(day)!
  const target = new Date(date.getFullYear(), date.getMonth() + months + 1, 0)
  target.setDate(Math.min(date.getDate(), target.getDate()))
  return isoDay(target)
}

const KEY_STEPS: Readonly<Record<string, (day: string) => string>> = {
  ArrowLeft: (day) => shiftDay(day, -1),
  ArrowRight: (day) => shiftDay(day, 1),
  ArrowUp: (day) => shiftDay(day, -7),
  ArrowDown: (day) => shiftDay(day, 7),
  PageUp: (day) => shiftMonth(day, -1),
  PageDown: (day) => shiftMonth(day, 1),
}

async function focusDate(): Promise<void> {
  await nextTick()
  calendar.value?.querySelector<HTMLElement>('.plan-calendar-day[tabindex="0"]')?.focus()
}

function onKeydown(event: KeyboardEvent): void {
  if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return
  const step = KEY_STEPS[event.key]
  const from = (event.target as HTMLElement | null)?.dataset.day
  if (!step || !from) return
  event.preventDefault()
  const next = step(from)
  const day = next < earliest.value ? earliest.value : next > latest.value ? latest.value : next
  focusDay.value = day
  if (!inWindow(day)) {
    const targetMonth = monthIndex(day)
    firstMonth.value = targetMonth < firstMonth.value ? Math.max(todayMonth.value, targetMonth) : windowFor(day)
  }
  void focusDate()
}
</script>
