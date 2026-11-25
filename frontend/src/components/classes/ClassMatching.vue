<template>
  <div class="class-player">
    <AppToast :message="toast" />
    <header class="class-focus-head">
      <p class="page-eyebrow">{{ t('classPlayer.matching.title') }}</p>
      <h1 class="focus-title"><CjkText :text="title" /></h1>
      <div class="class-player-row class-focus-sub">
        <span>{{
          answersInChinese ? t('classPlayer.matching.instructionAnswers') : t('classPlayer.matching.instruction')
        }}</span>
        <strong class="class-player-count" aria-live="polite">
          {{ t('classPlayer.matching.paired', { paired: pairedCount, total: words.length }) }}
        </strong>
      </div>
    </header>

    <div ref="board" class="class-matching">
      <div class="class-matching-column">
        <h2 class="page-eyebrow">
          {{ answersInChinese ? t('classPlayer.matching.question') : t('classPlayer.matching.chinese') }}
        </h2>
        <button
          v-for="word in words"
          :key="word.id"
          :ref="(el) => setWordButton(word.id, el as HTMLButtonElement | null)"
          type="button"
          class="class-matching-item"
          :class="itemClass(word.id)"
          :aria-pressed="selectedWord === word.id"
          :disabled="done || busy"
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
            <span v-if="results[word.id] !== undefined" class="sr-only">{{
              t(results[word.id] ? 'classPlayer.matching.rightPair' : 'classPlayer.matching.wrongPair')
            }}</span>
          </span>
        </button>
      </div>
      <div class="class-matching-column">
        <h2 class="page-eyebrow">
          {{ answersInChinese ? t('classPlayer.matching.answer') : t('classPlayer.matching.meaning') }}
        </h2>
        <button
          v-for="{ text, word } in meaningRows"
          :key="text"
          type="button"
          class="class-matching-item"
          :class="word ? itemClass(word.id) : undefined"
          :disabled="done || busy || (selectedWord === null && !word)"
          @click="pickMeaning(text)"
        >
          <span class="class-matching-copy">
            <span class="class-matching-meaning"><CjkText :text="text" /></span>
          </span>
          <span v-if="word" class="class-matching-pair">
            <IconGlyph :name="markIcon(word.id)" :size="14" />
            <span class="cjk-text" lang="zh-CN">{{ word.chinese }}</span>
            <span v-if="results[word.id] !== undefined" class="sr-only">{{
              t(results[word.id] ? 'classPlayer.matching.rightPair' : 'classPlayer.matching.wrongPair')
            }}</span>
          </span>
        </button>
      </div>
    </div>

    <FocusFooter v-if="!done" :tone="wrongCount ? 'wrong' : null">
      <template #start>
        <!-- One button, Try again while a pair is wrong and Reset after: the focus stays on it. -->
        <button
          type="button"
          class="btn btn-secondary btn-lg"
          :disabled="busy"
          :aria-disabled="busy || (!wrongCount && !pairedCount)"
          @click="wrongCount ? tryAgain() : reset()"
        >
          {{ wrongCount ? t('classPlayer.matching.tryAgain') : t('classPlayer.matching.reset') }}
        </button>
      </template>
      <template #middle>
        <!-- Some pairs are wrong: a band that says how many, and how to fix them. -->
        <div v-if="wrongCount" class="class-focus-band" role="status">
          <span class="class-focus-band-mark is-wrong" aria-hidden="true"><IconGlyph name="close" :size="24" /></span>
          <div class="class-focus-band-copy">
            <p class="class-focus-band-title">
              {{ t('classPlayer.matching.wrongTitle', { count: wrongCount, total: words.length }, wrongCount) }}
            </p>
            <p class="class-focus-sub">{{ t('classPlayer.matching.wrongHint') }}</p>
          </div>
        </div>
        <HintPanel
          v-else-if="hintsAllowed"
          variant="class"
          :offered="offer.offered.value"
          :hint-key="hint?.wordId ?? null"
          :text="hintText"
          :loading="hintLoading"
          :error="hintError"
          :footnote="t('classPlayer.hint.changeInAccount')"
          :report-context="hintContext"
          :reported="hint !== null && reportedHintIds.has(hint.wordId)"
          :own-toast="false"
          @show="askHint"
          @reported="onHintReported"
        />
      </template>
      <!-- The board checks itself once every pair is made (S11.08); only a check that failed to reach the server needs a
           button, to send the same pairs again. -->
      <template v-if="checkFailed" #end>
        <button
          ref="checkAgainButton"
          type="button"
          class="btn btn-primary btn-lg"
          :disabled="busy"
          :aria-busy="busy"
          data-testid="matching-check-again"
          @click="check"
        >
          {{ t('classPlayer.matching.checkAgain') }}
        </button>
      </template>
    </FocusFooter>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'

