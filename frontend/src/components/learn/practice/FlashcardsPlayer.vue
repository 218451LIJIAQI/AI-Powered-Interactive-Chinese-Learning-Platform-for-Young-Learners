<template>
  <div class="practice-player" data-testid="flashcards-player">
    <PracticeProgress :label="t('practice.cardOf', { current: index + 1, total: set.items.length })" />

    <!-- A click anywhere on the card turns it; Show meaning in the footer does the same for the keyboard. -->
    <div
      v-if="card"
      class="practice-card"
      :class="{ 'is-flipped': flipped }"
      data-testid="flashcard"
      @click="flipFromCard"
    >
      <div class="practice-card-stage" aria-live="polite" aria-atomic="true">
        <Transition name="practice-flip" mode="out-in">
          <div :key="`${index}-${flipped}`" class="practice-card-face">
            <p class="practice-card-label">{{ t(flipped ? 'practice.meaning' : 'practice.word') }}</p>
            <template v-if="!flipped">
              <p class="practice-card-hanzi hanzi" lang="zh-CN">{{ card.hanzi }}</p>
              <p v-if="card.pinyin" class="practice-card-pinyin">{{ card.pinyin }}</p>
            </template>
            <template v-else>
              <p class="practice-card-meaning">{{ card.meaning }}</p>
              <p class="practice-card-word">
                <span class="hanzi" lang="zh-CN">{{ card.hanzi }}</span>
                <span v-if="card.pinyin">{{ card.pinyin }}</span>
              </p>
              <p v-if="card.example" class="practice-card-example">
                <span class="cjk-text" lang="zh-CN">{{ card.example }}</span>
                <template v-if="card.exampleMeaning"> · {{ card.exampleMeaning }}</template>
              </p>
            </template>
          </div>
        </Transition>
      </div>
      <!-- Outside the turning face, so the focus stays where it is while the face changes. -->
      <div class="btn-group is-sm">
        <ListenButtons v-if="card.hanzi" :text="card.hanzi" />
        <button
          v-if="flipped"
          class="btn btn-secondary btn-sm"
          type="button"
          :disabled="run.busy.value"
          data-testid="flip-back"
          @click="flip"
        >
          <IconGlyph name="retry" :size="16" />{{ t('practice.flipBack') }}
        </button>
      </div>
    </div>

    <p v-if="run.error.value" class="field-error" role="alert">{{ run.error.value }}</p>

    <div class="practice-footer">
      <div class="btn-group">
        <button
          class="btn btn-secondary"
          type="button"
          :disabled="run.busy.value"
          :aria-disabled="index === 0"
          data-testid="previous-card"
          @click="back"
        >
          {{ t('practice.previous') }}
        </button>
      </div>
      <div class="practice-footer-end">
        <!-- The back asks how well the learner knows the card; the two buttons look the same, as neither is wrong. -->
        <div
          v-if="flipped"
          ref="ratingGroup"
          class="btn-group"
          role="group"
          :aria-label="t('practice.rateCard')"
          data-testid="rate-card"
        >
          <button
            class="btn btn-secondary"
            type="button"
            :disabled="run.busy.value"
            :aria-busy="run.busy.value && rated === 'STILL_LEARNING'"
            data-testid="rate-learning"
            @click="rate('STILL_LEARNING')"
          >
            {{ t('practice.stillLearning') }}
          </button>
          <button
            class="btn btn-secondary"
            type="button"
            :disabled="run.busy.value"
            :aria-busy="run.busy.value && rated === 'GOT_IT'"
            data-testid="rate-known"
            @click="rate('GOT_IT')"
          >
            {{ t('practice.gotIt') }}
          </button>
        </div>
        <button v-else ref="flipButton" class="btn btn-primary" type="button" data-testid="flip" @click="flip">
          {{ t('practice.showMeaning') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import ListenButtons from '@/components/learn/ListenButtons.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { practiceMessages } from '@/plugins/practiceMessages'
import type { FlashcardRating, PracticeResult, PracticeSet } from '@/types/learn'

import PracticeProgress from './PracticeProgress.vue'
import { usePracticeRun } from './usePracticeRun'

/**
 * Flashcards: the word and its pinyin; Show meaning (or a click on the card) turns it to the meaning and an example,
 * with Listen on both sides. The back asks the learner to rate themselves, Still learning or Got it, and moves on to
 * the next card; Previous goes back to look again (a new rating replaces the old one). Rating the last card finishes
 * the set: the ratings are sent with it, and the cards still being learned wait in Mistakes as multiple-choice
 * questions.
 */
const props = defineProps<{ set: PracticeSet }>()
const emit = defineEmits<{ finished: [result: PracticeResult] }>()

const { t } = useLocalMessages({ practice: practiceMessages })
const run = usePracticeRun(() => props.set, t)

const index = ref(0)
const flipped = ref(false)
const ratings = new Map<string, FlashcardRating>()
/** The rating being sent with the finished set (its button shows it is busy). */
const rated = ref<FlashcardRating | null>(null)
const flipButton = ref<HTMLButtonElement | null>(null)
const ratingGroup = ref<HTMLElement | null>(null)

const card = computed(() => props.set.items[index.value] ?? null)

/**
 * Turns the card. The footer's buttons change with the side, so the focus follows: to the ratings on the back, to Show
 * meaning on the front.
 */
async function flip(): Promise<void> {
  if (run.busy.value) return
  flipped.value = !flipped.value
  await nextTick()
  if (flipped.value) ratingGroup.value?.querySelector('button')?.focus()
  else flipButton.value?.focus()
}

/** The buttons inside the card do their own thing. */
function flipFromCard(event: MouseEvent): void {
  if (event.target instanceof Element && event.target.closest('button, a')) return
  void flip()
}

async function rate(rating: FlashcardRating): Promise<void> {
  if (!card.value || !flipped.value || run.busy.value) return
  ratings.set(card.value.key, rating)
  if (index.value >= props.set.items.length - 1) {
    rated.value = rating
    await finish()
    return
  }
  index.value += 1
  flipped.value = false
  await nextTick()
  flipButton.value?.focus()
}

function back(): void {
  if (index.value === 0 || run.busy.value) return
  index.value -= 1
  flipped.value = false
}

async function finish(): Promise<void> {
  const answers = Array.from(ratings, ([itemKey, answer]) => ({ itemKey, answer }))
  const result = await run.complete(answers)
  rated.value = null
  if (result) emit('finished', result)
}
</script>
