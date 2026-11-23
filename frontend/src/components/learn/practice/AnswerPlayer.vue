<template>
  <div class="practice-player" :data-testid="isTyping ? undefined : `${set.type.toLowerCase()}-player`">
    <PracticeProgress :label="counterLabel" />

    <div v-if="isAgain" class="practice-combo">
      <span class="tag is-outline" data-testid="once-more">
        <IconGlyph name="retry" :size="12" />{{ t('answer.onceMore') }}
      </span>
    </div>

    <TypingStage
      v-if="isTyping && item"
      ref="typingStage"
      v-model="answer"
      v-model:typed="typed"
      :set-id="set.id"
      :item-key="item.key"
      :meaning="item.meaning"
      :locked="locked"
      :check="check"
      :ruled-out="hint.ruledOut.value"
      @poke="hint.poke"
    />

    <QuestionStage
      v-else-if="question"
      v-model="answer"
      v-model:audio-failed="audioFailed"
      :question="question"
      :locked="locked"
      :check="check"
      :draggable-tiles="isSentence"
      :ruled-out="hint.ruledOut.value"
    />

    <PracticeHint
      :active="!check"
      :hint="hint"
      :type="set.type"
      :options="question?.options.map((option) => option.text)"
      :report-context="hintReportContext(set, item?.key)"
    />

    <PracticeVerdict v-if="check && given" :check="check" :sent="given" :comes-back="comesBack" :xp="setXp" />
    <p v-if="run.error.value" class="field-error" role="alert">{{ run.error.value }}</p>

    <div class="practice-footer">
      <div v-if="!check" class="btn-group">
        <button
          v-if="!isSentence && !isTyping"
          class="btn btn-secondary"
          type="button"
          :disabled="run.busy.value"
          data-testid="skip"
          @click="submit(null, null)"
        >
          {{ t('common.skip') }}
        </button>
        <button
          v-else
          class="btn btn-secondary"
          type="button"
          :disabled="run.busy.value"
          :aria-disabled="!hasAnswer"
          data-testid="reset"
          @click="resetAnswer"
        >
          {{ t('practice.reset') }}
        </button>
      </div>
      <div class="practice-footer-end">
        <button
          v-if="check"
          ref="nextButton"
          class="btn btn-primary"
          type="button"
          :disabled="run.busy.value"
          :aria-busy="run.busy.value"
          data-testid="continue"
          @click="next"
        >
          {{ isLast ? t('common.seeResult') : t('practice.nextItem') }}
        </button>
        <ConfidenceActions
          v-else
          :disabled="answer === null || run.busy.value || hint.loading.value"
          :busy="run.busy.value"
          :hint-used="hint.used.value"
          :picked="answer !== null"
          @answer="submit(answer, $event)"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useTemplateRef, watch } from 'vue'

import { practiceApi } from '@/api/learn'
import IconGlyph from '@/components/common/IconGlyph.vue'
import QuestionStage from '@/components/learn/QuestionStage.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { practiceAnswerMessages } from '@/plugins/practiceAnswerMessages'
import { practiceMessages } from '@/plugins/practiceMessages'
import type {
  AnswerCheck,
  AnswerConfidence,
  PracticeItemAnswer,
  PracticeResult,
  PracticeSet,
  QuestionView,
} from '@/types/learn'
import { questionShortcut } from '@/utils/questionShortcut'

import ConfidenceActions from './ConfidenceActions.vue'
import PracticeHint from './PracticeHint.vue'
import PracticeProgress from './PracticeProgress.vue'
import PracticeVerdict from './PracticeVerdict.vue'
import TypingStage from './TypingStage.vue'
import type { RunTally } from './practiceTypes'
import { usePracticeRun, type CheckedAnswer } from './usePracticeRun'
import { hintReportContext, useStuckHint } from './useStuckHint'

/**
 * Listening, sentence building and pinyin typing share grading, hints and the retry queue. A Listening item whose audio
 * did not play can be skipped without counting: the server only shows its answer. The finished run goes out with its
 * tally (no recap: the done card says instead whether anything went to Mistakes).
 */
const props = defineProps<{ set: PracticeSet }>()
const emit = defineEmits<{ finished: [result: PracticeResult, recap: string, tally: RunTally] }>()

const { t } = useLocalMessages({ practice: practiceMessages, answer: practiceAnswerMessages })
const isListening = computed(() => props.set.type === 'LISTENING')
const isSentence = computed(() => props.set.type === 'DRAG_DROP')
const isTyping = computed(() => props.set.type === 'TYPING')

