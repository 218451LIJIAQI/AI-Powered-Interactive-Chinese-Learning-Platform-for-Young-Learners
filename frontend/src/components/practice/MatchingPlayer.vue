<template>
  <div class="practice-player matching-player">
    <div class="matching-grid">
      <section class="matching-column" :aria-labelledby="`${baseId}-left`">
        <h3 :id="`${baseId}-left`" class="practice-section-title">{{ t('studentPractice.matchingLeftHeading') }}</h3>
        <ul class="matching-list">
          <li v-for="item in game.leftItems" :key="item.id" class="matching-left-row">
            <button
              type="button"
              class="practice-choice matching-left"
              :class="{ 'is-paired': pairedText(item.id) !== null }"
              data-testid="matching-left-item"
              :aria-pressed="activeLeftId === item.id"
              :disabled="locked"
              @click="selectLeft(item.id)"
            >
              <img v-if="safeImage(item.imageUrl)" :src="safeImage(item.imageUrl)!" alt="" class="matching-image" />
              <span class="matching-left-copy">
                <span class="cjk-text practice-cjk" :lang="cjkLang(item.text)">{{ item.text }}</span>
                <span v-if="item.pinyin" class="practice-pinyin">{{ item.pinyin }}</span>
                <span class="matching-pair-label" :class="{ 'is-paired': pairedText(item.id) !== null }">
                  {{
                    pairedText(item.id) !== null
                      ? t('studentPractice.matchingPairedWith', { right: pairedText(item.id) })
                      : t('studentPractice.matchingNoPair')
                  }}
                </span>
              </span>
            </button>
            <button
              v-if="pairedText(item.id) !== null && !locked"
              type="button"
              class="btn btn-ghost btn-sm matching-remove"
              :aria-label="t('studentPractice.matchingRemoveAria', { left: item.text })"
              @click="removePair(item.id)"
            >
              {{ t('studentPractice.matchingRemove') }}
            </button>
          </li>
        </ul>
      </section>

      <section class="matching-column" :aria-labelledby="`${baseId}-right`">
        <h3 :id="`${baseId}-right`" class="practice-section-title">{{ t('studentPractice.matchingRightHeading') }}</h3>
        <ul class="matching-list">
          <li v-for="(option, index) in game.rightOptions" :key="`${index}-${option.text}`">
            <button
              type="button"
              class="practice-choice matching-right"
              :class="{ 'is-paired': usedBy(index) !== null }"
              data-testid="matching-right-option"
              :aria-pressed="activeLeftId !== null && selection[activeLeftId] === index"
              :disabled="locked"
              @click="chooseRight(index)"
            >
              <img v-if="safeImage(option.imageUrl)" :src="safeImage(option.imageUrl)!" alt="" class="matching-image" />
              <span class="matching-left-copy">
                <span :class="{ 'cjk-text practice-cjk': cjkLang(option.text) }" :lang="cjkLang(option.text)">{{
                  option.text
                }}</span>
                <span v-if="usedBy(index)" class="matching-pair-label is-paired">
                  {{ t('studentPractice.matchingUsedBy', { left: usedBy(index) }) }}
                </span>
              </span>
            </button>
          </li>
        </ul>
      </section>
    </div>

    <div class="practice-submit-row">
      <span class="practice-progress">{{
        t('studentPractice.matchingProgress', { done: matchedCount, total: game.leftItems.length })
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
import { computed, ref, useId } from 'vue'

import type { StudentMatchingGameResponse } from '@/types/api'
import {
  assignMatch,
  buildMatchingCompletion,
  cjkLang,
  isMatchingReady,
  unassignMatch,
  type ActivitySubmission,
  type MatchingSelection,
} from '@/utils/activityCompletion'
import { safeHttpUrl } from '@/utils/url'

import { usePracticeI18n } from './usePracticeI18n'

const props = defineProps<{
  game: StudentMatchingGameResponse
  locked?: boolean
  submitting?: boolean
}>()

const emit = defineEmits<{ submit: [submission: ActivitySubmission] }>()

const { t } = usePracticeI18n()
const baseId = `matching-${useId()}`
const selection = ref<MatchingSelection>({})
const activeLeftId = ref<number | null>(props.game.leftItems[0]?.id ?? null)
const announcement = ref('')

const ready = computed(() => isMatchingReady(props.game.leftItems, props.game.rightOptions, selection.value))
const matchedCount = computed(
  () => props.game.leftItems.filter((item) => selection.value[item.id] !== undefined).length,
)

function safeImage(url: string | null | undefined): string | null {
  return safeHttpUrl(url, { allowRelative: true })
}

function leftText(id: number): string {
  return props.game.leftItems.find((item) => item.id === id)?.text ?? ''
}

function pairedText(leftId: number): string | null {
  const index = selection.value[leftId]
  return index === undefined ? null : (props.game.rightOptions[index]?.text ?? null)
}

function usedBy(rightIndex: number): string | null {
  const entry = Object.entries(selection.value).find(([, value]) => value === rightIndex)
  return entry ? leftText(Number(entry[0])) : null
}

function selectLeft(id: number): void {
  activeLeftId.value = id
  announcement.value = t('studentPractice.matchingSelected', { item: leftText(id) })
}

function nextUnmatched(afterId: number): number | null {
  const items = props.game.leftItems
  const start = items.findIndex((item) => item.id === afterId)
  for (let offset = 1; offset <= items.length; offset += 1) {
    const candidate = items[(start + offset) % items.length]
    if (candidate && selection.value[candidate.id] === undefined) return candidate.id
  }
  return null
}

function chooseRight(index: number): void {
  const leftId = activeLeftId.value
  if (leftId === null) {
    announcement.value = t('studentPractice.matchingPickLeftFirst')
    return
  }

  selection.value = assignMatch(selection.value, leftId, index)
  announcement.value = t('studentPractice.matchingPaired', {
    left: leftText(leftId),
    right: props.game.rightOptions[index]?.text ?? '',
  })
  activeLeftId.value = nextUnmatched(leftId)
}

function removePair(leftId: number): void {
  selection.value = unassignMatch(selection.value, leftId)
  activeLeftId.value = leftId
  announcement.value = t('studentPractice.matchingUnpaired', { left: leftText(leftId) })
}

function submit(): void {
  if (!ready.value || props.locked) return
  emit('submit', {
    type: 'MATCHING_GAME',
    body: buildMatchingCompletion(props.game.leftItems, props.game.rightOptions, selection.value),
  })
}
</script>

<style scoped src="./practicePlayer.css"></style>
<style scoped>
.matching-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.matching-column {
  display: grid;
  gap: var(--space-2-5);
  align-content: start;
  min-width: 0;
}

.matching-list {
  display: grid;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.matching-left-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-1-5);
  align-items: center;
}

.matching-left,
.matching-right {
  width: 100%;
}

.matching-left.is-paired:not([aria-pressed='true']),
.matching-right.is-paired:not([aria-pressed='true']) {
  border-style: dashed;
  border-color: var(--color-primary-300);
  background: var(--surface-subtle);
}

.matching-left-copy {
  display: grid;
  gap: var(--space-0-5);
  min-width: 0;
}

.matching-pair-label {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
}

.matching-pair-label.is-paired {
  color: var(--text-brand);
}

.matching-image {
  flex: 0 0 auto;
  width: 44px;
  height: 44px;
  border-radius: var(--radius-sm);
  object-fit: cover;
}

@media (max-width: 640px) {
  .matching-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
