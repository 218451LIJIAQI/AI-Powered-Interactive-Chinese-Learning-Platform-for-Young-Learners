<template>
  <div class="learn-map" :style="{ height: `${height}px` }">
    <!-- The ground and its road are a drawing; only the levels on it can be pressed. -->
    <div class="learn-map-ground" aria-hidden="true">
      <svg class="learn-map-road" :height="height">
        <path v-for="tuft in tufts" :key="tuft" class="learn-map-tuft" :d="tuft" />
        <path class="learn-map-road-edge" :d="road" />
        <path class="learn-map-road-surface" :d="road" />
        <path class="learn-map-road-dashes" :d="road" />
        <template v-if="walked">
          <path class="learn-map-progress-edge" :d="walked" />
          <path class="learn-map-progress" :d="walked" />
        </template>
      </svg>
      <span v-if="locked" class="learn-map-fog" />
    </div>

    <ol class="learn-map-levels" :aria-label="t('learn.unitLessons', { number: unit.number })">
      <li
        v-for="{ stop, point } in levels"
        :key="stop.id"
        class="learn-level"
        :class="levelClass(stop)"
        :style="{ '--x': `${point.x}px`, '--y': `${point.y}px` }"
        @keydown.esc.stop="close(stop.id)"
        @focusout="onFocusOut(stop.id, $event)"
      >
        <span v-if="stop.current" class="learn-level-halo" aria-hidden="true" />
        <LearnPanda v-if="stop.current" class="learn-level-panda" :class="{ 'is-right': point.x < 0 }" />
        <span v-if="stop.current && openId !== stop.id" class="learn-level-start" aria-hidden="true">
          {{ t('learn.startBubble') }}
        </span>

        <button
          :ref="(element) => setButton(stop.id, element)"
          class="learn-level-button"
          type="button"
          :aria-label="levelLabel(stop)"
          aria-haspopup="dialog"
          :aria-expanded="openId === stop.id"
          :aria-controls="openId === stop.id ? `${baseId}-card-${stop.id}` : undefined"
          :data-testid="`level-${stop.id}`"
          @click="toggle(stop.id)"
        >
          <svg v-if="stop.state === 'DONE'" class="learn-level-stars" viewBox="0 0 66 26" aria-hidden="true">
            <path d="M10 9.5L12.1 14.1L17.1 14.7L13.4 18.1L14.4 23.1L10 20.6L5.6 23.1L6.6 18.1L2.9 14.7L7.9 14.1Z" />
            <path d="M33 1L35.8 7.1L42.5 7.9L37.6 12.5L38.9 19.1L33 15.8L27.1 19.1L28.4 12.5L23.5 7.9L30.2 7.1Z" />
            <path
              d="M56 9.5L58.1 14.1L63.1 14.7L59.4 18.1L60.4 23.1L56 20.6L51.6 23.1L52.6 18.1L48.9 14.7L53.9 14.1Z"
            />
          </svg>
          <span class="learn-level-disc" aria-hidden="true">
            <IconGlyph v-if="stop.kind === 'BONUS'" class="learn-level-glyph" name="star" :size="22" />
            <IconGlyph v-else-if="stop.kind === 'REVIEW'" name="crown" :size="26" />
            <template v-else>{{ stop.position }}</template>
          </span>
          <span class="learn-level-label">{{ ' ' + stop.title }}</span>
        </button>

        <div
          v-if="openId === stop.id"
          :id="`${baseId}-card-${stop.id}`"
          class="learn-level-card"
          role="dialog"
          tabindex="-1"
          :aria-label="stop.title"
          :aria-describedby="`${baseId}-detail-${stop.id}`"
          :data-testid="`level-card-${stop.id}`"
        >
          <p class="learn-level-card-title">{{ stop.title }}</p>
          <p v-if="stop.state === 'LOCKED'" :id="`${baseId}-detail-${stop.id}`" class="learn-level-card-meta">
            {{ lockedMessage(stop) }}
          </p>
          <template v-else>
            <p :id="`${baseId}-detail-${stop.id}`" class="learn-level-card-meta">
              {{
                stop.kind === 'BONUS'
                  ? t('learn.bonusMinutes', { minutes: stop.minutes })
                  : t('learn.aboutMinutes', { minutes: stop.minutes })
              }}
            </p>
            <RouterLink
              class="btn btn-block"
              :class="stop.state === 'DONE' ? 'btn-secondary' : 'btn-accent'"
              :to="{ name: 'student-learn-lesson', params: { lessonId: stop.id } }"
              :data-testid="`start-${stop.id}`"
            >
              {{ stop.state === 'DONE' ? t('common.practiseAgain') : t('learn.startWithXp', { xp: stop.xpReward }) }}
            </RouterLink>
          </template>
        </div>
      </li>
    </ol>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, type ComponentPublicInstance } from 'vue'
import { RouterLink } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { learnMessages } from '@/plugins/learnMessages'
import type { PathStop, PathUnit, StopState } from '@/types/learn'

