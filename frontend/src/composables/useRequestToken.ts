/**
 * Monotonic request token guard for pages that load the same logical
 * resource multiple times (apply filters / change page / fast-click /
 * navigate away and back). Without this guard the slowest in-flight
 * request can resolve last and overwrite the newer correct result.
 *
 * Usage:
 *
 * ```ts
 * const coursesToken = useRequestToken()
 *
 * async function loadCourses() {
 *   const token = coursesToken.start()
 *   loading.value = true
 *   try {
 *     const data = await courseService.list(filters)
 *     if (!coursesToken.isCurrent(token)) {
 *       // A newer request has already started; drop the stale response.
 *       return
 *     }
 *     items.value = data
 *   } catch (error) {
 *     if (!coursesToken.isCurrent(token)) return
 *     errorMessage.value = extractApiError(error)
 *   } finally {
 *     if (coursesToken.isCurrent(token)) loading.value = false
 *   }
 * }
 * ```
 *
 * <p>The token is a plain monotonically-increasing integer scoped to the
 * composable instance — there is no shared global state, so two components
 * each calling {@code useRequestToken()} get fully independent guards.</p>
 *
 * <p>This composable is intentionally tiny because the value comes from
 * having a single well-tested implementation that pages reuse, instead of
 * the previous pattern where each page re-implemented the same five lines
 * inline and risked subtle drift.</p>
 */
export interface RequestTokenGuard {
  /**
   * Allocate and return a new token. The newly returned token becomes the
   * "current" one; any previously-allocated token is now stale.
   */
  start(): number

  /**
   * Returns {@code true} if the supplied token is still the most-recently
   * allocated one. Callers should check this both before applying the
   * response (success path) AND before surfacing an error (rejection path)
   * because a stale rejection should not clobber the newer request's state.
   */
  isCurrent(token: number): boolean

  /**
   * Returns the current token without allocating a new one. Useful for
   * tests; production code rarely needs it.
   */
  current(): number
}

export function useRequestToken(): RequestTokenGuard {
  let current = 0
  return {
    start() {
      current += 1
      return current
    },
    isCurrent(token: number) {
      return token === current
    },
    current() {
      return current
    },
  }
}
