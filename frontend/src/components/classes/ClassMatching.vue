<template>
  <div class="class-player">
    <header class="class-focus-head">
      <p class="page-eyebrow">{{ t('classPlayer.matching.title') }}</p>
      <h1 class="focus-title"><CjkText :text="title" /></h1>
      <div class="class-player-row class-focus-sub">
        <span>{{
          answersInChinese ? t('classPlayer.matching.instructionAnswers') : t('classPlayer.matching.instruction')
        }}</span>
        <span class="btn-group is-sm class-player-tools">
          <strong class="class-player-count" aria-live="polite">
            {{ t('classPlayer.matching.paired', { paired: pairedCount, total: words.length }) }}
          </strong>
          <button
            v-if="hintLimit > 0 && !done"
            type="button"
            class="btn btn-secondary btn-sm"
            :disabled="hintsLeft === 0 || busy"
            data-testid="matching-hint"
            @click="askHint"
          >
            {{
              hintLimit === 1
                ? hintsLeft
                  ? t('classPlayer.matching.useHint')
                  : t('classPlayer.matching.hintUsed')
                : t('classPlayer.activity.showHint')
            }}
          </button>
        </span>
      </div>
    </header>

    <p v-if="hintText" class="class-quiz-hint" role="status"><CjkText :text="hintText" /></p>

    <div class="class-matching">
      <div class="class-matching-column">
        <h2 class="page-eyebrow">
          {{ answersInChinese ? t('classPlayer.matching.question') : t('classPlayer.matching.chinese') }}
        </h2>
        <button
          v-for="word in words"
          :key="word.id"
          type="button"
          class="class-matching-item"
          :class="itemClass(word.id)"
          :aria-pressed="selectedWord === word.id"
          :disabled="done"
          :data-testid="`match-word-${word.id}`"
          @click="pickWord(word.id)"
        >
          <span class="class-matching-copy">
            <span class="hanzi class-matching-chinese" lang="zh-CN">{{ word.chinese }}</span>
            <span v-if="word.pinyin" class="type-caption">{{ word.pinyin }}</span>
          </span>
          <span v-if="pairs[word.id]" class="class-matching-pair">
            <IconGlyph :name="markIcon(word.id)" :size="14" />
            <CjkText :text="pairs[word.id]" />
            <span v-if="results[word.id] === false" class="sr-only">{{ t('classPlayer.matching.tryAgain') }}</span>
          </span>
        </button>
      </div>
      <div class="class-matching-column">
        <h2 class="page-eyebrow">
          {{ answersInChinese ? t('classPlayer.matching.answer') : t('classPlayer.matching.meaning') }}
        </h2>
        <button
          v-for="(meaning, meaningIndex) in meanings"
          :key="`${meaning}-${meaningIndex}`"
          type="button"
          class="class-matching-item"
          :class="meaningClass(meaning)"
          :disabled="done || (selectedWord === null && !usedMeanings.has(meaning))"
          @click="pickMeaning(meaning)"
        >
          <span class="class-matching-copy">
            <span class="class-matching-meaning"><CjkText :text="meaning" /></span>
          </span>
          <span v-if="usedMeanings.has(meaning)" class="class-matching-pair">
            <IconGlyph :name="markIcon(wordIdFor(meaning))" :size="14" />
            <span class="cjk-text" lang="zh-CN">{{ wordFor(meaning) }}</span>
            <span v-if="results[wordIdFor(meaning)] === false" class="sr-only">{{
              t('classPlayer.matching.tryAgain')
            }}</span>
          </span>
        </button>
      </div>
    </div>

    <template v-if="!done">
      <!-- Some pairs are wrong: a band that says how many, and how to fix them. -->
      <FocusFooter v-if="wrongCount" tone="wrong">
        <template #start>
          <div class="class-focus-band" role="status">
            <span class="class-focus-band-mark is-wrong" aria-hidden="true"><IconGlyph name="close" :size="24" /></span>
            <div class="class-focus-band-copy">
              <p class="class-focus-band-title">
                {{ t('classPlayer.matching.wrongTitle', { count: wrongCount, total: words.length }, wrongCount) }}
              </p>
              <p class="class-focus-sub">{{ t('classPlayer.matching.wrongHint') }}</p>
            </div>
          </div>
        </template>
        <template #end>
          <button type="button" class="btn btn-secondary btn-lg" :disabled="busy" @click="tryAgain">
            {{ t('classPlayer.matching.tryAgain') }}
          </button>
          <button
            type="button"
            class="btn btn-primary btn-lg"
            :disabled="pairedCount < words.length || busy"
            :aria-busy="busy"
            data-testid="activity-finish"
            @click="check"
          >
            {{ t('classPlayer.matching.check') }}
          </button>
        </template>
      </FocusFooter>
      <FocusFooter v-else>
        <template #start>
          <button type="button" class="btn btn-secondary btn-lg" :disabled="!pairedCount || busy" @click="reset">
            {{ t('classPlayer.matching.reset') }}
          </button>
        </template>
        <template #end>
          <button
            type="button"
            class="btn btn-primary btn-lg"
            :disabled="pairedCount < words.length || busy"
            :aria-busy="busy"
            data-testid="activity-finish"
            @click="check"
          >
            {{ t('classPlayer.matching.check') }}
          </button>
        </template>
      </FocusFooter>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import { studentClassesApi } from '@/api/classes'
