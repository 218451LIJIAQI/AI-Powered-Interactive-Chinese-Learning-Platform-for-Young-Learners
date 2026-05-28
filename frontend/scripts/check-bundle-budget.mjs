import { gzipSync } from 'node:zlib'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const frontendRoot = path.resolve(scriptDirectory, '..')
const assetsDirectory = path.join(frontendRoot, 'dist', 'assets')

const KIB = 1024
const budgets = {
  entryJsGzip: 86 * KIB,
  mainCssGzip: 58 * KIB,
  routeChunkGzip: 17 * KIB,
}

function formatBytes(bytes) {
  return `${(bytes / KIB).toFixed(2)} KiB`
}

function readAssetFiles() {
  try {
    return readdirSync(assetsDirectory)
      .map((name) => {
        const fullPath = path.join(assetsDirectory, name)
        const stats = statSync(fullPath)
        return stats.isFile() ? { name, fullPath } : null
      })
      .filter(Boolean)
  } catch (error) {
    throw new Error('Build assets are missing. Run `npm --prefix frontend run build` first.', {
      cause: error,
    })
  }
}

function gzipSize(file) {
  return gzipSync(readFileSync(file.fullPath)).byteLength
}

function findSingleAsset(files, label, pattern) {
  const matches = files.filter((file) => pattern.test(file.name))
  if (matches.length !== 1) {
    throw new Error(`Expected exactly one ${label} asset, found ${matches.length}.`)
  }
  return matches[0]
}

function assertBudget(label, actual, limit) {
  if (actual > limit) {
    throw new Error(`${label} exceeds budget: ${formatBytes(actual)} > ${formatBytes(limit)}.`)
  }
  console.log(`${label}: ${formatBytes(actual)} / ${formatBytes(limit)}`)
}

const files = readAssetFiles()
const entryJs = findSingleAsset(files, 'entry JavaScript', /^index-[\w-]+\.js$/)
const mainCss = findSingleAsset(files, 'main CSS', /^index-[\w-]+\.css$/)
const routeChunks = files.filter((file) => /Page-[\w-]+\.js$/.test(file.name))

if (routeChunks.length === 0) {
  throw new Error('No route page chunks were found in the production build.')
}

const largestRouteChunk = routeChunks
  .map((file) => ({ file, gzipBytes: gzipSize(file) }))
  .sort((a, b) => b.gzipBytes - a.gzipBytes)[0]

assertBudget('Entry JavaScript gzip', gzipSize(entryJs), budgets.entryJsGzip)
assertBudget('Main CSS gzip', gzipSize(mainCss), budgets.mainCssGzip)
assertBudget(
  `Largest route chunk gzip (${largestRouteChunk.file.name})`,
  largestRouteChunk.gzipBytes,
  budgets.routeChunkGzip,
)
