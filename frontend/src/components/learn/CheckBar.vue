<template>
  <FocusFooter class="learn-checkbar" :tone="tone" data-testid="check-bar">
    <template #start>
      <div v-if="check" :id="feedbackId" class="learn-checkbar-feedback" role="status" aria-live="polite">
        <span class="learn-checkbar-icon" aria-hidden="true">
          <IconGlyph :name="check.correct ? 'check' : 'close'" :size="26" />
        </span>
        <div class="learn-checkbar-copy">
          <p class="learn-checkbar-title">{{ title }}</p>
          <p v-if="!check.correct && answerLine" class="learn-checkbar-answer">
            <span :lang="hasHan(check.correctAnswer) ? 'zh-CN' : undefined">{{ check.correctAnswer }}</span>
            <span v-if="showPinyin" lang="zh-Latn"> · {{ check.pinyin }}</span>
            <span v-if="check.meaning && check.meaning !== check.correctAnswer"> · {{ check.meaning }}</span>
          </p>
          <p v-if="check.explanation || note" class="learn-checkbar-explanation">
            <CjkText :text="[check.explanation, note].filter(Boolean).join(' ')" />
          </p>
        </div>
      </div>
      <button
        v-else
        class="btn btn-secondary btn-lg"
        type="button"
        :disabled="checking"
        data-testid="skip"
        @click="emit('skip')"
      >
        {{ skipLabel || t('common.skip') }}
      </button>
    </template>

    <!-- While answering, the middle holds the question's hint (a block) or a short note. -->
    <template v-if="$slots.middle && !check" #middle><slot name="middle" /></template>
    <template v-else-if="hint && !check" #default>{{ hint }}</template>

    <template #end>
      <button
        v-if="!check"
        class="btn btn-primary btn-lg"
        type="button"
        :disabled="!canCheck || checking"
        :aria-busy="checking"
        data-testid="check"
        @click="emit('check')"
      >
        {{ t('common.checkAnswer') }}
      </button>
      <button
        v-else
        ref="continueButton"
        class="btn btn-primary btn-lg"
        type="button"
        :disabled="checking"
        :aria-busy="checking"
        :aria-describedby="feedbackId"
        data-testid="continue"
        @click="emit('continue')"
      >
        {{ continueLabel || t('learn.continue') }}
      </button>
    </template>
  </FocusFooter>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, useId, watch } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import FocusFooter from '@/components/layout/FocusFooter.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { learnSharedMessages } from '@/plugins/learnSharedMessages'
import type { AnswerCheck } from '@/types/learn'
import { hasHan } from '@/utils/cjkText'

/**
 * The footer under a question of a lesson, the mistakes review and the level check: [Skip] left and [Check] right while
 * answering, then a band with the verdict and why on the left and [Continue] on the right. The right option is marked
 * in place on the question, so the band does not repeat it; only an answer that cannot be marked there (a sentence
 * built from tiles, `answerLine`) is written under the title. The verdict is announced; Continue takes the focus so
 * Enter moves on. The `middle` slot keeps keyboard instructions or an offered hint in the footer while answering.
 */
interface Props {
  check: AnswerCheck | null
  canCheck: boolean
  checking: boolean
  /** A short line between the buttons while answering, without a `middle` slot: the keys, or a checkpoint's rule. */
  hint?: string
  continueLabel?: string
  /** The verdict line of a right answer ("Correct!" in the Mistakes review); by default "Nice!". */
  correctTitle?: string
  /** The verdict line of a wrong answer ("Almost!" in the level check); by default "Not quite.". */
  wrongTitle?: string
  /** The skip button ("I don’t know yet" in the level check). */
  skipLabel?: string
  /** The answer was skipped: the verdict says "Skipped". */
  skipped?: boolean
  /** Writes the right answer under the title ("Correct answer: …"): for an answer the question cannot mark in place. */
  answerLine?: boolean
  /** A line after the explanation: what happens to the question now ("It stays on your Mistakes list."). */
  note?: string
}

const props = defineProps<Props>()

const emit = defineEmits<{
  check: []
  skip: []
  continue: []
}>()

const { t } = useLocalMessages({ learn: learnSharedMessages })
const feedbackId = `${useId()}-feedback`
const continueButton = ref<HTMLButtonElement | null>(null)

const tone = computed(() => {
  if (!props.check) return null
  return props.check.correct ? 'correct' : 'wrong'
})

const title = computed(() => {
  if (props.check?.correct) return props.correctTitle || t('learn.nice')
  if (props.skipped) return t('learn.skipped')
  if (props.wrongTitle) return props.wrongTitle
  return props.answerLine ? t('learn.correctAnswer') : t('learn.notQuite')
})

/** The pinyin on the answer line, unless the answer is the pinyin itself. */
const showPinyin = computed(() => Boolean(props.check?.pinyin && props.check.pinyin !== props.check.correctAnswer))

watch(
  () => props.check,
  async (check) => {
    if (!check) return
    await nextTick()
    continueButton.value?.focus()
  },
)
</script>
