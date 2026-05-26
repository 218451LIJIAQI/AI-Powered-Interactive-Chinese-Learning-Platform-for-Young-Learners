/**
 * Browser-driven control click audit.
 *
 * This complements `browser-qa-deep-audit.mjs` and `browser-qa-flows.mjs`.
 * The deep audit proves every route renders correctly; the flow audit proves
 * core journeys work. This script adds a stricter control-level pass:
 * for each canonical route, role, and viewport it enumerates visible enabled
 * buttons / internal links, reloads the page to a clean state, performs a real
 * mouse click on each control, then checks that the app is still rendered,
 * has no console/runtime/network errors, and has no horizontal overflow.
 *
 * External links are recorded but skipped so the audit does not leave the app
 * or depend on third-party sites. Disabled controls are recorded as protected
 * states rather than clickable failures. Unlabelled internal controls are still
 * clicked and are marked as UI/accessibility issues.
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
const debuggingPort = parseTcpPort(process.env.QA_CHROME_PORT || '9232', 'QA_CHROME_PORT')
const routeFilter = process.env.QA_ROUTE_FILTER || ''
const viewportFilter = process.env.QA_VIEWPORT_FILTER || ''
const timestamp = new Date().toISOString().replace(/[:.]/g, '-')
const evidenceDir = path.resolve(process.cwd(), 'browser-qa-evidence', `${timestamp}-control-click-audit`)
const userDataDir = path.join(os.tmpdir(), `mandarin-spark-control-click-audit-${timestamp}`)

async function ensureEvidenceDir() {
  await mkdir(evidenceDir, { recursive: true })
}

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
  if (!response.ok) throw new Error(`${response.status} ${response.statusText}`)
  return response.json()
}

async function fetchProbe(url) {
  const response = await fetch(url)
  const contentType = response.headers.get('content-type') || ''
  const text = await response.text()
  return {
    url,
    status: response.status,
    contentType,
    body: contentType.toLowerCase().includes('json') ? tryParseJson(text) : text,
  }
}

function tryParseJson(text) {
  try {
    return JSON.parse(text)
  } catch {
    return text
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

  throw new Error(`Timed out waiting for ${description}: ${lastError?.message || JSON.stringify(lastValue)}`)
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

function previewRoleForRoute(route) {
  if (route.startsWith('/student')) return 'STUDENT'
  if (route.startsWith('/teacher')) return 'TEACHER'
  if (route.startsWith('/admin')) return 'ADMIN'
  if (route.startsWith('/courses')) return 'STUDENT'
  return null
}

function matchesFilter(value, filter) {
  if (!filter) return true
  return filter
    .split(',')
    .map((entry) => entry.trim())
    .filter(Boolean)
    .some((entry) => (entry.startsWith('=') ? value === entry.slice(1) : value.includes(entry)))
}

function pathMatches(actualPath, expectedPath) {
  if (!expectedPath) return true
  if (!actualPath) return false
  if (actualPath === expectedPath) return true
  if (expectedPath.includes('?')) return false
  return actualPath.startsWith(`${expectedPath}?`)
}

function routeSlug(route) {
  return route.replace(/[/?=&:#]+/g, '-').replace(/^-|-$/g, '') || 'root'
}

function isExpectedHttpError(status, url) {
  return status === 401 && typeof url === 'string' && url.includes('/api/v1/auth/refresh')
}

function isExpectedConsoleError(message) {
  return typeof message === 'string' && message.includes('the server responded with a status of 401')
}

function isInternalHref(href) {
  if (!href) return false
  if (href.startsWith('#') || href.startsWith('/')) return true
  try {
    const target = new URL(href, baseUrl)
    return target.origin === new URL(baseUrl).origin
  } catch {
    return false
  }
}

function caseStatus(record) {
  if (record.pageError) return 'FAIL'
  if (record.clicks.some((click) => click.status === 'FAIL')) return 'FAIL'
  if (record.clicks.some((click) => click.status === 'NEEDS_FIX')) return 'NEEDS_FIX'
  return 'PASS'
}

async function main() {
  if (!existsSync(chromePath)) throw new Error(`Chrome executable not found at ${chromePath}`)

  const preflight = {
    frontendProbeUrl,
    frontend: await waitForEndpoint(
      `frontend at ${frontendProbeUrl}`,
      frontendProbeUrl,
      (result) => result.status >= 200 && result.status < 400,
      20000,
    ),
  }

  await ensureEvidenceDir()

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
  let activeClick = null
  let activePreviewScriptId = null
  const requestsById = new Map()
  const records = []

  try {
    client = await createCdpClient(await waitForDevTools())
    const { send, on, waitFor } = client

    on('Runtime.exceptionThrown', (params) => {
      activeClick?.runtimeExceptions.push(
        params.exceptionDetails?.exception?.description || params.exceptionDetails?.text || 'Runtime exception',
      )
    })

    on('Runtime.consoleAPICalled', (params) => {
      if (!activeClick) return
      const message =
        params.args
          ?.map((arg) => arg.value || arg.description || '')
          .filter(Boolean)
          .join(' ') || params.type
      if (params.type === 'error' && !isExpectedConsoleError(message)) activeClick.consoleErrors.push(message)
      if (params.type === 'warning' || params.type === 'warn') activeClick.consoleWarnings.push(message)
    })

    on('Log.entryAdded', (params) => {
      if (!activeClick) return
      const entry = params.entry || {}
      if (entry.level === 'error' && !isExpectedConsoleError(entry.text || 'Log error'))
        activeClick.consoleErrors.push(entry.text || 'Log error')
      if (entry.level === 'warning') activeClick.consoleWarnings.push(entry.text || 'Log warning')
    })

    on('Network.requestWillBeSent', (params) => {
      requestsById.set(params.requestId, {
        method: params.request?.method || '',
        url: params.request?.url || '',
      })
    })

    on('Network.loadingFailed', (params) => {
      if (!activeClick || params.canceled || params.errorText === 'net::ERR_ABORTED') return
      const request = requestsById.get(params.requestId) || {}
      activeClick.failedRequests.push({
        method: request.method || '',
        url: request.url || '',
        errorText: params.errorText,
        type: params.type,
      })
    })

    on('Network.responseReceived', (params) => {
      if (!activeClick) return
      const response = params.response || {}
      if (response.status >= 400 && !isExpectedHttpError(response.status, response.url)) {
        activeClick.httpErrors.push({ status: response.status, url: response.url, type: params.type })
      }
    })

    await send('Page.enable')
    await send('Runtime.enable')
    await send('Network.enable')
    await send('Log.enable')

    async function evaluate(expression) {
      const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
      if (result.exceptionDetails) throw new Error(result.exceptionDetails.text || 'Evaluate failed')
      return result.result?.value
    }

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

    async function navigate(url) {
      await send('Page.navigate', { url })
      await sleep(350)
      await send('Page.stopLoading').catch(() => {})
      await sleep(650)
    }

    async function prepareCase(item, viewport) {
      await send('Emulation.setDeviceMetricsOverride', {
        width: viewport.width,
        height: viewport.height,
        deviceScaleFactor: 1,
        mobile: viewport.mobile,
      })
      await preparePreviewRole(item.role)
      await navigate(`${baseUrl}${item.route}`)
      await poll(
        `rendered ${item.route}`,
        pageState,
        (state) => state.rendered && (!item.expectedPath || pathMatches(state.path, item.expectedPath)),
        15000,
        300,
      )
    }

    async function pageState() {
      return evaluate(`(() => {
        const text = document.body?.innerText || '';
        const controls = Array.from(document.querySelectorAll('button,a[href],[role="button"]')).filter((el) => {
          const rect = el.getBoundingClientRect();
          const style = getComputedStyle(el);
          return rect.width > 0 && rect.height > 0 && style.display !== 'none' && style.visibility !== 'hidden';
        });
        return {
          title: document.title,
          path: location.pathname + location.search,
          href: location.href,
          rendered: text.trim().length > 0 || controls.length > 0,
          textLength: text.trim().length,
          notFound: document.title.includes('Page not found') || /Oops, this lesson path is missing/i.test(text),
          unauthorized: document.title.includes('Unauthorized'),
          horizontalOverflow: document.documentElement.scrollWidth > window.innerWidth + 2,
          scrollWidth: document.documentElement.scrollWidth,
          innerWidth: window.innerWidth,
          replacementChar: text.includes('\\uFFFD'),
        };
      })()`)
    }

    async function collectControls() {
      return evaluate(`(() => {
        const labelFor = (el) => {
          if (el.id) {
            const label = document.querySelector('label[for="' + CSS.escape(el.id) + '"]');
            if (label?.innerText?.trim()) return label.innerText.trim();
          }
          const wrappingLabel = el.closest('label');
          if (wrappingLabel?.innerText?.trim()) return wrappingLabel.innerText.trim();
          return '';
        };
        const controlLabel = (el) =>
          (el.innerText || el.getAttribute('aria-label') || el.getAttribute('title') || el.getAttribute('placeholder') || labelFor(el) || '').trim().replace(/\\s+/g, ' ');
        return Array.from(document.querySelectorAll('button,a[href],[role="button"]'))
          .map((el, domIndex) => ({ el, domIndex }))
          .filter(({ el }) => {
            const rect = el.getBoundingClientRect();
            const style = getComputedStyle(el);
            return rect.width > 0 && rect.height > 0 && style.display !== 'none' && style.visibility !== 'hidden';
          })
          .map(({ el, domIndex }, visibleIndex) => {
            const rect = el.getBoundingClientRect();
            const href = el.getAttribute('href') || '';
            return {
              visibleIndex,
              domIndex,
              tag: el.tagName.toLowerCase(),
              role: el.getAttribute('role') || '',
              type: el.getAttribute('type') || '',
              className: String(el.className || '').slice(0, 120),
              label: controlLabel(el).slice(0, 160),
              href,
              target: el.getAttribute('target') || '',
              disabled: Boolean(el.disabled || el.getAttribute('aria-disabled') === 'true'),
              width: Math.round(rect.width),
              height: Math.round(rect.height),
            };
          });
      })()`)
    }

    async function clickControl(visibleIndex) {
      const rect = await evaluate(`(() => {
        const controls = Array.from(document.querySelectorAll('button,a[href],[role="button"]')).filter((el) => {
          const rect = el.getBoundingClientRect();
          const style = getComputedStyle(el);
          return rect.width > 0 && rect.height > 0 && style.display !== 'none' && style.visibility !== 'hidden';
        });
        const el = controls[${visibleIndex}];
        if (!el) return null;
        el.scrollIntoView({ block: 'center', inline: 'center' });
        const rect = el.getBoundingClientRect();
        return {
          x: Math.max(1, Math.min(window.innerWidth - 1, rect.left + rect.width / 2)),
          y: Math.max(1, Math.min(window.innerHeight - 1, rect.top + rect.height / 2)),
          width: rect.width,
          height: rect.height,
          disabled: Boolean(el.disabled || el.getAttribute('aria-disabled') === 'true'),
        };
      })()`)

      if (!rect) throw new Error(`Visible control index ${visibleIndex} no longer exists`)
      if (rect.disabled) return { clicked: false, reason: 'became-disabled' }

      await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: rect.x, y: rect.y })
      await send('Input.dispatchMouseEvent', {
        type: 'mousePressed',
        x: rect.x,
        y: rect.y,
        button: 'left',
        clickCount: 1,
      })
      await send('Input.dispatchMouseEvent', {
        type: 'mouseReleased',
        x: rect.x,
        y: rect.y,
        button: 'left',
        clickCount: 1,
      })
      await sleep(700)
      return { clicked: true }
    }

    const cases = [
      ...routes.map((route) => ({ route, role: previewRoleForRoute(route), expectedPath: '' })),
      ...guardRoutes,
    ]

    for (const viewport of viewports.filter((entry) => matchesFilter(entry.name, viewportFilter))) {
      for (const item of cases.filter((entry) => matchesFilter(entry.route, routeFilter))) {
        const record = {
          route: item.route,
          role: item.role || 'guest',
          expectedPath: item.expectedPath || '',
          viewport: viewport.name,
          width: viewport.width,
          height: viewport.height,
          controlsFound: 0,
          clickable: 0,
          disabled: 0,
          externalSkipped: 0,
          emptyLabelControls: 0,
          clicks: [],
          pageError: '',
          status: 'PENDING',
        }

        try {
          await prepareCase(item, viewport)
          const controls = await collectControls()
          record.controlsFound = controls.length

          for (const control of controls) {
            const clickRecord = {
              control,
              status: 'PENDING',
              reason: '',
              beforePath: '',
              afterPath: '',
              afterTitle: '',
              consoleErrors: [],
              consoleWarnings: [],
              runtimeExceptions: [],
              httpErrors: [],
              failedRequests: [],
              uiIssues: [],
              screenshot: '',
            }

            if (!control.label) record.emptyLabelControls += 1

            if (control.disabled) {
              record.disabled += 1
              clickRecord.status = 'SKIPPED'
              clickRecord.reason = 'disabled'
              record.clicks.push(clickRecord)
              console.log(
                `SKIP\t${viewport.name}\t${record.role}\t${item.route}\t#${control.visibleIndex}\t${control.tag}\t${control.label}\tdisabled`,
              )
              continue
            }

            if (control.tag === 'a' && !isInternalHref(control.href)) {
              record.externalSkipped += 1
              clickRecord.status = 'SKIPPED'
              clickRecord.reason = 'external-link'
              record.clicks.push(clickRecord)
              console.log(
                `SKIP\t${viewport.name}\t${record.role}\t${item.route}\t#${control.visibleIndex}\t${control.tag}\t${control.label}\texternal`,
              )
              continue
            }

            record.clickable += 1
            activeClick = clickRecord

            try {
              await prepareCase(item, viewport)
              clickRecord.beforePath = (await pageState()).path
              const clickResult = await clickControl(control.visibleIndex)
              if (!clickResult.clicked) {
                clickRecord.status = 'SKIPPED'
                clickRecord.reason = clickResult.reason
                record.clicks.push(clickRecord)
                console.log(
                  `SKIP\t${viewport.name}\t${record.role}\t${item.route}\t#${control.visibleIndex}\t${control.tag}\t${control.label}\t${clickResult.reason}`,
                )
                activeClick = null
                continue
              }

              const state = await poll('post-click rendered state', pageState, (value) => value.rendered, 10000, 250)
              clickRecord.afterPath = state.path
              clickRecord.afterTitle = state.title

              if (!control.label) clickRecord.uiIssues.push('empty-accessible-label')
              if (!state.rendered) clickRecord.uiIssues.push('blank-page')
              if (state.horizontalOverflow) clickRecord.uiIssues.push('horizontal-overflow')
              if (state.replacementChar) clickRecord.uiIssues.push('replacement-character')
              if (state.notFound && !clickRecord.beforePath.includes('not-a-real-route-for-browser-qa')) {
                clickRecord.uiIssues.push('unexpected-not-found')
              }

              if (
                clickRecord.consoleErrors.length ||
                clickRecord.runtimeExceptions.length ||
                clickRecord.httpErrors.length ||
                clickRecord.failedRequests.length ||
                clickRecord.uiIssues.length
              ) {
                const screenshot = await send('Page.captureScreenshot', { format: 'png', fromSurface: true })
                const screenshotPath = path.join(
                  evidenceDir,
                  `${viewport.name}-${record.role}-${routeSlug(item.route)}-${control.visibleIndex}-failure.png`,
                )
                await ensureEvidenceDir()
                await writeFile(screenshotPath, Buffer.from(screenshot.data, 'base64'))
                clickRecord.screenshot = screenshotPath
                clickRecord.status = 'NEEDS_FIX'
              } else {
                clickRecord.status = 'PASS'
              }
            } catch (error) {
              clickRecord.status = 'FAIL'
              clickRecord.reason = redactText(error.message)
              try {
                const screenshot = await send('Page.captureScreenshot', { format: 'png', fromSurface: true })
                const screenshotPath = path.join(
                  evidenceDir,
                  `${viewport.name}-${record.role}-${routeSlug(item.route)}-${control.visibleIndex}-error.png`,
                )
                await ensureEvidenceDir()
                await writeFile(screenshotPath, Buffer.from(screenshot.data, 'base64'))
                clickRecord.screenshot = screenshotPath
              } catch {}
            } finally {
              activeClick = null
            }

            record.clicks.push(clickRecord)
            const displayLabel = control.label || '(empty label)'
            console.log(
              `${clickRecord.status}\t${viewport.name}\t${record.role}\t${item.route}\t#${control.visibleIndex}\t${control.tag}\t${displayLabel}\t${clickRecord.reason || clickRecord.afterPath || ''}`,
            )
          }
        } catch (error) {
          record.pageError = redactText(error.message)
        }

        record.status = caseStatus(record)
        records.push(record)
        const pass = record.clicks.filter((click) => click.status === 'PASS').length
        const needsFix = record.clicks.filter((click) => click.status === 'NEEDS_FIX').length
        const fail = record.clicks.filter((click) => click.status === 'FAIL').length
        console.log(
          `${record.status}\t${record.viewport}\t${record.role}\t${record.route}\tcontrols=${record.controlsFound}\tclicked=${pass}/${record.clickable}\tskipped=${record.disabled + record.externalSkipped}\tunlabeled=${record.emptyLabelControls}\tissues=${needsFix + fail}`,
        )
      }
    }

    const clicks = records.flatMap((record) => record.clicks.map((click) => ({ record, click })))
    const report = {
      generatedAt: new Date().toISOString(),
      baseUrl,
      preflight,
      evidenceDir,
      filters: {
        routeFilter,
        viewportFilter,
      },
      routes,
      guardRoutes,
      viewports,
      totals: {
        cases: records.length,
        pass: records.filter((record) => record.status === 'PASS').length,
        needsFix: records.filter((record) => record.status === 'NEEDS_FIX').length,
        fail: records.filter((record) => record.status === 'FAIL').length,
        controlsFound: records.reduce((total, record) => total + record.controlsFound, 0),
        clickedPass: clicks.filter(({ click }) => click.status === 'PASS').length,
        clickedNeedsFix: clicks.filter(({ click }) => click.status === 'NEEDS_FIX').length,
        clickedFail: clicks.filter(({ click }) => click.status === 'FAIL').length,
        skippedDisabled: records.reduce((total, record) => total + record.disabled, 0),
        skippedExternal: records.reduce((total, record) => total + record.externalSkipped, 0),
        unlabeledControls: records.reduce((total, record) => total + record.emptyLabelControls, 0),
      },
      records,
    }

    await ensureEvidenceDir()
    await writeFile(path.join(evidenceDir, 'control-click-report.json'), safeJsonStringify(report), 'utf8')
    await writeFile(path.join(evidenceDir, 'chrome.log'), tailTextForEvidence(chromeLogs.join('\n')), 'utf8')
    console.log(`CONTROL_CLICK_REPORT_JSON=${path.join(evidenceDir, 'control-click-report.json')}`)
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
