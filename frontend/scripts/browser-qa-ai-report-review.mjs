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

  const chrome = spawn(chromePath, [
    '--headless=new',
    '--disable-gpu',
    '--disable-extensions',
    '--no-first-run',
    '--no-default-browser-check',
    `--remote-debugging-port=${debuggingPort}`,
    `--user-data-dir=${userDataDir}`,
    '--window-size=1440,1000',
    'about:blank',
  ], { stdio: ['ignore', 'pipe', 'pipe'] })
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
      report.runtimeExceptions.push(params.exceptionDetails?.exception?.description || params.exceptionDetails?.text || 'Runtime exception')
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

    await send('Page.navigate', { url: `${baseUrl}/admin/moderation` })
    await waitFor('document.readyState === "complete"')
    await evaluate(`(() => {
      window.localStorage.removeItem('mandarin-spark.token');
      window.sessionStorage.setItem('mandarin-spark.token', ${JSON.stringify(token)});
    })()`)
    await send('Page.navigate', { url: `${baseUrl}/admin/moderation` })
    await waitFor('document.readyState === "complete"')
    await waitFor(`Array.from(document.querySelectorAll('option')).some((option) => option.value === '${reportId}')`, 20000)

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
    await evaluate(`
      (() => {
        const buttons = Array.from(document.querySelectorAll('button'));
        const button = buttons.find((item) => item.textContent && item.textContent.includes('Save review'));
        button.click();
      })()
    `)
    await waitFor(`
      (() => {
        const text = document.body.innerText;
        return text.includes('AI report updated to Resolved and queue refreshed.')
          || (text.includes('This report is already closed.') && text.includes('Resolved'));
      })()
    `, 20000)
    await sleep(500)

    const screenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
    await writeFile(path.join(evidenceDir, 'admin-ai-report-review.png'), Buffer.from(screenshot.data, 'base64'))
    report.finalBodyText = await evaluate('document.body.innerText')
    report.finalUrl = await evaluate('location.href')
    report.uiSuccess = report.finalBodyText.includes('AI report updated to Resolved and queue refreshed.')
    report.uiShowsResolved = report.finalBodyText.includes('Resolved')
    report.uiShowsClosedNotice = report.finalBodyText.includes('This report is already closed.')
    report.reviewRequest = report.network.find((entry) =>
      entry.method === 'POST' && entry.url.includes(`/admin/ai-response-reports/${reportId}/review`),
    )
    report.reviewedReport = await fetchJson(`${apiBaseUrl}/admin/ai-response-reports/${reportId}`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    const reviewedData = report.reviewedReport.data
    report.pass = Boolean(
      report.reviewRequest?.status === 200
        && reviewedData?.status === 'RESOLVED'
        && reviewedData?.reviewedAt
        && report.uiShowsResolved
        && report.consoleErrors.length === 0
        && report.runtimeExceptions.length === 0,
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
  console.log(JSON.stringify({
    pass: report.pass,
    evidenceDir,
    reviewRequest: report.reviewRequest,
    consoleErrors: report.consoleErrors.length,
    runtimeExceptions: report.runtimeExceptions.length,
  }, null, 2))
}

await main()
