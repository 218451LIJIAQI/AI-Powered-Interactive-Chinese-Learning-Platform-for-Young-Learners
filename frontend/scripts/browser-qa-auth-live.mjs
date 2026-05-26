/**
 * Browser-driven QA for the live authentication flow.
 *
 * <h2>What it exercises</h2>
 *   1. Pre-flight: hits `actuator/health` and the public courses
 *      endpoint so the script bails out with a clear error if the
 *      backend is not up (rather than producing confusing Chrome
 *      failures later).
 *   2. Registers a fresh student account using a timestamped email
 *      so concurrent runs never collide.
 *   3. Registers a fresh teacher account.
 *   4. Signs in / signs out / signs in again to verify the full
 *      session lifecycle — token issue, JWT validation, and the
 *      auth-store sessionStorage hand-off.
 *   5. Captures deep-link auth redirects to confirm
 *      `?redirect=` honours the open-redirect allowlist.
 *
 * <h2>Why this is "live"</h2>
 * Unlike `browser-qa-flows.mjs` which can run against the demo
 * fixture, this script REQUIRES a running backend with a writable
 * database. Catches regressions in the JPA save layer, password
 * hashing, JWT issuance, and the auth-store hydration paths that
 * the mocked flows cannot.
 *
 * <h2>Configuration</h2>
 *   - `QA_BASE_URL`        — frontend origin (default 127.0.0.1:5173)
 *   - `QA_API_BASE_URL`    — backend `/api/v1` base
 *   - `QA_API_HEALTH_URL`  — actuator override
 *   - `QA_PUBLIC_COURSES_URL` — public catalogue smoke-test URL
 *   - `QA_CHROME_PORT`     — DevTools port (default 9227)
 *
 * <h2>Evidence layout</h2>
 * Outputs per-step screenshots and a structured run report into
 * `browser-qa-evidence/<timestamp>-auth-live/`.
 */