const run = usePracticeRun(() => props.set, t)
const queue = ref([...props.set.items])
const firstRetry = queue.value.length
const position = ref(0)
const item = computed(() => queue.value[position.value] ?? null)
const isLast = computed(() => position.value >= queue.value.length - 1)
const isAgain = computed(() => item.value !== null && position.value >= firstRetry)

const answers: PracticeItemAnswer[] = []
const tally = { answered: 0, mistakes: 0 }
const allRight = ref(true)
const answer = ref<string | null>(null)
const typed = ref('')
const check = ref<AnswerCheck | null>(null)
const given = ref<CheckedAnswer | null>(null)
const audioFailed = ref(false)
const nextButton = useTemplateRef<HTMLButtonElement>('nextButton')
const typingStage = useTemplateRef<InstanceType<typeof TypingStage>>('typingStage')
const locked = computed(() => check.value !== null || run.busy.value)
const comesBack = computed(() => check.value?.correct === true && given.value?.confidence === 'GUESS' && !isAgain.value)
const setXp = computed(() =>
  check.value?.correct && isLast.value && allRight.value && !props.set.done ? props.set.xpReward : 0,
)
const hint = useStuckHint({
  itemKey: computed(() => (item.value ? position.value + ':' + item.value.key : null)),
  budgetKey: computed(() => item.value?.key ?? null),
  open: computed(() => !locked.value && item.value !== null),
  load: () => practiceApi.hint(props.set.type, props.set.id, item.value?.key ?? '', props.set),
})

watch(answer, hint.poke)

const counterLabel = computed(() => {
  const values = { current: position.value + 1, total: queue.value.length }
  if (isTyping.value) return t('answer.wordOf', values)
  return t(isSentence.value ? 'practice.sentenceOf' : 'practice.questionOf', values)
})

const question = computed<QuestionView | null>(() => {
  const current = item.value
  if (!current || isTyping.value) return null
  if (isSentence.value) {
    return {
      id: position.value,
      answerKind: 'BUILD',
      type: 'TRANSLATE_BUILD',
      prompt: null,
      hanzi: null,
      pinyin: null,
      meaning: current.meaning,
      audioText: null,
      options: [],
      tiles: current.tiles,
    }
  }
  return {
    id: position.value,
    answerKind: 'CHOICE',
    type: null,
    prompt: current.prompt,
    hanzi: current.hanzi,
    pinyin: current.pinyin,
    meaning: null,
    audioText: isListening.value ? current.audioText : null,
    options: current.options,
    tiles: [],
  }
})

function onKeydown(event: KeyboardEvent): void {
  if (isTyping.value || run.busy.value) return
  const current = question.value
  if (!current) return
  const shortcut = questionShortcut(event)
  if (shortcut === null) return
  if (shortcut === 'enter') {
    if (check.value) void next()
    return
  }
  if (locked.value || current.answerKind !== 'CHOICE') return
  const option = current.options[shortcut]
  if (option && !hint.ruledOut.value.includes(option.text)) answer.value = option.text
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))

const hasAnswer = computed(() => answer.value !== null || (isTyping.value && typed.value !== ''))

function clearAnswer(): void {
  answer.value = null
  if (isTyping.value) {
    typed.value = ''
    typingStage.value?.focusInput()
  }
}

function resetAnswer(): void {
  if (hasAnswer.value) clearAnswer()
}

async function submit(value: string | null, confidence: AnswerConfidence | null): Promise<void> {
  const current = item.value
  if (!current || locked.value) return
  const sent: CheckedAnswer =
    value === null && isListening.value && audioFailed.value
      ? { itemKey: current.key, answer: null, confidence, audioFailed: true }
      : { itemKey: current.key, answer: value, confidence }
  const verdict = await run.check(sent)
  if (!verdict) return
  answer.value = value
  check.value = verdict
  given.value = sent
  if (!verdict.correct && !sent.audioFailed) tally.mistakes += 1
  if (!isAgain.value) {
    answers.push({ itemKey: current.key, answer: value })
    if (value !== null) tally.answered += 1
    if (!verdict.correct) allRight.value = false
    if (verdict.correct && confidence === 'GUESS') queue.value.push(current)
  }
  await nextTick()
  nextButton.value?.focus()
}

async function next(): Promise<void> {
  if (!check.value || run.busy.value) return
  if (!isLast.value) {
    position.value += 1
    check.value = null
    given.value = null
    audioFailed.value = false
    clearAnswer()
    return
  }
  const result = await run.complete(answers)
  if (result) emit('finished', result, '', { ...tally })
}
</script>
