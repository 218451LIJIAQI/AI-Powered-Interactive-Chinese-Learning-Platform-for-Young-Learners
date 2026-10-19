<template>
  <article class="admin-kpi">
    <span class="admin-icon-badge" :class="`admin-tone-${tone}`" aria-hidden="true">
      <IconGlyph :name="icon" :size="20" />
    </span>
    <div class="admin-kpi-copy">
      <span class="admin-kpi-label">{{ label }}</span>
      <strong class="admin-kpi-value">{{ value }}</strong>
      <span v-if="hint" class="admin-kpi-hint">{{ hint }}</span>
    </div>
  </article>
</template>

<script setup lang="ts">
import IconGlyph from '@/components/common/IconGlyph.vue'
import type { AdminTone } from './adminTone'

/** One KPI tile of an admin page's KPI row (`.admin-kpi-grid`): tinted icon, label, big value and a short hint. */
interface Props {
  label: string
  value: string | number
  hint?: string
  icon: string
  tone?: AdminTone
}

withDefaults(defineProps<Props>(), {
  hint: '',
  tone: 'primary',
})
</script>
