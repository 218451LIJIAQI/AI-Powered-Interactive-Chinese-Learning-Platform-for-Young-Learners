<template>
  <div class="learn-page">
    <h1 class="sr-only">{{ t('nav.learn') }}</h1>

    <!-- Loading: the map's own skeleton, so the page keeps its shape when the path arrives. -->
    <div v-if="path.loading.value" class="learn-layout" data-testid="learn-loading">
      <p class="sr-only" role="status">{{ t('learn.pathLoading') }}</p>
      <div class="learn-path" aria-hidden="true">
        <header class="learn-path-header">
          <span class="skeleton learn-skeleton-band" />
          <span class="skeleton learn-skeleton-banner" />
        </header>
        <div class="learn-path-scroll">
          <div class="learn-path-road">
            <LearnMapSkeleton />
          </div>
        </div>
      </div>
      <div class="learn-rail is-loading" aria-hidden="true">
        <span class="skeleton learn-skeleton-line is-short" />
        <span class="skeleton learn-skeleton-title" />
        <span class="skeleton learn-skeleton-line" />
        <span class="skeleton learn-skeleton-line is-short" />
        <span class="skeleton learn-skeleton-bar" />
      </div>
    </div>

    <StatePanel
      v-else-if="path.error.value"
      heading-tag="h2"
      tone="error"
      spacious
      :title="t('common.errorTitle')"
      :message="path.error.value"
      @retry="reloadAll"
    />

    <StatePanel
      v-else-if="path.data.value && !pinned"
      heading-tag="h2"
      tone="empty"
      icon="route"
      spacious
      :title="t('learn.emptyPathTitle')"
      :message="t('learn.emptyPathMessage')"
    />

    <div v-else-if="path.data.value && pinned" class="learn-layout">
      <div class="learn-path">
        <!-- Pinned: the level and the unit the learner is on. Only the map below scrolls. -->
        <header class="learn-path-header">
          <h2 class="learn-level-title">{{ hskLevelName(t, pinned.level.hskLevel) }}</h2>
          <LearnUnitBanner :unit="pinned.unit" here @guidebook="guidebookUnit = $event" />
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
              <h2 v-if="levelIndex > 0 || level !== pinned.level" class="learn-level-title">
                {{ hskLevelName(t, level.hskLevel) }}
              </h2>

              <!-- A level behind its checkpoint: what it holds (published units only), each with its lock. -->
              <ul v-if="level.locked" class="learn-level-overview" data-testid="locked-level">
                <li v-for="unit in level.units" :key="unit.id">
                  <IconGlyph name="lock" :size="14" />
                  <span class="learn-level-overview-title">
                    {{ t('learn.unitShort', { number: unit.number, title: unit.title }) }} ·
                    <span lang="zh-CN">{{ unit.titleZh }}</span>
                  </span>
                </li>
              </ul>
              <template v-else>
                <section
                  v-for="(unit, unitIndex) in level.units"
                  :id="`learn-unit-${unit.number}`"
                  :key="unit.id"
                  class="learn-unit"
                  :aria-labelledby="`learn-unit-${unit.number}-title`"
                >
                  <!-- The pinned banner stands right above the first unit, so its heading only speaks to assistive
                       technology. -->
                  <h3
                    v-if="unit.id === pinned.unit.id && levelIndex === 0 && unitIndex === 0"
                    :id="`learn-unit-${unit.number}-title`"
                    class="sr-only"
                  >
                    {{ unit.title }} · <span lang="zh-CN">{{ unit.titleZh }}</span>
                  </h3>
                  <LearnUnitBanner
                    v-else
                    :unit="unit"
                    :title-id="`learn-unit-${unit.number}-title`"
                    :show-guidebook="unit.id !== pinned.unit.id"
                    @guidebook="guidebookUnit = $event"
                  />
                  <p class="sr-only">{{ t('learn.xOfY', { done: unit.completedCount, total: unit.lessonCount }) }}</p>
                  <LearnLevelMap :unit="unit" :newly-unlocked="newlyUnlocked" />
                </section>
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
                  <!-- With the Chinese word for it, unless the page already speaks Chinese. -->
                  <p class="learn-checkpoint-overline">
                    {{ t('learn.checkpointOverline', { hsk: level.hskLevel })
                    }}<span v-if="locale !== 'zh-CN'"> · <span lang="zh-CN">关卡</span></span>
                  </p>
                  <h3 :id="`learn-checkpoint-${level.level}`" class="learn-checkpoint-title">
                    {{ t('learn.checkpointTitle', { hsk: level.hskLevel + 1 }) }}
                  </h3>
                  <p class="learn-checkpoint-meta">{{ checkpointMeta(level) }}</p>
                </div>
                <p v-if="level.checkpoint.state === 'LOCKED' && level.units.length" class="learn-banner-note">
                  {{ t('learn.finishUnitToUnlock', { number: level.units.at(-1)!.number }) }}
                </p>
                <StatusPill
                  v-else-if="level.checkpoint.state === 'DONE'"
                  :label="t('learn.passed')"
                  tone="success"
                  icon="check"
                />
                <RouterLink
                  v-else-if="level.checkpoint.state === 'AVAILABLE'"
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

      <LearnPathRail
        :path="path.data.value"
        :today="today.data.value"
        :today-error="today.error.value"
        @retry-today="today.reload"
      />
    </div>

    <GuidebookDrawer v-if="guidebookUnit" :unit="guidebookUnit" @close="guidebookUnit = null" />
    <AppToast :message="toast" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { learnApi, xpApi } from '@/api/learn'
