<template>
  <section
    class="learn-question"
    :aria-labelledby="`${baseId}-instruction`"
    data-testid="question-stage"
    @dragend="dragged = null"
  >
    <h2 :id="`${baseId}-instruction`" ref="heading" class="focus-title" tabindex="-1">{{ instruction }}</h2>
    <p v-if="context && !contextAfterPinyin" class="learn-question-context">{{ context }}</p>

    <div v-if="isListening" class="learn-question-prompt">
      <ListenButtons :key="question.id" v-model:failed="audioFailed" :text="question.audioText ?? ''" large />
    </div>
    <div v-else-if="question.hanzi" class="learn-question-prompt">
      <ListenButtons v-if="listenText" :text="listenText" prompt />
      <div class="learn-question-word">
        <p class="hanzi learn-question-hanzi" lang="zh-CN">{{ question.hanzi }}</p>
        <p v-if="caption" class="learn-question-pinyin">{{ caption }}</p>
      </div>
    </div>
    <p v-else-if="question.type === 'TRANSLATE_BUILD' && question.meaning" class="learn-question-meaning">
      “{{ question.meaning }}”
    </p>

    <ol v-if="question.answerKind === 'CHOICE'" class="learn-options" :aria-label="t('learn.answers')">
      <li v-for="(option, index) in choiceOptions" :key="`${option.text}-${index}`">
        <button
          class="option-row learn-option"
          :class="{
            'is-correct': option.mark === 'correct',
            'is-wrong': option.mark === 'incorrect',
            'is-ruled-out': option.ruledOut,
          }"
          type="button"
          :aria-pressed="modelValue === option.text"
          :disabled="locked || option.ruledOut"
          :data-testid="`option-${index}`"
          @click="emit('update:modelValue', option.text)"
        >
          <span class="option-keycap" aria-hidden="true">{{ index + 1 }}</span>
          <span class="learn-option-text">
            <span v-if="option.mark === 'correct'" class="sr-only">{{ t('learn.correctAnswer') }} </span>
            <span :class="{ 'hanzi learn-option-hanzi': option.han }" :lang="option.han ? 'zh-CN' : undefined">
              {{ option.text }}
            </span>
            <span v-if="option.pinyin" class="learn-option-pinyin">{{ option.pinyin }}</span>
          </span>
          <IconGlyph
            v-if="option.mark"
            class="learn-option-mark"
            :class="`is-${option.mark}`"
            :name="option.mark === 'correct' ? 'check' : 'close'"
            :size="22"
          />
        </button>
      </li>
    </ol>

    <div v-else class="learn-build">
      <div
        class="learn-build-answer"
        :class="{ 'is-correct': check?.correct === true, 'is-wrong': check?.correct === false }"
        role="group"
        :aria-label="t('learn.yourSentence')"
        data-testid="build-answer"
        @dragover="allowDrop"
        @drop="dropOnAnswer"
      >
        <span v-if="!placed.length" class="learn-build-placeholder">{{ t('learn.clickTilesHint') }}</span>
        <button
          v-for="(tileIndex, position) in placed"
          :key="`placed-${tileIndex}`"
          class="learn-tile"
          :class="{ hanzi: tiles[tileIndex]?.han }"
          :lang="tiles[tileIndex]?.han ? 'zh-CN' : undefined"
          type="button"
          :disabled="locked"
          :aria-label="t('learn.removeTile', { tile: question.tiles[tileIndex] })"
          :draggable="draggableTiles && !locked"
          @click="remove(position)"
          @dragstart="startDrag($event, tileIndex)"
        >
          {{ question.tiles[tileIndex] }}
        </button>
      </div>
      <div
        class="learn-build-bank"
        role="group"
        :aria-label="t('learn.wordTiles')"
        @dragover="allowDrop"
        @drop="dropOnBank"
      >
        <button
          v-for="(tile, index) in tiles"
          :key="`bank-${index}`"
          class="learn-tile"
          :class="{ 'is-used': tile.used, 'is-ruled-out': tile.ruledOut, hanzi: tile.han }"
          :lang="tile.han ? 'zh-CN' : undefined"
          type="button"
          :disabled="!tile.available"
          :data-testid="`tile-${index}`"
          :draggable="draggableTiles && tile.available"
          @click="add(index)"
          @dragstart="startDrag($event, index)"
        >
          {{ tile.text }}
        </button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { learnSharedMessages } from '@/plugins/learnSharedMessages'
