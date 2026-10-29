import { describe, expect, it } from 'vitest'

import { moveCursor, toCanvasPoint } from './tracing'

describe('tracing pad geometry', () => {
  it('maps client coordinates to the canvas resolution', () => {
    const rect = { left: 100, top: 50, width: 150, height: 150 }
    expect(toCanvasPoint(175, 125, rect, 300, 300)).toEqual({ x: 150, y: 150 })
    expect(toCanvasPoint(100, 50, { ...rect, width: 0, height: 0 }, 300, 300)).toEqual({ x: 0, y: 0 })
  })

  it('moves the keyboard pen cursor with arrow keys inside the pad', () => {
    expect(moveCursor({ x: 10, y: 10 }, 'ArrowRight', 12, 320)).toEqual({ x: 22, y: 10 })
    expect(moveCursor({ x: 5, y: 5 }, 'ArrowUp', 12, 320)).toEqual({ x: 5, y: 0 })
    expect(moveCursor({ x: 315, y: 5 }, 'ArrowRight', 12, 320)).toEqual({ x: 320, y: 5 })
    expect(moveCursor({ x: 5, y: 5 }, 'Enter', 12, 320)).toBeNull()
  })
})
