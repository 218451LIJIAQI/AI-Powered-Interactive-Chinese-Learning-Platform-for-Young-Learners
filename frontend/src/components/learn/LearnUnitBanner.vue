<template>
  <div class="learn-banner" :class="`is-${here ? 'here' : unit.state.toLowerCase()}`">
    <span class="learn-banner-tile" aria-hidden="true">
      <IconGlyph :name="tileIcon" :size="22" />
    </span>
    <div class="learn-banner-copy">
      <p class="learn-banner-overline">{{ overline }}</p>
      <component :is="here ? 'p' : 'h3'" :id="titleId" class="learn-banner-title">
        {{ unit.title }} · <span class="hanzi" lang="zh-CN">{{ unit.titleZh }}</span>
      </component>
    </div>
    <p v-if="unit.state === 'LOCKED'" class="learn-banner-note">
      {{
        unit.unlockAfterUnit !== null
          ? t('learn.finishUnitToUnlock', { number: unit.unlockAfterUnit })
          : t('learn.stateLocked')
      }}
    </p>
    <button
      v-else-if="showGuidebook"
      class="btn btn-secondary btn-sm"
      type="button"
      :data-testid="`guidebook-${unit.number}`"
      @click="emit('guidebook', unit)"
    >
      {{ t('learn.guidebook') }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { learnMessages } from '@/plugins/learnMessages'
import type { PathUnit } from '@/types/learn'

/**
 * The banner of a unit: its icon, "Unit N", the title and what can be done from here. `here` is the filled banner pinned
 * above the path (the unit the learner is on); in the path a finished or open unit is a white sheet and a locked one a
 * sunken strip that says what unlocks it.
 */
interface Props {
  unit: PathUnit
  /** The pinned "You are here" banner. Its title is a paragraph: the unit's heading belongs to its section. */
  here?: boolean
  titleId?: string
  showGuidebook?: boolean
}

const props = withDefaults(defineProps<Props>(), { titleId: undefined, showGuidebook: true })

const emit = defineEmits<{
  guidebook: [unit: PathUnit]
}>()

const { t } = useLocalMessages({ learn: learnMessages })

const tileIcon = computed(() => {
  if (props.here || props.unit.state === 'ACTIVE') return props.unit.iconKey
  return props.unit.state === 'LOCKED' ? 'lock' : 'check'
})

/** "You are here · Unit 1", "Unit 1 · Done" or "Unit 2" (a locked one says what unlocks it at its end). */
const overline = computed(() => {
  const number = props.unit.number
  if (props.here) return t('learn.youAreHereUnit', { number })
  const label = t('learn.unitNumber', { number })
  return props.unit.state === 'DONE' ? `${label} · ${t('learn.unitDone')}` : label
})
</script>