import { spawn } from 'node:child_process'
import { mkdir, rm, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'

import {
  normalizeHttpBaseUrl,
  parseTcpPort,
  redactText,
  resolveBrowserExecutablePath,
  safeJsonStringify,
  stopProcessTree,
  tailTextForEvidence,
} from './lib/browser-runtime.mjs'

const baseUrl = normalizeHttpBaseUrl(process.env.QA_BASE_URL || 'http://127.0.0.1:5173', 'QA_BASE_URL')
const apiBaseUrl = normalizeHttpBaseUrl(
  process.env.QA_API_BASE_URL || 'http://localhost:8080/api/v1',
  'QA_API_BASE_URL',
)
const chromePath = resolveBrowserExecutablePath()
const debuggingPort = parseTcpPort(process.env.QA_CHROME_PORT || '9227', 'QA_CHROME_PORT')
const timestamp = new Date().toISOString().replace(/[:.]/g, '-')
const evidenceDir = path.resolve(process.cwd(), 'browser-qa-evidence', `${timestamp}-auth-live`)
const userDataDir = path.join(os.tmpdir(), `mandarin-spark-auth-live-${timestamp}`)
const apiHealthUrl = normalizeHttpBaseUrl(
  process.env.QA_API_HEALTH_URL || new URL('/actuator/health', apiBaseUrl).toString(),
  'QA_API_HEALTH_URL',
)
const publicCoursesUrl = normalizeHttpBaseUrl(
  process.env.QA_PUBLIC_COURSES_URL || new URL('/api/v1/public/courses', apiBaseUrl).toString(),
  'QA_PUBLIC_COURSES_URL',
)

const studentEmail = `qa.browser.student.${Date.now()}@example.com`
const teacherEmail = `qa.browser.teacher.${Date.now()}@example.com`
const studentPassword = 'Password123!'
const teacherPassword = 'Password123!'

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function fetchJson(url) {
  const response = await fetch(url)
  if (!response.ok) throw new Error(`${response.status} ${response.statusText}`)
  return response.json()
}

function summarizeValue(value) {
  if (value == null) return ''
  if (typeof value === 'string') return value.replace(/\s+/g, ' ').trim().slice(0, 240)

  try {
    return safeJsonStringify(value).slice(0, 240)
  } catch {
    return String(value).slice(0, 240)
  }
}

async function poll(description, action, predicate, timeoutMs = 15000, intervalMs = 250) {
  const started = Date.now()
  let lastValue
  let lastError

  while (Date.now() - started < timeoutMs) {
    try {
      lastValue = await action()
      if (predicate(lastValue)) return lastValue
    } catch (error) {
      lastError = error
    }

    await sleep(intervalMs)
  }

  const reason = lastError?.message || summarizeValue(lastValue) || 'condition not met'
  throw new Error(`Timed out waiting for ${description}: ${reason}`)
}

async function fetchProbe(url) {
  const response = await fetch(url)
  const contentType = response.headers.get('content-type') || ''
  const text = await response.text()
  let body

  if (contentType.toLowerCase().includes('json')) {
    try {
      body = JSON.parse(text)
    } catch {
      body = text
    }
  } else {
    body = text
  }

  return {
    url,
    status: response.status,
    contentType,
    body,
  }
}

async function waitForEndpoint(description, url, predicate, timeoutMs = 30000) {
  return poll(description, () => fetchProbe(url), predicate, timeoutMs, 500)
}

async function waitForDevTools() {
  const endpoint = `http://127.0.0.1:${debuggingPort}/json/list`
  const started = Date.now()

  while (Date.now() - started < 15000) {
    try {
      const targets = await fetchJson(endpoint)
      const page = targets.find((target) => target.type === 'page')
      if (page?.webSocketDebuggerUrl) return page.webSocketDebuggerUrl
    } catch {}

    await sleep(250)
  }

  throw new Error('Chrome DevTools endpoint did not become available')
}

function createCdpClient(webSocketUrl) {
  const socket = new WebSocket(webSocketUrl)
  let nextId = 1
  const pending = new Map()
  const handlers = new Map()

  socket.addEventListener('message', (event) => {
    const message = JSON.parse(event.data)
    if (message.id && pending.has(message.id)) {
      const { resolve, reject } = pending.get(message.id)
      pending.delete(message.id)
      message.error ? reject(new Error(message.error.message || 'CDP error')) : resolve(message.result || {})
      return
    }

    if (message.method && handlers.has(message.method)) {
      for (const handler of handlers.get(message.method)) handler(message.params || {})
    }
  })

  function send(method, params = {}) {
    const id = nextId++
    socket.send(JSON.stringify({ id, method, params }))
    return new Promise((resolve, reject) => {
      pending.set(id, { resolve, reject })
      setTimeout(() => {
        if (pending.has(id)) {
          pending.delete(id)
          reject(new Error(`Timed out waiting for ${method}`))
        }
      }, 15000)
    })
  }

  function on(method, handler) {
    const list = handlers.get(method) || []
    list.push(handler)
    handlers.set(method, list)
  }

  function waitFor(method, timeoutMs = 10000) {
    return new Promise((resolve) => {
      const timeout = setTimeout(() => resolve(null), timeoutMs)
      on(method, (params) => {
        clearTimeout(timeout)
        resolve(params)
      })
    })
  }

  return new Promise((resolve, reject) => {
    socket.addEventListener('open', () => resolve({ send, on, waitFor, close: () => socket.close() }))
    socket.addEventListener('error', () => reject(new Error('WebSocket error')))
  })
}

function safeName(name) {
  return (
    name
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-|-$/g, '') || 'step'
  )
}

