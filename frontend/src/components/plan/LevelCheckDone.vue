<template>
  <section class="plan-check-done" aria-live="polite" data-testid="level-check-done">
    <StampSeal character="棒" :caption="t('plan.check.stampCaption')" />
    <h2 class="plan-setup-title">{{ t('plan.check.doneTitle') }}</h2>
    <p class="plan-setup-lead">{{ t('plan.check.doneLead', { count: answered }, answered) }}</p>
    <slot />
    <dl class="plan-skill-rows">
      <div>
        <dt><IconGlyph name="check" :size="16" />{{ t('plan.check.alreadyKnow') }}</dt>
        <dd>
          <ul v-if="knownSkills.length" class="plan-chips">
            <li v-for="skill in knownSkills" :key="skill" class="plan-chip is-known">{{ t(SKILL_KEYS[skill]) }}</li>
          </ul>
          <span v-else class="plan-chip-empty">{{ t('plan.check.nothingYet') }}</span>
        </dd>
      </div>
      <div v-if="newSkills.length">
        <dt><IconGlyph name="chart" :size="16" />{{ t('plan.check.newForYou') }}</dt>
        <dd>
          <ul class="plan-chips">
            <li v-for="skill in newSkills" :key="skill" class="plan-chip">{{ t(SKILL_KEYS[skill]) }}</li>
          </ul>
        </dd>
      </div>
    </dl>
  </section>
</template>

<script setup lang="ts">
import IconGlyph from '@/components/common/IconGlyph.vue'
import StampSeal from '@/components/learn/StampSeal.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { planMessages } from '@/plugins/planMessages'
import type { LevelCheckSkill } from '@/types/learn'

import { SKILL_KEYS } from './planLabels'

/**
 * The end of a level check: the 棒 seal, how many questions it took, and side by side what the student already
 * knows and what is new for them.
 */
defineProps<{
  answered: number
  knownSkills: readonly LevelCheckSkill[]
  newSkills: readonly LevelCheckSkill[]
}>()

const { t } = useLocalMessages({ plan: planMessages })
</script>
