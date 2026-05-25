/**
 * Browser-driven smoke scan across every documented route.
 *
 * <h2>Purpose</h2>
 * Fastest QA pass in the tree — for every route in the canonical
 * list it just confirms the page loads, captures a screenshot, and
 * records any console / network errors. No deep interaction, no
 * viewport matrix.
 *
 * <h2>When to use this over deep-audit</h2>
 *   - Pre-commit / local smoke check.
 *   - PR-time CI step (catch outright broken routes in &lt; 1 minute).
 *   - First triage before running the heavier
 *     {@link browser-qa-deep-audit.mjs} which is 3x as slow because
 *     it tests three viewports per route.
 *
 * <h2>Evidence layout</h2>
 * `browser-qa-evidence/<timestamp>/` — note the timestamp is the
 * folder name directly (unlike deep-audit which suffixes with
 * `-deep-audit`) so the latest run is easy to spot in `ls`.
 *
 * <h2>Configuration</h2>
 *   - `QA_BASE_URL`           — frontend origin
 *   - `QA_FRONTEND_PROBE_URL` — separate probe origin if needed
 *   - `QA_CHROME_PORT`        — DevTools port (default 9223)
 */
import { spawn } from 'node:child_process'
import { mkdir, writeFile, rm } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'

import { resolveBrowserExecutablePath } from './lib/browser-runtime.mjs'

const baseUrl = process.env.QA_BASE_URL || 'http://127.0.0.1:5173'
const frontendProbeUrl = process.env.QA_FRONTEND_PROBE_URL || baseUrl
const chromePath = resolveBrowserExecutablePath()
const debuggingPort = Number(process.env.QA_CHROME_PORT || 9223)
const timestamp = new Date().toISOString().replace(/[:.]/g, '-')
const evidenceDir = path.resolve(process.cwd(), 'browser-qa-evidence', timestamp)
const userDataDir = path.join(os.tmpdir(), `mandarin-spark-qa-${timestamp}`)

const routes = [
  '/',
  '/auth',
  '/reset-password?token=qa-demo-token',
  '/terms',
  '/privacy',
  '/payment/success',
  '/payment/cancel',
  '/courses',
  '/courses/PACKAGE-1',
  '/courses/LESSON-2',
  '/student/home',
  '/student/learn',
  '/student/practice',
  '/student/quiz',
  '/student/classes',
  '/student/achievements',
  '/student/rewards',
  '/student/ai-helper',
  '/student/profile',
  '/teacher/dashboard',
  '/teacher/content',
  '/teacher/classes',
  '/teacher/students',
  '/teacher/feedback',
  '/teacher/profile',
  '/teacher/earnings',
  '/admin/dashboard',
  '/admin/users',
  '/admin/teacher-verification',
  '/admin/content',
  '/admin/activities',
  '/admin/moderation',
  '/admin/finance',
  '/admin/analytics',
  '/admin/settings',
  '/unauthorized',
  '/not-a-real-route-for-browser-qa',
]

const viewports = [
  { name: 'desktop', width: 1440, height: 1000, mobile: false },
  { name: 'tablet', width: 1024, height: 900, mobile: false },
  { name: 'mobile', width: 430, height: 920, mobile: true },
]

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function fetchJson(url) {
  const response = await fetch(url)
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText} for ${url}`)
  }
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

async function waitForEndpoint(description, url, predicate, timeoutMs = 20000) {
  return poll(description, () => fetchProbe(url), predicate, timeoutMs, 500)
}

async function waitForDevTools() {
  const endpoint = `http://127.0.0.1:${debuggingPort}/json/list`
  const started = Date.now()
  let lastError

  while (Date.now() - started < 15000) {
    try {
      const targets = await fetchJson(endpoint)
      const page = targets.find((target) => target.type === 'page')
      if (page?.webSocketDebuggerUrl) {
        return page.webSocketDebuggerUrl
      }
    } catch (error) {
      lastError = error
    }
    await sleep(250)
  }

  throw lastError ?? new Error('Chrome DevTools endpoint did not become available')
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
      if (message.error) {
        reject(new Error(`${message.error.message || 'CDP error'} ${JSON.stringify(message.error.data || '')}`))
      } else {
        resolve(message.result || {})
      }
      return
    }

    if (message.method && handlers.has(message.method)) {
      for (const handler of handlers.get(message.method)) {
        handler(message.params || {})
      }
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
    socket.addEventListener('error', (event) => reject(event.error || new Error('WebSocket error')))
  })
}

