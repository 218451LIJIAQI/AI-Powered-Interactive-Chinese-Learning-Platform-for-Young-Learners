<template>
  <div class="practice-player tracing-pad">
    <p class="practice-note" data-testid="tracing-practice-only">{{ t('studentPractice.tracingPracticeOnly') }}</p>

    <div class="tracing-target">
      <strong v-if="glyph" class="cjk-text tracing-target-glyph" lang="zh-CN">{{ glyph }}</strong>
      <span v-if="exercise.pinyin" class="practice-pinyin">{{ exercise.pinyin }}</span>
      <span v-if="exercise.meaning" class="tracing-meaning">{{
        t('studentPractice.tracingMeaning', { meaning: exercise.meaning })
      }}</span>
    </div>
    <p v-if="!glyph" class="practice-note is-warning">{{ t('studentPractice.tracingNoCharacter') }}</p>

    <div class="tracing-surface">
      <canvas
        ref="canvasRef"
        class="tracing-canvas"
        :class="{ 'is-locked': locked }"
        data-testid="tracing-canvas"
        role="application"
        tabindex="0"
        :aria-label="
          glyph
            ? t('studentPractice.tracingCanvasLabel', { character: glyph })
            : t('studentPractice.tracingCanvasLabelFree')
        "
        :aria-describedby="`${baseId}-help ${baseId}-keys`"
        :aria-disabled="locked ? 'true' : undefined"
        @pointerdown="onPointerDown"
        @pointermove="onPointerMove"
        @pointerup="onPointerUp"
        @pointercancel="onPointerCancel"
        @lostpointercapture="onPointerUp"
        @keydown="onKeydown"
        @focus="onFocusChange(true)"
        @blur="onFocusChange(false)"
      />
    </div>
    <p :id="`${baseId}-help`" class="helper-text tracing-help">{{ t('studentPractice.tracingHelp') }}</p>
    <p :id="`${baseId}-keys`" class="helper-text tracing-help">{{ t('studentPractice.tracingKeyboardHelp') }}</p>

    <div class="tracing-tools">
      <span class="practice-progress" data-testid="tracing-stroke-count" aria-live="polite">{{
        t('studentPractice.tracingStrokeCount', { count: strokeCount })
      }}</span>
      <button
        type="button"
        class="btn btn-ghost btn-sm"
        data-testid="tracing-clear"
        :disabled="locked || (strokeCount === 0 && !drawing)"
        @click="clearPad"
      >
        <IconGlyph name="eraser" :size="16" />
        <span>{{ t('studentPractice.tracingClear') }}</span>
      </button>
      <a
        v-if="safeWorksheetUrl"
        class="practice-link"
        :href="safeWorksheetUrl"
        target="_blank"
        rel="noopener noreferrer"
        >{{ t('studentPractice.openWorksheet') }}</a
      >
    </div>

    <div class="practice-submit-row">
      <span class="practice-progress">{{ t('studentPractice.tracingNotGraded') }}</span>
      <button
        type="button"
        class="btn btn-primary"
        data-testid="activity-submit"
        :disabled="!ready || locked || submitting"
        :aria-describedby="ready ? undefined : `${baseId}-hint`"
        @click="submit"
      >
        {{ submitting ? t('studentPractice.markingReviewed') : t('studentPractice.tracingSubmit') }}
      </button>
    </div>
    <p v-if="!ready && !locked" :id="`${baseId}-hint`" class="practice-progress">
      {{ t('studentPractice.tracingNeedStroke') }}
    </p>

    <p class="sr-only" role="status" aria-live="polite">{{ announcement }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, useId, watch } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import type { StudentTracingExerciseResponse } from '@/types/api'
import { buildTracingCompletion, isTracingReady, type ActivitySubmission } from '@/utils/activityCompletion'
import { readCssToken } from '@/utils/cssToken'
import {
  createStrokeCounter,
  moveCursor,
  referenceFontSize,
  referenceGlyph,
  toCanvasPoint,
  type StrokePoint,
} from '@/utils/tracing'
import { safeHttpUrl } from '@/utils/url'

import { usePracticeI18n } from './usePracticeI18n'

const props = defineProps<{
  exercise: StudentTracingExerciseResponse
  locked?: boolean
  submitting?: boolean
}>()

const emit = defineEmits<{ submit: [submission: ActivitySubmission] }>()

/** Logical pad size (CSS pixels); the backing store is scaled by devicePixelRatio. */
const PAD_SIZE = 320
const KEYBOARD_STEP = 12

