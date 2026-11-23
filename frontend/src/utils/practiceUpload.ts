/**
 * Format and limits of recordings and handwriting photos sent to `POST /ai/practice-checks`.
 */

/** Same limit as the target text of a practice check (`targetText` @Size). */
export const MAX_PRACTICE_TARGET_LENGTH = 100
/** Upper bound of a single browser recording. */
export const MAX_RECORDING_SECONDS = 60

/**
 * The one format recorded in the browser: WEBM with Opus. The current Chrome, Edge and Firefox all record it, and the
 * server accepts WEBM; any other browser falls back to choosing a file.
 */
export const RECORDER_MIME_TYPE = 'audio/webm;codecs=opus'
/** Content type of the recorded file (the server checks the base type). */
export const RECORDING_FILE_TYPE = 'audio/webm'

/** e.g. `pronunciation-20260518-0300.webm` (local time). */
export function recordingFileName(now: Date = new Date()): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}`
  return `pronunciation-${stamp}.webm`
}
