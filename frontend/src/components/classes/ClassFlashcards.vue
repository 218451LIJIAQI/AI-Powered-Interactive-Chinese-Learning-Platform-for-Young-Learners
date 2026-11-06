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
          @click="speakChinese(card.chinese)"
        >
          <IconGlyph name="audio" :size="16" />{{ t('classes.listen') }}
        </button>
      </div>

      <button
        type="button"
        class="class-flashcard"
        :class="{ 'is-flipped': flipped }"
        :aria-pressed="flipped"
        data-testid="flashcard"
        @click="flip"
      >
        <template v-if="!flipped">
          <span class="page-eyebrow">{{ t('classPlayer.flashcards.phrase') }}</span>
          <strong
            class="hanzi class-flashcard-word"
            :class="{ 'is-long': Array.from(card.chinese).length > LONG_PHRASE }"
            lang="zh-CN"
            >{{ card.chinese }}</strong
          >
          <span class="class-flashcard-pinyin">{{ card.pinyin }}</span>
          <span class="class-flashcard-flip">
            <IconGlyph name="refresh" :size="14" />{{ t('classPlayer.flashcards.showMeaning') }}
          </span>
        </template>
        <template v-else>
          <strong class="class-flashcard-meaning">{{ card.meaning }}</strong>
          <span class="class-flashcard-pinyin">
            <span class="cjk-text" lang="zh-CN">{{ card.chinese }}</span> · {{ card.pinyin }}
          </span>
          <span class="class-flashcard-flip">
            <IconGlyph name="refresh" :size="14" />{{ t('classPlayer.flashcards.showWord') }}
          </span>
        </template>
      </button>
    </div>

    <FocusFooter v-if="!done">
      <template #start>
        <button
          type="button"
          class="btn btn-secondary btn-lg"
          :disabled="index === 0"
          :aria-label="t('classPlayer.flashcards.previousCard')"
          @click="go(index - 1)"
        >
          {{ t('classPlayer.flashcards.previous') }}
        </button>
        <button
          type="button"
          class="btn btn-secondary btn-lg"
          :disabled="index >= cards.length - 1"
          :aria-label="t('classPlayer.flashcards.nextCard')"
          @click="go(index + 1)"
        >
          {{ t('classPlayer.flashcards.next') }}
        </button>
      </template>
      <template v-if="cards.length && !allSeen" #default>
        {{ t('classPlayer.flashcards.seen', { seen: seen.size, total: cards.length }) }}
      </template>
      <template #end>
        <button
          type="button"
          class="btn btn-primary btn-lg"
          :disabled="!allSeen || busy"
          :aria-busy="busy"
          data-testid="activity-finish"
          @click="emit('finish')"
        >
          {{ t('classPlayer.flashcards.markReviewed') }}
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
import { classPlayerMessages } from '@/plugins/classPlayerMessages'
import { classesMessages } from '@/plugins/classesMessages'
import type { KeyPhrase } from '@/types/classes'
import { canSpeak, speakChinese } from '@/utils/speech'

/**
 * Flashcards of the lesson's key phrases: flip each card (Chinese and pinyin, then the meaning). The set can be marked
 * as reviewed once every card was seen; after that (`done`) the page's result band replaces the footer.
 */
const props = defineProps<{ title: string; cards: readonly KeyPhrase[]; busy: boolean; done: boolean }>()
const emit = defineEmits<{ finish: [] }>()

/** Longer phrases than this use the smaller size, so they stay on one line of the column. */
const LONG_PHRASE = 8

const { t } = useLocalMessages({ classPlayer: classPlayerMessages, classes: classesMessages })
const canListen = canSpeak()

const index = ref(0)
const flipped = ref(false)
const seen = ref(new Set<number>([0]))

const card = computed(() => props.cards[index.value] ?? null)
const allSeen = computed(() => props.cards.length > 0 && seen.value.size >= props.cards.length)

function go(next: number): void {
  index.value = Math.min(Math.max(0, next), props.cards.length - 1)
  flipped.value = false
  seen.value = new Set([...seen.value, index.value])
}

function flip(): void {
  flipped.value = !flipped.value
}
</script>