import type { AnswerCheck, QuestionView } from '@/types/learn'
import { hasHan } from '@/utils/cjkText'

import ListenButtons from './ListenButtons.vue'
import { isListeningQuestion } from './listeningQuestion'

/**
 * One question of a lesson, the mistakes review, the level check or a Practice set: its instruction (written from the
 * question type, or the question's own wording), what it asks about (the word with its speaker and pinyin, the audio,
 * or the sentence to translate), and either answer rows with keycaps or word tiles to put in order. The answer is the
 * chosen option text, or the placed tiles separated by spaces. Only Chinese is marked `lang="zh-CN"`: an option or a
 * tile can be pinyin, English or a name. Every state is drawn: selected, locked after the check, right (with a check)
 * and wrong (with a cross).
 */
interface Props {
  question: QuestionView
  modelValue: string | null
  /** After the check: nothing can change any more. */
  locked: boolean
  check: AnswerCheck | null
  /**
   * Where the question comes from, such as what the learner answered last time: after the word's pinyin when it shows
   * one, else under the instruction.
   */
  context?: string
  /** Word tiles can also be dragged, to the sentence and back (Practice's Drag & Drop); clicks and keys always work. */
  draggableTiles?: boolean
  /** Answers or tiles a hint ruled out: shown faded and no longer chosen. */
  ruledOut?: readonly string[]
}

const props = withDefaults(defineProps<Props>(), { context: '', ruledOut: () => [] })
const audioFailed = defineModel<boolean>('audioFailed', { default: false })

const emit = defineEmits<{
  'update:modelValue': [value: string | null]
}>()

const { t } = useLocalMessages({ learn: learnSharedMessages })
const baseId = useId()

function isRuledOut(text: string): boolean {
  return !props.check && props.ruledOut.includes(text)
}

const isListening = computed(() => isListeningQuestion(props.question))

const INSTRUCTION_KEYS: Readonly<Record<NonNullable<QuestionView['type']>, string>> = {
  MEANING: 'learn.instructionMeaning',
  PINYIN: 'learn.instructionPinyin',
  HANZI: 'learn.instructionHanzi',
  LISTEN_CHOOSE: 'learn.instructionListenChoose',
  LISTEN_BUILD: 'learn.instructionListenBuild',
  TRANSLATE_BUILD: 'learn.instructionTranslate',
}

/** Written from the question type ("{meaning}" only the hanzi one uses), else the question's own wording. */
const instruction = computed(() => {
  const { type, meaning, prompt } = props.question
  if (type) return t(INSTRUCTION_KEYS[type], { meaning: meaning ?? '' })
  const text = prompt?.trim()
  if (text) return text
  return isListening.value ? t('learn.instructionListenChoose') : ''
})

/** A pinyin question asks for the sound of the word, so the student may hear it even though no audio text is sent. */
const listenText = computed(() => (props.question.type === 'PINYIN' ? props.question.hanzi : props.question.audioText))

/** A context line follows the word's pinyin ("nǐ hǎo · marked “Still learning” in Flashcards") when it has one. */
const contextAfterPinyin = computed(() =>
  Boolean(
    props.context &&
    !isListening.value &&
    props.question.hanzi &&
    props.question.type !== 'PINYIN' &&
    props.question.pinyin,
  ),
)

/** The line under the word: its pinyin (and the context), or, when the pinyin is the answer, a nudge to listen first. */
const caption = computed(() => {
  if (props.question.type === 'PINYIN') return t('learn.hintPinyin')
  const pinyin = props.question.pinyin ?? ''
  return contextAfterPinyin.value ? `${pinyin} · ${props.context}` : pinyin
})

