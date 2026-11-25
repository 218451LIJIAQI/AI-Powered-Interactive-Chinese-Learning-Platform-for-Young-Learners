<template>
  <HintPanel
    variant="practice"
    :visible="visible"
    :text="sentence || null"
    :offered="hint.offered.value"
    :loading="hint.loading.value"
    :error="errorMessage"
    :offer-text="offerText"
    :footnote="t('answer.hintNeverCostsXp')"
    :report-context="reportContext"
    :hint-key="step"
    :reported="reported"
    :own-toast="ownToast"
    @show="hint.show"
    @reported="onReported"
  >
    <slot />
  </HintPanel>
</template>

<script setup lang="ts">
import { computed, shallowRef } from 'vue'

import HintPanel from '@/components/learning/HintPanel.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { hintChromeMessages } from '@/plugins/hintChromeMessages'
import { practiceAnswerMessages } from '@/plugins/practiceAnswerMessages'
import type { HintStep, PracticeType } from '@/types/learn'
import { tracingGlyphs } from '@/utils/tracing'

import type { StuckHint } from './useStuckHint'

const props = withDefaults(
  defineProps<{
    hint: StuckHint
    /** Typing and listening use activity-specific first-sound clues. */
    type?: PracticeType | null
    /** The answers in the order shown, to name one as "answer 2". */
    options?: readonly string[]
    /** What the hint belongs to (a set and item, a lesson question), sent with a report so the team can find it. */
    reportContext: string
    /** Set false after answering so HintPanel hides its card while keeping its own confirmation toast alive. */
    active?: boolean
    /** Forwarded to HintPanel when a parent owns the confirmation toast. */
    ownToast?: boolean
  }>(),
  { type: null, options: () => [], active: true, ownToast: true },
)
const emit = defineEmits<{ reported: [] }>()

const { t } = useLocalMessages({ answer: practiceAnswerMessages, hintChrome: hintChromeMessages })

const visible = computed(() => props.active && props.hint.mode.value !== 'OFF')
const step = computed(() => props.hint.hint.value)
const errorMessage = computed(() =>
  props.hint.failed.value ? (props.hint.refusal.value ?? t('answer.hintFailed')) : null,
)
const offerText = computed(() => {
  const prompt = props.hint.mode.value === 'ON_REQUEST' ? t('hintChrome.hintOfferAsk') : t('hintChrome.hintOffer')
  const left = props.hint.budgetLeft.value
  return left !== null && left > 0 ? `${prompt} · ${t('answer.hintsLeftInSet', { count: left }, left)}` : prompt
})

/** The hint a report was sent for; the next item's hint can be reported again. */
const reportedStep = shallowRef<HintStep | null>(null)
const reported = computed(() => step.value !== null && reportedStep.value === step.value)

function onReported(): void {
  reportedStep.value = step.value
  emit('reported')
}

function optionName(text: string): string {
  const index = props.options.indexOf(text)
  return index >= 0 ? t('answer.optionN', { n: index + 1 }) : text
}

function sentenceOf(current: HintStep): string {
  const [first = '', second = ''] = current.words
  const pinyin = current.pinyin[0] ?? ''
  const count = current.count ?? 0
  switch (current.kind) {
    case 'RULE_OUT':
      return pinyin
        ? t('answer.hintRuleOutPinyin', { option: optionName(first), word: first, pinyin })
        : t('answer.hintRuleOut', { option: optionName(first), word: first })
    case 'CLUE':
      return current.text ?? ''
    case 'FIRST_SOUND':
      if (props.type === 'TYPING') return t('answer.hintTypingStart', { sound: current.text, count }, count)
      if (props.type === 'LISTENING') return t('answer.hintListenFirst', { sound: current.text })
      return t('answer.hintFirstSound', { sound: current.text })
    case 'FIRST_LETTER':
      return t('answer.hintFirstLetter', { letter: first })
    case 'LISTEN_SLOW':
      return t('answer.hintListenSlow')
    case 'TWO_LEFT':
      return t('answer.hintTwoLeft', { first: optionName(first), second: optionName(second) })
    case 'FIRST_TILE':
      return t('answer.hintFirstTile', { word: first })
    case 'LEAVE_OUT':
      return t('answer.hintLeaveOut', { words: current.words.join(' · ') }, current.words.length)
    case 'ORDER_START':
      return t('answer.hintOrderStart', { words: current.words.join(' ') })
    case 'INITIALS':
      return t('answer.hintInitials', { sounds: current.words.map((sound) => `${sound}…`).join(' ') })
    case 'TYPE_THIS':
      return t('answer.hintTypeThis', { pinyin: current.text })
    case 'PAIR':
      return pinyin
        ? t('answer.hintPair', { word: first, pinyin, meaning: second })
        : t('answer.hintPairNoPinyin', { word: first, meaning: second })
    case 'STROKE_COUNT':
      return t('answer.hintStrokeCount', { characters: tracingGlyphs(first).join('') || first, count }, count)
    case 'STROKE_ORDER':
      return t('answer.hintStrokeOrder')
  }
}

const sentence = computed(() => (step.value ? sentenceOf(step.value) : ''))
</script>
