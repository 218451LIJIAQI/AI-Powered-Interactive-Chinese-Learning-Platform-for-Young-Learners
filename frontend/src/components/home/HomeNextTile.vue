<template>
  <section class="home-next" :class="{ 'is-done': !tile }" aria-labelledby="home-next-title" data-testid="home-next">
    <template v-if="tile">
      <div class="home-next-body">
        <div class="home-next-copy">
          <p class="page-eyebrow">{{ tile.eyebrow }}</p>
          <h2 id="home-next-title" class="home-next-title">
            <template v-if="tile.unitIntro">
              {{ tile.next.unitTitle }} · <span class="hanzi" lang="zh-CN">{{ tile.next.unitTitleZh }}</span>
            </template>
            <template v-else>{{ tile.next.title }}</template>
          </h2>
          <p class="home-next-meta">{{ tile.metaLine }}</p>
        </div>
        <ol class="home-steps" :aria-label="tile.stepsLabel">
          <li
            v-for="stop in tile.stops"
            :key="stop.id"
            :class="{ 'is-done': stop.state === 'DONE', 'is-current': stop.current }"
            :aria-current="stop.current ? 'step' : undefined"
          >
            <span class="sr-only">{{ stop.label }}</span>
            <span class="home-step" aria-hidden="true">
              <IconGlyph v-if="stop.state === 'DONE'" name="check" :size="16" />
              <template v-else>{{ stop.position }}</template>
            </span>
          </li>
        </ol>
        <!-- Home's one next-step action: amber, like Start on Learn. -->
        <RouterLink
          class="btn btn-accent btn-lg"
          :to="{ name: 'student-learn-lesson', params: { lessonId: tile.next.lessonId } }"
          data-testid="home-continue"
        >
          {{ tile.actionLabel }}
          <IconGlyph name="chevronRight" :size="18" />
        </RouterLink>
      </div>
      <!-- The lesson's character on practice paper, with what it means. -->
      <figure class="home-next-show" :aria-hidden="!tile.next.phrase || undefined">
        <span class="hanzi-tile home-next-tile" lang="zh-CN" aria-hidden="true">{{ tile.next.character ?? '学' }}</span>
        <figcaption v-if="tile.next.phrase" class="home-next-phrase">
          <span class="cjk-text" lang="zh-CN">{{ tile.next.phrase.hanzi }}</span>
          <span>{{ tile.phraseLine }}</span>
        </figcaption>
      </figure>
    </template>
    <div v-else class="home-next-body">
      <span class="plan-icon-tile is-lg" aria-hidden="true"><IconGlyph name="celebrate" :size="28" /></span>
      <div class="home-next-copy">
        <h2 id="home-next-title" class="home-next-title">{{ t('studentHome.pathDoneTitle') }}</h2>
        <p class="home-next-meta">{{ t('studentHome.pathDoneText') }}</p>
      </div>
      <RouterLink class="btn btn-primary" :to="{ name: 'student-practice' }">
        {{ t('studentHome.openPractice') }}
      </RouterLink>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { studentHomeMessages } from '@/plugins/studentHomeMessages'
import type { HomeNextLesson } from '@/types/plan'

/**
 * Home's one big action: the next lesson (in the plan, or the next open Learn lesson without one, past it or once the
 * plan has ended) with its key character on a 田字格 tile and what it means, the unit's lessons as numbered steps (a
 * check when done) and Start lesson. On the first day it introduces the whole first unit ("Pinyin and tones · 拼音")
 * with its Start button instead (a quick review after a head start). A level checkpoint, the gate of its level, is
 * shown with the level: the level's units as the steps.
 */
const props = defineProps<{
  next: HomeNextLesson | null
  /** The learner has a plan that is still running (its date has not passed). */
  hasPlan: boolean
  firstTime: boolean
}>()

const { t } = useLocalMessages({ studentHome: studentHomeMessages })

function stopLabel(stop: HomeNextLesson['stops'][number], isCheckpoint: boolean): string {
  if (isCheckpoint) {
    return t(stop.state === 'DONE' ? 'studentHome.unitStopDone' : 'studentHome.unitStopOpen', { number: stop.position })
  }
  if (stop.state === 'DONE') return t('studentHome.stopDone', { position: stop.position })
  if (stop.current) return t('studentHome.stopCurrent', { position: stop.position })
  return t('studentHome.stopLocked', { position: stop.position })
}

const tile = computed(() => {
  const next = props.next
  if (!next) return null
  const isCheckpoint = next.kind === 'CHECKPOINT'
  const unitIntro = props.firstTime && !isCheckpoint
  const inPlan = props.hasPlan && next.inPlan

  const stepsLabel = isCheckpoint
    ? t('studentHome.levelPathLabel', { level: next.level })
    : t('studentHome.unitPathLabel', { number: next.unitNumber })
  const stops = next.stops.map((stop) => ({ ...stop, label: stopLabel(stop, isCheckpoint) }))

  if (unitIntro) {
    const phraseLine = next.phrase
      ? t(next.quickReview ? 'studentHome.youKnowThis' : 'studentHome.firstWord', { meaning: next.phrase.meaning })
      : ''
    const metaLine = next.quickReview
      ? t('studentHome.reviewUnit', { count: next.stops.length, minutes: next.unitMinutesLeft })
      : t('studentHome.startUnitCopy', { count: next.stops.length, minutes: next.minutes })
    return {
      next,
      unitIntro: true,
      eyebrow: t(inPlan ? 'studentHome.firstInPlan' : 'studentHome.startHere', { number: next.unitNumber }),
      stepsLabel,
      metaLine,
      phraseLine,
      actionLabel: t('studentHome.startUnit', { number: next.unitNumber }),
      stops,
    }
  }

  const eyebrow = isCheckpoint
    ? t(inPlan ? 'studentHome.checkpointInPlan' : 'studentHome.checkpointInLearn', { level: next.level })
    : t(inPlan ? 'studentHome.nextInPlan' : 'studentHome.nextInLearn', {
        number: next.unitNumber,
        title: next.unitTitle,
      })

  return {
    next,
    unitIntro: false,
    eyebrow,
    stepsLabel,
    metaLine: t(isCheckpoint ? 'studentHome.checkpointMeta' : 'studentHome.lessonMeta', {
      minutes: next.minutes,
      xp: next.xpReward,
    }),
    phraseLine: next.phrase ? t('studentHome.youllLearn', { meaning: next.phrase.meaning }) : '',
    actionLabel: t(isCheckpoint ? 'studentHome.takeCheckpoint' : 'studentHome.startLesson'),
    stops,
  }
})
</script>
