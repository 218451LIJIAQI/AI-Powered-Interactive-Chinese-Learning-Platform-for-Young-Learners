import { describe, expect, it } from 'vitest'

import { useRequestToken } from './useRequestToken'

describe('useRequestToken', () => {
  it('starts at token 0 before any start() call', () => {
    const guard = useRequestToken()
    expect(guard.current()).toBe(0)
    // The token 0 is a "no request issued yet" sentinel — isCurrent(0) is
    // true only when no requests have started, never after.
    expect(guard.isCurrent(0)).toBe(true)
  })

  it('returns a strictly increasing token from each start() call', () => {
    const guard = useRequestToken()
    expect(guard.start()).toBe(1)
    expect(guard.start()).toBe(2)
    expect(guard.start()).toBe(3)
  })

  it('treats only the latest token as current', () => {
    const guard = useRequestToken()
    const first = guard.start()
    const second = guard.start()
    expect(guard.isCurrent(first)).toBe(false)
    expect(guard.isCurrent(second)).toBe(true)
  })

  it('preserves currency of the latest token when checked multiple times', () => {
    const guard = useRequestToken()
    const token = guard.start()
    expect(guard.isCurrent(token)).toBe(true)
    expect(guard.isCurrent(token)).toBe(true)
    expect(guard.current()).toBe(token)
  })

  it('drops every previously-issued token once a newer one is allocated', () => {
    const guard = useRequestToken()
    const tokens = [guard.start(), guard.start(), guard.start(), guard.start()]
    const latest = guard.start()
    for (const stale of tokens) {
      expect(guard.isCurrent(stale)).toBe(false)
    }
    expect(guard.isCurrent(latest)).toBe(true)
  })

  it('keeps independent state across separate composable instances', () => {
    const first = useRequestToken()
    const second = useRequestToken()
    const firstToken = first.start()
    second.start()
    second.start()
    // Allocating tokens on the second guard must not invalidate the first
    // guard's current token — they are independent state machines.
    expect(first.isCurrent(firstToken)).toBe(true)
    expect(first.current()).toBe(1)
    expect(second.current()).toBe(2)
  })

  it('models the documented race scenario correctly', async () => {
    // This test reproduces the production race: user calls loadCourses()
    // twice in quick succession; the slow first request resolves AFTER the
    // fast second one. The guard must drop the slow stale result.
    const guard = useRequestToken()
    const trackedResults: number[] = []

    async function loadCourses(simulatedLatencyMs: number, payload: number) {
      const token = guard.start()
      await new Promise((resolve) => setTimeout(resolve, simulatedLatencyMs))
      if (!guard.isCurrent(token)) {
        return
      }
      trackedResults.push(payload)
    }

    // Kick off both concurrently — the SLOW one starts first.
    const slow = loadCourses(40, 1)
    const fast = loadCourses(5, 2)
    await Promise.all([slow, fast])

    // Only the latest request's payload (2) should appear; the stale slow
    // response (1) must be discarded.
    expect(trackedResults).toEqual([2])
  })

  it('also drops stale rejection paths so errors do not clobber newer state', async () => {
    const guard = useRequestToken()
    const trackedErrors: string[] = []

    async function loadCourses(simulatedLatencyMs: number, shouldFail: boolean, label: string) {
      const token = guard.start()
      try {
        await new Promise((resolve, reject) =>
          setTimeout(() => (shouldFail ? reject(new Error(label)) : resolve(null)), simulatedLatencyMs),
        )
        // success path intentionally ignored for this test
      } catch (error) {
        if (!guard.isCurrent(token)) return
        trackedErrors.push((error as Error).message)
      }
    }

    // First request will FAIL slowly; second request will SUCCEED quickly.
    const slowFailing = loadCourses(40, true, 'stale-error')
    const fastSucceeding = loadCourses(5, false, 'fast-success')
    await Promise.all([slowFailing, fastSucceeding])

    // The slow rejection must be discarded — otherwise the error banner
    // would flash for the user even though their latest request succeeded.
    expect(trackedErrors).toEqual([])
  })
})
