<template>
  <div class="ai-check-score">
    <div v-if="meta" class="ai-check-total">
      <p class="ai-check-number">
        <strong>{{ check.score }}</strong>
        <span>/ 100</span>
      </p>
      <div class="ai-check-copy">
        <p class="ai-check-headline">{{ check.pronunciation ? t('aiHelper.pronunciationResult') : check.headline }}</p>
        <p class="ai-check-meta">{{ meta }}</p>
      </div>
    </div>
    <p v-else-if="!check.pronunciation" class="ai-check-headline">{{ check.headline }}</p>

    <img v-if="photo" class="ai-check-photo" :src="photo" :alt="t('aiHelper.photoPreview')" />

    <template v-if="check.type === 'RECORDING'">
      <p class="field-hint">
        {{ t(check.pronunciation ? 'aiHelper.pronunciationScoreBasis' : 'aiHelper.recordingScoreBasis') }}
      </p>
      <dl class="ai-subscores">
        <div v-for="part in parts" :key="part.labelKey">
          <dt>{{ t(part.labelKey) }}</dt>
          <dd>{{ part.value === null ? t('aiHelper.notMeasured') : t('aiHelper.outOf100', { score: part.value }) }}</dd>
        </div>
      </dl>
      <AiToneDiagram v-if="toneComparisons.length" :comparisons="toneComparisons" />
      <div v-if="check.pronunciation && speech.canSpeak.value" class="btn-group is-sm">
        <button class="btn btn-secondary btn-sm" type="button" @click="playTarget">
          {{ speech.speaking.value ? t('aiHelper.stop') : t('aiHelper.listen') }}
        </button>
      </div>
    </template>

    <section v-if="check.feedback" class="ai-check-section">
      <h3 class="field-label">
        {{ check.type === 'HANDWRITING' ? t('aiHelper.strokeFeedback') : t('aiHelper.sparkbotComment') }}
      </h3>
      <p>{{ check.feedback }}</p>
    </section>

    <section v-if="check.pronunciation?.issues.length || check.tips.length" class="ai-check-section">
      <h3 class="field-label">{{ t('aiHelper.whatToWorkOn') }}</h3>
      <ul class="ai-tips">
        <li v-for="(issue, index) in check.pronunciation?.issues ?? []" :key="`issue-${index}`">
          <strong lang="zh-CN">{{ issue.text }}</strong> · {{ issueAdvice(issue) }}
        </li>
        <li v-for="(tip, index) in check.tips" :key="`tip-${index}-${tip}`">{{ tip }}</li>
      </ul>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { useLocalMessages } from '@/composables/useLocalMessages'
import { useMandarinReadAloud } from '@/composables/useMandarinSpeech'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import { aiVoiceMessages } from '@/plugins/aiVoiceMessages'
import { isMandarinTone, type PracticeCheck, type PronunciationIssue } from '@/types/ai'

import AiToneDiagram from './AiToneDiagram.vue'

/**
 * A practice score: the total and headline (the drawer of a recent check shows the total in its own list, so it leaves
 * `meta` out), the measurements and feedback, and the handwriting photo when the page still has it.
 */
const props = defineProps<{ check: PracticeCheck; meta?: string; photo?: string | null }>()

const { t } = useLocalMessages({ aiHelper: [aiHelperMessages, aiVoiceMessages] })
const speech = useMandarinReadAloud()
const toneComparisons = computed(() => {
  const pronunciation = props.check.pronunciation
  const raw = pronunciation?.toneComparisons?.length
    ? pronunciation.toneComparisons
    : (pronunciation?.issues.filter((issue) => issue.kind === 'TONE_MISMATCH') ?? [])
  return raw.filter((item) => isMandarinTone(item.expectedTone) && isMandarinTone(item.detectedTone))
})
const parts = computed(() => {
  const pronunciation = props.check.pronunciation
  if (pronunciation) {
    return [
      { labelKey: 'aiHelper.pronunciationAccuracy', value: pronunciation.accuracy },
      { labelKey: 'aiHelper.pronunciationFluency', value: pronunciation.fluency },
      { labelKey: 'aiHelper.pronunciationCompleteness', value: pronunciation.completeness },
    ]
  }
  return [
    { labelKey: 'aiHelper.tones', value: null },
    { labelKey: 'aiHelper.clarity', value: props.check.clarityScore },
    { labelKey: 'aiHelper.pace', value: props.check.paceScore },
  ]
})

function playTarget(): void {
  if (speech.speaking.value) speech.stopSpeaking()
  else speech.speak(props.check.targetText, true)
}

function issueAdvice(issue: PronunciationIssue): string {
  switch (issue.kind) {
    case 'OMITTED':
      return t('aiHelper.pronunciationOmitted')
    case 'MISPRONOUNCED':
      return t('aiHelper.pronunciationMisread')
    case 'TONE_MISMATCH':
      if (!isMandarinTone(issue.expectedTone) || !isMandarinTone(issue.detectedTone)) {
        return t('aiHelper.pronunciationMisread')
      }
      return t('aiHelper.pronunciationToneMismatch', {
        expected: t(`aiHelper.pronunciationTone${issue.expectedTone}`),
        heard: t(`aiHelper.pronunciationTone${issue.detectedTone}`),
        tip: t(`aiHelper.pronunciationTip${issue.expectedTone}`),
      })
  }
}
</script>
