<template>
  <div class="class-player">
    <div class="class-player-head">
      <span class="page-eyebrow student-classes-pigment class-player-kind"
        ><IconGlyph name="flash" :size="16" />{{ t('classPlayer.flashcards.title') }}</span
      >
      <strong v-if="card" class="class-player-count">
        {{ t('classPlayer.flashcards.card', { number: index + 1, total: cards.length }) }}
      </strong>
      <span class="class-player-tools">
        <button
          v-if="hintsAllowed && card && !showPinyin"
          type="button"
          class="btn btn-ghost btn-sm"
          @click="hintOpen = !hintOpen"
        >
          <IconGlyph name="hint" :size="16" />{{
            hintOpen ? t('classPlayer.activity.hideHint') : t('classPlayer.activity.showHint')
          }}
        </button>
        <button
          v-if="canListen && card"
          type="button"
          class="btn btn-ghost btn-sm"
          :aria-label="t('classes.listenTo', { text: card.chinese })"
          @click="speakChinese(card.chinese)"
        >
          <IconGlyph name="audio" :size="16" />{{ t('classes.listen') }}
        </button>
      </span>
    </div>

    <p v-if="!cards.length" class="class-player-empty">{{ t('classPlayer.flashcards.empty') }}</p>
    <template v-else-if="card">
      <button
        type="button"
        class="class-flashcard"
        :class="{ 'is-flipped': flipped }"
        :aria-pressed="flipped"
        data-testid="flashcard"
        @click="flip"
      >
        <template v-if="!flipped">
          <span class="page-eyebrow">{{ t('classPlayer.flashcards.word') }}</span>
          <strong class="hanzi class-flashcard-word" lang="zh-CN">{{ card.chinese }}</strong>
          <span v-if="showPinyin || hintOpen" class="class-flashcard-pinyin">{{ card.pinyin }}</span>
          <span class="class-flashcard-flip"
            ><IconGlyph name="refresh" :size="14" />{{ t('classPlayer.flashcards.showMeaning') }}</span
          >
        </template>
        <template v-else>
          <strong class="class-flashcard-meaning">{{ card.meaning }}</strong>
          <span class="class-flashcard-pinyin">
            <span class="cjk-text" lang="zh-CN">{{ card.chinese }}</span> · {{ card.pinyin }}
          </span>
          <span class="class-flashcard-flip"
            ><IconGlyph name="refresh" :size="14" />{{ t('classPlayer.flashcards.showWord') }}</span
          >
        </template>
      </button>

      <div class="class-player-nav">
        <button type="button" class="btn btn-outline btn-sm" :disabled="index === 0" @click="go(index - 1)">
          <IconGlyph name="chevronLeft" :size="16" />{{ t('classPlayer.flashcards.previousCard') }}
        </button>
        <span class="class-player-dots" aria-hidden="true">
          <span
            v-for="(_, dotIndex) in cards"
            :key="dotIndex"
            class="class-player-dot"
            :class="{ 'is-current': dotIndex === index, 'is-seen': seen.has(dotIndex) }"
          />
        </span>
        <button
          type="button"
          class="btn btn-outline btn-sm"
          :disabled="index === cards.length - 1"
          @click="go(index + 1)"
        >
          {{ t('classPlayer.flashcards.nextCard') }}<IconGlyph name="chevronRight" :size="16" />
        </button>
      </div>
    </template>

    <div class="class-player-foot">
      <span class="type-caption">
        {{
          allSeen
            ? t('classPlayer.flashcards.footer')
            : t('classPlayer.flashcards.seeAll', { seen: seen.size, total: cards.length })
        }}
      </span>
      <button
        type="button"
        class="btn btn-primary"
        :disabled="!allSeen || busy"
        :aria-busy="busy"
        data-testid="activity-finish"
        @click="emit('finish')"
      >
        <IconGlyph name="check" :size="16" />{{ t('classPlayer.flashcards.markReviewed') }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classPlayerMessages } from '@/plugins/classPlayerMessages'
import { classesMessages } from '@/plugins/classesMessages'
import type { LearningStage } from '@/types/api'
import type { KeyPhrase } from '@/types/classes'
import { canSpeak, speakChinese } from '@/utils/speech'

/**
 * Flashcards of the lesson's key phrases. By stage: Guided shows the pinyin, Supported hides it behind a hint,
 * Independent has no hint. The set can be marked as reviewed once every card was seen.
 */
const props = defineProps<{ cards: readonly KeyPhrase[]; stage: LearningStage | null; busy: boolean }>()
const emit = defineEmits<{ finish: [] }>()

const { t } = useLocalMessages({ classPlayer: classPlayerMessages, classes: classesMessages })
const canListen = canSpeak()

const index = ref(0)
const flipped = ref(false)
const hintOpen = ref(false)
const seen = ref(new Set<number>([0]))

const card = computed(() => props.cards[index.value] ?? null)
const showPinyin = computed(() => props.stage !== 'SCAFFOLDED' && props.stage !== 'INDEPENDENT')
const hintsAllowed = computed(() => props.stage === 'SCAFFOLDED')
const allSeen = computed(() => props.cards.length > 0 && seen.value.size >= props.cards.length)

function go(next: number): void {
  index.value = Math.min(Math.max(0, next), props.cards.length - 1)
  flipped.value = false
  hintOpen.value = false
  seen.value = new Set([...seen.value, index.value])
}

function flip(): void {
  flipped.value = !flipped.value
}
</script>
