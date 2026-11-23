type RoadState = 'done' | 'next' | 'later'

export interface RoadPoint {
  x: number
  y: number
}

export interface RoadSegment {
  key: string
  d: string
  state: RoadState
}

interface RoadLayout {
  you: RoadPoint
  goal: RoadPoint
  learn: RoadPoint | null
  /** Bottom y-coordinate of the Learn unit copy (relative to the road root) so departing curves drop clear of text. */
  learnWordsEnd: number
  fork: RoadPoint | null
  branches: readonly { point: RoadPoint; picked: boolean }[]
}

/** Down out of a mark, across, and into the next one from above. */
function trunkCurve(from: RoadPoint, to: RoadPoint): string {
  const height = to.y - from.y
  return `M ${from.x} ${from.y} C ${from.x} ${from.y + height * 0.65} ${to.x} ${from.y + height * 0.4} ${to.x} ${to.y}`
}

/** Down past the words beside a mark, then across and into the next one from above. */
function trunkBelowCurve(from: RoadPoint, to: RoadPoint, wordsEnd: number): string {
  const bend = Math.max(from.y, Math.min(wordsEnd, to.y - 24))
  const height = to.y - bend
  return `M ${from.x} ${from.y} L ${from.x} ${bend} C ${from.x} ${bend + height * 0.7} ${to.x} ${bend + height * 0.3} ${to.x} ${to.y}`
}

/** From the fork to a class. A class directly below the fork follows the trunk. */
function intoBranchCurve(fork: RoadPoint, branch: RoadPoint): string {
  if (Math.abs(branch.x - fork.x) < 1) return trunkCurve(fork, branch)
  return `M ${fork.x} ${fork.y} C ${fork.x + (branch.x - fork.x) * 0.6} ${fork.y} ${branch.x} ${fork.y} ${branch.x} ${branch.y}`
}

/** Down from the class and around to the goal when it lies to one side. */
function intoGoalCurve(branch: RoadPoint, goal: RoadPoint): string {
  if (Math.abs(branch.x - goal.x) < 1) return trunkCurve(branch, goal)
  const bend = Math.max(goal.y - 56, branch.y)
  return `M ${branch.x} ${branch.y} L ${branch.x} ${bend} C ${branch.x} ${goal.y - 8} ${branch.x + (goal.x - branch.x) * 0.5} ${goal.y} ${goal.x} ${goal.y}`
}

/**
 * Computes the SVG path segments connecting measured landmarks on the learning-plan road, sorted so highlighted
 * (`state === 'next'`) segments render last and stay on top where paths overlap.
 */
export function buildRoadSegments(layout: RoadLayout): RoadSegment[] {
  const { you, goal, learn, learnWordsEnd, fork, branches } = layout
  const list: RoadSegment[] = []
  const fromLearnOrYou = (target: RoadPoint) =>
    learn ? trunkBelowCurve(learn, target, learnWordsEnd) : trunkCurve(you, target)

  if (learn) {
    list.push({ key: 'you-learn', d: trunkCurve(you, learn), state: 'done' })
  }
  if (fork) {
    list.push({ key: 'learn-fork', d: fromLearnOrYou(fork), state: learn ? 'next' : 'done' })
  }

  branches.forEach(({ point, picked }, index) => {
    const state: RoadState = picked ? 'next' : 'later'
    list.push({ key: `fork-${index}`, d: fork ? intoBranchCurve(fork, point) : fromLearnOrYou(point), state })
    list.push({ key: `goal-${index}`, d: intoGoalCurve(point, goal), state })
  })

  if (!branches.length) {
    list.push({
      key: 'to-goal',
      d: fork ? trunkCurve(fork, goal) : fromLearnOrYou(goal),
      state: learn ? 'next' : 'done',
    })
  }

  return list.sort((a, b) => Number(a.state === 'next') - Number(b.state === 'next'))
}
