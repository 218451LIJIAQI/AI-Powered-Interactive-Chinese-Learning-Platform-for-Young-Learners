import type { MeetingPlatform, TeacherSession } from '@/types/classes'
import { formatTime, formatWeekdayDate } from '@/utils/format'

/** "Mon, May 18 · 4:00 PM – 4:45 PM": when a session runs, as every session row and dialog shows it. */
export function sessionWhen(session: Pick<TeacherSession, 'startsAt' | 'endsAt'>): string {
  return `${formatWeekdayDate(session.startsAt)} · ${formatTime(session.startsAt)} – ${formatTime(session.endsAt)}`
}

const PLATFORM_HOSTS: readonly (readonly [RegExp, MeetingPlatform])[] = [
  [/(^|\.)zoom\.us$/i, 'ZOOM'],
  [/(^|\.)meet\.google\.com$/i, 'GOOGLE_MEET'],
  [/(^|\.)teams\.(microsoft|live)\.com$/i, 'MICROSOFT_TEAMS'],
]

/** The teacher no longer picks a platform; the server still records one, so it is read from the meeting link. */
export function platformFromLink(link: string): MeetingPlatform {
  let host = ''
  try {
    host = new URL(link).hostname
  } catch {
    return 'OTHER'
  }
  return PLATFORM_HOSTS.find(([pattern]) => pattern.test(host))?.[1] ?? 'OTHER'
}
