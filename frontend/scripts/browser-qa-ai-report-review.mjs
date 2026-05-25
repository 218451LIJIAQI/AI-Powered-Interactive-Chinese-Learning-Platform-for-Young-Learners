/**
 * Browser-driven QA for the admin AI-response-review workflow.
 *
 * <h2>What it exercises</h2>
 * Drives a real Chromium instance through the end-to-end happy path
 * of the AI moderation queue:
 *   1. Authenticates as the seeded admin account via the live REST API.
 *   2. Loads the admin moderation page and opens an existing
 *      {@code AiResponseReport} (id supplied via `QA_AI_REPORT_ID` or
 *      created on the fly).
 *   3. Records evidence — page screenshots, network log, console
 *      errors, runtime exceptions — into
 *      {@code browser-qa-evidence/<timestamp>-ai-report-review-ui/}.
 *   4. Writes a structured JSON report at the end so CI can fail fast
 *      on regressions.
 *
 * <h2>Configuration</h2>
 * All knobs default to local-dev ports and seeded credentials so the
 * script works out-of-the-box. Override via env when running against
 * a remote QA stack:
 *   - `QA_BASE_URL`        — frontend origin (default 127.0.0.1:5176)
 *   - `QA_API_BASE_URL`    — backend `/api/v1` base
 *   - `QA_ADMIN_EMAIL` / `QA_ADMIN_PASSWORD` — admin login
 *   - `QA_PASSWORD`        — fallback password for seeded fixtures
 *   - `QA_CHROME_PORT`     — DevTools debugging port (avoids clashes
 *                            when multiple QA scripts run in parallel)
 *   - `QA_AI_REPORT_ID`    — existing report to inspect; created on
 *                            the fly when omitted
 *
 * <h2>Lifecycle / isolation</h2>
 * Spawns Chrome with a unique `userDataDir` under the system temp
 * directory so concurrent QA runs cannot pollute each other's cookies
 * or storage. The directory is removed on exit.
 *
 * <h2>Why a custom runner</h2>
 * Puppeteer / Playwright would be heavier dependencies for what is
 * fundamentally a CDP-over-WebSocket script. Using the bundled
 * `resolveBrowserExecutablePath` from {@link ./lib/browser-runtime.mjs}
 * keeps the QA tree dependency-free.
 */
