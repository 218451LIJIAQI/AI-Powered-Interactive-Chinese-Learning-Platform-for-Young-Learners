<template>
  <figure v-if="validComparisons.length" class="ai-tone-diagram" :aria-labelledby="titleId">
    <div class="ai-tone-diagram-header">
      <h3 :id="titleId" class="field-label">{{ t('aiHelper.toneDiagramTitle') }}</h3>
      <ul class="ai-tone-legend" role="list">
        <li><span class="is-target" aria-hidden="true"></span>{{ t('aiHelper.toneDiagramTarget') }}</li>
        <li><span aria-hidden="true"></span>{{ t('aiHelper.toneDiagramDetected') }}</li>
      </ul>
    </div>

    <ol
      class="ai-tone-strip"
      role="list"
      :tabindex="validComparisons.length > 1 ? 0 : undefined"
      :aria-labelledby="titleId"
    >
      <li
        v-for="({ text, expectedTone, detectedTone }, index) in validComparisons"
        :key="index"
        class="ai-tone-card"
        :class="{ 'is-different': expectedTone !== detectedTone }"
      >
        <strong class="ai-tone-character" lang="zh-CN">{{ text }}</strong>
        <svg
          class="ai-tone-curves"
          viewBox="0 0 128 48"
          preserveAspectRatio="none"
          aria-hidden="true"
          focusable="false"
        >
          <path class="is-target" :d="CONTOURS[expectedTone]" vector-effect="non-scaling-stroke" />
          <path :d="CONTOURS[detectedTone]" vector-effect="non-scaling-stroke" />
        </svg>
        <span class="ai-tone-label" aria-hidden="true"
          >{{ toneName(expectedTone) }} → {{ toneName(detectedTone) }}</span
        >
        <span class="sr-only">{{ toneLabels(expectedTone, detectedTone) }}</span>
      </li>
    </ol>

    <figcaption class="field-hint">{{ t('aiHelper.toneDiagramNote') }}</figcaption>
  </figure>
</template>

<script setup lang="ts">
import { computed, useId } from 'vue'

import { useLocalMessages } from '@/composables/useLocalMessages'
import { aiVoiceMessages } from '@/plugins/aiVoiceMessages'
import { isMandarinTone, type MandarinTone, type ToneComparison } from '@/types/ai'

const props = defineProps<{ comparisons: ToneComparison[] }>()

const { t } = useLocalMessages({ aiHelper: aiVoiceMessages })
const titleId = useId()
const validComparisons = computed(() =>
  props.comparisons.filter((item) => isMandarinTone(item.expectedTone) && isMandarinTone(item.detectedTone)),
)

// Reference shapes for the four tone categories. Tencent Cloud provides the categories rather than pitch samples.
const CONTOURS: Record<MandarinTone, string> = {
  1: 'M 8,9 H 120',
  2: 'M 8,31 Q 68,26 120,9',
  3: 'M 8,25 Q 47,61 79,35 L 120,16',
  4: 'M 8,9 Q 61,18 120,41',
}

function toneName(tone: MandarinTone): string {
  return t(`aiHelper.pronunciationTone${tone}`)
}

function toneLabels(expected: MandarinTone, detected: MandarinTone): string {
  return t('aiHelper.toneDiagramLabels', { target: toneName(expected), detected: toneName(detected) })
}
</script>
