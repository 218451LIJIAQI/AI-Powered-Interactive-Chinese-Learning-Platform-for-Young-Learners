import { describe, expect, it } from 'vitest'

import { buildRoadSegments } from './planRoad'

/** Parses `M sx sy [L lx ly] C c1x c1y c2x c2y ex ey` into landmark points and control-point coordinates. */
function parseCubic(d: string) {
  const match = /^M (\S+) (\S+)(?: L (\S+) (\S+))? C (\S+) (\S+) (\S+) (\S+) (\S+) (\S+)$/.exec(d)
  if (!match) throw new Error(`Unexpected road SVG path: ${d}`)
  return {
    start: { x: Number(match[1]), y: Number(match[2]) },
    stem: match[3] !== undefined ? { x: Number(match[3]), y: Number(match[4]) } : null,
    c1: { x: Number(match[5]), y: Number(match[6]) },
    c2: { x: Number(match[7]), y: Number(match[8]) },
    end: { x: Number(match[9]), y: Number(match[10]) },
  }
}

describe('planRoad', () => {
  it('draws a direct route from You through Learn to Goal when there are no branches', () => {
    const segments = buildRoadSegments({
      you: { x: 100, y: 20 },
      learn: { x: 100, y: 120 },
      learnWordsEnd: 170,
      fork: null,
      branches: [],
      goal: { x: 100, y: 260 },
    })

    expect(segments.map(({ key, state }) => [key, state])).toEqual([
      ['you-learn', 'done'],
      ['to-goal', 'next'],
    ])
    const youLearn = parseCubic(segments[0]!.d)
    expect(youLearn.start).toEqual({ x: 100, y: 20 })
    expect(youLearn.end).toEqual({ x: 100, y: 120 })
    expect(youLearn.stem).toBeNull()

    const toGoal = parseCubic(segments[1]!.d)
    expect(toGoal.start).toEqual({ x: 100, y: 120 })
    expect(toGoal.stem).toEqual({ x: 100, y: 170 })
    expect(toGoal.end).toEqual({ x: 100, y: 260 })
  })

  it('draws a done trunk straight from You to Goal when no Learn unit remains', () => {
    const segments = buildRoadSegments({
      you: { x: 100, y: 20 },
      learn: null,
      learnWordsEnd: 0,
      fork: null,
      branches: [],
      goal: { x: 100, y: 200 },
    })

    expect(segments.map(({ key, state }) => [key, state])).toEqual([['to-goal', 'done']])
    const toGoal = parseCubic(segments[0]!.d)
    expect(toGoal.start).toEqual({ x: 100, y: 20 })
    expect(toGoal.end).toEqual({ x: 100, y: 200 })
    expect(toGoal.c1.x).toBe(100)
    expect(toGoal.c2.x).toBe(100)
  })

  it('draws fork branches and places the picked ("next") branch segments last so they stay on top', () => {
    const fork = { x: 150, y: 220 }
    const left = { x: 60, y: 320 }
    const right = { x: 240, y: 320 }
    const goal = { x: 150, y: 440 }

    const segments = buildRoadSegments({
      you: { x: 150, y: 20 },
      learn: { x: 150, y: 110 },
      learnWordsEnd: 165,
      fork,
      branches: [
        { point: left, picked: true },
        { point: right, picked: false },
      ],
      goal,
    })

    expect(segments.map((segment) => [segment.key, segment.state])).toEqual([
      ['you-learn', 'done'],
      ['fork-1', 'later'],
      ['goal-1', 'later'],
      ['learn-fork', 'next'],
      ['fork-0', 'next'],
      ['goal-0', 'next'],
    ])
    const forkLeft = parseCubic(segments.find((segment) => segment.key === 'fork-0')!.d)
    expect(forkLeft.start).toEqual(fork)
    expect(forkLeft.end).toEqual(left)
    expect(forkLeft.c1.x).toBeLessThan(fork.x)
    expect(forkLeft.c1.x).toBeGreaterThan(left.x)

    const goalLeft = parseCubic(segments.find((segment) => segment.key === 'goal-0')!.d)
    expect(goalLeft.start).toEqual(left)
    expect(goalLeft.stem?.x).toBe(left.x)
    expect(goalLeft.stem!.y).toBeGreaterThan(left.y)
    expect(goalLeft.end).toEqual(goal)
    expect(goalLeft.c2.x).toBeGreaterThan(left.x)
    expect(goalLeft.c2.x).toBeLessThan(goal.x)
  })

  it('uses a straight trunk curve when a branch aligns vertically with the fork and goal', () => {
    const fork = { x: 150, y: 200 }
    const center = { x: 150, y: 310 }
    const goal = { x: 150, y: 420 }

    const segments = buildRoadSegments({
      you: { x: 150, y: 20 },
      learn: null,
      learnWordsEnd: 0,
      fork,
      branches: [{ point: center, picked: false }],
      goal,
    })

    const forkCenter = parseCubic(segments.find((segment) => segment.key === 'fork-0')!.d)
    expect(forkCenter.start).toEqual(fork)
    expect(forkCenter.end).toEqual(center)
    expect(forkCenter.c1.x).toBe(150)
    expect(forkCenter.c2.x).toBe(150)

    const goalCenter = parseCubic(segments.find((segment) => segment.key === 'goal-0')!.d)
    expect(goalCenter.start).toEqual(center)
    expect(goalCenter.end).toEqual(goal)
    expect(goalCenter.c1.x).toBe(150)
    expect(goalCenter.c2.x).toBe(150)
  })
})
