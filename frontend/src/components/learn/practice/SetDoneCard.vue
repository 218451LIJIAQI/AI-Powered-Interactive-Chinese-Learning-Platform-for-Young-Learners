<template>
  <div class="practice-player" data-testid="set-done">
    <PracticeProgress :dots="dots" :squares="isTracing" :label="counterLabel" :list-label="t('practice.setProgress')" />

    <section class="practice-done" aria-live="polite" :aria-labelledby="titleId">
      <span class="practice-done-stamp" :class="{ 'is-open': !result.allCorrect }" aria-hidden="true">
        <IconGlyph :name="result.allCorrect ? 'check' : 'retry'" :size="28" />
      </span>
      <h2 :id="titleId" class="practice-done-title">{{ title }}</h2>
      <p v-if="subtitle" class="practice-done-sub">{{ subtitle }}</p>
      <strong v-if="earned > 0" class="tag is-strong">{{ t('practice.xpTag', { xp: earned }) }}</strong>
      <p v-else class="practice-done-sub">{{ xpNote }}</p>
    </section>

    <div class="practice-footer">
      <div class="btn-group">
        <button class="btn btn-secondary" type="button" data-testid="practise-again" @click="emit('again')">
          <IconGlyph name="retry" :size="16" />{{ t('practice.practiseAgain') }}
        </button>
      </div>
      <div class="practice-footer-end">
        <button
          v-if="result.nextSet"
          class="btn btn-primary"
          type="button"
          data-testid="next-set"
          @click="emit('next', result.nextSet.id)"
        >
          {{ t('practice.nextSet', { title: result.nextSet.title }) }}
        </button>
        <RouterLink
          v-else
          class="btn btn-primary"
          :to="{ name: 'student-practice', params: { type: nextType.slug } }"
          data-testid="next-type"
        >
          {{ t('practice.tryType', { type: t(nextType.labelKey) }) }}
        </RouterLink>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, useId } from 'vue'
import { RouterLink } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { practiceMessages } from '@/plugins/practiceMessages'
import type { PracticeResult } from '@/types/learn'

import PracticeProgress from './PracticeProgress.vue'
import { nextPracticeType } from './practiceTypes'

/**
 * A finished set, in the place of its player: the progress row all filled, the Complete card (a check stamp when every
 * item was right, an open one otherwise, how it went and the XP it earned or why none), then Practise again and the next
 * set, or the next activity once this type has no set left. Today's goal and streak are in the strip above the stage.
 */
const props = defineProps<{
  result: PracticeResult
  xpReward: number
  /** The set's strokes, for tracing's progress row. */
  strokeCount: number | null
  /** Characters of a traced set, for its title. */
  characters: string
  /** What the set covered ("三 three · 五 five"), from the player. */
  recap: string
}>()
const emit = defineEmits<{ again: []; next: [setId: number] }>()

const { t } = useLocalMessages({ practice: practiceMessages })
const titleId = useId()

const isTracing = computed(() => props.result.type === 'TRACING')
const earned = computed(() => props.result.xp.xpEarned + props.result.xp.bonusXp)
const nextType = computed(() => nextPracticeType(props.result.type))

const marks = computed(() => (isTracing.value ? Math.max(1, props.strokeCount ?? 1) : props.result.itemCount))
const dots = computed(() => Array.from({ length: marks.value }, () => 'seen' as const))

const counterLabel = computed(() => {
  const total = marks.value
  if (isTracing.value) return t('practice.strokesOf', { count: total, total })
  if (props.result.type === 'MATCHING') return t('practice.pairedOf', { paired: total, total })
  return t('practice.setComplete', { total })
})

const title = computed(() => {
  const result = props.result
  if (result.type === 'FLASHCARDS') return t('practice.doneFlashcards')
  if (result.type === 'TRACING') {
    return result.allCorrect ? t('practice.doneTracing', { characters: props.characters }) : t('practice.notFinished')
  }
  if (!result.allCorrect) return t('practice.rightOf', { right: result.correctCount, total: result.itemCount })
  return result.type === 'MATCHING'
    ? t('practice.doneMatching', { total: result.itemCount })
    : t('practice.doneAllRight')
})

const subtitle = computed(() => {
  const result = props.result
  if (result.type === 'FLASHCARDS') return t('practice.cardsReviewed', { count: result.itemCount }, result.itemCount)
  if (result.allCorrect) return props.recap
  const wrong = result.itemCount - result.correctCount
  return t('practice.mistakesSaved', { count: wrong }, wrong)
})

const xpNote = computed(() =>
  props.result.allCorrect ? t('practice.xpAlreadyEarned') : t('practice.xpWhenAllRight', { xp: props.xpReward }),
)
</script>
