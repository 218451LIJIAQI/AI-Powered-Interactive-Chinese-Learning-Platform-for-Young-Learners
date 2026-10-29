<template>
  <div class="ai-check-score" data-testid="ai-check-score">
    <div class="ai-check-hero">
      <div v-if="tiles.length" class="ai-check-target" aria-hidden="true">
        <span v-for="(char, index) in tiles" :key="index" class="hanzi-tile" lang="zh-CN">{{ char }}</span>
      </div>
      <div class="ai-check-total">
        <p class="ai-check-number">
          <strong>{{ check.score }}</strong>
          <span>/ 100</span>
        </p>
        <p class="ai-check-headline">{{ check.headline }}</p>
        <p class="ai-check-meta">{{ meta }}</p>
      </div>
    </div>

    <dl v-if="check.type === 'RECORDING'" class="ai-subscores">
      <div v-for="part in parts" :key="part.key">
        <dt>{{ t(part.labelKey) }}</dt>
        <dd>
          <span class="ai-subscore-figure">{{ part.value === null ? '–' : part.value }}</span>
          <ProgressBar v-if="part.value !== null" class="is-sm" :progress="part.value" :label="t(part.labelKey)" />
        </dd>
      </div>
    </dl>

    <section v-if="check.feedback" class="ai-check-feedback">
      <h4 class="ai-recap-label">
        {{ check.type === 'HANDWRITING' ? t('aiHelper.strokeFeedback') : t('aiHelper.sparkbotComment') }}
      </h4>
      <p>{{ check.feedback }}</p>
    </section>

    <section v-if="check.tips.length" class="ai-check-feedback">
      <h4 class="ai-recap-label">{{ t('aiHelper.whatToWorkOn') }}</h4>
      <ul class="ai-tips">
        <li v-for="tip in check.tips" :key="tip">{{ tip }}</li>
      </ul>
    </section>

    <p class="ai-footnote"><IconGlyph name="info" :size="14" />{{ t('aiHelper.practiceScoreNote') }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import type { PracticeCheck } from '@/types/ai'

/**
 * A practice score: the characters practised in 田字格 tiles beside the total and headline, Tones / Clarity / Pace as
 * quiet meters for a recording, then the feedback and what to work on.
 */
const props = defineProps<{ check: PracticeCheck; meta: string }>()

/** Short all-Chinese targets are shown as tiles; longer or mixed ones are already in the heading. */
const MAX_TILES = 6
const HAN = /^\p{Script=Han}+$/u

const { t } = useLocalMessages({ aiHelper: aiHelperMessages })
const tiles = computed(() => {
  const target = props.check.targetText.replace(/[\s，。！？、,.!?]/g, '')
  const chars = [...target]
  return HAN.test(target) && chars.length <= MAX_TILES ? chars : []
})
const parts = computed(() => [
  { key: 'tones', labelKey: 'aiHelper.tones', value: props.check.tonesScore ?? null },
  { key: 'clarity', labelKey: 'aiHelper.clarity', value: props.check.clarityScore ?? null },
  { key: 'pace', labelKey: 'aiHelper.pace', value: props.check.paceScore ?? null },
])
</script>
