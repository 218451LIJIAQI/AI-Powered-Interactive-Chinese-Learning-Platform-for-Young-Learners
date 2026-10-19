import type { LiveClassResponse } from '@/types/api'

/** Seat availability of one live class (FR-LIVE-05). */
export type LiveClassSeatState =
  | { kind: 'unlimited' }
  | { kind: 'full'; total: number }
  | { kind: 'available'; remaining: number; total: number }

/**
 * The single main action a learner can take on a class card:
 * - `join`: enrolled and inside the join window (backend `canJoin`);
 * - `enrol`: the backend allows enrolment (`canEnrol`);
 * - `full`: not enrolled and every seat is taken;
 * - `waiting`: enrolled, but the join window is not open yet;
 * - `closed`: nothing to do (cancelled, completed or not open for enrolment).
 */
type LiveClassPrimaryAction = 'join' | 'enrol' | 'full' | 'waiting' | 'closed'

export interface LiveClassActionState {
  primary: LiveClassPrimaryAction
  /** True when the learner may still cancel the enrolment (enrolled, not started, not terminal). */
  canCancel: boolean
}

type SeatFields = Pick<LiveClassResponse, 'maxParticipants' | 'enrolledCount' | 'remainingSeats' | 'full'>
type ActionFields = SeatFields &
  Pick<LiveClassResponse, 'canJoin' | 'canEnrol' | 'enrolled' | 'liveClassStatus' | 'startTime'>

const TERMINAL_STATUSES = new Set(['CANCELLED', 'COMPLETED'])
const CANCELLABLE_STATUSES = new Set(['SCHEDULED', 'RESCHEDULED'])

export function liveClassSeatState(item: SeatFields): LiveClassSeatState {
  const total = item.maxParticipants ?? null
  if (total === null || total <= 0) return { kind: 'unlimited' }

  const remaining = Math.max(0, item.remainingSeats ?? total - (item.enrolledCount ?? 0))
  const full = item.full ?? remaining <= 0
  if (full || remaining <= 0) return { kind: 'full', total }
  return { kind: 'available', remaining, total }
}

export function isTerminalLiveClass(item: Pick<LiveClassResponse, 'liveClassStatus'>): boolean {
  return TERMINAL_STATUSES.has(item.liveClassStatus)
}

export function liveClassActionState(item: ActionFields, nowMs: number = Date.now()): LiveClassActionState {
  const start = new Date(item.startTime).getTime()
  const notStarted = Number.isFinite(start) && start > nowMs
  const terminal = isTerminalLiveClass(item)
  const canCancel = item.enrolled && !terminal && CANCELLABLE_STATUSES.has(item.liveClassStatus) && notStarted

  let primary: LiveClassPrimaryAction = 'closed'
  if (item.canJoin) primary = 'join'
  else if (item.canEnrol) primary = 'enrol'
  else if (!item.enrolled && !terminal && liveClassSeatState(item).kind === 'full') primary = 'full'
  else if (item.enrolled && !terminal) primary = 'waiting'

  return { primary, canCancel }
}

const IN_PROGRESS_STATUSES = new Set(['ONGOING', 'IN_PROGRESS', 'LIVE'])

/**
 * D21: an in-progress (ongoing / live) status is only shown while `nowMs` is inside the class window. Before the start
 * (or with an unreadable time) the class shows as SCHEDULED, after the end as COMPLETED; other statuses are unchanged.
 */
export function effectiveLiveClassStatus(
  item: Pick<LiveClassResponse, 'startTime' | 'endTime'> & { liveClassStatus: string },
  nowMs: number = Date.now(),
): string {
  if (!IN_PROGRESS_STATUSES.has((item.liveClassStatus ?? '').toUpperCase())) return item.liveClassStatus
  const start = new Date(item.startTime).getTime()
  const end = new Date(item.endTime).getTime()
  if (Number.isNaN(start) || Number.isNaN(end) || nowMs < start) return 'SCHEDULED'
  if (nowMs > end) return 'COMPLETED'
  return item.liveClassStatus
}

/** Sorts classes by start time (earliest first) without mutating the input. */
export function sortLiveClassesByStart<T extends Pick<LiveClassResponse, 'startTime'>>(items: readonly T[]): T[] {
  const time = (value: string) => {
    const parsed = new Date(value).getTime()
    return Number.isFinite(parsed) ? parsed : Number.POSITIVE_INFINITY
  }
  return [...items].sort((left, right) => time(left.startTime) - time(right.startTime))
}

/** Replaces the class with the same id by the updated response returned from enrol / cancel. */
export function replaceLiveClass<T extends { id: number }>(items: readonly T[], updated: T): T[] {
  return items.map((item) => (item.id === updated.id ? updated : item))
}

export interface LiveClassScheduleLabels {
  date: string
  time: string
  timeZone: string
}

/** Returns the IANA zone when the runtime supports it, otherwise undefined (browser zone). */
export function safeTimeZone(timeZone: string | null | undefined): string | undefined {
  if (!timeZone) return undefined
  try {
    new Intl.DateTimeFormat('en', { timeZone }).format(0)
    return timeZone
  } catch {
    return undefined
  }
}

/** Formats a class's date and time range in the class's own time zone. */
export function formatLiveClassSchedule(
  item: Pick<LiveClassResponse, 'startTime' | 'endTime' | 'timeZone'>,
  locale: string,
): LiveClassScheduleLabels | null {
  const start = new Date(item.startTime)
  const end = new Date(item.endTime)
  if (Number.isNaN(start.getTime())) return null

  const timeZone = safeTimeZone(item.timeZone)
  const date = new Intl.DateTimeFormat(locale, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone,
  }).format(start)
  const timeFormat = new Intl.DateTimeFormat(locale, { hour: 'numeric', minute: '2-digit', timeZone })
  const time = Number.isNaN(end.getTime())
    ? timeFormat.format(start)
    : `${timeFormat.format(start)} - ${timeFormat.format(end)}`

  return { date, time, timeZone: timeZone ?? item.timeZone ?? '' }
}
