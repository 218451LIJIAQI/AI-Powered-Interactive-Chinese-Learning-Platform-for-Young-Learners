/**
 * The geometry of a Learn map board (LearnLevelMap, LearnMapSkeleton): one level every 130px down a winding road,
 * swinging 140px either side of the board's centre line (centre, left, centre, right, …). Coordinates are in px from
 * the centre line (x) and the top of the board (y), so the drawing follows the width of the column.
 */
interface MapPoint {
  readonly x: number
  readonly y: number
}

const LEVEL_STEP = 130
const LEVEL_SWING = 140
/** From the top of the board to the first level's disc, and from the last disc to the bottom (room for its label). */
const MAP_TOP = 84
const MAP_BOTTOM = 96
const SWING_PATTERN = [0, -1, 0, 1] as const

/** Where level `index` sits; the road also passes the places before the first level and after the last. */
export function levelPoint(index: number): MapPoint {
  const cycle = SWING_PATTERN.length
  return { x: LEVEL_SWING * SWING_PATTERN[((index % cycle) + cycle) % cycle]!, y: MAP_TOP + index * LEVEL_STEP }
}

export function mapHeight(levels: number): number {
  return MAP_TOP + Math.max(0, levels - 1) * LEVEL_STEP + MAP_BOTTOM
}

const round = (value: number) => Math.round(value * 10) / 10

/** The road from place `index` to the next one: a Catmull-Rom curve, so it bends smoothly through every disc. */
function roadSegment(index: number): string {
  const before = levelPoint(index - 1)
  const from = levelPoint(index)
  const to = levelPoint(index + 1)
  const after = levelPoint(index + 2)
  const first = { x: from.x + (to.x - before.x) / 6, y: from.y + (to.y - before.y) / 6 }
  const second = { x: to.x - (after.x - from.x) / 6, y: to.y - (after.y - from.y) / 6 }
  return `C${round(first.x)} ${round(first.y)} ${round(second.x)} ${round(second.y)} ${to.x} ${to.y}`
}

/**
 * The road from where it enters the board (above its top edge) up to level `last`; `last` equal to the number of
 * levels runs it on past the bottom edge.
 */
export function roadPath(last: number): string {
  const start = levelPoint(-1)
  let path = `M${start.x} ${start.y}`
  for (let index = -1; index < last; index += 1) path += roadSegment(index)
  return path
}

/** Grass tufts on the ground beside the road, every few levels on alternating sides. */
export function groundTufts(height: number): string[] {
  const tufts: string[] = []
  for (let row = 0, y = 128; y < height - 24; row += 1, y += 236) {
    const x = (row % 2 === 0 ? -1 : 1) * (262 + (row % 3) * 22)
    tufts.push(`M${x - 5} ${y}l-2 -7M${x} ${y}v-9M${x + 5} ${y}l2 -7`)
  }
  return tufts
}
