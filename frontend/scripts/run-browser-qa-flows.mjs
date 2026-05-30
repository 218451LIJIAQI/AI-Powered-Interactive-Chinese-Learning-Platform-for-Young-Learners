/**
 * Driver / harness for {@link browser-qa-flows.mjs}.
 *
 * <h2>What it does</h2>
 *   1. Probes the backend health endpoint across the common local
 *      ports (8080 / 8081) so the harness "just works" regardless
 *      of which the developer is running.
 *   2. Finds an available frontend port — starts at
 *      `QA_FRONTEND_PORT` (default 5174, matching local Vite) and
 *      walks forward up to 10 ports if the preferred port is busy.
 *   3. Spawns Vite in `previewqa` mode pointing at the resolved API
 *      base URL.
 *   4. Waits for the SPA to start serving `/auth` (signals
 *      readiness, not just port-open).
 *   5. Invokes `browser-qa-flows.mjs` as a child process with the
 *      right env vars.
 *   6. Cleans up: kills the Vite process tree (uses `taskkill /t`
 *      on Windows because Vite spawns child esbuild processes that
 *      a plain SIGTERM would not reach).
 *
 * <h2>Why a separate harness</h2>
 * Keeps the actual flow logic in a single, well-documented script
 * that does not have to know how Vite is started. Lets QA engineers
 * iterate on the flow steps without touching process plumbing.
 *
 * <h2>Configuration</h2>
 *   - `QA_FRONTEND_HOST` / `QA_FRONTEND_PORT` — preview server host/port
 *   - `QA_API_HEALTH_URL` — explicit health URL override
 *   - `QA_API_BASE_URL` / `VITE_API_BASE_URL` — backend base
 */
import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'
import { mkdir, writeFile } from 'node:fs/promises'
import net from 'node:net'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

import {
  normalizeHttpBaseUrl,
  parseTcpPort,
  redactText,
  stopProcessTree,
  tailTextForEvidence,
  waitForHttpOk,
} from './lib/browser-runtime.mjs'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const frontendDir = path.resolve(scriptDir, '..')
const viteBin = path.resolve(frontendDir, 'node_modules', 'vite', 'bin', 'vite.js')
const flowScript = path.resolve(scriptDir, 'browser-qa-flows.mjs')
const host = process.env.QA_FRONTEND_HOST || '127.0.0.1'
const requestedPort = parseTcpPort(process.env.QA_FRONTEND_PORT || '5174', 'QA_FRONTEND_PORT')
const configuredApiHealthUrl = process.env.QA_API_HEALTH_URL
  ? normalizeHttpBaseUrl(process.env.QA_API_HEALTH_URL, 'QA_API_HEALTH_URL')
  : ''
const defaultApiHealthUrls = [
  'http://localhost:8080/actuator/health',
  'http://127.0.0.1:8080/actuator/health',
  'http://localhost:8081/actuator/health',
  'http://127.0.0.1:8081/actuator/health',
]
const timestamp = new Date().toISOString().replace(/[:.]/g, '-')
const logDir = path.resolve(frontendDir, 'browser-qa-evidence', `${timestamp}-flows-runner`)
const viteLogPath = path.join(logDir, 'vite-previewqa.log')

