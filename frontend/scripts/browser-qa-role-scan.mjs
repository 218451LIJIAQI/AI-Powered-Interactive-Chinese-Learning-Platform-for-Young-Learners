/**
 * Browser-driven role-based access-control scan.
 *
 * <h2>What it asserts</h2>
 * For each persona scenario (guest / student / teacher / admin)
 * the script:
 *   1. Loads every route in that persona's scope, including a
 *      handful of cross-persona routes the scenario should NOT be
 *      able to view.
 *   2. Confirms the response — gated routes redirect to /auth or
 *      /unauthorized, while owned routes render normally.
 *   3. Verifies the final URL after Vue Router guards run.
 *   4. Captures console / network errors per route.
 *
 * <h2>Session wiring</h2>
 * Per-persona sessions are established through the real sign-in form.
 * This deliberately exercises the frontend's memory-only access token
 * design plus the HttpOnly refresh-cookie bootstrap used after a full
 * page navigation. The guest scenario clears browser storage/cookies and
 * uses no session.
 *
 * <h2>Evidence layout</h2>
 * `browser-qa-evidence/<timestamp>-roles/` with one screenshot per
 * `<role>__<route>` pair plus a structured JSON summary.
 *
 * <h2>When to run</h2>
 * Run this after touching the Vue Router auth guard, route
 * normalisation utilities, or any backend `@PreAuthorize` rule —
 * regressions in those layers are otherwise easy to miss in
 * single-persona QA.
 */
