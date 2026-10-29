<template>
  <section
    :id="`learn-unit-${unit.number}`"
    class="learn-unit"
    :class="[`is-${unit.state.toLowerCase()}`, { 'is-expanded': expanded }]"
    :aria-labelledby="`${baseId}-title`"
  >
    <header class="learn-unit-head">
      <span v-if="unit.state === 'DONE'" class="seal learn-unit-seal" lang="zh-CN" aria-hidden="true">完</span>
      <span v-else class="learn-unit-numeral" aria-hidden="true">{{ unit.number }}</span>
      <div class="learn-unit-copy">
        <p class="learn-unit-overline">{{ overline }}</p>
        <h3 :id="`${baseId}-title`" class="learn-unit-title">
          {{ unit.title }} · <span class="cjk-text" lang="zh-CN">{{ unit.titleZh }}</span>
        </h3>
        <p v-if="unit.state === 'LOCKED'" class="learn-unit-status">
          <IconGlyph name="lock" :size="14" />
          {{
            unit.unlockAfterUnit
              ? t('learn.finishUnitToUnlock', { number: unit.unlockAfterUnit })
              : t('learn.lessonCount', { count: unit.lessonCount }, unit.lessonCount)
          }}
        </p>
        <div v-else class="learn-unit-status">
          <ProgressBar
            v-if="unit.state === 'ACTIVE'"
            class="is-sm learn-unit-bar"
            :progress="ratioPercent(unit.completedCount, unit.lessonCount)"
            :label="t('learn.unitProgress', { number: unit.number })"
          />
          <span>{{ t('learn.xOfY', { done: unit.completedCount, total: unit.lessonCount }) }}</span>
        </div>
        <!-- Each title stays whole; the separator stays with the title before it, so lines break only between titles. -->
        <p v-if="unit.state === 'LOCKED'" class="learn-unit-teaser">
          <template v-for="(stop, index) in unit.lessons" :key="stop.id">
            <span class="learn-unit-teaser-item"
              >{{ stop.title }}{{ index < unit.lessons.length - 1 ? '\u00a0·' : '' }}</span
            >{{ ' ' }}
          </template>
        </p>
      </div>
      <div v-if="unit.state !== 'LOCKED'" class="learn-unit-tools">
        <button
          class="btn btn-ghost btn-sm"
          type="button"
          :data-testid="`guidebook-${unit.number}`"
          @click="emit('guidebook', unit)"
        >
          <IconGlyph name="learn" :size="16" />{{ t('learn.guidebook') }}
        </button>
        <button
          class="btn btn-ghost btn-sm learn-unit-toggle"
          type="button"
          :aria-expanded="expanded"
          :aria-controls="`${baseId}-lessons`"
          :data-testid="`unit-toggle-${unit.number}`"
          @click="expanded = !expanded"
        >
          {{ expanded ? t('learn.hideLessons') : t('learn.showLessons') }}
          <IconGlyph name="chevronDown" :size="16" />
        </button>
      </div>
    </header>

    <ol
      v-if="expanded"
      :id="`${baseId}-lessons`"
      class="learn-lessons"
      :aria-label="t('learn.unitLessons', { number: unit.number })"
    >
      <li
        v-for="stop in unit.lessons"
        :key="stop.id"
        class="learn-lesson"
        :class="[`is-${stop.state.toLowerCase()}`, { 'is-current': stop.current, 'is-open': openStopId === stop.id }]"
      >
        <div class="learn-lesson-row">
          <button
            :ref="(el) => setNodeRef(stop.id, el)"
            class="learn-lesson-button"
            type="button"
            :aria-expanded="openStopId === stop.id"
            :aria-controls="`${baseId}-card-${stop.id}`"
            :aria-label="stopLabel(stop)"
            :data-testid="`stop-${stop.id}`"
            @click="toggle(stop.id)"
          >
            <span class="seal learn-lesson-seal" :class="sealClass(stop)" lang="zh-CN" aria-hidden="true">{{
              hanziNumeral(stop.position)
            }}</span>
            <span class="learn-lesson-copy">
              <span class="learn-lesson-title">{{ stop.title }}</span>
              <span class="learn-lesson-meta">
                <IconGlyph v-if="stop.state === 'LOCKED'" name="lock" :size="12" />{{ rowMeta(stop) }}
              </span>
            </span>
            <!-- The current lesson shows Continue in the chevron's place, so the chevrons keep one column. -->
            <IconGlyph v-if="!showsContinue(stop)" class="learn-lesson-chevron" name="chevronDown" :size="16" />
          </button>
          <RouterLink
            v-if="showsContinue(stop)"
            class="btn btn-accent learn-lesson-continue"
            :to="{ name: 'student-learn-lesson', params: { lessonId: stop.id } }"
            :data-testid="`continue-${stop.id}`"
          >
            {{ t('learn.continue') }}
          </RouterLink>
        </div>

        <div
          v-if="openStopId === stop.id"
          :id="`${baseId}-card-${stop.id}`"
          class="learn-lesson-card"
          role="dialog"
          :aria-label="stop.title"
          @keydown.esc.stop="close(stop.id)"
        >
          <p class="learn-lesson-card-meta">
            {{
              t('learn.stopMeta', {
                position: stop.position,
                total: unit.lessonCount,
                unit: unit.number,
                minutes: stop.minutes,
              })
            }}
          </p>
          <p v-if="stop.state === 'LOCKED'" class="learn-lesson-card-locked">
            <IconGlyph name="lock" :size="14" />
            {{ t('learn.finishToUnlock', { title: stop.unlockAfter ?? '' }) }}
          </p>
          <RouterLink
            v-else
            class="btn btn-sm"
            :class="stop.state === 'DONE' ? 'btn-secondary' : 'btn-primary'"
            :to="{ name: 'student-learn-lesson', params: { lessonId: stop.id } }"
            :data-testid="`start-${stop.id}`"
          >
            {{ stop.state === 'DONE' ? t('learn.practiseAgain') : t('learn.startWithXp', { xp: stop.xpReward }) }}
          </RouterLink>
        </div>
      </li>
    </ol>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, useId, type ComponentPublicInstance } from 'vue'
