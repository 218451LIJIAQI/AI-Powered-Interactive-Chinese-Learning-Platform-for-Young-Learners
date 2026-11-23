const DEFAULT_TIMEOUT_MS = 5000
const MAX_DURATION_SECONDS = 2_147_483_647

/**
 * Probes a local video file's duration in rounded seconds via an off-DOM `<video>` element. Resolves to `null` if the
 * browser cannot decode metadata, reports an out-of-range duration, or does not respond within `timeoutMs`, so uploads
 * never hang on metadata probing.
 */
export function probeVideoDuration(file: File, timeoutMs = DEFAULT_TIMEOUT_MS): Promise<number | null> {
  return new Promise((resolve) => {
    let url: string
    try {
      url = URL.createObjectURL(file)
    } catch {
      resolve(null)
      return
    }
    const probe = document.createElement('video')
    let settled = false
    const timeout = window.setTimeout(() => finish(null), timeoutMs)
    function finish(seconds: number | null): void {
      if (settled) return
      settled = true
      window.clearTimeout(timeout)
      probe.onloadedmetadata = null
      probe.onerror = null
      probe.removeAttribute('src')
      try {
        probe.load()
      } catch {
        // jsdom and restricted media environments may throw on unimplemented HTMLMediaElement.load().
      } finally {
        URL.revokeObjectURL(url)
        resolve(seconds)
      }
    }
    probe.preload = 'metadata'
    probe.onloadedmetadata = () => {
      const seconds = Number.isFinite(probe.duration) ? Math.round(probe.duration) : null
      finish(seconds !== null && seconds >= 0 && seconds <= MAX_DURATION_SECONDS ? seconds : null)
    }
    probe.onerror = () => finish(null)
    probe.src = url
  })
}
