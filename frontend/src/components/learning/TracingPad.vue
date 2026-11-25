<template>
  <canvas
    ref="canvas"
    v-bind="$attrs"
    class="tracing-pad-canvas"
    :tabindex="disabled ? -1 : 0"
    role="application"
    :aria-label="label"
    :aria-describedby="keysId"
    @focus="onFocus"
    @blur="onBlur"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="endStroke"
    @pointercancel="cancelStroke"
    @lostpointercapture="cancelStroke"
    @keydown="onKeydown"
  />
  <p :id="keysId" class="sr-only">{{ t('tracingPad.keyboard') }}</p>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, toRaw, useId, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { tracingGlyphs, moveCursor, toCanvasPoint, type StrokePoint } from '@/utils/tracing'

/**
 * A 田字格 writing pad (purple guides, ink strokes), one square cell of `cellSize` per character, with an optional
 * faint model glyph under the strokes. The strokes are the v-model: the caller counts, undoes and clears them. Draw
 * with a mouse, trackpad or stylus; keyboard: Space (or Enter) starts or ends a stroke, the arrow keys draw. The pad
 * sets `touch-action: none` on its own canvas so stylus input draws instead of scrolling; callers style sizing and
 * borders through their class. `disabled` keeps the strokes on show and takes no more input.
 */
defineOptions({ inheritAttrs: false })
const props = defineProps<{
  characters: string
  showModel: boolean
  label: string
  cellSize: number
  disabled?: boolean
}>()
const strokes = defineModel<StrokePoint[][]>('strokes', { required: true })

const KEY_STEP = 10
const COARSE_KEY_STEP = 40
const MIN_POINTS = 2
/** Ink width, model glyph size and the glyph's drop below the middle, as parts of a cell. */
const INK = 1 / 26
const GLYPH = 0.72
const GLYPH_DROP = 0.023

const { t } = useI18n()
const keysId = `${useId()}-keys`

const canvas = ref<HTMLCanvasElement | null>(null)
const glyphs = computed(() => tracingGlyphs(props.characters))
const width = computed(() => glyphs.value.length * props.cellSize)
let current: StrokePoint[] | null = null
let capturedPointer: number | null = null
let windowPointerFallback = false
let lastCommittedStrokes: StrokePoint[][] | null = null
let disposed = false
let cursor: StrokePoint = { x: props.cellSize / 2, y: props.cellSize / 2 }
let showCursor = false
let context: CanvasRenderingContext2D | null = null
let palette = { ink: '', guide: '', model: '', font: '', cursor: '', paper: '' }
const forcedColorsQuery = typeof window !== 'undefined' ? window.matchMedia?.('(forced-colors: active)') : undefined
let forcedColors = false

function setup(): void {
  const element = canvas.value
  if (!element) return
  const ratio = Math.min(Math.max(window.devicePixelRatio || 1, 1), 3)
  element.width = width.value * ratio
  element.height = props.cellSize * ratio
  context = element.getContext('2d')
  context?.setTransform(ratio, 0, 0, ratio, 0, 0)
  // Canvas cannot read var(); the tokens are read once the pad is mounted.
  const style = window.getComputedStyle(element)
  const token = (name: string) => style.getPropertyValue(name).trim()
  forcedColors = Boolean(forcedColorsQuery?.matches)
  palette = forcedColors
    ? {
        ink: 'CanvasText',
        guide: 'GrayText',
        model: 'GrayText',
        font: token('--font-hanzi'),
        cursor: 'Highlight',
        paper: 'Canvas',
      }
    : {
        ink: token('--color-ink-900'),
        guide: token('--grid-line'),
        model: token('--color-ink-a12'),
        font: token('--font-hanzi'),
        cursor: token('--color-primary-700'),
        paper: token('--surface-card'),
      }
  redraw()
}

function redraw(): void {
  const ctx = context
  if (!ctx) return
  const size = props.cellSize
  ctx.clearRect(0, 0, width.value, size)
  ctx.save()
  ctx.strokeStyle = palette.guide
  ctx.lineWidth = 1
  ctx.setLineDash([6, 6])
  if (props.showModel) {
    ctx.fillStyle = palette.model
    ctx.font = `700 ${Math.round(size * GLYPH)}px ${palette.font}`
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
  }
  for (const [cell, glyph] of glyphs.value.entries()) {
    const left = cell * size
    ctx.beginPath()
    ctx.moveTo(left + size / 2, 0)
    ctx.lineTo(left + size / 2, size)
    ctx.moveTo(left, size / 2)
    ctx.lineTo(left + size, size / 2)
    if (cell > 0) {
      ctx.moveTo(left, 0)
      ctx.lineTo(left, size)
    }
    ctx.stroke()
    if (props.showModel) {
      ctx.save()
      if (forcedColors) ctx.globalAlpha = 0.25
      ctx.fillText(glyph, left + size / 2, size / 2 + Math.round(size * GLYPH_DROP))
      ctx.restore()
    }
  }
  ctx.restore()

  ctx.save()
  ctx.strokeStyle = palette.ink
  ctx.lineWidth = Math.round(size * INK)
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  for (const stroke of strokes.value) drawStroke(ctx, stroke)
  if (current) drawStroke(ctx, current)
  ctx.restore()

  if (showCursor && !props.disabled) {
    ctx.save()
    ctx.beginPath()
    ctx.arc(cursor.x, cursor.y, Math.max(6, size / 32), 0, 2 * Math.PI)
    ctx.strokeStyle = palette.paper
    ctx.lineWidth = 5
    ctx.stroke()
    ctx.strokeStyle = palette.cursor
    ctx.lineWidth = 2
    ctx.stroke()
    if (current) {
      ctx.fillStyle = palette.cursor
      ctx.fill()
    }
    ctx.restore()
  }
}

