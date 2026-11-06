<template>
  <section class="learn-question" :aria-labelledby="`${baseId}-instruction`" data-testid="question-stage">
    <h2 :id="`${baseId}-instruction`" class="focus-title">{{ instruction }}</h2>
    <p v-if="context" class="learn-question-context">{{ context }}</p>

    <div v-if="isListening" class="learn-question-prompt">
      <ListenButtons :text="question.audioText ?? ''" :label="t('learn.theAudio')" large slow />
    </div>
    <div v-else-if="question.hanzi" class="learn-question-prompt">
      <ListenButtons v-if="listenText" :text="listenText" prompt />
      <div class="learn-question-word">
        <p class="hanzi learn-question-hanzi" lang="zh-CN">{{ question.hanzi }}</p>
        <p v-if="hint" class="learn-question-pinyin">{{ hint }}</p>
      </div>
    </div>
    <p v-else-if="question.type === 'TRANSLATE_BUILD' && question.meaning" class="learn-question-meaning">
      “{{ question.meaning }}”
    </p>

    <ol v-if="question.answerKind === 'CHOICE'" class="learn-options" :aria-label="t('learn.answers')">
      <li v-for="(option, index) in question.options" :key="`${option.text}-${index}`">
        <button
          class="option-row learn-option"
          :class="optionClass(option.text)"
          type="button"
          :aria-pressed="modelValue === option.text"
          :disabled="locked"
          :data-testid="`option-${index}`"
          @click="emit('update:modelValue', option.text)"
        >
          <span class="option-keycap" aria-hidden="true">{{ index + 1 }}</span>
          <span class="learn-option-text">
            <span
              :class="{ 'hanzi learn-option-hanzi': isChinese(option.text) }"
              :lang="isChinese(option.text) ? 'zh-CN' : undefined"
            >
              {{ option.text }}
            </span>
            <span v-if="option.pinyin" class="learn-option-pinyin">{{ option.pinyin }}</span>
          </span>
          <IconGlyph
            v-if="optionMark(option.text)"
            class="learn-option-mark"
            :class="`is-${optionMark(option.text)}`"
            :name="optionMark(option.text)!"
            :size="22"
          />
        </button>
      </li>
    </ol>

    <div v-else class="learn-build">
      <div
        class="learn-build-answer"
        :class="buildClass"
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
          :class="{ hanzi: isChinese(question.tiles[tileIndex] ?? '') }"
          lang="zh-CN"
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
          v-for="(tile, index) in question.tiles"
          :key="`bank-${index}`"
          class="learn-tile"
          :class="{ 'is-used': placed.includes(index), hanzi: isChinese(tile) }"
          lang="zh-CN"
          type="button"
          :disabled="locked || placed.includes(index)"
          :data-testid="`tile-${index}`"
          :draggable="draggableTiles && !locked && !placed.includes(index)"
          @click="add(index)"
          @dragstart="startDrag($event, index)"
        >
          {{ tile }}
        </button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { learnMessages } from '@/plugins/learnMessages'
import type { AnswerCheck, QuestionView } from '@/types/learn'

import ListenButtons from './ListenButtons.vue'

/**
 * One question of a lesson or a review: its instruction (written from the question type, or the question's own wording),
 * what it asks about (the word with its speaker and pinyin, the audio, or the sentence to say), and either answer rows
 * with keycaps or word tiles to put in order. The answer is the chosen option text, or the placed tiles separated by
 * spaces. Every state is drawn: selected, locked after the check, right (with a check) and wrong (with a cross).
 */
interface Props {
  question: QuestionView
  modelValue: string | null
  /** After the check: nothing can change any more. */
  locked?: boolean
  check?: AnswerCheck | null
  /** A line under the instruction, such as what the student answered last time. */
  context?: string
  /** Word tiles can also be dragged, to the sentence and back (Practice's Drag & Drop); clicks and keys always work. */
  draggableTiles?: boolean
}

const props = withDefaults(defineProps<Props>(), { locked: false, check: null, context: '', draggableTiles: false })

const emit = defineEmits<{
  'update:modelValue': [value: string | null]
}>()

const { t } = useLocalMessages({ learn: learnMessages })
const baseId = useId()

const isListening = computed(() => {
  const type = props.question.type
  if (type === 'LISTEN_CHOOSE' || type === 'LISTEN_BUILD') return true
  return type === null && Boolean(props.question.audioText) && !props.question.hanzi
})

const instruction = computed(() => {
  switch (props.question.type) {
    case 'MEANING':
      return t('learn.instructionMeaning')
    case 'PINYIN':
      return t('learn.instructionPinyin')
    case 'HANZI':
      return t('learn.instructionHanzi', { meaning: props.question.meaning ?? '' })
    case 'LISTEN_CHOOSE':
      return t('learn.instructionListenChoose')
    case 'LISTEN_BUILD':
      return t('learn.instructionListenBuild')
    case 'TRANSLATE_BUILD':
      return t('learn.instructionTranslate')
    default:
      return props.question.prompt ?? ''
  }
})

/** A pinyin question asks for the sound of the word, so the student may hear it even though no audio text is sent. */
const listenText = computed(() => (props.question.type === 'PINYIN' ? props.question.hanzi : props.question.audioText))

const hint = computed(() => (props.question.type === 'PINYIN' ? t('learn.hintPinyin') : (props.question.pinyin ?? '')))

// ----- choices -----

function same(a: string | null | undefined, b: string | null | undefined): boolean {
  const normalize = (value: string | null | undefined) =>
    (value ?? '')
      .normalize('NFKC')
      .toLowerCase()
      .replace(/[\s\p{P}]/gu, '')
  return normalize(a) !== '' && normalize(a) === normalize(b)
}

function optionClass(text: string): Record<string, boolean> {
  const checked = props.check !== null
  return {
    'is-selected': props.modelValue === text,
    'is-correct': checked && same(text, props.check?.correctAnswer),
    'is-wrong': checked && props.modelValue === text && !same(text, props.check?.correctAnswer),
  }
}

function optionMark(text: string): 'correct' | 'incorrect' | null {
  if (!props.check) return null
  if (same(text, props.check.correctAnswer)) return 'correct'
  return props.modelValue === text ? 'incorrect' : null
}

function isChinese(text: string): boolean {
  return /\p{Script=Han}/u.test(text)
}

// ----- tiles -----

const placed = ref<number[]>([])

watch(
  () => props.question.id,
  () => {
    placed.value = []
  },
)

watch(
  () => props.modelValue,
  (value) => {
    if (value === null && placed.value.length) placed.value = []
  },
)

function sync(): void {
  emit(
    'update:modelValue',
    placed.value.length ? placed.value.map((index) => props.question.tiles[index]).join(' ') : null,
  )
}

function add(index: number): void {
  if (props.locked || placed.value.includes(index)) return
  placed.value = [...placed.value, index]
  sync()
}

function remove(position: number): void {
  if (props.locked) return
  placed.value = placed.value.filter((_, at) => at !== position)
  sync()
}

/** The tile being dragged, as its index in `question.tiles`. */
let dragged: number | null = null

function startDrag(event: DragEvent, tileIndex: number): void {
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

const buildClass = computed(() => ({
  'is-correct': props.check?.correct === true,
  'is-wrong': props.check?.correct === false,
}))
</script>