import IconGlyph from '@/components/common/IconGlyph.vue'
import FocusFooter from '@/components/layout/FocusFooter.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classPlayerMessages } from '@/plugins/classPlayerMessages'
import type { LearningStage } from '@/types/api'
import type { ActivityOutcome, MatchWord } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { splitCjkRuns } from '@/utils/cjkText'

/**
 * Match each Chinese word with its meaning, or each question with its answer when the right column is Chinese too. The
 * server checks the pairs: wrong ones stay marked until the student changes them, right ones stay. Once all are right
 * (`done`) the page's result band replaces the footer. Hints: any number in a Guided activity, one in a Supported one,
 * none in an Independent one.
 */
const props = defineProps<{
  title: string
  activityId: number
  words: readonly MatchWord[]
  meanings: readonly string[]
  stage: LearningStage | null
  done: boolean
}>()
const emit = defineEmits<{ outcome: [outcome: ActivityOutcome]; error: [message: string] }>()

const { t } = useLocalMessages({ classPlayer: classPlayerMessages })

const pairs = ref<Record<number, string>>({})
const selectedWord = ref<number | null>(null)
const results = ref<Record<number, boolean>>({})
const busy = ref(false)
const hintText = ref('')
const hintsUsed = ref(0)

const answersInChinese = computed(() => props.meanings.some((meaning) => splitCjkRuns(meaning).some((run) => run.cjk)))
const hintLimit = computed(() => (props.stage === 'INDEPENDENT' ? 0 : props.stage === 'SCAFFOLDED' ? 1 : Infinity))
const hintsLeft = computed(() => Math.max(0, hintLimit.value - hintsUsed.value))
const pairedCount = computed(() => Object.keys(pairs.value).length)
const usedMeanings = computed(() => new Set(Object.values(pairs.value)))
const wrongCount = computed(() => Object.values(results.value).filter((correct) => !correct).length)

function itemClass(wordId: number) {
  return {
    'is-selected': selectedWord.value === wordId,
    'is-paired': Boolean(pairs.value[wordId]),
    'is-wrong': results.value[wordId] === false,
    'is-right': results.value[wordId] === true,
  }
}

function meaningClass(meaning: string) {
  return usedMeanings.value.has(meaning) ? itemClass(wordIdFor(meaning)) : {}
}

function markIcon(wordId: number): string {
  if (results.value[wordId] === false) return 'close'
  return results.value[wordId] === true ? 'check' : 'link'
}

function wordIdFor(meaning: string): number {
  return Number(Object.entries(pairs.value).find(([, value]) => value === meaning)?.[0])
}

function wordFor(meaning: string): string {
  return props.words.find((word) => word.id === wordIdFor(meaning))?.chinese ?? ''
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
    pickWord(wordIdFor(meaning))
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
}

function reset(): void {
  pairs.value = {}
  results.value = {}
  selectedWord.value = null
}

/** Takes the wrong pairs apart; the right ones stay. */
function tryAgain(): void {
  const keptPairs = { ...pairs.value }
  const keptResults = { ...results.value }
  for (const [wordId, correct] of Object.entries(results.value)) {
    if (correct) continue
    delete keptPairs[Number(wordId)]
    delete keptResults[Number(wordId)]
  }
  pairs.value = keptPairs
  results.value = keptResults
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
    if (outcome.done) {
      results.value = Object.fromEntries(props.words.map((word) => [word.id, true]))
      emit('outcome', outcome)
    }
  } catch (reason) {
    emit('error', resolveApiError(reason, t('classPlayer.matching.checkError')))
  } finally {
    busy.value = false
  }
}
</script>
