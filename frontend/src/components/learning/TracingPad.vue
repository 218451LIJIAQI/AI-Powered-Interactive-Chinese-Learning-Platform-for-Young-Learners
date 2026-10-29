<template>
  <canvas
    ref="canvas"
    v-bind="$attrs"
    tabindex="0"
    role="application"
    :aria-label="label"
    :aria-describedby="keysId"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="endStroke"
    @pointercancel="cancelStroke"
    @lostpointercapture="endStroke"
    @keydown="onKeydown"
  />
  <p :id="keysId" class="sr-only">{{ t('tracingPad.keyboard') }}</p>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, useId, watch } from 'vue'

import { useLocalMessages } from '@/composables/useLocalMessages'
import { tracingPadMessages } from '@/plugins/tracingPadMessages'
import { moveCursor, toCanvasPoint, type StrokePoint } from '@/utils/tracing'

/**
 * A 田字格 writing pad (purple guides, ink strokes), one square cell of `cellSize` per character, with an optional
 * faint model glyph under the strokes. The strokes are the v-model: the caller counts, undoes and clears them. Draw
 * with a mouse, a finger or a pen; keyboard: Space (or Enter) starts or ends a stroke, the arrow keys draw. The caller
 * styles the canvas through its class (keep `touch-action: none` there, so a finger draws instead of scrolling).
 */
defineOptions({ inheritAttrs: false })
const props = defineProps<{ characters: string; showModel: boolean; label: string; cellSize: number }>()
const strokes = defineModel<StrokePoint[][]>('strokes', { required: true })

const KEY_STEP = 10
const MIN_POINTS = 2
/** Ink width, model glyph size and the glyph's drop below the middle, as parts of a cell. */
const INK = 1 / 26
const GLYPH = 0.72
const GLYPH_DROP = 0.023

const { t } = useLocalMessages({ tracingPad: tracingPadMessages })
const keysId = `${useId()}-keys`

const canvas = ref<HTMLCanvasElement | null>(null)
const cells = computed(() => Math.max(1, Array.from(props.characters).length))
const width = computed(() => cells.value * props.cellSize)
let current: StrokePoint[] | null = null
let cursor: StrokePoint = { x: props.cellSize / 2, y: props.cellSize / 2 }
let context: CanvasRenderingContext2D | null = null
let palette = {
  ink: '#152042',
  guide: 'rgba(111, 76, 255, 0.3)',
  model: 'rgba(21, 32, 66, 0.12)',
  font: "'Noto Serif SC', 'Songti SC', 'STSong', 'SimSun', serif",
}

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
  const token = (name: string, fallback: string) => style.getPropertyValue(name).trim() || fallback
  palette = {
    ink: token('--color-ink-900', palette.ink),
    guide: token('--grid-line', palette.guide),
    model: token('--color-ink-a12', palette.model),
    font: token('--font-hanzi', palette.font),
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
  const glyphs = Array.from(props.characters)
  for (let cell = 0; cell < cells.value; cell++) {
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
    if (props.showModel && glyphs[cell]) {
      ctx.fillStyle = palette.model
      ctx.font = `${Math.round(size * GLYPH)}px ${palette.font}`
      ctx.textAlign = 'center'
      ctx.textBaseline = 'middle'
      ctx.fillText(glyphs[cell]!, left + size / 2, size / 2 + Math.round(size * GLYPH_DROP))
    }
  }
  ctx.restore()

  ctx.save()
  ctx.strokeStyle = palette.ink
  ctx.lineWidth = Math.round(size * INK)
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  for (const stroke of current ? [...strokes.value, current] : strokes.value) {
    ctx.beginPath()
    stroke.forEach((point, index) => (index === 0 ? ctx.moveTo(point.x, point.y) : ctx.lineTo(point.x, point.y)))
    ctx.stroke()
  }
  ctx.restore()
}

function pointOf(event: PointerEvent): StrokePoint {
  const element = canvas.value!
  return toCanvasPoint(event.clientX, event.clientY, element.getBoundingClientRect(), width.value, props.cellSize)
}

function onPointerDown(event: PointerEvent): void {
  canvas.value?.setPointerCapture?.(event.pointerId)
  current = [pointOf(event)]
}

function onPointerMove(event: PointerEvent): void {
  if (!current) return
  current.push(pointOf(event))
  redraw()
}

function endStroke(): void {
  if (current && current.length >= MIN_POINTS) strokes.value = [...strokes.value, current]
  current = null
  redraw()
}

function cancelStroke(): void {
  current = null
  redraw()
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === ' ' || event.key === 'Enter') {
    event.preventDefault()
    if (current) endStroke()
    else current = [{ ...cursor }]
    return
  }
  const moved = moveCursor(cursor, event.key, KEY_STEP, width.value)
  if (!moved) return
  event.preventDefault()
  cursor = { x: moved.x, y: Math.min(moved.y, props.cellSize) }
  if (current) {
    current.push({ ...cursor })
    redraw()
  }
}

onMounted(setup)
watch(width, setup)
watch([strokes, () => props.characters, () => props.showModel], redraw)
</script>
