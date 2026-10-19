<template>
  <div class="practice-player dragdrop-player">
    <p class="practice-instructions">{{ t('studentPractice.dragHelp') }}</p>

    <div class="dragdrop-board">
      <section
        v-for="(group, groupIndex) in groups"
        :key="group.zone ?? '__pool__'"
        class="dragdrop-zone"
        :class="{ 'is-pool': group.zone === null, 'is-over': overGroup === groupIndex }"
        :data-testid="group.zone === null ? 'dragdrop-pool' : 'dragdrop-zone'"
        :aria-labelledby="`${baseId}-zone-${groupIndex}`"
        @dragover.prevent="onDragOver(groupIndex, $event)"
        @dragleave="onDragLeave(groupIndex)"
        @drop.prevent="onDrop(group.zone, $event)"
      >
        <h3 :id="`${baseId}-zone-${groupIndex}`" class="practice-section-title">
          {{ group.zone ?? t('studentPractice.dragPool') }}
        </h3>
        <ul class="dragdrop-items">
          <li
            v-for="item in group.items"
            :key="item.id"
            class="dragdrop-item"
            :class="{ 'is-dragging': draggingId === item.id }"
            data-testid="dragdrop-item"
            :draggable="!locked"
            @dragstart="onDragStart(item.id, $event)"
            @dragend="onDragEnd"
          >
            <span class="dragdrop-item-text">
              <img v-if="safeImage(item.imageUrl)" :src="safeImage(item.imageUrl)!" alt="" class="dragdrop-image" />
              <span class="cjk-text practice-cjk" :lang="cjkLang(item.dragText)">{{ item.dragText }}</span>
              <span v-if="item.pinyin" class="practice-pinyin">{{ item.pinyin }}</span>
            </span>
            <label :for="selectId(item.id)" class="sr-only">
              {{ t('studentPractice.dragSelectLabel', { item: item.dragText }) }}
            </label>
            <select
              :id="selectId(item.id)"
              class="select dragdrop-select"
              data-testid="dragdrop-select"
              :value="placements[item.id] ?? ''"
              :disabled="locked"
              @change="onSelect(item.id, $event)"
            >
              <option value="">{{ t('studentPractice.dragNotPlaced') }}</option>
              <option v-for="zone in activity.zones" :key="zone" :value="zone">{{ zone }}</option>
            </select>
          </li>
        </ul>
        <p v-if="!group.items.length" class="dragdrop-empty">
          {{ group.zone === null ? t('studentPractice.dragPoolEmpty') : t('studentPractice.dragZoneEmpty') }}
        </p>
      </section>
    </div>

    <div class="practice-submit-row">
      <span class="practice-progress">{{
        t('studentPractice.dragProgress', { done: placedCount, total: activity.items.length })
      }}</span>
      <button
        type="button"
        class="btn btn-primary"
        data-testid="activity-submit"
        :disabled="!ready || locked || submitting"
        :aria-describedby="ready ? undefined : `${baseId}-hint`"
        @click="submit"
      >
        {{ submitting ? t('studentPractice.submitting') : t('studentPractice.submitAnswers') }}
      </button>
    </div>
    <p v-if="!ready && !locked" :id="`${baseId}-hint`" class="practice-progress">
      {{ t('studentPractice.submitHint') }}
    </p>

    <p class="sr-only" role="status" aria-live="polite">{{ announcement }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, useId } from 'vue'

import type { StudentDragDropActivityResponse, StudentDragDropItemResponse } from '@/types/api'
import {
  buildDragDropCompletion,
  cjkLang,
  isDragDropReady,
  placeItem,
  type ActivitySubmission,
  type DragDropPlacements,
} from '@/utils/activityCompletion'
import { safeHttpUrl } from '@/utils/url'

import { usePracticeI18n } from './usePracticeI18n'

const props = defineProps<{
  activity: StudentDragDropActivityResponse
  locked?: boolean
  submitting?: boolean
}>()

const emit = defineEmits<{ submit: [submission: ActivitySubmission] }>()

const DRAG_TYPE = 'text/plain'
const { t } = usePracticeI18n()
const baseId = `dragdrop-${useId()}`
const placements = ref<DragDropPlacements>({})
const draggingId = ref<number | null>(null)
const overGroup = ref<number | null>(null)
const announcement = ref('')

const ready = computed(() => isDragDropReady(props.activity.items, props.activity.zones, placements.value))
const placedCount = computed(
  () => props.activity.items.filter((item) => props.activity.zones.includes(placements.value[item.id] ?? '')).length,
)

