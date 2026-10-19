import { describe, expect, it } from 'vitest'

import { createStrokeCounter, moveCursor, referenceFontSize, referenceGlyph, toCanvasPoint } from './tracing'

describe('tracing stroke counter', () => {
  it('counts a stroke on release only when it has at least two points', () => {
    const counter = createStrokeCounter()

    counter.begin({ x: 10, y: 10 })
    expect(counter.active).toBe(true)
    expect(counter.end()).toBe(false)
    expect(counter.count).toBe(0)

    counter.begin({ x: 10, y: 10 })
    expect(counter.extend({ x: 20, y: 25 })).toEqual({ x: 10, y: 10 })
    expect(counter.end()).toBe(true)
    expect(counter.count).toBe(1)
    expect(counter.active).toBe(false)
    expect(counter.strokes[0]).toEqual([
      { x: 10, y: 10 },
      { x: 20, y: 25 },
    ])
  })

  it('ignores duplicate points and moves without an active stroke', () => {
    const counter = createStrokeCounter()

    expect(counter.extend({ x: 5, y: 5 })).toBeNull()
    counter.begin({ x: 5, y: 5 })
    expect(counter.extend({ x: 5, y: 5 })).toBeNull()
    expect(counter.end()).toBe(false)
    expect(counter.count).toBe(0)
  })

  it('drops a cancelled stroke and resets everything on clear', () => {
    const counter = createStrokeCounter()

    counter.begin({ x: 0, y: 0 })
    counter.extend({ x: 1, y: 1 })
    counter.cancel()
    expect(counter.end()).toBe(false)

    for (let stroke = 0; stroke < 3; stroke += 1) {
      counter.begin({ x: stroke, y: 0 })
      counter.extend({ x: stroke, y: 10 })
      counter.end()
    }
    expect(counter.count).toBe(3)

    counter.begin({ x: 0, y: 0 })
    counter.clear()
    expect(counter.count).toBe(0)
    expect(counter.strokes).toEqual([])
    expect(counter.active).toBe(false)
  })
})

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

  it('handles a missing target character and scales the glyph to its length', () => {
    expect(referenceGlyph(null)).toBeNull()
    expect(referenceGlyph('   ')).toBeNull()
    expect(referenceGlyph(' 好 ')).toBe('好')
    expect(referenceFontSize(300, '好')).toBe(234)
    expect(referenceFontSize(300, '你好')).toBe(117)
    expect(referenceFontSize(300, null)).toBe(0)
  })
})
