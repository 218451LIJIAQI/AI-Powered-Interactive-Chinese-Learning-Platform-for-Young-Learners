<template>
  <div class="class-player">
    <header class="class-focus-head">
      <p class="page-eyebrow">{{ t('classPlayer.tracing.title') }}</p>
      <h1 class="focus-title"><CjkText :text="title" /></h1>
      <p class="class-focus-sub" aria-live="polite">
        {{ done ? t('classPlayer.tracing.compare') : t('classPlayer.tracing.instruction', strokes.length) }}
      </p>
    </header>

    <p v-if="!characters.length" class="class-focus-sub">{{ t('classPlayer.tracing.empty') }}</p>
    <div v-else-if="current" class="class-tracing">
      <div class="class-tracing-model">
        <span class="page-eyebrow">
          {{ t('classPlayer.tracing.model', { number: index + 1, total: characters.length }) }}
        </span>
        <strong class="hanzi class-tracing-glyph" lang="zh-CN">{{ current.character }}</strong>
        <span class="class-tracing-meaning">{{ [current.pinyin, current.meaning].filter(Boolean).join(' · ') }}</span>
        <span v-for="character in traced" :key="character" class="type-caption class-tracing-done">
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
          :disabled="done"
          :label="t('classPlayer.tracing.padLabel', { character: current.character })"
          data-testid="tracing-pad"
        />
      </div>
    </div>

    <FocusFooter v-if="!done">
      <template #start>
        <button type="button" class="btn btn-secondary btn-lg" :disabled="!strokes.length" @click="clear">
          {{ t('classPlayer.tracing.clear') }}
        </button>
      </template>
      <template #end>
        <button
          type="button"
          class="btn btn-primary btn-lg"
          :disabled="!strokes.length || busy"
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
 * needs at least one stroke. Once finished (`done`) the last tracing stays to compare, and the page's result band
 * replaces the footer.
 */
const props = defineProps<{ title: string; characters: readonly TraceCharacter[]; busy: boolean; done: boolean }>()
const emit = defineEmits<{ finish: [] }>()

const { t } = useLocalMessages({ classPlayer: classPlayerMessages })

const PAD_SIZE = 280
const index = ref(0)
const strokes = ref<StrokePoint[][]>([])
const traced = ref<string[]>([])
const current = computed(() => props.characters[index.value] ?? null)

function clear(): void {
  strokes.value = []
}

function next(): void {
  if (!current.value) return
  if (index.value < props.characters.length - 1) {
    traced.value = [...traced.value, current.value.character]
    index.value++
    clear()
  } else {
    emit('finish')
  }
}
</script>
