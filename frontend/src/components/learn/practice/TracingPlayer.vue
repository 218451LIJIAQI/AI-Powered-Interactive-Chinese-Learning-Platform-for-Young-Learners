<template>
  <div class="practice-player" data-testid="tracing-player">
    <div class="practice-trace">
      <div class="practice-trace-model">
        <p class="practice-trace-tiles" lang="zh-CN">
          <span v-for="(glyph, position) in glyphs" :key="position" class="hanzi-tile">{{ glyph }}</span>
        </p>
        <p class="practice-trace-word">
          <span v-if="item?.pinyin">{{ item.pinyin }}</span>
          <template v-if="item?.pinyin && item?.meaning"> · </template>
          <span v-if="item?.meaning">{{ item.meaning }}</span>
        </p>
        <ol class="practice-dots is-squares" aria-hidden="true">
          <li v-for="stroke in required" :key="stroke" :class="stroke <= strokes.length ? 'is-seen' : 'is-ahead'" />
        </ol>
        <p class="practice-counter" aria-live="polite" data-testid="stroke-count">
          {{ t('practice.strokesOf', { count: strokes.length, total: required }) }}
        </p>
        <button class="btn btn-secondary btn-sm" type="button" :aria-pressed="showHint" @click="showHint = !showHint">
          <IconGlyph name="hint" :size="16" />{{ showHint ? t('practice.hideHint') : t('practice.showHint') }}
        </button>
      </div>

      <div class="practice-trace-board">
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
        <div class="btn-group is-sm">
          <button
            class="btn btn-secondary btn-sm"
            type="button"
            :disabled="!strokes.length"
            @click="strokes = strokes.slice(0, -1)"
          >
            <IconGlyph name="undo" :size="16" />{{ t('practice.undo') }}
          </button>
          <button class="btn btn-secondary btn-sm" type="button" :disabled="!strokes.length" @click="strokes = []">
            {{ t('practice.clear') }}
          </button>
        </div>
      </div>
    </div>

    <p v-if="run.error.value" class="field-error" role="alert">{{ run.error.value }}</p>

    <div class="practice-footer">
      <div class="practice-footer-end">
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
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import TracingPad from '@/components/learning/TracingPad.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { practiceMessages } from '@/plugins/practiceMessages'
import type { PracticeResult, PracticeSet } from '@/types/learn'
import type { StrokePoint } from '@/utils/tracing'

import { usePracticeRun } from './usePracticeRun'

/**
 * Character tracing: the model (tile, pinyin and meaning, the strokes drawn so far, the hint switch) beside a 田字格
 * pad, one square per character, with Undo and Clear. Draw every stroke by mouse or keyboard, then Done. The hint (the
 * faint glyph on the pad) is on at first in guided sets.
 */
const props = defineProps<{ set: PracticeSet }>()
const emit = defineEmits<{ finished: [result: PracticeResult, recap: string] }>()

const PAD_CELL = 300

const { t } = useLocalMessages({ practice: practiceMessages })
const run = usePracticeRun(
  () => props.set,
  () => t('practice.saveFailed'),
)

const item = computed(() => props.set.items[0] ?? null)
const characters = computed(() => item.value?.hanzi ?? '')
const glyphs = computed(() => Array.from(characters.value))
const cells = computed(() => Math.max(1, glyphs.value.length))
const required = computed(() => Math.max(1, props.set.strokeCount ?? 1))
const showHint = ref(props.set.stage !== 'INDEPENDENT')
const strokes = ref<StrokePoint[][]>([])

async function finish(): Promise<void> {
  const result = await run.complete([], strokes.value.length)
  if (!result) return
  const word = [item.value?.pinyin, item.value?.meaning].filter(Boolean).join(' · ')
  emit('finished', result, word)
}
</script>
