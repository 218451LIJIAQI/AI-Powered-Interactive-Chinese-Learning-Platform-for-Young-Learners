<template>
  <div class="practice-player" data-testid="matching-player">
    <div class="practice-meter">
      <span class="practice-counter" aria-live="polite">
        {{ t('practice.pairedOf', { paired: pairs.size, total: set.items.length }) }}
      </span>
      <ProgressBar
        :progress="ratioPercent(pairs.size, set.items.length)"
        :label="t('practice.setProgress')"
        class="is-sm practice-meter-bar"
      />
    </div>
    <p class="practice-hint practice-match-hint">{{ t('practice.matchingHint') }}</p>

    <div class="practice-match">
      <ul class="practice-match-column" :aria-label="t('practice.words')">
        <li v-for="item in set.items" :key="item.key">
          <button
            class="practice-match-tile"
            :class="tileClass(item.key)"
            type="button"
            :aria-pressed="selected === item.key"
            :disabled="checked"
            :data-testid="`word-${item.key}`"
            @click="selectWord(item.key)"
          >
            <span class="practice-match-index" aria-hidden="true">{{ pairNumber(item.key) }}</span>
            <span class="hanzi practice-match-hanzi" lang="zh-CN">{{ item.hanzi }}</span>
            <span class="practice-match-pinyin">{{ item.pinyin }}</span>
            <IconGlyph v-if="checked" :name="results.get(item.key) ? 'correct' : 'incorrect'" :size="18" />
          </button>
        </li>
      </ul>
      <ul class="practice-match-column" :aria-label="t('practice.meanings')">
        <li v-for="meaning in set.matchingChoices" :key="meaning">
          <button
            class="practice-match-tile is-meaning"
            :class="meaningClass(meaning)"
            type="button"
            :disabled="checked || selected === null"
            :aria-label="meaningLabel(meaning)"
            :data-testid="`meaning-${meaning}`"
            @click="pairWith(meaning)"
          >
            <span v-if="ownerOf(meaning)" class="practice-match-index" aria-hidden="true">
              {{ pairNumber(ownerOf(meaning)!) }}
            </span>
            {{ meaning }}
          </button>
        </li>
      </ul>
    </div>

    <div class="practice-footer">
      <button class="btn btn-ghost" type="button" :disabled="!pairs.size || checked" @click="reset">
        <IconGlyph name="undo" :size="16" />{{ t('practice.reset') }}
      </button>
      <button
        v-if="!checked"
        class="btn btn-primary"
        type="button"
        :disabled="pairs.size < set.items.length || run.busy.value"
        :aria-busy="run.busy.value"
        data-testid="matching-check"
        @click="checkPairs"
      >
        {{ t('practice.check') }}
      </button>
      <button v-else class="btn btn-primary" type="button" :disabled="run.busy.value" @click="finish">
        {{ t('practice.seeResult') }}
      </button>
    </div>
    <p v-if="checked" class="practice-feedback" role="status">
      {{ t('practice.pairsRight', { right: rightCount, total: set.items.length }) }}
    </p>
    <p v-if="run.error.value" class="field-error" role="alert">{{ run.error.value }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { practiceMessages } from '@/plugins/practiceMessages'
import type { PracticeResult, PracticeSet } from '@/types/learn'
import { ratioPercent } from '@/utils/format'

import { usePracticeRun } from './usePracticeRun'

/**
 * Matching: click a word, then its meaning; each pair carries a number, so pairs read without colour. Check grades every
 * pair on the server (a wrong pair becomes a mistake) and marks it right or wrong.
 */
const props = defineProps<{ set: PracticeSet }>()
const emit = defineEmits<{ finished: [result: PracticeResult] }>()

const { t } = useLocalMessages({ practice: practiceMessages })
const run = usePracticeRun(
  () => props.set,
  () => t('practice.saveFailed'),
)

const selected = ref<string | null>(null)
const pairs = ref(new Map<string, string>())
const results = ref(new Map<string, boolean>())
const checked = ref(false)

const order = computed(() => [...pairs.value.keys()])
const rightCount = computed(() => [...results.value.values()].filter(Boolean).length)

function pairNumber(key: string): string {
  const position = order.value.indexOf(key)
  return position >= 0 ? String(position + 1) : ''
}

function ownerOf(meaning: string): string | null {
  for (const [key, value] of pairs.value) if (value === meaning) return key
  return null
}

function selectWord(key: string): void {
  selected.value = selected.value === key ? null : key
}

function pairWith(meaning: string): void {
  if (selected.value === null) return
  const next = new Map(pairs.value)
  const previousOwner = ownerOf(meaning)
  if (previousOwner) next.delete(previousOwner)
  next.set(selected.value, meaning)
  pairs.value = next
  selected.value = null
}

function reset(): void {
  pairs.value = new Map()
  selected.value = null
}

function tileClass(key: string): Record<string, boolean> {
  return {
    'is-selected': selected.value === key,
    'is-paired': pairs.value.has(key),
    'is-correct': checked.value && results.value.get(key) === true,
    'is-wrong': checked.value && results.value.get(key) === false,
  }
}

function meaningClass(meaning: string): Record<string, boolean> {
  const owner = ownerOf(meaning)
  return {
    'is-paired': owner !== null,
    'is-correct': checked.value && owner !== null && results.value.get(owner) === true,
    'is-wrong': checked.value && owner !== null && results.value.get(owner) === false,
  }
}

function meaningLabel(meaning: string): string {
  const owner = ownerOf(meaning)
  const word = props.set.items.find((item) => item.key === owner)?.hanzi
  return word ? t('practice.pairedWith', { meaning, word }) : meaning
}

const answers = computed(() =>
  props.set.items.map((item) => ({ itemKey: item.key, answer: pairs.value.get(item.key) ?? null })),
)

async function checkPairs(): Promise<void> {
  const verdicts = await run.checkAll(answers.value)
  if (!verdicts) return
  results.value = new Map(props.set.items.map((item, index) => [item.key, verdicts[index]?.correct === true]))
  checked.value = true
}

async function finish(): Promise<void> {
  const result = await run.complete(answers.value)
  if (result) emit('finished', result)
}
</script>
