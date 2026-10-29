<template>
  <span class="session-date-tile" :class="{ 'is-muted': muted }" aria-hidden="true">
    <span class="session-date-tile-weekday">{{ tile.weekday }}</span>
    <strong class="session-date-tile-day">{{ tile.day }}</strong>
    <span v-if="showMonth" class="session-date-tile-month">{{ tile.month }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import { dateTile } from '@/utils/classes'

/** The "MON 18" calendar tile of session lists; the row next to it carries the full date for screen readers. */
const props = withDefaults(defineProps<{ at: string; muted?: boolean; showMonth?: boolean }>(), {
  muted: false,
  showMonth: false,
})

const { locale } = useI18n()
const tile = computed(() => dateTile(props.at, locale.value))
</script>
