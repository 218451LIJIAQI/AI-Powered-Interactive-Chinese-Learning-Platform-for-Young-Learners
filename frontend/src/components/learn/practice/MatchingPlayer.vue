<template>
  <div class="practice-player">
    <p class="practice-counter" aria-live="polite" data-testid="practice-counter">{{ counterLabel }}</p>

    <div class="practice-match">
      <ul class="practice-match-column" :aria-label="t('practice.words')">
        <li v-for="item in set.items" :key="item.key">
          <button
            :ref="(el) => setWordButton(item.key, el as HTMLButtonElement | null)"
            class="practice-match-tile"
            :class="tileClass(item.key, selected === item.key)"
            type="button"
            :aria-pressed="selected === item.key"
            :disabled="locked"
            :data-testid="`word-${item.key}`"
            @click="selectWord(item.key)"
          >
            <span class="practice-match-index" aria-hidden="true">{{ pairNumbers.get(item.key) }}</span>
            <span class="practice-match-word">
              <span class="hanzi practice-match-hanzi" lang="zh-CN">{{ item.hanzi }}</span>
              <span class="practice-match-pinyin">{{ item.pinyin }}</span>
              <span v-if="pairs.has(item.key)" class="sr-only">
                — {{ t('practice.wordPairedWith', { meaning: pairs.get(item.key) }) }}
              </span>
              <span v-if="checked" class="sr-only">
                — {{ t(results.get(item.key) ? 'practice.rightPair' : 'practice.wrongPair') }}
              </span>
            </span>
            <IconGlyph
              v-if="checked"
              class="practice-match-mark"
              :name="results.get(item.key) ? 'correct' : 'incorrect'"
              :size="16"
            />
          </button>
        </li>
      </ul>
      <ul class="practice-match-column" :aria-label="t('practice.meanings')">
        <li v-for="{ meaning, word } in meaningRows" :key="meaning">
          <button
            class="practice-match-tile"
            :class="word && tileClass(word.key)"
            type="button"
            :disabled="locked || selected === null"
            :aria-label="meaningLabel(meaning, word?.key, word?.hanzi)"
            :data-testid="`meaning-${meaning}`"
            @click="pairWith(meaning)"
          >
            <span class="practice-match-index" aria-hidden="true">{{ word && pairNumbers.get(word.key) }}</span>
            <span class="practice-match-word">{{ meaning }}</span>
            <IconGlyph
              v-if="checked && word"
              class="practice-match-mark"
              :name="results.get(word.key) ? 'correct' : 'incorrect'"
              :size="16"
            />
          </button>
        </li>
      </ul>
    </div>

    <PracticeHint :active="!checked" :hint="hint" type="MATCHING" :report-context="hintReportContext(set)" />

    <PracticeFeedback
      v-if="checked"
      :correct="rightCount === set.items.length"
      :title="t('practice.pairsRight', { right: rightCount, total: set.items.length })"
      :xp="rightCount === set.items.length && !set.done ? set.xpReward : 0"
    />
    <p v-if="run.error.value" class="field-error" role="alert">{{ run.error.value }}</p>

    <div class="practice-footer">
      <div v-if="!checked" class="btn-group">
        <button
          class="btn btn-secondary"
          type="button"
          :disabled="locked"
          :aria-disabled="!pairs.size"
          data-testid="reset"
          @click="reset"
        >
          {{ t('practice.reset') }}
        </button>
      </div>
      <div class="practice-footer-end">
        <button
          v-if="!checked"
          ref="checkButton"
          class="btn btn-primary"
          type="button"
          :disabled="pairs.size < set.items.length || run.busy.value"
          :aria-busy="run.busy.value"
          data-testid="matching-check"
          @click="checkPairs"
        >
          <IconGlyph name="check" :size="16" />{{ t('common.checkAnswer') }}
        </button>
        <button
          v-else
          ref="resultButton"
          class="btn btn-primary"
          type="button"
          :disabled="run.busy.value"
          :aria-busy="run.busy.value"
          data-testid="continue"
          @click="finish"
        >
          {{ t('common.seeResult') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'

import { getAuthSessionVersion } from '@/api/http'
import { practiceApi } from '@/api/learn'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { notifyProgressChanged } from '@/composables/useProgressRefresh'
import { practiceMessages } from '@/plugins/practiceMessages'
import type { AnswerCheck, PracticeSet } from '@/types/learn'

import PracticeFeedback from './PracticeFeedback.vue'
import PracticeHint from './PracticeHint.vue'
import type { PracticeCompletion } from './practiceTypes'
import { usePracticeRun } from './usePracticeRun'
import { hintReportContext, useStuckHint } from './useStuckHint'

/**
 * Matching: click a word, then its meaning; each pair carries a number, so pairs read across the two columns and without
 * colour. Check grades every pair on the server (a wrong pair becomes a mistake) and marks it right or wrong.
 * The learner can ask for at most one pair as a hint for the whole board.
 */
const props = defineProps<{ set: PracticeSet }>()
const emit = defineEmits<{ finished: [completion: PracticeCompletion] }>()

const { t } = useLocalMessages({ practice: practiceMessages })
const run = usePracticeRun(() => props.set, t)

const selected = ref<string | null>(null)
const pairs = ref(new Map<string, string>())
const results = ref(new Map<string, boolean>())
const wordButtons = new Map<string, HTMLButtonElement>()
const checkButton = ref<HTMLButtonElement | null>(null)
const resultButton = ref<HTMLButtonElement | null>(null)

function setWordButton(key: string, el: HTMLButtonElement | null): void {
  if (el) wordButtons.set(key, el)
  else wordButtons.delete(key)
}

const checked = computed(() => results.value.size > 0)
const locked = computed(() => checked.value || run.busy.value)
const pairNumbers = computed(() => new Map([...pairs.value.keys()].map((key, index) => [key, index + 1])))
const pairedWords = computed(() => {
  const paired = new Map<string, PracticeSet['items'][number]>()
  for (const word of props.set.items) {
    const meaning = pairs.value.get(word.key)
    if (meaning !== undefined) paired.set(meaning, word)
  }
  return paired
})
const meaningRows = computed(() =>
  props.set.matchingChoices.map((meaning) => ({ meaning, word: pairedWords.value.get(meaning) })),
)
const rightCount = computed(() => [...results.value.values()].filter(Boolean).length)

const hint = useStuckHint({
  itemKey: computed(() => `board-${props.set.id}`),
  open: computed(() => !locked.value),
  perSetBudget: false,
  load() {
    // The board is one question with one hint: the pair of the first word not paired yet (whether a pair is wrong is
    // unknown before the board is checked), or of the first word when every word has a pair.
    const word = props.set.items.find((item) => !pairs.value.has(item.key)) ?? props.set.items[0]!
    return practiceApi.hint('MATCHING', props.set.id, word.key, props.set)
  },
})

watch(pairs, () => hint.poke())

const counterLabel = computed(() => {
  const paired = t('practice.pairedOf', { paired: pairs.value.size, total: props.set.items.length })
  return checked.value ? paired : `${paired} · ${t('practice.matchingHint')}`
})

function selectWord(key: string): void {
  if (locked.value) return
  selected.value = selected.value === key ? null : key
}

function pairWith(meaning: string): void {
  if (locked.value || selected.value === null) return
  const next = new Map(pairs.value)
  const previousOwner = pairedWords.value.get(meaning)
  if (previousOwner) next.delete(previousOwner.key)
  next.set(selected.value, meaning)
  pairs.value = next
  selected.value = null
  // Clearing `selected` disables the focused meaning button; move focus to the next unpaired word or Check answer.
  const nextWord = props.set.items.find((item) => !next.has(item.key))
  void nextTick(() => {
    if (nextWord) wordButtons.get(nextWord.key)?.focus()
    else checkButton.value?.focus()
  })
}

/** With no pair made, Reset stays focusable (aria-disabled) and does nothing. */
function reset(): void {
  if (locked.value || !pairs.value.size) return
  pairs.value = new Map()
  selected.value = null
  run.error.value = null
}

function tileClass(key: string, isSelected = false): string | undefined {
  const verdict = results.value.get(key)
  if (verdict !== undefined) return verdict ? 'is-correct' : 'is-wrong'
  if (isSelected) return 'is-selected'
  return pairs.value.has(key) ? 'is-paired' : undefined
}

function meaningLabel(meaning: string, wordKey?: string, wordHanzi?: string | null): string {
  if (!wordKey || !wordHanzi) return meaning
  const base = t('practice.pairedWith', { meaning, word: wordHanzi })
  if (!checked.value) return base
  return `${base} — ${t(results.value.get(wordKey) ? 'practice.rightPair' : 'practice.wrongPair')}`
}

const answers = computed(() =>
  props.set.items.map((item) => ({ itemKey: item.key, answer: pairs.value.get(item.key) ?? null })),
)
const checkedPairs = new Map<string, { answer: string | null; verdict: AnswerCheck }>()

async function checkPairs(): Promise<void> {
  if (locked.value || pairs.value.size < props.set.items.length) return
  const current = props.set
  const session = getAuthSessionVersion()
  const runKey = `${current.type}:${current.id}:${current.runSignature ?? ''}`
  const verdicts = await run.run(async () => {
    let recordedMistake = false
    try {
      const checks = await Promise.allSettled(
        answers.value.map(async (answer) => {
          const cacheKey = `${runKey}:${answer.itemKey}`
          const previous = checkedPairs.get(cacheKey)
          if (previous && previous.answer === answer.answer) return previous.verdict
          const verdict = await practiceApi.check(current.type, current.id, answer)
          checkedPairs.set(cacheKey, { answer: answer.answer, verdict })
          if (!verdict.correct) recordedMistake = true
          return verdict
        }),
      )
      return checks.map((check) => {
        if (check.status === 'rejected') throw check.reason
        return check.value
      })
    } finally {
      if (recordedMistake && session === getAuthSessionVersion()) notifyProgressChanged()
    }
  })
  if (!verdicts) return
  results.value = new Map(props.set.items.map((item, index) => [item.key, verdicts[index]?.correct === true]))
  await nextTick()
  resultButton.value?.focus()
}

async function finish(): Promise<void> {
  const result = await run.complete(answers.value)
  if (!result) return
  const recap = props.set.items.map((item) => `${item.hanzi} ${pairs.value.get(item.key)}`).join(' · ')
  emit('finished', { result, recap })
}
</script>
