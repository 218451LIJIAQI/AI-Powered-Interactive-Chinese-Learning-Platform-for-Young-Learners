<template>
  <div class="learn-page">
    <PageHeaderBlock :title="t('learn.pageTitle')" hide-title />

    <DataNotice
      v-if="finished"
      class="learn-notice"
      :success="finishedMessage"
      :success-title="t('learn.lessonCompleteTitle')"
    />

    <ResourceSection class="learn-layout" :loading="path.loading.value" :error="path.error.value" @retry="reloadAll">
      <StatePanel
        v-if="path.data.value && !path.data.value.levels.length"
        tone="empty"
        icon="route"
        spacious
        :title="t('learn.emptyPathTitle')"
        :message="t('learn.emptyPathMessage')"
      />
      <template v-else-if="path.data.value && pinnedUnit && pinnedLevel">
        <div class="learn-path">
          <!-- Pinned: the level and the unit the student is on. Only the road below scrolls. -->
          <header class="learn-path-header">
            <div class="learn-level-band">
              <h2 class="learn-level-title">{{ levelTitle(pinnedLevel) }}</h2>
              <span class="learn-level-meta">{{ levelMeta(pinnedLevel) }}</span>
            </div>
            <LearnUnitBanner :unit="pinnedUnit" here @guidebook="guidebookUnit = $event" />
          </header>

          <div
            ref="scroller"
            class="learn-path-scroll"
            role="region"
            tabindex="0"
            :aria-label="t('learn.pathScrollLabel')"
          >
            <div class="learn-path-road">
              <template v-for="(level, levelIndex) in path.data.value.levels" :key="level.level">
                <div v-if="levelIndex > 0 || level !== pinnedLevel" class="learn-level-band">
                  <h2 class="learn-level-title">{{ levelTitle(level) }}</h2>
                  <span class="learn-level-meta">{{ levelMeta(level) }}</span>
                </div>

                <ul v-if="isLockedLevel(level)" class="learn-level-overview" data-testid="locked-level">
                  <li v-for="unit in level.units" :key="unit.id">
                    <IconGlyph name="lock" :size="14" />
                    <span class="learn-level-overview-title">
                      {{ t('learn.unitShort', { number: unit.number, title: unit.title }) }} ·
                      <span lang="zh-CN">{{ unit.titleZh }}</span>
                    </span>
                    <span class="learn-level-overview-count">{{
                      t('learn.lessonCount', { count: unit.lessonCount }, unit.lessonCount)
                    }}</span>
                  </li>
                </ul>
                <template v-else>
                  <LearnUnitChapter
                    v-for="(unit, unitIndex) in level.units"
                    :key="unit.id"
                    :unit="unit"
                    :is-pinned-unit="unit.id === pinnedUnit.id"
                    :banner-pinned="unit.id === pinnedUnit.id && levelIndex === 0 && unitIndex === 0"
                    @guidebook="guidebookUnit = $event"
                  />
                </template>

                <section
                  v-if="level.checkpoint"
                  class="learn-checkpoint"
                  :class="`is-${level.checkpoint.state.toLowerCase()}`"
                  :aria-labelledby="`learn-checkpoint-${level.level}`"
                  data-testid="checkpoint"
                >
                  <span class="learn-checkpoint-tile" aria-hidden="true"><IconGlyph name="trophy" :size="22" /></span>
                  <div class="learn-checkpoint-copy">
                    <p class="learn-checkpoint-overline">
                      {{ t('learn.checkpointOverline', { level: level.level }) }} ·
                      <span lang="zh-CN">关卡</span>
                    </p>
                    <h3 :id="`learn-checkpoint-${level.level}`" class="learn-checkpoint-title">
                      {{ t('learn.checkpointTitle', { next: level.level + 1 }) }}
                    </h3>
                    <p class="learn-checkpoint-meta">{{ checkpointMeta(level) }}</p>
                  </div>
                  <StatusPill
                    v-if="level.checkpoint.state === 'LOCKED'"
                    :label="t('learn.locked')"
                    tone="outline"
                    icon="lock"
                  />
                  <StatusPill
                    v-else-if="level.checkpoint.state === 'DONE'"
                    :label="t('learn.passed')"
                    tone="success"
                    icon="check"
                  />
                  <RouterLink
                    v-else
                    class="btn btn-accent"
                    :to="{ name: 'student-learn-lesson', params: { lessonId: level.checkpoint.id } }"
                    data-testid="checkpoint-start"
                  >
                    {{ t('learn.startWithXp', { xp: level.checkpoint.xpReward }) }}
                  </RouterLink>
                </section>
              </template>
            </div>
          </div>
        </div>

        <LearnPathRail :path="path.data.value" :today="today.data.value" />
      </template>
    </ResourceSection>

    <GuidebookDrawer :unit="guidebookUnit" @close="guidebookUnit = null" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { learnApi, xpApi } from '@/api/learn'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import GuidebookDrawer from '@/components/learn/GuidebookDrawer.vue'
