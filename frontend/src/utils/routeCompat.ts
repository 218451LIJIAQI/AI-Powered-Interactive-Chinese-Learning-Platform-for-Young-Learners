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
  '/reset-password',
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

const dynamicRoutePrefixes = [
  '/courses/',
  '/student/quiz/',
]

function splitPathAndSuffix(path: string) {
  const queryStart = path.indexOf('?')
  const hashStart = path.indexOf('#')
  const suffixStart = [queryStart, hashStart].filter((index) => index >= 0).sort((left, right) => left - right)[0]

  if (suffixStart === undefined) {
    return {
      basePath: path,
      suffix: '',
    }
  }

  return {
    basePath: path.slice(0, suffixStart),
    suffix: path.slice(suffixStart),
  }
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
