<template>
  <AppOverlay :title="title" :described-by-id="messageId" test-id="rank-up" @close="emit('close')">
    <div class="learn-rank-up">
      <span class="learn-rank-medal" aria-hidden="true">
        <span class="learn-rank-medal-number">{{ rankUp.rank }}</span>
        <IconGlyph class="learn-rank-medal-spark" name="star" :size="18" />
      </span>
      <p :id="messageId" class="overlay-message">{{ message }}</p>
    </div>
    <template #footer>
      <button
        class="btn btn-primary"
        type="button"
        data-autofocus
        data-testid="rank-up-keep-going"
        @click="emit('close')"
      >
        {{ t('learn.keepGoing') }}
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { computed, useId } from 'vue'

import AppOverlay from '@/components/common/AppOverlay.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { learnSharedMessages } from '@/plugins/learnSharedMessages'
import type { RankUp } from '@/types/learn'
import { xpLevelTitleKey } from '@/utils/xpLevel'
import '@/styles/learn-shared.css'

/**
 * "You reached Rank 4 · Committed": a finished lesson (Learn), set or mistakes review (Practice) took the learner past
 * a rank threshold. It names the rank, the XP passed and when the next rank opens, with the streak to keep when there
 * is one; Keep going closes it.
 */
const props = defineProps<{
  rankUp: RankUp
  /** The learner's streak after the result: 0 leaves the streak out. */
  streak: number
  /** What earned it: today's lesson, or today's practice (a set or the mistakes review). */
  source: 'lesson' | 'practice'
}>()

const emit = defineEmits<{ close: [] }>()

const { t } = useLocalMessages({ learn: learnSharedMessages })
const messageId = `${useId()}-message`

const title = computed(() =>
  t('learn.rankUpTitle', { rank: props.rankUp.rank, title: t(xpLevelTitleKey(props.rankUp.rank)) }),
)

const message = computed(() => {
  const passed = t(props.source === 'lesson' ? 'learn.rankUpLesson' : 'learn.rankUpPractice', {
    xp: props.rankUp.rankXp,
  })
  const next = { next: props.rankUp.rank + 1, nextXp: props.rankUp.nextRankXp }
  const ahead =
    props.streak > 0 ? t('learn.rankUpStreak', { ...next, days: props.streak }) : t('learn.rankUpNext', next)
  return `${passed} ${ahead}`
})
</script>
