/**
 * In-app paths that a redirect query or a stored notification may point to. Old paths (for example /courses or
 * /student/achievements) are listed too: the router redirects them to their new pages (see `legacyRedirects` in
 * router/index.ts), so links written before the redesign keep working.
 */
const KNOWN_ROUTE_PATTERNS: readonly RegExp[] = [
  /^\/$/,
  /^\/(auth|forgot-password|reset-password|confirm-email|terms|privacy|responsible-ai|help|safety|unauthorized)$/,
  /^\/classes(\/\d+)?$/,
  /^\/(courses|catalog)(\/[\w-]+)?$/,
  /^\/student\/(home|setup|learn|ai|classes|progress|support|notifications|account)$/,
  /^\/student\/learn\/lessons\/\d+$/,
  /^\/student\/practice(\/(flashcards|quizzes|listening|tracing|drag-drop|matching|story|mistakes|review))?$/,
  /^\/student\/ai\/(talk|ask|quiz|story|voice)$/,
  /^\/student\/classes\/(find|live)$/,
  /^\/student\/classes\/\d+(\/(results|feedback))?$/,
  /^\/student\/classes\/\d+\/lessons\/\d+(\/activities\/[\w-]+\/\d+)?$/,
  /^\/student\/quiz\/\d+$/,
  /^\/student\/progress\/plan(\/level-check)?$/,
  /^\/student\/support\/\d+$/,
  /^\/student\/(achievements|rewards|profile|feedback|ai-helper|ai-reports|practice-submissions|live-classes)$/,
  /^\/student\/practice\/wrong-answers$/,
  /^\/teacher\/(dashboard|classes|live-sessions|students|profile|locked|support|notifications|account)$/,
  /^\/teacher\/classes\/\d+(\/(details|history))?$/,
  /^\/teacher\/classes\/\d+\/lessons\/\d+(\/(activities|quiz|preview))?$/,
  /^\/teacher\/live-sessions\/past$/,
  /^\/teacher\/students\/feedback$/,
  /^\/teacher\/support\/\d+$/,
  /^\/teacher\/(content|activities|feedback|live-classes|recorded-lessons|moderation-records|verification-status)$/,
  /^\/admin\/(dashboard|users|content|ai|support|analytics|audit-log|notifications|account)$/,
  /^\/admin\/users\/applications$/,
  /^\/admin\/content\/(classes|learning-path|practice)$/,
  /^\/admin\/ai\/(usage|settings)$/,
  /^\/admin\/(teacher-verification|teacher-approval|content-review|activities|moderation|ai-response-reports|settings)$/,
]

const UNSAFE_PATH_SEGMENTS = new Set(['.', '..'])

/** The path before its query or hash. */
function basePathOf(path: string): string {
  const suffixStart = path.search(/[?#]/)
  return suffixStart < 0 ? path : path.slice(0, suffixStart)
}

function containsUnsafePathSegment(basePath: string): boolean {
  // A segment that cannot be decoded counts as unsafe; `%2e%2e` decodes to `..`.
  return basePath.split('/').some((segment) => {
    try {
      return UNSAFE_PATH_SEGMENTS.has(decodeURIComponent(segment))
    } catch {
      return true
    }
  })
}

/**
 * A safe in-app target for a redirect or a notification link: the trimmed path when it is a first-party route
 * (no other origin, no traversal, no control characters), otherwise null.
 */
export function normalizeAppRoute(path: string | null | undefined): string | null {
  const trimmed = path?.trim()
  if (!trimmed || !trimmed.startsWith('/') || trimmed.startsWith('//')) return null
  if (trimmed.includes('\\') || /[\u0000-\u001f\u007f]/u.test(trimmed)) return null

  const basePath = basePathOf(trimmed)
  if (containsUnsafePathSegment(basePath)) return null
  return KNOWN_ROUTE_PATTERNS.some((pattern) => pattern.test(basePath)) ? trimmed : null
}
