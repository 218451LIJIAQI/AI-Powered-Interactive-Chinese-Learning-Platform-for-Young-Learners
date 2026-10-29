<template>
  <div class="practice-player" data-testid="tracing-player">
    <div class="practice-meter">
      <span class="practice-counter" aria-live="polite" data-testid="stroke-count">
        {{ t('practice.strokesOf', { count: strokes.length, total: required }) }}
      </span>
      <ProgressBar
        :progress="ratioPercent(strokes.length, required)"
        :label="t('practice.setProgress')"
        class="is-sm practice-meter-bar"
      />
    </div>

    <div class="practice-trace-target">
      <span class="practice-trace-glyph hanzi" lang="zh-CN">{{ characters }}</span>
      <span class="practice-trace-meaning">
        <span v-if="item?.pinyin" class="practice-trace-pinyin">{{ item.pinyin }}</span>
        {{ item?.meaning }}
      </span>
      <ListenButtons v-if="characters" :text="characters" />
    </div>

    <div class="practice-trace-pad" :style="{ aspectRatio: `${cells} / 1`, maxWidth: `${cells * PAD_CELL}px` }">
      <TracingPad
        v-model:strokes="strokes"
        class="practice-trace-canvas"
        :characters="characters"
        :show-model="showHint"
        :cell-size="PAD_CELL"
        :label="t('practice.tracePadLabel', { characters })"
        data-testid="trace-canvas"
      />
    </div>

    <div class="practice-trace-tools">
      <button class="btn btn-ghost btn-sm" type="button" :aria-pressed="showHint" @click="showHint = !showHint">
        <IconGlyph name="hint" :size="14" />{{ showHint ? t('practice.hideHint') : t('practice.showHint') }}
      </button>
      <button
        class="btn btn-ghost btn-sm"
        type="button"
        :disabled="!strokes.length"
        @click="strokes = strokes.slice(0, -1)"
      >
        <IconGlyph name="undo" :size="14" />{{ t('practice.undo') }}
      </button>
      <button class="btn btn-ghost btn-sm" type="button" :disabled="!strokes.length" @click="strokes = []">
        <IconGlyph name="eraser" :size="14" />{{ t('practice.clear') }}
      </button>
    </div>

    <div class="practice-footer">
      <RouterLink class="btn btn-link" :to="{ name: 'student-ai', params: { tab: 'voice' } }">
        {{ t('practice.wantAScore') }}
      </RouterLink>
      <button
        class="btn btn-primary"
        type="button"
        :disabled="strokes.length < required || run.busy.value"
        :aria-busy="run.busy.value"
        data-testid="tracing-done"
        @click="finish"
      >
        <IconGlyph name="check" :size="16" />{{ t('practice.done') }}
      </button>
    </div>
    <p v-if="run.error.value" class="field-error" role="alert">{{ run.error.value }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import ListenButtons from '@/components/learn/ListenButtons.vue'
import TracingPad from '@/components/learning/TracingPad.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { practiceMessages } from '@/plugins/practiceMessages'
import type { PracticeResult, PracticeSet } from '@/types/learn'
import { ratioPercent } from '@/utils/format'
import type { StrokePoint } from '@/utils/tracing'

import { usePracticeRun } from './usePracticeRun'

/**
 * Character tracing on a 田字格 pad, one square per character: draw every stroke (the count is shown), with a faint
 * hint glyph (shown at first in guided sets), Undo and Clear. Tracing is practice only; for a score the student can
 * check a photo of their handwriting in AI Helper.
 */
const props = defineProps<{ set: PracticeSet }>()
const emit = defineEmits<{ finished: [result: PracticeResult] }>()

const PAD_CELL = 260

const { t } = useLocalMessages({ practice: practiceMessages })
const run = usePracticeRun(
  () => props.set,
  () => t('practice.saveFailed'),
)

const item = computed(() => props.set.items[0] ?? null)
const characters = computed(() => item.value?.hanzi ?? '')
const cells = computed(() => Math.max(1, Array.from(characters.value).length))
const required = computed(() => Math.max(1, props.set.strokeCount ?? 1))
const showHint = ref(props.set.stage !== 'INDEPENDENT')
const strokes = ref<StrokePoint[][]>([])

async function finish(): Promise<void> {
  const result = await run.complete([], strokes.value.length)
  if (result) emit('finished', result)
}
</script>
