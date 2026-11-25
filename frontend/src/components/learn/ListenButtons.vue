<template>
  <!-- The audio of a listening question did not play: it says so, with Retry while the browser can speak at all. -->
  <div v-if="large && playbackFailed" class="learn-listen is-large is-failed" data-testid="audio-failed">
    <p class="learn-listen-failed" role="alert"><IconGlyph name="alert" :size="20" />{{ t('learn.audioFailed') }}</p>
    <button
      v-if="supportsRetry && hasText"
      ref="retryButton"
      class="btn btn-secondary"
      type="button"
      data-testid="audio-retry"
      @click="play()"
    >
      <IconGlyph name="retry" :size="16" />{{ t('learn.retryAudio') }}
    </button>
  </div>
  <div v-else-if="speechAvailable && hasText" class="learn-listen" :class="{ 'is-large': large, 'is-prompt': prompt }">
    <button
      ref="playButton"
      class="btn learn-listen-play"
      :class="large || prompt ? 'btn-primary' : 'btn-secondary btn-sm'"
      type="button"
      :aria-label="t('learn.listenTo', { text: name })"
      @click="play()"
    >
      <IconGlyph name="audio" :size="large ? 30 : prompt ? 22 : 16" />
      <span v-if="!large && !prompt">{{ t('learn.listen') }}</span>
    </button>
    <button
      v-if="large"
      class="btn btn-secondary learn-listen-slow"
      type="button"
      :aria-label="t('learn.listenSlowlyTo', { text: name })"
      @click="play(true)"
    >
      {{ t('learn.slow') }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useMandarinReadAloud } from '@/composables/useMandarinSpeech'
import { learnSharedMessages } from '@/plugins/learnSharedMessages'
import { supportsSpeechSynthesis } from '@/utils/speech'

/**
 * Listen for a Chinese word or sentence; hidden where the browser cannot speak. `prompt` is the 56 speaker beside a
 * question's word, `large` the 84 speaker of a listening question with Slow beside it, and the default a small Listen
 * for rows. A listening question cannot be answered without its audio, so the large player says when the audio did not
 * play ("Audio didn't load", with Retry): in a browser that cannot speak at all, or when its speech fails before it
 * starts. `v-model:failed` tells the question's player, which may then not count a skip.
 */
interface Props {
  text: string
  /** The big speaker of a listening question, with Slow beside it. */
  large?: boolean
  /** The speaker beside the word of a question. */
  prompt?: boolean
}

const props = defineProps<Props>()
const emit = defineEmits<{ play: [] }>()
/** A skipped listening question is exempt only if the learner could not hear this item. */
const failed = defineModel<boolean>('failed', { default: false })
const playbackFailed = ref(false)
const playButton = ref<HTMLButtonElement | null>(null)
const retryButton = ref<HTMLButtonElement | null>(null)
let heard = false

const { t } = useLocalMessages({ learn: learnSharedMessages })
const { canSpeak: speechAvailable, speak, stopSpeaking } = useMandarinReadAloud()
const supportsRetry = supportsSpeechSynthesis()
const hasText = computed(() => Boolean(props.text.trim()))
/** What the accessible name says is played. The audio of a listening question is its answer, so it is not spelt out. */
const name = computed(() => (props.large ? t('learn.theAudio') : props.text))

function reportPlaybackFailure(hadFocus = false): void {
  playbackFailed.value = true
  failed.value = !heard
  if (hadFocus) void nextTick(() => retryButton.value?.focus())
}

function play(slow = false): void {
  if (!hasText.value) {
    if (props.large) reportPlaybackFailure()
    return
  }
  emit('play')
  if (!props.large) {
    speak(props.text, slow)
    return
  }
  const hadFocus = document.activeElement === playButton.value || document.activeElement === retryButton.value
  const wasFailed = playbackFailed.value
  let started = false
  const asked = speak(props.text, slow, (speaking, reason) => {
    if (speaking) {
      started = true
      heard = true
      playbackFailed.value = false
      failed.value = false
      if (hadFocus && wasFailed) void nextTick(() => playButton.value?.focus())
    } else if (!started && reason !== 'cancelled') {
      // It ended without ever starting: the browser's speech failed.
      reportPlaybackFailure(hadFocus)
    }
  })
  if (!asked) reportPlaybackFailure(hadFocus)
}

/** An unavailable Mandarin voice or blank text gives a listening question skip protection. */
function expectAudio(): void {
  heard = false
  playbackFailed.value = Boolean(props.large) && (!speechAvailable.value || !hasText.value)
  failed.value = playbackFailed.value
}

onMounted(expectAudio)
watch(speechAvailable, (available) => {
  if (!props.large) return
  if (!available) {
    stopSpeaking()
    reportPlaybackFailure()
  } else if (playbackFailed.value) {
    playbackFailed.value = false
    failed.value = false
  }
})
// A new text is another question: what the last one's audio did no longer counts.
watch(
  () => props.text,
  () => {
    stopSpeaking()
    expectAudio()
  },
)
</script>
