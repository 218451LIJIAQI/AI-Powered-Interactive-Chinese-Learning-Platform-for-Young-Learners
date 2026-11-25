<template>
  <section
    ref="rootEl"
    class="panel ai-check-result"
    :aria-labelledby="`${baseId}-result`"
    data-testid="ai-check-result"
  >
    <header class="section-header">
      <h2 :id="`${baseId}-result`" class="card-title">
        {{ t('aiHelper.yourScore') }} ·
        {{ result.audioFile ? t('aiHelper.audioFile') : t(practiceCheckKindKey(result.check.type)) }} ·
        <span lang="zh-CN">{{ result.check.targetText }}</span>
      </h2>
      <AiResultLabel v-if="!result.check.pronunciation" />
    </header>
    <AiCheckScore
      :check="result.check"
      :meta="formatPracticeCheckMeta(result.check, t('aiHelper.checkedJustNow'), result.fileName)"
      :photo="result.photo"
    />
    <p class="callout">{{ t('aiHelper.practiceScoreNote', { notice: aiNotice }) }}</p>
    <div class="form-actions">
      <button v-if="!reported" class="btn btn-secondary" type="button" @click="emit('report', result.check)">
        <IconGlyph name="flag" :size="16" />
        {{ t('aiHelper.reportScore') }}
      </button>
      <span v-else class="ai-reported"><IconGlyph name="status" :size="14" />{{ t('aiSafety.reported') }}</span>
      <button class="btn btn-primary" type="button" @click="emit('tryAgain')">{{ t('aiHelper.tryAgain') }}</button>
    </div>
    <AiNotice :text="aiNotice" />
  </section>
</template>

<script setup lang="ts">
import { ref, useId } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { aiVoiceMessages } from '@/plugins/aiVoiceMessages'
import type { PracticeCheck } from '@/types/ai'

import AiCheckScore from './AiCheckScore.vue'
import AiNotice from './AiNotice.vue'
import AiResultLabel from './AiResultLabel.vue'
import { formatPracticeCheckMeta, practiceCheckKindKey, revealResult } from './aiHelper'

export interface PracticeCheckView {
  check: PracticeCheck
  photo: string | null
  fileName: string | null
  audioFile: boolean
}

defineProps<{
  result: PracticeCheckView
  reported: boolean
  aiNotice: string
}>()
const emit = defineEmits<{
  report: [check: PracticeCheck]
  tryAgain: []
}>()

const { t } = useLocalMessages({ aiHelper: aiVoiceMessages })
const baseId = useId()
const rootEl = ref<HTMLElement | null>(null)

function reveal(): void {
  revealResult(rootEl.value)
}

defineExpose({ reveal })
</script>