/** The unplaced pool first, then one group per zone; every item is rendered exactly once. */
const groups = computed<Array<{ zone: string | null; items: StudentDragDropItemResponse[] }>>(() => [
  {
    zone: null,
    items: props.activity.items.filter((item) => !props.activity.zones.includes(placements.value[item.id] ?? '')),
  },
  ...props.activity.zones.map((zone) => ({
    zone,
    items: props.activity.items.filter((item) => placements.value[item.id] === zone),
  })),
])

function safeImage(url: string | null | undefined): string | null {
  return safeHttpUrl(url, { allowRelative: true })
}

function selectId(itemId: number): string {
  return `${baseId}-item-${itemId}`
}

function itemText(itemId: number): string {
  return props.activity.items.find((item) => item.id === itemId)?.dragText ?? ''
}

function place(itemId: number, zone: string | null, restoreFocus = false): void {
  if (props.locked) return
  const target = zone && props.activity.zones.includes(zone) ? zone : null
  placements.value = placeItem(placements.value, itemId, target)
  announcement.value = target
    ? t('studentPractice.dragPlaced', { item: itemText(itemId), zone: target })
    : t('studentPractice.dragRemoved', { item: itemText(itemId) })

  if (restoreFocus) {
    // The item moves to another list (re-rendered), so give keyboard users their select back.
    void nextTick(() => document.getElementById(selectId(itemId))?.focus())
  }
}

function onSelect(itemId: number, event: Event): void {
  place(itemId, (event.target as HTMLSelectElement).value || null, true)
}

function onDragStart(itemId: number, event: DragEvent): void {
  if (props.locked) {
    event.preventDefault()
    return
  }
  draggingId.value = itemId
  event.dataTransfer?.setData(DRAG_TYPE, String(itemId))
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}

function onDragOver(groupIndex: number, event: DragEvent): void {
  if (props.locked) return
  overGroup.value = groupIndex
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
}

function onDragLeave(groupIndex: number): void {
  if (overGroup.value === groupIndex) overGroup.value = null
}

function onDrop(zone: string | null, event: DragEvent): void {
  overGroup.value = null
  const fromTransfer = Number(event.dataTransfer?.getData(DRAG_TYPE))
  const itemId = Number.isInteger(fromTransfer) && fromTransfer > 0 ? fromTransfer : draggingId.value
  draggingId.value = null
  if (itemId === null || !props.activity.items.some((item) => item.id === itemId)) return
  place(itemId, zone)
}

function onDragEnd(): void {
  draggingId.value = null
  overGroup.value = null
}

function submit(): void {
  if (!ready.value || props.locked) return
  emit('submit', {
    type: 'DRAG_DROP_ACTIVITY',
    body: buildDragDropCompletion(props.activity.items, props.activity.zones, placements.value),
  })
}
</script>

<style scoped src="./practicePlayer.css"></style>
<style scoped>
.dragdrop-board {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 200px), 1fr));
  gap: var(--space-3);
}

.dragdrop-zone {
  display: grid;
  gap: var(--space-2);
  align-content: start;
  min-width: 0;
  min-height: 120px;
  padding: var(--space-3);
  border: var(--border-width-thick) dashed var(--color-primary-200);
  border-radius: var(--radius-lg);
  background: var(--surface-sunken);
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background var(--duration-fast) var(--ease-standard);
}

.dragdrop-zone.is-pool {
  grid-column: 1 / -1;
  min-height: 0;
  border-style: solid;
  border-color: var(--border-default);
  background: var(--surface-card);
}

.dragdrop-zone.is-over {
  border-style: solid;
  border-color: var(--color-primary-500);
  background: var(--color-primary-50);
}

.dragdrop-items {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.dragdrop-item {
  display: grid;
  gap: var(--space-1-5);
  min-width: 0;
  max-width: 100%;
  padding: var(--space-2-5);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-card);
  box-shadow: var(--shadow-xs);
  cursor: grab;
}

.dragdrop-item:hover {
  border-color: var(--border-interactive);
}

.dragdrop-item.is-dragging {
  border-style: dashed;
  border-color: var(--color-primary-500);
  opacity: 0.6;
  cursor: grabbing;
}

.dragdrop-item-text {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-1-5);
}

.dragdrop-image {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-xs);
  object-fit: cover;
}

.dragdrop-select {
  max-width: 100%;
}

.dragdrop-zone:not(.is-pool) .practice-section-title {
  /* Zone names are authored text: keep their casing. */
  color: var(--text-primary);
  font-size: var(--font-size-base);
  letter-spacing: normal;
  text-transform: none;
}

.dragdrop-empty {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}
</style>
