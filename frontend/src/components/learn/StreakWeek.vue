<template>
  <ol class="learn-week" :aria-label="t('learn.lastSevenDays')">
    <li
      v-for="day in days"
      :key="day.date"
      class="learn-week-day"
      :class="{ 'is-today': day.isToday }"
      :aria-current="day.isToday ? 'date' : undefined"
      :title="day.label"
    >
      <span
        class="seal learn-week-seal"
        :class="{ 'is-outline': !day.goalReached && day.isToday, 'is-muted': !day.goalReached && !day.isToday }"
        lang="zh-CN"
        aria-hidden="true"
        >{{ day.hanzi }}</span
      >
      <!-- The weekday's initial, unless the seal already reads it (in Chinese). -->
      <span v-if="day.initial !== day.hanzi" class="learn-week-letter" aria-hidden="true">{{ day.initial }}</span>
      <span class="sr-only">{{ day.label }}</span>
    </li>
  </ol>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { hanziWeekday } from '@/components/learn/sealGlyphs'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { learnMessages } from '@/plugins/learnMessages'
import { formatDate } from '@/utils/format'

/**
 * The last seven days as seals, today last (the streak on Home and in Practice's today rail): each seal carries the
 * weekday in Chinese with its initial below; a day whose goal was reached is stamped, a missed day stays a quiet
 * outline, and today is an open seal until its goal is reached. Each day is named for screen readers.
 */
interface Props {
  week: readonly { readonly date: string; readonly goalReached: boolean }[]
  today: string
}

const props = defineProps<Props>()

const { t, locale } = useLocalMessages({ learn: learnMessages })

const days = computed(() =>
  props.week.map((day) => {
    const weekday = formatDate(day.date, { weekday: 'long' }, locale.value)
    const initial = formatDate(day.date, { weekday: 'narrow' }, locale.value)
    return {
      ...day,
      initial,
      hanzi: hanziWeekday(day.date),
      isToday: day.date === props.today,
      label: t(day.goalReached ? 'learn.dayReached' : 'learn.dayMissed', { day: weekday }),
    }
  }),
)
</script>

<style scoped>
/* Scoped: Home and Practice load different stylesheets. Seven 26px seals fit the 224px today rail. */
.learn-week {
  display: flex;
  gap: var(--space-1-5);
  margin: 0;
  padding: 0;
  list-style: none;
}

.learn-week-day {
  display: grid;
  justify-items: center;
  gap: var(--space-1);
}

.learn-week-seal {
  --seal-size: 26px;
}

/* Today's initial is in ink. */
.learn-week-letter {
  color: var(--text-secondary);
  font-size: var(--font-size-2xs);
  font-weight: var(--font-weight-bold);
  letter-spacing: var(--letter-spacing-wide);
}

.learn-week-day.is-today .learn-week-letter {
  color: var(--text-primary);
}
</style>
