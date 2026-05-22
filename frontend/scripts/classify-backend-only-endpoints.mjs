import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const frontendDir = path.resolve(__dirname, '..')
const repoRoot = path.resolve(frontendDir, '..')

const openapiPathsPath = path.resolve(repoRoot, 'backend', 'target', 'fresh-openapi-paths.txt')
const servicesPath = path.resolve(frontendDir, 'src', 'api', 'services.ts')
const routerPath = path.resolve(frontendDir, 'src', 'router', 'index.ts')
const outputPath = path.resolve(repoRoot, 'backend', 'target', 'backend-only-endpoint-classification.json')

const openapiPaths = (await fs.readFile(openapiPathsPath, 'utf8'))
  .split(/\r?\n/)
  .map((line) => line.trim())
  .filter(Boolean)

const servicesSource = await fs.readFile(servicesPath, 'utf8')
const routerSource = await fs.readFile(routerPath, 'utf8')

const serviceCallPattern = /http\.(get|post|put|patch|delete)\((`[^`]+`|'[^']+'|"[^"]+")/g
const frontendCalls = []
for (const match of servicesSource.matchAll(serviceCallPattern)) {
  const raw = match[2].slice(1, -1)
  if (raw === '/admin/moderation/${action}') {
    for (const action of ['hide', 'unpublish', 'archive', 'request-revision', 'restore']) {
      frontendCalls.push({
        method: match[1].toUpperCase(),
        raw: `/admin/moderation/${action}`,
        normalized: `/api/v1/admin/moderation/${action}`,
      })
    }
    continue
  }
  frontendCalls.push({
    method: match[1].toUpperCase(),
    raw,
    normalized: normalizeFrontendPath(raw),
  })
}

const classification = openapiPaths.map((endpoint) => {
  const directCalls = frontendCalls.filter((call) => pathMatches(call.normalized, endpoint))
  const manual = manualClassification(endpoint)
  return {
    endpoint,
    frontendDirectCallCount: directCalls.length,
    frontendDirectCalls: directCalls,
    status: directCalls.length > 0 ? 'FRONTEND_CALLED' : manual.status,
    reason: directCalls.length > 0 ? 'Found direct frontend API client call in src/api/services.ts.' : manual.reason,
    frontendRouteEvidence: manual.routeEvidence ?? routeEvidence(endpoint),
  }
})

const summary = {
  generatedAt: new Date().toISOString(),
  totalOpenApiEndpoints: classification.length,
  frontendCalled: classification.filter((item) => item.status === 'FRONTEND_CALLED').length,
  backendOnly: classification.filter((item) => item.status !== 'FRONTEND_CALLED').length,
  byStatus: classification.reduce((acc, item) => {
    acc[item.status] = (acc[item.status] ?? 0) + 1
    return acc
  }, {}),
}

await fs.writeFile(outputPath, JSON.stringify({ summary, classification }, null, 2))
console.log(JSON.stringify(summary, null, 2))

function normalizeFrontendPath(rawPath) {
  let normalized = rawPath
    .replace(/\$\{[^}]+\}/g, '{id}')
    .replace(/\/\{id\}\/items\/\{id\}/g, '/{packageId}/items/{itemId}')
    .replace('/admin/moderation/{id}', '/admin/moderation/{action}')
  if (!normalized.startsWith('/api/v1')) normalized = `/api/v1${normalized}`
  return normalized
}

function pathMatches(frontendPath, openapiPath) {
  const frontendParts = frontendPath.split('/').filter(Boolean)
  const openapiParts = openapiPath.split('/').filter(Boolean)
  if (frontendParts.length !== openapiParts.length) return false
  return frontendParts.every((part, index) => {
    const other = openapiParts[index]
    return part === other || (part.startsWith('{') && other.startsWith('{'))
  })
}

function routeEvidence(endpoint) {
  if (endpoint.includes('/admin/')) {
    return containsAny(routerSource, ['/admin/dashboard', '/admin/analytics', '/admin/content', '/admin/moderation', '/admin/finance', '/admin/settings'])
      ? 'Admin route shell exists and related list/action pages are routed.'
      : null
  }
  if (endpoint.includes('/students/')) {
    return containsAny(routerSource, ['/student/home', '/student/learn', '/student/practice', '/student/profile'])
      ? 'Student route shell exists and related pages are routed.'
      : null
  }
  if (endpoint.includes('/teachers/')) {
    return containsAny(routerSource, ['/teacher/content', '/teacher/classes', '/teacher/profile'])
      ? 'Teacher route shell exists and related pages are routed.'
      : null
  }
  return null
}

function containsAny(source, needles) {
  return needles.some((needle) => source.includes(needle))
}

function manualClassification(endpoint) {
  const details = {
    '/api/v1/admin/live-classes/{id}': ['BACKEND_ONLY_DETAIL_DRILLDOWN', 'Admin live-class detail endpoint exists, while current admin UI uses list rows rather than a separate detail view.'],
    '/api/v1/admin/moderation-records/{id}': ['BACKEND_ONLY_DETAIL_DRILLDOWN', 'Admin moderation record detail endpoint exists; current moderation page consumes the list queue only.'],
    '/api/v1/admin/ai-response-reports/{id}': ['BACKEND_ONLY_DETAIL_DRILLDOWN', 'Admin AI report detail endpoint exists; current moderation queue reviews from the list item payload.'],
    '/api/v1/admin/audit-logs/{id}': ['BACKEND_ONLY_DETAIL_DRILLDOWN', 'Audit log detail endpoint exists; current admin UI consumes paged audit list only.'],
    '/api/v1/admin/payments/{paymentId}': ['BACKEND_ONLY_DETAIL_DRILLDOWN', 'Admin payment detail endpoint exists; finance UI consumes paged payment list.'],
    '/api/v1/public/teachers/{id}': ['BACKEND_ONLY_OPTIONAL_PUBLIC_DETAIL', 'Public teacher profile detail endpoint exists; current catalogue shows teacher summaries inside course cards.'],
    '/api/v1/students/analytics': ['BACKEND_ONLY_OPTIONAL_ANALYTICS', 'Student analytics endpoint exists; current student dashboard/profile use dashboard/progress/reward endpoints instead.'],
    '/api/v1/students/feedback/{id}': ['BACKEND_ONLY_DETAIL_DRILLDOWN', 'Student feedback detail endpoint exists; current student profile consumes feedback list.'],
    '/api/v1/students/lessons/{lessonId}/recorded-videos': ['BACKEND_ONLY_MEDIA_DETAIL', 'Recorded-video listing exists for lesson player extensions; current student flow loads lesson player and video progress directly.'],
    '/api/v1/students/lessons/{lessonId}/recorded-videos/{videoId}': ['BACKEND_ONLY_MEDIA_DETAIL', 'Recorded-video detail endpoint exists for media player drilldown; current student UI has no separate video detail route.'],
    '/api/v1/students/live-classes/{id}': ['BACKEND_ONLY_DETAIL_DRILLDOWN', 'Student live-class detail endpoint exists; classes page currently uses list/join/enrol actions.'],
    '/api/v1/students/live-classes/{id}/cancel-enrolment': ['BACKEND_ONLY_OPTIONAL_ACTION', 'Cancel-enrolment endpoint exists; current student class UI exposes enrol/join but no cancel button was found.'],
    '/api/v1/students/payments/{paymentId}': ['BACKEND_ONLY_DETAIL_DRILLDOWN', 'Student payment detail endpoint exists; profile/payment history currently uses list plus payment status endpoint.'],
    '/api/v1/students/quiz-attempts/{attemptId}': ['BACKEND_ONLY_RESULT_DETAIL', 'Quiz-attempt detail endpoint exists; current quiz UI renders the submission response inline after POST.'],
    '/api/v1/students/quiz-attempts/{attemptId}/wrong-answers': ['BACKEND_ONLY_RESULT_DETAIL', 'Wrong-answer attempt detail exists; current practice page uses the aggregate wrong-answers endpoint.'],
    '/api/v1/students/recorded-videos/{videoId}/stream': ['BACKEND_ONLY_MEDIA_STREAM', 'Protected video stream endpoint is intended as media src/download target, not a JSON API client method.'],
    '/api/v1/students/video-progress/{videoId}': ['FRONTEND_CALLED_ALIAS', 'Frontend uses GET/POST video-progress; PATCH variant remains an alternate partial-update endpoint.'],
    '/api/v1/teachers/lessons/{id}': ['BACKEND_ONLY_DETAIL_DRILLDOWN', 'Teacher lesson detail endpoint exists; current content page edits from list/state via create/update flows.'],
    '/api/v1/teachers/lessons/{lessonId}/recorded-videos/{videoId}/stream': ['BACKEND_ONLY_MEDIA_STREAM', 'Protected teacher video stream endpoint is intended as media src/download target.'],
    '/api/v1/teachers/live-classes/{id}': ['BACKEND_ONLY_DETAIL_DRILLDOWN', 'Teacher live-class detail endpoint exists; current classes page uses list/update/cancel/enrolments.'],
    '/api/v1/teachers/live-classes/{id}/reschedule': ['BACKEND_ONLY_OPTIONAL_ACTION', 'Dedicated reschedule endpoint exists; current UI uses full PUT update for class changes.'],
    '/api/v1/teachers/live-classes/{id}/status': ['BACKEND_ONLY_OPTIONAL_ACTION', 'Dedicated status endpoint exists; current UI exposes cancel and full update paths.'],
    '/api/v1/teachers/modules/{id}': ['BACKEND_ONLY_DETAIL_DRILLDOWN', 'Teacher module detail endpoint exists; current content page uses module list/create/update/archive.'],
    '/api/v1/teachers/packages/{id}': ['BACKEND_ONLY_DETAIL_DRILLDOWN', 'Teacher package detail endpoint exists; current content page uses package list/create/update/archive/item actions.'],
    '/api/v1/payments/webhooks/stripe': ['INTERNAL_PROVIDER_CALLBACK', 'Stripe webhook endpoint is called by payment provider, not by frontend.'],
  }

  if (endpoint.startsWith('/api/v1/admin/activities/') && endpoint.endsWith('s')) {
    return {
      status: 'BACKEND_ONLY_TYPE_SPECIFIC_LIST',
      reason: 'Type-specific admin activity list endpoint exists; current admin UI uses the cross-type /admin/activities feed plus per-item detail/action endpoints.',
    }
  }

  const item = details[endpoint]
  if (item) return { status: item[0], reason: item[1] }
  return {
    status: 'UNCLASSIFIED_BACKEND_ONLY',
    reason: 'No direct frontend API client call found and no manual classification rule matched.',
  }
}
