<template>
  <div class="practice-player" data-testid="set-done">
    <PracticeProgress :label="counterLabel" />

    <section class="practice-done" aria-live="polite" :aria-labelledby="titleId">
      <span class="practice-done-stamp" :class="{ 'is-open': !complete }" aria-hidden="true">
        <IconGlyph :name="complete ? 'check' : 'retry'" :size="28" />
      </span>
      <h2 :id="titleId" class="practice-done-title"><CjkText :text="title" /></h2>
      <p v-if="subtitle" class="practice-done-sub"><CjkText :text="subtitle" /></p>
      <strong v-if="earned > 0" class="tag is-strong">{{ t('practice.xpTag', { xp: earned }) }}</strong>
      <p v-else class="practice-done-sub">{{ xpNote }}</p>
    </section>

    <div class="practice-footer">
      <div class="btn-group">
        <button
          class="btn btn-secondary"
          type="button"
          :disabled="busy"
          :aria-busy="busy"
          data-testid="practise-again"
          @click="emit('again')"
        >
          <IconGlyph name="retry" :size="16" />{{ t('common.practiseAgain') }}
        </button>
      </div>
      <div class="practice-footer-end">
        <RouterLink
          v-if="result.nextSet"
          class="btn btn-primary"
          :to="practiceSetRoute(result.type, result.nextSet.id)"
          data-testid="next-set"
        >
          {{ t('practice.nextSet', { title: result.nextSet.title }) }}
        </RouterLink>
        <RouterLink
          v-else
          class="btn btn-primary"
          :to="{ name: 'student-practice', params: { type: PRACTICE_ACTIVITIES[nextType].slug } }"
          data-testid="next-type"
        >
          {{ t('practice.tryType', { type: t(`practiceType.${nextType}`) }) }}
        </RouterLink>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, useId } from 'vue'
import { RouterLink } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { PRACTICE_ACTIVITIES } from '@/config/practiceActivities'
import { practiceMessages } from '@/plugins/practiceMessages'
import type { PracticeResult, PracticeSet, PracticeType } from '@/types/learn'
import { tracingGlyphs } from '@/utils/tracing'

import PracticeProgress from './PracticeProgress.vue'
import { nextPracticeType, practiceSetRoute, type RunTally } from './practiceTypes'

/**
 * A finished set, in the place of its player: the stage's counter complete, the Complete card (a check stamp when every
 * item was right, an open one otherwise, how it went and the XP it earned or why none), then Practise again and the next
 * set the server offers (the next one not done yet; with all done, the one after this), or the next activity when it
 * offers none (every set done and this the last). Today's goal is in the strip above the stage. A run of answered items
 * (Listening, Drag & Drop, Typing) counts the items answered and, when nothing went to Mistakes, the items correct.
 */
const props = defineProps<{
  result: PracticeResult
  /**
   * The set as played: its XP, whether it was done (every item right once) before this run, so its XP was paid then,
   * and a traced set's characters and strokes.
   */
  set: PracticeSet
  /** What the set covered ("三 three · 五 five"), from the player. */
  recap: string
  /** What the player of answered items counted of its run; null for the other activities. */
  tally?: RunTally | null
  /** Practise again is reading the set again. */
  busy?: boolean
}>()
const emit = defineEmits<{ again: [] }>()

/** The activities whose items are answered one by one, with a verdict each. */
const ANSWERED_TYPES: ReadonlySet<PracticeType> = new Set(['LISTENING', 'DRAG_DROP', 'TYPING'])

const { t } = useLocalMessages({ practice: practiceMessages })
const titleId = useId()

const isAnswered = computed(() => ANSWERED_TYPES.has(props.result.type))
/** Every item right; a reviewed flashcard set is complete whatever the ratings (cards still learned go to Mistakes). */
const complete = computed(() => props.result.allCorrect || props.result.type === 'FLASHCARDS')
const earned = computed(() => props.result.xp.xpEarned + props.result.xp.bonusXp)
const nextType = computed(() => nextPracticeType(props.result.type))
/** What the run put on the Mistakes list: the player's count, else every item not right. */
const mistakesAdded = computed(() => props.tally?.mistakes ?? props.result.itemCount - props.result.correctCount)

/** "6 of 6 cards", "8 of 8 strokes", "4 of 4 paired", "5 of 5 answered": the counter of the stage, now complete. */
const counterLabel = computed(() => {
  const total = props.set.strokeCount ?? props.result.itemCount
  if (props.result.type === 'TRACING') return t('practice.strokesOf', { count: total, total })
  if (props.result.type === 'MATCHING') return t('practice.pairedOf', { paired: total, total })
  if (props.result.type === 'FLASHCARDS') return t('practice.cardsOf', { count: total, total })
  // A skipped item is not answered.
  return t('practice.answeredOf', { answered: props.tally?.answered ?? total, total })
})

/** "5 of 5 correct", "4 of 4 sentences correct", "6 of 6 words correct". */
function correctLine(result: PracticeResult): string {
  const values = { correct: result.correctCount, total: result.itemCount }
  if (result.type === 'DRAG_DROP') return t('practice.sentencesCorrect', values, result.itemCount)
  if (result.type === 'TYPING') return t('practice.wordsCorrect', values, result.itemCount)
  return t('practice.correctOf', values)
}

const title = computed(() => {
  const result = props.result
  if (result.type === 'FLASHCARDS') return t('practice.doneFlashcards')
  if (result.type === 'TRACING') {
    const raw = props.set.items[0]?.hanzi ?? ''
    const characters = tracingGlyphs(raw).join('') || raw
    return result.allCorrect ? t('practice.doneTracing', { characters }) : t('practice.notFinished')
  }
  // Nothing went to Mistakes: an item skipped because its audio did not play is the only one that can be not right.
  if (isAnswered.value && mistakesAdded.value === 0) return correctLine(result)
  if (!result.allCorrect) return t('practice.rightOf', { right: result.correctCount, total: result.itemCount })
  return result.type === 'MATCHING'
    ? t('practice.doneMatching', { total: result.itemCount })
    : t('practice.doneAllRight')
})

const subtitle = computed(() => {
  const result = props.result
  if (result.type === 'FLASHCARDS') {
    // "5 got it · 1 added to Mistakes": the cards rated Still learning wait there.
    const again = result.itemCount - result.correctCount
    const known = t('practice.cardsKnown', { count: result.correctCount })
    return again > 0 ? `${known} · ${t('practice.cardsAddedToMistakes', { count: again })}` : known
  }
  if (result.type === 'TRACING') return result.allCorrect ? props.recap : ''
  if (isAnswered.value && mistakesAdded.value === 0) return t('practice.noNewMistakes')
  if (!isAnswered.value && result.allCorrect) return props.recap
  return t('practice.mistakesSaved', { count: mistakesAdded.value }, mistakesAdded.value)
})

/** Why the run earned nothing: the set's XP was earned before, or comes with every item right (once only). */
const xpNote = computed(() =>
  props.set.done || complete.value
    ? t('practice.xpAlreadyEarned')
    : t('practice.xpWhenAllRight', { xp: props.set.xpReward }),
)
</script>
