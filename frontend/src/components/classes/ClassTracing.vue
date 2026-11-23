<template>
  <div class="class-player">
    <header class="class-focus-head">
      <p class="page-eyebrow">{{ t('classPlayer.tracing.title') }}</p>
      <h1 class="focus-title"><CjkText :text="title" /></h1>
      <div class="class-player-row">
        <p class="class-focus-sub">
          {{ done ? t('classPlayer.tracing.compare') : t('classPlayer.tracing.instruction') }}
        </p>
        <!-- Where the learner is, once: the character (when there are several) and the strokes drawn on it. -->
        <strong v-if="!done && current" class="class-player-count" aria-live="polite" data-testid="tracing-progress">
          {{ progressText }}
        </strong>
      </div>
    </header>

    <p v-if="!characters.length" class="class-focus-sub">{{ t('classPlayer.tracing.empty') }}</p>
    <div v-else-if="current" class="class-tracing">
      <div class="class-tracing-model">
        <span class="page-eyebrow">{{ t('classPlayer.tracing.model') }}</span>
        <strong class="hanzi class-tracing-glyph" lang="zh-CN">{{ current.character }}</strong>
        <span class="class-tracing-meaning">{{ [current.pinyin, current.meaning].filter(Boolean).join(' · ') }}</span>
        <!-- Keyed by position: a word such as 谢谢 traces the same character twice. -->
        <span v-for="(character, position) in traced" :key="position" class="type-caption class-tracing-done">
          {{ t('classPlayer.tracing.traced', { character }) }}<IconGlyph name="check" :size="12" />
        </span>
      </div>
      <div class="class-tracing-pad">
        <TracingPad
          v-model:strokes="strokes"
          class="class-tracing-canvas"
          :characters="current.character"
          :show-model="true"
          :cell-size="PAD_SIZE"
          :disabled="done || busy"
          :label="t('classPlayer.tracing.padLabel', { character: current.character })"
          data-testid="tracing-pad"
        />
      </div>
    </div>

    <FocusFooter v-if="!done && characters.length">
      <template #start>
        <button
          type="button"
          class="btn btn-secondary btn-lg"
          :disabled="busy"
          :aria-disabled="busy || !strokes.length"
          @click="clear"
        >
          {{ t('classPlayer.tracing.clear') }}
        </button>
      </template>
      <template #end>
        <button
          type="button"
          class="btn btn-primary btn-lg"
          :disabled="busy"
          :aria-disabled="busy || !strokes.length"
          :aria-busy="busy"
          data-testid="activity-finish"
          @click="next"
        >
          {{ index < characters.length - 1 ? t('classPlayer.tracing.nextCharacter') : t('classPlayer.tracing.done') }}
        </button>
      </template>
    </FocusFooter>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import FocusFooter from '@/components/layout/FocusFooter.vue'
import CjkText from '@/components/learning/CjkText.vue'
import TracingPad from '@/components/learning/TracingPad.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classPlayerMessages } from '@/plugins/classPlayerMessages'
import type { TraceCharacter } from '@/types/classes'
import type { StrokePoint } from '@/utils/tracing'

/**
 * Character tracing on a 田字格 pad (not graded), the model beside it and faintly under the strokes. Each character
 * needs at least one stroke; the progress line counts the strokes drawn (the API gives no stroke total per character
 * yet). Once finished (`done`) the last tracing stays to compare, and the page's result band replaces the footer.
 */
const props = defineProps<{ title: string; characters: readonly TraceCharacter[]; busy: boolean; done: boolean }>()
const emit = defineEmits<{ finish: [] }>()

const { t } = useLocalMessages({ classPlayer: classPlayerMessages })

const PAD_SIZE = 280
const index = ref(0)
const strokes = ref<StrokePoint[][]>([])
const traced = computed(() => props.characters.slice(0, index.value).map((item) => item.character))
const current = computed(() => props.characters[index.value] ?? null)

/** "Character 1 of 2 · 3 strokes", or only the strokes when there is one character. */
const progressText = computed(() => {
  const drawn = t('classPlayer.tracing.strokes', strokes.value.length)
  if (props.characters.length < 2) return drawn
  return t('classPlayer.tracing.characterProgress', {
    number: index.value + 1,
    total: props.characters.length,
    strokes: drawn,
  })
})

// Without strokes, Clear and Next character (or Done) stay focusable (aria-disabled), and do nothing: going on to the
// next character keeps the focus on its button.
function clear(): void {
  if (!strokes.value.length) return
  strokes.value = []
}

function next(): void {
  if (!current.value || !strokes.value.length) return
  if (index.value < props.characters.length - 1) {
    index.value++
    clear()
  } else {
    emit('finish')
  }
}
</script>