function deriveApiBaseUrl(healthUrl) {
  try {
    const parsed = new URL(healthUrl)
    return `${parsed.origin}/api/v1`
  } catch {
    return 'http://localhost:8080/api/v1'
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function pipeToBuffer(stream, chunks, label) {
  stream.on('data', (data) => {
    const text = data.toString()
    chunks.push(text)
    process[label].write(text)
  })
}

async function canBindPort(hostname, port) {
  return new Promise((resolve) => {
    const server = net.createServer()
    server.unref()
    server.once('error', () => resolve(false))
    server.listen({ host: hostname, port }, () => {
      server.close(() => resolve(true))
    })
  })
}

async function resolveAvailablePort(hostname, port, attempts = 10) {
  for (let candidate = port; candidate < port + attempts; candidate += 1) {
    if (await canBindPort(hostname, candidate)) {
      return candidate
    }
  }

  throw new Error(`Could not find an available preview QA port between ${port} and ${port + attempts - 1}.`)
}

async function main() {
  if (!existsSync(viteBin)) {
    throw new Error(`Vite executable not found at ${viteBin}. Run \`cd frontend && npm install\` first.`)
  }
  await mkdir(logDir, { recursive: true })

  const apiHealthUrl = await resolveApiHealthUrl()
  const apiBaseUrl = normalizeHttpBaseUrl(
    process.env.VITE_API_BASE_URL || process.env.QA_API_BASE_URL || deriveApiBaseUrl(apiHealthUrl),
    'QA_API_BASE_URL',
  )

  process.stdout.write(`Waiting for backend health at ${apiHealthUrl}\n`)
  await waitForHttpOk(apiHealthUrl, 30000, 1000)
  process.stdout.write(`Using API base URL ${apiBaseUrl}\n`)

  const resolvedPort = await resolveAvailablePort(host, requestedPort)
  const baseUrl = `http://${host}:${resolvedPort}`
  const authUrl = new URL('/auth', baseUrl).toString()
  process.stdout.write(
    resolvedPort === requestedPort
      ? `Using preview QA port ${resolvedPort}\n`
      : `Preview QA port ${requestedPort} is busy. Using ${resolvedPort} instead.\n`,
  )

  const viteLogs = []
  const vite = spawn(
    process.execPath,
    [viteBin, '--mode', 'previewqa', '--host', host, '--port', String(resolvedPort), '--strictPort'],
    {
      cwd: frontendDir,
      env: {
        ...process.env,
        VITE_API_BASE_URL: apiBaseUrl,
      },
      stdio: ['ignore', 'pipe', 'pipe'],
    },
  )
  pipeToBuffer(vite.stdout, viteLogs, 'stdout')
  pipeToBuffer(vite.stderr, viteLogs, 'stderr')

  try {
    await waitForHttpOk(authUrl, 30000, 500)
    await sleep(300)

    const qaProcess = spawn(process.execPath, [flowScript], {
      cwd: frontendDir,
      env: {
        ...process.env,
        VITE_API_BASE_URL: apiBaseUrl,
        QA_BASE_URL: baseUrl,
        QA_FRONTEND_PROBE_URL: baseUrl,
      },
      stdio: 'inherit',
    })

    const qaExitCode = await new Promise((resolve, reject) => {
      qaProcess.on('error', reject)
      qaProcess.on('exit', (code) => resolve(code ?? 1))
    })

    if (qaExitCode !== 0) {
      throw new Error(`browser-qa-flows exited with code ${qaExitCode}`)
    }
  } finally {
    await stopProcessTree(vite)
    await sleep(1000)
    await writeFile(viteLogPath, tailTextForEvidence(viteLogs.join(''), 100000), 'utf8')
    const latestRunnerMarker = path.join(logDir, 'runner-summary.txt')
    await writeFile(
      latestRunnerMarker,
      `baseUrl=${baseUrl}\nauthUrl=${authUrl}\napiHealthUrl=${apiHealthUrl}\nviteLog=${viteLogPath}\n`,
      'utf8',
    )
  }
}

async function resolveApiHealthUrl() {
  if (configuredApiHealthUrl) {
    return configuredApiHealthUrl
  }

  for (const candidate of defaultApiHealthUrls) {
    try {
      await waitForHttpOk(candidate, 3000, 500)
      return candidate
    } catch {
      // Try the next common local backend port before giving the runner's
      // normal, longer wait a chance to report a clear failure.
    }
  }

  return defaultApiHealthUrls[0]
}

main().catch((error) => {
  console.error(redactText(error?.stack || error?.message || String(error)))
  process.exitCode = 1
})
