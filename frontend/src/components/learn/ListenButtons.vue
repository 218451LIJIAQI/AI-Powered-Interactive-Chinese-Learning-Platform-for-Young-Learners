<template>
  <div v-if="speechAvailable" class="learn-listen" :class="{ 'is-large': large }">
    <button
      class="btn learn-listen-play"
      :class="large ? 'btn-primary' : 'btn-secondary btn-sm'"
      type="button"
      :aria-label="t('learn.listenTo', { text: label || text })"
      data-testid="listen"
      @click="speakChinese(text)"
    >
      <IconGlyph name="audio" :size="large ? 32 : 16" />
      <span v-if="!large">{{ t('learn.listen') }}</span>
    </button>
    <button
      v-if="slow"
      class="btn btn-ghost"
      :class="{ 'btn-sm': !large }"
      type="button"
      :aria-label="t('learn.listenSlowlyTo', { text: label || text })"
      data-testid="listen-slow"
      @click="speakChinese(text, true)"
    >
      <IconGlyph name="slow" :size="16" />
      <span>{{ t('learn.slow') }}</span>
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
 * Listen (and optionally Slow) for a Chinese word or sentence; hidden where the browser cannot speak. The large form
 * is the round speaker of a listening question, with Slow under it.
 */
interface Props {
  text: string
  /** What the accessible name says is played; the text itself by default. */
  label?: string
  slow?: boolean
  /** The big speaker of a listening question. */
  large?: boolean
}

withDefaults(defineProps<Props>(), { label: '', slow: false, large: false })

const { t } = useLocalMessages({ learn: learnMessages })
const speechAvailable = canSpeak()

onBeforeUnmount(stopSpeaking)
</script>
