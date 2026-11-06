<template>
  <div class="page-stack practice-page">
    <PageHeaderBlock :title="t('practice.pageTitle')" hide-title />
    <RouteTabs :tabs="tabs" :label="t('practice.tabsLabel')" />
    <!-- Holds its line while today's numbers load, so the stage does not jump. -->
    <div class="practice-goal-line">
      <DailyGoalMeter
        v-if="today.data.value"
        strip
        :today-xp="today.data.value.todayXp"
        :goal-xp="today.data.value.dailyGoalXp"
        :reached="today.data.value.goalReached"
        :streak="today.data.value.currentStreak"
      />
    </div>
    <PracticeMistakesPanel v-if="view === 'mistakes'" />
    <PracticeTypeView v-else :key="view" :type="view" />
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { xpApi } from '@/api/learn'
import RouteTabs, { type RouteTab } from '@/components/common/RouteTabs.vue'
import DailyGoalMeter from '@/components/learn/DailyGoalMeter.vue'
import PracticeMistakesPanel from '@/components/learn/practice/PracticeMistakesPanel.vue'
import PracticeTypeView from '@/components/learn/practice/PracticeTypeView.vue'
import { PRACTICE_TYPES } from '@/components/learn/practice/practiceTypes'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useProgressVersion } from '@/composables/useProgressRefresh'
import { useResource } from '@/composables/useResource'
import { practiceMessages } from '@/plugins/practiceMessages'
import type { PracticeType } from '@/types/learn'
import '@/styles/learn.css'
import '@/styles/learn-practice.css'

/**
 * Practice: the seven tabs (six activities and Mistakes), today's goal, and one stage for the open tab. There is no
 * hub: `/student/practice` opens the first tab.
 */
const FIRST_TAB = PRACTICE_TYPES[0]!

const route = useRoute()
const router = useRouter()
const { t } = useLocalMessages({ practice: practiceMessages })

const today = useResource(() => xpApi.today())

const slug = computed(() => (typeof route.params.type === 'string' ? route.params.type : ''))
const activity = computed(() => PRACTICE_TYPES.find((info) => info.slug === slug.value) ?? null)

const view = computed<'mistakes' | PracticeType>(() =>
  slug.value === 'mistakes' ? 'mistakes' : (activity.value ?? FIRST_TAB).type,
)

const tabs = computed<RouteTab[]>(() => [
  ...PRACTICE_TYPES.map((info) => ({
    label: t(info.labelKey),
    to: { name: 'student-practice', params: { type: info.slug } },
    icon: info.icon,
    testId: `type-${info.slug}`,
  })),
  {
    label: t('practice.mistakesTitle'),
    to: { name: 'student-practice', params: { type: 'mistakes' } },
    icon: 'retry',
    count: today.data.value?.mistakesWaiting,
    testId: 'type-mistakes',
  },
])

// A link without a tab of its own (the sidebar, Home) lands on the first tab; the stage is already showing it.
watch(
  () => [route.name, view.value, activity.value] as const,
  ([name, opened, known]) => {
    if (name === 'student-practice' && opened !== 'mistakes' && !known) {
      void router.replace({ name: 'student-practice', params: { type: FIRST_TAB.slug }, query: route.query })
    }
  },
  { immediate: true },
)

// Practice here (a finished set) changes today's goal, the streak and the mistakes waiting.
watch(useProgressVersion(), () => void today.reload())
</script>
