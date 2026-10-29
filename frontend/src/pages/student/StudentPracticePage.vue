<template>
  <div class="page-stack">
    <PracticeHub v-if="view === 'hub'" />
    <PracticeMistakesPanel v-else-if="view === 'mistakes'" />
    <PracticeTypeView v-else :key="view" :type="view" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import { practiceTypeOfSlug } from '@/api/learn'
import PracticeHub from '@/components/learn/practice/PracticeHub.vue'
import PracticeMistakesPanel from '@/components/learn/practice/PracticeMistakesPanel.vue'
import PracticeTypeView from '@/components/learn/practice/PracticeTypeView.vue'
import type { PracticeType } from '@/types/learn'
import '@/styles/learn.css'
import '@/styles/learn-practice.css'

/**
 * Practice: `/student/practice` is the room (mistakes to fix, then the seven activities), `/student/practice/<type>`
 * runs that type's sets, and `/student/practice/mistakes` lists the waiting mistakes.
 */
const route = useRoute()

const view = computed<'hub' | 'mistakes' | PracticeType>(() => {
  const slug = typeof route.params.type === 'string' ? route.params.type : ''
  if (!slug) return 'hub'
  if (slug === 'mistakes') return 'mistakes'
  return practiceTypeOfSlug(slug) ?? 'hub'
})
</script>
