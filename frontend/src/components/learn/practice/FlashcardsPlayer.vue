<template>
  <div class="practice-player" data-testid="flashcards-player">
    <PracticeProgress
      :dots="dots"
      :label="t('practice.cardOf', { current: index + 1, total: cards.length })"
      :list-label="t('practice.cardsSeen', { seen: seen.size, total: cards.length })"
    />

    <div v-if="card" class="practice-card" :class="{ 'is-flipped': flipped }">
      <Transition name="practice-flip" mode="out-in">
        <div :key="`${index}-${flipped}`" class="practice-card-face">
          <template v-if="!flipped">
            <p class="practice-card-label">{{ t('practice.word') }}</p>
            <p class="practice-card-hanzi hanzi" lang="zh-CN">{{ card.hanzi }}</p>
            <p v-if="card.pinyin" class="practice-card-pinyin">{{ card.pinyin }}</p>
          </template>
          <template v-else>
            <p class="practice-card-label">{{ t('practice.meaning') }}</p>
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
      <!-- Outside the turning face, so the focus stays on Flip. -->
      <div class="btn-group is-sm">
        <ListenButtons v-if="card.hanzi" :text="card.hanzi" />
        <button class="btn btn-secondary btn-sm" type="button" data-testid="flip" @click="flipped = !flipped">
          <IconGlyph name="retry" :size="16" />
          {{ flipped ? t('practice.flipBack') : t('practice.clickToFlip') }}
        </button>
      </div>
    </div>

    <p v-if="run.error.value" class="field-error" role="alert">{{ run.error.value }}</p>

    <div class="practice-footer">
      <div class="btn-group">
        <button class="btn btn-secondary" type="button" :disabled="index === 0" @click="go(index - 1)">
          {{ t('practice.previous') }}
        </button>
        <button
          class="btn btn-secondary"
          type="button"
          :disabled="index >= cards.length - 1"
          data-testid="next-card"
          @click="go(index + 1)"
        >
          {{ t('practice.next') }}
        </button>
      </div>
      <div class="practice-footer-end">
        <p v-if="!allSeen" :id="`${baseId}-hint`" class="practice-hint">{{ t('practice.seeEveryCard') }}</p>
        <button
          class="btn btn-primary"
          type="button"
          :disabled="!allSeen || run.busy.value"
          :aria-busy="run.busy.value"
          :aria-describedby="allSeen ? undefined : `${baseId}-hint`"
          data-testid="mark-reviewed"
          @click="finish"
        >
          <IconGlyph name="check" :size="16" />{{ t('practice.markReviewed') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, useId } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import ListenButtons from '@/components/learn/ListenButtons.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { practiceMessages } from '@/plugins/practiceMessages'
import type { PracticeResult, PracticeSet } from '@/types/learn'

import PracticeProgress, { type PracticeDot } from './PracticeProgress.vue'
import { usePracticeRun } from './usePracticeRun'

/**
 * Flashcards: the word and its pinyin, flip for the meaning and an example, listen, previous / next; marking the set
 * reviewed (after seeing every card) finishes it.
 */
const props = defineProps<{ set: PracticeSet }>()
const emit = defineEmits<{ finished: [result: PracticeResult] }>()

const { t } = useLocalMessages({ practice: practiceMessages })
const baseId = useId()
const run = usePracticeRun(
  () => props.set,
  () => t('practice.saveFailed'),
)

const cards = computed(() => props.set.items)
const index = ref(0)
const flipped = ref(false)
const seen = ref(new Set<number>([0]))
const card = computed(() => cards.value[index.value] ?? null)
const allSeen = computed(() => seen.value.size >= cards.value.length)

const dots = computed<PracticeDot[]>(() =>
  cards.value.map((_, position) => {
    if (position === index.value) return 'current'
    return seen.value.has(position) ? 'seen' : 'ahead'
  }),
)

function go(next: number): void {
  index.value = Math.min(Math.max(0, next), cards.value.length - 1)
  flipped.value = false
  seen.value = new Set([...seen.value, index.value])
}

async function finish(): Promise<void> {
  const result = await run.complete([])
  if (result) emit('finished', result)
}
</script>
