<template>
  <div
    class="learn-checkbar"
    :class="{ 'is-correct': check?.correct === true, 'is-wrong': check?.correct === false, 'is-inline': inline }"
    data-testid="check-bar"
  >
    <div class="learn-checkbar-inner">
      <div v-if="check" class="learn-checkbar-feedback" role="status" aria-live="polite">
        <span class="learn-checkbar-icon" aria-hidden="true">
          <IconGlyph :name="check.correct ? 'check' : 'close'" :size="26" />
        </span>
        <div class="learn-checkbar-copy">
          <p class="learn-checkbar-title">
            {{ check.correct ? correctTitle || t('learn.nice') : wrongTitle || t('learn.correctAnswer') }}
          </p>
          <p v-if="!check.correct || showAnswerWhenRight" class="learn-checkbar-answer">
            <span class="cjk-text" lang="zh-CN">{{ check.correctAnswer }}</span>
            <span v-if="check.meaning && check.meaning !== check.correctAnswer"> · {{ check.meaning }}</span>
          </p>
          <p v-if="showPinyin" class="learn-checkbar-pinyin">
            {{ check.pinyin }}
          </p>
          <p v-if="check.explanation" class="learn-checkbar-explanation">{{ check.explanation }}</p>
        </div>
      </div>
      <p v-else class="learn-checkbar-hint">{{ hint }}</p>

      <div class="learn-checkbar-actions">
        <template v-if="!check">
          <button
            v-if="skippable"
            class="btn btn-ghost"
            type="button"
            :disabled="checking"
            data-testid="skip"
            @click="emit('skip')"
          >
            {{ skipLabel || t('learn.skip') }}
          </button>
          <button
            class="btn btn-primary btn-lg learn-checkbar-main"
            type="button"
            :disabled="!canCheck || checking"
            :aria-busy="checking"
            data-testid="check"
            @click="emit('check')"
          >
            {{ t('learn.check') }}
          </button>
        </template>
        <button
          v-else
          ref="continueButton"
          class="btn btn-primary btn-lg learn-checkbar-main"
          type="button"
          :disabled="checking"
          :aria-busy="checking"
          data-testid="continue"
          @click="emit('continue')"
        >
          {{ continueLabel || t('learn.continue') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { learnMessages } from '@/plugins/learnMessages'
import type { AnswerCheck } from '@/types/learn'

/**
 * The bar under a question: Skip and Check while answering, then the verdict (the right answer and why) and
 * Continue. The verdict is announced; Continue takes the focus so Enter moves on.
 */
interface Props {
  check: AnswerCheck | null
  canCheck: boolean
  checking?: boolean
  skippable?: boolean
  hint?: string
  continueLabel?: string
  correctTitle?: string
  /** The verdict line of a wrong answer ("Almost!" in the level check); "Correct answer:" by default. */
  wrongTitle?: string
  /** The skip button ("I don’t know yet" in the level check). */
  skipLabel?: string
  /** Also show the answer line after a right answer (a practice item's meaning). */
  showAnswerWhenRight?: boolean
  /** Inside a Practice stage instead of fixed at the bottom of a focus-mode task. */
  inline?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  checking: false,
  skippable: true,
  hint: '',
  continueLabel: '',
  correctTitle: '',
  wrongTitle: '',
  skipLabel: '',
  showAnswerWhenRight: false,
  inline: false,
})

const emit = defineEmits<{
  check: []
  skip: []
  continue: []
}>()

const { t } = useLocalMessages({ learn: learnMessages })
const continueButton = ref<HTMLButtonElement | null>(null)

/** The pinyin line under the answer, unless the answer is the pinyin itself. */
const showPinyin = computed(() => {
  const check = props.check
  if (!check?.pinyin || check.pinyin === check.correctAnswer) return false
  return !check.correct || props.showAnswerWhenRight
})

watch(
  () => props.check,
  async (check) => {
    if (!check) return
    await nextTick()
    continueButton.value?.focus()
  },
)
</script>
