<template>
  <section class="plan-center" aria-live="polite" data-testid="level-check-done">
    <h2 class="focus-headline">{{ t('plan.check.doneTitle') }}</h2>
    <slot />
    <dl class="plan-skill-rows">
      <div>
        <dt>{{ t('plan.check.alreadyKnow') }}</dt>
        <dd>
          <ul v-if="knownSkills.length" class="plan-skill-chips">
            <li v-for="skill in knownSkills" :key="skill" class="plan-skill-chip is-known">
              <IconGlyph name="check" :size="14" />{{ t(SKILL_KEYS[skill]) }}
            </li>
          </ul>
          <span v-else class="plan-skill-none">{{ t('plan.check.nothingYet') }}</span>
        </dd>
      </div>
      <div v-if="newSkills.length">
        <dt>{{ t('plan.check.newForYou') }}</dt>
        <dd>
          <ul class="plan-skill-chips">
            <li v-for="skill in newSkills" :key="skill" class="plan-skill-chip">
              <IconGlyph name="progress" :size="14" />{{ t(SKILL_KEYS[skill]) }}
            </li>
          </ul>
        </dd>
      </div>
    </dl>
  </section>
</template>

<script setup lang="ts">
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { planMessages } from '@/plugins/planMessages'
import type { LevelCheckSkill } from '@/types/learn'

import { SKILL_KEYS } from './planLabels'

/** The end of a level check: what the student already knows and what is new for them, as knowledge points. */
defineProps<{
  knownSkills: readonly LevelCheckSkill[]
  newSkills: readonly LevelCheckSkill[]
}>()

const { t } = useLocalMessages({ plan: planMessages })
</script>
