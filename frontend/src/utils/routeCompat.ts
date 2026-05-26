/**
 * Backwards-compatibility shim for deep-links and stored
 * `?redirect=` paths that point at routes the app no longer ships.
 *
 * <h2>What it does</h2>
 * <ul>
 *   <li>Rewrites legacy paths to the current canonical equivalent
 *       (e.g. {@code /student/payments → /student/profile}).</li>
 *   <li>Dynamic-path variants are normalised through
 *       prefix-matching for routes like {@code /student/live-classes/123}.</li>
 *   <li>Validates that the resulting path is a known route so
 *       attacker-controlled redirect query params cannot be used to
 *       break out into unintended app surfaces.</li>
 * </ul>
 *
 * <p>Used by the auth flow when restoring the post-login redirect
 * target and by router guards that need to sanitise deep-links from
 * older email notifications.</p>
 */
const legacyRouteMap: Record<string, string> = {
  '/student/payments': '/student/profile',
  '/student/refunds': '/student/profile',
  '/student/feedback': '/student/profile',
  '/student/ai-reports': '/student/ai-helper',
  '/student/live-classes': '/student/classes',
  '/teacher/live-classes': '/teacher/classes',
  '/teacher/payouts': '/teacher/earnings',
  '/teacher/recorded-lessons': '/teacher/classes',
  '/teacher/moderation-records': '/teacher/feedback',
  '/teacher/verification-status': '/teacher/profile',
  '/admin/teacher-approval': '/admin/teacher-verification',
  '/admin/ai-response-reports': '/admin/moderation',
}

const exactAppRoutes = new Set([
  '/',
  '/auth',
  '/forgot-password',
  '/reset-password',
  '/terms',
  '/privacy',
  '/payment/success',
  '/payment/cancel',
  '/courses',
  '/unauthorized',
  '/student/home',
  '/student/learn',
  '/student/practice',
  '/student/quiz',
  '/student/classes',
  '/student/achievements',
  '/student/rewards',
  '/student/ai-helper',
  '/student/profile',
  '/teacher/dashboard',
  '/teacher/content',
  '/teacher/classes',
  '/teacher/students',
  '/teacher/feedback',
  '/teacher/profile',
  '/teacher/earnings',
  '/admin/dashboard',
  '/admin/users',
  '/admin/teacher-verification',
  '/admin/content',
  '/admin/activities',
  '/admin/moderation',
  '/admin/finance',
  '/admin/analytics',
  '/admin/settings',
])

const dynamicRoutePrefixes = ['/courses/', '/student/quiz/']

const UNSAFE_PATH_SEGMENTS = new Set(['.', '..'])

function splitPathAndSuffix(path: string) {
  const queryStart = path.indexOf('?')
  const hashStart = path.indexOf('#')
  const suffixStart = Math.min(queryStart >= 0 ? queryStart : path.length, hashStart >= 0 ? hashStart : path.length)

  return {
    basePath: path.slice(0, suffixStart),
    suffix: path.slice(suffixStart),
  }
}

function containsUnsafePathSegment(basePath: string) {
  return basePath.split('/').some((segment) => {
    if (UNSAFE_PATH_SEGMENTS.has(segment)) {
      return true
    }

    try {
      return UNSAFE_PATH_SEGMENTS.has(decodeURIComponent(segment))
    } catch {
      return true
    }
  })
}

function isSafeRedirectCandidate(path: string, basePath: string) {
  return !path.includes('\\') && !/[\u0000-\u001f\u007f]/u.test(path) && !containsUnsafePathSegment(basePath)
}

function isKnownAppRoute(basePath: string) {
  return exactAppRoutes.has(basePath) || dynamicRoutePrefixes.some((prefix) => basePath.startsWith(prefix))
}

export function normalizeAppRoute(path: string | null | undefined) {
  if (!path) {
    return null
  }

  const trimmed = path.trim()
  if (!trimmed.startsWith('/') || trimmed.startsWith('//')) {
    return null
  }

  const { basePath, suffix } = splitPathAndSuffix(trimmed)
  if (!isSafeRedirectCandidate(trimmed, basePath)) {
    return null
  }

  if (legacyRouteMap[basePath]) {
    return `${legacyRouteMap[basePath]}${suffix}`
  }

  if (basePath.startsWith('/student/live-classes/')) {
    return `/student/classes${suffix}`
  }

  if (basePath.startsWith('/admin/ai-response-reports/')) {
    return `/admin/moderation${suffix}`
  }

  if (!isKnownAppRoute(basePath)) {
    return null
  }

  return trimmed
}
