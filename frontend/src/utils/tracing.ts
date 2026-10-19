/**
 * Stroke tracking for the character tracing practice pad.
 *
 * Tracing is practice only (never graded): the pad just counts finished strokes so the completion request can send
 * `{ strokeCount }` (>= 1). A stroke counts when the pointer is released after at least {@link MIN_STROKE_POINTS}
 * distinct points, so a plain tap does not count.
 */
export interface StrokePoint {
  x: number
  y: number
}

const MIN_STROKE_POINTS = 2

export interface StrokeCounter {
  /** Starts a new stroke (an unfinished stroke is discarded). */
  begin(point: StrokePoint): void
  /** Adds a point to the active stroke; returns the previous point when a segment should be drawn. */
  extend(point: StrokePoint): StrokePoint | null
  /** Finishes the active stroke; returns true when it was counted. */
  end(): boolean
  /** Drops the active stroke without counting it (pointercancel). */
  cancel(): void
  /** Removes every stroke and resets the counter. */
  clear(): void
  readonly count: number
  readonly active: boolean
  /** Points of the stroke being drawn, or null. */
  readonly current: readonly StrokePoint[] | null
  readonly strokes: readonly (readonly StrokePoint[])[]
}

function samePoint(a: StrokePoint | undefined, b: StrokePoint): boolean {
  return a !== undefined && a.x === b.x && a.y === b.y
}

export function createStrokeCounter(): StrokeCounter {
  let strokes: StrokePoint[][] = []
  let current: StrokePoint[] | null = null

  return {
    begin(point) {
      current = [{ x: point.x, y: point.y }]
    },
    extend(point) {
      if (!current) return null
      const previous = current[current.length - 1]
      if (samePoint(previous, point)) return null
      current.push({ x: point.x, y: point.y })
      return previous ?? null
    },
    end() {
      const stroke = current
      current = null
      if (!stroke || stroke.length < MIN_STROKE_POINTS) return false
      strokes = [...strokes, stroke]
      return true
    },
    cancel() {
      current = null
    },
    clear() {
      strokes = []
      current = null
    },
    get count() {
      return strokes.length
    },
    get active() {
      return current !== null
    },
    get current() {
      return current
    },
    get strokes() {
      return strokes
    },
  }
}

/** Maps a pointer position (client coordinates) to the canvas' internal coordinate space. */
export function toCanvasPoint(
  clientX: number,
  clientY: number,
  rect: { left: number; top: number; width: number; height: number },
  canvasWidth: number,
  canvasHeight: number,
): StrokePoint {
  const scaleX = rect.width > 0 ? canvasWidth / rect.width : 1
  const scaleY = rect.height > 0 ? canvasHeight / rect.height : 1
  return {
    x: Math.round((clientX - rect.left) * scaleX * 10) / 10,
    y: Math.round((clientY - rect.top) * scaleY * 10) / 10,
  }
}

const ARROW_STEPS: Readonly<Record<string, StrokePoint>> = {
  ArrowUp: { x: 0, y: -1 },
  ArrowDown: { x: 0, y: 1 },
  ArrowLeft: { x: -1, y: 0 },
  ArrowRight: { x: 1, y: 0 },
}

/**
 * Keyboard drawing: moves the pen cursor for an arrow key (clamped to the pad), or returns null for other keys.
 */
export function moveCursor(point: StrokePoint, key: string, step: number, padSize: number): StrokePoint | null {
  const direction = ARROW_STEPS[key]
  if (!direction) return null
  const clamp = (value: number) => Math.min(Math.max(value, 0), padSize)
  return { x: clamp(point.x + direction.x * step), y: clamp(point.y + direction.y * step) }
}

/** The reference glyph drawn behind the pad, or null when the exercise has no target character. */
export function referenceGlyph(targetCharacter: string | null | undefined): string | null {
  const trimmed = targetCharacter?.trim()
  return trimmed ? trimmed : null
}

/** Font size (px) for the faint reference glyph so that one or several characters fit the square pad. */
export function referenceFontSize(padSize: number, glyph: string | null): number {
  if (!glyph || padSize <= 0) return 0
  const length = Math.max(1, Array.from(glyph).length)
  return Math.floor((padSize * 0.78) / length)
}
