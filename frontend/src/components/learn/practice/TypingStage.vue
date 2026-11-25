<template>
  <section class="practice-typing" :aria-labelledby="`${baseId}-meaning`" @keydown="onKey">
    <p class="practice-typing-label">{{ t('answer.typeItIn') }}</p>
    <p :id="`${baseId}-meaning`" class="practice-typing-meaning">“{{ meaning }}”</p>

    <label class="sr-only" :for="`${baseId}-input`">{{ t('answer.typeLabel') }}</label>
    <input
      :id="`${baseId}-input`"
      ref="input"
      v-model="typed"
      class="input practice-typing-input"
      type="text"
      autocomplete="off"
      spellcheck="false"
      maxlength="200"
      :placeholder="t('answer.typePlaceholder')"
      :disabled="locked"
      :aria-describedby="`${baseId}-help`"
      data-testid="typing-input"
    />

    <p class="sr-only" role="status" aria-live="polite">
      {{ candidates.length ? t('answer.candidates', { pinyin: typed.trim() }) : '' }}
    </p>
    <div
      v-if="candidates.length"
      class="practice-candidates"
      role="group"
      :aria-label="t('answer.candidates', { pinyin: typed.trim() })"
      data-testid="candidates"
    >
      <button
        v-for="(word, index) in candidates"
        :key="word"
        class="practice-candidate"
        :class="candidateClass(word)"
        type="button"
        lang="zh-CN"
        :aria-pressed="model === word"
        :disabled="locked || ruledOut.includes(word)"
        :data-testid="`candidate-${index}`"
        @click="choose(word)"
      >
        <span class="practice-candidate-key" aria-hidden="true">{{ index + 1 }}</span>
        <span class="hanzi">{{ word }}</span>
      </button>
    </div>
    <p v-else-if="noMatch" class="practice-typing-nomatch" role="status">
      {{ t('answer.noCandidates', { pinyin: typed.trim() }) }}
    </p>
    <div v-else-if="candidateError" class="btn-group">
      <span class="field-error" role="alert">{{ candidateError }}</span>
      <button class="btn btn-secondary btn-sm" type="button" :disabled="locked" @click="retryCandidates">
        {{ t('common.retry') }}
      </button>
    </div>
    <p :id="`${baseId}-help`" class="practice-typing-help">{{ t('answer.typeHelp') }}</p>
  </section>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, useId, useTemplateRef, watch } from 'vue'

import { practiceApi } from '@/api/learn'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { practiceAnswerMessages } from '@/plugins/practiceAnswerMessages'
import type { AnswerCheck } from '@/types/learn'
import { resolveApiError } from '@/utils/apiError'
import { hasHan } from '@/utils/cjkText'

const CANDIDATE_DELAY_MS = 250

const model = defineModel<string | null>({ required: true })
const typed = defineModel<string>('typed', { required: true })

const props = defineProps<{
  setId: number
  itemKey: string
  meaning: string | null
  locked: boolean
  check: AnswerCheck | null
  ruledOut: readonly string[]
}>()
const emit = defineEmits<{ poke: [] }>()

const { t } = useLocalMessages({ answer: practiceAnswerMessages })
const baseId = useId()
const input = useTemplateRef<HTMLInputElement>('input')

const candidates = ref<string[]>([])
const noMatch = ref(false)
const candidateError = ref<string | null>(null)
let lookup: ReturnType<typeof setTimeout> | undefined
let request = 0

function clearLookup(): void {
  clearTimeout(lookup)
  // Invalidate an in-flight lookup as soon as the input or item changes.
  request += 1
  candidates.value = []
  noMatch.value = false
  candidateError.value = null
}

onBeforeUnmount(() => {
  clearTimeout(lookup)
  request += 1
})

watch(typed, (value) => {
  emit('poke')
  model.value = null
  clearLookup()
  if (value.trim()) lookup = setTimeout(loadCandidates, CANDIDATE_DELAY_MS)
})

watch([() => props.setId, () => props.itemKey], clearLookup)

watch(
  () => props.locked,
  (locked, wasLocked) => {
    if (
      wasLocked &&
      !locked &&
      typed.value.trim() &&
      !candidates.value.length &&
      !noMatch.value &&
      !candidateError.value
    ) {
      void loadCandidates()
    }
  },
)

watch(
  () => props.ruledOut,
  (ruledOut) => {
    if (!props.locked && model.value !== null && ruledOut.includes(model.value)) model.value = null
  },
)

function retryCandidates(): void {
  focusInput()
  void loadCandidates()
}

async function loadCandidates(): Promise<void> {
  const mine = ++request
  const pinyin = typed.value.trim()
  if (!pinyin || props.locked) return
  candidateError.value = null
  try {
    const words = await practiceApi.candidates(props.setId, props.itemKey, pinyin)
    if (mine !== request || props.locked) return
    candidates.value = words
    // Say "no word" immediately for Chinese characters (expects Pinyin) or for accented/plain spelling long enough to be a word.
    const latinLength = pinyin
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-z]/gi, '').length
    noMatch.value = words.length === 0 && (hasHan(pinyin) || latinLength >= 3)
  } catch (reason) {
    if (mine === request && !props.locked) candidateError.value = resolveApiError(reason, t('answer.candidatesFailed'))
  }
}

function choose(word: string): void {
  if (props.locked || props.ruledOut.includes(word)) return
  model.value = word
  emit('poke')
}

function onKey(event: KeyboardEvent): void {
  if (
    event.isComposing ||
    event.key === 'Process' ||
    event.keyCode === 229 ||
    event.altKey ||
    event.ctrlKey ||
    event.metaKey
  )
    return
  if (!/^[1-9]$/.test(event.key) || !candidates.value.length) return
  event.preventDefault()
  const word = candidates.value[Number(event.key) - 1]
  if (word) choose(word)
}

function candidateClass(word: string): Record<string, boolean> {
  const verdict = props.check
  return {
    'is-correct': verdict !== null && word === verdict.correctAnswer,
    'is-wrong': verdict !== null && !verdict.correct && word === model.value,
    'is-ruled-out': verdict === null && props.ruledOut.includes(word),
  }
}

function focusInput(): void {
  void nextTick(() => input.value?.focus())
}

defineExpose({ focusInput })
</script>
