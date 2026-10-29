<template>
  <section class="panel plan-card" aria-labelledby="plan-card-title" data-testid="plan-card">
    <StatePanel
      v-if="resource.loading.value"
      tone="loading"
      :title="t('plan.page.loading')"
      :message="t('plan.page.loadingHint')"
    />
    <StatePanel
      v-else-if="resource.error.value"
      tone="error"
      icon="alert"
      :title="t('plan.page.loadError')"
      :message="resource.error.value"
    >
      <template #actions>
        <button class="btn btn-secondary btn-sm" type="button" @click="resource.reload">
          {{ t('plan.common.retry') }}
        </button>
      </template>
    </StatePanel>

    <template v-else-if="summary">
      <div class="plan-card-goal">
        <h2 id="plan-card-title" class="plan-card-overline">{{ t('plan.cards.myPlan') }}</h2>
        <p class="plan-card-title">{{ title }}</p>
        <p class="plan-card-note">
          {{
            summary.deadline
              ? t('plan.cards.byWeek', {
                  date: longDate(summary.deadline),
                  week: summary.week,
                  total: summary.totalWeeks,
                })
              : t('plan.cards.weekOnly', { week: summary.week, total: summary.totalWeeks })
          }}
        </p>
      </div>
      <div class="plan-card-level">
        <p class="plan-card-label">{{ t('plan.page.mandarinLevel') }}</p>
        <LevelLadder :placed="summary.level" :target="summary.targetLevel" />
        <p class="plan-card-note">{{ t('plan.cards.separateFromXp') }}</p>
      </div>
      <div class="plan-card-progress">
        <p class="plan-card-label">{{ t('plan.cards.planProgress', { percent: summary.percent }) }}</p>
        <ProgressBar :progress="summary.percent" :label="t('plan.page.planProgress')" />
        <div class="plan-card-foot">
          <StatusPill
            :label="t(STATUS_KEYS[summary.status])"
            :tone="summary.status === 'BEHIND' ? 'warning' : 'success'"
          />
          <RouterLink class="btn btn-link" :to="{ name: 'student-plan' }">
            {{ t('plan.cards.viewPlan') }}<IconGlyph name="chevronRight" :size="14" />
          </RouterLink>
        </div>
      </div>
    </template>

    <div v-else class="plan-card-build">
      <div class="plan-card-goal">
        <h2 id="plan-card-title" class="plan-card-title">{{ t('plan.cards.buildTitle') }}</h2>
        <p class="plan-card-note">{{ t('plan.cards.buildText') }}</p>
      </div>
      <RouterLink class="btn btn-primary" :to="{ name: 'student-setup' }">{{ t('plan.cards.buildPlan') }}</RouterLink>
    </div>
  </section>
</template>

<script setup lang="ts">
import '@/styles/plan.css'

import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import { planApi } from '@/api/plan'
import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { planMessages } from '@/plugins/planMessages'

import LevelLadder from './LevelLadder.vue'
import { goalTitle, longDate } from './planFormat'
import { STATUS_KEYS } from './planLabels'

/**
 * The Progress page's "My learning plan" sheet: where the student is heading (the goal and its week), the Mandarin
 * level from the level check (separate from XP) and plan progress; or "Build your learning plan".
 */
const { t } = useLocalMessages({ plan: planMessages })
const resource = useResource(() => planApi.overview())

const summary = computed(() => resource.data.value?.plan ?? null)
const title = computed(() => (summary.value ? goalTitle(t, summary.value.goalTitle, summary.value.reason) : ''))
</script>
