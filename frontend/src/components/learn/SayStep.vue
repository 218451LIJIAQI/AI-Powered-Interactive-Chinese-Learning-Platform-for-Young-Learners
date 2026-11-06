<template>
  <section class="learn-question" :aria-labelledby="`${baseId}-title`" data-testid="say-step">
    <h2 :id="`${baseId}-title`" class="focus-title">{{ t('learn.instructionSay') }}</h2>

    <div class="learn-question-prompt">
      <ListenButtons :text="hanzi" prompt />
      <div class="learn-question-word">
        <p class="hanzi learn-question-hanzi" lang="zh-CN">{{ hanzi }}</p>
        <p v-if="caption" class="learn-question-pinyin">{{ caption }}</p>
      </div>
    </div>

    <div class="learn-say-record">
      <template v-if="recorder.supported">
        <button
          v-if="recorder.isRecording.value"
          class="btn btn-secondary"
          type="button"
          data-testid="say-stop"
          @click="recorder.stop"
        >
          <IconGlyph name="stop" :size="16" />
          {{ t('learn.stopRecording', { time: formatClock(recorder.elapsedSeconds.value) }) }}
        </button>
        <button
          v-else
          class="btn btn-secondary"
          type="button"
          :disabled="recorder.isBusy.value"
          :aria-busy="recorder.isBusy.value"
          data-testid="say-record"
          @click="recorder.start"
        >
          <IconGlyph name="mic" :size="16" />
          {{ recorder.previewUrl.value ? t('learn.recordAgain') : t('learn.startRecording') }}
        </button>
        <audio
          v-if="recorder.previewUrl.value"
          class="learn-say-audio"
          controls
          :src="recorder.previewUrl.value"
          :aria-label="t('learn.yourRecording')"
        />
      </template>
      <p class="learn-say-note" :role="problem ? 'alert' : undefined">{{ problem || t('learn.sayNote') }}</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, useId } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useAudioRecorder } from '@/composables/useAudioRecorder'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { learnMessages } from '@/plugins/learnMessages'
import { formatClock } from '@/utils/format'

import ListenButtons from './ListenButtons.vue'

/**
 * The last step of a lesson: hear a phrase from the lesson, say it, and play the recording back to compare it with the
 * model. It is not graded and nothing leaves the browser; the footer's Skip and Done both move on.
 */
const props = defineProps<{
  hanzi: string
  pinyin: string | null
  meaning: string | null
}>()

const { t } = useLocalMessages({ learn: learnMessages })
const baseId = useId()
const recorder = useAudioRecorder()

const caption = computed(() => [props.pinyin, props.meaning].filter(Boolean).join(' · '))

/** Why recording is not possible right now; saying it aloud still works, so the step never blocks. */
const problem = computed(() => {
  if (!recorder.supported) return t('learn.sayUnavailable')
  if (recorder.status.value === 'denied') return t('learn.sayDenied')
  if (recorder.status.value === 'error') return t('learn.sayFailed')
  return ''
})
</script>
