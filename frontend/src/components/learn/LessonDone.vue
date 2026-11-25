<template>
  <section class="learn-done" data-testid="lesson-complete" aria-live="polite">
    <figure class="learn-stamp">
      <span class="seal learn-stamp-seal" lang="zh-CN" aria-hidden="true">{{ result.passed ? '棒' : '加' }}</span>
      <figcaption class="learn-stamp-caption">
        {{ result.passed ? t('learn.bangCaption') : t('learn.jiayouCaption') }}
      </figcaption>
    </figure>
    <h2 class="focus-headline" tabindex="-1">{{ title }}</h2>
    <dl class="learn-done-stats">
      <div class="is-xp">
        <dt>{{ t('learn.xpEarned') }}</dt>
        <dd>+{{ result.xp.xpEarned + result.xp.bonusXp }}</dd>
      </div>
      <div>
        <dt>{{ t('learn.accuracy') }}</dt>
        <dd>{{ result.accuracy }}%</dd>
      </div>
    </dl>
    <p v-if="!result.passed" class="learn-done-note">
      {{ t('learn.checkpointNotPassed', { need: result.passPercent, got: result.accuracy }) }}
    </p>
    <p v-else-if="result.xp.xpEarned === 0" class="learn-done-note">{{ t('learn.alreadyEarned') }}</p>
    <p class="learn-done-streak">
      <span v-if="week.length" class="learn-done-week" aria-hidden="true">
        <span v-for="day in week" :key="day.date" :class="{ 'is-reached': day.goalReached }" />
      </span>
      <span>{{ goalLine }}</span>
    </p>
    <p v-if="result.nextLesson && result.passed" class="learn-done-next">
      <span class="learn-done-label">{{
        result.nextLesson.unlockedNow ? t('learn.unlocked') : t('learn.upNext')
      }}</span>
      <span class="learn-done-next-title">{{
        result.nextLesson.unlockedNow ? t('learn.nextUp', { title: result.nextLesson.title }) : result.nextLesson.title
      }}</span>
    </p>
    <ul v-if="result.xp.newBadges.length" class="learn-done-badges">
      <li v-for="badge in result.xp.newBadges" :key="badge">
        <IconGlyph :name="BADGES[badge].icon" :size="16" />
        {{ t('learn.newBadge', { badge: t(BADGES[badge].nameKey) }) }}
      </li>
    </ul>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { xpApi } from '@/api/learn'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { learnBadgeMessages } from '@/plugins/learnBadgeMessages'
import { learnMessages } from '@/plugins/learnMessages'
import type { LessonResult } from '@/types/learn'
import { BADGES } from '@/utils/learnBadges'

/**
 * The end of a lesson, in the place of its questions: the seal (棒, or 加 for a checkpoint not passed yet), the XP
 * earned and the accuracy, today's goal beside the week's goal days, what unlocked and any new badge. The week is read
 * once the result is on screen, so it never holds the result (or Try again) back; each result shows its own week.
 */
const props = defineProps<{ result: LessonResult }>()

const { t } = useLocalMessages({ learn: [learnMessages, learnBadgeMessages] })

const today = useResource(xpApi.today)
const week = computed(() => today.data.value?.week ?? [])

const title = computed(() => {
  if (props.result.kind !== 'CHECKPOINT') return t('learn.lessonComplete')
  return props.result.passed ? t('learn.checkpointPassed') : t('learn.checkpointFailed')
})

/** The streak once today's goal is reached, else the XP it still takes. */
const goalLine = computed(() => {
  const xp = props.result.xp
  if (xp.goalReached) return t('learn.streakGoalReached', { days: xp.currentStreak }, xp.currentStreak)
  return t('learn.streakGoalPending', { xp: Math.max(0, xp.dailyGoalXp - xp.todayXp) })
})
</script>
