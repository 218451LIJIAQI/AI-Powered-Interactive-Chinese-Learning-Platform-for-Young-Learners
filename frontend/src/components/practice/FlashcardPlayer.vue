<template>
  <div class="practice-player flashcard-player">
    <button
      type="button"
      class="flashcard-card"
      :class="{ 'is-flipped': flipped }"
      data-testid="flashcard-flip"
      :aria-pressed="flipped"
      :aria-describedby="hintId"
      @click="flip"
    >
      <span class="flashcard-badge">{{
        flipped ? t('studentPractice.cardBack') : t('studentPractice.cardFront')
      }}</span>
      <template v-if="!flipped">
        <strong class="flashcard-word cjk-text" :lang="cjkLang(flashcard.frontText)">{{ flashcard.frontText }}</strong>
        <span class="flashcard-pinyin">{{ flashcard.pinyin || t('studentPractice.pinyinUnavailable') }}</span>
      </template>
      <template v-else>
        <span class="flashcard-meaning-row">
          <small>{{ t('studentPractice.englishMeaning') }}</small>
          <b>{{ flashcard.englishMeaning || flashcard.backText }}</b>
        </span>
        <span v-if="flashcard.malayMeaning" class="flashcard-meaning-row" lang="ms">
          <small>{{ t('studentPractice.malayMeaning') }}</small>
          <b>{{ flashcard.malayMeaning }}</b>
        </span>
        <span
          v-if="flashcard.englishMeaning && flashcard.backText && flashcard.backText !== flashcard.englishMeaning"
          class="flashcard-back-text"
          :lang="cjkLang(flashcard.backText)"
          >{{ flashcard.backText }}</span
        >
      </template>
      <span class="flashcard-flip-label">
        <IconGlyph name="refresh" :size="16" />
        {{ flipped ? t('studentPractice.flipToFront') : t('studentPractice.flipToBack') }}
      </span>
    </button>
    <p :id="hintId" class="sr-only">
      {{
        t('studentPractice.flipAria', {
          side: flipped ? t('studentPractice.cardBack') : t('studentPractice.cardFront'),
        })
      }}
    </p>

    <div class="flashcard-audio">
      <audio
        v-if="safeAudioUrl"
        controls
        preload="none"
        :src="safeAudioUrl"
        :aria-label="t('studentPractice.pronunciationAudio')"
      />
      <button v-else type="button" class="btn btn-ghost btn-sm" @click="speak">
        <IconGlyph name="audio" :size="16" />
        <span>{{ t('studentPractice.playPronunciation') }}</span>
      </button>
    </div>

    <div class="practice-submit-row">
      <span class="practice-progress">{{
        flashcard.completed ? t('studentPractice.flashcardCompleted') : t('studentPractice.flashcardReview')
      }}</span>
      <button
        type="button"
        class="btn btn-primary"
        data-testid="activity-submit"
        :disabled="locked || submitting"
        @click="emit('submit', { type: 'FLASHCARD' })"
      >
        {{ submitting ? t('studentPractice.markingReviewed') : t('studentPractice.markReviewed') }}
      </button>
    </div>

    <p class="sr-only" role="status" aria-live="polite">{{ announcement }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, useId } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import type { StudentFlashcardResponse } from '@/types/api'
import { cjkLang, type ActivitySubmission } from '@/utils/activityCompletion'
import { safeHttpUrl } from '@/utils/url'

import { usePracticeI18n } from './usePracticeI18n'

const props = defineProps<{
  flashcard: StudentFlashcardResponse
  locked?: boolean
  submitting?: boolean
}>()

const emit = defineEmits<{ submit: [submission: ActivitySubmission] }>()

const { t } = usePracticeI18n()
const hintId = `flashcard-hint-${useId()}`
const flipped = ref(false)
const announcement = ref('')
const safeAudioUrl = computed(() => safeHttpUrl(props.flashcard.audioUrl, { allowRelative: true }))

function flip(): void {
  flipped.value = !flipped.value
}

function speak(): void {
  if (typeof window === 'undefined' || !('speechSynthesis' in window)) {
    announcement.value = t('studentPractice.playbackUnavailable')
    return
  }

  const utterance = new SpeechSynthesisUtterance(props.flashcard.frontText)
  utterance.lang = 'zh-CN'
  window.speechSynthesis.cancel()
  window.speechSynthesis.speak(utterance)
  announcement.value = t('studentPractice.playing')
}
</script>

<style scoped src="./practicePlayer.css"></style>
<style scoped>
.flashcard-card {
  position: relative;
  display: grid;
  justify-items: center;
  align-content: center;
  gap: var(--space-2-5);
  width: 100%;
  min-height: 220px;
  padding: var(--space-7) var(--space-5) var(--space-5);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-xl);
  background: linear-gradient(180deg, var(--surface-card), var(--color-primary-50));
  color: var(--text-primary);
  text-align: center;
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    box-shadow var(--duration-fast) var(--ease-standard);
}

.flashcard-card:hover {
  border-color: var(--border-interactive);
  box-shadow: var(--shadow-md);
}

.flashcard-card.is-flipped {
  border-color: var(--color-info-border);
  background: linear-gradient(180deg, var(--surface-card), var(--color-info-soft));
  animation: flashcard-flip var(--duration-slow) var(--ease-out);
}

@keyframes flashcard-flip {
  from {
    transform: rotateY(80deg);
  }
  to {
    transform: rotateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .flashcard-card.is-flipped {
    animation: none;
  }
}

.flashcard-badge {
  padding: var(--space-0-5) var(--space-2-5);
  border-radius: var(--radius-pill);
  background: var(--surface-card);
  color: var(--text-secondary);
  font-size: var(--type-overline-size);
  font-weight: var(--font-weight-extrabold);
  letter-spacing: var(--letter-spacing-wide);
  text-transform: uppercase;
}

.flashcard-word {
  font-family: var(--font-cjk);
  font-size: var(--font-size-7xl);
  line-height: var(--line-height-tight);
  overflow-wrap: anywhere;
}

.flashcard-pinyin {
  color: var(--text-secondary);
  font-size: var(--font-size-lg);
}

.flashcard-meaning-row {
  display: grid;
  gap: var(--space-0-5);
}

.flashcard-meaning-row small {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
}

.flashcard-meaning-row b {
  color: var(--color-primary-700);
  font-size: var(--font-size-2xl);
  overflow-wrap: anywhere;
}

.flashcard-back-text {
  color: var(--text-secondary);
}

.flashcard-flip-label {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1-5);
  margin-top: var(--space-1-5);
  color: var(--text-link);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-extrabold);
}

.flashcard-audio {
  display: flex;
  justify-content: center;
}

.flashcard-audio audio {
  width: min(100%, 320px);
}

@media (max-width: 640px) {
  .flashcard-card {
    min-height: 180px;
  }

  .flashcard-word {
    font-size: var(--font-size-6xl);
  }
}
</style>
