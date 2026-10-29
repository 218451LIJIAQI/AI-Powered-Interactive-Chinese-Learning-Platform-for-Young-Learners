<template>
  <header class="practice-head">
    <RouterLink class="btn btn-link practice-head-back" :to="{ name: 'student-practice' }">
      <IconGlyph name="chevronLeft" :size="16" />{{ t('practice.allActivities') }}
    </RouterLink>
    <div class="practice-head-title">
      <slot name="mark" />
      <div class="practice-head-copy">
        <h1>{{ title }}</h1>
        <p v-if="lede">{{ lede }}</p>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { practiceMessages } from '@/plugins/practiceMessages'

/**
 * The header every room of Practice shares (a type's sets, Mistakes): back to all activities, the room's mark (its
 * glyph tile or seal), the page's one h1 and a one-line lede.
 */
defineProps<{ title: string; lede?: string }>()

const { t } = useLocalMessages({ practice: practiceMessages })
</script>
