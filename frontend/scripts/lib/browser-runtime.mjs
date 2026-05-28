/**
 * Shared runtime helpers for the `browser-qa-*` scripts.
 *
 * <p>Centralises two concerns that every QA script needs:
 *   1. Locating an installed Chromium-compatible browser without
 *      asking the developer to install Puppeteer / Playwright.
 *   2. Polling an HTTP endpoint until it returns 200 so the
 *      scripts can synchronise with backend / Vite startup.</p>
 *
 * <h2>Why no `puppeteer-core` dependency</h2>
 * The browser-QA tree is intentionally dependency-free so a clean
 * `npm install` of the frontend does not pull in a ~300 MB Chromium
 * download on every developer machine. Each QA script speaks the
 * Chrome DevTools Protocol over WebSocket directly against a
 * locally-installed browser.
 *
 * <h2>Search order for the browser binary</h2>
 * Explicit env vars beat OS defaults so CI / Docker can override:
 *   1. {@code CHROME_PATH} (de-facto convention, used by many tools)
 *   2. {@code QA_CHROME_PATH} (our own override, matches `QA_*` env
 *      prefix used by the rest of the QA scripts)
 *   3. Per-platform default install locations for Chrome, Chrome
 *      Beta, Chromium, Microsoft Edge, Brave.
 *
 * The function throws a clear, actionable error listing the
 * candidate paths it tried when no browser is found, rather than
 * letting `spawn` fail with a cryptic ENOENT.
 */
import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'
import { rm } from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import process from 'node:process'

const SENSITIVE_KEY_PATTERN =
  /(?:authorization|password|passphrase|secret|token|jwt|cookie|session|credential|api[_-]?key|reset|refresh)/i
const SENSITIVE_QUERY_KEY_PATTERN =
  /^(?:authorization|password|passphrase|secret|token|access_token|refresh_token|id_token|jwt|session|code|api[_-]?key|key)$/i
const SECRET_LIKE_VALUE_PATTERN = /^[A-Za-z0-9._~+/=-]{32,}$/

const windowsProgramFiles = [
  process.env['ProgramFiles'],
  process.env['ProgramFiles(x86)'],
  process.env.LOCALAPPDATA,
].filter(Boolean)

function unique(values) {
  return [...new Set(values.filter(Boolean))]
}

function browserCandidates() {
  if (process.platform === 'win32') {
    return unique([
      process.env.CHROME_PATH,
      process.env.QA_CHROME_PATH,
      ...windowsProgramFiles.flatMap((root) => [
        path.join(root, 'Google', 'Chrome', 'Application', 'chrome.exe'),
        path.join(root, 'Google', 'Chrome Beta', 'Application', 'chrome.exe'),
        path.join(root, 'Chromium', 'Application', 'chrome.exe'),
        path.join(root, 'Microsoft', 'Edge', 'Application', 'msedge.exe'),
        path.join(root, 'BraveSoftware', 'Brave-Browser', 'Application', 'brave.exe'),
      ]),
    ])
  }

  if (process.platform === 'darwin') {
    return unique([
      process.env.CHROME_PATH,
      process.env.QA_CHROME_PATH,
      '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
      '/Applications/Google Chrome Beta.app/Contents/MacOS/Google Chrome Beta',
      '/Applications/Chromium.app/Contents/MacOS/Chromium',
      '/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',
      '/Applications/Brave Browser.app/Contents/MacOS/Brave Browser',
      path.join(os.homedir(), 'Applications', 'Google Chrome.app', 'Contents', 'MacOS', 'Google Chrome'),
    ])
  }

  return unique([
    process.env.CHROME_PATH,
    process.env.QA_CHROME_PATH,
    '/usr/bin/google-chrome',
    '/usr/bin/google-chrome-stable',
    '/usr/bin/chromium',
    '/usr/bin/chromium-browser',
    '/snap/bin/chromium',
    '/usr/bin/microsoft-edge',
    '/usr/bin/brave-browser',
  ])
}

export function resolveBrowserExecutablePath() {
  const candidates = browserCandidates()
  const match = candidates.find((candidate) => existsSync(candidate))

  if (!match) {
    const formattedCandidates = candidates.map((candidate) => `- ${candidate}`).join('\n')
    throw new Error(
      `No Chromium-compatible browser executable was found. Set CHROME_PATH to an installed browser, or install one of:\n${formattedCandidates}`,
    )
  }

  return match
}

export function parseTcpPort(value, label = 'port') {
  const parsed = Number(value)
  if (!Number.isInteger(parsed) || parsed < 1 || parsed > 65535) {
    throw new Error(`${label} must be an integer between 1 and 65535. Received: ${value}`)
  }
  return parsed
}

export function normalizeHttpBaseUrl(value, label = 'URL') {
  const raw = String(value || '').trim()
  if (!raw) {
    throw new Error(`${label} is required.`)
  }

  let parsed
  try {
    parsed = new URL(raw)
  } catch {
    throw new Error(`${label} must be an absolute HTTP(S) URL. Received: ${raw}`)
  }

  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw new Error(`${label} must use http or https. Received protocol: ${parsed.protocol}`)
  }
  if (parsed.username || parsed.password) {
    throw new Error(`${label} must not include embedded credentials.`)
  }
  if (parsed.search || parsed.hash) {
    throw new Error(`${label} must not include a query string or fragment.`)
  }

  return parsed.toString().replace(/\/+$/, '')
}

