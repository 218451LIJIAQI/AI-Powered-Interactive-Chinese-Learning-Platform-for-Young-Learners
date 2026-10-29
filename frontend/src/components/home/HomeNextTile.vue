<template>
  <section class="home-next" aria-labelledby="home-next-title" data-testid="home-next">
    <template v-if="next">
      <span class="hanzi-tile home-next-tile" lang="zh-CN" aria-hidden="true">{{ next.character ?? '学' }}</span>
      <div class="home-next-copy">
        <p class="home-next-eyebrow">{{ eyebrow }}</p>
        <h2 id="home-next-title" class="home-next-title">
          <template v-if="showUnit">
            {{ next.unitTitle }} · <span class="cjk-text" lang="zh-CN">{{ next.unitTitleZh }}</span>
          </template>
          <template v-else>{{ next.title }}</template>
        </h2>
        <p class="home-next-meta">{{ metaLine }}</p>
        <p v-if="next.phrase" class="home-next-phrase">
          <span class="hanzi" lang="zh-CN">{{ next.phrase.hanzi }}</span>
          <span>{{ phraseLine }}</span>
        </p>
        <div class="home-next-actions">
          <!-- Home's one hero action: yellow, like Continue on Learn and Start review on Practice. -->
          <RouterLink
            class="btn btn-accent btn-lg"
            :to="{ name: 'student-learn-lesson', params: { lessonId: next.lessonId } }"
            data-testid="home-continue"
          >
            {{ actionLabel }}
          </RouterLink>
          <RouterLink v-if="!showUnit" class="btn btn-link" :to="pathRoute">
            {{ t('studentHome.seeMyPath') }}<IconGlyph name="chevronRight" :size="14" />
          </RouterLink>
        </div>
      </div>
      <div class="home-next-unit">
        <ol class="home-next-stops" :aria-label="t('studentHome.unitPathLabel', { number: next.unitNumber })">
          <li
            v-for="stop in next.stops"
            :key="stop.id"
            :class="{ 'is-done': stop.state === 'DONE', 'is-current': stop.current }"
            :aria-label="stopLabel(stop)"
          >
            <span
              class="seal home-next-seal"
              :class="{ 'is-outline': stop.current, 'is-muted': stop.state !== 'DONE' && !stop.current }"
              lang="zh-CN"
              aria-hidden="true"
              >{{ hanziNumeral(stop.position) }}</span
            >
          </li>
        </ol>
        <p class="home-next-progress">
          {{ t('studentHome.doneOf', { done: next.completedInUnit, total: next.stops.length }) }}
        </p>
      </div>
    </template>
    <template v-else>
      <span class="hanzi-tile home-next-tile" lang="zh-CN" aria-hidden="true">棒</span>
      <div class="home-next-copy">
        <h2 id="home-next-title" class="home-next-title">{{ t('studentHome.pathDoneTitle') }}</h2>
        <p class="home-next-meta">{{ t('studentHome.pathDoneText') }}</p>
        <div class="home-next-actions">
          <RouterLink class="btn btn-primary btn-lg" :to="{ name: 'student-practice' }">
            {{ t('studentHome.openPractice') }}
          </RouterLink>
        </div>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { hanziNumeral } from '@/components/learn/sealGlyphs'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { studentHomeMessages } from '@/plugins/studentHomeMessages'
import type { HomeNextLesson } from '@/types/plan'

/**
 * Home's one big action: the next lesson (in the plan, or the next open Learn lesson without one) with its key
 * character on a 田字格 tile, the phrase it teaches and Continue; under it the unit's lessons as a row of seals
 * (stamped when done, outlined for the next one). On the first day it introduces the whole first unit instead (a
 * quick review after a head start).
 */
const props = defineProps<{
  next: HomeNextLesson | null
  hasPlan: boolean
  firstTime: boolean
}>()

const { t } = useLocalMessages({ studentHome: studentHomeMessages })

/** The first day shows the unit ("Say hello · 你好") and its Start button; later days show the lesson. */
const showUnit = computed(() => props.firstTime)

const eyebrow = computed(() => {
  const next = props.next
  if (!next) return ''
  if (props.firstTime) {
    return props.hasPlan
      ? t('studentHome.firstInPlan', { number: next.unitNumber })
      : t('studentHome.startHere', { number: next.unitNumber })
  }
  return props.hasPlan
    ? t('studentHome.nextInPlan', { number: next.unitNumber, title: next.unitTitle })
    : t('studentHome.nextOnPath', { number: next.unitNumber, title: next.unitTitle })
})

const metaLine = computed(() => {
  const next = props.next
  if (!next) return ''
  if (props.firstTime) {
    return next.quickReview
      ? t('studentHome.reviewUnit', {
          number: next.unitNumber,
          count: next.stops.length,
          minutes: next.unitMinutesLeft,
        })
      : t('studentHome.startUnitCopy', { count: next.stops.length, minutes: next.minutes })
  }
  if (next.kind === 'CHECKPOINT') {
    return t('studentHome.checkpointMeta', { minutes: next.minutes, xp: next.xpReward })
  }
  return t('studentHome.lessonMeta', {
    position: next.position,
    total: next.unitLessonCount,
    minutes: next.minutes,
    xp: next.xpReward,
  })
})

const phraseLine = computed(() => {
  const phrase = props.next?.phrase
  if (!phrase) return ''
  if (props.firstTime) {
    return props.next?.quickReview
      ? t('studentHome.youKnowThis', { meaning: phrase.meaning })
      : t('studentHome.firstWord', { meaning: phrase.meaning })
  }
  return t('studentHome.youllLearn', { meaning: phrase.meaning })
})

const actionLabel = computed(() => {
  const next = props.next
  if (!next) return ''
  if (showUnit.value) return t('studentHome.startUnit', { number: next.unitNumber })
  return next.kind === 'CHECKPOINT' ? t('studentHome.takeCheckpoint') : t('studentHome.continueLesson')
})

const pathRoute = computed<RouteLocationRaw>(() =>
  props.hasPlan ? { name: 'student-plan' } : { name: 'student-learn' },
)

function stopLabel(stop: HomeNextLesson['stops'][number]): string {
  if (stop.state === 'DONE') return t('studentHome.stopDone', { position: stop.position })
  if (stop.current) return t('studentHome.stopCurrent', { position: stop.position })
  return t('studentHome.stopLocked', { position: stop.position })
}
</script>
