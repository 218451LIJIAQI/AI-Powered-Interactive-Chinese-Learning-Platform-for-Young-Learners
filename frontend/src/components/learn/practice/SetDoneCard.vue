<template>
  <section class="practice-done" data-testid="set-done" aria-live="polite">
    <figure class="practice-done-stamp">
      <span class="practice-done-marks" aria-hidden="true">
        <PracticeGlyph :type="result.type" size="lg" />
        <span class="seal practice-done-seal" :class="{ 'is-outline': !result.allCorrect }" lang="zh-CN">{{
          result.allCorrect ? '好' : '加'
        }}</span>
      </span>
      <figcaption class="practice-done-caption">
        {{ result.allCorrect ? t('practice.haoCaption') : t('practice.jiayouCaption') }}
      </figcaption>
    </figure>

    <div class="practice-done-copy">
      <h2 class="practice-done-title">{{ title }}</h2>
      <p class="practice-done-sub">{{ subtitle }}</p>
    </div>

    <div class="practice-done-reward">
      <strong v-if="earned > 0" class="stat-figure practice-done-xp">+{{ earned }} XP</strong>
      <p v-else class="practice-done-note">{{ xpNote }}</p>
      <p class="practice-done-goal">
        <IconGlyph :name="result.xp.goalReached ? 'check' : 'target'" :size="16" />
        {{ goalLine }}
      </p>
    </div>

    <div class="practice-done-actions">
      <button class="btn btn-secondary" type="button" data-testid="practise-again" @click="emit('again')">
        <IconGlyph name="retry" :size="16" />{{ t('practice.practiseAgain') }}
      </button>
      <button
        v-if="result.nextSet"
        class="btn btn-primary"
        type="button"
        data-testid="next-set"
        @click="emit('next', result.nextSet.id)"
      >
        {{ t('practice.nextSet', { title: result.nextSet.title }) }}<IconGlyph name="chevronRight" :size="16" />
      </button>
      <RouterLink
        v-else
        class="btn btn-primary"
        :to="{ name: 'student-practice', params: { type: nextType.slug } }"
        data-testid="next-type"
      >
        {{ t('practice.tryType', { type: t(nextType.labelKey) }) }}<IconGlyph name="chevronRight" :size="16" />
      </RouterLink>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { practiceMessages } from '@/plugins/practiceMessages'
import type { PracticeResult } from '@/types/learn'

import PracticeGlyph from './PracticeGlyph.vue'
import { nextPracticeType } from './practiceTypes'

/**
 * A finished set: the type's tile stamped with a seal (好 when every item was right, an open 加 "keep going"
 * otherwise), how it went, the XP it earned (or why none) and today's goal, then Practise again and the next set, or
 * the next activity once this type has no set left.
 */
const props = defineProps<{ result: PracticeResult; xpReward: number }>()
const emit = defineEmits<{ again: []; next: [setId: number] }>()

const { t } = useLocalMessages({ practice: practiceMessages })

const earned = computed(() => props.result.xp.xpEarned + props.result.xp.bonusXp)
const nextType = computed(() => nextPracticeType(props.result.type))

const title = computed(() => {
  const result = props.result
  switch (result.type) {
    case 'FLASHCARDS':
      return t('practice.doneFlashcards')
    case 'TRACING':
      return result.allCorrect ? t('practice.doneTracing', { title: result.title }) : t('practice.notFinished')
    case 'STORY':
      return result.allCorrect
        ? t('practice.doneStory')
        : t('practice.rightOf', { right: result.correctCount, total: result.itemCount })
    case 'MATCHING':
      return result.allCorrect
        ? t('practice.doneMatching', { total: result.itemCount })
        : t('practice.rightOf', { right: result.correctCount, total: result.itemCount })
    default:
      return result.allCorrect
        ? t('practice.doneAllRight')
        : t('practice.rightOf', { right: result.correctCount, total: result.itemCount })
  }
})

const subtitle = computed(() => {
  const result = props.result
  if (result.type === 'FLASHCARDS') return t('practice.cardsReviewed', { count: result.itemCount }, result.itemCount)
  if (result.allCorrect) return t('practice.everyItemRight', { title: result.title })
  return t(
    'practice.mistakesSaved',
    { count: result.itemCount - result.correctCount },
    result.itemCount - result.correctCount,
  )
})

const xpNote = computed(() =>
  props.result.allCorrect ? t('practice.xpAlreadyEarned') : t('practice.xpWhenAllRight', { xp: props.xpReward }),
)

const goalLine = computed(() => {
  const xp = props.result.xp
  if (xp.goalJustReached) return t('practice.goalJustReached', { days: xp.currentStreak }, xp.currentStreak)
  if (xp.goalReached) return t('practice.goalAlreadyReached', { xp: xp.todayXp, goal: xp.dailyGoalXp })
  return t('practice.goalToGo', {
    xp: Math.max(0, xp.dailyGoalXp - xp.todayXp),
    today: xp.todayXp,
    goal: xp.dailyGoalXp,
  })
})
</script>