/** Canvas cannot read `var()`, so the design tokens are resolved once the canvas is mounted. */
interface PadPalette {
  ink: string
  guide: string
  reference: string
  cursor: string
}
const FALLBACK_PALETTE: PadPalette = {
  ink: '#3a1f9c',
  guide: 'rgba(111, 76, 255, 0.24)',
  reference: 'rgba(21, 32, 66, 0.12)',
  cursor: '#6f4cff',
}
let palette: PadPalette = FALLBACK_PALETTE

function readPalette(element: Element): PadPalette {
  return {
    ink: readCssToken(element, '--color-primary-800', FALLBACK_PALETTE.ink),
    guide: readCssToken(element, '--color-primary-a24', FALLBACK_PALETTE.guide),
    reference: readCssToken(element, '--color-ink-a12', FALLBACK_PALETTE.reference),
    cursor: readCssToken(element, '--focus-ring-color', FALLBACK_PALETTE.cursor),
  }
}

const { t } = usePracticeI18n()
const baseId = `tracing-${useId()}`
const canvasRef = ref<HTMLCanvasElement | null>(null)
const counter = createStrokeCounter()
const strokeCount = ref(0)
const drawing = ref(false)
const announcement = ref('')
const keyboardFocus = ref(false)
const cursor = ref<StrokePoint>({ x: PAD_SIZE / 2, y: PAD_SIZE / 2 })
let context: CanvasRenderingContext2D | null = null
let activePointerId: number | null = null

const glyph = computed(() => referenceGlyph(props.exercise.targetCharacter))
const safeWorksheetUrl = computed(() => safeHttpUrl(props.exercise.worksheetUrl, { allowRelative: true }))
const ready = computed(() => isTracingReady(strokeCount.value))

function setupCanvas(): void {
  const canvas = canvasRef.value
  if (!canvas) return
  const ratio = typeof window === 'undefined' ? 1 : Math.min(Math.max(window.devicePixelRatio || 1, 1), 3)
  canvas.width = PAD_SIZE * ratio
  canvas.height = PAD_SIZE * ratio
  try {
    context = canvas.getContext('2d')
  } catch {
    context = null
  }
  context?.setTransform(ratio, 0, 0, ratio, 0, 0)
  palette = readPalette(canvas)
}

function drawGuides(ctx: CanvasRenderingContext2D): void {
  ctx.save()
  ctx.strokeStyle = palette.guide
  ctx.lineWidth = 1
  ctx.setLineDash([6, 6])
  ctx.beginPath()
  ctx.moveTo(PAD_SIZE / 2, 0)
  ctx.lineTo(PAD_SIZE / 2, PAD_SIZE)
  ctx.moveTo(0, PAD_SIZE / 2)
  ctx.lineTo(PAD_SIZE, PAD_SIZE / 2)
  ctx.stroke()
  ctx.restore()
}

function drawReference(ctx: CanvasRenderingContext2D): void {
  const text = glyph.value
  const size = referenceFontSize(PAD_SIZE, text)
  if (!text || size <= 0) return
  ctx.save()
  ctx.fillStyle = palette.reference
  ctx.font = `${size}px "Noto Sans SC", "PingFang SC", "Microsoft YaHei", "KaiTi", sans-serif`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(text, PAD_SIZE / 2, PAD_SIZE / 2 + size * 0.04)
  ctx.restore()
}

function drawStroke(ctx: CanvasRenderingContext2D, points: readonly StrokePoint[]): void {
  const [first, ...rest] = points
  if (!first) return
  ctx.beginPath()
  if (!rest.length) {
    ctx.arc(first.x, first.y, ctx.lineWidth / 2, 0, Math.PI * 2)
    ctx.fill()
    return
  }
  ctx.moveTo(first.x, first.y)
  for (const point of rest) ctx.lineTo(point.x, point.y)
  ctx.stroke()
}

function redraw(): void {
  const ctx = context
  if (!ctx) return
  ctx.clearRect(0, 0, PAD_SIZE, PAD_SIZE)
  drawGuides(ctx)
  drawReference(ctx)

  ctx.save()
  ctx.strokeStyle = palette.ink
  ctx.fillStyle = palette.ink
  ctx.lineWidth = 10
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  for (const stroke of counter.strokes) drawStroke(ctx, stroke)
  if (counter.current) drawStroke(ctx, counter.current)
  ctx.restore()

  if (keyboardFocus.value && !props.locked) {
    ctx.save()
    ctx.strokeStyle = counter.active ? palette.ink : palette.cursor
    ctx.lineWidth = 2
    ctx.beginPath()
    ctx.arc(cursor.value.x, cursor.value.y, 8, 0, Math.PI * 2)
    ctx.stroke()
    ctx.restore()
  }
}

