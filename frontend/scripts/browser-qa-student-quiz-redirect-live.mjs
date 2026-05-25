/**
 * Browser-driven regression test for the student-quiz auth-redirect
 * round-trip against a LIVE backend.
 *
 * <h2>The bug class this guards against</h2>
 * Earlier versions of the SPA would lose the original
 * `/student/quiz/:id` deep link when the auth guard redirected
 * through /auth — after sign-in the user would land on the generic
 * student home instead of resuming the quiz they clicked. This
 * script:
 *   1. Boots a Vite preview server with `previewqa` mode so the
 *      build runs against the real backend, not the demo fixture.
 *   2. Registers a fresh student account.
 *   3. While LOGGED OUT, opens `/student/quiz` (deep link).
 *   4. Confirms the guard redirects to `/auth?redirect=...`.
 *   5. Signs in.
 *   6. Asserts the post-login route IS `/student/quiz`, not the
 *      default student home.
 *
 * <h2>Why this needs its own script</h2>
 * The general flow scripts cover happy paths; this one is a
 * dedicated regression locked to a specific historical bug so the
 * fix cannot rot. Kept small and focused on purpose.
 *
 * <h2>Configuration</h2>
 *   - `QA_FRONTEND_HOST` / `QA_FRONTEND_PORT` — Vite host/port
 *   - `QA_BASE_URL`     — override for the frontend origin
 *   - `QA_API_BASE_URL` / `VITE_API_BASE_URL` — backend base
 *   - `QA_CHROME_PORT`  — DevTools port (default 9231)
 */
import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'
import { mkdir, rm, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import os from 'node:os'
import path from 'node:path'

import { resolveBrowserExecutablePath, waitForHttpOk } from './lib/browser-runtime.mjs'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const frontendDir = path.resolve(scriptDir, '..')
const viteBin = path.resolve(frontendDir, 'node_modules', 'vite', 'bin', 'vite.js')
const host = process.env.QA_FRONTEND_HOST || '127.0.0.1'
const requestedFrontendPort = Number(process.env.QA_FRONTEND_PORT || '5173')
const configuredBaseUrl = process.env.QA_BASE_URL || ''
const apiBaseUrl = process.env.QA_API_BASE_URL || process.env.VITE_API_BASE_URL || 'http://127.0.0.1:8081/api/v1'
const chromePath = resolveBrowserExecutablePath()
const debuggingPort = Number(process.env.QA_CHROME_PORT || 9231)
const timestamp = new Date().toISOString().replace(/[:.]/g, '-')
const evidenceDir = path.resolve(process.cwd(), 'browser-qa-evidence', `${timestamp}-student-quiz-redirect-live`)
const userDataDir = path.join(os.tmpdir(), `mandarin-spark-student-quiz-live-${timestamp}`)
const studentEmail = `qa.quiz.redirect.${Date.now()}@example.com`
const studentPassword = 'Password123!'

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
    return JSON.stringify(value).slice(0, 240)
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

async function requestApi(pathname, options = {}) {
  const normalizedBase = apiBaseUrl.replace(/\/+$/, '')
  const normalizedPath = pathname.startsWith('/') ? pathname : `/${pathname}`
  const response = await fetch(`${normalizedBase}${normalizedPath}`, options)
  const text = await response.text()
  let body = null
  try {
    body = text ? JSON.parse(text) : null
  } catch {
    body = text
  }
  return { status: response.status, body }
}

async function canBindPort(hostname, port) {
  const net = await import('node:net')
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
    if (await canBindPort(hostname, candidate)) return candidate
  }
  throw new Error(`Could not find an available frontend port between ${port} and ${port + attempts - 1}.`)
}

