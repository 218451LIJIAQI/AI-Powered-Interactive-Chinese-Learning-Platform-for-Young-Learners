<template>
  <div class="learn-page page-stack">
    <PageHeaderBlock :title="t('learn.pageTitle')" :description="t('learn.pageLede')" />

    <DataNotice v-if="finished" :success="finishedMessage" :success-title="t('learn.lessonCompleteTitle')" />

    <ResourceSection class="learn-layout" :loading="path.loading.value" :error="path.error.value" @retry="reloadAll">
      <div v-if="path.data.value" class="learn-main">
        <StatePanel
          v-if="!path.data.value.levels.length"
          tone="empty"
          icon="route"
          spacious
          :title="t('learn.emptyPathTitle')"
          :message="t('learn.emptyPathMessage')"
        />
        <div v-for="level in path.data.value.levels" :key="level.level" class="learn-level">
          <div class="learn-level-head">
            <h2 class="learn-level-title">{{ levelTitle(level) }}</h2>
            <span class="learn-level-meta">
              <IconGlyph v-if="isLockedLevel(level)" name="lock" :size="14" />
              {{
                isLockedLevel(level)
                  ? t('learn.lockedUntilCheckpoint', { level: level.level - 1 })
                  : t('learn.levelMeta', { units: level.units.length, lessons: level.lessonCount })
              }}
            </span>
          </div>

          <ul v-if="isLockedLevel(level)" class="learn-level-preview" data-testid="locked-level">
            <li v-for="unit in level.units" :key="unit.id">
              <span class="learn-level-preview-number" aria-hidden="true">{{ unit.number }}</span>
              <span class="learn-level-preview-title">
                {{ t('learn.unitShort', { number: unit.number, title: unit.title }) }} ·
                <span class="cjk-text" lang="zh-CN">{{ unit.titleZh }}</span>
              </span>
              <span class="learn-level-preview-count">{{
                t('learn.lessonCount', { count: unit.lessonCount }, unit.lessonCount)
              }}</span>
            </li>
          </ul>
          <template v-else>
            <LearnUnitChapter
              v-for="unit in level.units"
              :key="unit.id"
              :ref="(component) => setChapterRef(unit, component)"
              :unit="unit"
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
            <span
              class="seal learn-checkpoint-seal"
              :class="{
                'is-outline': level.checkpoint.state === 'AVAILABLE',
                'is-muted': level.checkpoint.state === 'LOCKED',
              }"
              lang="zh-CN"
              aria-hidden="true"
              >关</span
            >
            <div class="learn-checkpoint-copy">
              <p class="learn-checkpoint-overline">
                {{ t('learn.checkpointOverline', { level: level.level }) }} ·
                <span class="cjk-text" lang="zh-CN">关卡</span>
              </p>
              <h3 :id="`learn-checkpoint-${level.level}`" class="learn-checkpoint-title">
                {{ t('learn.checkpointTitle', { next: level.level + 1 }) }}
              </h3>
              <p class="learn-checkpoint-meta">
                {{
                  t('learn.checkpointMeta', {
                    questions: level.checkpoint.questionCount,
                    units: unitRange(level),
                    xp: level.checkpoint.xpReward,
                  })
                }}
              </p>
            </div>
            <StatusPill
              v-if="level.checkpoint.state === 'LOCKED'"
              :label="t('learn.locked')"
              tone="neutral"
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
              class="btn btn-primary"
              :to="{ name: 'student-learn-lesson', params: { lessonId: level.checkpoint.id } }"
              data-testid="checkpoint-start"
            >
              {{ t('learn.startWithXp', { xp: level.checkpoint.xpReward }) }}
            </RouterLink>
          </section>
        </div>
      </div>

      <div v-if="path.data.value" class="learn-side">
        <LearnPathRail :path="path.data.value" :today="today.data.value" />
      </div>
    </ResourceSection>

    <GuidebookDrawer :unit="guidebookUnit" @close="guidebookUnit = null" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch, type ComponentPublicInstance } from 'vue'
import { RouterLink } from 'vue-router'

import { learnApi, xpApi } from '@/api/learn'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import GuidebookDrawer from '@/components/learn/GuidebookDrawer.vue'
import LearnPathRail from '@/components/learn/LearnPathRail.vue'
import LearnUnitChapter from '@/components/learn/LearnUnitChapter.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { learnMessages } from '@/plugins/learnMessages'
import type { LessonResult, PathLevel, PathUnit } from '@/types/learn'
import '@/styles/learn.css'

/**
 * The Learn path as the table of contents of a book: each level a part, each unit a chapter (the open one shows its
 * lessons on an ink thread), each level closed by its checkpoint; a level that is still locked is a quiet list of
 * what comes next. The rail beside it says what the next lessons unlock, how far the path is and today's goal.
 */
const { t } = useLocalMessages({ learn: learnMessages })

const path = useResource(() => learnApi.path())
const today = useResource(() => xpApi.today())
const guidebookUnit = ref<PathUnit | null>(null)

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

function unitRange(level: PathLevel): string {
  const numbers = level.units.map((unit) => unit.number)
  return numbers.length ? t('learn.unitsRange', { from: numbers[0], to: numbers[numbers.length - 1] }) : ''
}

// ----- bring the current lesson into view when it is further down the path -----

const chapters = new Map<number, InstanceType<typeof LearnUnitChapter>>()

function setChapterRef(unit: PathUnit, component: Element | ComponentPublicInstance | null): void {
  if (component) chapters.set(unit.id, component as InstanceType<typeof LearnUnitChapter>)
  else chapters.delete(unit.id)
}

watch(
  () => path.data.value?.nextLesson,
  async (next) => {
    if (!next || next.unitNumber <= 1) return
    await nextTick()
    const node = chapters.get(next.unitId)?.currentNode()
    node?.scrollIntoView({ block: 'center' })
  },
)
</script>
