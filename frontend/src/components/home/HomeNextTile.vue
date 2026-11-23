<template>
  <section class="home-next" :class="{ 'is-done': !next }" aria-labelledby="home-next-title" data-testid="home-next">
    <template v-if="next">
      <div class="home-next-body">
        <div class="home-next-copy">
          <p class="page-eyebrow">{{ eyebrow }}</p>
          <h2 id="home-next-title" class="home-next-title">
            <template v-if="firstTime">
              {{ next.unitTitle }} · <span class="hanzi" lang="zh-CN">{{ next.unitTitleZh }}</span>
            </template>
            <template v-else>{{ next.title }}</template>
          </h2>
          <p class="home-next-meta">{{ metaLine }}</p>
        </div>
        <ol class="home-steps" :aria-label="stepsLabel">
          <li
            v-for="stop in next.stops"
            :key="stop.id"
            :class="{ 'is-done': stop.state === 'DONE', 'is-current': stop.current }"
            :aria-current="stop.current ? 'step' : undefined"
          >
            <span class="sr-only">{{ stopLabel(stop) }}</span>
            <span class="home-step" aria-hidden="true">
              <IconGlyph v-if="stop.state === 'DONE'" name="check" :size="16" />
              <template v-else>{{ stop.position }}</template>
            </span>
          </li>
        </ol>
        <!-- Home's one next-step action: amber, like Continue on Learn and Start review on Practice. -->
        <RouterLink
          class="btn btn-accent btn-lg"
          :to="{ name: 'student-learn-lesson', params: { lessonId: next.lessonId } }"
          data-testid="home-continue"
        >
          {{ actionLabel }}
          <IconGlyph name="chevronRight" :size="18" />
        </RouterLink>
      </div>
      <!-- The lesson's character on practice paper, with what it means. -->
      <figure class="home-next-show" :aria-hidden="!next.phrase || undefined">
        <span class="hanzi-tile home-next-tile" lang="zh-CN" aria-hidden="true">{{ next.character ?? '学' }}</span>
        <figcaption v-if="next.phrase" class="home-next-phrase">
          <span class="cjk-text" lang="zh-CN">{{ next.phrase.hanzi }}</span>
          <span>{{ phraseLine }}</span>
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

/** A checkpoint closes its level: it is named by the level, and its steps are the level's units. */
const isCheckpoint = computed(() => props.next?.kind === 'CHECKPOINT')

/** "In your plan" only for a unit the running plan has; otherwise the lesson is simply next in Learn. */
const eyebrow = computed(() => {
  const next = props.next
  if (!next) return ''
  const inPlan = props.hasPlan && next.inPlan
  if (props.firstTime) {
    return t(inPlan ? 'studentHome.firstInPlan' : 'studentHome.startHere', { number: next.unitNumber })
  }
  if (isCheckpoint.value) {
    return t(inPlan ? 'studentHome.checkpointInPlan' : 'studentHome.checkpointInLearn', { level: next.level })
  }
  return t(inPlan ? 'studentHome.nextInPlan' : 'studentHome.nextInLearn', {
    number: next.unitNumber,
    title: next.unitTitle,
  })
})

const stepsLabel = computed(() => {
  const next = props.next
  if (!next) return ''
  return isCheckpoint.value
    ? t('studentHome.levelPathLabel', { level: next.level })
    : t('studentHome.unitPathLabel', { number: next.unitNumber })
})

/** The steps already show where the lesson sits in its unit, so the line says only how long it takes. */
const metaLine = computed(() => {
  const next = props.next
  if (!next) return ''
  if (props.firstTime) {
    return next.quickReview
      ? t('studentHome.reviewUnit', { count: next.stops.length, minutes: next.unitMinutesLeft })
      : t('studentHome.startUnitCopy', { count: next.stops.length, minutes: next.minutes })
  }
  return t(isCheckpoint.value ? 'studentHome.checkpointMeta' : 'studentHome.lessonMeta', {
    minutes: next.minutes,
    xp: next.xpReward,
  })
})

const phraseLine = computed(() => {
  const phrase = props.next?.phrase
  if (!phrase) return ''
  if (props.firstTime) {
    return t(props.next?.quickReview ? 'studentHome.youKnowThis' : 'studentHome.firstWord', { meaning: phrase.meaning })
  }
  return t('studentHome.youllLearn', { meaning: phrase.meaning })
})

const actionLabel = computed(() => {
  const next = props.next
  if (!next) return ''
  if (props.firstTime) return t('studentHome.startUnit', { number: next.unitNumber })
  return isCheckpoint.value ? t('studentHome.takeCheckpoint') : t('studentHome.startLesson')
})

function stopLabel(stop: HomeNextLesson['stops'][number]): string {
  if (isCheckpoint.value) {
    return t(stop.state === 'DONE' ? 'studentHome.unitStopDone' : 'studentHome.unitStopOpen', { number: stop.position })
  }
  if (stop.state === 'DONE') return t('studentHome.stopDone', { position: stop.position })
  if (stop.current) return t('studentHome.stopCurrent', { position: stop.position })
  return t('studentHome.stopLocked', { position: stop.position })
}
</script>