import { spawn } from 'node:child_process'
import { mkdir, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'

import {
  cleanupUserDataDir,
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
const apiBaseUrl = normalizeHttpBaseUrl(
  process.env.QA_API_BASE_URL || process.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080/api/v1',
  'QA_API_BASE_URL',
)
const chromePath = resolveBrowserExecutablePath()
const debuggingPort = parseTcpPort(process.env.QA_CHROME_PORT || '9225', 'QA_CHROME_PORT')
const configuredQuizId = String(process.env.QA_QUIZ_ID || '').trim()
const timestamp = new Date().toISOString().replace(/[:.]/g, '-')
const evidenceDir = path.resolve(process.cwd(), 'browser-qa-evidence', `${timestamp}-roles`)
const userDataDir = path.join(os.tmpdir(), `mandarin-spark-role-qa-${timestamp}`)

const personas = {
  student: {
    role: 'student',
    email: process.env.QA_STUDENT_EMAIL || '',
    password: process.env.QA_STUDENT_PASSWORD || '',
    landingPath: '/student/home',
  },
  teacher: {
    role: 'teacher',
    email: process.env.QA_TEACHER_EMAIL || '',
    password: process.env.QA_TEACHER_PASSWORD || '',
    landingPath: '/teacher/dashboard',
  },
  admin: {
    role: 'admin',
    email: process.env.QA_ADMIN_EMAIL || 'admin@example.com',
    password: process.env.QA_ADMIN_PASSWORD || '',
    landingPath: '/admin/dashboard',
  },
}

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

async function apiRequest(pathname, { method = 'GET', token, body } = {}) {
  const headers = {}
  const options = { method, headers }
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    options.body = JSON.stringify(body)
  }

  const response = await fetch(
    `${apiBaseUrl.replace(/\/+$/, '')}${pathname.startsWith('/') ? pathname : `/${pathname}`}`,
    options,
  )
  const contentType = response.headers.get('content-type') || ''
  const text = await response.text()
  let payload = text

  if (contentType.toLowerCase().includes('json')) {
    try {
      payload = JSON.parse(text)
    } catch {}
  }

  return { status: response.status, ok: response.ok, body: payload }
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
        reject(new Error(`${message.error.message || 'CDP error'} ${safeJsonStringify(message.error.data || '')}`))
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
  if (value == null) return ''
  if (typeof value === 'string') return value
  if (typeof value === 'object' && 'description' in value) return value.description || ''
  return String(value)
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
    record.ui?.overflowElements?.length ||
    record.ui?.blankControls?.length ||
    record.ui?.suspiciousText?.length
  ) {
    return 'NEEDS_REVIEW'
  }
  return 'PASS'
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

function requirePersonaCredentials() {
  for (const persona of Object.values(personas)) {
    if (!persona.email || !persona.password) {
      throw new Error(
        `Missing browser role-scan credentials for ${persona.role}. Set QA_${persona.role.toUpperCase()}_EMAIL and QA_${persona.role.toUpperCase()}_PASSWORD.`,
      )
    }
  }
}

async function discoverPublicCourseRoutes() {
  const configuredCourseId = String(process.env.QA_PUBLIC_COURSE_ID || '').trim()
  if (configuredCourseId) {
    return [`/courses/${configuredCourseId}`]
  }

  const response = await fetch(`${apiBaseUrl}/public/courses?size=3`)
  if (!response.ok) {
    throw new Error(`Could not discover public course route: ${response.status} ${response.statusText}`)
  }
  const body = await response.json()
  const courseIds = (body?.data?.items || []).map((item) => item?.id).filter(Boolean)
  if (!courseIds.length) {
    throw new Error('Could not discover public course route: /public/courses returned no published courses.')
  }
  return courseIds.slice(0, 2).map((id) => `/courses/${id}`)
}

async function discoverStudentQuizRoute() {
  if (configuredQuizId && !/^\d+$/.test(configuredQuizId)) {
    throw new Error(`QA_QUIZ_ID must be a positive numeric id when provided. Received: ${configuredQuizId}`)
  }

  const result = {
    configuredQuizId: configuredQuizId || null,
    route: null,
    source: 'none',
    skippedConfiguredQuiz: null,
    discoveredQuizId: null,
  }

  const login = await apiRequest('/auth/login', {
    method: 'POST',
    body: {
      email: personas.student.email,
      password: personas.student.password,
    },
  })
  const token = login.body?.data?.token
  if (login.status !== 200 || !token) {
    throw new Error(`Could not authenticate student for quiz route discovery: ${login.status}`)
  }

  if (configuredQuizId) {
    const configuredQuiz = await apiRequest(`/students/quizzes/${configuredQuizId}`, { token })
    if (configuredQuiz.status === 200 && configuredQuiz.body?.success === true) {
      result.route = `/student/quiz/${configuredQuizId}`
      result.source = 'configured'
      return result
    }

    result.skippedConfiguredQuiz = {
      quizId: configuredQuizId,
      status: configuredQuiz.status,
      message: configuredQuiz.body?.message || summarizeValue(configuredQuiz.body),
    }
  }

  const quizList = await apiRequest('/students/quizzes?size=1', { token })
  if (quizList.status !== 200 || quizList.body?.success !== true) {
    throw new Error(`Could not discover accessible student quiz route: ${quizList.status}`)
  }

  const discoveredQuizId = quizList.body?.data?.items?.[0]?.id
  if (discoveredQuizId) {
    result.discoveredQuizId = discoveredQuizId
    result.route = `/student/quiz/${discoveredQuizId}`
    result.source = 'student-list'
  }

  return result
}

function buildRoleCases(publicCourseRoutes, studentQuizRoute) {
  const resetPasswordCase = {
    route: '/reset-password?token=qa-demo-token',
    expectedPath: '/reset-password',
  }
  const scenarios = [
    {
      role: 'guest',
      routes: [
        { route: '/', expectedPath: '/' },
        { route: '/auth', expectedPath: '/auth' },
        resetPasswordCase,
        { route: '/terms', expectedPath: '/terms' },
        { route: '/privacy', expectedPath: '/privacy' },
        { route: '/payment/success', expectedPath: '/payment/success' },
        { route: '/payment/cancel', expectedPath: '/payment/cancel' },
        { route: '/courses', expectedPath: '/courses' },
        ...publicCourseRoutes.map((route) => ({ route, expectedPath: route })),
        { route: '/unauthorized', expectedPath: '/unauthorized' },
        {
          route: '/not-a-real-route-for-browser-qa',
          expectedPath: '/not-a-real-route-for-browser-qa',
        },
      ],
    },
    {
      role: 'student',
      routes: [
        '/student/home',
        '/student/learn',
        '/student/practice',
        '/student/quiz',
        ...(studentQuizRoute.route ? [studentQuizRoute.route] : []),
        '/student/classes',
        '/student/achievements',
        '/student/rewards',
        '/student/ai-helper',
        '/student/profile',
      ].map((route) => ({ route, expectedPath: route })),
    },
    {
      role: 'teacher',
      routes: [
        '/teacher/dashboard',
        '/teacher/content',
        '/teacher/classes',
        '/teacher/students',
        '/teacher/feedback',
        '/teacher/profile',
        '/teacher/earnings',
      ].map((route) => ({ route, expectedPath: route })),
    },
    {
      role: 'admin',
      routes: [
        '/admin/dashboard',
        '/admin/users',
        '/admin/teacher-verification',
        '/admin/content',
        '/admin/activities',
        '/admin/moderation',
        '/admin/finance',
        '/admin/analytics',
        '/admin/settings',
      ].map((route) => ({ route, expectedPath: route })),
    },
  ]

  const guardScenarios = [
    { role: 'student', route: '/admin/dashboard', expectedPath: '/unauthorized' },
    { role: 'teacher', route: '/admin/dashboard', expectedPath: '/unauthorized' },
    { role: 'student', route: '/teacher/dashboard', expectedPath: '/unauthorized' },
    { role: 'guest', route: '/student/home', expectedPath: '/auth' },
  ]

  return {
    scenarios,
    guardScenarios,
    allCases: [
      ...scenarios.flatMap((scenario) => scenario.routes.map((item) => ({ role: scenario.role, ...item }))),
      ...guardScenarios,
    ],
  }
}

async function main() {
  if (!existsSync(chromePath)) {
    throw new Error(`Chrome executable not found at ${chromePath}`)
  }

  requirePersonaCredentials()
  const publicCourseRoutes = await discoverPublicCourseRoutes()
  const studentQuizRoute = await discoverStudentQuizRoute()
  const { scenarios, guardScenarios, allCases } = buildRoleCases(publicCourseRoutes, studentQuizRoute)

  const preflight = {
    frontendProbeUrl,
    frontend: await waitForEndpoint(
      `frontend at ${frontendProbeUrl}`,
      frontendProbeUrl,
      (result) => result.status >= 200 && result.status < 400,
      20000,
    ),
    apiBaseUrl,
    publicCourseRoutes,
    studentQuizRoute,
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

    async function navigate(url) {
      const loadPromise = waitFor('Page.loadEventFired', 10000)
      const navigation = await send('Page.navigate', { url })
      await loadPromise
      await sleep(1400)
      return navigation
    }

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
          const escapeForSelector = (value) => {
            if (window.CSS?.escape) return window.CSS.escape(value);
            return String(value).replace(/["\\\\]/g, '\\\\$&');
          };
          const labelText = (el) => {
            const direct = (
              el.innerText ||
              el.getAttribute('aria-label') ||
              el.getAttribute('title') ||
              el.getAttribute('placeholder') ||
              ''
            ).trim();
            if (direct) return direct;

            if (el.tagName.toLowerCase() === 'select') {
              const selected = Array.from(el.selectedOptions || []).map((option) => option.textContent || '').join(' ').trim();
              if (selected) return selected;
            }

            const ariaLabelledBy = (el.getAttribute('aria-labelledby') || '').trim();
            if (ariaLabelledBy) {
              const fromIds = ariaLabelledBy
                .split(/\\s+/)
                .map((id) => document.getElementById(id)?.innerText || '')
                .join(' ')
                .trim();
              if (fromIds) return fromIds;
            }

            if (el.id) {
              const label = document.querySelector('label[for="' + escapeForSelector(el.id) + '"]');
              const text = (label?.innerText || '').trim();
              if (text) return text;
            }

            const wrappingLabel = el.closest('label');
            return (wrappingLabel?.innerText || '').trim();
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
              const label = labelText(el);
              return !label && el.tagName.toLowerCase() !== 'input' && el.tagName.toLowerCase() !== 'textarea';
            })
            .slice(0, 12)
            .map((el) => ({ tag: el.tagName.toLowerCase(), className: String(el.className || '').slice(0, 120) }));
          const suspiciousText = ['undefined', 'NaN', 'null', 'Request failed', '[object Object]']
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

    async function waitForExpectedPath(expectedPath, timeoutMs = 15000) {
      if (!expectedPath) return null
      return poll(
        `route ${expectedPath}`,
        evaluatePageUi,
        (ui) => pathMatches(ui?.path || '', expectedPath),
        timeoutMs,
        250,
      )
    }

    async function evaluate(expression) {
      const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
      return result.result?.value
    }

    async function clearBrowserSession() {
      await send('Network.clearBrowserCookies')
      await navigate(`${baseUrl}/auth`)
      await evaluate(`(() => {
        try {
          window.sessionStorage.clear();
          window.localStorage.removeItem('mandarin-spark.token');
          window.localStorage.removeItem('mandarin-spark.auth-token');
          window.localStorage.removeItem('mandarin-spark.preview-role');
        } catch {}
        window.dispatchEvent(new CustomEvent('mandarin-spark:auth-invalidated'));
        return true;
      })()`)
      await sleep(300)
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
      if (!ok) throw new Error(`Could not set value for ${selector}`)
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
      await sleep(250)
    }

    let currentSessionRole = ''
    async function ensureSession(role) {
      if (currentSessionRole === role) return

      await clearBrowserSession()
      if (role === 'guest') {
        currentSessionRole = role
        return
      }

      const persona = personas[role]
      if (!persona) {
        throw new Error(`Unknown role scenario ${role}`)
      }

      await navigate(`${baseUrl}${persona.landingPath}`)
      await setValue('#login-email', persona.email)
      await setValue('#login-password', persona.password)
      await clickSelector('.auth-submit-button:not([disabled])')
      await waitForExpectedPath(persona.landingPath, 25000)
      currentSessionRole = role
    }

    for (const viewport of viewports) {
      await send('Emulation.setDeviceMetricsOverride', {
        width: viewport.width,
        height: viewport.height,
        deviceScaleFactor: 1,
        mobile: viewport.mobile,
      })

      for (const testCase of allCases) {
        const role = testCase.role
        const route = testCase.route
        const url = `${baseUrl}${route}`
        const record = {
          role,
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
          actualPath: '',
          expectedPath: testCase.expectedPath || '',
        }

        await ensureSession(testCase.role)

        activeRecord = record
        const navigation = await navigate(url)
        if (navigation.errorText) {
          record.navigationError = navigation.errorText
        }

        if (testCase.expectedPath) {
          try {
            await waitForExpectedPath(testCase.expectedPath)
          } catch (error) {
            record.pageError ||= error.message
          }
        }

        try {
          record.ui = await evaluatePageUi()
          record.actualPath = record.ui?.path || ''
        } catch (error) {
          record.pageError = error.message
        }

        try {
          const screenshot = await send('Page.captureScreenshot', { format: 'png', fromSurface: true })
          const screenshotName = `${viewport.name}-${role}-${routeSlug(route)}.png`
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
      configuredQuizId,
      studentQuizRoute,
      scenarios: scenarios.map(({ role, routes }) => ({ role, routes })),
      guardScenarios,
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

    await writeFile(path.join(evidenceDir, 'report.json'), safeJsonStringify(report), 'utf8')
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
      if (record.ui?.overflowElements?.length) issues.push(`overflowElements=${record.ui.overflowElements.length}`)
      if (record.ui?.blankControls?.length) issues.push(`blankControls=${record.ui.blankControls.length}`)
      if (record.ui?.suspiciousText?.length) issues.push(`text=${record.ui.suspiciousText.join('|')}`)
      console.log(
        `${record.status}\t${record.viewport}\t${record.role}\t${record.route}\t${record.ui?.title || ''}\t${issues.join(',')}`,
      )
    }

    console.log(`ROLE_REPORT_JSON=${path.join(evidenceDir, 'report.json')}`)
    if (report.totals.fail > 0 || report.totals.needsFix > 0 || report.totals.needsReview > 0) {
      throw new Error(`Browser role QA gate failed: ${safeJsonStringify(report.totals)}`)
    }
  } finally {
    client?.close()
    await stopProcessTree(chrome)
    await sleep(500)
    const cleanupWarning = await cleanupUserDataDir(userDataDir)
    if (cleanupWarning) console.warn(`Browser profile cleanup warning: ${cleanupWarning}`)
  }
}

main().catch((error) => {
  console.error(redactText(error?.stack || error?.message || String(error)))
  process.exitCode = 1
})
