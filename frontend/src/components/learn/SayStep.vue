<template>
  <section class="learn-question" :aria-labelledby="`${baseId}-title`" data-testid="say-step">
    <h2 :id="`${baseId}-title`" ref="heading" class="focus-title" tabindex="-1">{{ t('learn.instructionSay') }}</h2>

    <div class="learn-question-prompt">
      <ListenButtons :text="hanzi" prompt />
      <div class="learn-question-word">
        <p class="hanzi learn-question-hanzi" lang="zh-CN">{{ hanzi }}</p>
        <p v-if="caption" class="learn-question-pinyin">{{ caption }}</p>
      </div>
    </div>

    <div class="learn-say-record">
      <div v-if="recorder.supported" class="btn-group">
        <button
          v-if="recorder.isRecording.value"
          class="btn btn-secondary"
          type="button"
          data-testid="say-stop"
          @click="recorder.stop"
        >
          <IconGlyph name="stop" :size="16" />
          {{ t('learn.stop') }}
        </button>
        <button
          v-else
          class="btn btn-secondary"
          type="button"
          :disabled="recorder.isBusy.value"
          :aria-busy="recorder.isBusy.value"
          data-testid="say-record"
          @click="requestRecording"
        >
          <IconGlyph name="mic" :size="16" />
          {{ recorder.previewUrl.value ? t('learn.recordAgain') : t('learn.startRecording') }}
        </button>
        <button
          v-if="recorder.previewUrl.value && !recorder.isBusy.value"
          class="btn btn-secondary"
          type="button"
          :aria-pressed="playing"
          data-testid="say-play"
          @click="togglePlayback"
        >
          <IconGlyph :name="playing ? 'stop' : 'audio'" :size="16" />
          {{ playing ? t('learn.stop') : t('learn.playYours') }}
        </button>
      </div>
      <!-- Played by the button above: the page draws no player of its own. -->
      <audio
        v-if="recorder.previewUrl.value"
        ref="player"
        :src="recorder.previewUrl.value"
        preload="auto"
        @play="playing = true"
        @pause="playing = false"
        @ended="playing = false"
      />
      <!-- While recording: keep the ticking clock outside the live region so screen readers are not interrupted every second. -->
      <p v-if="recorder.isRecording.value" class="learn-say-note">
        <span role="status" aria-live="polite">{{ t('learn.sayRecording') }}</span
        >{{ ' ' }}<span aria-hidden="true">{{ formatClock(recorder.elapsedSeconds.value) }}</span
        >{{ ' ' }}<span>{{ t('learn.sayRecordingLine', { phrase: hanzi }) }}</span>
      </p>
      <p v-else-if="problem" class="learn-say-note" role="alert">{{ problem }}</p>
      <p v-else class="learn-say-note" aria-live="polite">{{ status }}</p>
    </div>

    <!-- Our own question first; only Allow lets the browser ask for the microphone. -->
    <AppOverlay
      v-if="mic.asking.value"
      :title="t('learn.micTitle')"
      :subtitle="t('learn.micLead')"
      test-id="mic-ask"
      @close="mic.cancel"
    >
      <p class="learn-mic-note">
        <IconGlyph name="shield" :size="16" />
        {{ t('learn.micNote') }}
      </p>
      <template #footer>
        <button class="btn btn-secondary" type="button" data-testid="mic-not-now" @click="mic.cancel">
          {{ t('common.notNow') }}
        </button>
        <button class="btn btn-primary" type="button" data-autofocus data-testid="mic-allow" @click="mic.confirm">
          {{ t('common.allowMicrophone') }}
        </button>
      </template>
    </AppOverlay>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'

import AppOverlay from '@/components/common/AppOverlay.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useAudioRecorder } from '@/composables/useAudioRecorder'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useMicPermission } from '@/composables/useMicPermission'
import { learnMessages } from '@/plugins/learnMessages'
import { learnSharedMessages } from '@/plugins/learnSharedMessages'
import { formatClock } from '@/utils/format'
import { stopSpeaking } from '@/utils/speech'

import ListenButtons from './ListenButtons.vue'

/**
 * The last step of a lesson: hear a phrase from the lesson, say it, and play the recording back to compare it with the
 * model. It is not graded and nothing leaves the browser; the footer's Continue moves on whatever happens here. The
 * first recording asks in our own dialog before the browser asks; a blocked microphone says how to allow it, and the
 * step can still be said out loud. While recording, the note counts the time up and names the phrase to say before
 * Stop.
 */
const props = defineProps<{
  hanzi: string
  pinyin: string | null
  meaning: string | null
}>()

const { t } = useLocalMessages({ learn: [learnSharedMessages, learnMessages] })
const baseId = useId()
const recorder = useAudioRecorder()
const mic = useMicPermission(recorder.start, { checkOnRequest: true })

const caption = computed(() => [props.pinyin, props.meaning].filter(Boolean).join(' · '))

// Playing the recording back

const heading = ref<HTMLHeadingElement | null>(null)
const player = ref<HTMLAudioElement | null>(null)
const playing = ref(false)
const playbackFailed = ref(false)

onMounted(() => {
  if (!document.activeElement || document.activeElement === document.body) {
    heading.value?.focus()
  }
})

async function togglePlayback(): Promise<void> {
  const audio = player.value
  if (!audio) return
  if (playing.value) {
    audio.pause()
    audio.currentTime = 0
    return
  }
  stopSpeaking()
  playbackFailed.value = false
  try {
    await audio.play()
  } catch (reason) {
    if (audio === player.value && !(reason instanceof DOMException && reason.name === 'AbortError')) {
      playbackFailed.value = true
    }
  }
}

function stopPlayback(): void {
  if (player.value && !player.value.paused) player.value.pause()
}

function requestRecording(): void {
  stopSpeaking()
  stopPlayback()
  void mic.request()
}

// A new recording replaces the one being played.
watch(recorder.previewUrl, () => {
  stopPlayback()
  playing.value = false
  playbackFailed.value = false
})

onBeforeUnmount(stopPlayback)

/** Why recording is not possible right now; saying it out loud still works, so the step never blocks. */
const problem = computed(() => {
  if (!recorder.supported) return t('learn.sayUnavailable')
  if (recorder.status.value === 'denied') return t('learn.sayDenied')
  if (recorder.status.value === 'error') return t('learn.sayFailed')
  if (playbackFailed.value) return t('learn.audioFailed')
  return ''
})

const status = computed(() => {
  if (recorder.status.value === 'recorded') {
    return t('learn.sayRecorded', { time: formatClock(recorder.elapsedSeconds.value) })
  }
  return t('learn.sayNote')
})
</script>
