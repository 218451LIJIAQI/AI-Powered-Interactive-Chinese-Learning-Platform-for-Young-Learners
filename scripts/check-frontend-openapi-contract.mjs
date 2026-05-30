import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const openApiPath = path.join(repositoryRoot, 'backend', 'target', 'openapi.json')
const servicesPath = path.join(repositoryRoot, 'frontend', 'src', 'api', 'services.ts')

const supportedMethods = new Set(['get', 'post', 'put', 'patch', 'delete'])

function fail(message) {
  console.error(message)
  process.exit(1)
}

function lineNumberAt(source, index) {
  return source.slice(0, index).split('\n').length
}

function normalisePath(value) {
  let pathValue = value.trim()
  pathValue = pathValue.replace(/\$\{[^}]+\}/g, '{}')
  pathValue = pathValue.replace(/^\/api\/v1(?=\/|$)/, '')
  pathValue = pathValue.replace(/\/+/g, '/')
  if (pathValue.length > 1) {
    pathValue = pathValue.replace(/\/$/, '')
  }
  return pathValue || '/'
}

function routeShape(value) {
  return normalisePath(value).replace(/\{[^/{}]+\}/g, '{}')
}

function signature(method, route) {
  return `${method.toUpperCase()} ${routeShape(route)}`
}

if (!existsSync(openApiPath)) {
  fail(
    `Missing ${path.relative(repositoryRoot, openApiPath)}. Run the backend quality gate before contract validation.`,
  )
}

const openApi = JSON.parse(readFileSync(openApiPath, 'utf8'))
const openApiSignatures = new Set()

for (const [route, operations] of Object.entries(openApi.paths ?? {})) {
  for (const method of Object.keys(operations ?? {})) {
    if (supportedMethods.has(method.toLowerCase())) {
      openApiSignatures.add(signature(method, route))
    }
  }
}

const servicesSource = readFileSync(servicesPath, 'utf8')
const frontendEndpoints = new Map()

function addEndpoint(method, route, index) {
  if (!route.startsWith('/')) {
    return
  }
  const endpointSignature = signature(method, route)
  if (!frontendEndpoints.has(endpointSignature)) {
    frontendEndpoints.set(endpointSignature, {
      method: method.toUpperCase(),
      route: normalisePath(route),
      line: lineNumberAt(servicesSource, index),
    })
  }
}

const httpCallPattern = /\bhttp\.(get|post|put|patch|delete)\(\s*(['"`])([\s\S]*?)\2/g
for (const match of servicesSource.matchAll(httpCallPattern)) {
  addEndpoint(match[1], match[3], match.index ?? 0)
}

const apiUrlPattern = /\bapiUrl\(\s*(['"`])([\s\S]*?)\1\s*\)/g
for (const match of servicesSource.matchAll(apiUrlPattern)) {
  const afterCall = servicesSource.slice(
    (match.index ?? 0) + match[0].length,
    (match.index ?? 0) + match[0].length + 400,
  )
  const methodMatch = afterCall.match(/\bmethod:\s*['"`]([A-Z]+)['"`]/)
  addEndpoint(methodMatch?.[1]?.toLowerCase() ?? 'get', match[2], match.index ?? 0)
}

const missing = [...frontendEndpoints.entries()]
  .filter(([endpointSignature]) => !openApiSignatures.has(endpointSignature))
  .map(([, endpoint]) => endpoint)
  .sort((a, b) => a.line - b.line)

if (missing.length > 0) {
  console.error('Frontend API wrappers reference routes that are not exported by backend OpenAPI:')
  for (const endpoint of missing) {
    console.error(
      `- ${path.relative(repositoryRoot, servicesPath)}:${endpoint.line} ${endpoint.method} ${endpoint.route}`,
    )
  }
  process.exit(1)
}

console.log(
  `Checked ${frontendEndpoints.size} frontend endpoint signatures against ${openApiSignatures.size} OpenAPI signatures.`,
)