export function sanitizeUrlForEvidence(value) {
  if (value == null) return value
  const raw = String(value)
  try {
    const parsed = raw.startsWith('/') ? new URL(raw, 'http://qa.local') : new URL(raw)
    if (parsed.username) parsed.username = 'redacted'
    if (parsed.password) parsed.password = 'redacted'

    for (const key of [...parsed.searchParams.keys()]) {
      if (SENSITIVE_QUERY_KEY_PATTERN.test(key)) {
        parsed.searchParams.set(key, '[redacted]')
      }
    }
    if (parsed.hash) {
      parsed.hash = '[redacted-fragment]'
    }

    const sanitized = parsed.toString()
    if (raw.startsWith('/')) {
      return `${parsed.pathname}${parsed.search}${parsed.hash}`
    }
    return sanitized
  } catch {
    return redactText(raw)
  }
}

export function redactText(value) {
  if (value == null) return value
  let text = String(value)

  text = text.replace(/\bBearer\s+[A-Za-z0-9._~+/=-]+/gi, 'Bearer [redacted]')
  text = text.replace(/\bBasic\s+[A-Za-z0-9+/=]+/gi, 'Basic [redacted]')
  text = text.replace(
    /(["']?(?:authorization|password|passphrase|secret|token|jwt|cookie|session|credential|api[_-]?key|reset|refresh)["']?\s*:\s*)(["'])(.*?)(\2)/gi,
    '$1$2[redacted]$4',
  )
  text = text.replace(
    /\b(authorization|password|passphrase|secret|token|jwt|cookie|session|credential|api[_-]?key|reset|refresh)\s*=\s*(['"]?)[^'"\s,;&)]+/gi,
    '$1=$2[redacted]',
  )

  if (SECRET_LIKE_VALUE_PATTERN.test(text) && !/^https?:\/\//i.test(text)) {
    return `${text.slice(0, 8)}...[redacted]`
  }

  return text
}

export function sanitizeForEvidence(value, depth = 0) {
  if (value == null) return value
  if (typeof value === 'string') {
    if (/^(?:https?:)?\/\//i.test(value) || value.startsWith('/')) {
      return sanitizeUrlForEvidence(value)
    }
    return redactText(value)
  }
  if (typeof value === 'number' || typeof value === 'boolean') return value
  if (typeof value === 'bigint') return value.toString()
  if (value instanceof Date) return value.toISOString()
  if (depth > 12) return '[max-depth]'
  if (Array.isArray(value)) return value.map((entry) => sanitizeForEvidence(entry, depth + 1))

  if (typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([key, entry]) => [
        key,
        SENSITIVE_KEY_PATTERN.test(key) ? '[redacted]' : sanitizeForEvidence(entry, depth + 1),
      ]),
    )
  }

  return String(value)
}

export function safeJsonStringify(value, space = 2) {
  return JSON.stringify(sanitizeForEvidence(value), null, space)
}

export function tailTextForEvidence(value, maxLength = 4000) {
  const text = redactText(value || '')
  return text.length > maxLength ? text.slice(-maxLength) : text
}

export async function stopProcessTree(processHandle, timeoutMs = 5000) {
  if (!processHandle || processHandle.killed || processHandle.exitCode !== null) return

  if (process.platform === 'win32') {
    await new Promise((resolve) => {
      const killer = spawn('taskkill', ['/pid', String(processHandle.pid), '/t', '/f'], { stdio: 'ignore' })
      const timeout = setTimeout(resolve, timeoutMs)
      killer.on('error', () => {
        clearTimeout(timeout)
        resolve()
      })
      killer.on('exit', () => {
        clearTimeout(timeout)
        resolve()
      })
    })
    return
  }

  processHandle.kill('SIGTERM')
  await new Promise((resolve) => {
    const timeout = setTimeout(() => {
      processHandle.kill('SIGKILL')
      resolve()
    }, timeoutMs)
    processHandle.once('exit', () => {
      clearTimeout(timeout)
      resolve()
    })
  })
}

export async function cleanupUserDataDir(userDataDir, retries = 5, retryDelayMs = 250) {
  if (!userDataDir) return ''

  try {
    await rm(userDataDir, {
      recursive: true,
      force: true,
      maxRetries: retries,
      retryDelay: retryDelayMs,
    })
    return ''
  } catch (error) {
    return redactText(error instanceof Error ? error.message : String(error))
  }
}

export async function waitForHttpOk(url, timeoutMs = 30000, intervalMs = 500) {
  const started = Date.now()
  let lastError

  while (Date.now() - started < timeoutMs) {
    try {
      const response = await fetch(url)
      if (response.ok) {
        return response
      }
      lastError = new Error(`${response.status} ${response.statusText}`)
    } catch (error) {
      lastError = error
    }

    await new Promise((resolve) => setTimeout(resolve, intervalMs))
  }

  throw new Error(`Timed out waiting for ${url}: ${lastError?.message || 'request failed'}`)
}
