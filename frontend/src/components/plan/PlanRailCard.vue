<template>
  <section v-if="overview" class="learn-rail-card" data-testid="plan-rail">
    <template v-if="overview.plan">
      <h2 class="learn-rail-overline">{{ t('plan.cards.yourPlan') }}</h2>
      <p class="learn-rail-title">{{ goalTitle(t, overview.plan.goalTitle, overview.plan.reason) }}</p>
      <p v-if="overview.plan.goalUnit" class="learn-rail-text">
        {{
          t('plan.cards.goalAt', {
            number: overview.plan.goalUnit.number,
            title: `${overview.plan.goalUnit.title} · ${overview.plan.goalUnit.titleZh}`,
          })
        }}
      </p>
      <RouterLink class="btn btn-link" :to="{ name: 'student-plan' }">
        {{ t('plan.cards.viewPlan') }}<IconGlyph name="chevronRight" :size="14" />
      </RouterLink>
    </template>
    <template v-else>
      <h2 class="learn-rail-overline">{{ t('plan.cards.yourPlan') }}</h2>
      <p class="learn-rail-text">{{ t('plan.cards.buildText') }}</p>
      <RouterLink class="btn btn-secondary btn-sm" :to="{ name: 'student-setup' }">{{
        t('plan.cards.buildPlan')
      }}</RouterLink>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import { planApi } from '@/api/plan'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { planMessages } from '@/plugins/planMessages'

import { goalTitle } from './planFormat'

/**
 * The Learn rail's "Your plan" card: the goal and the unit it is reached at. It stays out of the way while it loads
 * or when the plan cannot be read (the rail's other cards still work).
 */
const { t } = useLocalMessages({ plan: planMessages })
const resource = useResource(() => planApi.overview())

const overview = computed(() => resource.data.value)
</script>