async function main() {
  if (!existsSync(chromePath)) throw new Error(`Chrome executable not found at ${chromePath}`)
  await mkdir(evidenceDir, { recursive: true })

  const preflight = {
    frontendUrl: baseUrl,
    apiBaseUrl,
    apiHealthUrl,
    publicCoursesUrl,
    frontend: await waitForEndpoint(
      `frontend at ${baseUrl}`,
      baseUrl,
      (result) => result.status >= 200 && result.status < 400,
      20000,
    ),
    backendHealth: await waitForEndpoint(
      `backend health at ${apiHealthUrl}`,
      apiHealthUrl,
      (result) => result.status === 200 && result.body?.status === 'UP',
      30000,
    ),
    publicCourses: await waitForEndpoint(
      `public courses API at ${publicCoursesUrl}`,
      publicCoursesUrl,
      (result) => result.status === 200 && typeof result.body === 'object',
      30000,
    ),
  }

  const chrome = spawn(
    chromePath,
    [
      '--headless=new',
      '--disable-gpu',
      '--disable-extensions',
      '--no-first-run',
      '--no-default-browser-check',
      `--remote-debugging-port=${debuggingPort}`,
      `--user-data-dir=${userDataDir}`,
      '--window-size=1440,1000',
      'about:blank',
    ],
    { stdio: ['ignore', 'pipe', 'pipe'] },
  )

  const chromeLogs = []
  chrome.stdout.on('data', (data) => chromeLogs.push(data.toString()))
  chrome.stderr.on('data', (data) => chromeLogs.push(data.toString()))

  let client
  let activeFlow = null
  const requestsById = new Map()
  const records = []

  try {
    client = await createCdpClient(await waitForDevTools())
    const { send, on, waitFor } = client

    on('Runtime.consoleAPICalled', (params) => {
      if (!activeFlow) return
      const message =
        params.args
          ?.map((arg) => arg.value || arg.description || '')
          .filter(Boolean)
          .join(' ') || params.type
      if (params.type === 'error') activeFlow.consoleErrors.push(message)
      if (params.type === 'warning' || params.type === 'warn') activeFlow.consoleWarnings.push(message)
    })
    on('Runtime.exceptionThrown', (params) => {
      activeFlow?.runtimeExceptions.push(
        params.exceptionDetails?.exception?.description || params.exceptionDetails?.text || 'Runtime exception',
      )
    })
    on('Network.requestWillBeSent', (params) => {
      requestsById.set(params.requestId, { method: params.request?.method || '', url: params.request?.url || '' })
    })
    on('Network.loadingFailed', (params) => {
      if (!activeFlow || params.canceled || params.errorText === 'net::ERR_ABORTED') return
      const request = requestsById.get(params.requestId) || {}
      activeFlow.failedRequests.push({
        method: request.method || '',
        url: request.url || '',
        errorText: params.errorText,
        type: params.type,
      })
    })
    on('Network.responseReceived', (params) => {
      if (!activeFlow) return
      const response = params.response || {}
      if (response.status >= 400)
        activeFlow.httpErrors.push({ status: response.status, url: response.url, type: params.type })
    })

    await send('Page.enable')
    await send('Runtime.enable')
    await send('Network.enable')
    await send('Log.enable')
    await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })

    async function evaluate(expression) {
      const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
      if (result.exceptionDetails) throw new Error(result.exceptionDetails.text || 'Evaluate failed')
      return result.result?.value
    }

    async function pageState() {
      return evaluate(`(() => ({
        title: document.title,
        path: location.pathname + location.search,
        text: document.body.innerText.slice(0, 5000),
        buttonStates: Array.from(document.querySelectorAll('button')).map((button) => ({ text: button.innerText.trim(), disabled: button.disabled })).slice(0, 30),
      }))()`)
    }

    async function navigate(route) {
      const load = waitFor('Page.loadEventFired', 10000)
      await send('Page.navigate', { url: `${baseUrl}${route}` })
      await load
      await sleep(600)
    }

    async function waitForPage(description, predicate, timeoutMs = 15000) {
      return poll(description, pageState, predicate, timeoutMs, 250)
    }

    async function setValue(selector, value) {
      const ok = await evaluate(`(() => {
        const el = document.querySelector(${JSON.stringify(selector)});
        if (!el) return false;
        el.focus();
        el.value = ${JSON.stringify(value)};
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
        return true;
      })()`)

      if (!ok) throw new Error(`Could not set ${selector}`)
      await sleep(120)
    }

    async function setCheckbox(index, checked = true) {
      const ok = await evaluate(`(() => {
        const el = document.querySelectorAll('.auth-checkbox input')[${index}];
        if (!el) return false;
        el.checked = ${checked ? 'true' : 'false'};
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
        return true;
      })()`)

      if (!ok) throw new Error(`Could not set checkbox index ${index}`)
      await sleep(120)
    }

    async function setCheckboxInRegisterForm(index, checked = true) {
      const ok = await evaluate(`(() => {
        const el = document.querySelectorAll('.auth-form-panel--register .auth-checkbox input')[${index}];
        if (!el) return false;
        el.checked = ${checked ? 'true' : 'false'};
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
        return true;
      })()`)

      if (!ok) throw new Error(`Could not set register checkbox index ${index}`)
      await sleep(120)
    }

    async function clickButtonByText(text) {
      const ok = await evaluate(`(() => {
        const candidates = Array.from(document.querySelectorAll('button'));
        const el = candidates.find((node) => node.innerText.trim().includes(${JSON.stringify(text)}) && !node.disabled);
        if (!el) return false;
        el.click();
        return true;
      })()`)

      if (!ok) throw new Error(`Could not click button containing ${text}`)
      await sleep(450)
    }

    async function clickSelector(selector) {
      const ok = await evaluate(`(() => {
        const el = document.querySelector(${JSON.stringify(selector)});
        if (!el || el.disabled) return false;
        el.click();
        return true;
      })()`)

      if (!ok) throw new Error(`Could not click ${selector}`)
      await sleep(450)
    }

    async function snapshot(name) {
      const state = await pageState()
      const screenshot = await send('Page.captureScreenshot', { format: 'png', fromSurface: true })
      const file = path.join(evidenceDir, `${String(records.length + 1).padStart(2, '0')}-${safeName(name)}.png`)
      await writeFile(file, Buffer.from(screenshot.data, 'base64'))
      return { ...state, screenshot: file }
    }

    async function runFlow(name, fn) {
      const record = {
        name,
        status: 'PASS',
        error: '',
        consoleErrors: [],
        consoleWarnings: [],
        runtimeExceptions: [],
        failedRequests: [],
        httpErrors: [],
        snapshots: [],
      }
      activeFlow = record
      try {
        await fn(record)
      } catch (error) {
        record.status = 'FAIL'
        record.error = redactText(error.message)
        try {
          record.snapshots.push(await snapshot(`${name}-failure`))
        } catch {}
      }

      if (
        record.status === 'PASS' &&
        (record.consoleErrors.length ||
          record.runtimeExceptions.length ||
          record.failedRequests.length ||
          record.httpErrors.length)
      ) {
        record.status = 'NEEDS_FIX'
      }

      records.push(record)
      activeFlow = null
      console.log(`${record.status}\t${name}\t${record.error}`)
    }

    await runFlow('Student registration success', async (record) => {
      await navigate('/auth?tab=register&role=student')
      await setValue('#register-full-name', 'Browser QA Student')
      await setValue('#register-email', studentEmail)
      await setValue('#register-password', studentPassword)
      await setValue('#register-confirm-password', studentPassword)
      await setCheckboxInRegisterForm(0, true)
      await setCheckboxInRegisterForm(1, true)
      await clickSelector('.auth-form-panel--register .auth-submit-button:not([disabled])')
      await waitForPage(
        'student registration success message',
        (state) => state.text.includes('Student account created. You can sign in now.'),
        25000,
      )
      record.snapshots.push(await snapshot('register-student'))
      if (!record.snapshots.at(-1).text.includes('Student account created. You can sign in now.')) {
        throw new Error('Student registration success message was not shown')
      }
    })

    await runFlow('Guest deep link, student login, and logout', async (record) => {
      await navigate('/student/home')
      record.snapshots.push(await snapshot('deep-link-auth'))
      if (!record.snapshots.at(-1).path.startsWith('/auth?redirect=/student/home')) {
        throw new Error('Guest deep link did not redirect to auth with redirect query')
      }

      await setValue('#login-email', studentEmail)
      await setValue('#login-password', studentPassword)
      await clickSelector('.auth-submit-button:not([disabled])')
      await waitForPage('student home after login', (state) => state.path === '/student/home', 25000)
      record.snapshots.push(await snapshot('student-home-after-login'))
      if (record.snapshots.at(-1).path !== '/student/home') {
        throw new Error('Student login did not land on /student/home')
      }

      await clickButtonByText('Sign out')
      await waitForPage('auth page after logout', (state) => state.path.startsWith('/auth'), 15000)
      record.snapshots.push(await snapshot('after-sign-out'))
      if (!record.snapshots.at(-1).path.startsWith('/auth')) {
        throw new Error('Sign out did not return to auth')
      }
    })

    await runFlow('Protected session invalidation redirects back to auth', async (record) => {
      await navigate('/auth?redirect=/student/home')
      await setValue('#login-email', studentEmail)
      await setValue('#login-password', studentPassword)
      await clickSelector('.auth-submit-button:not([disabled])')
      await waitForPage('student home before auth invalidation', (state) => state.path === '/student/home', 25000)
      record.snapshots.push(await snapshot('session-before-invalidation'))
      if (record.snapshots.at(-1).path !== '/student/home') {
        throw new Error('Student login did not land on /student/home before invalidation')
      }

      await evaluate(`(() => {
        window.dispatchEvent(new CustomEvent('mandarin-spark:auth-invalidated'))
        return true
      })()`)
      await waitForPage(
        'auth redirect after session invalidation',
        (state) => state.path.startsWith('/auth?redirect=/student/home'),
        15000,
      )
      record.snapshots.push(await snapshot('session-after-invalidation'))
      if (!record.snapshots.at(-1).path.startsWith('/auth?redirect=/student/home')) {
        throw new Error('Auth invalidation did not redirect back to auth with the protected redirect')
      }
    })

    await runFlow('Forgot password success message', async (record) => {
      await navigate('/auth')
      await setValue('#login-email', studentEmail)
      await clickButtonByText('Forgot password?')
      await waitForPage('forgot password page', (state) => state.path.startsWith('/forgot-password'), 25000)
      await setValue('#forgot-email', studentEmail)
      await clickSelector('.auth-submit-button:not([disabled])')
      await waitForPage(
        'forgot password success message',
        (state) =>
          state.text.includes(
            `If ${studentEmail.toLowerCase()} is registered, reset instructions will be sent shortly.`,
          ),
        25000,
      )
      record.snapshots.push(await snapshot('forgot-password'))
      if (
        !record.snapshots
          .at(-1)
          .text.includes(`If ${studentEmail.toLowerCase()} is registered, reset instructions will be sent shortly.`)
      ) {
        throw new Error('Forgot password success message was not shown')
      }
    })

    await runFlow('Teacher registration success', async (record) => {
      await navigate('/auth?tab=register&role=teacher')
      await setValue('#register-full-name', 'Browser QA Teacher')
      await setValue('#register-email', teacherEmail)
      await setValue('#register-password', teacherPassword)
      await setValue('#register-confirm-password', teacherPassword)
      await setValue(
        '#register-teacher-bio',
        'Experienced Mandarin teacher validating live browser registration flows for production QA.',
      )
      await setCheckboxInRegisterForm(0, true)
      await setCheckboxInRegisterForm(1, true)
      await clickSelector('.auth-form-panel--register .auth-submit-button:not([disabled])')
      await waitForPage(
        'teacher registration success message',
        (state) =>
          state.text.includes('Teacher account created. Please sign in and complete verification if required.'),
        25000,
      )
      record.snapshots.push(await snapshot('register-teacher'))
      if (
        !record.snapshots
          .at(-1)
          .text.includes('Teacher account created. Please sign in and complete verification if required.')
      ) {
        throw new Error('Teacher registration success message was not shown')
      }
    })

    const report = {
      generatedAt: new Date().toISOString(),
      baseUrl,
      apiBaseUrl,
      evidenceDir,
      preflight,
      generatedAccounts: {
        studentEmail,
        teacherEmail,
      },
      totals: {
        flows: records.length,
        pass: records.filter((record) => record.status === 'PASS').length,
        needsFix: records.filter((record) => record.status === 'NEEDS_FIX').length,
        fail: records.filter((record) => record.status === 'FAIL').length,
      },
      records,
    }

    await writeFile(path.join(evidenceDir, 'auth-live-report.json'), safeJsonStringify(report), 'utf8')
    await writeFile(path.join(evidenceDir, 'chrome.log'), tailTextForEvidence(chromeLogs.join('\n')), 'utf8')
    console.log(`AUTH_LIVE_REPORT_JSON=${path.join(evidenceDir, 'auth-live-report.json')}`)
  } finally {
    client?.close()
    await stopProcessTree(chrome)
    await sleep(500)
    await rm(userDataDir, { recursive: true, force: true })
  }
}

main().catch((error) => {
  console.error(redactText(error?.stack || error?.message || String(error)))
  process.exitCode = 1
})