async function resolveFrontend() {
  if (configuredBaseUrl) {
    await waitForHttpOk(configuredBaseUrl, 30000, 500)
    return { baseUrl: configuredBaseUrl, stop: async () => {} }
  }

  const expectedBaseUrl = `http://${host}:${requestedFrontendPort}`
  try {
    await waitForHttpOk(expectedBaseUrl, 2000, 500)
    return { baseUrl: expectedBaseUrl, stop: async () => {} }
  } catch {}

  if (!existsSync(viteBin)) {
    throw new Error(`Vite executable not found at ${viteBin}. Run \`cd frontend && npm install\` first.`)
  }

  const frontendPort = await resolveAvailablePort(host, requestedFrontendPort)
  const baseUrl = `http://${host}:${frontendPort}`
  const vite = spawn(process.execPath, [viteBin, '--host', host, '--port', String(frontendPort), '--strictPort'], {
    cwd: frontendDir,
    env: { ...process.env, VITE_API_BASE_URL: apiBaseUrl },
    stdio: ['ignore', 'pipe', 'pipe'],
  })
  vite.stdout.on('data', (data) => process.stdout.write(data.toString()))
  vite.stderr.on('data', (data) => process.stderr.write(data.toString()))
  await waitForHttpOk(baseUrl, 30000, 500)
  return {
    baseUrl,
    stop: async () => {
      if (vite.exitCode !== null || vite.killed) return
      if (process.platform === 'win32') {
        await new Promise((resolve) => {
          const killer = spawn('taskkill', ['/pid', String(vite.pid), '/t', '/f'], { stdio: 'ignore' })
          killer.on('exit', resolve)
          killer.on('error', resolve)
        })
      } else {
        vite.kill('SIGTERM')
      }
    },
  }
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

async function main() {
  if (!existsSync(chromePath)) throw new Error(`Chrome executable not found at ${chromePath}`)
  await mkdir(evidenceDir, { recursive: true })
  const frontend = await resolveFrontend()

  const registration = await requestApi('/auth/register/student', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      fullName: 'Quiz Redirect QA Student',
      email: studentEmail,
      password: studentPassword,
      confirmPassword: studentPassword,
      age: 9,
      parentEmail: `parent.${studentEmail}`,
      learningGoals: 'Validate student quiz redirect flow',
    }),
  })
  if (registration.status >= 400) {
    throw new Error(`Student registration failed with ${registration.status}`)
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
  const requestsById = new Map()
  const evidence = {
    generatedAt: new Date().toISOString(),
    baseUrl: frontend.baseUrl,
    apiBaseUrl,
    evidenceDir,
    studentEmail,
    registrationStatus: registration.status,
    consoleErrors: [],
    consoleWarnings: [],
    runtimeExceptions: [],
    failedRequests: [],
    httpErrors: [],
    apiResponses: [],
  }

  try {
    client = await createCdpClient(await waitForDevTools())
    const { send, on, waitFor } = client

    on('Runtime.consoleAPICalled', (params) => {
      const message =
        params.args
          ?.map((arg) => arg.value || arg.description || '')
          .filter(Boolean)
          .join(' ') || params.type
      if (params.type === 'error') evidence.consoleErrors.push(message)
      if (params.type === 'warning' || params.type === 'warn') evidence.consoleWarnings.push(message)
    })
    on('Runtime.exceptionThrown', (params) => {
      evidence.runtimeExceptions.push(
        params.exceptionDetails?.exception?.description || params.exceptionDetails?.text || 'Runtime exception',
      )
    })
    on('Network.requestWillBeSent', (params) => {
      requestsById.set(params.requestId, { method: params.request?.method || '', url: params.request?.url || '' })
    })
    on('Network.loadingFailed', (params) => {
      if (params.canceled || params.errorText === 'net::ERR_ABORTED') return
      const request = requestsById.get(params.requestId) || {}
      evidence.failedRequests.push({
        method: request.method || '',
        url: request.url || '',
        errorText: params.errorText,
        type: params.type,
      })
    })
    on('Network.responseReceived', (params) => {
      const response = params.response || {}
      if (response.url?.startsWith(apiBaseUrl)) {
        evidence.apiResponses.push({
          status: response.status,
          url: response.url,
          type: params.type,
          method: requestsById.get(params.requestId)?.method || '',
        })
      }
      if (response.status >= 400)
        evidence.httpErrors.push({ status: response.status, url: response.url, type: params.type })
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
        buttons: Array.from(document.querySelectorAll('button')).map((button) => ({ text: button.innerText.trim(), disabled: button.disabled })).slice(0, 40),
      }))()`)
    }

    async function navigate(route) {
      const load = waitFor('Page.loadEventFired', 10000)
      await send('Page.navigate', { url: `${frontend.baseUrl}${route}` })
      await load
      await sleep(600)
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

    await navigate('/auth?redirect=/student/quiz')
    evidence.authPageState = await poll(
      'auth login form',
      pageState,
      (state) =>
        state.path.startsWith('/auth') && state.text.includes('Email Address') && state.text.includes('Password'),
      15000,
      250,
    )
    await setValue('#login-email', studentEmail)
    await setValue('#login-password', studentPassword)
    await clickSelector('.auth-submit-button:not([disabled])')

    evidence.finalState = await poll(
      'student quiz page after redirect login',
      pageState,
      (state) =>
        state.path.startsWith('/student/quiz') &&
        state.title.includes('Quiz') &&
        (state.text.includes('Question') || state.text.includes('No quiz ready')),
      25000,
      250,
    )

    const screenshot = await send('Page.captureScreenshot', { format: 'png', fromSurface: true })
    evidence.screenshotPath = path.join(evidenceDir, 'student-quiz-after-login.png')
    await writeFile(evidence.screenshotPath, Buffer.from(screenshot.data, 'base64'))
    evidence.pass =
      evidence.consoleErrors.length === 0 &&
      evidence.runtimeExceptions.length === 0 &&
      evidence.failedRequests.length === 0 &&
      evidence.httpErrors.length === 0 &&
      evidence.apiResponses.some((response) => response.url.includes('/auth/login') && response.status === 200) &&
      evidence.apiResponses.some((response) => response.url.includes('/students/quizzes') && response.status === 200) &&
      evidence.finalState.path.startsWith('/student/quiz') &&
      (evidence.finalState.text.includes('Question') || evidence.finalState.text.includes('No quiz ready'))
  } finally {
    client?.close()
    chrome.kill()
    await sleep(500)
    await rm(userDataDir, { recursive: true, force: true })
    await frontend.stop()
    await writeFile(
      path.join(evidenceDir, 'student-quiz-redirect-live-report.json'),
      JSON.stringify(evidence, null, 2),
      'utf8',
    )
    await writeFile(path.join(evidenceDir, 'chrome.log'), chromeLogs.join('\n'), 'utf8')
  }

  console.log(
    `STUDENT_QUIZ_REDIRECT_LIVE_REPORT_JSON=${path.join(evidenceDir, 'student-quiz-redirect-live-report.json')}`,
  )
  if (!evidence.pass) {
    process.exitCode = 1
  }
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