import AppToast from '@/components/common/AppToast.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import GuidebookDrawer from '@/components/learn/GuidebookDrawer.vue'
import LearnLevelMap from '@/components/learn/LearnLevelMap.vue'
import LearnMapSkeleton from '@/components/learn/LearnMapSkeleton.vue'
import LearnPathRail from '@/components/learn/LearnPathRail.vue'
import LearnUnitBanner from '@/components/learn/LearnUnitBanner.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { useToast } from '@/composables/useToast'
import { learnMessages } from '@/plugins/learnMessages'
import type { PathLevel, PathUnit } from '@/types/learn'
import { hskLevelName } from '@/utils/hsk'
import '@/styles/learn-shared.css'
import '@/styles/learn.css'
import { takeFinishedLesson } from './finishedLesson'

/**
 * The Learn path as a level map: the level and the unit the learner is on stay pinned, the units' maps scroll under
 * them (with each level's checkpoint and the overview of the locked level after it), and the side column stays beside
 * them. Back from a lesson, a toast says what it earned and unlocked, and the map shows the new level open.
 */
const { t, locale } = useLocalMessages({ learn: learnMessages })
const { toast, showToast } = useToast()

const path = useResource(learnApi.path)
const today = useResource(xpApi.today)
const guidebookUnit = ref<PathUnit | null>(null)
const scroller = ref<HTMLElement | null>(null)

function reloadAll(): void {
  void path.reload()
  void today.reload()
}

const finished = takeFinishedLesson()

/** The level the finished lesson opened: it pops in once on the map. */
const newlyUnlocked = finished?.nextLesson?.unlockedNow ? finished.nextLesson.title : null

if (finished) {
  const xp = finished.xp.xpEarned + finished.xp.bonusXp
  const checkpoint = finished.kind === 'CHECKPOINT'
  const parts = [
    xp > 0
      ? t(checkpoint ? 'learn.toastCheckpointXp' : 'learn.toastLessonXp', { xp })
      : t(checkpoint ? 'learn.toastCheckpoint' : 'learn.toastLesson'),
  ]
  if (newlyUnlocked) parts.push(t('learn.toastUnlocked', { title: newlyUnlocked }))
  // A toast over the page, not a banner above it: the map keeps its height.
  showToast(parts.join(' '))
}

// What is pinned: the next lesson's unit, else the furthest unit reached.

const pinned = computed(() => {
  const data = path.data.value
  if (!data) return null
  let fallback: { unit: PathUnit; level: PathLevel } | null = null
  for (const level of data.levels) {
    for (const unit of level.units) {
      if (unit.id === data.nextLesson?.unitId) return { unit, level }
      if (!fallback || unit.state !== 'LOCKED') fallback = { unit, level }
    }
  }
  return fallback
})

function checkpointMeta(level: PathLevel): string {
  const checkpoint = level.checkpoint
  const first = level.units[0]
  const last = level.units.at(-1)
  if (!checkpoint || !first || !last) return ''
  const units =
    first.number === last.number
      ? t('learn.unitNumber', { number: first.number })
      : t('learn.unitsRange', { from: first.number, to: last.number })
  return t('learn.checkpointMeta', {
    questions: checkpoint.questionCount,
    units,
    xp: checkpoint.xpReward,
  })
}

// Bring the current level (or the open checkpoint) into view when it is further down the map.

watch(path.data, async (data) => {
  if (!data) return
  await nextTick()
  const target = scroller.value?.querySelector(
    '.learn-level.is-current .learn-level-button, .learn-checkpoint.is-available',
  )
  if (target && target.getBoundingClientRect().bottom > window.innerHeight) {
    target.scrollIntoView?.({ block: 'center' })
  }
})
</script>
