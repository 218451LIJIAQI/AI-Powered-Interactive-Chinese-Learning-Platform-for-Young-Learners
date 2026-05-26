/**
 * Browser-driven deep audit — exhaustive route walk + accessibility
 * + network/console correctness check.
 *
 * <h2>Scope vs. browser-qa-scan</h2>
 * `browser-qa-scan` is a fast smoke pass; this script is the deeper
 * audit that should run before a release. For EVERY route in the
 * canonical list (~30 routes covering public / student / teacher /
 * admin / system) it:
 *   - Loads the route in three viewport sizes (desktop / tablet /
 *     mobile) to catch responsive layout regressions.
 *   - Captures console errors, runtime exceptions, network 4xx/5xx,
 *     and CSP violations.
 *   - Runs a `document.body.scrollWidth > window.innerWidth` check
 *     to flag horizontal overflow.
 *   - Screenshots each viewport for visual diff review.
 *   - Records final URL after redirects so legacy-path normalisation
 *     can be verified.
 *
 * <h2>Evidence</h2>
 * Outputs into `browser-qa-evidence/<timestamp>-deep-audit/` with
 * a per-viewport screenshot and a structured JSON summary that the
 * CI quality gate can grep for `fail` / `needsFix` counts.
 *
 * <h2>Configuration</h2>
 *   - `QA_BASE_URL`           — frontend origin
 *   - `QA_FRONTEND_PROBE_URL` — separate probe URL when behind a
 *                               reverse proxy
 *   - `QA_CHROME_PORT`        — DevTools port (default 9228)
 */
import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'
import { mkdir, rm, writeFile } from 'node:fs/promises'
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
const frontendProbeUrl = normalizeHttpBaseUrl(process.env.QA_FRONTEND_PROBE_URL || baseUrl, 'QA_FRONTEND_PROBE_URL')
const chromePath = resolveBrowserExecutablePath()
const debuggingPort = parseTcpPort(process.env.QA_CHROME_PORT || '9228', 'QA_CHROME_PORT')
const timestamp = new Date().toISOString().replace(/[:.]/g, '-')
const evidenceDir = path.resolve(process.cwd(), 'browser-qa-evidence', `${timestamp}-deep-audit`)
const userDataDir = path.join(os.tmpdir(), `mandarin-spark-deep-audit-${timestamp}`)

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

const guardRoutes = [
  { role: null, route: '/student/home', expectedPath: '/auth?redirect=/student/home' },
  { role: 'STUDENT', route: '/teacher/dashboard', expectedPath: '/auth?redirect=/teacher/dashboard' },
  { role: 'TEACHER', route: '/admin/dashboard', expectedPath: '/auth?redirect=/admin/dashboard' },
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
      if (page?.webSocketDebuggerUrl) return page.webSocketDebuggerUrl
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
        reject(new Error(`${message.error.message || 'CDP error'} ${safeJsonStringify(message.error.data || '')}`))
      } else {
        resolve(message.result || {})
      }
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
    socket.addEventListener('error', (event) => reject(event.error || new Error('WebSocket error')))
  })
}