import LearnPanda from './LearnPanda.vue'
import { groundTufts, levelPoint, mapHeight, roadPath } from './learnMap'

/**
 * One unit of the Learn path as a level map: a plain ground with one wide road, and each lesson a thick numbered disc
 * on it, alternating left and right, with its name under it (★ a bonus chest, ♛ the unit review). Done levels are
 * violet under three stars; the current one is amber on a white halo, with START and the panda beside it; locked
 * ones are white. An amber line runs along the road from the unit's entrance to the current level, and a locked unit
 * lies under a fog. A level opens its card in place: Start (+XP), Practise again, or what to finish first.
 */
interface Props {
  unit: PathUnit
  /** The level the lesson just finished unlocked: it pops in once. */
  newlyUnlocked?: string | null
}

const props = withDefaults(defineProps<Props>(), { newlyUnlocked: null })

const { t } = useLocalMessages({ learn: learnMessages })
const baseId = useId()

const locked = computed(() => props.unit.state === 'LOCKED')
const height = computed(() => mapHeight(props.unit.lessons.length))
/** Each level of the unit with the point where it sits on the board. */
const levels = computed(() => props.unit.lessons.map((stop, index) => ({ stop, point: levelPoint(index) })))
const tufts = computed(() => groundTufts(height.value))
const road = computed(() => roadPath(props.unit.lessons.length))

/**
 * How far the amber line runs: through the whole unit once it is done, to the current level (or the last one done)
 * while it is open, nowhere while it is locked.
 */
const walked = computed(() => {
  if (locked.value) return null
  const lessons = props.unit.lessons
  if (props.unit.state === 'DONE') return road.value
  const current = lessons.findIndex((stop) => stop.current)
  if (current >= 0) return roadPath(current)
  const lastDone = lessons.reduce((last, stop, index) => (stop.state === 'DONE' ? index : last), -1)
  return lastDone >= 0 ? roadPath(lastDone) : null
})

function levelClass(stop: PathStop): Record<string, boolean> {
  return {
    [`is-${stop.state.toLowerCase()}`]: true,
    'is-current': stop.current,
    'is-review': stop.kind === 'REVIEW',
    'is-new': stop.current && stop.title === props.newlyUnlocked,
  }
}

const STATE_KEYS: Readonly<Record<StopState, string>> = {
  DONE: 'learn.stateDone',
  AVAILABLE: 'learn.stateOpen',
  LOCKED: 'learn.stateLocked',
}

/** "Level 3, Initials 1, current"; a bonus chest or a unit review is not a numbered level. */
function levelLabel(stop: PathStop): string {
  const state = t(stop.current ? 'learn.stateCurrent' : STATE_KEYS[stop.state])
  if (stop.kind === 'BONUS') return t('learn.levelLabelBonus', { title: stop.title, state })
  if (stop.kind === 'REVIEW') return t('learn.levelLabelReview', { title: stop.title, state })
  return t('learn.levelLabel', { number: stop.position, title: stop.title, state })
}

function lockedMessage(stop: PathStop): string {
  if (stop.unlockAfter) return t('learn.finishToUnlock', { title: stop.unlockAfter })
  const previousUnit = props.unit.unlockAfterUnit ?? (props.unit.number > 1 ? props.unit.number - 1 : null)
  return previousUnit !== null ? t('learn.finishUnitToUnlock', { number: previousUnit }) : t('learn.stateLocked')
}

// A level's card, one open at a time.

const openId = ref<number | null>(null)
const buttons = new Map<number, HTMLElement>()

function setButton(id: number, element: Element | ComponentPublicInstance | null): void {
  if (element instanceof HTMLElement) buttons.set(id, element)
  else buttons.delete(id)
}

async function toggle(id: number): Promise<void> {
  openId.value = openId.value === id ? null : id
  if (openId.value === null) return
  await nextTick()
  const card = buttons.get(id)?.parentElement?.querySelector<HTMLElement>('.learn-level-card')
  card?.focus({ preventScroll: true })
  card?.scrollIntoView?.({ block: 'nearest' })
}

function onFocusOut(id: number, event: FocusEvent): void {
  const next = event.relatedTarget
  if (openId.value === id && next instanceof Node && !(event.currentTarget as HTMLElement).contains(next)) {
    openId.value = null
  }
}

function close(id: number): void {
  if (openId.value !== id) return
  openId.value = null
  buttons.get(id)?.focus()
}

/**
 * A click anywhere outside the open level closes its card. The click's path, not `contains`: opening a card has
 * already re-rendered by the time the click reaches the document, and that removes the START tag that was clicked.
 */
function onDocumentClick(event: MouseEvent): void {
  if (openId.value === null) return
  const open = buttons.get(openId.value)?.parentElement
  if (!open || !event.composedPath().includes(open)) openId.value = null
}

onMounted(() => document.addEventListener('click', onDocumentClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocumentClick))
</script>