import { studentClassesApi } from '@/api/classes'
import AppToast from '@/components/common/AppToast.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import FocusFooter from '@/components/layout/FocusFooter.vue'
import CjkText from '@/components/learning/CjkText.vue'
import HintPanel from '@/components/learning/HintPanel.vue'
import { useHintOffer } from '@/composables/useHintOffer'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useToast } from '@/composables/useToast'
import { classPlayerMessages } from '@/plugins/classPlayerMessages'
import type { ActivityOutcome, MatchHint, MatchWord } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { hasHan } from '@/utils/cjkText'

/**
 * Match each Chinese word with its meaning, or each question with its answer when the right column is Chinese too. The
 * board is checked by the server as soon as every pair is made, with no Check button (S11.08): wrong pairs stay marked
 * until the learner changes them, right ones stay, and the board is checked again once it is full with no pair marked
 * wrong. A check that could not reach the server offers "Check again". Once all are right (`done`) the page's result
 * band replaces the footer. When the lesson allows hints, the learner's setting controls when to offer the meaning
 * of the chosen word, or of the first one not paired yet. `error` reports a failed
 * check, and null when the next check starts, so the page never keeps an old failure beside a newer answer.
 */
const props = defineProps<{
  title: string
  activityId: number
  lessonTitle: string
  words: readonly MatchWord[]
  meanings: readonly string[]
  hintsAllowed: boolean
  done: boolean
}>()
const emit = defineEmits<{
  outcome: [outcome: ActivityOutcome]
  error: [message: string | null]
  busy: [busy: boolean]
}>()

const { t } = useLocalMessages({ classPlayer: classPlayerMessages })
const { toast, showToast } = useToast()

const board = ref<HTMLDivElement | null>(null)
const checkAgainButton = ref<HTMLButtonElement | null>(null)
const pairs = ref<Record<number, string>>({})
const selectedWord = ref<number | null>(null)
const results = ref<Record<number, boolean>>({})
const busy = ref(false)
watch(busy, (value) => emit('busy', value), { flush: 'sync' })
const reportedHintIds = reactive(new Set<number>())
function onHintReported(): void {
  if (hint.value) reportedHintIds.add(hint.value.wordId)
  showToast(t('aiSafety.reportSent'))
}
/** The last check of a full board did not reach the server: the same pairs can be sent again. */
const checkFailed = ref(false)
const hint = ref<MatchHint | null>(null)
const hintLoading = ref(false)
const hintError = ref<string | null>(null)
let active = true
let hintVersion = 0
const wordButtons = new Map<number, HTMLButtonElement>()
function setWordButton(wordId: number, element: HTMLButtonElement | null): void {
  if (element) wordButtons.set(wordId, element)
  else wordButtons.delete(wordId)
}
onBeforeUnmount(() => {
  active = false
  hintVersion++
  if (busy.value) emit('busy', false)
})

const answersInChinese = computed(() => props.meanings.some(hasHan))
const hintText = computed(() => {
  const answer = hint.value
  if (!answer) return null
  const word = props.words.find((item) => item.id === answer.wordId)
  if (!word) return null
  return t(answersInChinese.value ? 'classPlayer.matching.hintAnswer' : 'classPlayer.matching.hintMeaning', {
    word: word.chinese,
    meaning: answer.meaning,
  })
})
const pairedCount = computed(() => Object.keys(pairs.value).length)
const wrongCount = computed(() => Object.values(results.value).filter((correct) => !correct).length)

const wordsByMeaning = computed(() => {
  const paired = new Map<string, MatchWord>()
  for (const word of props.words) {
    const meaning = pairs.value[word.id]
    if (meaning !== undefined) paired.set(meaning, word)
  }
  return paired
})
const meaningRows = computed(() => props.meanings.map((text) => ({ text, word: wordsByMeaning.value.get(text) })))

// A full board with no pair marked wrong is checked at once. A board that still has a wrong pair waits for it to change:
// checking the same pairs again would only mark them again.
watch(pairedCount, (count) => {
  if (checkFailed.value) {
    checkFailed.value = false
    emit('error', null)
  }
  // The hint offer waits again for the new board, so an earlier hint request or error no longer applies.
  hintVersion++
  hintLoading.value = false
  hintError.value = null
  if (count === props.words.length && count > 0 && wrongCount.value === 0 && !props.done) void check()
})