import { RouterLink } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { learnMessages } from '@/plugins/learnMessages'
import type { PathStop, PathUnit } from '@/types/learn'
import { ratioPercent } from '@/utils/format'

import { hanziNumeral } from './sealGlyphs'

/**
 * One unit of the Learn path as a chapter of a book: its number, title and progress, then its lessons on an ink
 * thread, each a seal (stamped when done, outlined in purple for the current one, muted while locked). The open unit
 * shows its lessons; a finished unit is stamped 完 and folds away, a locked one lists what it teaches and why it is
 * locked. A lesson opens its card in place: Start (+XP), Practise again, or what to finish first.
 */
interface Props {
  unit: PathUnit
}

const props = defineProps<Props>()

const emit = defineEmits<{
  guidebook: [unit: PathUnit]
}>()

const { t } = useLocalMessages({ learn: learnMessages })
const baseId = useId()

/** The unit being worked on starts open; finished and locked units start folded. */
const expanded = ref(props.unit.state === 'ACTIVE')

/** "Unit 2", "Unit 2 · Locked" or "Unit 1 · done". */
const overline = computed(() => {
  const number = t('learn.unitNumber', { number: props.unit.number })
  if (props.unit.state === 'LOCKED') return `${number} · ${t('learn.locked')}`
  if (props.unit.state === 'DONE') return `${number} · ${t('learn.unitDone')}`
  return number
})

function showsContinue(stop: PathStop): boolean {
  return stop.current && stop.state !== 'LOCKED'
}

function sealClass(stop: PathStop): Record<string, boolean> {
  return {
    'is-outline': stop.state === 'AVAILABLE',
    'is-muted': stop.state === 'LOCKED',
  }
}

function kindPrefix(stop: PathStop): string {
  if (stop.kind === 'BONUS') return `${t('learn.kindBonus')} · `
  if (stop.kind === 'REVIEW') return `${t('learn.kindReview')} · `
  return ''
}

function rowMeta(stop: PathStop): string {
  if (stop.state === 'DONE') return `${kindPrefix(stop)}${t('learn.stateDoneShort')}`
  if (stop.state === 'LOCKED') {
    return stop.unlockAfter ? t('learn.opensAfter', { title: stop.unlockAfter }) : t('learn.locked')
  }
  return `${kindPrefix(stop)}${t('learn.minutesXp', { minutes: stop.minutes, xp: stop.xpReward })}`
}

function stopLabel(stop: PathStop): string {
  const state =
    stop.state === 'DONE'
      ? t('learn.stateDone')
      : stop.state === 'LOCKED'
        ? t('learn.stateLocked')
        : t('learn.stateOpen')
  return `${stop.title} (${state})`
}

// ----- the lesson card, one open at a time -----

const openStopId = ref<number | null>(null)
const nodes = new Map<number, HTMLElement>()

function setNodeRef(id: number, element: Element | ComponentPublicInstance | null): void {
  if (element instanceof HTMLElement) nodes.set(id, element)
  else nodes.delete(id)
}

function toggle(id: number): void {
  openStopId.value = openStopId.value === id ? null : id
}

function close(id: number): void {
  openStopId.value = null
  nodes.get(id)?.focus()
}

/** The current lesson's row, so the page can bring it into view. */
function currentNode(): HTMLElement | null {
  const current = props.unit.lessons.find((stop) => stop.current)
  return current ? (nodes.get(current.id) ?? null) : null
}

defineExpose({ currentNode })
</script>