function routeSlug(route) {
  const cleaned = route.replace(/[/?=&:#]+/g, '-').replace(/^-|-$/g, '')
  return cleaned || 'root'
}

function summarizeEventText(value) {
  if (value == null) return ''
  if (typeof value === 'string') return value
  if (typeof value === 'object' && 'description' in value) return value.description || ''
  return String(value)
}

function previewRoleForRoute(route) {
  if (route.startsWith('/student')) return 'STUDENT'
  if (route.startsWith('/teacher')) return 'TEACHER'
  if (route.startsWith('/admin')) return 'ADMIN'
  if (route.startsWith('/courses')) return 'STUDENT'
  return null
}

function pathMatches(actualPath, expectedPath) {
  if (!expectedPath) return true
  if (!actualPath) return false
  if (actualPath === expectedPath) return true
  if (expectedPath.includes('?')) return false
  return actualPath.startsWith(`${expectedPath}?`)
}

function isExpectedHttpError(status, url) {
  return status === 401 && typeof url === 'string' && url.includes('/api/v1/auth/refresh')
}

function isExpectedConsoleError(message) {
  return typeof message === 'string' && message.includes('the server responded with a status of 401')
}

function getCaseStatus(record) {
  if (record.navigationError || record.pageError) return 'FAIL'
  if (!record.ui?.rendered) return 'FAIL'
  if (
    record.runtimeExceptions.length ||
    record.consoleErrors.length ||
    record.appHttpErrors.length ||
    record.failedRequests.length
  ) {
    return 'NEEDS_FIX'
  }
  if (record.expectedPath && !pathMatches(record.actualPath, record.expectedPath)) return 'NEEDS_FIX'
  if (
    record.ui?.horizontalOverflow ||
    record.ui?.blankControls?.length ||
    record.ui?.unlabeledInputs?.length ||
    record.ui?.missingImageAlt?.length ||
    record.ui?.smallTouchTargets?.length ||
    record.ui?.suspiciousText?.length ||
    record.ui?.visibleReplacementCharacter
  ) {
    return 'NEEDS_REVIEW'
  }
  return 'PASS'
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
    client = await createCdpClient(await waitForDevTools())
    const { send, on, waitFor } = client
    let activeRecord = null
    const requestsById = new Map()

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
      if (params.type === 'error' && !isExpectedConsoleError(message)) activeRecord?.consoleErrors.push(message)
      if (params.type === 'warning' || params.type === 'warn') activeRecord?.consoleWarnings.push(message)
    })

    on('Log.entryAdded', (params) => {
      const entry = params.entry || {}
      if (entry.level === 'error' && !isExpectedConsoleError(entry.text || 'Log error'))
        activeRecord?.consoleErrors.push(entry.text || 'Log error')
      if (entry.level === 'warning') activeRecord?.consoleWarnings.push(entry.text || 'Log warning')
    })

    on('Network.requestWillBeSent', (params) => {
      requestsById.set(params.requestId, {
        method: params.request?.method || '',
        url: params.request?.url || '',
      })
    })

    on('Network.loadingFailed', (params) => {
      if (params.canceled || params.errorText === 'net::ERR_ABORTED') return
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
        const item = { status: response.status, type: params.type, url: response.url }
        activeRecord?.httpErrors.push(item)
        if (
          (response.url.startsWith(baseUrl) || response.url.includes('/api/')) &&
          !isExpectedHttpError(response.status, response.url)
        ) {
          activeRecord?.appHttpErrors.push(item)
        }
      }
    })

    await send('Page.enable')
    await send('Runtime.enable')
    await send('Network.enable')
    await send('Log.enable')

    const records = []

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
          const labelFor = (el) => {
            if (el.id) {
              const label = document.querySelector('label[for="' + CSS.escape(el.id) + '"]');
              if (label?.innerText?.trim()) return label.innerText.trim();
            }
            const wrappingLabel = el.closest('label');
            if (wrappingLabel?.innerText?.trim()) return wrappingLabel.innerText.trim();
            return '';
          };
          const visible = (el) => {
            const rect = el.getBoundingClientRect();
            const style = window.getComputedStyle(el);
            return rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none';
          };
          const controlLabel = (el) =>
            (el.innerText || el.getAttribute('aria-label') || el.getAttribute('title') || el.getAttribute('placeholder') || labelFor(el) || '').trim();
          const controlPreview = (el) => {
            const rect = el.getBoundingClientRect();
            return {
              tag: el.tagName.toLowerCase(),
              type: el.getAttribute('type') || '',
              role: el.getAttribute('role') || '',
              className: String(el.className || '').slice(0, 120),
              label: controlLabel(el).slice(0, 120),
              disabled: Boolean(el.disabled || el.getAttribute('aria-disabled') === 'true'),
              width: Math.round(rect.width),
              height: Math.round(rect.height),
            };
          };
          const controls = Array.from(document.querySelectorAll(selectors.join(','))).filter(visible);
          const textInputs = Array.from(document.querySelectorAll('input,select,textarea')).filter(visible);
          const horizontalOverflow = scrollWidth > viewportWidth + 2;
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
          const blankControls = controls
            .filter((el) => {
              const tag = el.tagName.toLowerCase();
              if (tag === 'input' || tag === 'select' || tag === 'textarea') return false;
              return !controlLabel(el);
            })
            .slice(0, 12)
            .map(controlPreview);
          const unlabeledInputs = textInputs
            .filter((el) => !controlLabel(el))
            .slice(0, 12)
            .map(controlPreview);
          const smallTouchTargets = controls
            .filter((el) => {
              if (el.disabled) return false;
              const rect = el.getBoundingClientRect();
              const label = controlLabel(el);
              if (!label || label.length <= 1) return false;
              return (rect.width < 36 || rect.height < 36) && viewportWidth <= 1024;
            })
            .slice(0, 12)
            .map(controlPreview);
          const missingImageAlt = Array.from(document.querySelectorAll('img'))
            .filter(visible)
            .filter((img) => !img.getAttribute('alt'))
            .slice(0, 12)
            .map((img) => ({ src: img.currentSrc || img.src, className: String(img.className || '').slice(0, 120) }));
          const headings = Array.from(document.querySelectorAll('h1,h2'))
            .map((el) => el.innerText.trim())
            .filter(Boolean)
            .slice(0, 8);
          const visibleButtons = Array.from(document.querySelectorAll('button'))
            .filter(visible)
            .map(controlPreview)
            .slice(0, 80);
          const visibleLinks = Array.from(document.querySelectorAll('a[href]'))
            .filter(visible)
            .map((el) => ({ ...controlPreview(el), href: el.getAttribute('href') || '' }))
            .slice(0, 80);
          const visibleInputs = textInputs.map(controlPreview).slice(0, 80);
          const suspiciousNeedles = ['undefined', 'NaN', 'null', 'Request failed', '[object Object]', '鈥', '馃', '鉃', '鈫', '猸', '鈻', '鑻', '濡', '浣', '膩', '菐'];
          const suspiciousText = suspiciousNeedles.filter((needle) => text.includes(needle));
          return {
            title: document.title,
            path: location.pathname + location.search,
            textLength: text.trim().length,
            headings,
            buttonCount: visibleButtons.length,
            linkCount: visibleLinks.length,
            inputCount: visibleInputs.length,
            formCount: document.querySelectorAll('form').length,
            disabledCount: Array.from(document.querySelectorAll('button:disabled,input:disabled,select:disabled,textarea:disabled')).filter(visible).length,
            viewportWidth,
            viewportHeight,
            scrollWidth,
            scrollHeight,
            horizontalOverflow,
            overflowElements,
            blankControls,
            unlabeledInputs,
            smallTouchTargets,
            missingImageAlt,
            suspiciousText,
            visibleReplacementCharacter: text.includes('\\uFFFD'),
            controls: { buttons: visibleButtons, links: visibleLinks, inputs: visibleInputs },
            rendered: text.trim().length > 0 || headings.length > 0 || controls.length > 0,
          };
        })()`,
      })

      return evaluated.result?.value || null
    }

    async function navigate(url) {
      const loadPromise = waitFor('Page.loadEventFired', 10000)
      const navigation = await send('Page.navigate', { url })
      await loadPromise
      await sleep(700)
      return navigation
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
          if (role) window.sessionStorage.setItem(previewKey, role);
        })();`,
      })
      activePreviewScriptId = result.identifier
    }

    const cases = [
      ...routes.map((route) => ({ route, role: previewRoleForRoute(route), expectedPath: '' })),
      ...guardRoutes,
    ]

    for (const viewport of viewports) {
      await send('Emulation.setDeviceMetricsOverride', {
        width: viewport.width,
        height: viewport.height,
        deviceScaleFactor: 1,
        mobile: viewport.mobile,
      })

      for (const item of cases) {
        const record = {
          route: item.route,
          role: item.role || 'guest',
          expectedPath: item.expectedPath || '',
          actualPath: '',
          url: `${baseUrl}${item.route}`,
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
        await preparePreviewRole(item.role)

        const navigation = await navigate(record.url)
        if (navigation.errorText) record.navigationError = navigation.errorText

        try {
          await poll(
            `rendered page for ${item.route}`,
            evaluatePageUi,
            (ui) => Boolean(ui?.path && ui.rendered && (!item.expectedPath || pathMatches(ui.path, item.expectedPath))),
            15000,
            300,
          )
        } catch (error) {
          record.pageError ||= error.message
        }

        try {
          record.ui = await evaluatePageUi()
          record.actualPath = record.ui?.path || ''
        } catch (error) {
          record.pageError = error.message
        }

        try {
          const screenshot = await send('Page.captureScreenshot', { format: 'png', fromSurface: true })
          const screenshotName = `${viewport.name}-${record.role}-${routeSlug(item.route)}.png`
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
      guardRoutes,
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

    await writeFile(path.join(evidenceDir, 'deep-audit-report.json'), safeJsonStringify(report), 'utf8')
    await writeFile(path.join(evidenceDir, 'chrome.log'), tailTextForEvidence(chromeLogs.join('\n')), 'utf8')

    for (const record of records) {
      const issues = []
      if (record.navigationError) issues.push(`navigation=${record.navigationError}`)
      if (record.pageError) issues.push(`page=${record.pageError}`)
      if (record.runtimeExceptions.length) issues.push(`runtime=${record.runtimeExceptions.length}`)
      if (record.consoleErrors.length) issues.push(`console=${record.consoleErrors.length}`)
      if (record.appHttpErrors.length) issues.push(`appHttp=${record.appHttpErrors.length}`)
      if (record.failedRequests.length) issues.push(`network=${record.failedRequests.length}`)
      if (record.expectedPath && !pathMatches(record.actualPath, record.expectedPath)) {
        issues.push(`expected=${record.expectedPath}`, `actual=${record.actualPath}`)
      }
      if (record.ui?.horizontalOverflow) issues.push('overflowX')
      if (record.ui?.blankControls?.length) issues.push(`blankControls=${record.ui.blankControls.length}`)
      if (record.ui?.unlabeledInputs?.length) issues.push(`unlabeledInputs=${record.ui.unlabeledInputs.length}`)
      if (record.ui?.missingImageAlt?.length) issues.push(`missingAlt=${record.ui.missingImageAlt.length}`)
      if (record.ui?.smallTouchTargets?.length) issues.push(`smallTargets=${record.ui.smallTouchTargets.length}`)
      if (record.ui?.suspiciousText?.length) issues.push(`text=${record.ui.suspiciousText.join('|')}`)
      if (record.ui?.visibleReplacementCharacter) issues.push('replacementChar')
      console.log(
        `${record.status}\t${record.viewport}\t${record.role}\t${record.route}\t${record.ui?.title || ''}\t${issues.join(',')}`,
      )
    }

    console.log(`DEEP_AUDIT_REPORT_JSON=${path.join(evidenceDir, 'deep-audit-report.json')}`)
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