function drawStroke(ctx: CanvasRenderingContext2D, stroke: StrokePoint[]): void {
  ctx.beginPath()
  stroke.forEach((point, index) => (index === 0 ? ctx.moveTo(point.x, point.y) : ctx.lineTo(point.x, point.y)))
  ctx.stroke()
}

function pointOf(event: PointerEvent): StrokePoint {
  const element = canvas.value!
  return toCanvasPoint(event.clientX, event.clientY, element.getBoundingClientRect(), width.value, props.cellSize)
}

function onWindowBlur(): void {
  cancelStroke()
}

function attachWindowPointerFallback(): void {
  if (windowPointerFallback) return
  windowPointerFallback = true
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', endStroke)
  window.addEventListener('pointercancel', cancelStroke)
  window.addEventListener('blur', onWindowBlur)
}

function detachWindowPointerFallback(): void {
  if (!windowPointerFallback) return
  windowPointerFallback = false
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', endStroke)
  window.removeEventListener('pointercancel', cancelStroke)
  window.removeEventListener('blur', onWindowBlur)
}

function capturePointer(pointerId: number): boolean {
  try {
    canvas.value?.setPointerCapture?.(pointerId)
    return Boolean(canvas.value?.hasPointerCapture?.(pointerId))
  } catch {
    return false
  }
}

function onPointerDown(event: PointerEvent): void {
  if (props.disabled || event.button !== 0 || capturedPointer !== null) return
  showCursor = false
  cancelStroke()
  capturedPointer = event.pointerId
  if (!capturePointer(event.pointerId)) attachWindowPointerFallback()
  current = [pointOf(event)]
}

function onPointerMove(event: PointerEvent): void {
  if (props.disabled || !current || event.pointerId !== capturedPointer) return
  if (windowPointerFallback && event.currentTarget === canvas.value) return
  current.push(pointOf(event))
  redraw()
}

function endStroke(event?: PointerEvent): void {
  if (event && event.pointerId !== capturedPointer) return
  if (!props.disabled && current && current.length >= MIN_POINTS) {
    const next = [...strokes.value, current]
    lastCommittedStrokes = next
    strokes.value = next
  }
  cancelStroke()
}

function cancelStroke(event?: PointerEvent): void {
  if (event && event.pointerId !== capturedPointer) return
  current = null
  const pointer = capturedPointer
  capturedPointer = null
  detachWindowPointerFallback()
  if (pointer !== null && canvas.value?.hasPointerCapture?.(pointer)) {
    try {
      canvas.value.releasePointerCapture(pointer)
    } catch {
      // Capture may already have been released by the browser.
    }
  }
  redraw()
}

function onFocus(): void {
  showCursor = canvas.value?.matches(':focus-visible') ?? false
  redraw()
}

function onBlur(): void {
  showCursor = false
  if (capturedPointer === null) endStroke()
}

function onKeydown(event: KeyboardEvent): void {
  if (props.disabled || capturedPointer !== null || event.isComposing || event.altKey || event.ctrlKey || event.metaKey)
    return
  if (event.key === 'Escape' && current) {
    event.preventDefault()
    event.stopPropagation()
    cancelStroke()
    return
  }
  if (event.key === ' ' || event.key === 'Enter') {
    event.preventDefault()
    if (event.repeat) return
    showCursor = true
    if (current) endStroke()
    else {
      current = [{ ...cursor }]
      redraw()
    }
    return
  }
  if (!current && glyphs.value.length > 1 && (event.key === 'PageDown' || event.key === 'PageUp')) {
    event.preventDefault()
    const currentCell = Math.min(glyphs.value.length - 1, Math.max(0, Math.floor(cursor.x / props.cellSize)))
    const nextCell = Math.min(glyphs.value.length - 1, Math.max(0, currentCell + (event.key === 'PageDown' ? 1 : -1)))
    showCursor = true
    cursor = { x: nextCell * props.cellSize + props.cellSize / 2, y: props.cellSize / 2 }
    redraw()
    return
  }
  const step = event.shiftKey ? COARSE_KEY_STEP : KEY_STEP
  const moved = moveCursor(cursor, event.key, step, width.value, props.cellSize)
  if (!moved) return
  event.preventDefault()
  showCursor = true
  cursor = moved
  if (current) current.push({ ...cursor })
  redraw()
}

onMounted(() => {
  setup()
  forcedColorsQuery?.addEventListener?.('change', setup)
  // Redraw once the Hanzi webfont finishes loading so the model glyph never stays in fallback font.
  void document.fonts?.ready.then(() => {
    if (!disposed) redraw()
  })
})
onBeforeUnmount(() => {
  disposed = true
  forcedColorsQuery?.removeEventListener?.('change', setup)
  cancelStroke()
})
watch([() => props.characters, () => props.cellSize], () => {
  cancelStroke()
  cursor = { x: props.cellSize / 2, y: props.cellSize / 2 }
  setup()
})
watch(
  () => props.disabled,
  (disabled) => {
    if (disabled) cancelStroke()
  },
)
watch(strokes, (value) => {
  const isOwnCommit = lastCommittedStrokes !== null && toRaw(value) === toRaw(lastCommittedStrokes)
  lastCommittedStrokes = null
  if (isOwnCommit) redraw()
  else cancelStroke()
})
watch(() => props.showModel, redraw)
</script>

<style scoped>
.tracing-pad-canvas {
  touch-action: none;
}
</style>
