<template>
  <section
    :id="`learn-unit-${unit.number}`"
    class="learn-unit"
    :class="`is-${unit.state.toLowerCase()}`"
    :aria-labelledby="`${baseId}-title`"
  >
    <!-- The pinned banner above the path already shows this unit, so its heading only speaks to assistive technology. -->
    <h3 v-if="bannerPinned" :id="`${baseId}-title`" class="sr-only">{{ unit.title }} · {{ unit.titleZh }}</h3>
    <LearnUnitBanner
      v-else
      :unit="unit"
      :title-id="`${baseId}-title`"
      :show-guidebook="!isPinnedUnit"
      @guidebook="emit('guidebook', $event)"
    />
    <p class="sr-only">{{ t('learn.xOfY', { done: unit.completedCount, total: unit.lessonCount }) }}</p>

    <div class="learn-road" :style="{ height: `${roadHeight}px` }">
      <!-- The road is a drawing: its origin is the centre line of the column, so it follows the column's width. -->
      <svg class="learn-road-line" aria-hidden="true">
        <line
          v-for="segment in segments"
          :key="segment.key"
          :class="segment.walked ? 'is-walked' : 'is-ahead'"
          :x1="segment.x1"
          :y1="segment.y1"
          :x2="segment.x2"
          :y2="segment.y2"
        />
      </svg>

      <ol class="learn-road-stops" :aria-label="t('learn.unitLessons', { number: unit.number })">
        <li
          v-for="(stop, index) in unit.lessons"
          :key="stop.id"
          class="learn-node"
          :class="[
            `is-${stop.state.toLowerCase()}`,
            {
              'is-current': stop.current,
              'is-bonus': stop.kind === 'BONUS',
              'is-open': openStopId === stop.id,
            },
          ]"
          :style="{ '--node-x': `${point(index).x}px`, '--node-y': `${point(index).y}px` }"
          @keydown.esc.stop="close(stop.id)"
        >
          <button
            :ref="(el) => setNodeRef(stop.id, el)"
            class="learn-node-button"
            type="button"
            :aria-expanded="openStopId === stop.id"
            :aria-controls="`${baseId}-card-${stop.id}`"
            :aria-label="stopLabel(stop)"
            :data-testid="`stop-${stop.id}`"
            @click="toggle(stop.id)"
          >
            <span class="learn-node-disc">
              <IconGlyph v-if="stop.state === 'DONE'" name="check" :size="stop.current ? 36 : 32" />
              <span v-else-if="stop.kind === 'BONUS'" class="learn-node-bonus">{{ t('learn.kindBonus') }}</span>
              <IconGlyph v-else-if="stop.state === 'LOCKED'" name="lock" :size="24" />
              <IconGlyph v-else class="learn-node-star" name="star" :size="stop.current ? 32 : 24" />
            </span>
            <span class="learn-node-label">{{ stop.title }}</span>
            <span v-if="stop.current && openStopId !== stop.id" class="learn-node-bubble">{{
              t('learn.startBubble')
            }}</span>
          </button>

          <div
            v-if="openStopId === stop.id"
            :id="`${baseId}-card-${stop.id}`"
            class="learn-node-card"
            role="dialog"
            :aria-label="stop.title"
          >
            <p class="learn-node-card-title">{{ stop.title }}</p>
            <template v-if="stop.state === 'LOCKED'">
              <p class="learn-node-card-meta">{{ lockedReason(stop) }}</p>
              <button class="btn btn-secondary btn-block" type="button" disabled>{{ t('learn.locked') }}</button>
            </template>
            <template v-else>
              <p class="learn-node-card-meta">
                {{
                  t('learn.stopMeta', {
                    position: stop.position,
                    total: unit.lessonCount,
                    unit: unit.number,
                    minutes: stop.minutes,
                  })
                }}
              </p>
              <RouterLink
                class="btn btn-block"
                :class="stop.current ? 'btn-accent' : 'btn-secondary'"
                :to="{ name: 'student-learn-lesson', params: { lessonId: stop.id } }"
                :data-testid="`start-${stop.id}`"
              >
                {{ stop.state === 'DONE' ? t('learn.practiseAgain') : t('learn.startWithXp', { xp: stop.xpReward }) }}
              </RouterLink>
            </template>
          </div>
        </li>
      </ol>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, type ComponentPublicInstance } from 'vue'