function pointFromEvent(event: PointerEvent): StrokePoint {
  const canvas = canvasRef.value
  if (!canvas) return { x: 0, y: 0 }
  return toCanvasPoint(event.clientX, event.clientY, canvas.getBoundingClientRect(), PAD_SIZE, PAD_SIZE)
}

function finishStroke(): void {
  if (counter.end()) {
    strokeCount.value = counter.count
    announcement.value = t('studentPractice.tracingStrokeAdded', { count: counter.count })
  }
  drawing.value = false
  redraw()
}

function onPointerDown(event: PointerEvent): void {
  if (props.locked || activePointerId !== null) return
  if (event.pointerType === 'mouse' && event.button !== 0) return
  event.preventDefault()
  activePointerId = event.pointerId
  try {
    canvasRef.value?.setPointerCapture(event.pointerId)
  } catch {
    /* pointer capture is optional */
  }
  counter.begin(pointFromEvent(event))
  drawing.value = true
  redraw()
}

function onPointerMove(event: PointerEvent): void {
  if (activePointerId !== event.pointerId) return
  event.preventDefault()
  if (counter.extend(pointFromEvent(event))) redraw()
}

function onPointerUp(event: PointerEvent): void {
  if (activePointerId !== event.pointerId) return
  activePointerId = null
  finishStroke()
}

function onPointerCancel(event: PointerEvent): void {
  if (activePointerId !== event.pointerId) return
  activePointerId = null
  counter.cancel()
  drawing.value = false
  redraw()
}

function onKeydown(event: KeyboardEvent): void {
  if (props.locked) return
  keyboardFocus.value = true
  if (event.key === ' ' || event.key === 'Enter') {
    event.preventDefault()
    if (counter.active) {
      finishStroke()
    } else {
      counter.begin(cursor.value)
      drawing.value = true
      redraw()
    }
    return
  }
  if (event.key === 'Escape' && counter.active) {
    counter.cancel()
    drawing.value = false
    redraw()
    return
  }

  const next = moveCursor(cursor.value, event.key, KEYBOARD_STEP, PAD_SIZE)
  if (!next) return
  event.preventDefault()
  cursor.value = next
  if (counter.active) counter.extend(next)
  redraw()
}

function onFocusChange(focused: boolean): void {
  // Only show the keyboard pen cursor for keyboard focus, not after a mouse/touch press.
  keyboardFocus.value = focused && activePointerId === null
  redraw()
}

function clearPad(): void {
  counter.clear()
  activePointerId = null
  strokeCount.value = 0
  drawing.value = false
  announcement.value = t('studentPractice.tracingCleared')
  redraw()
}

function submit(): void {
  if (!ready.value || props.locked) return
  emit('submit', { type: 'TRACING_EXERCISE', body: buildTracingCompletion(strokeCount.value) })
}

watch(
  () => props.locked,
  (locked) => {
    if (locked && counter.active) {
      counter.cancel()
      activePointerId = null
      drawing.value = false
    }
    redraw()
  },
)

watch(glyph, redraw)

onMounted(() => {
  setupCanvas()
  redraw()
})
</script>

<style scoped src="./practicePlayer.css"></style>
<style scoped>
.tracing-target {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-2) var(--space-3);
}

.tracing-target-glyph {
  font-size: var(--font-size-4xl);
  line-height: var(--line-height-snug);
}

.tracing-meaning {
  color: var(--text-secondary);
  font-weight: var(--font-weight-bold);
}

.tracing-surface {
  display: flex;
  justify-content: center;
}

.tracing-canvas {
  display: block;
  width: min(100%, 320px);
  height: auto;
  aspect-ratio: 1;
  border: var(--border-width-thick) solid var(--color-primary-200);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
  box-shadow: var(--shadow-xs);
  touch-action: none;
  cursor: crosshair;
}

.tracing-canvas:hover:not(.is-locked) {
  border-color: var(--color-primary-300);
}

.tracing-canvas.is-locked {
  border-color: var(--border-subtle);
  background: var(--surface-sunken);
  cursor: not-allowed;
}

.tracing-help {
  margin: 0;
  text-align: center;
}

.tracing-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: var(--space-2-5) var(--space-4);
}
</style>
