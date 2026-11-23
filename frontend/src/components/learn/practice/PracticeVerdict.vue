<template>
  <PracticeFeedback :correct="check.correct" :title="title" :note="note" :xp="xp">
    {{ lead }}<span :lang="hasHan(check.correctAnswer) ? 'zh-CN' : undefined">{{ check.correctAnswer }}</span>
    <template v-if="check.pinyin && check.pinyin !== check.correctAnswer"> · {{ check.pinyin }}</template>
    <template v-if="check.meaning && check.meaning !== check.correctAnswer"> · {{ check.meaning }}</template>
  </PracticeFeedback>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { useLocalMessages } from '@/composables/useLocalMessages'
import { practiceAnswerMessages } from '@/plugins/practiceAnswerMessages'
import type { AnswerCheck } from '@/types/learn'
import { hasHan } from '@/utils/cjkText'

import PracticeFeedback from './PracticeFeedback.vue'
import type { CheckedAnswer } from './usePracticeRun'

/**
 * The verdict on one answer, by how sure the student was: right and sure; right but guessed (it comes back once more
 * in this run); wrong though sure (it leads the Mistakes list); wrong while guessing; skipped; skipped because the audio
 * did not play (not counted). The answer follows the title ("It was …" when it was not given); the note says what
 * happens next, then the explanation.
 */
const props = defineProps<{
  check: AnswerCheck
  /** The answer as checked: its text and how sure the student was, both null for a skip. */
  sent: CheckedAnswer
  /** The item is queued once more (a right guess asked for the first time). */
  comesBack?: boolean
  xp?: number
}>()

const { t } = useLocalMessages({ answer: practiceAnswerMessages })

const title = computed(() => {
  const { answer, confidence } = props.sent
  if (answer === null) return t('answer.skipped')
  if (props.check.correct) return confidence === 'GUESS' ? t('answer.correctGuessed') : t('answer.correctSure')
  return confidence === 'SURE' ? t('answer.wrongSure') : t('answer.wrongGuess')
})

/** "It was" before the right answer, when the learner's answer was not it. */
const lead = computed(() => (props.check.correct ? '' : `${t('answer.itWas')} `))

const note = computed(() => {
  const parts: string[] = []
  if (props.check.correct) {
    if (props.comesBack) parts.push(t('answer.comesBack'))
  } else if (props.sent.audioFailed) {
    parts.push(t('answer.audioNotCounted'))
  } else {
    parts.push(props.sent.confidence === 'SURE' ? t('answer.topOfMistakes') : t('answer.onMistakes'))
  }
  if (props.check.explanation) parts.push(props.check.explanation)
  return parts.join(' ')
})
</script>