import { spawn } from 'node:child_process'
import { mkdir, rm, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'

import { resolveBrowserExecutablePath } from './lib/browser-runtime.mjs'

const baseUrl = process.env.QA_BASE_URL || 'http://127.0.0.1:5176'
const apiBaseUrl = process.env.QA_API_BASE_URL || 'http://127.0.0.1:8081/api/v1'
const adminEmail = process.env.QA_ADMIN_EMAIL || 'admin@example.com'
const adminPassword = process.env.QA_ADMIN_PASSWORD || 'SuperSecure123!'
const qaPassword = process.env.QA_PASSWORD || 'Password123!'
const runId = `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`
let reportId = Number(process.env.QA_AI_REPORT_ID || 0)
const chromePath = resolveBrowserExecutablePath()
const debuggingPort = Number(process.env.QA_CHROME_PORT || 9231)
const timestamp = new Date().toISOString().replace(/[:.]/g, '-')
const evidenceDir = path.resolve(process.cwd(), 'browser-qa-evidence', `${timestamp}-ai-report-review-ui`)
const userDataDir = path.join(os.tmpdir(), `mandarin-spark-ai-report-review-${timestamp}`)

const report = {
  generatedAt: new Date().toISOString(),
  baseUrl,
  apiBaseUrl,
  reportId,
  runId,
  evidenceDir,
  network: [],
  consoleErrors: [],
  runtimeExceptions: [],
  networkFailures: [],
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function fetchJson(url, options) {
  const response = await fetch(url, options)
  const json = await response.json()
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}: ${JSON.stringify(json)}`)
  }
  return json
}

async function createPendingAiReport() {
  const studentEmail = `qa.browser.ai.report.${runId}@example.com`
  await fetchJson(`${apiBaseUrl}/auth/register/student`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      fullName: `QA Browser AI Reporter ${runId}`,
      email: studentEmail,
      password: qaPassword,
      confirmPassword: qaPassword,
      preferredLanguage: 'en',
    }),
  })

  const studentLogin = await fetchJson(`${apiBaseUrl}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: studentEmail, password: qaPassword }),
  })

  const created = await fetchJson(`${apiBaseUrl}/ai/reports`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${studentLogin.data.token}`,
    },
    body: JSON.stringify({
      featureKey: 'CHAT_TUTOR',
      reasonCategory: 'HALLUCINATION',
      description: `Browser UI pending AI report ${runId}`,
      promptSnapshot: 'How do you say hello in Mandarin?',
      aiResponseSnapshot: `QA browser report response snapshot ${runId}`,
    }),
  })

  return {
    studentEmail,
    report: created.data,
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

async function waitForReviewedReport(reportId, token, timeoutMs = 25000) {
  const started = Date.now()
  let lastValue = null
  while (Date.now() - started < timeoutMs) {
    try {
      const fetched = await fetchJson(`${apiBaseUrl}/admin/ai-response-reports/${reportId}`, {
        headers: { Authorization: `Bearer ${token}` },
      })
      lastValue = fetched.data
      if (lastValue?.status && lastValue.status !== 'PENDING_REVIEW' && lastValue?.reviewedAt) {
        return lastValue
      }
    } catch (error) {
      lastValue = { error: error instanceof Error ? error.message : String(error) }
    }
    await sleep(250)
  }

  throw new Error(`Timed out waiting for resolved report: ${JSON.stringify(lastValue)}`)
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

  return new Promise((resolve, reject) => {
    socket.addEventListener('open', () => resolve({ send, on, close: () => socket.close() }))
    socket.addEventListener('error', () => reject(new Error('WebSocket error')))
  })
}

async function main() {
  if (!existsSync(chromePath)) throw new Error(`Chrome executable not found at ${chromePath}`)
  await mkdir(evidenceDir, { recursive: true })

  const adminLogin = await fetchJson(`${apiBaseUrl}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: adminEmail, password: adminPassword }),
  })
  const token = adminLogin.data.token

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
  let mainError = null
  try {
    if (!reportId) {
      const created = await createPendingAiReport()
      report.createdReport = created
      reportId = created.report.id
      report.reportId = reportId
    }

    client = await createCdpClient(await waitForDevTools())
    const { send, on } = client
    const requests = new Map()

    on('Runtime.consoleAPICalled', (params) => {
      if (params.type === 'error') {
        report.consoleErrors.push(params.args?.map((arg) => arg.value || arg.description || '').join(' '))
      }
    })
    on('Runtime.exceptionThrown', (params) => {
      report.runtimeExceptions.push(
        params.exceptionDetails?.exception?.description || params.exceptionDetails?.text || 'Runtime exception',
      )
    })
    on('Network.requestWillBeSent', (params) => {
      requests.set(params.requestId, {
        method: params.request?.method,
        url: params.request?.url,
        postData: params.request?.postData,
      })
    })
    on('Network.responseReceived', async (params) => {
      const request = requests.get(params.requestId) || {}
      if (params.response?.url?.includes('/api/v1/')) {
        report.network.push({
          method: request.method,
          url: params.response.url,
          status: params.response.status,
          requestBody: request.postData,
        })
      }
    })
    on('Network.loadingFailed', (params) => {
      const request = requests.get(params.requestId) || {}
      if (request.url?.includes('/api/v1/')) {
        report.networkFailures.push({
          method: request.method,
          url: request.url,
          errorText: params.errorText,
          blockedReason: params.blockedReason,
          canceled: params.canceled,
        })
      }
    })

    await send('Page.enable')
    await send('Runtime.enable')
    await send('Network.enable')
    await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })

    async function evaluate(expression) {
      const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
      if (result.exceptionDetails) throw new Error(result.exceptionDetails.text || 'Evaluate failed')
      return result.result?.value
    }

    async function waitFor(predicateSource, timeoutMs = 15000) {
      const started = Date.now()
      let lastValue
      while (Date.now() - started < timeoutMs) {
        lastValue = await evaluate(predicateSource)
        if (lastValue) return lastValue
        await sleep(250)
      }
      throw new Error(`Timed out waiting for browser condition: ${predicateSource}; last=${JSON.stringify(lastValue)}`)
    }

    async function submitReviewForm(selector) {
      const state = await evaluate(`
        (() => {
          const element = document.querySelector(${JSON.stringify(selector)});
          if (!element) return null;
          element.scrollIntoView({ block: 'center', inline: 'center' });
          const box = element.getBoundingClientRect();
          const state = {
            x: box.left + box.width / 2,
            y: box.top + box.height / 2,
            width: box.width,
            height: box.height,
            disabled: Boolean(element.disabled),
            text: element.innerText || element.value || '',
          };
          if (!state.disabled) {
            const form = element.closest('form');
            if (form && typeof form.requestSubmit === 'function') {
              form.requestSubmit(element);
            } else {
              element.click();
            }
          }
          return state;
        })()
      `)
      if (!state || state.disabled) {
        throw new Error(`Cannot submit ${selector}: ${JSON.stringify(state)}`)
      }
      return state
    }

    await send('Page.navigate', { url: `${baseUrl}/admin/moderation` })
    await waitFor('document.readyState === "complete"')
    await evaluate(`(() => {
      window.localStorage.removeItem('mandarin-spark.token');
      window.sessionStorage.setItem('mandarin-spark.token', ${JSON.stringify(token)});
    })()`)
    await send('Page.navigate', { url: `${baseUrl}/admin/moderation` })
    await waitFor('document.readyState === "complete"')
    await waitFor(
      `Array.from(document.querySelectorAll('option')).some((option) => option.value === '${reportId}')`,
      20000,
    )
    await evaluate(`
      (() => {
        const select = document.querySelector('#admin-moderation-report');
        if (!select) return false;
        select.value = '${reportId}';
        select.dispatchEvent(new Event('input', { bubbles: true }));
        select.dispatchEvent(new Event('change', { bubbles: true }));
        return true;
      })()
    `)
    await waitFor(
      `(() => {
      const select = document.querySelector('#admin-moderation-report');
      const detail = document.querySelector('.admin-report-detail')?.innerText || '';
      return select?.value === '${reportId}' && detail.includes(${JSON.stringify(runId)});
    })()`,
      15000,
    )

    const reportState = await evaluate(`(() => {
      const select = document.querySelector('#admin-moderation-report');
      const statusSelect = document.querySelector('#admin-moderation-status');
      const note = document.querySelector('#admin-moderation-reviewer-note');
      return {
        reportDisabled: Boolean(select?.disabled),
        statusDisabled: Boolean(statusSelect?.disabled),
        selectedReportText: document.querySelector('.admin-report-detail')?.innerText || '',
        noteValue: note?.value || '',
        bodyText: document.body.innerText,
      };
    })()`)

    report.selectedReportText = reportState.selectedReportText
    report.uiShowsTargetPendingReport = reportState.selectedReportText.includes(runId)
    report.uiShowsClosedNotice = reportState.bodyText.includes('This report is already closed.')

    if (!reportState.statusDisabled) {
      await evaluate(`
        (() => {
          const setValue = (selector, value) => {
            const element = document.querySelector(selector);
            element.value = value;
            element.dispatchEvent(new Event('input', { bubbles: true }));
            element.dispatchEvent(new Event('change', { bubbles: true }));
          };
          setValue('#admin-moderation-report', '${reportId}');
          setValue('#admin-moderation-status', 'RESOLVED');
          setValue('#admin-moderation-reviewer-note', 'Browser UI verified AI report review after notification enum migration.');
        })()
      `)
      await sleep(300)
      await waitFor(
        `(() => {
        const button = document.querySelector('.admin-moderation-page form button[type="submit"]');
        return button && !button.disabled && button.innerText.includes('Save review');
      })()`,
        5000,
      )
      report.submitButton = await submitReviewForm('.admin-moderation-page form button[type="submit"]')
      if (!report.submitButton || report.submitButton.disabled) {
        throw new Error(`Cannot submit AI report review: ${JSON.stringify(report.submitButton)}`)
      }
      const reviewedSnapshot = await waitForReviewedReport(reportId, token)
      await waitFor(
        `document.body.innerText.includes('AI report updated to Resolved and queue refreshed.')`,
        15000,
      ).catch(() => null)

      report.reviewedReport = {
        success: true,
        message: 'AI response report retrieved successfully.',
        data: reviewedSnapshot,
        errors: [],
      }
    } else {
      throw new Error(`Target AI report form was unexpectedly disabled before review: ${JSON.stringify(reportState)}`)
    }

    await sleep(1250)

    const screenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
    await writeFile(path.join(evidenceDir, 'admin-ai-report-review.png'), Buffer.from(screenshot.data, 'base64'))
    report.selectedReportText = await evaluate(
      `(() => document.querySelector('.admin-report-detail')?.innerText || '')()`,
    )
    report.finalBodyText = await evaluate('document.body.innerText')
    report.finalUrl = await evaluate('location.href')
    report.uiShowsResolved =
      report.selectedReportText.includes('Resolved') || report.selectedReportText.includes('Reviewed by')
    report.uiSuccess = report.finalBodyText.includes('AI report updated to Resolved and queue refreshed.')
    report.reviewRequest = report.network.find(
      (entry) => entry.method === 'POST' && entry.url.includes(`/admin/ai-response-reports/${reportId}/review`),
    )
    const reviewedData = report.reviewedReport?.data
    report.pass = Boolean(
      report.reviewRequest?.status === 200 &&
      reviewedData?.status &&
      reviewedData.status !== 'PENDING_REVIEW' &&
      reviewedData?.reviewedAt &&
      report.uiShowsTargetPendingReport &&
      (report.uiShowsResolved || report.finalBodyText.includes('Saved successfully')) &&
      report.uiSuccess &&
      report.consoleErrors.length === 0 &&
      report.runtimeExceptions.length === 0,
    )
  } catch (error) {
    mainError = error
    report.error = {
      message: error instanceof Error ? error.message : String(error),
      stack: error instanceof Error ? error.stack : undefined,
    }
  } finally {
    client?.close()
    chrome.kill('SIGKILL')
    await sleep(750)
    try {
      await rm(userDataDir, { recursive: true, force: true })
    } catch (error) {
      report.cleanupWarning = error instanceof Error ? error.message : String(error)
    }
    report.chromeLogs = chromeLogs.join('').slice(-4000)
    await writeFile(path.join(evidenceDir, 'ai-report-review-ui-report.json'), JSON.stringify(report, null, 2))
  }

  if (mainError) {
    throw mainError
  }

  if (!report.pass) {
    throw new Error(`AI report review UI verification failed: ${JSON.stringify(report, null, 2)}`)
  }
  console.log(
    JSON.stringify(
      {
        pass: report.pass,
        evidenceDir,
        reviewRequest: report.reviewRequest,
        consoleErrors: report.consoleErrors.length,
        runtimeExceptions: report.runtimeExceptions.length,
      },
      null,
      2,
    ),
  )
}

await main()