function routeSlug(route) {
  const cleaned = route.replace(/[/?=&:#]+/g, '-').replace(/^-|-$/g, '')
  return cleaned || 'root'
}

function summarizeEventText(value) {
  if (value == null) {
    return ''
  }
  if (typeof value === 'string') {
    return value
  }
  if (typeof value === 'object' && 'description' in value) {
    return value.description || ''
  }
  return String(value)
}

function getCaseStatus(record) {
  if (record.navigationError || record.pageError) {
    return 'FAIL'
  }
  if (!record.ui?.rendered) {
    return 'FAIL'
  }
  if (
    record.runtimeExceptions.length ||
    record.consoleErrors.length ||
    record.appHttpErrors.length ||
    record.failedRequests.length
  ) {
    return 'NEEDS_FIX'
  }
  if (record.ui?.horizontalOverflow || record.ui?.suspiciousText?.length) {
    return 'NEEDS_REVIEW'
  }
  return 'PASS'
}

function previewRoleForRoute(route) {
  if (route.startsWith('/student')) return 'STUDENT'
  if (route.startsWith('/teacher')) return 'TEACHER'
  if (route.startsWith('/admin')) return 'ADMIN'
  return null
}

async function main() {
  if (!existsSync(chromePath)) {
    throw new Error(`Chrome executable not found at ${chromePath}`)
  }

  const preflight = {
    frontendProbeUrl,
    frontend: await waitForEndpoint(
      `frontend at ${frontendProbeUrl}`,
      frontendProbeUrl,
      (result) => result.status >= 200 && result.status < 400,
      20000,
    ),
  }

  await mkdir(evidenceDir, { recursive: true })

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

  try {
    const webSocketUrl = await waitForDevTools()
    client = await createCdpClient(webSocketUrl)

    const { send, on, waitFor } = client
    let activeRecord = null

    on('Runtime.exceptionThrown', (params) => {
      activeRecord?.runtimeExceptions.push({
        text: params.exceptionDetails?.text || 'Runtime exception',
        description: params.exceptionDetails?.exception?.description || '',
        url: params.exceptionDetails?.url || '',
        lineNumber: params.exceptionDetails?.lineNumber,
      })
    })

    on('Runtime.consoleAPICalled', (params) => {
      const message = params.args?.map(summarizeEventText).filter(Boolean).join(' ') || params.type
      if (params.type === 'error') {
        activeRecord?.consoleErrors.push(message)
      }
      if (params.type === 'warning' || params.type === 'warn') {
        activeRecord?.consoleWarnings.push(message)
      }
    })

    on('Log.entryAdded', (params) => {
      const entry = params.entry || {}
      if (entry.level === 'error') {
        activeRecord?.consoleErrors.push(entry.text || 'Log error')
      }
      if (entry.level === 'warning') {
        activeRecord?.consoleWarnings.push(entry.text || 'Log warning')
      }
    })

    const records = []
    const requestsById = new Map()

    on('Network.requestWillBeSent', (params) => {
      requestsById.set(params.requestId, {
        method: params.request?.method || '',
        url: params.request?.url || '',
      })
    })

    on('Network.loadingFailed', (params) => {
      if (params.canceled || params.errorText === 'net::ERR_ABORTED') {
        return
      }
      const request = requestsById.get(params.requestId) || {}
      activeRecord?.failedRequests.push({
        requestId: params.requestId,
        method: request.method || '',
        url: request.url || '',
        type: params.type,
        errorText: params.errorText,
      })
    })

    on('Network.responseReceived', (params) => {
      const response = params.response || {}
      if (response.status >= 400) {
        const item = {
          status: response.status,
          type: params.type,
          url: response.url,
        }
        activeRecord?.httpErrors.push(item)
        if (response.url.startsWith(baseUrl) || response.url.includes('/api/')) {
          activeRecord?.appHttpErrors.push(item)
        }
      }
    })

    await send('Page.enable')
    await send('Runtime.enable')
    await send('Network.enable')
    await send('Log.enable')

    async function evaluatePageUi() {
      const evaluated = await send('Runtime.evaluate', {
        awaitPromise: true,
        returnByValue: true,
        expression: `(() => {
          const text = document.body?.innerText || '';
          const viewportWidth = window.innerWidth;
          const viewportHeight = window.innerHeight;
          const doc = document.documentElement;
          const body = document.body;
          const scrollWidth = Math.max(doc.scrollWidth, body?.scrollWidth || 0);
          const scrollHeight = Math.max(doc.scrollHeight, body?.scrollHeight || 0);
          const selectors = ['button', 'a[href]', 'input', 'select', 'textarea', '[role="button"]'];
          const visible = (el) => {
            const rect = el.getBoundingClientRect();
            const style = window.getComputedStyle(el);
            return rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none';
          };
          const overflowElements = Array.from(document.body.querySelectorAll('*'))
            .filter((el) => {
              const rect = el.getBoundingClientRect();
              const style = window.getComputedStyle(el);
              if (!visible(el) || style.position === 'fixed') return false;
              return rect.right > viewportWidth + 2 || rect.left < -2;
            })
            .slice(0, 10)
            .map((el) => {
              const rect = el.getBoundingClientRect();
              return {
                tag: el.tagName.toLowerCase(),
                className: String(el.className || '').slice(0, 120),
                text: String(el.innerText || el.getAttribute('aria-label') || '').trim().slice(0, 120),
                left: Math.round(rect.left),
                right: Math.round(rect.right),
                width: Math.round(rect.width),
              };
            });
          const blankControls = Array.from(document.querySelectorAll(selectors.join(',')))
            .filter(visible)
            .filter((el) => {
              const label = (el.innerText || el.getAttribute('aria-label') || el.getAttribute('title') || el.getAttribute('placeholder') || '').trim();
              return !label && el.tagName.toLowerCase() !== 'input' && el.tagName.toLowerCase() !== 'textarea';
            })
            .slice(0, 10)
            .map((el) => ({ tag: el.tagName.toLowerCase(), className: String(el.className || '').slice(0, 120) }));
          const suspiciousText = ['undefined', 'NaN', 'null', 'Request failed', 'API notice', '[object Object]']
            .filter((needle) => text.includes(needle));
          const headings = Array.from(document.querySelectorAll('h1,h2'))
            .map((el) => el.innerText.trim())
            .filter(Boolean)
            .slice(0, 8);
          return {
            title: document.title,
            path: location.pathname + location.search,
            textLength: text.trim().length,
            headings,
            buttonCount: Array.from(document.querySelectorAll('button')).filter(visible).length,
            linkCount: Array.from(document.querySelectorAll('a[href]')).filter(visible).length,
            inputCount: Array.from(document.querySelectorAll('input,select,textarea')).filter(visible).length,
            formCount: document.querySelectorAll('form').length,
            disabledCount: Array.from(document.querySelectorAll('button:disabled,input:disabled,select:disabled,textarea:disabled')).filter(visible).length,
            viewportWidth,
            viewportHeight,
            scrollWidth,
            scrollHeight,
            horizontalOverflow: scrollWidth > viewportWidth + 2,
            overflowElements,
            blankControls,
            suspiciousText,
            rendered: text.trim().length > 0 || headings.length > 0 || document.querySelectorAll('button,a[href],input,select,textarea').length > 0,
          };
        })()`,
      })

      return evaluated.result?.value || null
    }

    let activePreviewScriptId = null
    async function preparePreviewRole(role) {
      if (activePreviewScriptId) {
        await send('Page.removeScriptToEvaluateOnNewDocument', { identifier: activePreviewScriptId })
      }

      const result = await send('Page.addScriptToEvaluateOnNewDocument', {
        source: `(() => {
          const previewKey = 'mandarin-spark.preview-role';
          const tokenKey = 'mandarin-spark.token';
          window.localStorage.removeItem(previewKey);
          window.sessionStorage.removeItem(previewKey);
          window.localStorage.removeItem(tokenKey);
          window.sessionStorage.removeItem(tokenKey);
          const role = ${JSON.stringify(role)};
          if (role) {
            window.sessionStorage.setItem(previewKey, role);
          }
        })();`,
      })
      activePreviewScriptId = result.identifier
    }

    for (const viewport of viewports) {
      await send('Emulation.setDeviceMetricsOverride', {
        width: viewport.width,
        height: viewport.height,
        deviceScaleFactor: 1,
        mobile: viewport.mobile,
      })

      for (const route of routes) {
        const url = `${baseUrl}${route}`
        const record = {
          route,
          url,
          viewport: viewport.name,
          width: viewport.width,
          height: viewport.height,
          status: 'PENDING',
          screenshot: '',
          navigationError: '',
          pageError: '',
          consoleErrors: [],
          consoleWarnings: [],
          runtimeExceptions: [],
          httpErrors: [],
          appHttpErrors: [],
          failedRequests: [],
          ui: null,
        }

        activeRecord = record
        await preparePreviewRole(previewRoleForRoute(route))
        const loadPromise = waitFor('Page.loadEventFired', 10000)
        const navigation = await send('Page.navigate', { url })
        if (navigation.errorText) {
          record.navigationError = navigation.errorText
        }
        await loadPromise
        try {
          await poll(`rendered page for ${route}`, evaluatePageUi, (ui) => Boolean(ui?.path && ui.rendered), 15000, 300)
        } catch (error) {
          record.pageError ||= error.message
        }

        try {
          record.ui = await evaluatePageUi()
        } catch (error) {
          record.pageError = error.message
        }

        try {
          const screenshot = await send('Page.captureScreenshot', { format: 'png', fromSurface: true })
          const screenshotName = `${viewport.name}-${routeSlug(route)}.png`
          const screenshotPath = path.join(evidenceDir, screenshotName)
          await writeFile(screenshotPath, Buffer.from(screenshot.data, 'base64'))
          record.screenshot = screenshotPath
        } catch (error) {
          record.consoleWarnings.push(`Screenshot failed: ${error.message}`)
        }

        record.consoleErrors = [...new Set(record.consoleErrors)]
        record.consoleWarnings = [...new Set(record.consoleWarnings)]
        record.status = getCaseStatus(record)
        records.push(record)
        activeRecord = null
      }
    }

    const report = {
      generatedAt: new Date().toISOString(),
      baseUrl,
      preflight,
      evidenceDir,
      routes,
      viewports,
      totals: {
        cases: records.length,
        pass: records.filter((record) => record.status === 'PASS').length,
        needsReview: records.filter((record) => record.status === 'NEEDS_REVIEW').length,
        needsFix: records.filter((record) => record.status === 'NEEDS_FIX').length,
        fail: records.filter((record) => record.status === 'FAIL').length,
      },
      records,
    }

    await writeFile(path.join(evidenceDir, 'report.json'), JSON.stringify(report, null, 2), 'utf8')
    await writeFile(path.join(evidenceDir, 'chrome.log'), chromeLogs.join('\n'), 'utf8')

    for (const record of records) {
      const issues = []
      if (record.navigationError) issues.push(`navigation=${record.navigationError}`)
      if (record.pageError) issues.push(`page=${record.pageError}`)
      if (record.runtimeExceptions.length) issues.push(`runtime=${record.runtimeExceptions.length}`)
      if (record.consoleErrors.length) issues.push(`console=${record.consoleErrors.length}`)
      if (record.appHttpErrors.length) issues.push(`appHttp=${record.appHttpErrors.length}`)
      if (record.failedRequests.length) issues.push(`network=${record.failedRequests.length}`)
      if (record.ui?.horizontalOverflow) issues.push('overflowX')
      if (record.ui?.suspiciousText?.length) issues.push(`text=${record.ui.suspiciousText.join('|')}`)
      console.log(
        `${record.status}\t${record.viewport}\t${record.route}\t${record.ui?.title || ''}\t${issues.join(',')}`,
      )
    }

    console.log(`REPORT_JSON=${path.join(evidenceDir, 'report.json')}`)
  } finally {
    client?.close()
    chrome.kill()
    await sleep(500)
    await rm(userDataDir, { recursive: true, force: true })
  }
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