import { RouterLink } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { learnMessages } from '@/plugins/learnMessages'
import type { PathStop, PathUnit } from '@/types/learn'

import LearnUnitBanner from './LearnUnitBanner.vue'

/**
 * One unit of the Learn path: its banner, then its lessons as round stops on a winding road, solid where the student has
 * walked and dotted ahead. A stop opens its card in place: Start (+XP), Practise again, or what to finish first.
 */
interface Props {
  unit: PathUnit
  /** This unit is the one the pinned "You are here" banner shows. */
  isPinnedUnit?: boolean
  /** The pinned banner stands directly above this unit, so it draws no banner of its own. */
  bannerPinned?: boolean
}

const props = withDefaults(defineProps<Props>(), { isPinnedUnit: false, bannerPinned: false })

const emit = defineEmits<{
  guidebook: [unit: PathUnit]
}>()

const { t } = useLocalMessages({ learn: learnMessages })
const baseId = useId()

// ----- the road: stops every 130px, swinging 120px either side of the centre line -----

const STOP_SPACING = 130
const SWING = 120
const ROAD_EDGE = 60
/** Room for the Start bubble when the very first stop is the current one. */
const BUBBLE_ROOM = 24

const roadTop = computed(() => ROAD_EDGE + (props.unit.lessons[0]?.current ? BUBBLE_ROOM : 0))

const roadHeight = computed(() => roadTop.value + Math.max(0, props.unit.lessons.length - 1) * STOP_SPACING + ROAD_EDGE)

function point(index: number): { x: number; y: number } {
  return {
    x: Math.round(-SWING * Math.sin((index * Math.PI) / 4)),
    y: roadTop.value + index * STOP_SPACING,
  }
}

const segments = computed(() =>
  props.unit.lessons.slice(1).map((stop, index) => {
    const from = point(index)
    const to = point(index + 1)
    const previous = props.unit.lessons[index]!
    return {
      key: stop.id,
      x1: from.x,
      y1: from.y,
      x2: to.x,
      y2: to.y,
      // An optional bonus chest may be passed by, so what lies beyond it also counts as walked.
      walked: previous.state === 'DONE' || stop.state === 'DONE',
    }
  }),
)

// ----- names -----

function stopLabel(stop: PathStop): string {
  const state =
    stop.state === 'DONE'
      ? t('learn.stateDone')
      : stop.state === 'LOCKED'
        ? t('learn.stateLocked')
        : t('learn.stateOpen')
  return `${stop.title} (${state})`
}

function lockedReason(stop: PathStop): string {
  return stop.unlockAfter
    ? t('learn.finishToUnlock', { title: stop.unlockAfter })
    : props.unit.unlockAfterUnit
      ? t('learn.finishUnitToUnlock', { number: props.unit.unlockAfterUnit })
      : t('learn.locked')
}

// ----- the stop's card, one open at a time -----

const openStopId = ref<number | null>(null)
const nodes = new Map<number, HTMLElement>()

function setNodeRef(id: number, element: Element | ComponentPublicInstance | null): void {
  if (element instanceof HTMLElement) nodes.set(id, element)
  else nodes.delete(id)
}

async function toggle(id: number): Promise<void> {
  openStopId.value = openStopId.value === id ? null : id
  if (openStopId.value === null) return
  await nextTick()
  nodes.get(id)?.parentElement?.querySelector('.learn-node-card')?.scrollIntoView?.({ block: 'nearest' })
}

function close(id: number): void {
  if (openStopId.value !== id) return
  openStopId.value = null
  nodes.get(id)?.focus()
}

/** A click anywhere outside the open stop closes its card. */
function onDocumentClick(event: MouseEvent): void {
  if (openStopId.value === null) return
  const openNode = nodes.get(openStopId.value)?.parentElement
  if (!openNode?.contains(event.target as Node)) openStopId.value = null
}

onMounted(() => document.addEventListener('click', onDocumentClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocumentClick))
</script>
