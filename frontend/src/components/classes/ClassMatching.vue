<template>
  <div class="class-player">
    <div class="class-player-head">
      <span class="page-eyebrow student-classes-pigment class-player-kind"
        ><IconGlyph name="puzzle" :size="16" />{{ t('classPlayer.matching.title') }}</span
      >
      <strong class="class-player-count">{{
        t('classPlayer.matching.paired', { paired: pairedCount, total: words.length })
      }}</strong>
      <button
        v-if="hintLimit > 0"
        type="button"
        class="btn btn-ghost btn-sm"
        :disabled="hintsLeft === 0 || busy"
        data-testid="matching-hint"
        @click="askHint"
      >
        <IconGlyph name="hint" :size="16" />
        {{
          hintLimit === 1
            ? hintsLeft
              ? t('classPlayer.matching.useHint')
              : t('classPlayer.matching.hintUsed')
            : t('classPlayer.activity.showHint')
        }}
      </button>
    </div>
    <p class="class-player-instruction">{{ t('classPlayer.matching.instruction') }}</p>
    <p v-if="hintText" class="callout is-compact" role="status">
      <IconGlyph name="hint" :size="16" /><span>{{ hintText }}</span>
    </p>

    <div class="class-matching">
      <div class="class-matching-column">
        <h3 class="page-eyebrow">{{ t('classPlayer.matching.chinese') }}</h3>
        <button
          v-for="word in words"
          :key="word.id"
          type="button"
          class="class-matching-item"
          :class="itemClass(word.id)"
          :aria-pressed="selectedWord === word.id"
          :data-testid="`match-word-${word.id}`"
          @click="pickWord(word.id)"
        >
          <span class="cjk-text class-matching-chinese" lang="zh-CN">{{ word.chinese }}</span>
          <span v-if="word.pinyin && stage !== 'INDEPENDENT'" class="type-caption">{{ word.pinyin }}</span>
          <span v-if="pairs[word.id]" class="class-matching-pair">
            <IconGlyph name="link" :size="12" />{{ pairs[word.id] }}
          </span>
          <span v-if="results[word.id] === false" class="sr-only">{{ t('classPlayer.matching.wrong') }}</span>
        </button>
      </div>
      <div class="class-matching-column">
        <h3 class="page-eyebrow">{{ t('classPlayer.matching.meaning') }}</h3>
        <button
          v-for="(meaning, meaningIndex) in meanings"
          :key="`${meaning}-${meaningIndex}`"
          type="button"
          class="class-matching-item"
          :class="{ 'is-paired': usedMeanings.has(meaning) }"
          :disabled="selectedWord === null && !usedMeanings.has(meaning)"
          @click="pickMeaning(meaning)"
        >
          {{ meaning }}
          <span v-if="usedMeanings.has(meaning)" class="class-matching-pair cjk-text" lang="zh-CN">
            <IconGlyph name="link" :size="12" />{{ wordFor(meaning) }}
          </span>
        </button>
      </div>
    </div>

    <p v-if="checked" class="alert" :class="allRight ? 'alert-success' : 'alert-warning'" role="status">
      {{ allRight ? t('classPlayer.matching.allRight') : t('classPlayer.matching.someWrong') }}
    </p>

    <div class="class-player-foot">
      <span class="type-caption">{{ t('classPlayer.matching.undo') }}</span>
      <div class="class-player-foot-actions">
        <button type="button" class="btn btn-outline" :disabled="!pairedCount || busy" @click="reset">
          <IconGlyph name="retry" :size="16" />{{ t('classPlayer.matching.reset') }}
        </button>
        <button
          type="button"
          class="btn btn-primary"
          :disabled="pairedCount < words.length || busy"
          :aria-busy="busy"
          data-testid="activity-finish"
          @click="check"
        >
          <IconGlyph name="check" :size="16" />{{ t('classPlayer.matching.check') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import { studentClassesApi } from '@/api/classes'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classPlayerMessages } from '@/plugins/classPlayerMessages'
import type { LearningStage } from '@/types/api'
import type { ActivityOutcome, MatchWord } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'

/**
 * Match each Chinese word with its meaning; the server checks the pairs and marks the wrong ones, which the student
 * fixes and checks again. Hints by stage: Guided any number, Supported one, Independent none (no pinyin either).
 */
const props = defineProps<{
  activityId: number
  words: readonly MatchWord[]
  meanings: readonly string[]
  stage: LearningStage | null
}>()
const emit = defineEmits<{ outcome: [outcome: ActivityOutcome]; error: [message: string] }>()

const { t } = useLocalMessages({ classPlayer: classPlayerMessages })

const pairs = ref<Record<number, string>>({})
const selectedWord = ref<number | null>(null)
const results = ref<Record<number, boolean>>({})
const checked = ref(false)
const allRight = ref(false)
const busy = ref(false)
const hintText = ref('')
const hintsUsed = ref(0)

const hintLimit = computed(() => (props.stage === 'INDEPENDENT' ? 0 : props.stage === 'SCAFFOLDED' ? 1 : Infinity))
const hintsLeft = computed(() => Math.max(0, hintLimit.value - hintsUsed.value))
const pairedCount = computed(() => Object.keys(pairs.value).length)
const usedMeanings = computed(() => new Set(Object.values(pairs.value)))

function itemClass(wordId: number) {
  return {
    'is-selected': selectedWord.value === wordId,
    'is-paired': Boolean(pairs.value[wordId]),
    'is-wrong': results.value[wordId] === false,
    'is-right': results.value[wordId] === true,
  }
}

function wordFor(meaning: string): string {
  const entry = Object.entries(pairs.value).find(([, value]) => value === meaning)
  return props.words.find((word) => String(word.id) === entry?.[0])?.chinese ?? ''
}

function pickWord(wordId: number): void {
  if (pairs.value[wordId]) {
    // Clicking a paired word undoes the pair.
    const next = { ...pairs.value }
    delete next[wordId]
    pairs.value = next
    clearResult(wordId)
    selectedWord.value = null
    return
  }
  selectedWord.value = selectedWord.value === wordId ? null : wordId
}

function pickMeaning(meaning: string): void {
  if (usedMeanings.value.has(meaning)) {
    const entry = Object.entries(pairs.value).find(([, value]) => value === meaning)
    if (entry) pickWord(Number(entry[0]))
    return
  }
  if (selectedWord.value === null) return
  pairs.value = { ...pairs.value, [selectedWord.value]: meaning }
  clearResult(selectedWord.value)
  selectedWord.value = null
}

function clearResult(wordId: number): void {
  if (wordId in results.value) {
    const next = { ...results.value }
    delete next[wordId]
    results.value = next
  }
  checked.value = false
}

function reset(): void {
  pairs.value = {}
  results.value = {}
  selectedWord.value = null
  checked.value = false
}

async function askHint(): Promise<void> {
  const wordId = selectedWord.value ?? props.words.find((word) => !pairs.value[word.id])?.id ?? null
  if (wordId === null) {
    hintText.value = t('classPlayer.matching.pickWordFirst')
    return
  }
  busy.value = true
  try {
    const hint = await studentClassesApi.matchingHint(props.activityId, wordId)
    hintsUsed.value++
    const word = props.words.find((item) => item.id === hint.wordId)
    hintText.value = t('classPlayer.matching.hintFor', { word: word?.chinese ?? '', meaning: hint.meaning })
  } catch (reason) {
    emit('error', resolveApiError(reason, t('classPlayer.matching.checkError')))
  } finally {
    busy.value = false
  }
}

async function check(): Promise<void> {
  busy.value = true
  try {
    const outcome = await studentClassesApi.checkMatching(
      props.activityId,
      Object.entries(pairs.value).map(([wordId, meaning]) => ({ wordId: Number(wordId), meaning })),
    )
    results.value = Object.fromEntries(outcome.pairs.map((pair) => [pair.wordId, pair.correct]))
    checked.value = true
    allRight.value = outcome.done
    if (outcome.done) {
      emit('outcome', outcome)
    } else {
      // Wrong pairs are taken apart so the student can try them again; right ones stay.
      const kept = { ...pairs.value }
      for (const pair of outcome.pairs) if (!pair.correct) delete kept[pair.wordId]
      pairs.value = kept
    }
  } catch (reason) {
    emit('error', resolveApiError(reason, t('classPlayer.matching.checkError')))
  } finally {
    busy.value = false
  }
}
</script>
