<template>
  <div class="practice-player">
    <div class="practice-trace">
      <div class="practice-trace-model">
        <p class="practice-trace-tiles" lang="zh-CN">
          <span v-for="(glyph, position) in glyphs" :key="position" class="hanzi-tile">{{ glyph }}</span>
        </p>
        <p class="practice-trace-word">
          <span v-if="item.pinyin">{{ item.pinyin }}</span>
          <template v-if="item.pinyin && item.meaning"> · </template>
          <span v-if="item.meaning">{{ item.meaning }}</span>
        </p>
        <p class="practice-counter" aria-live="polite" data-testid="stroke-count">
          {{
            byCharacter
              ? t('practice.characterStrokes', byCharacter)
              : t('practice.strokesOf', { count: strokes.length, total: required })
          }}
        </p>
      </div>

      <div class="practice-trace-board">
        <div
          class="practice-trace-pad"
          :style="{ aspectRatio: `${glyphs.length} / 1`, maxWidth: `${glyphs.length * PAD_CELL}px` }"
        >
          <TracingPad
            v-model:strokes="strokes"
            class="practice-trace-canvas"
            :characters="characters"
            :show-model="showModel"
            :cell-size="PAD_CELL"
            :disabled="run.busy.value"
            :label="t('practice.tracePadLabel', { characters })"
          />
        </div>
        <div class="btn-group is-sm">
          <button
            class="btn btn-secondary btn-sm"
            type="button"
            :disabled="run.busy.value"
            :aria-disabled="!strokes.length"
            @click="undo"
          >
            <IconGlyph name="undo" :size="16" />{{ t('common.undo') }}
          </button>
          <button
            class="btn btn-secondary btn-sm"
            type="button"
            :disabled="run.busy.value"
            :aria-disabled="!strokes.length"
            @click="clear"
          >
            {{ t('practice.clear') }}
          </button>
        </div>
      </div>
    </div>

    <PracticeHint :hint="hint" type="TRACING" :report-context="hintReportContext(set, item.key)" />
    <p v-if="run.error.value" class="field-error" role="alert">{{ run.error.value }}</p>

    <div class="practice-footer">
      <div class="practice-footer-end">
        <button
          class="btn btn-primary"
          type="button"
          :disabled="!ready || run.busy.value"
          :aria-busy="run.busy.value"
          data-testid="tracing-done"
          @click="finish"
        >
          <IconGlyph name="check" :size="16" />{{ t('practice.done') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { practiceApi } from '@/api/learn'
import IconGlyph from '@/components/common/IconGlyph.vue'
import TracingPad from '@/components/learning/TracingPad.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { practiceMessages } from '@/plugins/practiceMessages'
import type { PracticeResult, PracticeSet } from '@/types/learn'
import { tracingGlyphs, type StrokePoint } from '@/utils/tracing'

import PracticeHint from './PracticeHint.vue'
import { usePracticeRun } from './usePracticeRun'
import { hintReportContext, useStuckHint } from './useStuckHint'

/**
 * Character tracing: the model (tile, pinyin and meaning, and the strokes drawn so far, as text) beside a 田字格 pad,
 * one square per character, with Undo and Clear. Draw every stroke by mouse or keyboard, then Done. Guided sets show
 * the faint glyph on the pad. An independent set reveals it when the learner opens the hint.
 */
const props = defineProps<{ set: PracticeSet }>()
const emit = defineEmits<{ finished: [result: PracticeResult, recap: string] }>()

const PAD_CELL = 300

const { t } = useLocalMessages({ practice: practiceMessages })
const run = usePracticeRun(() => props.set, t)

// The server only keeps a traced set with its characters and at least one stroke, as one item.
const item = computed(() => props.set.items[0]!)
const glyphs = computed(() => tracingGlyphs(item.value.hanzi!))
const characters = computed(() => glyphs.value.join(''))
const required = computed(() => props.set.strokeCount!)
const strokes = ref<StrokePoint[][]>([])

/**
 * A word of several characters counts its strokes per character ("Character 1 of 2 · 0 of 4 strokes") when the set
 * says how many strokes each has: a stroke belongs to the square it starts in, and the character on the way is the
 * first one not finished yet. Without that the line counts the strokes of the whole word.
 */
const byCharacter = computed(() => {
  const counts = props.set.characterStrokes
  if (!counts || glyphs.value.length < 2 || counts.length !== glyphs.value.length) return null
  const drawn = counts.map(() => 0)
  for (const [first] of strokes.value) {
    if (first) drawn[Math.min(counts.length - 1, Math.max(0, Math.floor(first.x / PAD_CELL)))] += 1
  }
  const open = counts.findIndex((needed, at) => drawn[at] < needed)
  const at = open < 0 ? counts.length - 1 : open
  return { character: at + 1, characters: counts.length, count: Math.min(drawn[at], counts[at]), total: counts[at] }
})

const ready = computed(() => {
  const progress = byCharacter.value
  return progress
    ? progress.character === progress.characters && progress.count === progress.total
    : strokes.value.length >= required.value
})

const hint = useStuckHint({
  itemKey: computed(() => `${props.set.id}:${item.value.key}`),
  open: computed(() => !run.busy.value && !ready.value),
  perSetBudget: false,
  load: () => practiceApi.hint('TRACING', props.set.id, item.value.key, props.set),
})

// A stroke is progress: the student is not stuck.
watch(
  () => strokes.value.length,
  () => hint.poke(),
)

// With no stroke to take back, Undo and Clear stay focusable (aria-disabled) and do nothing.
function undo(): void {
  if (strokes.value.length) strokes.value = strokes.value.slice(0, -1)
}

function clear(): void {
  if (strokes.value.length) strokes.value = []
}

/** The faint glyph on the pad: an independent set shows it only once a hint was opened. */
const showModel = computed(() => props.set.stage !== 'INDEPENDENT' || hint.used.value)

async function finish(): Promise<void> {
  if (!ready.value) return
  const result = await run.complete([], strokes.value.length)
  if (!result) return
  const word = [item.value.pinyin, item.value.meaning].filter(Boolean).join(' · ')
  emit('finished', result, word)
}
</script>
