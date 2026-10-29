<template>
  <div class="practice-player" data-testid="story-player">
    <div class="practice-meter">
      <span class="practice-counter">{{
        t('practice.answeredOf', { answered: answers.size, total: set.items.length })
      }}</span>
      <ProgressBar
        :progress="ratioPercent(answers.size, set.items.length)"
        :label="t('practice.setProgress')"
        class="is-sm practice-meter-bar"
      />
    </div>

    <div class="practice-story-tools">
      <label class="practice-toggle">
        <input v-model="showPinyin" type="checkbox" role="switch" class="switch" />
        {{ t('practice.pinyin') }}
      </label>
      <label class="practice-toggle">
        <input v-model="showTranslation" type="checkbox" role="switch" class="switch" />
        {{ t('practice.translation') }}
      </label>
      <ListenButtons :text="storyText" :label="t('practice.theStory')" slow />
    </div>

    <ol class="practice-story-lines">
      <li v-for="(line, index) in set.storyLines" :key="index" class="practice-story-line">
        <p class="cjk-text practice-story-hanzi" lang="zh-CN">{{ line.hanzi }}</p>
        <p v-if="showPinyin && line.pinyin" class="practice-story-pinyin">{{ line.pinyin }}</p>
        <p v-if="showTranslation && line.translation" class="practice-story-translation">{{ line.translation }}</p>
      </li>
    </ol>

    <fieldset v-for="(item, index) in set.items" :key="item.key" class="practice-story-question">
      <legend>
        <span class="practice-story-number">{{ index + 1 }}</span>
        {{ item.prompt }}
      </legend>
      <div class="practice-story-options">
        <button
          v-for="option in item.options"
          :key="option.text"
          class="practice-story-option"
          :class="optionClass(item.key, option.text)"
          type="button"
          :aria-pressed="answers.get(item.key) === option.text"
          :disabled="checks.size > 0"
          @click="choose(item.key, option.text)"
        >
          {{ option.text }}
          <IconGlyph v-if="mark(item.key, option.text)" :name="mark(item.key, option.text)!" :size="16" />
        </button>
      </div>
      <p v-if="checks.get(item.key)?.explanation" class="practice-story-explanation">
        {{ checks.get(item.key)!.explanation }}
      </p>
    </fieldset>

    <div class="practice-footer">
      <button
        v-if="!checks.size"
        class="btn btn-primary"
        type="button"
        :disabled="answers.size < set.items.length || run.busy.value"
        :aria-busy="run.busy.value"
        data-testid="story-check"
        @click="checkAnswers"
      >
        {{ t('practice.checkAnswers') }}
      </button>
      <button v-else class="btn btn-primary" type="button" :disabled="run.busy.value" @click="finish">
        {{ t('practice.seeResult') }}
      </button>
    </div>
    <p v-if="run.error.value" class="field-error" role="alert">{{ run.error.value }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import ListenButtons from '@/components/learn/ListenButtons.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { practiceMessages } from '@/plugins/practiceMessages'
import type { AnswerCheck, PracticeResult, PracticeSet } from '@/types/learn'
import { ratioPercent } from '@/utils/format'

import { usePracticeRun } from './usePracticeRun'

/** A short story with pinyin and translation toggles, read aloud, and questions checked together. */
const props = defineProps<{ set: PracticeSet }>()
const emit = defineEmits<{ finished: [result: PracticeResult] }>()

const { t } = useLocalMessages({ practice: practiceMessages })
const run = usePracticeRun(
  () => props.set,
  () => t('practice.saveFailed'),
)

const showPinyin = ref(props.set.stage !== 'INDEPENDENT')
const showTranslation = ref(false)
const answers = ref(new Map<string, string>())
const checks = ref(new Map<string, AnswerCheck>())

const storyText = computed(() => props.set.storyLines.map((line) => line.hanzi).join(''))

function choose(key: string, text: string): void {
  answers.value = new Map(answers.value).set(key, text)
}

function optionClass(key: string, text: string): Record<string, boolean> {
  const check = checks.value.get(key)
  return {
    'is-selected': answers.value.get(key) === text,
    'is-correct': check !== undefined && check.correctAnswer === text,
    'is-wrong': check !== undefined && !check.correct && answers.value.get(key) === text,
  }
}

function mark(key: string, text: string): string | null {
  const check = checks.value.get(key)
  if (!check) return null
  if (check.correctAnswer === text) return 'correct'
  return answers.value.get(key) === text ? 'incorrect' : null
}

const payload = computed(() =>
  props.set.items.map((item) => ({ itemKey: item.key, answer: answers.value.get(item.key) ?? null })),
)

async function checkAnswers(): Promise<void> {
  const verdicts = await run.checkAll(payload.value)
  if (!verdicts) return
  checks.value = new Map(props.set.items.map((item, index) => [item.key, verdicts[index]!]))
}

async function finish(): Promise<void> {
  const result = await run.complete(payload.value)
  if (result) emit('finished', result)
}
</script>
