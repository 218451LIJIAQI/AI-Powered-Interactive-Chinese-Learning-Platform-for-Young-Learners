<template>
  <div class="ai-check-score" data-testid="ai-check-score">
    <div v-if="meta" class="ai-check-total">
      <p class="ai-check-number">
        <strong>{{ check.score }}</strong>
        <span>/ 100</span>
      </p>
      <div class="ai-check-copy">
        <p class="ai-check-headline">{{ check.headline }}</p>
        <p class="ai-check-meta">{{ meta }}</p>
      </div>
    </div>
    <p v-else class="ai-check-headline">{{ check.headline }}</p>

    <dl v-if="check.type === 'RECORDING'" class="ai-subscores">
      <div v-for="part in parts" :key="part.key">
        <dt>{{ t(part.labelKey) }}</dt>
        <dd>{{ part.value === null ? '–' : t('aiHelper.outOf100', { score: part.value }) }}</dd>
      </div>
    </dl>

    <section v-if="check.feedback" class="ai-check-section">
      <h3 class="field-label">
        {{ check.type === 'HANDWRITING' ? t('aiHelper.strokeFeedback') : t('aiHelper.sparkbotComment') }}
      </h3>
      <p>{{ check.feedback }}</p>
    </section>

    <section v-if="check.tips.length" class="ai-check-section">
      <h3 class="field-label">{{ t('aiHelper.whatToWorkOn') }}</h3>
      <ul class="ai-tips">
        <li v-for="tip in check.tips" :key="tip">{{ tip }}</li>
      </ul>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { useLocalMessages } from '@/composables/useLocalMessages'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import type { PracticeCheck } from '@/types/ai'

/**
 * A practice score: the total and headline (the drawer of a recent check shows the total in its own list, so it leaves
 * `meta` out), Tones / Clarity / Pace for a recording, then the feedback and what to work on.
 */
const props = defineProps<{ check: PracticeCheck; meta?: string }>()

const { t } = useLocalMessages({ aiHelper: aiHelperMessages })
const parts = computed(() => [
  { key: 'tones', labelKey: 'aiHelper.tones', value: props.check.tonesScore ?? null },
  { key: 'clarity', labelKey: 'aiHelper.clarity', value: props.check.clarityScore ?? null },
  { key: 'pace', labelKey: 'aiHelper.pace', value: props.check.paceScore ?? null },
])
</script>