import LearnPathRail from '@/components/learn/LearnPathRail.vue'
import LearnUnitBanner from '@/components/learn/LearnUnitBanner.vue'
import LearnUnitChapter from '@/components/learn/LearnUnitChapter.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { learnMessages } from '@/plugins/learnMessages'
import type { LessonResult, PathLevel, PathUnit } from '@/types/learn'
import '@/styles/learn.css'

/**
 * The Learn path: the level and the unit the student is on stay pinned, the road of lessons scrolls under them (the
 * following units, the level's checkpoint and the locked level after it), and the side column stays beside it.
 */
const { t } = useLocalMessages({ learn: learnMessages })

const path = useResource(() => learnApi.path())
const today = useResource(() => xpApi.today())
const guidebookUnit = ref<PathUnit | null>(null)
const scroller = ref<HTMLElement | null>(null)

function reloadAll(): void {
  void path.reload()
  void today.reload()
}

// ----- the lesson just finished (handed over by the lesson player in the history state) -----

const finished = ref<LessonResult | null>(readFinishedLesson())

function readFinishedLesson(): LessonResult | null {
  const state = window.history.state as { lessonResult?: LessonResult } | null
  const result = state?.lessonResult ?? null
  if (result) {
    // Shown once: a reload or a later visit does not repeat it.
    window.history.replaceState({ ...state, lessonResult: undefined }, '')
  }
  return result
}

const finishedMessage = computed(() => {
  const result = finished.value
  if (!result) return null
  const parts = [
    result.xp.xpEarned > 0
      ? t('learn.bannerXp', { title: result.title, xp: result.xp.xpEarned })
      : t('learn.bannerDone', { title: result.title }),
  ]
  if (result.nextLesson?.unlockedNow) parts.push(t('learn.bannerUnlocked', { title: result.nextLesson.title }))
  if (result.xp.goalJustReached)
    parts.push(t('learn.bannerGoal', { days: result.xp.currentStreak }, result.xp.currentStreak))
  return parts.join(' ')
})

// ----- what is pinned: the next lesson's unit, else the furthest unit reached -----

const pinnedUnit = computed<PathUnit | null>(() => {
  const data = path.data.value
  if (!data) return null
  const units = data.levels.flatMap((level) => level.units)
  const next = data.nextLesson
  return (
    units.find((unit) => unit.id === next?.unitId) ??
    [...units].reverse().find((unit) => unit.state !== 'LOCKED') ??
    units[0] ??
    null
  )
})

const pinnedLevel = computed<PathLevel | null>(
  () => path.data.value?.levels.find((level) => level.units.some((unit) => unit.id === pinnedUnit.value?.id)) ?? null,
)

// ----- headings -----

function isLockedLevel(level: PathLevel): boolean {
  return level.locked && level.level > 1
}

function levelTitle(level: PathLevel): string {
  return t('learn.levelTitle', {
    level: level.level,
    name: t(level.level === 1 ? 'learn.beginner1' : 'learn.beginner2'),
    hsk: level.hskLevel,
  })
}

function levelMeta(level: PathLevel): string {
  const counts = t('learn.levelMeta', { units: level.units.length, lessons: level.lessonCount })
  return isLockedLevel(level) ? `${t('learn.lockedUntilCheckpoint', { level: level.level - 1 })} · ${counts}` : counts
}

function checkpointMeta(level: PathLevel): string {
  const checkpoint = level.checkpoint
  const first = level.units[0]
  const last = level.units[level.units.length - 1]
  if (!checkpoint || !first || !last) return ''
  const values = {
    questions: checkpoint.questionCount,
    units: t('learn.unitsRange', { from: first.number, to: last.number }),
    xp: checkpoint.xpReward,
  }
  return checkpoint.state === 'LOCKED'
    ? t('learn.checkpointMetaLocked', { ...values, last: last.number })
    : t('learn.checkpointMeta', values)
}

// ----- bring the current stop (or the open checkpoint) into view when it is further down the road -----

watch(
  () => path.data.value,
  async (data) => {
    if (!data) return
    await nextTick()
    const road = scroller.value
    // On a small window the whole page scrolls: it stays at the top while a banner has something to say.
    if (!road || (finished.value && road.scrollHeight <= road.clientHeight)) return
    const target = road.querySelector('.learn-node.is-current button, .learn-checkpoint.is-available')
    if (target && target.getBoundingClientRect().bottom > window.innerHeight) {
      target.scrollIntoView?.({ block: 'center' })
    }
  },
)
</script>
