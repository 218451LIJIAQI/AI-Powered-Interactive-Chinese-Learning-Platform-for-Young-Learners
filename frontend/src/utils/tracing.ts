/** Pointer and keyboard geometry for the character tracing pad (TracingPad). */
export interface StrokePoint {
  x: number
  y: number
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
