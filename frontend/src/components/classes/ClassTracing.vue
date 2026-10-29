<template>
  <div class="class-player">
    <div class="class-player-head">
      <span class="page-eyebrow student-classes-pigment class-player-kind"
        ><IconGlyph name="brush" :size="16" />{{ t('classPlayer.tracing.title') }}</span
      >
      <span class="tag">{{ t('classPlayer.tracing.notGraded') }}</span>
      <button
        v-if="stage === 'SCAFFOLDED' && current"
        type="button"
        class="btn btn-ghost btn-sm"
        @click="hintOpen = !hintOpen"
      >
        <IconGlyph name="hint" :size="16" />{{
          hintOpen ? t('classPlayer.activity.hideHint') : t('classPlayer.activity.showHint')
        }}
      </button>
    </div>

    <p v-if="!characters.length" class="class-player-empty">{{ t('classPlayer.tracing.empty') }}</p>
    <div v-else-if="current" class="class-tracing">
      <div class="class-tracing-model">
        <span class="page-eyebrow">
          {{ t('classPlayer.tracing.model', { number: index + 1, total: characters.length }) }}
        </span>
        <span class="hanzi-tile class-tracing-tile" lang="zh-CN">{{ current.character }}</span>
        <span class="type-caption">{{ [current.pinyin, current.meaning].filter(Boolean).join(' · ') }}</span>
        <ul v-if="traced.length" class="class-tracing-done">
          <li v-for="done in traced" :key="done">
            <IconGlyph name="check" :size="12" />{{ t('classPlayer.tracing.traced', { character: done }) }}
          </li>
        </ul>
      </div>
      <div class="class-tracing-pad">
        <span class="type-caption class-tracing-count" aria-live="polite">
          {{ t('classPlayer.tracing.strokes', { count: strokes.length }, strokes.length) }}
        </span>
        <TracingPad
          v-model:strokes="strokes"
          class="class-tracing-canvas"
          :characters="current.character"
          :show-model="showModel"
          :cell-size="PAD_SIZE"
          :label="t('classPlayer.tracing.padLabel', { character: current.character })"
          data-testid="tracing-pad"
        />
      </div>
    </div>

    <div class="class-player-foot">
      <span class="type-caption">{{ t('classPlayer.tracing.footer') }}</span>
      <div class="class-player-foot-actions">
        <button type="button" class="btn btn-outline" :disabled="!strokes.length" @click="clear">
          <IconGlyph name="eraser" :size="16" />{{ t('classPlayer.tracing.clear') }}
        </button>
        <button
          type="button"
          class="btn btn-primary"
          :disabled="!strokes.length || busy"
          :aria-busy="busy"
          data-testid="activity-finish"
          @click="next"
        >
          <IconGlyph name="check" :size="16" />
          {{ index < characters.length - 1 ? t('classPlayer.tracing.nextCharacter') : t('classPlayer.tracing.done') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import TracingPad from '@/components/learning/TracingPad.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classPlayerMessages } from '@/plugins/classPlayerMessages'
import type { LearningStage } from '@/types/api'
import type { TraceCharacter } from '@/types/classes'
import type { StrokePoint } from '@/utils/tracing'

/**
 * Character tracing on a 田字格 pad (not graded), the model shown in a 田字格 tile beside it. By stage: Guided shows
 * the model under the pad, Supported shows it on request, Independent leaves the pad empty. Each character needs at
 * least one stroke.
 */
const props = defineProps<{ characters: readonly TraceCharacter[]; stage: LearningStage | null; busy: boolean }>()
const emit = defineEmits<{ finish: [] }>()

const { t } = useLocalMessages({ classPlayer: classPlayerMessages })

const PAD_SIZE = 320
const index = ref(0)
const strokes = ref<StrokePoint[][]>([])
const hintOpen = ref(false)
const traced = ref<string[]>([])
const current = computed(() => props.characters[index.value] ?? null)
const showModel = computed(() => props.stage === null || props.stage === 'GUIDED' || hintOpen.value)

function clear(): void {
  strokes.value = []
}

function next(): void {
  if (!current.value) return
  traced.value = [...traced.value, current.value.character]
  if (index.value < props.characters.length - 1) {
    index.value++
    hintOpen.value = false
    clear()
  } else {
    emit('finish')
  }
}

watch(hintOpen, () => {
  // The model sits under the strokes, so showing it starts the character again.
  clear()
})
</script>
