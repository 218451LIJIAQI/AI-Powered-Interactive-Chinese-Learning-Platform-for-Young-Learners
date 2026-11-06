<template>
  <FocusFooter class="learn-checkbar" :tone="tone" data-testid="check-bar">
    <template #start>
      <div v-if="check" class="learn-checkbar-feedback" role="status" aria-live="polite">
        <span class="learn-checkbar-icon" aria-hidden="true">
          <IconGlyph :name="check.correct ? 'check' : 'close'" :size="26" />
        </span>
        <div class="learn-checkbar-copy">
          <p class="learn-checkbar-title">
            {{ check.correct ? t('learn.nice') : wrongTitle || t('learn.correctAnswer') }}
          </p>
          <p v-if="!check.correct" class="learn-checkbar-answer">
            <span lang="zh-CN">{{ check.correctAnswer }}</span>
            <span v-if="showPinyin"> · {{ check.pinyin }}</span>
            <span v-if="check.meaning && check.meaning !== check.correctAnswer"> · {{ check.meaning }}</span>
          </p>
          <p v-if="check.explanation" class="learn-checkbar-explanation">{{ check.explanation }}</p>
        </div>
      </div>
      <button
        v-else-if="skippable"
        class="btn btn-secondary btn-lg"
        type="button"
        :disabled="checking"
        data-testid="skip"
        @click="emit('skip')"
      >
        {{ skipLabel || t('learn.skip') }}
      </button>
    </template>

    <template v-if="hint && !check" #default>{{ hint }}</template>

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
        {{ t('learn.check') }}
      </button>
      <button
        v-else
        ref="continueButton"
        class="btn btn-primary btn-lg"
        type="button"
        :disabled="checking"
        :aria-busy="checking"
        data-testid="continue"
        @click="emit('continue')"
      >
        {{ continueLabel || t('learn.continue') }}
      </button>
    </template>
  </FocusFooter>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import FocusFooter from '@/components/layout/FocusFooter.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { learnMessages } from '@/plugins/learnMessages'
import type { AnswerCheck } from '@/types/learn'

/**
 * The footer under a question, the same in every focus flow: [Skip] left and [Check] right while answering, then a band
 * with the verdict (the right answer and why) on the left and [Continue] on the right. The verdict is announced; Continue
 * takes the focus so Enter moves on.
 */
interface Props {
  check: AnswerCheck | null
  canCheck: boolean
  checking?: boolean
  skippable?: boolean
  hint?: string
  continueLabel?: string
  /** The verdict line of a wrong answer ("Almost!" in the level check); "Correct answer:" by default. */
  wrongTitle?: string
  /** The skip button ("I don’t know yet" in the level check). */
  skipLabel?: string
}

const props = withDefaults(defineProps<Props>(), {
  checking: false,
  skippable: true,
  hint: '',
  continueLabel: '',
  wrongTitle: '',
  skipLabel: '',
})

const emit = defineEmits<{
  check: []
  skip: []
  continue: []
}>()

const { t } = useLocalMessages({ learn: learnMessages })
const continueButton = ref<HTMLButtonElement | null>(null)

const tone = computed(() => {
  if (!props.check) return null
  return props.check.correct ? 'correct' : 'wrong'
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