const offer = useHintOffer({
  itemKey: pairedCount,
  open: computed(
    () =>
      props.hintsAllowed &&
      !props.done &&
      !busy.value &&
      !hint.value &&
      wrongCount.value === 0 &&
      pairedCount.value < props.words.length,
  ),
})

/** What a report of the hint is about: the lesson and the activity. */
const hintContext = computed(() => `${props.lessonTitle} · ${props.title}`)

function itemClass(wordId: number): string | undefined {
  const verdict = results.value[wordId]
  if (verdict !== undefined) return verdict ? 'is-right' : 'is-wrong'
  if (selectedWord.value === wordId) return 'is-selected'
  return pairs.value[wordId] ? 'is-paired' : undefined
}

function markIcon(wordId: number): string {
  if (results.value[wordId] === false) return 'close'
  return results.value[wordId] === true ? 'check' : 'link'
}

function pickWord(wordId: number): void {
  if (pairs.value[wordId]) {
    // Clicking a paired word undoes the pair.
    delete pairs.value[wordId]
    delete results.value[wordId]
    selectedWord.value = null
    return
  }
  selectedWord.value = selectedWord.value === wordId ? null : wordId
}

function pickMeaning(meaning: string): void {
  const pairedWord = wordsByMeaning.value.get(meaning)
  if (pairedWord) {
    pickWord(pairedWord.id)
    // Unpairing disables the meaning button when no word is selected; move keyboard focus to the unpaired word.
    void nextTick(() => {
      if (active) wordButtons.get(pairedWord.id)?.focus({ preventScroll: true })
    })
    return
  }
  const wordId = selectedWord.value
  if (wordId === null) return
  pairs.value[wordId] = meaning
  selectedWord.value = null
  // The next offer waits again when the pair count changes.
  if (hint.value?.wordId === wordId) hint.value = null
}

/** With no pair made, Reset stays focusable (aria-disabled) and does nothing. */
function reset(): void {
  if (!pairedCount.value) return
  pairs.value = {}
  results.value = {}
  selectedWord.value = null
}

/** Takes the wrong pairs apart; the right ones stay. */
function tryAgain(): void {
  for (const [wordId, correct] of Object.entries(results.value)) {
    if (correct) continue
    delete pairs.value[Number(wordId)]
    delete results.value[Number(wordId)]
  }
}

async function askHint(): Promise<void> {
  const wordId = selectedWord.value ?? props.words.find((word) => !pairs.value[word.id])?.id ?? null
  if (wordId === null || hintLoading.value) return
  const version = ++hintVersion
  hintLoading.value = true
  hintError.value = null
  try {
    const answer = await studentClassesApi.matchingHint(props.activityId, wordId)
    if (!active || version !== hintVersion || props.done || pairs.value[answer.wordId] !== undefined) return
    hint.value = answer
  } catch (reason) {
    if (active && version === hintVersion && !props.done && pairs.value[wordId] === undefined) {
      hintError.value = resolveApiError(reason, t('classPlayer.hint.error'))
    }
  } finally {
    if (active && version === hintVersion) hintLoading.value = false
  }
}

async function check(): Promise<void> {
  if (busy.value) return
  const hadBoardFocus = Boolean(
    board.value?.contains(document.activeElement) || document.activeElement === checkAgainButton.value,
  )
  busy.value = true
  checkFailed.value = false
  emit('error', null)
  try {
    const outcome = await studentClassesApi.checkMatching(
      props.activityId,
      Object.entries(pairs.value).map(([wordId, meaning]) => ({ wordId: Number(wordId), meaning })),
    )
    if (!active) return
    results.value = Object.fromEntries(outcome.pairs.map((pair) => [pair.wordId, pair.correct]))
    hint.value = null
    if (outcome.done) emit('outcome', outcome)
  } catch (reason) {
    if (!active) return
    checkFailed.value = true
    emit('error', resolveApiError(reason, t('classPlayer.matching.checkError')))
  } finally {
    if (active) {
      busy.value = false
      if (hadBoardFocus && !props.done) {
        void nextTick(() => {
          if (!active || props.done) return
          if (checkFailed.value) {
            checkAgainButton.value?.focus({ preventScroll: true })
            return
          }
          const firstWrong = props.words.find((word) => results.value[word.id] === false)
          if (firstWrong) wordButtons.get(firstWrong.id)?.focus({ preventScroll: true })
        })
      }
    }
  }
}
</script>
