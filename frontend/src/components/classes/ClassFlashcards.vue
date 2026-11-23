<template>
  <div class="class-player">
    <header class="class-focus-head">
      <p class="page-eyebrow">{{ t('classPlayer.flashcards.title') }}</p>
      <h1 class="focus-title"><CjkText :text="title" /></h1>
      <p class="class-focus-sub">{{ t('classPlayer.flashcards.instruction') }}</p>
    </header>

    <p v-if="!cards.length" class="class-focus-sub">{{ t('classPlayer.flashcards.empty') }}</p>
    <div v-else-if="card" class="class-flashcard-stack">
      <div class="class-player-row">
        <strong class="class-player-count" aria-live="polite">
          {{ t('classPlayer.flashcards.card', { number: index + 1, total: cards.length }) }}
        </strong>
        <button
          v-if="canListen"
          type="button"
          class="btn btn-secondary btn-sm"
          :aria-label="t('classes.listenTo', { text: card.chinese })"
          @click="speak(card.chinese)"
        >
          <IconGlyph name="audio" :size="16" />{{ t('classes.listen') }}
        </button>
      </div>

      <button type="button" class="class-flashcard" :aria-pressed="flipped" @click="flipped = !flipped">
        <template v-if="!flipped">
          <span class="page-eyebrow">{{ t('classPlayer.flashcards.phrase') }}</span>
          <strong
            class="hanzi class-flashcard-word"
            :class="{ 'is-long': Array.from(card.chinese).length > LONG_PHRASE }"
            lang="zh-CN"
            >{{ card.chinese }}</strong
          >
          <span v-if="card.pinyin" class="class-flashcard-pinyin">{{ card.pinyin }}</span>
          <span class="class-flashcard-flip">
            <IconGlyph name="refresh" :size="14" />{{ t('classPlayer.flashcards.showMeaning') }}
          </span>
        </template>
        <template v-else>
          <strong class="class-flashcard-meaning">{{ card.meaning }}</strong>
          <span class="class-flashcard-pinyin">
            <span class="cjk-text" lang="zh-CN">{{ card.chinese }}</span>
            <template v-if="card.pinyin"> · {{ card.pinyin }}</template>
          </span>
          <span class="class-flashcard-flip">
            <IconGlyph name="refresh" :size="14" />{{ t('classPlayer.flashcards.showWord') }}
          </span>
        </template>
      </button>
    </div>

    <FocusFooter v-if="!done && cards.length">
      <template #start>
        <button
          type="button"
          class="btn btn-secondary btn-lg"
          :disabled="busy"
          :aria-disabled="busy || index === 0"
          @click="previous"
        >
          {{ t('classPlayer.flashcards.previous') }}
        </button>
      </template>
      <template #end>
        <!-- After the last card the set counts as reviewed: its Next finishes it. -->
        <button
          type="button"
          class="btn btn-primary btn-lg"
          :disabled="busy"
          :aria-busy="busy"
          :data-testid="isLast ? 'activity-finish' : 'flashcard-next'"
          @click="isLast ? finish() : go(index + 1)"
        >
          {{ t('classPlayer.flashcards.next') }}
        </button>
      </template>
    </FocusFooter>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import FocusFooter from '@/components/layout/FocusFooter.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useMandarinReadAloud } from '@/composables/useMandarinSpeech'
import { classPlayerMessages } from '@/plugins/classPlayerMessages'
import { classesMessages } from '@/plugins/classesMessages'
import type { PhraseCard } from '@/types/classes'

/**
 * The activity's flashcards (an older activity without cards of its own gets the lesson's key phrases from the
 * server): flip each card (the word and its pinyin, then the meaning with the pinyin), Previous and Next
 * through the set. Next on the last card finishes it (`finish`); after that (`done`) the page's result band replaces
 * the footer and the last card stays on show.
 */
const props = defineProps<{ title: string; cards: readonly PhraseCard[]; busy: boolean; done: boolean }>()
const emit = defineEmits<{ finish: [] }>()

/** Longer phrases than this use the smaller size, so they stay on one line of the column. */
const LONG_PHRASE = 8

const { t } = useLocalMessages({ classPlayer: classPlayerMessages, classes: classesMessages })
const { canSpeak: canListen, speak, stopSpeaking } = useMandarinReadAloud()

const index = ref(0)
const flipped = ref(false)

const card = computed(() => props.cards[index.value] ?? null)
const isLast = computed(() => index.value >= props.cards.length - 1)

function go(next: number): void {
  stopSpeaking()
  index.value = next
  flipped.value = false
}

/** On the first card Previous stays focusable (aria-disabled) and does nothing. */
function previous(): void {
  if (index.value > 0) go(index.value - 1)
}

function finish(): void {
  stopSpeaking()
  emit('finish')
}
</script>