// The button that went on (Continue, Next) left with the last question, and the focus with it: the new question takes
// it, so the keyboard and a screen reader go on from its instruction. A focus kept anywhere else stays there.
const heading = ref<HTMLHeadingElement | null>(null)
watch(
  () => props.question,
  () => {
    if (!document.activeElement || document.activeElement === document.body) heading.value?.focus()
  },
  { flush: 'post' },
)

// Choices

/** As the server compares answers (`LearnAnswers.matches`): case, width, spaces and punctuation do not count. */
function normalizeAnswer(value: string): string {
  return (
    value
      .normalize('NFKC')
      // eslint-disable-next-line no-control-regex
      .replace(/[\s\u001c-\u001f\u0085]/gu, '')
      .toLowerCase()
      .replace(/\p{P}/gu, '')
  )
}

/** Level checks return the exact option. Learn can return an answer key whose spelling differs from the option. */
const correctChoice = computed(() => {
  const answer = props.check?.correctAnswer
  if (!answer) return undefined
  const { options } = props.question
  if (options.some((option) => option.text === answer)) return answer
  const key = normalizeAnswer(answer)
  return key ? options.find((option) => normalizeAnswer(option.text) === key)?.text : undefined
})

const choiceOptions = computed(() => {
  return props.question.options.map((option) => {
    let mark: 'correct' | 'incorrect' | null = null
    if (props.check) {
      if (props.modelValue === option.text) mark = props.check.correct ? 'correct' : 'incorrect'
      else if (option.text === correctChoice.value) mark = 'correct'
    }
    return { ...option, mark, han: hasHan(option.text), ruledOut: isRuledOut(option.text) }
  })
})

// Tiles

const placed = ref<number[]>([])
const tiles = computed(() => {
  const placedIndexes = new Set(placed.value)
  return props.question.tiles.map((text, index) => {
    const used = placedIndexes.has(index)
    const ruledOut = isRuledOut(text)
    return { text, used, ruledOut, han: hasHan(text), available: !props.locked && !used && !ruledOut }
  })
})
/** The tile being dragged, as its index in `question.tiles`: only a drag `startDrag` allows sets it. */
let dragged: number | null = null

// Every caller clears the answer on the next question, on Skip and on Reset. Also clear placed tiles on question switch.
watch(
  [() => props.modelValue, () => props.question, () => props.locked, () => props.draggableTiles],
  ([value, nextQuestion], [, previousQuestion]) => {
    if (value === null || nextQuestion !== previousQuestion) placed.value = []
    dragged = null
  },
)

// A hint that rules out the chosen answer, or a placed tile, takes it back: what it greys out is never the answer sent.
watch(
  () => props.ruledOut,
  (ruledOut) => {
    if (props.locked || props.check) return
    if (props.question.answerKind === 'CHOICE') {
      if (props.modelValue !== null && ruledOut.includes(props.modelValue)) emit('update:modelValue', null)
      return
    }
    const kept = placed.value.filter((index) => !ruledOut.includes(props.question.tiles[index]))
    if (kept.length === placed.value.length) return
    placed.value = kept
    sync()
  },
)

function sync(): void {
  emit(
    'update:modelValue',
    placed.value.length ? placed.value.map((index) => props.question.tiles[index]).join(' ') : null,
  )
}

function add(index: number): void {
  // A placed tile can be dragged and dropped on the sentence again.
  if (!tiles.value[index]?.available) return
  placed.value.push(index)
  sync()
}

function remove(position: number): void {
  if (props.locked) return
  placed.value.splice(position, 1)
  sync()
}

function startDrag(event: DragEvent, tileIndex: number): void {
  if (props.locked || !props.draggableTiles) return
  dragged = tileIndex
  // Firefox starts a drag only with data set.
  event.dataTransfer?.setData('text/plain', props.question.tiles[tileIndex] ?? '')
}

function allowDrop(event: DragEvent): void {
  if (dragged !== null) event.preventDefault()
}

function dropOnAnswer(event: DragEvent): void {
  event.preventDefault()
  if (dragged !== null) add(dragged)
  dragged = null
}

function dropOnBank(event: DragEvent): void {
  event.preventDefault()
  const position = dragged === null ? -1 : placed.value.indexOf(dragged)
  if (position >= 0) remove(position)
  dragged = null
}
</script>
