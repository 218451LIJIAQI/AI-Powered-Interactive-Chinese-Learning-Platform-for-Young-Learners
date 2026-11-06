<template>
  <div v-if="speechAvailable" class="learn-listen" :class="{ 'is-large': large, 'is-prompt': prompt }">
    <button
      class="btn learn-listen-play"
      :class="large || prompt ? 'btn-primary' : 'btn-secondary btn-sm'"
      type="button"
      :aria-label="t('learn.listenTo', { text: label || text })"
      data-testid="listen"
      @click="speakChinese(text)"
    >
      <IconGlyph name="audio" :size="large ? 30 : prompt ? 22 : 16" />
      <span v-if="!large && !prompt">{{ t('learn.listen') }}</span>
    </button>
    <button
      v-if="slow"
      class="btn btn-secondary learn-listen-slow"
      :class="{ 'btn-sm': !large }"
      type="button"
      :aria-label="t('learn.listenSlowlyTo', { text: label || text })"
      data-testid="listen-slow"
      @click="speakChinese(text, true)"
    >
      {{ t('learn.slow') }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { learnMessages } from '@/plugins/learnMessages'
import { canSpeak, speakChinese, stopSpeaking } from '@/utils/speech'

/**
 * Listen (and optionally Slow) for a Chinese word or sentence; hidden where the browser cannot speak. `prompt` is the 56
 * speaker beside a question's word, `large` the 84 speaker of a listening question with Slow beside it, and the default a
 * small Listen for rows.
 */
interface Props {
  text: string
  /** What the accessible name says is played; the text itself by default. */
  label?: string
  slow?: boolean
  /** The big speaker of a listening question. */
  large?: boolean
  /** The speaker beside the word of a question. */
  prompt?: boolean
}

withDefaults(defineProps<Props>(), { label: '', slow: false, large: false, prompt: false })

const { t } = useLocalMessages({ learn: learnMessages })
const speechAvailable = canSpeak()

onBeforeUnmount(stopSpeaking)
</script>
